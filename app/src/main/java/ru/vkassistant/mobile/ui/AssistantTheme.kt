package ru.vkassistant.mobile.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Navy = Color(0xFF14233D)
private val Blue = Color(0xFF4778E8)
private val PaleBlue = Color(0xFFDCE7FF)
private val Ink = Color(0xFF17243A)
private val Canvas = Color(0xFFF5F7FC)

private val LightColors = lightColorScheme(
    primary = Blue,
    onPrimary = Color.White,
    primaryContainer = PaleBlue,
    onPrimaryContainer = Navy,
    secondary = Color(0xFF526783),
    background = Canvas,
    surface = Color.White,
    onSurface = Ink
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9DB8FF),
    onPrimary = Color(0xFF10264F),
    primaryContainer = Color(0xFF263F70),
    onPrimaryContainer = Color(0xFFDCE7FF),
    secondary = Color(0xFFB8C7E2),
    background = Color(0xFF0E1624),
    surface = Color(0xFF172235),
    onSurface = Color(0xFFE8EEF9)
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
