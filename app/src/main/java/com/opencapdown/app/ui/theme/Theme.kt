package com.opencapdown.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF6366F1), // Indigo
    secondary = Color(0xFF06B6D4), // Cyan
    tertiary = Color(0xFFEC4899), // Pink
    background = Color(0xFF0B0C10), // Rich Dark Black
    surface = Color(0xFF161B22), // Deep Slate Gray
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFFF3F4F6),
    onSurface = Color(0xFFE5E7EB),
    surfaceVariant = Color(0xFF21262D)
)

@Composable
fun OpenCapDownTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
