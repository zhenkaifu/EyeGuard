package com.eyeguard.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF2E7161),
    onPrimary = Color(0xFFF8F6EA),
    primaryContainer = Color(0xFFD9E8DA),
    onPrimaryContainer = Color(0xFF173C36),
    secondary = Color(0xFF8B6B35),
    onSecondary = Color(0xFFF8F6EA),
    secondaryContainer = Color(0xFFF0E2BC),
    onSecondaryContainer = Color(0xFF4A3C24),
    tertiary = Color(0xFFAA5A43),
    background = Color(0xFFF3F1E5),
    onBackground = Color(0xFF173C36),
    surface = Color(0xFFF8F6EA),
    onSurface = Color(0xFF173C36),
    surfaceVariant = Color(0xFFE5E9DC),
    onSurfaceVariant = Color(0xFF47675E),
    outline = Color(0xFF9DAB97)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9BD1B6),
    onPrimary = Color(0xFF12352E),
    primaryContainer = Color(0xFF28574B),
    onPrimaryContainer = Color(0xFFE1EDDD),
    secondary = Color(0xFFE2C47E),
    onSecondary = Color(0xFF3B301C),
    secondaryContainer = Color(0xFF56472A),
    tertiary = Color(0xFFE0A58E),
    background = Color(0xFF132824),
    onBackground = Color(0xFFE8ECDD),
    surface = Color(0xFF1B332E),
    onSurface = Color(0xFFE8ECDD),
    surfaceVariant = Color(0xFF2B423B),
    onSurfaceVariant = Color(0xFFBACABD),
    outline = Color(0xFF758B7D)
)

/**
 * EyeGuard 全局 Material 3 主题（护眼绿色系）。
 */
@Composable
fun EyeGuardTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
