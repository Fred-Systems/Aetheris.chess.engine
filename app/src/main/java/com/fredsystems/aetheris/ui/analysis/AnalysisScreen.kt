package com.fredsystems.aetheris.ui.analysis

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
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

    // Populate real principal variations as soon as Analysis opens and refresh them
    // whenever the user navigates to a different position.
    LaunchedEffect(index, length) {
        if (!loading) viewModel.analyzePosition(settings.analysisLineCount)
    }

    LaunchedEffect(index, length) {
        if (lines.isEmpty() && !loading) viewModel.analyzePosition(settings.analysisLineCount)
    }

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
            Box(Modifier.weight(1f).height(280.dp)) {
                ChessBoard(
                    board = board,
                    onSquareClick = { viewModel.onSquareClick(it) },
                    modifier = Modifier.fillMaxSize(),
                    pieceSet = settings.pieceSet,
                    boardTheme = BoardThemes.byId(settings.boardTheme),
                    flipped = viewModel.flipped.value
                )
                val bestMove = lines.firstOrNull()?.moves?.firstOrNull()
                if (bestMove != null && bestMove.length >= 4) {
                    Canvas(Modifier.fillMaxSize()) {
                        fun center(square: String): Offset {
                            val file = square[0].lowercaseChar() - 'a'
                            val rank = square[1].digitToInt() - 1
                            val flipped = viewModel.flipped.value
                            val col = if (flipped) 7 - file else file
                            val row = if (flipped) rank else 7 - rank
                            val cell = size.width / 8f
                            return Offset((col + 0.5f) * cell, (row + 0.5f) * cell)
                        }
                        val start = center(bestMove.substring(0, 2))
                        val end = center(bestMove.substring(2, 4))
                        val color = Color(0xFF35D07F)
                        drawLine(color, start, end, strokeWidth = size.width * 0.018f, cap = StrokeCap.Round)
                        val angle = kotlin.math.atan2(end.y - start.y, end.x - start.x)
                        val head = size.width * 0.075f
                        val p1 = Offset(end.x - head * kotlin.math.cos(angle - 0.55f), end.y - head * kotlin.math.sin(angle - 0.55f))
                        val p2 = Offset(end.x - head * kotlin.math.cos(angle + 0.55f), end.y - head * kotlin.math.sin(angle + 0.55f))
                        val path = Path().apply { moveTo(end.x, end.y); lineTo(p1.x, p1.y); lineTo(p2.x, p2.y); close() }
                        drawPath(path, color)
                    }
                }
            }
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
            Text("Calculating principal variations… or tap Analyze position to retry.", style = MaterialTheme.typography.bodySmall)
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
