package com.fredsystems.aetheris.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7C4DFF),
    secondary = Color(0xFF03DAC6),
    background = Color(0xFF0D0D12),
    surface = Color(0xFF1A1A24),
    onSurface = Color(0xFFE6E6F0)
)

@Composable
fun AetherisTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColors, content = content)
}