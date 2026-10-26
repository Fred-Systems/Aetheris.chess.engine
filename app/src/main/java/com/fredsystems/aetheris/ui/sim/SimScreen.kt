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
import com.fredsystems.aetheris.viewmodel.SimViewModel

@Composable
fun SimScreen(viewModel: SimViewModel, onBack: () -> Unit) {
    val board by viewModel.boardState
    val eval by viewModel.eval
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
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    val maxBoard = if (maxWidth < maxHeight) maxWidth else maxHeight
                    val boardSize = maxBoard - 34.dp
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        EvaluationBar(score = eval, modifier = Modifier.height(boardSize).width(20.dp))
                        Spacer(Modifier.width(10.dp))
                        ChessBoard(board = board, onSquareClick = {}, modifier = Modifier.size(boardSize))
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
