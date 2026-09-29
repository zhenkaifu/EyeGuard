package com.eyeguard.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF2E6B57),
    secondary = Color(0xFF4E9F7B),
    tertiary = Color(0xFF7FBFA0)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7FBFA0),
    secondary = Color(0xFF4E9F7B),
    tertiary = Color(0xFF2E6B57)
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
