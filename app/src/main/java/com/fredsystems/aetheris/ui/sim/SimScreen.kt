package com.fredsystems.aetheris.ui.sim

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
fun SimScreen(viewModel: SimViewModel, onBack: () -> Unit) {
    val board by viewModel.boardState
    val eval by viewModel.eval
    val settings = com.fredsystems.aetheris.domain.model.SettingsStore(androidx.compose.ui.platform.LocalContext.current).load()
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF0E0C14), Color(0xFF141020))))) {
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Engine Arena", fontSize = 24.sp, fontWeight = FontWeight.Black, color = Color(0xFFF2EEFF))
                    Text(viewModel.status.value, color = Color(0xFF9B93B8), fontSize = 12.sp)
                }
                TextButton(onClick = onBack) { Text("Back") }
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                EnginePersonaPicker("White engine", viewModel.modeA, { viewModel.modeA = it }, Modifier.weight(1f))
                EnginePersonaPicker("Black engine", viewModel.modeB, { viewModel.modeB = it }, Modifier.weight(1f))
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = viewModel.depthA.toString(),
                    onValueChange = { viewModel.depthA = it.toIntOrNull() ?: viewModel.depthA },
                    label = { Text("Depth A") },
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = viewModel.depthB.toString(),
                    onValueChange = { viewModel.depthB = it.toIntOrNull() ?: viewModel.depthB },
                    label = { Text("Depth B") },
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = viewModel.timeMs.toString(),
                    onValueChange = { viewModel.timeMs = it.toIntOrNull() ?: viewModel.timeMs },
                    label = { Text("Move ms") },
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(10.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    val maxBoard = if (maxWidth < maxHeight) maxWidth else maxHeight
                    val boardSize = maxBoard - 34.dp
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
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = { viewModel.start() }, enabled = !viewModel.running.value, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                    Text("Start Match")
                }
                OutlinedButton(onClick = { viewModel.stop() }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                    Text("Stop")
                }
            }
        }
    }
}

@Composable
private fun EnginePersonaPicker(label: String, current: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val options = listOf("DualCore" to "Dual-Core", "PureNNUE" to "Pure NNUE", "PureClassical" to "Classical")
    Column(modifier) {
        Text(label, color = Color(0xFF9B93B8), fontSize = 11.sp)
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Text(options.firstOrNull { it.first == current }?.second ?: current, fontSize = 12.sp)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { (id, lbl) ->
                    DropdownMenuItem(text = { Text(lbl) }, onClick = { onChange(id); expanded = false })
                }
            }
        }
    }
}
