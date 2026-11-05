package com.eyeguard.app.feature.focus

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.eyeguard.app.R

/** 一次只回答一个问题的今日计划向导；所有数值均用按钮调整，不依赖滑动。 */
@Composable
fun FocusPlanWizard(
    targetMinutes: Int,
    segmentMinutes: Int,
    restMinutes: Int,
    goal: String,
    strictMode: Boolean,
    presenceEnabled: Boolean,
    allowedAppCount: Int,
    usageAccessGranted: Boolean,
    onTargetChange: (Int) -> Unit,
    onSegmentChange: (Int) -> Unit,
    onRestChange: (Int) -> Unit,
    onGoalChange: (String) -> Unit,
    onStrictChange: (Boolean) -> Unit,
    onPresenceChange: (Boolean) -> Unit,
    onChooseApps: () -> Unit,
    onDismiss: () -> Unit,
    onComplete: () -> Unit
) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    val titles = listOf(
        stringResource(R.string.focus_plan_target_question),
        stringResource(R.string.focus_plan_segment_question),
        stringResource(R.string.focus_plan_rest_question),
        stringResource(R.string.focus_plan_strict_question),
        stringResource(R.string.focus_plan_presence_question),
        stringResource(R.string.focus_plan_goal_question)
    )

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = CultivationPalette.Panel,
                contentColor = CultivationPalette.Ink
            ),
            border = BorderStroke(1.dp, CultivationPalette.Gold.copy(alpha = 0.38f))
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    stringResource(R.string.focus_plan_wizard_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = CultivationPalette.Jade,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(5.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        titles[step],
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        stringResource(R.string.focus_plan_step, step + 1),
                        style = MaterialTheme.typography.labelMedium,
                        color = CultivationPalette.InkSoft
                    )
                }
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { (step + 1) / 6f },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = CultivationPalette.Gold,
                    trackColor = CultivationPalette.JadeLight.copy(alpha = 0.22f)
                )
                Spacer(Modifier.height(20.dp))

                Crossfade(targetState = step, label = "planWizardStep") { currentStep ->
                    when (currentStep) {
                        0 -> WizardQuestion(stringResource(R.string.focus_plan_target_hint)) {
                            MinuteStepper(
                                value = targetMinutes,
                                minimum = 25,
                                maximum = 480,
                                increment = 5,
                                onChange = onTargetChange
                            )
                        }
                        1 -> WizardQuestion(stringResource(R.string.focus_plan_segment_hint)) {
                            MinuteStepper(
                                value = segmentMinutes,
                                minimum = 25,
                                maximum = 120,
                                increment = 5,
                                onChange = onSegmentChange
                            )
                            PlanPreview(targetMinutes, segmentMinutes)
                        }
                        2 -> WizardQuestion(stringResource(R.string.focus_plan_rest_hint)) {
                            MinuteStepper(
                                value = restMinutes,
                                minimum = 1,
                                maximum = 30,
                                increment = 1,
                                onChange = onRestChange
                            )
                        }
                        3 -> Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            SettingSwitchRow(
                                title = stringResource(R.string.focus_strict_title),
                                description = stringResource(R.string.focus_strict_description),
                                checked = strictMode,
                                onChecked = onStrictChange
                            )
                            if (strictMode) {
                                OutlinedButton(
                                    onClick = onChooseApps,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(15.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = CultivationPalette.Ink
                                    )
                                ) {
                                    Text(stringResource(R.string.focus_allowed_apps, allowedAppCount))
                                }
                                Text(
                                    stringResource(
                                        if (usageAccessGranted) R.string.focus_usage_permission_granted
                                        else R.string.focus_usage_permission_hint
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (usageAccessGranted) CultivationPalette.Jade
                                    else CultivationPalette.Cinnabar
                                )
                            }
                        }
                        4 -> WizardQuestion(
                            stringResource(
                                if (strictMode) R.string.focus_presence_strict_description
                                else R.string.focus_presence_description
                            )
                        ) {
                            SettingSwitchRow(
                                title = stringResource(R.string.focus_presence_title),
                                description = stringResource(R.string.focus_privacy_note),
                                checked = strictMode || presenceEnabled,
                                enabled = !strictMode,
                                onChecked = onPresenceChange
                            )
                        }
                        else -> Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text(
                                stringResource(R.string.focus_plan_goal_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                color = CultivationPalette.InkSoft
                            )
                            OutlinedTextField(
                                value = goal,
                                onValueChange = { onGoalChange(it.take(40)) },
                                label = { Text(stringResource(R.string.focus_goal_optional)) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(15.dp),
                                minLines = 2,
                                maxLines = 2
                            )
                            PlanSummary(
                                targetMinutes = targetMinutes,
                                segmentMinutes = segmentMinutes,
                                restMinutes = restMinutes,
                                strictMode = strictMode,
                                presenceEnabled = strictMode || presenceEnabled
                            )
                        }
                    }
                }

                Spacer(Modifier.height(22.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { if (step == 0) onDismiss() else step-- }) {
                        Text(
                            stringResource(
                                if (step == 0) R.string.cancel else R.string.focus_plan_previous
                            ),
                            color = CultivationPalette.Ink
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    CultivationPrimaryButton(
                        onClick = { if (step == 5) onComplete() else step++ }
                    ) {
                        Text(
                            stringResource(
                                if (step == 5) R.string.focus_start_daily_plan
                                else R.string.focus_plan_next
                            ),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WizardQuestion(hint: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text(
            hint,
            style = MaterialTheme.typography.bodyMedium,
            color = CultivationPalette.InkSoft
        )
        content()
    }
}

@Composable
private fun MinuteStepper(
    value: Int,
    minimum: Int,
    maximum: Int,
    increment: Int,
    onChange: (Int) -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        StepButton(
            decrease = true,
            enabled = value > minimum,
            onClick = { onChange((value - increment).coerceAtLeast(minimum)) }
        )
        Text(
            stringResource(R.string.focus_minutes_value, value),
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.headlineMedium,
            color = CultivationPalette.Jade,
            fontWeight = FontWeight.Bold
        )
        StepButton(
            decrease = false,
            enabled = value < maximum,
            onClick = { onChange((value + increment).coerceAtMost(maximum)) }
        )
    }
}

@Composable
private fun StepButton(decrease: Boolean, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.width(58.dp).height(52.dp),
        shape = RoundedCornerShape(16.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = CultivationPalette.Jade,
            disabledContentColor = CultivationPalette.InkSoft.copy(alpha = 0.40f)
        ),
        border = BorderStroke(1.dp, CultivationPalette.Gold.copy(alpha = 0.42f))
    ) {
        Icon(
            if (decrease) Icons.Filled.Remove else Icons.Filled.Add,
            contentDescription = stringResource(
                if (decrease) R.string.focus_plan_decrease else R.string.focus_plan_increase
            )
        )
    }
}

@Composable
private fun PlanPreview(targetMinutes: Int, segmentMinutes: Int) {
    val preview = FocusPlanLogic.segmentsFor(targetMinutes, segmentMinutes)
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = CultivationPalette.JadeLight.copy(alpha = 0.16f)
    ) {
        Text(
            stringResource(R.string.focus_plan_preview, preview.size, preview.joinToString(" / ")),
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            style = MaterialTheme.typography.bodySmall,
            color = CultivationPalette.Jade,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun PlanSummary(
    targetMinutes: Int,
    segmentMinutes: Int,
    restMinutes: Int,
    strictMode: Boolean,
    presenceEnabled: Boolean
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = CultivationPalette.JadeLight.copy(alpha = 0.13f)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                stringResource(R.string.focus_plan_summary_title),
                fontWeight = FontWeight.Bold,
                color = CultivationPalette.Ink
            )
            Text(stringResource(R.string.focus_plan_target, targetMinutes),
                style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.focus_plan_segment, segmentMinutes),
                style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.focus_plan_rest, restMinutes),
                style = MaterialTheme.typography.bodySmall)
            Text(
                stringResource(
                    if (strictMode) R.string.focus_plan_summary_strict
                    else R.string.focus_plan_summary_normal
                ),
                style = MaterialTheme.typography.bodySmall,
                color = CultivationPalette.Jade,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                stringResource(
                    if (presenceEnabled) R.string.focus_plan_summary_presence_on
                    else R.string.focus_plan_summary_presence_off
                ),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
