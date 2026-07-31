package com.herolens.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB84D),
    secondary = Color(0xFF67E8F9),
    background = Color(0xFF10131A),
    surface = Color(0xFF171C26),
    surfaceVariant = Color(0xFF232B38)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF9A5200),
    secondary = Color(0xFF00677A),
    background = Color(0xFFF7F8FB),
    surface = Color.White,
    surfaceVariant = Color(0xFFE8EDF4)
)

@Composable
fun HeroLensTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content
    )
}
