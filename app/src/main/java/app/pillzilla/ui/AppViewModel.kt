package app.pillzilla.ui

import android.app.Application
import android.nfc.Tag
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.pillzilla.AssignResult
import app.pillzilla.Pillzilla
import app.pillzilla.ScanResult
import app.pillzilla.data.Occurrence
import app.pillzilla.data.PointsEntry
import app.pillzilla.data.Reminder
import app.pillzilla.data.RewardItem
import app.pillzilla.nfc.TagIo
import app.pillzilla.points.PointsConfig
import app.pillzilla.points.StreakState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Tag assignment in progress for one reminder. */
data class AssignState(
    val reminderId: Int,
    val tagName: String,
    val message: String? = null,
    val done: Boolean = false,
    val error: Boolean = false,
)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val app = Pillzilla.get(application)
    private val db = app.db

    val reminders: StateFlow<List<Reminder>> = db.reminders().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val pending: StateFlow<List<Occurrence>> = db.occurrences().observePending()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val balance: StateFlow<Int> = db.points().observeBalance()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val lifetime: StateFlow<Int> = db.points().observeLifetime()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val recentPoints: StateFlow<List<PointsEntry>> = db.points().observeRecent(20)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val rewards: StateFlow<List<RewardItem>> = db.rewards().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val config: StateFlow<PointsConfig> = app.rules.changes().map { app.rules.config }
        .stateIn(viewModelScope, SharingStarted.Eagerly, app.rules.config)

    val streak: StateFlow<StreakState> = app.rules.changes().map { app.rules.streak }
        .stateIn(viewModelScope, SharingStarted.Eagerly, app.rules.streak)

    private val _assign = MutableStateFlow<AssignState?>(null)
    val assign: StateFlow<AssignState?> = _assign.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun occurrencesBetween(from: Long, to: Long): Flow<List<Occurrence>> = db.occurrences().observeBetween(from, to)

    fun pointsEarnedBetween(from: Long, to: Long): Flow<Int> = db.points().observeEarnedBetween(from, to)

    fun consumeMessage() {
        _message.value = null
    }

    fun saveReminder(reminder: Reminder) = viewModelScope.launch(Dispatchers.IO) { app.saveReminder(reminder) }

    fun startAssign(reminderId: Int, suggestedName: String) {
        _assign.value = AssignState(reminderId, suggestedName)
    }

    fun setAssignName(name: String) {
        _assign.value = _assign.value?.copy(tagName = name)
    }

    fun cancelAssign() {
        _assign.value = null
    }

    /** Called by the NFC reader on a binder thread for every tag held to the phone. */
    fun onTag(tag: Tag) {
        val uid = TagIo.uidOf(tag)
        val assigning = _assign.value
        viewModelScope.launch(Dispatchers.IO) {
            if (assigning != null && !assigning.done) {
                assignTag(assigning, tag, uid)
            } else {
                _message.value = when (val result = app.handleScan(uid)) {
                    is ScanResult.Confirmed -> buildString {
                        append(result.reminder.name).append(" confirmed")
                        if (result.early) append(" (early)")
                        if (result.points > 0) append(" · +").append(result.points).append(" points")
                    }
                    is ScanResult.NothingDue -> "${result.reminder.name}: nothing to confirm right now"
                    ScanResult.UnknownTag -> "This tag isn't assigned to a reminder"
                }
            }
        }
    }

    private suspend fun assignTag(state: AssignState, tag: Tag, uid: String) {
        when (val result = app.assignTag(state.reminderId, uid, state.tagName)) {
            is AssignResult.TagInUse -> _assign.value = state.copy(
                message = "This tag already belongs to ${result.owner.name}. Each reminder needs its own tag.",
                error = true,
            )
            AssignResult.ReminderActive -> _assign.value = state.copy(
                message = "This reminder is active. You can change its tag once it has ended.",
                error = true,
            )
            AssignResult.Ok -> {
                val written = TagIo.writeMarker(tag, getApplication<Application>().packageName)
                _assign.value = state.copy(
                    done = true,
                    error = false,
                    message = if (written) {
                        "Tag $uid saved. Scanning it now opens Pillzilla, even when it's closed."
                    } else {
                        "Tag $uid saved, but it couldn't be written. It works while Pillzilla is open."
                    },
                )
            }
        }
    }

    fun saveConfig(config: PointsConfig) {
        app.rules.config = config
    }

    fun saveReward(reward: RewardItem) = viewModelScope.launch(Dispatchers.IO) { db.rewards().upsert(reward) }

    fun deleteReward(reward: RewardItem) = viewModelScope.launch(Dispatchers.IO) { db.rewards().delete(reward) }

    fun redeem(reward: RewardItem) = viewModelScope.launch(Dispatchers.IO) {
        val streakNow = app.rules.streak.current
        when {
            db.points().balance() < reward.cost -> _message.value = "Not enough points for ${reward.name} yet"
            streakNow < reward.requiredStreak -> _message.value = "${reward.name} needs a ${reward.requiredStreak}-dose streak"
            else -> {
                db.points().insert(PointsEntry(at = System.currentTimeMillis(), delta = -reward.cost, reason = "Redeemed ${reward.name}"))
                _message.value = "Enjoy: ${reward.name}!"
            }
        }
    }
}
