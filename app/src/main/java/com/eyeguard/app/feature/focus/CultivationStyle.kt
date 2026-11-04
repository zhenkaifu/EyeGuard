package com.eyeguard.app.feature.focus

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.eyeguard.app.R

/** 青绿仙侠视觉语言：低亮度青绿场景、宣纸面板与少量鎏金奖励反馈。 */
object CultivationPalette {
    val Ink = Color(0xFF173C36)
    val InkSoft = Color(0xFF47675E)
    val Jade = Color(0xFF2E7161)
    val JadeLight = Color(0xFF8FC6AA)
    val Paper = Color(0xFFF3F1E5)
    val Panel = Color(0xFFF8F6EA)
    val Gold = Color(0xFFC79B4A)
    val GoldLight = Color(0xFFF2D89A)
    val Cinnabar = Color(0xFFAA5A43)
    val Mist = Color(0xFFE6F0E7)
}

data class RealmPalette(
    val skyTop: Color,
    val skyBottom: Color,
    val mountainFar: Color,
    val mountainNear: Color,
    val robe: Color,
    val robeDark: Color,
    val accent: Color
)

fun realmPalette(index: Int): RealmPalette = when (index.coerceIn(0, 6)) {
    1 -> RealmPalette(Color(0xFFB9D8D6), Color(0xFFE5EEE3), Color(0xFF6B9995), Color(0xFF315E59),
        Color(0xFF4E8290), Color(0xFF285364), Color(0xFF8ED8E6))
    2 -> RealmPalette(Color(0xFFE5D6AE), Color(0xFFF2E9CF), Color(0xFFA18E60), Color(0xFF625D3C),
        Color(0xFF9B7438), Color(0xFF654923), Color(0xFFFFD36B))
    3 -> RealmPalette(Color(0xFFCFC4DF), Color(0xFFE9E1E9), Color(0xFF897D9B), Color(0xFF514D68),
        Color(0xFF75639C), Color(0xFF493B68), Color(0xFFC5A8FF))
    4 -> RealmPalette(Color(0xFFB9CBE0), Color(0xFFE2E9E8), Color(0xFF6C819F), Color(0xFF344E67),
        Color(0xFF405F91), Color(0xFF253D66), Color(0xFF91BFFF))
    5 -> RealmPalette(Color(0xFFCFC0D2), Color(0xFFE8DEDF), Color(0xFF82708E), Color(0xFF4A4053),
        Color(0xFF695079), Color(0xFF41334E), Color(0xFFC5A2E8))
    6 -> RealmPalette(Color(0xFFEBDDAF), Color(0xFFF5EFD8), Color(0xFFB8A77E), Color(0xFF70694F),
        Color(0xFFE2D5B3), Color(0xFFA3916E), Color(0xFFFFE6A1))
    else -> RealmPalette(Color(0xFFB8D4C5), Color(0xFFE7EDDE), Color(0xFF7D9E8E), Color(0xFF355F50),
        Color(0xFF6F8778), Color(0xFF3E5549), Color(0xFFA8D9B7))
}

@Composable
fun CultivationPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(
            containerColor = CultivationPalette.Panel.copy(alpha = 0.96f),
            contentColor = CultivationPalette.Ink
        ),
        border = BorderStroke(1.dp, CultivationPalette.Gold.copy(alpha = 0.24f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        content = content
    )
}

@Composable
fun CultivationPrimaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 52.dp),
        enabled = enabled,
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = CultivationPalette.Ink,
            contentColor = CultivationPalette.GoldLight,
            disabledContainerColor = CultivationPalette.InkSoft,
            disabledContentColor = CultivationPalette.Mist
        ),
        border = BorderStroke(1.dp, CultivationPalette.Gold.copy(alpha = 0.65f)),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
    ) { content() }
}

/** 水墨立绘底景＋轻量程序云雾，保持画风统一并支持减少动画。 */
@Composable
fun CaveBackground(
    realmIndex: Int,
    phase: String,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier
) {
    val palette = realmPalette(realmIndex)
    val transition = rememberInfiniteTransition(label = "cave")
    val animatedDrift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(14_000, easing = LinearEasing), RepeatMode.Reverse),
        label = "cloudDrift"
    )
    val animatedFlow by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(7_000, easing = LinearEasing)),
        label = "motes"
    )
    val drift = if (reduceMotion) 0.35f else animatedDrift
    val flow = if (reduceMotion) 0.5f else animatedFlow
    val active = phase == FocusPhase.ACTIVE || phase == FocusPhase.COMPLETE

    Box(modifier) {
        Image(
            painter = painterResource(R.drawable.cave_ink_background),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            // 极淡云雾只在远景流动，不遮盖人物和主要山水笔触。
            cloudBand(w, h, h * (0.44f + drift * 0.018f), w * (0.04f + drift * 0.04f),
                Color.White.copy(alpha = 0.10f))
            cloudBand(w, h, h * (0.61f - drift * 0.012f), -w * (0.02f + drift * 0.03f),
                CultivationPalette.Mist.copy(alpha = 0.10f))

            val moteCount = if (active) 8 else 3
            repeat(moteCount) { index ->
                val x = w * (0.12f + ((index * 0.217f + flow * 0.025f) % 0.76f))
                val y = h * (0.25f + ((index * 0.143f + flow * 0.04f) % 0.50f))
                drawCircle(palette.accent.copy(alpha = if (active) 0.38f else 0.16f),
                    (1.1f + index % 3 * 0.4f).dp.toPx(), Offset(x, y))
            }
            drawRect(
                Brush.verticalGradient(
                    listOf(Color.Transparent, CultivationPalette.Ink.copy(alpha = 0.09f)),
                    startY = h * 0.75f,
                    endY = h
                )
            )
        }
    }
}

private fun DrawScope.mountainLayer(
    width: Float,
    height: Float,
    peakY: Float,
    baseY: Float,
    color: Color,
    phase: Float
) {
    val path = Path().apply {
        moveTo(0f, height * baseY)
        lineTo(0f, height * (peakY + 0.17f))
        quadraticBezierTo(width * (0.10f + phase * 0.1f), height * (peakY + 0.04f), width * 0.22f, height * peakY)
        quadraticBezierTo(width * 0.31f, height * (peakY + 0.17f), width * 0.43f, height * (baseY - 0.08f))
        quadraticBezierTo(width * 0.58f, height * (peakY - 0.06f), width * 0.67f, height * (peakY + 0.08f))
        quadraticBezierTo(width * 0.79f, height * (peakY + 0.20f), width, height * (peakY + 0.02f))
        lineTo(width, height)
        lineTo(0f, height)
        close()
    }
    drawPath(path, color)
}

private fun DrawScope.cloudBand(width: Float, height: Float, y: Float, shift: Float, color: Color) {
    repeat(5) { index ->
        val left = shift + width * (index * 0.23f - 0.12f)
        drawOval(color, Offset(left, y + (index % 2) * height * 0.025f),
            Size(width * 0.36f, height * 0.085f))
    }
}

private fun DrawScope.pineBranch(
    origin: Offset,
    width: Float,
    height: Float,
    color: Color,
    mirrored: Boolean
) {
    val direction = if (mirrored) -1f else 1f
    drawLine(color, origin, origin + Offset(direction * width * 0.52f, height), width * 0.035f, StrokeCap.Round)
    repeat(4) { index ->
        val start = origin + Offset(direction * width * (0.12f + index * 0.10f), height * (0.24f + index * 0.17f))
        val sign = if (index % 2 == 0) 1f else -1f
        val end = start + Offset(direction * width * 0.45f, sign * height * 0.13f)
        drawLine(color, start, end, width * 0.025f, StrokeCap.Round)
        repeat(3) { needle ->
            val center = start + (end - start) * (0.40f + needle * 0.22f)
            drawCircle(color, width * 0.105f, center)
        }
    }
}
