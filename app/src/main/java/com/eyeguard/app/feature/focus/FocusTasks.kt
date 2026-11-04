package com.eyeguard.app.feature.focus

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.eyeguard.app.R
import com.eyeguard.app.feature.focus.data.FocusTaskEntity
import kotlin.math.roundToInt

@Composable
fun FocusTaskDialog(
    tasks: List<FocusTaskEntity>,
    startingPlan: Boolean,
    onAdd: (String, Int) -> Unit,
    onStatus: (Long, String) -> Unit,
    rewardFeedback: TaskRewardFeedback?,
    onRewardDismiss: () -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    onSkip: () -> Unit
) {
    var title by rememberSaveable { mutableStateOf("") }
    var reward by rememberSaveable { mutableIntStateOf(3) }
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(rewardFeedback?.eventId) {
        if (rewardFeedback != null) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            kotlinx.coroutines.delay(1_600L)
            onRewardDismiss()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = CultivationPalette.Panel)
        ) {
            Column(
                Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(stringResource(R.string.focus_tasks_title),
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.focus_tasks_subtitle),
                    style = MaterialTheme.typography.bodySmall, color = CultivationPalette.InkSoft)
                AnimatedVisibility(
                    visible = rewardFeedback != null,
                    enter = fadeIn() + scaleIn(initialScale = 0.82f)
                ) {
                    rewardFeedback?.let { feedback ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = CultivationPalette.Jade,
                            contentColor = CultivationPalette.GoldLight
                        ) {
                            Text(
                                stringResource(R.string.focus_task_reward_feedback, feedback.xp, feedback.title),
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(60) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(15.dp),
                    singleLine = true,
                    label = { Text(stringResource(R.string.focus_task_name_hint)) }
                )
                Text(stringResource(R.string.focus_task_reward, reward),
                    style = MaterialTheme.typography.titleSmall, color = CultivationPalette.Jade)
                Slider(
                    value = reward.toFloat(),
                    onValueChange = { reward = it.roundToInt().coerceIn(1, 5) },
                    valueRange = 1f..5f,
                    steps = 3,
                    colors = SliderDefaults.colors(
                        thumbColor = CultivationPalette.Jade,
                        activeTrackColor = CultivationPalette.Jade
                    )
                )
                OutlinedButton(
                    onClick = {
                        if (title.isNotBlank()) {
                            onAdd(title, reward)
                            title = ""
                        }
                    },
                    enabled = title.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                        contentColor = CultivationPalette.Jade,
                        disabledContentColor = CultivationPalette.InkSoft
                    )
                ) {
                    Text(stringResource(R.string.focus_task_add))
                }

                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 290.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (tasks.isEmpty()) {
                        item {
                            Text(stringResource(R.string.focus_task_empty),
                                color = CultivationPalette.InkSoft,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(vertical = 8.dp))
                        }
                    }
                    items(tasks, key = { it.id }) { task ->
                        TaskRow(task, onStatus)
                    }
                }
                if (tasks.any { it.status == FocusTaskEntity.COMPLETED }) {
                    Text(stringResource(R.string.focus_task_restore_note),
                        color = CultivationPalette.InkSoft,
                        style = MaterialTheme.typography.labelSmall)
                }

                if (startingPlan) {
                    CultivationPrimaryButton(onClick = onConfirm, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.focus_tasks_start), fontWeight = FontWeight.Bold)
                    }
                    TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.focus_tasks_skip))
                    }
                } else {
                    CultivationPrimaryButton(onClick = onConfirm, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.focus_tasks_close), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskRow(task: FocusTaskEntity, onStatus: (Long, String) -> Unit) {
    val completed = task.status == FocusTaskEntity.COMPLETED
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (completed) CultivationPalette.JadeLight.copy(alpha = 0.16f)
        else CultivationPalette.Paper
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(
                task.title,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = if (task.status == FocusTaskEntity.CANCELLED) CultivationPalette.InkSoft
                else CultivationPalette.Ink
            )
            Spacer(Modifier.width(4.dp))
            Text(
                stringResource(when (task.status) {
                    FocusTaskEntity.COMPLETED -> R.string.focus_task_completed
                    FocusTaskEntity.CANCELLED -> R.string.focus_task_cancelled
                    else -> R.string.focus_task_pending
                }, task.rewardXp),
                style = MaterialTheme.typography.bodySmall,
                color = if (completed) CultivationPalette.Jade else CultivationPalette.InkSoft
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                when (task.status) {
                    FocusTaskEntity.ACTIVE -> {
                        TextButton(onClick = { onStatus(task.id, FocusTaskEntity.COMPLETED) }) {
                            Text(stringResource(R.string.focus_task_complete), color = CultivationPalette.Jade)
                        }
                        TextButton(onClick = { onStatus(task.id, FocusTaskEntity.CANCELLED) }) {
                            Text(stringResource(R.string.focus_task_cancel), color = CultivationPalette.Cinnabar)
                        }
                    }
                    else -> TextButton(onClick = { onStatus(task.id, FocusTaskEntity.ACTIVE) }) {
                        Text(stringResource(R.string.focus_task_restore), color = CultivationPalette.Jade)
                    }
                }
            }
        }
    }
}
