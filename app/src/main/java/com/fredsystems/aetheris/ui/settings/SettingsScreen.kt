package com.fredsystems.aetheris.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fredsystems.aetheris.domain.model.EngineMode
import com.fredsystems.aetheris.domain.model.EngineOptions
import com.fredsystems.aetheris.viewmodel.GameViewModel

@Composable
fun SettingsScreen(viewModel: GameViewModel, onCheckUpdate: () -> Unit) {
    val opts by viewModel.options
    var aggression by remember { mutableStateOf(opts.aggression.toFloat()) }
    var material by remember { mutableStateOf(opts.materialWeight.toFloat()) }
    var depth by remember { mutableStateOf(opts.depth.toFloat()) }
    var mode by remember { mutableStateOf(opts.mode) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Engine Settings", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        Text("Aggression: ${aggression.toInt()}")
        Slider(value = aggression, onValueChange = { aggression = it }, valueRange = 0f..100f)
        Text("Material Weight: ${material.toInt()}")
        Slider(value = material, onValueChange = { material = it }, valueRange = 0f..100f)
        Text("Depth: ${depth.toInt()}")
        Slider(value = depth, onValueChange = { depth = it }, valueRange = 1f..24f)
        Text("Engine Mode")
        Row {
            FilterChip(selected = mode == EngineMode.NNUE, onClick = { mode = EngineMode.NNUE }, label = { Text("Aetheris NNUE") })
            Spacer(Modifier.width(8.dp))
            FilterChip(selected = mode == EngineMode.CLASSICAL, onClick = { mode = EngineMode.CLASSICAL }, label = { Text("Aetheris Classical") })
        }
        Button(onClick = {
            viewModel.updateOptions(EngineOptions(aggression.toInt(), material.toInt(), opts.useOpeningBook, depth.toInt(), mode))
        }) { Text("Apply") }
        Spacer(Modifier.height(12.dp))
        Button(onClick = onCheckUpdate) { Text("Check for Updates") }
        Spacer(Modifier.weight(1f))
        Text(
            "Developed by Fred-Systems",
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall
        )
    }
}