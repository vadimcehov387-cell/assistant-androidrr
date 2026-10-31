package ru.vkassistant.mobile.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val BrandBlue = Color(0xFF2878F0)
private val BrandBlueDark = Color(0xFF1558C0)
private val BrandPale = Color(0xFFE7F0FF)
private val Ink = Color(0xFF152238)
private val Canvas = Color(0xFFF4F7FC)
private val Muted = Color(0xFF64748B)
private val Violet = Color(0xFF7865D8)

private val LightColors = lightColorScheme(
    primary = BrandBlue,
    onPrimary = Color.White,
    primaryContainer = BrandPale,
    onPrimaryContainer = BrandBlueDark,
    secondary = Violet,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEDE9FF),
    onSecondaryContainer = Color(0xFF34256E),
    tertiary = Color(0xFF168C83),
    background = Canvas,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Color(0xFFE9EEF6),
    onSurfaceVariant = Muted,
    outline = Color(0xFFD6DFEB)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF91B9FF),
    onPrimary = Color(0xFF062E69),
    primaryContainer = Color(0xFF174A91),
    onPrimaryContainer = Color(0xFFDCE9FF),
    secondary = Color(0xFFC1B5FF),
    onSecondary = Color(0xFF30236B),
    secondaryContainer = Color(0xFF493C85),
    onSecondaryContainer = Color(0xFFE9E3FF),
    tertiary = Color(0xFF78D4C8),
    background = Color(0xFF0B1422),
    onBackground = Color(0xFFE7EDF7),
    surface = Color(0xFF142135),
    onSurface = Color(0xFFE7EDF7),
    surfaceVariant = Color(0xFF26364D),
    onSurfaceVariant = Color(0xFFB1C0D4),
    outline = Color(0xFF43536A)
)

@Composable
fun AssistantTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
