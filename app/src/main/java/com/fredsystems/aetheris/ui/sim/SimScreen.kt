package com.fredsystems.aetheris.ui.sim

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fredsystems.aetheris.ui.board.ChessBoard
import com.fredsystems.aetheris.ui.board.EvaluationBar
import com.fredsystems.aetheris.viewmodel.SimViewModel

@Composable
fun SimScreen(viewModel: SimViewModel, onBack: () -> Unit) {
    val board by viewModel.board
    val eval by viewModel.eval
    Column(Modifier.fillMaxSize().padding(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(viewModel.status.value)
            TextButton(onClick = onBack) { Text("Back") }
        }
        Row(Modifier.weight(1f)) {
            EvaluationBar(score = eval, modifier = Modifier.fillMaxHeight())
            ChessBoard(board = board, onSquareClick = {}, modifier = Modifier.weight(1f))
        }
        Row {
            Button(onClick = { viewModel.start() }, enabled = !viewModel.running.value) { Text("Start") }
            Spacer(Modifier.width(8.dp))
            Button(onClick = { viewModel.stop() }) { Text("Stop") }
        }
    }
}