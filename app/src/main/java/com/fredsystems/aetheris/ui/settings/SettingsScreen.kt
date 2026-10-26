@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.fredsystems.aetheris.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fredsystems.aetheris.domain.model.EngineMode
import com.fredsystems.aetheris.domain.model.EngineOptions
import com.fredsystems.aetheris.viewmodel.GameViewModel

@Composable
fun SettingsScreen(
    viewModel: GameViewModel,
    onCheckUpdate: () -> Unit,
    onOpenSim: () -> Unit,
    onOpenNotebook: () -> Unit,
    onOpenExternal: () -> Unit
) {
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
            "The Grandmaster" -> { aggression = 40f; positional = 80f; complexity = 30f; solidity = 80f; mode = EngineMode.DUAL_CORE }
            "The Maverick" -> { aggression = 70f; positional = 30f; complexity = 90f; solidity = 20f; mode = EngineMode.PURE_NNUE }
            "The Iron Wall" -> { aggression = 10f; positional = 90f; complexity = 10f; solidity = 100f; mode = EngineMode.PURE_CLASSICAL }
            "The Tal Prodigy" -> { aggression = 100f; positional = 20f; complexity = 100f; solidity = 0f; mode = EngineMode.PURE_NNUE }
        }
    }

    fun resetAll() {
        val d = EngineOptions()
        aggression = d.aggression.toFloat()
        positional = d.positional.toFloat()
        complexity = d.complexity.toFloat()
        solidity = d.solidity.toFloat()
        depth = d.depth.toFloat()
        mode = d.mode
        viewModel.resetOptions()
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp)
    ) {
        Text("Engine Settings", fontSize = 28.sp, fontWeight = FontWeight.Black, color = Color(0xFFF2EEFF))
        Text("Shape Aetheris into your perfect opponent.", fontSize = 13.sp, color = Color(0xFF9B93B8))
        Spacer(Modifier.height(18.dp))

        Surface(shape = RoundedCornerShape(20.dp), color = Color(0xFF1A1626)) {
            Column(Modifier.padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Persona Presets", fontWeight = FontWeight.SemiBold, color = Color(0xFFF2EEFF))
                    Box {
                        OutlinedButton(onClick = { presetExpanded = true }, shape = RoundedCornerShape(12.dp)) {
                            Text("Choose")
                        }
                        DropdownMenu(expanded = presetExpanded, onDismissRequest = { presetExpanded = false }) {
                            listOf("The Grandmaster", "The Maverick", "The Iron Wall", "The Tal Prodigy").forEach { p ->
                                DropdownMenuItem(text = { Text(p) }, onClick = { applyPreset(p); presetExpanded = false })
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("Engine Mode", color = Color(0xFFB9B2CE), fontSize = 12.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ModeChip("Dual", mode == EngineMode.DUAL_CORE, Modifier.weight(1f)) { mode = EngineMode.DUAL_CORE }
                    ModeChip("NNUE", mode == EngineMode.PURE_NNUE, Modifier.weight(1f)) { mode = EngineMode.PURE_NNUE }
                    ModeChip("Classical", mode == EngineMode.PURE_CLASSICAL, Modifier.weight(1f)) { mode = EngineMode.PURE_CLASSICAL }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        PersonalitySlider("Aggression", "Defense", aggression) { aggression = it }
        PersonalitySlider("Positional", "Tactical", positional) { positional = it }
        PersonalitySlider("Complexity", "Simplification", complexity) { complexity = it }
        PersonalitySlider("Solidity", "Activity", solidity) { solidity = it }

        Surface(shape = RoundedCornerShape(20.dp), color = Color(0xFF1A1626), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Search Depth: ${depth.toInt()}", color = Color(0xFFF2EEFF), fontWeight = FontWeight.SemiBold)
                Slider(value = depth, onValueChange = { depth = it }, valueRange = 1f..24f)
            }
        }

        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = {
                    viewModel.updateOptions(
                        EngineOptions(aggression.toInt(), positional.toInt(), complexity.toInt(), solidity.toInt(), opts.materialWeight, depth.toInt(), mode)
                    )
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp)
            ) { Text("Apply") }
            OutlinedButton(onClick = { resetAll() }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                Text("Reset Defaults")
            }
        }

        Spacer(Modifier.height(20.dp))
        Text("More", color = Color(0xFF9B93B8), fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))
        MenuRow("Engine vs Engine Simulation", onOpenSim)
        MenuRow("Game Notebook", onOpenNotebook)
        MenuRow("External Engines", onOpenExternal)
        MenuRow("Check for Updates", onCheckUpdate)

        Spacer(Modifier.height(30.dp))
        Text(
            "Developed by Fred-Systems",
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF6F6887)
        )
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun ModeChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(11.dp),
        color = if (selected) Color(0xFF7C4DFF) else Color(0xFF262136),
        onClick = onClick
    ) {
        Text(
            label,
            modifier = Modifier.padding(vertical = 10.dp),
            textAlign = TextAlign.Center,
            color = if (selected) Color.White else Color(0xFFB9B2CE),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun PersonalitySlider(left: String, right: String, value: Float, onChange: (Float) -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = Color(0xFF1A1626), modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(left, color = Color(0xFFF2EEFF), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text("${value.toInt()}", color = Color(0xFFB9A8FF), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(right, color = Color(0xFF9B93B8), fontSize = 13.sp)
            }
            Slider(value = value, onValueChange = onChange, valueRange = 0f..100f)
        }
    }
}

@Composable
private fun MenuRow(label: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF1A1626),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        onClick = onClick
    ) {
        Text(label, Modifier.padding(15.dp), color = Color(0xFFE7E1FA), fontSize = 14.sp)
    }
}
