package com.fredsystems.aetheris.ui.analysis

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
    val lines = viewModel.analysisLines.value
    val loading = viewModel.analysisLoading.value
    var autoplay by remember { mutableStateOf(false) }

    LaunchedEffect(autoplay, settings.analysisAutoplaySeconds, index, length) {
        if (autoplay) {
            if (index >= length - 1) autoplay = false
            else {
                delay(settings.analysisAutoplaySeconds * 1000L)
                viewModel.goForward()
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(8.dp)) {
        Text("Game Analysis", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(4.dp))
        Text(
            "Position ${index + 1} of $length · Evaluation ${if (evaluation >= 0) "+" else ""}${"%.2f".format(evaluation / 100.0)}",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
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
            EvaluationBar(score = evaluation, modifier = Modifier.height(280.dp).width(22.dp))
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Engine lines", style = MaterialTheme.typography.titleMedium)
            Text("${settings.analysisLineCount} lines · depth ${viewModel.options.value.depth.coerceIn(4, 12)}", style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(4.dp))
        Button(
            onClick = { viewModel.analyzePosition(settings.analysisLineCount) },
            enabled = !loading,
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (loading) "Analyzing position…" else "Analyze position") }
        if (lines.isEmpty() && !loading) {
            Spacer(Modifier.height(4.dp))
            Text("Run analysis to calculate principal variations for this position.", style = MaterialTheme.typography.bodySmall)
        }
        lines.forEachIndexed { i, line ->
            Card(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                Column(Modifier.fillMaxWidth().padding(10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Line ${i + 1}", style = MaterialTheme.typography.labelLarge)
                        Text("${if (line.scoreCp >= 0) "+" else ""}${"%.2f".format(line.scoreCp / 100.0)}", style = MaterialTheme.typography.labelLarge)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(line.moves.chunked(2).mapIndexed { moveIndex, pair ->
                        if (pair.size == 2) "${moveIndex + 1}. ${pair[0]} ${pair[1]}" else "${moveIndex + 1}. ${pair[0]}"
                    }.joinToString("  "), style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(onClick = { autoplay = false; viewModel.playAnalysisLine(line) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Play out this line")
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Playback interval: ${settings.analysisAutoplaySeconds}s per move (change it in Settings).",
            style = MaterialTheme.typography.bodySmall
        )
    }
}
