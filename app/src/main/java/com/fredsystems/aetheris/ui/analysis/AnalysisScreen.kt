package com.fredsystems.aetheris.ui.analysis

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.fredsystems.aetheris.ui.board.BoardArrow
import com.fredsystems.aetheris.ui.board.ChessBoard
import com.fredsystems.aetheris.ui.board.EvaluationBar
import com.fredsystems.aetheris.ui.theme.BoardThemes
import com.fredsystems.aetheris.viewmodel.GameViewModel
import kotlinx.coroutines.delay

@Composable
fun AnalysisScreen(viewModel: GameViewModel) {
    val settings = viewModel.settings.value
    val board = viewModel.boardState.value
    val index = viewModel.historyIndex.value
    val length = viewModel.historyLength.value
    val evaluation = viewModel.evaluation.value
    val lines = viewModel.analysisLines.value
    val bookMoves = viewModel.openingBookMoves.value
    val loading = viewModel.analysisLoading.value
    var autoplay by remember { mutableStateOf(false) }
    var continuousAnalysis by remember { mutableStateOf(true) }
    var analysisDepth by remember(index, length) { mutableStateOf(viewModel.options.value.depth.coerceIn(1, 20)) }
    var showDepthControls by remember { mutableStateOf(false) }
    val configuration = LocalConfiguration.current
    var pgnExpanded by remember(configuration.screenWidthDp, settings.showPgnPanel) { mutableStateOf(configuration.screenWidthDp >= 600 && settings.showPgnPanel) }

    LaunchedEffect(index, length, settings.openingBookEnabled) {
        viewModel.refreshOpeningBookMoves(viewModel.currentPositionFen())
    }

    LaunchedEffect(continuousAnalysis, index, length) {
        while (continuousAnalysis) {
            if (!viewModel.analysisLoading.value) {
                viewModel.options.value = viewModel.options.value.copy(depth = analysisDepth.coerceIn(1, 20))
                viewModel.analyzePosition(settings.analysisLineCount)
                analysisDepth = (analysisDepth + 1).coerceAtMost(20)
            }
            delay(5200)
        }
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

    Column(Modifier.fillMaxSize().background(Color(0xFF0E0C14)).verticalScroll(rememberScrollState()).padding(8.dp)) {
        Text("Game Analysis", color = Color(0xFFF2EEFF), style = MaterialTheme.typography.headlineSmall)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("Engine", color = Color(0xFF9B93B8), style = MaterialTheme.typography.labelSmall)
            FilterChip(
                selected = settings.selectedEngine == "aetheris",
                onClick = { viewModel.updateSettings(settings.copy(selectedEngine = "aetheris")) },
                label = { Text("Aetheris Classical") }
            )
            FilterChip(
                selected = settings.selectedEngine == "caissa",
                onClick = { viewModel.updateSettings(settings.copy(selectedEngine = "caissa")) },
                label = { Text("Caissa") }
            )
        }
        Spacer(Modifier.height(4.dp))
        Text("Position ${index + 1} of $length · Evaluation ${if (evaluation >= 0) "+" else ""}${"%.2f".format(evaluation / 100.0)}", color = Color(0xFFD7D0EA), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            OutlinedButton(onClick = { autoplay = false; viewModel.goToStart() }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 2.dp)) { Text("|◀") }
            OutlinedButton(onClick = { autoplay = false; viewModel.goBack() }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 2.dp)) { Text("◀") }
            Button(onClick = { autoplay = !autoplay }, modifier = Modifier.weight(1.3f), contentPadding = PaddingValues(horizontal = 2.dp)) { Text(if (autoplay) "Pause" else "▶ Play") }
            OutlinedButton(onClick = { autoplay = false; viewModel.goForward() }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 2.dp)) { Text("▶") }
            OutlinedButton(onClick = { autoplay = false; viewModel.goToEnd() }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 2.dp)) { Text("▶|") }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("Opening book arrows", color = Color(0xFFD69BFF), style = MaterialTheme.typography.labelSmall)
            Switch(
                checked = settings.showOpeningBookArrows,
                onCheckedChange = { viewModel.updateSettings(settings.copy(showOpeningBookArrows = it)) }
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChessBoard(
                board = board,
                onSquareClick = { viewModel.onSquareClick(it) },
                modifier = Modifier.weight(1f),
                pieceSet = settings.pieceSet,
                boardTheme = BoardThemes.byId(settings.boardTheme),
                flipped = viewModel.flipped.value,
                arrows = buildList {
                    if (settings.showBestMoveArrow || settings.showEngineThinkingArrows) {
                        addAll(lines.take(if (settings.showEngineThinkingArrows) 3 else 1).mapIndexedNotNull { arrowIndex, line ->
                            val move = line.moves.firstOrNull() ?: return@mapIndexedNotNull null
                            if (move.length < 4) return@mapIndexedNotNull null
                            val from = (move[0] - 'a') + (move[1] - '1') * 8
                            val to = (move[2] - 'a') + (move[3] - '1') * 8
                            val color = listOf(Color(0xFF42D66B), Color(0xFF42B8FF), Color(0xFFFFB547))[arrowIndex % 3]
                            BoardArrow(from, to, color)
                        })
                    }
                    if (settings.showOpeningBookArrows) {
                        addAll(bookMoves.take(5).mapNotNull { move ->
                            if (move.length < 4) null else BoardArrow(
                                (move[0] - 'a') + (move[1] - '1') * 8,
                                (move[2] - 'a') + (move[3] - '1') * 8,
                                Color(0xFFD69BFF)
                            )
                        })
                    }
                }
            )
            EvaluationBar(score = evaluation, modifier = Modifier.height(280.dp).width(22.dp))
        }
        if (settings.showOpeningBookArrows && bookMoves.isNotEmpty()) {
            Text("Opening book · ${bookMoves.size} continuations", color = Color(0xFFD69BFF), style = MaterialTheme.typography.labelMedium)
            Text(bookMoves.take(5).joinToString("   ") { it.uppercase() }, color = Color(0xFFC7B0E8), style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(10.dp))
        TextButton(onClick = { showDepthControls = !showDepthControls }) {
            Text(if (showDepthControls) "Hide depth controls" else "Change depth")
        }
        if (showDepthControls) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Search depth", style = MaterialTheme.typography.labelLarge)
                Text("$analysisDepth", style = MaterialTheme.typography.labelMedium, color = Color(0xFFB9A8FF))
            }
            Slider(value = analysisDepth.toFloat(), onValueChange = { analysisDepth = it.toInt().coerceIn(1, 20) }, valueRange = 1f..20f, steps = 18)
            Text("Higher depth takes longer to calculate.", color = Color(0xFFB9B2CE), style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Engine lines", color = Color(0xFFF2EEFF), style = MaterialTheme.typography.titleMedium)
            Text("${settings.analysisLineCount} lines · depth ${viewModel.options.value.depth.coerceIn(4, 20)}", style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(4.dp))
        Button(onClick = { continuousAnalysis = !continuousAnalysis }, modifier = Modifier.fillMaxWidth()) {
            Text(if (continuousAnalysis) "Pause continuous analysis" else "Resume continuous analysis")
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (lines.isEmpty() && !loading) Text("No lines returned. Resume analysis to retry.", style = MaterialTheme.typography.bodySmall)
        lines.forEachIndexed { i, line ->
            Card(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                Column(Modifier.fillMaxWidth().padding(10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Line ${i + 1}", style = MaterialTheme.typography.labelLarge)
                        Text("${if (line.scoreCp >= 0) "+" else ""}${"%.2f".format(line.scoreCp / 100.0)}", style = MaterialTheme.typography.labelLarge)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(viewModel.formatAnalysisLine(line.moves), color = Color(0xFFE5E0F0), style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(onClick = { autoplay = false; viewModel.playAnalysisLine(line) }, modifier = Modifier.fillMaxWidth()) { Text("Play out this line") }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { pgnExpanded = !pgnExpanded }, modifier = Modifier.fillMaxWidth()) {
            Text(if (pgnExpanded) "Hide PGN / move notebook" else "Show PGN / move notebook")
        }
        if (pgnExpanded) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(10.dp)) {
                    Text("Analysis session PGN", style = MaterialTheme.typography.titleSmall)
                    Text(viewModel.currentPgn(), style = MaterialTheme.typography.bodySmall)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { viewModel.copyPgn() }, modifier = Modifier.weight(1f)) { Text("Copy PGN") }
                        Button(onClick = { viewModel.saveCurrentGame() }, modifier = Modifier.weight(1f)) { Text("Save game") }
                    }
                    if (viewModel.pgnStatus.value.isNotBlank()) Text(viewModel.pgnStatus.value, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Text("Playback interval: ${settings.analysisAutoplaySeconds}s per move (change it in Settings).", style = MaterialTheme.typography.bodySmall)
    }
}
