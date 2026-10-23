package app.pillzilla.points

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** A streak milestone: reaching [streak] on-time doses pays [bonus] once and multiplies points by [multiplier]. */
data class Milestone(val streak: Int, val bonus: Int, val multiplier: Double)

data class PointsConfig(
    val onTime: Int = 10,
    val late: Int = 3,
    val perfectDay: Int = 10,
    val milestones: List<Milestone> = DEFAULT_MILESTONES,
    val shieldEnabled: Boolean = true,
) {
    fun multiplierFor(streak: Int): Double =
        milestones.filter { streak >= it.streak }.maxOfOrNull { it.multiplier } ?: 1.0

    companion object {
        val DEFAULT_MILESTONES = listOf(
            Milestone(7, 15, 1.5),
            Milestone(30, 100, 2.0),
            Milestone(100, 500, 3.0),
        )
    }
}

data class StreakState(
    val current: Int = 0,
    val best: Int = 0,
    val shieldUsedWeek: Int = -1,
    val lastPerfectDay: Long = -1,
)

/** Stores the editable points rules and the streak counters in SharedPreferences. */
class PointsRules(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("points", Context.MODE_PRIVATE)

    var config: PointsConfig
        get() = PointsConfig(
            onTime = prefs.getInt("onTime", 10),
            late = prefs.getInt("late", 3),
            perfectDay = prefs.getInt("perfectDay", 10),
            milestones = prefs.getString("milestones", null)?.let { parseMilestones(it) }
                ?: PointsConfig.DEFAULT_MILESTONES,
            shieldEnabled = prefs.getBoolean("shield", true),
        )
        set(value) {
            prefs.edit()
                .putInt("onTime", value.onTime)
                .putInt("late", value.late)
                .putInt("perfectDay", value.perfectDay)
                .putString("milestones", formatMilestones(value.milestones))
                .putBoolean("shield", value.shieldEnabled)
                .apply()
        }

    var streak: StreakState
        get() = StreakState(
            current = prefs.getInt("streak", 0),
            best = prefs.getInt("bestStreak", 0),
            shieldUsedWeek = prefs.getInt("shieldWeek", -1),
            lastPerfectDay = prefs.getLong("lastPerfectDay", -1),
        )
        set(value) {
            prefs.edit()
                .putInt("streak", value.current)
                .putInt("bestStreak", value.best)
                .putInt("shieldWeek", value.shieldUsedWeek)
                .putLong("lastPerfectDay", value.lastPerfectDay)
                .apply()
        }

    /** Emits whenever any rule or counter changes, so the UI can re-read [config] and [streak]. */
    fun changes(): Flow<Unit> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(Unit) }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(Unit)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    companion object {
        fun formatMilestones(list: List<Milestone>): String =
            list.joinToString(";") { "${it.streak}:${it.bonus}:${it.multiplier}" }

        fun parseMilestones(raw: String): List<Milestone> = raw.split(";").mapNotNull { part ->
            val bits = part.split(":")
            if (bits.size != 3) return@mapNotNull null
            val streak = bits[0].toIntOrNull() ?: return@mapNotNull null
            val bonus = bits[1].toIntOrNull() ?: return@mapNotNull null
            val mult = bits[2].toDoubleOrNull() ?: return@mapNotNull null
            Milestone(streak, bonus, mult)
        }.sortedBy { it.streak }
    }
}
