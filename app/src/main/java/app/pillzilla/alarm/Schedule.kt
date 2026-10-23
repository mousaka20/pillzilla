package app.pillzilla.alarm

import app.pillzilla.data.Reminder
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** Pure timing rules, kept free of Android types so they can be unit tested. */
object Schedule {
    const val MINUTE = 60_000L

    /** Nudges at 0, 15, 30, 45, 60, then every 10 min up to 140. */
    val NUDGE_OFFSETS_MIN: List<Int> = listOf(0, 15, 30, 45, 60) + (70..140 step 10).toList()

    /** At this offset the reminder ends and the dose is logged as missed. */
    const val CUTOFF_MIN = 150

    /** Doses confirmed within this many minutes of the alarm count as on time. */
    const val ON_TIME_MIN = 20

    /** A scan this many minutes before the alarm already counts. */
    const val EARLY_MIN = 5

    fun offsetMillis(step: Int): Long =
        (if (step < NUDGE_OFFSETS_MIN.size) NUDGE_OFFSETS_MIN[step] else CUTOFF_MIN) * MINUTE

    /** The step that is due next for an occurrence that started at [scheduledAt], or null once past the cutoff. */
    fun nextStep(scheduledAt: Long, now: Long): Int? {
        if (now >= scheduledAt + CUTOFF_MIN * MINUTE) return null
        val idx = NUDGE_OFFSETS_MIN.indexOfFirst { scheduledAt + it * MINUTE > now }
        return if (idx == -1) NUDGE_OFFSETS_MIN.size else idx
    }

    /** The first start time of [reminder] strictly after [after], or null if it runs on no day. */
    fun nextStart(reminder: Reminder, after: Long, zone: ZoneId = ZoneId.systemDefault()): Long? {
        if (reminder.daysMask == 0) return null
        val afterTime = Instant.ofEpochMilli(after).atZone(zone)
        var date: LocalDate = afterTime.toLocalDate()
        repeat(8) {
            if (reminder.isOnDay(date.dayOfWeek.value)) {
                val candidate = ZonedDateTime.of(date.atTime(reminder.hour, reminder.minute), zone)
                if (candidate.isAfter(afterTime)) return candidate.toInstant().toEpochMilli()
            }
            date = date.plusDays(1)
        }
        return null
    }

    fun isOnTime(scheduledAt: Long, confirmedAt: Long): Boolean =
        confirmedAt - scheduledAt <= ON_TIME_MIN * MINUTE
}
