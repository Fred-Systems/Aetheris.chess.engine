package com.fredsystems.aetheris.ui.analysis

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fredsystems.aetheris.ui.board.ChessBoard
import com.fredsystems.aetheris.domain.model.BoardState
import com.fredsystems.aetheris.viewmodel.GameViewModel

@Composable
fun AnalysisScreen(viewModel: GameViewModel) {
    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        Text("Game Analysis", style = androidx.compose.material3.MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        ChessBoard(board = viewModel.boardState.value, onSquareClick = {}, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Text("Move-by-move review with blunders/mistakes calculated by Aetheris.")
    }
}