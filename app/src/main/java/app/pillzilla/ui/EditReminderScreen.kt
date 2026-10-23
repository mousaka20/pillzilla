@file:OptIn(ExperimentalLayoutApi::class)

package app.pillzilla.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pillzilla.alarm.Schedule

@Composable
fun EditReminderScreen(viewModel: AppViewModel, reminderId: Int, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val reminders by viewModel.reminders.collectAsStateWithLifecycle()
    val original = reminders.firstOrNull { it.id == reminderId } ?: return
    var name by remember(reminderId) { mutableStateOf(original.name) }
    var hour by remember(reminderId) { mutableIntStateOf(original.hour) }
    var minute by remember(reminderId) { mutableIntStateOf(original.minute) }
    var days by remember(reminderId) { mutableIntStateOf(original.daysMask) }
    var enabled by remember(reminderId) { mutableStateOf(original.enabled) }
    var showPicker by remember { mutableStateOf(false) }

    val pending by viewModel.pending.collectAsStateWithLifecycle()
    val active = pending.firstOrNull { it.reminderId == reminderId }
    val assign by viewModel.assign.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TopBar("${original.name} reminder", onBack = onDone) {
            Switch(checked = enabled, onCheckedChange = { enabled = it })
        }

        Card {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(24) },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showPicker = true }
                    .padding(vertical = 4.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    SectionLabel("Time")
                    Text(hhmm(hour, minute), fontSize = 32.sp, fontWeight = FontWeight.Bold)
                }
                Icon(Icons.Filled.Schedule, contentDescription = "Change time", tint = PzColors.Teal)
            }
            SectionLabel("Repeat on")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                WEEKDAY_SHORT.forEachIndexed { i, label ->
                    val bit = 1 shl i
                    FilterChip(
                        selected = days and bit != 0,
                        onClick = { days = days xor bit },
                        label = { Text(label) },
                    )
                }
            }
        }

        Card {
            SectionLabel("Confirmation tag")
            if (original.tagUid != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Nfc, contentDescription = null, tint = PzColors.Teal, modifier = Modifier.size(28.dp))
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(original.tagName ?: "Tag", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(original.tagUid, fontSize = 12.sp, color = PzColors.Muted, fontFamily = FontFamily.Monospace)
                    }
                }
            } else {
                Text("No tag yet. The reminder only rings once a tag is assigned.", fontSize = 14.sp, color = PzColors.AmberInk)
            }

            val current = assign?.takeIf { it.reminderId == reminderId }
            when {
                active != null -> Text(
                    "This reminder is active. You can change its tag after " +
                        clock(active.scheduledAt + Schedule.CUTOFF_MIN * Schedule.MINUTE) + ", when it ends.",
                    fontSize = 13.sp,
                    color = PzColors.Muted,
                )
                current == null -> OutlinedButton(
                    onClick = { viewModel.startAssign(reminderId, original.tagName ?: "${original.name} tag") },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) {
                    Text(if (original.tagUid == null) "Assign tag" else "Replace or lost tag")
                }
                else -> AssignPanel(current, viewModel)
            }
        }

        Card {
            SectionLabel("Nudges until confirmed")
            Text(
                "At ${hhmm(hour, minute)}, then +15, +30, +45 and +60 min, then every 10 min. " +
                    "A scan up to 5 min early counts. Without a scan, the dose is logged as missed after 2 h 30 min.",
                fontSize = 13.sp,
                color = PzColors.Muted,
            )
        }

        Button(
            onClick = {
                viewModel.saveReminder(
                    original.copy(
                        name = name.ifBlank { original.name },
                        hour = hour,
                        minute = minute,
                        daysMask = days,
                        enabled = enabled,
                    ),
                )
                onDone()
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text("Save", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
    }

    if (showPicker) {
        TimeDialog(hour, minute, onDismiss = { showPicker = false }) { h, m ->
            hour = h
            minute = m
            showPicker = false
        }
    }
}

@Composable
private fun AssignPanel(state: AssignState, viewModel: AppViewModel) {
    OutlinedTextField(
        value = state.tagName,
        onValueChange = { viewModel.setAssignName(it.take(24)) },
        label = { Text("Tag name") },
        singleLine = true,
        enabled = !state.done,
        modifier = Modifier.fillMaxWidth(),
    )
    if (!state.done) {
        Card(color = PzColors.TealSoft) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Nfc, contentDescription = null, tint = PzColors.Teal, modifier = Modifier.size(32.dp))
                Text(
                    "  Hold the new tag to the back of your phone. Put it where your meds are.",
                    fontSize = 14.sp,
                    color = PzColors.Night,
                )
            }
        }
    }
    state.message?.let {
        Text(it, fontSize = 13.sp, color = if (state.error) PzColors.Red else PzColors.Green, fontWeight = FontWeight.SemiBold)
    }
    TextButton(onClick = { viewModel.cancelAssign() }, modifier = Modifier.fillMaxWidth()) {
        Text(if (state.done) "Done" else "Cancel")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(hour: Int, minute: Int, onDismiss: () -> Unit, onConfirm: (Int, Int) -> Unit) {
    val state = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onConfirm(state.hour, state.minute) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Reminder time") },
        text = { TimePicker(state = state) },
    )
}

