@file:OptIn(ExperimentalMaterial3Api::class)

package app.pillzilla.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pillzilla.data.Occurrence
import app.pillzilla.data.OccurrenceStatus
import app.pillzilla.data.Reminder
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

@Composable
fun LogScreen(viewModel: AppViewModel, onBack: () -> Unit, onRewards: () -> Unit, modifier: Modifier = Modifier) {
    var monthView by rememberSaveable { mutableStateOf(false) }
    var offset by rememberSaveable { mutableIntStateOf(0) }
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)

    val start: LocalDate = if (monthView) {
        today.withDayOfMonth(1).plusMonths(offset.toLong())
    } else {
        today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusWeeks(offset.toLong())
    }
    val end: LocalDate = if (monthView) start.plusMonths(1) else start.plusWeeks(1)
    val from = start.atStartOfDay(zone).toInstant().toEpochMilli()
    val to = end.atStartOfDay(zone).toInstant().toEpochMilli()

    val occurrencesFlow = remember(from, to) { viewModel.occurrencesBetween(from, to) }
    val occurrences by occurrencesFlow.collectAsStateWithLifecycle(emptyList())
    val earnedFlow = remember(from, to) { viewModel.pointsEarnedBetween(from, to) }
    val earned by earnedFlow.collectAsStateWithLifecycle(0)
    val reminders by viewModel.reminders.collectAsStateWithLifecycle()
    val streak by viewModel.streak.collectAsStateWithLifecycle()
    val recent by viewModel.recentPoints.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        TopBar("Log", onBack = onBack)

        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = !monthView,
                onClick = { monthView = false; offset = 0 },
                shape = SegmentedButtonDefaults.itemShape(0, 2),
            ) { Text("Week") }
            SegmentedButton(
                selected = monthView,
                onClick = { monthView = true; offset = 0 },
                shape = SegmentedButtonDefaults.itemShape(1, 2),
            ) { Text("Month") }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { offset-- }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous")
            }
            val label = if (monthView) {
                start.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH))
            } else {
                val fmt = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)
                "${start.format(fmt)} to ${end.minusDays(1).format(fmt)}"
            }
            Text(label, fontWeight = FontWeight.Bold, color = PzColors.Muted, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            IconButton(onClick = { offset++ }, enabled = offset < 0) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next")
            }
        }

        val resolved = occurrences.filter { it.status != OccurrenceStatus.PENDING }
        val onTime = resolved.count { it.status == OccurrenceStatus.ON_TIME }
        val missed = resolved.count { it.status == OccurrenceStatus.MISSED }
        val delays = resolved.mapNotNull { o -> o.confirmedAt?.let { (it - o.scheduledAt).coerceAtLeast(0) } }
        val avgDelay = if (delays.isEmpty()) null else delays.average().toLong()
        val pct = if (resolved.isEmpty()) "–" else "${onTime * 100 / resolved.size}% of doses"

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Stat("Within 20 min", "$onTime / ${resolved.size}", pct, Modifier.weight(1f))
            Stat("Missed", "$missed", if (missed == 0) "None, nice!" else "Streak resets", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Stat("Avg. delay", avgDelay?.let { duration(it) } ?: "–", "alarm to scan", Modifier.weight(1f))
            Stat("Points earned", "+${points(earned)}", "Streak: ${streak.current} · best ${streak.best}", Modifier.weight(1f))
        }

        Card {
            if (monthView) {
                MonthGrid(start, today, reminders, occurrences, zone)
            } else {
                WeekGrid(start, today, reminders, occurrences, zone)
            }
        }

        Card(color = PzColors.Night) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Recent points", color = Color.White, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                TextButton(onClick = onRewards) { Text("Rewards and rules", color = PzColors.Mint) }
            }
            if (recent.isEmpty()) {
                Text("Scan your first tag to start earning.", color = PzColors.TealPale, fontSize = 13.sp)
            }
            recent.take(8).forEach { entry ->
                Row {
                    Text("${dayClock(entry.at)}  ${entry.reason}", color = Color(0xFFE6FFFA), fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Text(
                        (if (entry.delta > 0) "+" else "") + points(entry.delta),
                        color = if (entry.delta > 0) Color(0xFF5EEAD4) else Color(0xFFFDE68A),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, sub: String, modifier: Modifier) {
    Card(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            SectionLabel(label)
            Text(value, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
            Text(sub, fontSize = 12.sp, color = PzColors.Muted)
        }
    }
}

private enum class DotKind { ON_TIME, LATE, MISSED, UPCOMING, NONE }

private fun dotFor(reminder: Reminder, date: LocalDate, today: LocalDate, list: List<Occurrence>, zone: ZoneId): DotKind {
    val occ = list.firstOrNull {
        it.reminderId == reminder.id && Instant.ofEpochMilli(it.scheduledAt).atZone(zone).toLocalDate() == date
    }
    return when (occ?.status) {
        OccurrenceStatus.ON_TIME -> DotKind.ON_TIME
        OccurrenceStatus.LATE -> DotKind.LATE
        OccurrenceStatus.MISSED -> DotKind.MISSED
        OccurrenceStatus.PENDING -> DotKind.UPCOMING
        null -> if (!date.isBefore(today) && reminder.enabled && reminder.isOnDay(date.dayOfWeek.value)) DotKind.UPCOMING else DotKind.NONE
    }
}

@Composable
private fun Dot(kind: DotKind) {
    val base = Modifier.size(18.dp).clip(CircleShape)
    when (kind) {
        DotKind.ON_TIME -> Box(base.background(PzColors.Teal))
        DotKind.LATE -> Box(base.background(PzColors.Amber))
        DotKind.MISSED -> Box(base.border(2.dp, PzColors.Red, CircleShape))
        DotKind.UPCOMING -> Box(base.background(PzColors.Line))
        DotKind.NONE -> Box(Modifier.size(18.dp))
    }
}

@Composable
private fun WeekGrid(start: LocalDate, today: LocalDate, reminders: List<Reminder>, list: List<Occurrence>, zone: ZoneId) {
    val days = (0 until 7).map { start.plusDays(it.toLong()) }
    Row {
        Spacer(Modifier.width(72.dp))
        days.forEach { d ->
            Text(
                d.dayOfWeek.getDisplayName(java.time.format.TextStyle.NARROW, Locale.ENGLISH),
                fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PzColors.Muted,
                textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
            )
        }
    }
    reminders.forEach { r ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(r.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(72.dp))
            days.forEach { d ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { Dot(dotFor(r, d, today, list, zone)) }
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Legend(DotKind.ON_TIME, "Within 20 min")
        Legend(DotKind.LATE, "Late")
        Legend(DotKind.MISSED, "Missed")
    }
}

@Composable
private fun Legend(kind: DotKind, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Dot(kind)
        Text(" $label", fontSize = 11.sp, color = PzColors.Muted)
    }
}

@Composable
private fun MonthGrid(start: LocalDate, today: LocalDate, reminders: List<Reminder>, list: List<Occurrence>, zone: ZoneId) {
    val lead = start.dayOfWeek.value - 1
    val length = start.lengthOfMonth()
    val cells = List(lead) { null } + (1..length).map { start.withDayOfMonth(it) }
    Row {
        WEEKDAY_SHORT.forEach {
            Text(it, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PzColors.Muted, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
        }
    }
    cells.chunked(7).forEach { week ->
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (i in 0 until 7) {
                val date = week.getOrNull(i)
                Box(Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                    if (date != null) {
                        val dayList = list.filter { Instant.ofEpochMilli(it.scheduledAt).atZone(zone).toLocalDate() == date }
                        val onTime = dayList.count { it.status == OccurrenceStatus.ON_TIME }
                        val due = reminders.count { it.enabled && it.tagUid != null && it.isOnDay(date.dayOfWeek.value) }
                        val (bg, fg) = when {
                            date.isAfter(today) || dayList.isEmpty() -> PzColors.Ground to PzColors.Muted
                            onTime == 0 -> PzColors.RedSoft to PzColors.Red
                            onTime >= maxOf(due, 1) -> PzColors.Teal to Color.White
                            onTime * 2 >= due -> PzColors.TealMid to PzColors.Night
                            else -> PzColors.TealPale to PzColors.Night
                        }
                        Box(
                            Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(8.dp))
                                .background(bg)
                                .then(if (date == today) Modifier.border(2.dp, PzColors.Ink, RoundedCornerShape(8.dp)) else Modifier),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("${date.dayOfMonth}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = fg)
                        }
                    }
                }
            }
        }
    }
    Text("Darker = more doses on time that day. Red = none on time.", fontSize = 11.sp, color = PzColors.Muted)
}
