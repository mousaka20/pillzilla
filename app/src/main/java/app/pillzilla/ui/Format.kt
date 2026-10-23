package app.pillzilla.ui

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val timeFmt = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)
private val dayTimeFmt = DateTimeFormatter.ofPattern("EEE HH:mm", Locale.ENGLISH)

fun hhmm(hour: Int, minute: Int): String = "%02d:%02d".format(hour, minute)

fun clock(millis: Long): String = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(timeFmt)

fun dayClock(millis: Long): String = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(dayTimeFmt)

fun duration(millis: Long): String {
    val totalMin = (millis / 60_000).coerceAtLeast(0)
    val h = totalMin / 60
    val m = totalMin % 60
    return if (h > 0) "$h h %02d min".format(m) else "$m min"
}

fun points(n: Int): String = "%,d".format(Locale.ENGLISH, n)

val WEEKDAY_SHORT = listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su")
