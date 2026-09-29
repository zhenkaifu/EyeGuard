package com.eyeguard.app.feature.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.eyeguard.app.core.database.DailyUsageEntity
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/**
 * 最近 7 天用眼时长柱状图（Compose Canvas 自绘，无第三方图表库）。
 *
 * 柱高按当日累计亮屏时长等比缩放， today's 柱使用主题主色，
 * 其余使用 tertiary 色；横轴标注星期（跟随系统/应用语言自动本地化）。
 */
@Composable
fun WeeklyBarChart(
    stats: List<DailyUsageEntity>,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    // 应用语言由 attachBaseContext 注入配置，跟随当前配置取显示语言
    val displayLocale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()

    Canvas(modifier = modifier
        .fillMaxWidth()
        .height(140.dp)) {
        if (stats.isEmpty()) return@Canvas

        val maxMs = stats.maxOf { it.totalScreenTimeMs }.coerceAtLeast(1L)
        val barAreaHeight = size.height * 0.8f
        val barWidth = size.width / (stats.size * 2f)
        val slotWidth = size.width / stats.size

        stats.forEachIndexed { index, entity ->
            val barHeight =
                (entity.totalScreenTimeMs.toFloat() / maxMs) * barAreaHeight
            val left = slotWidth * index + (slotWidth - barWidth) / 2f
            val color = if (index == stats.size - 1) primaryColor else secondaryColor

            drawRect(
                color = color,
                topLeft = Offset(left, size.height - barHeight),
                size = Size(barWidth, barHeight)
            )

            // 星期标注
            val dayLabel = dayOfWeekLabel(entity.date, displayLocale)
            val labelPaint = android.graphics.Paint().apply {
                setColor(android.graphics.Color.argb(
                    255,
                    (labelColor.red * 255).toInt(),
                    (labelColor.green * 255).toInt(),
                    (labelColor.blue * 255).toInt()
                ))
                textSize = 24f
                textAlign = android.graphics.Paint.Align.CENTER
            }
            drawContext.canvas.nativeCanvas.drawText(
                dayLabel,
                left + barWidth / 2f,
                size.height - 8f,
                labelPaint
            )
        }
    }
}

/** 由 yyyy-MM-dd 取星期标注，按指定语言本地化（周一 = Monday 等）。 */
private fun dayOfWeekLabel(date: String, locale: Locale): String {
    val dayOfWeek = runCatching {
        java.time.LocalDate.parse(date).dayOfWeek
    }.getOrDefault(DayOfWeek.MONDAY)
    return dayOfWeek.getDisplayName(TextStyle.NARROW, locale)
}
