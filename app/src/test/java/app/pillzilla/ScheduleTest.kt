package app.pillzilla

import app.pillzilla.alarm.Schedule
import app.pillzilla.data.Reminder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class ScheduleTest {
    private val zone = ZoneId.of("Europe/Zurich")
    private val minute = Schedule.MINUTE

    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int): Long =
        LocalDateTime.of(y, mo, d, h, mi).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun nudgesStopBeforeCutoff() {
        assertEquals(listOf(0, 15, 30, 45, 60, 70, 80, 90, 100, 110, 120, 130, 140), Schedule.NUDGE_OFFSETS_MIN)
        assertEquals(150 * minute, Schedule.offsetMillis(Schedule.NUDGE_OFFSETS_MIN.size))
    }

    @Test
    fun nextStepResumesAfterReboot() {
        val start = at(2026, 10, 23, 13, 30)
        assertEquals(0, Schedule.nextStep(start, start - minute))
        assertEquals(2, Schedule.nextStep(start, start + 20 * minute))
        assertEquals(13, Schedule.nextStep(start, start + 145 * minute))
        assertNull(Schedule.nextStep(start, start + 150 * minute))
    }

    @Test
    fun nextStartSkipsUnselectedWeekdays() {
        // Friday 23 Oct 2026, weekdays only.
        val reminder = Reminder(id = 1, name = "Morning", hour = 8, minute = 0, daysMask = 0b0011111)
        val fridayNoon = at(2026, 10, 23, 12, 0)
        assertEquals(at(2026, 10, 26, 8, 0), Schedule.nextStart(reminder, fridayNoon, zone))
        val fridayEarly = at(2026, 10, 23, 7, 0)
        assertEquals(at(2026, 10, 23, 8, 0), Schedule.nextStart(reminder, fridayEarly, zone))
    }

    @Test
    fun noDaysMeansNoSchedule() {
        val reminder = Reminder(id = 1, name = "Morning", hour = 8, minute = 0, daysMask = 0)
        assertNull(Schedule.nextStart(reminder, at(2026, 10, 23, 7, 0), zone))
    }

    @Test
    fun twentyMinuteWindowIsOnTime() {
        val start = at(2026, 10, 23, 8, 0)
        assertTrue(Schedule.isOnTime(start, start + 20 * minute))
        assertTrue(Schedule.isOnTime(start, start - 5 * minute))
        assertFalse(Schedule.isOnTime(start, start + 21 * minute))
    }
}
