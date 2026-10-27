package com.fredsystems.aetheris.ui.analysis

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fredsystems.aetheris.domain.model.SettingsStore
import com.fredsystems.aetheris.ui.board.ChessBoard
import com.fredsystems.aetheris.ui.board.EvaluationBar
import com.fredsystems.aetheris.ui.theme.BoardThemes
import com.fredsystems.aetheris.viewmodel.GameViewModel
import kotlinx.coroutines.delay

@Composable
fun AnalysisScreen(viewModel: GameViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val settings = SettingsStore(context).load()
    val board = viewModel.boardState.value
    val index = viewModel.historyIndex.value
    val length = viewModel.historyLength.value
    val evaluation = viewModel.evaluation.value
    var autoplay by remember { mutableStateOf(false) }

    LaunchedEffect(autoplay, settings.analysisAutoplaySeconds, index, length) {
        if (autoplay) {
            if (index >= length - 1) {
                autoplay = false
            } else {
                delay(settings.analysisAutoplaySeconds * 1000L)
                viewModel.goForward()
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        Text("Game Analysis", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(4.dp))
        Text(
            "Position ${index + 1} of $length · Evaluation ${if (evaluation >= 0) "+" else ""}${"%.2f".format(evaluation / 100.0)}",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = { autoplay = false; viewModel.goToStart() }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 2.dp)) { Text("|◀") }
            OutlinedButton(onClick = { autoplay = false; viewModel.goBack() }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 2.dp)) { Text("◀") }
            Button(onClick = { autoplay = !autoplay }, modifier = Modifier.weight(1.3f), contentPadding = PaddingValues(horizontal = 2.dp)) { Text(if (autoplay) "Pause" else "▶ Play") }
            OutlinedButton(onClick = { autoplay = false; viewModel.goForward() }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 2.dp)) { Text("▶") }
            OutlinedButton(onClick = { autoplay = false; viewModel.goToEnd() }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 2.dp)) { Text("▶|") }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChessBoard(
                board = board,
                onSquareClick = { viewModel.onSquareClick(it) },
                modifier = Modifier.weight(1f),
                pieceSet = settings.pieceSet,
                boardTheme = BoardThemes.byId(settings.boardTheme),
                flipped = viewModel.flipped.value
            )
            EvaluationBar(score = evaluation, modifier = Modifier.fillMaxHeight().width(22.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Use the arrows to review positions. Playback interval: ${settings.analysisAutoplaySeconds}s (change it in Settings).",
            style = MaterialTheme.typography.bodySmall
        )
    }
}
