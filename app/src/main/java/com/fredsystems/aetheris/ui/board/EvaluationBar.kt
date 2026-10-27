package com.fredsystems.aetheris.ui.board

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.tanh

@Composable
fun EvaluationBar(score: Int, modifier: Modifier = Modifier) {
    // Logistic-like mapping keeps small advantages visible without saturating too early.
    val whiteFraction = (0.5 + 0.5 * tanh(score.coerceIn(-4000, 4000) / 400.0)).toFloat().coerceIn(0.01f, 0.99f)
    val fraction by animateFloatAsState(targetValue = whiteFraction, animationSpec = tween(350), label = "eval")
    val label = when {
        abs(score) >= 90000 -> if (score > 0) "M+" else "M-"
        else -> String.format("%+.1f", score / 100f)
    }
    Column(
        modifier = modifier
            .width(22.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF0B0A10)),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .weight(1f - fraction)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color(0xFF2B2838), Color(0xFF14121C))))
        )
        Box(
            modifier = Modifier
                .weight(fraction)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color(0xFFF4F1EA), Color(0xFFD8D2C4))))
        )
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFB8B3C7),
            modifier = Modifier.padding(vertical = 4.dp)
        )
    }
}
