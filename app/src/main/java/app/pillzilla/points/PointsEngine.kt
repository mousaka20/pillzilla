package app.pillzilla.points

import app.pillzilla.alarm.Schedule
import app.pillzilla.data.AppDatabase
import app.pillzilla.data.OccurrenceStatus
import app.pillzilla.data.PointsEntry
import app.pillzilla.data.Reminder
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.IsoFields
import kotlin.math.roundToInt

/** Awards points and keeps the streak when a dose is confirmed or missed. */
class PointsEngine(
    private val db: AppDatabase,
    private val rules: PointsRules,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    /** Records the points for a confirmed dose and returns how many were earned. */
    suspend fun onConfirmed(reminder: Reminder, scheduledAt: Long, confirmedAt: Long): Int {
        val config = rules.config
        var state = rules.streak
        var earned = 0
        val onTime = Schedule.isOnTime(scheduledAt, confirmedAt)

        if (onTime) {
            val newStreak = state.current + 1
            val pts = (config.onTime * config.multiplierFor(newStreak)).roundToInt()
            earned += award(confirmedAt, pts, "${reminder.name} on time")
            config.milestones.firstOrNull { it.streak == newStreak }?.let {
                earned += award(confirmedAt, it.bonus, "${it.streak}-dose streak")
            }
            state = state.copy(current = newStreak, best = maxOf(state.best, newStreak))
        } else {
            val week = weekKey(confirmedAt)
            state = if (config.shieldEnabled && state.shieldUsedWeek != week) {
                state.copy(shieldUsedWeek = week)
            } else {
                state.copy(current = 0)
            }
            earned += award(confirmedAt, config.late, "${reminder.name} late")
        }
        rules.streak = state

        if (onTime) earned += maybePerfectDay(confirmedAt, config.perfectDay)
        return earned
    }

    fun onMissed() {
        rules.streak = rules.streak.copy(current = 0)
    }

    private suspend fun maybePerfectDay(now: Long, bonus: Int): Int {
        val day = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        if (rules.streak.lastPerfectDay == day.toEpochDay()) return 0
        val dueToday = db.reminders().getAll().filter {
            it.enabled && it.tagUid != null && it.isOnDay(day.dayOfWeek.value)
        }
        if (dueToday.isEmpty()) return 0
        val from = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val to = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val onTimeIds = db.occurrences().between(from, to)
            .filter { it.status == OccurrenceStatus.ON_TIME }
            .map { it.reminderId }
            .toSet()
        if (!dueToday.all { it.id in onTimeIds }) return 0
        rules.streak = rules.streak.copy(lastPerfectDay = day.toEpochDay())
        return award(now, bonus, "Perfect day")
    }

    private suspend fun award(at: Long, points: Int, reason: String): Int {
        if (points != 0) db.points().insert(PointsEntry(at = at, delta = points, reason = reason))
        return points
    }

    private fun weekKey(at: Long): Int {
        val date = Instant.ofEpochMilli(at).atZone(zone).toLocalDate()
        return date.get(IsoFields.WEEK_BASED_YEAR) * 100 + date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
    }
}
