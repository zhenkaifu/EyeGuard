package com.eyeguard.app.feature.focus

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.eyeguard.app.R
import com.eyeguard.app.feature.focus.data.FocusSessionEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun CultivationHero(
    state: FocusUiState,
    level: FocusProgression.Level,
    realmName: String,
    usageAccessAvailable: Boolean
) {
    val primaryStatus = when (state.phase) {
        FocusPhase.ACTIVE, FocusPhase.RESTING -> formatFocusTime(
            if (state.phase == FocusPhase.ACTIVE) state.remainingMs else state.restRemainingMs
        )
        FocusPhase.PRESENCE_PAUSED -> stringResource(if (usageAccessAvailable)
            R.string.focus_status_presence_paused else R.string.focus_usage_access_lost_title)
        FocusPhase.PLAN_PAUSED -> stringResource(R.string.focus_status_plan_paused)
        FocusPhase.REST_READY -> stringResource(R.string.focus_rest_ready_title)
        FocusPhase.COMPLETE -> stringResource(R.string.focus_daily_complete_title)
        else -> stringResource(R.string.focus_idle_hint)
    }
    val secondaryStatus = when (state.phase) {
        FocusPhase.ACTIVE -> stringResource(if (state.activeStrictMode)
            R.string.focus_strict_reward_active else R.string.focus_normal_reward_active)
        FocusPhase.RESTING -> stringResource(R.string.focus_resting_title)
        FocusPhase.PRESENCE_PAUSED -> stringResource(R.string.focus_presence_paused_body)
        FocusPhase.PLAN_PAUSED -> stringResource(R.string.focus_plan_paused_body)
        else -> stringResource(R.string.focus_realm_layer_progress, level.layer, level.progress * 100)
    }
    val ringProgress = when (state.phase) {
        FocusPhase.ACTIVE -> state.elapsedFraction
        FocusPhase.RESTING -> if (state.planRestMinutes > 0) {
            1f - (state.restRemainingMs / (state.planRestMinutes * 60_000f)).coerceIn(0f, 1f)
        } else 0f
        else -> level.progress
    }
    CultivationScene(
        realmIndex = level.realmIndex,
        layer = level.layer,
        phase = state.phase,
        reduceMotion = state.reduceMotion,
        stageTitle = stringResource(R.string.focus_current_stage, realmName, level.layer),
        stageSubtitle = stringResource(R.string.focus_xp_clarity_value, state.xp, state.clarityPoints),
        primaryStatus = primaryStatus,
        secondaryStatus = secondaryStatus,
        progress = ringProgress,
        modifier = Modifier.fillMaxWidth()
    )
}

/** 首页与修炼页共用的动态洞府，保证两处不是两套割裂的美术。 */
@Composable
fun CultivationScene(
    realmIndex: Int,
    layer: Int,
    phase: String,
    reduceMotion: Boolean,
    stageTitle: String,
    stageSubtitle: String,
    primaryStatus: String,
    secondaryStatus: String,
    progress: Float,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onClick: (() -> Unit)? = null
) {
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val effectiveReduceMotion = reduceMotion || !lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    val shape = RoundedCornerShape(30.dp)
    val border = BorderStroke(1.dp, CultivationPalette.Gold.copy(alpha = 0.38f))
    val content: @Composable () -> Unit = {
        Box(Modifier.fillMaxSize()) {
            CaveBackground(realmIndex, phase, effectiveReduceMotion, Modifier.fillMaxSize())
            Column(
                Modifier.fillMaxSize().padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = CultivationPalette.Panel.copy(alpha = 0.86f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.46f))
                    ) {
                        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                            Text(stageTitle, style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold, color = CultivationPalette.Ink)
                            Text(stageSubtitle, style = MaterialTheme.typography.labelMedium,
                                color = CultivationPalette.InkSoft)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Surface(
                        shape = CircleShape,
                        color = CultivationPalette.Ink.copy(alpha = 0.82f)
                    ) {
                        Text(
                            stringResource(R.string.focus_ten_layers),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = CultivationPalette.GoldLight
                        )
                    }
                }
                Box(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Box(Modifier.size(238.dp), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.fillMaxSize()) {
                            val stroke = 3.dp.toPx()
                            val inset = 9.dp.toPx()
                            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
                            drawArc(Color.White.copy(alpha = 0.35f), 0f, 360f, false,
                                Offset(inset, inset), arcSize, style = Stroke(stroke))
                            drawArc(realmPalette(realmIndex).accent.copy(alpha = 0.92f), -90f,
                                360f * progress.coerceIn(0f, 1f), false, Offset(inset, inset), arcSize,
                                style = Stroke(stroke * 1.5f, cap = StrokeCap.Round))
                        }
                        TaoistAvatar(realmIndex, layer, phase, effectiveReduceMotion, Modifier.fillMaxSize())
                    }
                }
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = CultivationPalette.Panel.copy(alpha = 0.90f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.50f))
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            primaryStatus,
                            style = if (phase in listOf(FocusPhase.ACTIVE, FocusPhase.RESTING)) {
                                MaterialTheme.typography.headlineSmall
                            } else MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            color = CultivationPalette.Ink,
                            maxLines = 2
                        )
                        Text(secondaryStatus, style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center, color = CultivationPalette.InkSoft,
                            maxLines = 2)
                        if (actionLabel != null) {
                            Text(actionLabel, style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold, color = CultivationPalette.Jade,
                                modifier = Modifier.padding(top = 3.dp))
                        }
                    }
                }
            }
        }
    }
    if (onClick == null) {
        Card(modifier.height(420.dp), shape = shape, border = border,
            elevation = CardDefaults.cardElevation(defaultElevation = 5.dp)) { content() }
    } else {
        Card(onClick = onClick, modifier = modifier.height(380.dp), shape = shape, border = border,
            elevation = CardDefaults.cardElevation(defaultElevation = 5.dp)) { content() }
    }
}

@Composable
fun FocusStatCard(value: String, label: String, modifier: Modifier = Modifier) {
    Card(
        modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CultivationPalette.Panel,
            contentColor = CultivationPalette.Ink),
        border = BorderStroke(1.dp, CultivationPalette.Gold.copy(alpha = 0.20f))
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 15.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center)
            Text(label, style = MaterialTheme.typography.bodySmall, color = CultivationPalette.InkSoft)
        }
    }
}

@Composable
fun FocusHistoryRow(session: FocusSessionEntity) {
    val date = remember(session.endedAt) {
        SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(session.endedAt))
    }
    val success = session.status == FocusSessionEntity.COMPLETED
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(10.dp).background(
                if (success) CultivationPalette.Gold else CultivationPalette.InkSoft.copy(alpha = 0.40f),
                CircleShape
            )
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(stringResource(when (session.status) {
                FocusSessionEntity.COMPLETED -> R.string.focus_history_completed
                FocusSessionEntity.DISTRACTED -> R.string.focus_history_distracted
                FocusSessionEntity.SHELVED -> R.string.focus_history_shelved
                else -> R.string.focus_history_abandoned
            }), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(R.string.focus_history_details, date, session.actualMinutes,
                    session.plannedMinutes, session.restCount),
                style = MaterialTheme.typography.bodySmall,
                color = CultivationPalette.InkSoft
            )
            if (session.goal.isNotBlank()) {
                Text(session.goal, style = MaterialTheme.typography.bodySmall,
                    color = CultivationPalette.InkSoft)
            }
        }
        Text(
            if (session.xpEarned > 0) stringResource(R.string.focus_history_xp, session.xpEarned)
            else stringResource(R.string.focus_history_no_xp),
            color = if (session.xpEarned > 0) CultivationPalette.Jade else CultivationPalette.InkSoft,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/** 突破与结算共用仪式：缓慢旋转的星芒、升腾灵气和角色新外观。 */
@Composable
fun BreakthroughCeremony(
    result: FocusCompletion,
    realmNames: List<String>,
    reduceMotion: Boolean,
    onDismiss: () -> Unit
) {
    val major = result.newRealm > result.previousRealm
    val haptic = LocalHapticFeedback.current
    val entrance = remember { Animatable(if (reduceMotion) 1f else 0.82f) }
    LaunchedEffect(Unit) {
        haptic.performHapticFeedback(if (major) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove)
        entrance.animateTo(1f, tween(if (reduceMotion) 0 else if (major) 850 else 520))
    }
    val transition = rememberInfiniteTransition(label = "ceremony")
    val animatedRotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(20_000, easing = LinearEasing)),
        label = "rays"
    )
    val rotation = if (reduceMotion) 0f else animatedRotation
    val palette = realmPalette(result.newRealm)
    val title = stringResource(when {
        result.dailyGoalCompleted -> R.string.focus_daily_complete_title
        major -> R.string.focus_breakthrough_title
        result.newLayer > result.previousLayer -> R.string.focus_layer_breakthrough_title
        else -> R.string.focus_complete_title
    })

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().graphicsLayer {
                scaleX = entrance.value
                scaleY = entrance.value
                alpha = entrance.value
            },
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(containerColor = CultivationPalette.Ink),
            border = BorderStroke(1.5.dp, CultivationPalette.GoldLight.copy(alpha = 0.70f))
        ) {
            Box {
                Canvas(Modifier.matchParentSize()) {
                    val center = Offset(size.width / 2f, size.height * 0.40f)
                    drawCircle(Brush.radialGradient(listOf(palette.accent.copy(alpha = 0.30f), Color.Transparent)),
                        size.width * 0.58f, center)
                    withTransform({ rotate(rotation, center) }) {
                        repeat(16) { index ->
                            val angle = Math.toRadians(index * 22.5)
                            drawLine(CultivationPalette.GoldLight.copy(alpha = if (index % 2 == 0) 0.18f else 0.08f),
                                center + Offset(cos(angle).toFloat(), sin(angle).toFloat()) * size.width * 0.15f,
                                center + Offset(cos(angle).toFloat(), sin(angle).toFloat()) * size.width * 0.48f,
                                1.dp.toPx(), StrokeCap.Round)
                        }
                    }
                }
                Column(
                    Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(shape = CircleShape, color = CultivationPalette.Gold.copy(alpha = 0.20f)) {
                        Text(stringResource(R.string.focus_complete_confirm), color = CultivationPalette.GoldLight,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp))
                    }
                    Text(title, style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold, color = CultivationPalette.GoldLight,
                        textAlign = TextAlign.Center)
                    TaoistAvatar(result.newRealm, result.newLayer, FocusPhase.COMPLETE,
                        reduceMotion, Modifier.fillMaxWidth().height(if (major) 246.dp else 204.dp))
                    Text(stringResource(R.string.focus_completion_xp, result.minutes, result.xpEarned),
                        color = CultivationPalette.Paper, textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyLarge)
                    if (major || result.newLayer != result.previousLayer) {
                        Text(stringResource(R.string.focus_completion_stage, realmNames[result.newRealm], result.newLayer),
                            color = palette.accent, fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium)
                    }
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CultivationPalette.Gold,
                            contentColor = CultivationPalette.Ink)
                    ) {
                        Text(stringResource(R.string.focus_complete_confirm), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun realmNames(): List<String> = listOf(
    stringResource(R.string.realm_qi),
    stringResource(R.string.realm_foundation),
    stringResource(R.string.realm_core),
    stringResource(R.string.realm_soul),
    stringResource(R.string.realm_spirit),
    stringResource(R.string.realm_tribulation),
    stringResource(R.string.realm_ascended)
)

fun formatFocusTime(milliseconds: Long): String {
    val seconds = (milliseconds / 1_000L).coerceAtLeast(0L)
    return "%02d:%02d".format(seconds / 60, seconds % 60)
}
