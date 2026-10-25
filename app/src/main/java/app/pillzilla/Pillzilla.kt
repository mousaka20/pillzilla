package app.pillzilla

import android.content.Context
import app.pillzilla.alarm.AlarmScheduler
import app.pillzilla.alarm.Notifier
import app.pillzilla.alarm.Schedule
import app.pillzilla.data.AppDatabase
import app.pillzilla.data.Occurrence
import app.pillzilla.data.OccurrenceStatus
import app.pillzilla.data.Reminder
import app.pillzilla.points.PointsEngine
import app.pillzilla.points.PointsRules
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface ScanResult {
    data class Confirmed(val reminder: Reminder, val points: Int, val early: Boolean) : ScanResult
    data class NothingDue(val reminder: Reminder) : ScanResult
    data object UnknownTag : ScanResult
}

sealed interface DeleteResult {
    data object Ok : DeleteResult
    data object ReminderActive : DeleteResult
}

sealed interface AssignResult {
    data object Ok : AssignResult
    data class TagInUse(val owner: Reminder) : AssignResult
    data object ReminderActive : AssignResult
}

/** Ties the database, alarms, notifications and points together. All functions run off the main thread. */
class Pillzilla private constructor(context: Context) {
    private val appContext = context.applicationContext
    val db = AppDatabase.get(appContext)
    val rules = PointsRules(appContext)
    val notifier = Notifier(appContext)
    val scheduler = AlarmScheduler(appContext)
    private val points = PointsEngine(db, rules)
    private val lock = Mutex()

    /** Creates the first reminder on a fresh install only, so deleting every reminder sticks. */
    suspend fun ensureSeeded() = lock.withLock {
        val prefs = appContext.getSharedPreferences("app", Context.MODE_PRIVATE)
        if (prefs.getBoolean("seeded", false)) return@withLock
        if (db.reminders().count() == 0) db.reminders().insert(AppDatabase.FIRST_REMINDER)
        prefs.edit().putBoolean("seeded", true).apply()
    }

    /** Adds a reminder (no tag yet, so it doesn't ring until one is assigned) and returns its id. */
    suspend fun addReminder(): Int = lock.withLock {
        val id = db.reminders().maxId() + 1
        db.reminders().insert(Reminder(id = id, name = "Reminder $id", hour = 12, minute = 0))
        id
    }

    /** Deletes a reminder and its alarm. Not allowed while it's ringing, so deleting never skips a dose. */
    suspend fun deleteReminder(reminderId: Int): DeleteResult = lock.withLock {
        val reminder = db.reminders().get(reminderId) ?: return@withLock DeleteResult.Ok
        if (db.occurrences().pendingFor(reminderId) != null) return@withLock DeleteResult.ReminderActive
        scheduler.cancel(reminderId)
        notifier.cancelAlarm(reminderId)
        db.occurrences().deletePendingFor(reminderId)
        db.reminders().delete(reminder)
        DeleteResult.Ok
    }

    /** Rebuilds every alarm, resuming nudges for doses that are still open. */
    suspend fun rescheduleAll() = lock.withLock {
        val now = System.currentTimeMillis()
        for (reminder in db.reminders().getAll()) rescheduleLocked(reminder, now)
    }

    suspend fun saveReminder(reminder: Reminder) = lock.withLock {
        db.reminders().update(reminder)
        rescheduleLocked(reminder, System.currentTimeMillis())
    }

    private suspend fun rescheduleLocked(reminder: Reminder, now: Long) {
        scheduler.cancel(reminder.id)
        if (!reminder.enabled || reminder.tagUid == null) return
        val open = db.occurrences().pendingFor(reminder.id)
        if (open != null) {
            val step = Schedule.nextStep(open.scheduledAt, now)
            if (step == null) {
                markMissedLocked(reminder, open)
            } else {
                scheduler.set(reminder.id, open.scheduledAt, step, open.scheduledAt + Schedule.offsetMillis(step))
                return
            }
        }
        scheduleNextStartLocked(reminder, now)
    }

    private suspend fun scheduleNextStartLocked(reminder: Reminder, after: Long) {
        var start = Schedule.nextStart(reminder, after) ?: return
        // Skip a start that was already confirmed by an early scan.
        while (db.occurrences().find(reminder.id, start)?.status?.let { it != OccurrenceStatus.PENDING } == true) {
            start = Schedule.nextStart(reminder, start) ?: return
        }
        scheduler.set(reminder.id, start, 0, start)
    }

    suspend fun onAlarm(reminderId: Int, scheduledAt: Long, step: Int) = lock.withLock {
        val reminder = db.reminders().get(reminderId) ?: return@withLock
        if (!reminder.enabled || reminder.tagUid == null) return@withLock
        val existing = db.occurrences().find(reminderId, scheduledAt)
        if (existing != null && existing.status != OccurrenceStatus.PENDING) {
            scheduleNextStartLocked(reminder, scheduledAt)
            return@withLock
        }
        val occurrence = existing ?: Occurrence(reminderId = reminderId, scheduledAt = scheduledAt)
            .let { it.copy(id = db.occurrences().insert(it)) }

        if (step >= Schedule.NUDGE_OFFSETS_MIN.size) {
            markMissedLocked(reminder, occurrence)
            scheduleNextStartLocked(reminder, scheduledAt)
            return@withLock
        }
        db.occurrences().update(occurrence.copy(nudgeCount = step + 1))
        notifier.showAlarm(reminder, scheduledAt, step)
        val next = step + 1
        scheduler.set(reminderId, scheduledAt, next, scheduledAt + Schedule.offsetMillis(next))
    }

    private suspend fun markMissedLocked(reminder: Reminder, occurrence: Occurrence) {
        db.occurrences().update(occurrence.copy(status = OccurrenceStatus.MISSED))
        points.onMissed()
        notifier.cancelAlarm(reminder.id)
        notifier.showMissed(reminder)
    }

    /** Called for every tag scan, from the app or from the background NFC activity. */
    suspend fun handleScan(uid: String, now: Long = System.currentTimeMillis()): ScanResult = lock.withLock {
        val reminder = db.reminders().findByTag(uid) ?: return@withLock ScanResult.UnknownTag
        val open = db.occurrences().pendingFor(reminder.id)
        if (open != null && now < open.scheduledAt + Schedule.CUTOFF_MIN * Schedule.MINUTE) {
            val status = if (Schedule.isOnTime(open.scheduledAt, now)) OccurrenceStatus.ON_TIME else OccurrenceStatus.LATE
            db.occurrences().update(open.copy(status = status, confirmedAt = now))
            val earned = points.onConfirmed(reminder, open.scheduledAt, now)
            finishConfirmLocked(reminder, open.scheduledAt, earned)
            return@withLock ScanResult.Confirmed(reminder, earned, early = false)
        }
        val nextStart = if (reminder.enabled) Schedule.nextStart(reminder, now) else null
        if (nextStart != null && nextStart - now <= Schedule.EARLY_MIN * Schedule.MINUTE) {
            val early = Occurrence(
                reminderId = reminder.id, scheduledAt = nextStart,
                status = OccurrenceStatus.ON_TIME, confirmedAt = now,
            )
            if (db.occurrences().insert(early) == -1L) {
                return@withLock ScanResult.NothingDue(reminder)
            }
            val earned = points.onConfirmed(reminder, nextStart, now)
            finishConfirmLocked(reminder, nextStart, earned)
            return@withLock ScanResult.Confirmed(reminder, earned, early = true)
        }
        ScanResult.NothingDue(reminder)
    }

    private suspend fun finishConfirmLocked(reminder: Reminder, scheduledAt: Long, earned: Int) {
        scheduler.cancel(reminder.id)
        notifier.cancelAlarm(reminder.id)
        notifier.showConfirmed(reminder, earned)
        scheduleNextStartLocked(reminder, scheduledAt)
    }

    /**
     * Assigns a scanned tag to a reminder. A tag can belong to one reminder only, and the tag of a
     * reminder can't be changed while that reminder is ringing, so losing a tag never skips a dose.
     */
    suspend fun assignTag(reminderId: Int, uid: String, tagName: String): AssignResult = lock.withLock {
        val owner = db.reminders().findByTag(uid)
        if (owner != null && owner.id != reminderId) return@withLock AssignResult.TagInUse(owner)
        if (db.occurrences().pendingFor(reminderId) != null) return@withLock AssignResult.ReminderActive
        val reminder = db.reminders().get(reminderId) ?: return@withLock AssignResult.ReminderActive
        val updated = reminder.copy(tagUid = uid, tagName = tagName.ifBlank { "Tag" })
        db.reminders().update(updated)
        rescheduleLocked(updated, System.currentTimeMillis())
        AssignResult.Ok
    }

    companion object {
        @Volatile
        private var instance: Pillzilla? = null

        fun get(context: Context): Pillzilla = instance ?: synchronized(this) {
            instance ?: Pillzilla(context).also { instance = it }
        }
    }
}
