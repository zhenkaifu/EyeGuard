package com.eyeguard.app.feature.focus

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.eyeguard.app.R
import kotlin.math.cos
import kotlin.math.sin

/**
 * 美术立绘与程序动画的混合角色。
 *
 * 七境均有透明底水墨立绘；炼气飘带使用独立 PNG，其他境界使用整层微动。
 * 身体呼吸、法阵、粒子和状态切换全部由 Compose 驱动，不依赖循环视频。
 */
@Composable
fun TaoistAvatar(
    realmIndex: Int,
    layer: Int,
    phase: String,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "taoistArt")
    val animatedBreath by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2_800), RepeatMode.Reverse),
        label = "breath"
    )
    val animatedRotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(24_000, easing = LinearEasing)),
        label = "runeRing"
    )
    val animatedSway by transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3_600), RepeatMode.Reverse),
        label = "ribbons"
    )
    val animatedMotes by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(7_000, easing = LinearEasing)),
        label = "motes"
    )
    val paused = phase in listOf(FocusPhase.PRESENCE_PAUSED, FocusPhase.PLAN_PAUSED)
    val channeling = phase in listOf(FocusPhase.ACTIVE, FocusPhase.COMPLETE)
    val motion = !reduceMotion && !paused
    val breath = if (motion) animatedBreath else 0.35f
    val rotation = if (motion && channeling) animatedRotation else 18f
    val sway = if (motion) animatedSway else 0f
    val motes = if (motion) animatedMotes else 0.45f
    val description = stringResource(R.string.focus_taoist_description)

    BoxWithConstraints(modifier.semantics { contentDescription = description }) {
        val side = minOf(maxWidth, maxHeight)
        val realm = realmIndex.coerceIn(0, 6)
        val safeLayer = layer.coerceIn(1, 10)
        val palette = realmPalette(realm)

        Canvas(Modifier.fillMaxSize()) {
            val unit = side.toPx() / 100f
            val cx = size.width / 2f
            val cy = size.height / 2f
            val center = Offset(cx, cy)
            val auraAlpha = when {
                paused -> 0.12f
                phase == FocusPhase.RESTING || phase == FocusPhase.REST_READY -> 0.22f
                phase == FocusPhase.COMPLETE -> 0.82f
                channeling -> 0.44f + safeLayer * 0.025f
                else -> 0.30f + safeLayer * 0.016f
            }

            drawOval(
                Color(0xFF173C36).copy(alpha = 0.13f),
                Offset(cx - 31f * unit, cy + 37f * unit),
                Size(62f * unit, 11f * unit)
            )
            drawOval(
                palette.accent.copy(alpha = auraAlpha * 0.22f),
                Offset(cx - 35f * unit, cy + 33f * unit),
                Size(70f * unit, 16f * unit)
            )
            drawCircle(
                Brush.radialGradient(
                    listOf(palette.accent.copy(alpha = auraAlpha * 0.30f), Color.Transparent)
                ),
                47f * unit,
                center
            )
            drawCircle(
                palette.accent.copy(alpha = auraAlpha * 0.45f),
                43f * unit,
                center,
                style = Stroke(0.38f * unit)
            )
            withTransform({ rotate(rotation, center) }) {
                drawArc(
                    palette.accent.copy(alpha = auraAlpha), 0f, 112f, false,
                    Offset(cx - 46f * unit, cy - 46f * unit), Size(92f * unit, 92f * unit),
                    style = Stroke(0.65f * unit, cap = StrokeCap.Round)
                )
                drawArc(
                    palette.accent.copy(alpha = auraAlpha), 180f, 112f, false,
                    Offset(cx - 46f * unit, cy - 46f * unit), Size(92f * unit, 92f * unit),
                    style = Stroke(0.65f * unit, cap = StrokeCap.Round)
                )
                repeat(10) { index ->
                    val angle = Math.toRadians((index * 36.0) - 90.0)
                    val inner = 44f * unit
                    val outer = (if (index < safeLayer) 47f else 45.5f) * unit
                    drawLine(
                        palette.accent.copy(
                            alpha = if (index < safeLayer) auraAlpha + 0.12f else auraAlpha * 0.35f
                        ),
                        Offset(center.x + cos(angle).toFloat() * inner,
                            center.y + sin(angle).toFloat() * inner),
                        Offset(center.x + cos(angle).toFloat() * outer,
                            center.y + sin(angle).toFloat() * outer),
                        (if (index < safeLayer) 0.8f else 0.4f) * unit,
                        StrokeCap.Round
                    )
                }
            }

            repeat(5 + safeLayer / 2) { index ->
                val count = 5 + safeLayer / 2
                val angle = Math.toRadians((rotation * 0.65f + index * 360.0 / count))
                val radius = (40f + index % 3 * 2f) * unit
                val lift = ((motes + index * 0.17f) % 1f) * 6f * unit
                val point = Offset(
                    center.x + cos(angle).toFloat() * radius,
                    center.y + sin(angle).toFloat() * radius * 0.80f - lift
                )
                drawCircle(
                    palette.accent.copy(alpha = (auraAlpha + 0.14f).coerceAtMost(0.9f)),
                    (0.48f + index % 3 * 0.15f) * unit,
                    point
                )
            }
        }

        // 身体与两条飘带共用呼吸变换，飘带再以各自的根部为轴轻摆。
        Box(
            Modifier.align(Alignment.Center).size(side).graphicsLayer {
                translationY = -breath * side.toPx() * 0.005f
                scaleX = 1f + breath * 0.006f
                scaleY = 1f + breath * 0.009f
                transformOrigin = TransformOrigin(0.5f, 0.96f)
            }
        ) {
            if (realm == 0) {
                Image(
                    painter = painterResource(R.drawable.taoist_qi_1_base),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
                Image(
                    painter = painterResource(R.drawable.taoist_qi_1_ribbon_left),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .offset(x = side * (40f / 1024f), y = side * (337f / 1024f))
                        .size(width = side * (294f / 1024f), height = side * (426f / 1024f))
                        .graphicsLayer {
                            rotationZ = sway * 1.6f
                            transformOrigin = TransformOrigin(0.97f, 0.04f)
                        }
                )
                Image(
                    painter = painterResource(R.drawable.taoist_qi_1_ribbon_right),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .offset(x = side * (648f / 1024f), y = side * (247f / 1024f))
                        .size(width = side * (262f / 1024f), height = side * (265f / 1024f))
                        .graphicsLayer {
                            rotationZ = -sway * 1.9f
                            transformOrigin = TransformOrigin(0.02f, 0.05f)
                        }
                )
            } else {
                val realmArt = when (realm) {
                    1 -> R.drawable.taoist_foundation
                    2 -> R.drawable.taoist_core
                    3 -> R.drawable.taoist_soul
                    4 -> R.drawable.taoist_spirit
                    5 -> R.drawable.taoist_tribulation
                    else -> R.drawable.taoist_ascended
                }
                Image(
                    painter = painterResource(realmArt),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        // 高境界保留统一呼吸，衣袂微转由整层极小幅度模拟。
                        rotationZ = sway * 0.18f
                        transformOrigin = TransformOrigin(0.5f, 0.85f)
                    }
                )
            }
        }
    }
}
