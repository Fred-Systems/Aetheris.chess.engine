package com.fredsystems.aetheris.ui.sim

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fredsystems.aetheris.ui.board.ChessBoard
import com.fredsystems.aetheris.ui.board.EvaluationBar
import com.fredsystems.aetheris.ui.theme.BoardThemes
import com.fredsystems.aetheris.viewmodel.SimViewModel

@Composable
fun SimScreen(viewModel: SimViewModel, onBack: () -> Unit, onOpenAnalysis: (com.fredsystems.aetheris.domain.model.GameRecord) -> Unit = {}) {
    val board by viewModel.boardState
    val eval by viewModel.eval
    val settings = com.fredsystems.aetheris.domain.model.SettingsStore(androidx.compose.ui.platform.LocalContext.current).load()
    var modeA by remember { mutableStateOf(viewModel.modeA) }
    var modeB by remember { mutableStateOf(viewModel.modeB) }
    var personaA by remember { mutableStateOf(viewModel.personaA) }
    var personaB by remember { mutableStateOf(viewModel.personaB) }
    var depthA by remember { mutableStateOf(viewModel.depthA.toFloat()) }
    var depthB by remember { mutableStateOf(viewModel.depthB.toFloat()) }
    var timeMs by remember { mutableStateOf(viewModel.timeMs.toFloat()) }
    var aggressionA by remember { mutableStateOf(viewModel.aggressionA.toFloat()) }
    var positionalA by remember { mutableStateOf(viewModel.positionalA.toFloat()) }
    var complexityA by remember { mutableStateOf(viewModel.complexityA.toFloat()) }
    var solidityA by remember { mutableStateOf(viewModel.solidityA.toFloat()) }
    var aggressionB by remember { mutableStateOf(viewModel.aggressionB.toFloat()) }
    var positionalB by remember { mutableStateOf(viewModel.positionalB.toFloat()) }
    var complexityB by remember { mutableStateOf(viewModel.complexityB.toFloat()) }
    var solidityB by remember { mutableStateOf(viewModel.solidityB.toFloat()) }
    var tuneA by remember { mutableStateOf(false) }
    var tuneB by remember { mutableStateOf(false) }
    var matchStarted by remember { mutableStateOf(false) }
    var showPgnDialog by remember { mutableStateOf(false) }
    var pgnDraft by remember { mutableStateOf("") }
    var matchComment by remember { mutableStateOf("") }
    LaunchedEffect(viewModel.running.value) { if (viewModel.running.value) matchStarted = true }

    if (showPgnDialog) {
        AlertDialog(
            onDismissRequest = { showPgnDialog = false },
            title = { Text("Engine Match PGN / Notebook") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Edit the movetext or full PGN before saving. Your edits are stored in the Notebook record.",
                        style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = pgnDraft,
                        onValueChange = { pgnDraft = it },
                        label = { Text("Editable PGN and move list") },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp, max = 360.dp),
                        minLines = 8,
                        maxLines = 16
                    )
                    OutlinedTextField(
                        value = matchComment,
                        onValueChange = { matchComment = it },
                        label = { Text("Match notes / comments") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 4
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.saveMatchToNotebook(pgnDraft, matchComment)
                    showPgnDialog = false
                }) { Text("Save to Notebook") }
            },
            dismissButton = {
                TextButton(onClick = { pgnDraft = viewModel.currentPgn() }) { Text("Reset PGN") }
            }
        )
    }

    fun applyPersona(persona: String, sideA: Boolean) {
        val values = when (persona) {
            "Aggressive" -> listOf(90f, 35f, 85f, 20f)
            "Positional" -> listOf(35f, 90f, 35f, 75f)
            "Solid" -> listOf(25f, 70f, 25f, 95f)
            "Tactical" -> listOf(80f, 45f, 95f, 25f)
            else -> listOf(50f, 50f, 50f, 50f)
        }
        if (sideA) {
            aggressionA = values[0]; positionalA = values[1]; complexityA = values[2]; solidityA = values[3]
        } else {
            aggressionB = values[0]; positionalB = values[1]; complexityB = values[2]; solidityB = values[3]
        }
    }

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF0E0C14), Color(0xFF141020))))) {
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Engine Arena", fontSize = 24.sp, fontWeight = FontWeight.Black, color = Color(0xFFF2EEFF))
                    Text(viewModel.status.value, color = Color(0xFF9B93B8), fontSize = 12.sp)
                }
                TextButton(onClick = onBack) { Text("Back") }
            }

            if (!matchStarted) {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 285.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EngineSetupCard(
                        label = "White engine",
                        mode = modeA,
                        onMode = { modeA = it; viewModel.modeA = it },
                        persona = personaA,
                        onPersona = { personaA = it; viewModel.personaA = it; applyPersona(it, true) },
                        depth = depthA,
                        onDepth = { depthA = it },
                        aggression = aggressionA, onAggression = { aggressionA = it },
                        positional = positionalA, onPositional = { positionalA = it },
                        complexity = complexityA, onComplexity = { complexityA = it },
                        solidity = solidityA, onSolidity = { solidityA = it },
                        expanded = tuneA, onExpanded = { tuneA = it },
                        modifier = Modifier.weight(1f)
                    )
                    EngineSetupCard(
                        label = "Black engine",
                        mode = modeB,
                        onMode = { modeB = it; viewModel.modeB = it },
                        persona = personaB,
                        onPersona = { personaB = it; viewModel.personaB = it; applyPersona(it, false) },
                        depth = depthB,
                        onDepth = { depthB = it },
                        aggression = aggressionB, onAggression = { aggressionB = it },
                        positional = positionalB, onPositional = { positionalB = it },
                        complexity = complexityB, onComplexity = { complexityB = it },
                        solidity = solidityB, onSolidity = { solidityB = it },
                        expanded = tuneB, onExpanded = { tuneB = it },
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = timeMs.toInt().toString(),
                    onValueChange = { timeMs = (it.toFloatOrNull() ?: timeMs).coerceIn(100f, 30000f) },
                    label = { Text("Think budget per move (ms)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            }

            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    val maxBoard = if (maxWidth < maxHeight) maxWidth else maxHeight
                    val boardSize = (maxBoard - 34.dp).coerceAtLeast(150.dp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        EvaluationBar(score = eval, modifier = Modifier.height(boardSize).width(20.dp))
                        Spacer(Modifier.width(10.dp))
                        ChessBoard(
                            board = board,
                            onSquareClick = {},
                            modifier = Modifier.size(boardSize),
                            pieceSet = settings.pieceSet,
                            boardTheme = BoardThemes.byId(settings.boardTheme),
                            flipped = false
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                if (!matchStarted) {
                    Button(
                        onClick = {
                            viewModel.depthA = depthA.toInt().coerceIn(1, 24)
                            viewModel.depthB = depthB.toInt().coerceIn(1, 24)
                            viewModel.timeMs = timeMs.toInt().coerceIn(100, 30000)
                            viewModel.aggressionA = aggressionA.toInt(); viewModel.positionalA = positionalA.toInt()
                            viewModel.complexityA = complexityA.toInt(); viewModel.solidityA = solidityA.toInt()
                            viewModel.aggressionB = aggressionB.toInt(); viewModel.positionalB = positionalB.toInt()
                            viewModel.complexityB = complexityB.toInt(); viewModel.solidityB = solidityB.toInt()
                            viewModel.start()
                            matchStarted = true
                        },
                        enabled = !viewModel.running.value,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp)
                    ) { Text("Start Match") }
                } else {
                    OutlinedButton(
                        onClick = { viewModel.stop(); matchStarted = false },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp)
                    ) { Text("Stop Match / Back to Setup") }
                }
                if (!viewModel.running.value && viewModel.status.value == "Finished") {
                    Button(onClick = {
                        val record = viewModel.saveMatchToNotebook(viewModel.currentPgn(), "")
                        if (record != null) onOpenAnalysis(record)
                    }, modifier = Modifier.weight(1f)) { Text("Save & Analyze") }
                }
                OutlinedButton(
                    onClick = {
                        pgnDraft = viewModel.currentPgn()
                        showPgnDialog = true
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp)
                ) { Text("PGN / Notebook") }
            }
        }
    }
}

@Composable
private fun EngineSetupCard(
    label: String,
    mode: String,
    onMode: (String) -> Unit,
    persona: String,
    onPersona: (String) -> Unit,
    depth: Float,
    onDepth: (Float) -> Unit,
    aggression: Float, onAggression: (Float) -> Unit,
    positional: Float, onPositional: (Float) -> Unit,
    complexity: Float, onComplexity: (Float) -> Unit,
    solidity: Float, onSolidity: (Float) -> Unit,
    expanded: Boolean,
    onExpanded: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier.background(Color(0xFF1A1626), RoundedCornerShape(14.dp)).padding(9.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(label, color = Color(0xFFF2EEFF), fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Text(if (mode == "Caissa") "Engine: Caissa Kotlin" else "Engine: Aetheris Classical", color = Color(0xFFB9B2CE), fontSize = 11.sp)
        PersonaPicker(persona, onPersona, Modifier.fillMaxWidth())
        Text("Search depth: ${depth.toInt()}", color = Color(0xFFB9B2CE), fontSize = 11.sp)
        Slider(value = depth, onValueChange = onDepth, valueRange = 1f..20f, steps = 18)
        TextButton(onClick = { onExpanded(!expanded) }, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) {
            Text(if (expanded) "Hide style sliders" else "Adjust style sliders", fontSize = 11.sp)
        }
        if (expanded) {
            CompactStyleSlider("Aggression", aggression, onAggression)
            CompactStyleSlider("Positional", positional, onPositional)
            CompactStyleSlider("Complexity", complexity, onComplexity)
            CompactStyleSlider("Solidity", solidity, onSolidity)
        }
    }
}

@Composable
private fun EngineModePicker(current: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val options = listOf("PureClassical" to "Aetheris Classical", "Caissa" to "Caissa")
    Box(modifier) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)) {
            Text(options.firstOrNull { it.first == current }?.second ?: current, fontSize = 11.sp)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (id, label) -> DropdownMenuItem(text = { Text(label) }, onClick = { onChange(id); expanded = false })
            }
        }
    }
}

@Composable
private fun PersonaPicker(current: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val options = listOf("Balanced", "Aggressive", "Positional", "Solid", "Tactical")
    Box(modifier) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)) {
            Text(personaLabel(current), fontSize = 11.sp)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { label -> DropdownMenuItem(text = { Text(label) }, onClick = { onChange(label); expanded = false }) }
        }
    }
}

private fun personaLabel(value: String) = value

@Composable
private fun CompactStyleSlider(label: String, value: Float, onValueChange: (Float) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = Color(0xFFB9B2CE), fontSize = 10.sp)
            Text(value.toInt().toString(), color = Color(0xFFEDE8FF), fontSize = 10.sp)
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = 0f..100f)
    }
}
