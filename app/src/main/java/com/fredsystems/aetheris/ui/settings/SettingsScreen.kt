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
fun SettingsScreen(viewModel: GameViewModel, onCheckUpdate: () -> Unit, onOpenSim: () -> Unit, onOpenNotebook: () -> Unit, onOpenExternal: () -> Unit) {
    val opts by viewModel.options
    var aggression by remember { mutableStateOf(opts.aggression.toFloat()) }
    var positional by remember { mutableStateOf(opts.positional.toFloat()) }
    var complexity by remember { mutableStateOf(opts.complexity.toFloat()) }
    var solidity by remember { mutableStateOf(opts.solidity.toFloat()) }
    var depth by remember { mutableStateOf(opts.depth.toFloat()) }
    var mode by remember { mutableStateOf(opts.mode) }
    var presetExpanded by remember { mutableStateOf(false) }

    fun applyPreset(name: String) {
        when (name) {
            "The Grandmaster" -> { aggression=40f; positional=80f; complexity=30f; solidity=80f; mode=EngineMode.DUAL_CORE }
            "The Maverick" -> { aggression=70f; positional=30f; complexity=90f; solidity=20f; mode=EngineMode.PURE_NNUE }
            "The Iron Wall" -> { aggression=10f; positional=90f; complexity=10f; solidity=100f; mode=EngineMode.PURE_CLASSICAL }
            "The Tal Prodigy" -> { aggression=100f; positional=20f; complexity=100f; solidity=0f; mode=EngineMode.PURE_NNUE }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Engine Settings", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        // Presets
        Box {
            OutlinedButton(onClick = { presetExpanded = true }) { Text("Persona Presets") }
            DropdownMenu(expanded = presetExpanded, onDismissRequest = { presetExpanded = false }) {
                listOf("The Grandmaster","The Maverick","The Iron Wall","The Tal Prodigy").forEach { p ->
                    DropdownMenuItem(text = { Text(p) }, onClick = { applyPreset(p); presetExpanded = false })
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("Engine Mode")
        Row {
            FilterChip(selected = mode == EngineMode.DUAL_CORE, onClick = { mode = EngineMode.DUAL_CORE }, label = { Text("Dual-Core") })
            Spacer(Modifier.width(4.dp))
            FilterChip(selected = mode == EngineMode.PURE_NNUE, onClick = { mode = EngineMode.PURE_NNUE }, label = { Text("Pure NNUE") })
            Spacer(Modifier.width(4.dp))
            FilterChip(selected = mode == EngineMode.PURE_CLASSICAL, onClick = { mode = EngineMode.PURE_CLASSICAL }, label = { Text("Pure Classical") })
        }
        Spacer(Modifier.height(8.dp))
        Text("Aggression vs Defense: ${aggression.toInt()}")
        Slider(value = aggression, onValueChange = { aggression = it }, valueRange = 0f..100f)
        Text("Positional vs Tactical: ${positional.toInt()}")
        Slider(value = positional, onValueChange = { positional = it }, valueRange = 0f..100f)
        Text("Complexity vs Simplification: ${complexity.toInt()}")
        Slider(value = complexity, onValueChange = { complexity = it }, valueRange = 0f..100f)
        Text("Solidity vs Activity: ${solidity.toInt()}")
        Slider(value = solidity, onValueChange = { solidity = it }, valueRange = 0f..100f)
        Text("Depth: ${depth.toInt()}")
        Slider(value = depth, onValueChange = { depth = it }, valueRange = 1f..24f)
        Button(onClick = {
            viewModel.updateOptions(EngineOptions(aggression.toInt(), positional.toInt(), complexity.toInt(), solidity.toInt(), opts.materialWeight, depth.toInt(), mode))
        }) { Text("Apply") }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onCheckUpdate) { Text("Check for Updates") }
        Button(onClick = onOpenSim) { Text("Engine vs Engine Simulation") }
        Button(onClick = onOpenNotebook) { Text("Game Notebook") }
        Button(onClick = onOpenExternal) { Text("External Engines") }
        Spacer(Modifier.weight(1f))
        Text("Developed by Fred-Systems", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
    }
}