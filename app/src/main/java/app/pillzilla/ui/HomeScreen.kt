package app.pillzilla.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pillzilla.alarm.AlarmScheduler
import app.pillzilla.alarm.Notifier
import app.pillzilla.alarm.Schedule
import app.pillzilla.data.Occurrence
import app.pillzilla.data.OccurrenceStatus
import app.pillzilla.data.Reminder
import app.pillzilla.nfc.TagIo
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HomeScreen(
    viewModel: AppViewModel,
    resumeTick: Int,
    onEdit: (Int) -> Unit,
    onLog: () -> Unit,
    onRewards: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reminders by viewModel.reminders.collectAsStateWithLifecycle()
    val pending by viewModel.pending.collectAsStateWithLifecycle()
    val balance by viewModel.balance.collectAsStateWithLifecycle()

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(30_000)
        }
    }
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val from = today.atStartOfDay(zone).toInstant().toEpochMilli()
    val to = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val todayFlow = remember(from) { viewModel.occurrencesBetween(from, to) }
    val todayOccurrences by todayFlow.collectAsStateWithLifecycle(emptyList())

    val scheduledToday = reminders.filter { it.enabled && it.tagUid != null && it.isOnDay(today.dayOfWeek.value) }
    val doneToday = todayOccurrences.count {
        it.status == OccurrenceStatus.ON_TIME || it.status == OccurrenceStatus.LATE
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Pillzilla", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = PzColors.Teal)
                Text("Today", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH)) +
                        " · $doneToday of ${scheduledToday.size} done",
                    fontSize = 14.sp,
                    color = PzColors.Muted,
                )
            }
            FilledTonalButton(onClick = onRewards) {
                Icon(Icons.Filled.Star, contentDescription = null)
                Text(" ${points(balance)}", fontWeight = FontWeight.ExtraBold)
            }
            IconButton(onClick = onLog) { Icon(Icons.Filled.BarChart, contentDescription = "Log") }
        }

        PermissionBanners(resumeTick)

        for (reminder in reminders) {
            ReminderCard(
                reminder = reminder,
                pending = pending.firstOrNull { it.reminderId == reminder.id },
                today = todayOccurrences.filter { it.reminderId == reminder.id },
                now = now,
                onClick = { onEdit(reminder.id) },
            )
        }

        Card(color = PzColors.TealSoft) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Nfc, contentDescription = null, tint = PzColors.Teal)
                Text(
                    "  Hold an assigned tag to the phone any time. Pillzilla doesn't need to be open.",
                    fontSize = 13.sp,
                    color = PzColors.Night,
                )
            }
        }
    }
}

@Composable
private fun ReminderCard(reminder: Reminder, pending: Occurrence?, today: List<Occurrence>, now: Long, onClick: () -> Unit) {
    val active = pending != null
    Card(modifier = Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(reminder.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = PzColors.Muted)
                Text(hhmm(reminder.hour, reminder.minute), fontSize = 40.sp, fontWeight = FontWeight.Bold)
            }
            val done = today.firstOrNull { it.status == OccurrenceStatus.ON_TIME || it.status == OccurrenceStatus.LATE }
            val missed = today.any { it.status == OccurrenceStatus.MISSED }
            when {
                active -> Pill("Overdue ${duration(now - pending!!.scheduledAt)}", PzColors.AmberSoft, PzColors.AmberInk)
                done != null -> Pill("Done ${clock(done.confirmedAt ?: done.scheduledAt)}", PzColors.GreenSoft, PzColors.Green)
                missed -> Pill("Missed", PzColors.RedSoft, PzColors.Red)
                !reminder.enabled -> Pill("Off", PzColors.Line, PzColors.Muted)
                reminder.tagUid == null -> Pill("Needs a tag", PzColors.AmberSoft, PzColors.AmberInk)
                else -> Schedule.nextStart(reminder, now)?.let { next ->
                    val sameDay = next - now < 24 * 60 * Schedule.MINUTE
                    Pill(if (sameDay) "In ${duration(next - now)}" else dayClock(next), PzColors.Line, PzColors.Ink)
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (reminder.tagUid != null) {
                Icon(Icons.Filled.Nfc, contentDescription = null, tint = PzColors.Teal)
                val extra = if (active) {
                    Schedule.nextStep(pending!!.scheduledAt, now)?.let {
                        " · next nudge ${clock(pending.scheduledAt + Schedule.offsetMillis(it))}"
                    } ?: ""
                } else ""
                Text("  ${reminder.tagName}$extra", fontSize = 13.sp, color = PzColors.Muted)
            } else {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = PzColors.AmberInk)
                Text("  No tag assigned · tap to assign", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = PzColors.AmberInk)
            }
        }
        if (active) {
            Text(
                "Hold your ${reminder.tagName} to the phone to confirm.",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = PzColors.Teal,
            )
        }
    }
}

@Composable
private fun PermissionBanners(resumeTick: Int) {
    val context = LocalContext.current
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    // Reading resumeTick makes the checks below run again after returning from system settings.
    if (resumeTick < 0) return

    val notificationsOk = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    val exactOk = AlarmScheduler(context).canScheduleExact()
    val fullScreenOk = Notifier(context).canUseFullScreen()
    val nfcOk = TagIo.isEnabled(context)
    val batteryOk = context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

    if (!TagIo.isAvailable(context)) {
        Banner("This phone has no NFC, so tags can't be scanned.", null) {}
    }
    if (!notificationsOk) {
        Banner("Allow notifications so Pillzilla can remind you.", "Allow") {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    if (!exactOk) {
        Banner("Allow alarms and reminders so doses ring exactly on time.", "Open settings") {
            open(context, Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkg(context)))
        }
    }
    if (!fullScreenOk && Build.VERSION.SDK_INT >= 34) {
        Banner("Allow full-screen alarms so reminders ring like an alarm clock.", "Open settings") {
            open(context, Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkg(context)))
        }
    }
    if (TagIo.isAvailable(context) && !nfcOk) {
        Banner("NFC is off. Turn it on to scan your tags.", "Turn on") {
            open(context, Intent(Settings.ACTION_NFC_SETTINGS))
        }
    }
    if (!batteryOk) {
        Banner("Exclude Pillzilla from battery optimisation so alarms aren't delayed.", "Open settings") {
            open(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }
}

@Composable
private fun Banner(text: String, action: String?, onAction: () -> Unit) {
    Card(color = PzColors.AmberSoft) {
        Text(text, fontSize = 14.sp, color = PzColors.AmberInk)
        if (action != null) {
            TextButton(onClick = onAction, modifier = Modifier.fillMaxWidth()) { Text(action, fontWeight = FontWeight.Bold) }
        }
    }
}

private fun pkg(context: Context): Uri = Uri.parse("package:${context.packageName}")

private fun open(context: Context, intent: Intent) {
    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
