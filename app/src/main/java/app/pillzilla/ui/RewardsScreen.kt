package app.pillzilla.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pillzilla.data.RewardItem
import app.pillzilla.points.Milestone
import app.pillzilla.points.PointsConfig

@Composable
fun RewardsScreen(viewModel: AppViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val balance by viewModel.balance.collectAsStateWithLifecycle()
    val lifetime by viewModel.lifetime.collectAsStateWithLifecycle()
    val streak by viewModel.streak.collectAsStateWithLifecycle()
    val config by viewModel.config.collectAsStateWithLifecycle()
    val rewards by viewModel.rewards.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<RewardItem?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        TopBar("Rewards", onBack = onBack)

        Card(color = PzColors.Night) {
            Row {
                Column(Modifier.weight(1f)) {
                    Text("Points to spend", color = PzColors.Mint, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(points(balance), color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
                    Text("${points(lifetime)} earned in total", color = PzColors.TealPale, fontSize = 12.sp)
                }
                Column(Modifier.weight(1f)) {
                    Text("Streak", color = PzColors.Mint, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.LocalFireDepartment, contentDescription = null, tint = Color(0xFFFBBF24))
                        Text(" ${streak.current}", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    Text("on-time doses · best ${streak.best}", color = PzColors.TealPale, fontSize = 12.sp)
                }
            }
            val next = config.milestones.firstOrNull { it.streak > streak.current }
            if (next != null) {
                LinearProgressIndicator(
                    progress = { streak.current.toFloat() / next.streak },
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFFFBBF24),
                    trackColor = Color.White.copy(alpha = 0.15f),
                )
                Text(
                    "${next.streak - streak.current} more for the ${next.streak}-dose badge: +${next.bonus} and x${fmtMult(next.multiplier)} points",
                    color = PzColors.TealPale,
                    fontSize = 12.sp,
                )
            } else {
                Text("All streak milestones reached. Legend.", color = PzColors.TealPale, fontSize = 12.sp)
            }
        }

        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("My rewards", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                FilledTonalButton(onClick = { editing = RewardItem(name = "", cost = 500) }) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text(" Add reward")
                }
            }
            if (rewards.isEmpty()) {
                Text("Add something to look forward to, like \"Movie night, 500 points\".", fontSize = 13.sp, color = PzColors.Muted)
            }
            rewards.forEach { reward ->
                val lockedByStreak = streak.current < reward.requiredStreak
                val affordable = balance >= reward.cost
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(reward.name, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        val detail = buildString {
                            append(points(reward.cost)).append(" pts")
                            if (reward.requiredStreak > 0) append(" · needs a ${reward.requiredStreak}-dose streak")
                            if (!affordable) append(" · ${points(reward.cost - balance)} to go")
                        }
                        Text(detail, fontSize = 12.sp, color = PzColors.Muted)
                    }
                    IconButton(onClick = { editing = reward }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit ${reward.name}", tint = PzColors.Muted)
                    }
                    Button(onClick = { viewModel.redeem(reward) }, enabled = affordable && !lockedByStreak) {
                        Text(if (lockedByStreak) "Streak" else "Redeem")
                    }
                }
            }
        }

        RulesCard(config, onSave = viewModel::saveConfig)
    }

    editing?.let { reward ->
        RewardDialog(
            reward = reward,
            onDismiss = { editing = null },
            onSave = {
                viewModel.saveReward(it)
                editing = null
            },
            onDelete = if (reward.id != 0L) {
                {
                    viewModel.deleteReward(reward)
                    editing = null
                }
            } else null,
        )
    }
}

private fun fmtMult(m: Double): String = if (m % 1.0 == 0.0) m.toInt().toString() else m.toString()

@Composable
private fun RulesCard(config: PointsConfig, onSave: (PointsConfig) -> Unit) {
    var onTime by remember(config) { mutableStateOf(config.onTime.toString()) }
    var late by remember(config) { mutableStateOf(config.late.toString()) }
    var perfect by remember(config) { mutableStateOf(config.perfectDay.toString()) }
    var shield by remember(config) { mutableStateOf(config.shieldEnabled) }
    val milestones = remember(config) {
        config.milestones.map { Triple(mutableStateOf(it.streak.toString()), mutableStateOf(it.bonus.toString()), mutableStateOf(fmtMult(it.multiplier))) }
    }

    Card {
        Text("Points rules", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
        NumberRow("Taken within 20 min (or up to 5 min early)", onTime) { onTime = it }
        NumberRow("Taken late, before the 2 h 30 cutoff", late) { late = it }
        NumberRow("Perfect day bonus (all doses on time)", perfect) { perfect = it }

        SectionLabel("Streak milestones: doses in a row · bonus · multiplier")
        milestones.forEach { (streak, bonus, mult) ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallField(streak.value, "In a row", Modifier.weight(1f)) { streak.value = it }
                SmallField(bonus.value, "Bonus", Modifier.weight(1f)) { bonus.value = it }
                SmallField(mult.value, "x Points", Modifier.weight(1f), decimal = true) { mult.value = it }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Streak shield", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text("One late dose per week doesn't break the streak. A missed dose always does.", fontSize = 12.sp, color = PzColors.Muted)
            }
            Switch(checked = shield, onCheckedChange = { shield = it })
        }

        Button(
            onClick = {
                onSave(
                    PointsConfig(
                        onTime = onTime.toIntOrNull() ?: config.onTime,
                        late = late.toIntOrNull() ?: config.late,
                        perfectDay = perfect.toIntOrNull() ?: config.perfectDay,
                        milestones = milestones.mapNotNull { (s, b, m) ->
                            val streak = s.value.toIntOrNull() ?: return@mapNotNull null
                            if (streak <= 0) return@mapNotNull null
                            Milestone(streak, b.value.toIntOrNull() ?: 0, m.value.replace(',', '.').toDoubleOrNull() ?: 1.0)
                        }.sortedBy { it.streak },
                        shieldEnabled = shield,
                    ),
                )
            },
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) { Text("Save rules", fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun NumberRow(label: String, value: String, onChange: (String) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 14.sp, modifier = Modifier.weight(1f))
        SmallField(value, "Points", Modifier.width(96.dp), onChange = onChange)
    }
}

@Composable
private fun SmallField(value: String, label: String, modifier: Modifier, decimal: Boolean = false, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { new -> onChange(new.filter { it.isDigit() || (decimal && (it == '.' || it == ',')) }.take(6)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        modifier = modifier,
    )
}

@Composable
private fun RewardDialog(reward: RewardItem, onDismiss: () -> Unit, onSave: (RewardItem) -> Unit, onDelete: (() -> Unit)?) {
    var name by remember { mutableStateOf(reward.name) }
    var cost by remember { mutableStateOf(reward.cost.toString()) }
    var streak by remember { mutableStateOf(if (reward.requiredStreak > 0) reward.requiredStreak.toString() else "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (reward.id == 0L) "New reward" else "Edit reward") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it.take(32) }, label = { Text("Reward") }, singleLine = true)
                SmallField(cost, "Points", Modifier.fillMaxWidth()) { cost = it }
                SmallField(streak, "Needs a streak of (optional)", Modifier.fillMaxWidth()) { streak = it }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && (cost.toIntOrNull() ?: 0) > 0,
                onClick = {
                    onSave(reward.copy(name = name.trim(), cost = cost.toInt(), requiredStreak = streak.toIntOrNull() ?: 0))
                },
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text("Delete", color = PzColors.Red) }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
