package com.fredsystems.aetheris.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9C7CFF),
    onPrimary = Color(0xFF140B2E),
    primaryContainer = Color(0xFF3B2A72),
    onPrimaryContainer = Color(0xFFE9E2FF),
    secondary = Color(0xFF7DE2D1),
    onSecondary = Color(0xFF00382F),
    background = Color(0xFF0E0C14),
    onBackground = Color(0xFFF2EEFF),
    surface = Color(0xFF1A1626),
    onSurface = Color(0xFFF2EEFF),
    surfaceVariant = Color(0xFF262136),
    onSurfaceVariant = Color(0xFFB9B2CE),
    outline = Color(0xFF4A4263),
    error = Color(0xFFFF6B6B)
)

private val AetherisShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun AetherisTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColors, shapes = AetherisShapes, typography = Typography, content = content)
}
