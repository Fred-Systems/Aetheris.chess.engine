package com.fredsystems.aetheris.ui.analysis

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fredsystems.aetheris.domain.model.SettingsStore
import com.fredsystems.aetheris.ui.board.ChessBoard
import com.fredsystems.aetheris.ui.theme.BoardThemes
import com.fredsystems.aetheris.viewmodel.GameViewModel

@Composable
fun AnalysisScreen(viewModel: GameViewModel) {
    val settings = SettingsStore(androidx.compose.ui.platform.LocalContext.current).load()
    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        Text("Game Analysis", style = androidx.compose.material3.MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        ChessBoard(
            board = viewModel.boardState.value,
            onSquareClick = { viewModel.onSquareClick(it) },
            modifier = Modifier.fillMaxWidth(),
            pieceSet = settings.pieceSet,
            boardTheme = BoardThemes.byId(settings.boardTheme),
            flipped = viewModel.flipped.value
        )
        Spacer(Modifier.height(8.dp))
        Text("Move-by-move review with blunders/mistakes calculated by Aetheris.")
    }
}
