package app.pillzilla.ui

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import app.pillzilla.Pillzilla
import app.pillzilla.ScanResult
import app.pillzilla.alarm.Schedule
import app.pillzilla.data.Occurrence
import app.pillzilla.data.Reminder
import app.pillzilla.nfc.TagIo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Full-screen alarm shown over the lock screen. Only the reminder's own tag clears it. */
class AlarmActivity : ComponentActivity() {
    private var ringtone: Ringtone? = null
    private var reminderId by mutableStateOf(-1)
    private var confirmedText by mutableStateOf<String?>(null)
    private var wrongTagText by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        reminderId = intent.getIntExtra(EXTRA_REMINDER, -1)
        val app = Pillzilla.get(this)

        setContent {
            PillzillaTheme {
                var reminder by remember { mutableStateOf<Reminder?>(null) }
                var occurrence by remember { mutableStateOf<Occurrence?>(null) }
                var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

                LaunchedEffect(reminderId) {
                    combine(app.db.reminders().observeAll(), app.db.occurrences().observePending()) { r, p ->
                        r.firstOrNull { it.id == reminderId } to p.firstOrNull { it.reminderId == reminderId }
                    }.collect { (r, o) ->
                        reminder = r
                        occurrence = o
                        if (o == null && confirmedText == null) finish()
                    }
                }
                LaunchedEffect(Unit) {
                    while (true) {
                        now = System.currentTimeMillis()
                        delay(15_000)
                    }
                }
                AlarmContent(reminder, occurrence, now, confirmedText, wrongTagText, onClose = { finish() })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        reminderId = intent.getIntExtra(EXTRA_REMINDER, reminderId)
    }

    override fun onResume() {
        super.onResume()
        startRinging()
        TagIo.enableReader(this) { tag ->
            val uid = TagIo.uidOf(tag)
            lifecycleScope.launch {
                val result = withContext(Dispatchers.IO) { Pillzilla.get(this@AlarmActivity).handleScan(uid) }
                when (result) {
                    is ScanResult.Confirmed -> {
                        stopRinging()
                        confirmedText = "${result.reminder.name} confirmed" +
                            if (result.points > 0) ". +${result.points} points!" else "."
                        delay(2_000)
                        finish()
                    }
                    is ScanResult.NothingDue -> wrongTagText = "That's the ${result.reminder.tagName}. Scan the tag for this reminder."
                    ScanResult.UnknownTag -> wrongTagText = "Unknown tag. Only the assigned tag clears this reminder."
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        TagIo.disableReader(this)
        stopRinging()
    }

    private fun startRinging() {
        if (ringtone?.isPlaying == true || confirmedText != null) return
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        ringtone = RingtoneManager.getRingtone(this, uri)?.apply {
            audioAttributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
            isLooping = true
            play()
        }
        // Ring for one minute per nudge; the next nudge rings again.
        lifecycleScope.launch {
            delay(60_000)
            stopRinging()
        }
    }

    private fun stopRinging() {
        ringtone?.stop()
        ringtone = null
    }

    companion object {
        private const val EXTRA_REMINDER = "reminderId"

        fun intent(context: Context, reminderId: Int): Intent =
            Intent(context, AlarmActivity::class.java)
                .putExtra(EXTRA_REMINDER, reminderId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

@Composable
private fun AlarmContent(
    reminder: Reminder?,
    occurrence: Occurrence?,
    now: Long,
    confirmedText: String?,
    wrongTagText: String?,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PzColors.Night)
            .padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            (reminder?.name ?: "Reminder").uppercase() + " REMINDER",
            color = PzColors.Mint, fontSize = 15.sp, fontWeight = FontWeight.Bold,
        )
        if (reminder != null) {
            Text(hhmm(reminder.hour, reminder.minute), color = Color.White, fontSize = 64.sp, fontWeight = FontWeight.ExtraBold)
        }
        if (occurrence != null && confirmedText == null) {
            val overdue = now - occurrence.scheduledAt
            if (overdue >= Schedule.MINUTE) {
                Pill("Overdue ${duration(overdue)} · nudge ${occurrence.nudgeCount}", Color(0xFFFBBF24), Color(0xFF451A03))
            }
        }

        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            if (confirmedText != null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFF5EEAD4), modifier = Modifier.size(120.dp))
                    Text(confirmedText, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(
                        Modifier.size(150.dp).clip(CircleShape).background(Color(0xFF14B8A6)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Nfc, contentDescription = null, tint = Color(0xFF042F2E), modifier = Modifier.size(72.dp))
                    }
                    Text(
                        "Scan your ${reminder?.tagName ?: "tag"}",
                        color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        wrongTagText ?: "Only this tag clears the reminder. Go get your meds!",
                        color = if (wrongTagText != null) Color(0xFFFDE68A) else PzColors.TealPale,
                        fontSize = 15.sp, textAlign = TextAlign.Center,
                    )
                }
            }
        }

        if (confirmedText == null) {
            TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Text("Close (reminder stays active)", color = PzColors.Mint, fontWeight = FontWeight.Bold)
            }
        }
    }
}
