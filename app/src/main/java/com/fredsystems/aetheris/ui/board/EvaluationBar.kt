package com.fredsystems.aetheris.ui.board

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun EvaluationBar(score: Int, modifier: Modifier = Modifier) {
    val clamped = score.coerceIn(-1000, 1000)
    val whiteFraction = (clamped + 1000) / 2000f
    Column(modifier = modifier.width(24.dp).fillMaxHeight()) {
        Box(modifier = Modifier.weight(1f - whiteFraction).fillMaxWidth().background(Color(0xFF222222)))
        Box(modifier = Modifier.weight(whiteFraction).fillMaxWidth().background(Color(0xFFEEEEEE)))
    }
    Text(text = (score / 100f).toString(), modifier = Modifier.padding(4.dp))
}