@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.fredsystems.aetheris

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.fredsystems.aetheris.domain.model.PieceColor
import com.fredsystems.aetheris.domain.model.EngineMode
import com.fredsystems.aetheris.domain.model.EngineOptions
import com.fredsystems.aetheris.domain.model.TimeControl
import com.fredsystems.aetheris.ui.analysis.AnalysisScreen
import com.fredsystems.aetheris.ui.board.ChessBoard
import com.fredsystems.aetheris.ui.board.BoardArrow
import com.fredsystems.aetheris.ui.board.EvaluationBar
import com.fredsystems.aetheris.ui.notebook.NotebookScreen
import com.fredsystems.aetheris.ui.settings.ExternalEnginesScreen
import com.fredsystems.aetheris.ui.settings.SettingsScreen
import com.fredsystems.aetheris.ui.sim.SimScreen
import com.fredsystems.aetheris.ui.theme.AetherisTheme
import com.fredsystems.aetheris.ui.theme.BoardThemes
import com.fredsystems.aetheris.viewmodel.GameNotebookViewModel
import com.fredsystems.aetheris.viewmodel.GameViewModel
import com.fredsystems.aetheris.viewmodel.PlayMode
import com.fredsystems.aetheris.viewmodel.SimViewModel
import com.fredsystems.aetheris.updater.GitHubUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onDestroy() {
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val crashReportPending = CrashDiagnostics.initialize(this) && packageName.endsWith(".crashreport")
        setContent {
            var showCrashReportPrompt by remember { mutableStateOf(crashReportPending) }
            AetherisTheme {
                val navController = rememberNavController()
                val vm: GameViewModel = viewModel(factory = GameViewModelFactory(this))
                val nnueImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                    uri?.let { selectedUri ->
                        runCatching {
                            val pending = File(filesDir, "nnue/import_pending.bin")
                            pending.parentFile?.mkdirs()
                            contentResolver.openInputStream(selectedUri)?.use { input ->
                                pending.outputStream().use { output -> input.copyTo(output) }
                            } ?: error("Could not open the selected file")
                            vm.loadCustomNnue(pending.absolutePath)
                            pending.delete()
                        }
                    }
                }
                val simVm: SimViewModel = viewModel(factory = SimViewModelFactory(this))
                val notebookVm: GameNotebookViewModel = viewModel(factory = NotebookViewModelFactory(this))
                val updater = remember { GitHubUpdater(this) }
                val settings by vm.settings
                val baseDensity = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(
                        density = baseDensity.density * settings.uiScale,
                        fontScale = baseDensity.fontScale
                    )
                ) {
                    NavHost(navController = navController, startDestination = "game") {
                        composable("game") { GameScreen(vm, navController) }
                        composable("settings") {
                            SettingsScreen(
                                vm,
                                onCheckUpdate = {
                                    CoroutineScope(Dispatchers.Main).launch {
                                        val (isNewer, url) = updater.checkForUpdate()
                                        if (isNewer && url != null) updater.downloadAndInstall(url)
                                    }
                                },
                                onOpenSim = { navController.navigate("sim") },
                                onOpenNotebook = { navController.navigate("notebook") },
                                onOpenExternal = { navController.navigate("external") },
                                onImportNnue = { nnueImportLauncher.launch(arrayOf("application/octet-stream", "application/x-binary", "*/*")) },
                                onExportCrashReport = {
                                    val share = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_SUBJECT, "Aetheris crash report")
                                        putExtra(Intent.EXTRA_TEXT, CrashDiagnostics.readReport(this@MainActivity))
                                    }
                                    startActivity(Intent.createChooser(share, "Send Aetheris crash report"))
                                }
                            )
                        }
                        composable("analysis") { AnalysisScreen(vm) }
                        composable("sim") { SimScreen(simVm, onBack = { navController.popBackStack() }, onOpenAnalysis = { record -> vm.openNotebookRecord(record); navController.navigate("analysis") }) }
                        composable("notebook") { NotebookScreen(notebookVm, onBack = { navController.popBackStack() }, onOpenAnalysis = { record -> vm.openNotebookRecord(record); navController.navigate("analysis") }) }
                        composable("external") { ExternalEnginesScreen() }
                    }
                    if (showCrashReportPrompt) {
                        Dialog(
                            onDismissRequest = {
                                CrashDiagnostics.acknowledgeCrashReport(this@MainActivity)
                                showCrashReportPrompt = false
                            },
                            properties = DialogProperties(usePlatformDefaultWidth = false)
                        ) {
                            Surface(
                                modifier = Modifier.fillMaxWidth().fillMaxHeight(0.92f).padding(12.dp),
                                shape = RoundedCornerShape(20.dp),
                                color = Color(0xFF171421)
                            ) {
                                Column(Modifier.fillMaxSize().padding(16.dp)) {
                                    Text("Aetheris crash report", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF2EEFF))
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        "The previous run appears to have ended unexpectedly. This report includes saved engine stages, Java stack traces, and native fatal-signal details when available.",
                                        color = Color(0xFFB9B2CE), fontSize = 13.sp
                                    )
                                    Spacer(Modifier.height(10.dp))
                                    Surface(
                                        modifier = Modifier.weight(1f).fillMaxWidth(),
                                        shape = RoundedCornerShape(10.dp), color = Color(0xFF0C0B10)
                                    ) {
                                        Text(
                                            text = CrashDiagnostics.readReport(this@MainActivity),
                                            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(10.dp),
                                            color = Color(0xFFE6E1F2), fontFamily = FontFamily.Monospace,
                                            fontSize = 10.sp, lineHeight = 13.sp
                                        )
                                    }
                                    Spacer(Modifier.height(12.dp))
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(
                                            modifier = Modifier.weight(1f),
                                            onClick = {
                                                CrashDiagnostics.acknowledgeCrashReport(this@MainActivity)
                                                showCrashReportPrompt = false
                                            }
                                        ) { Text("Later") }
                                        Button(
                                            modifier = Modifier.weight(1f),
                                            onClick = {
                                                val share = Intent(Intent.ACTION_SEND).apply {
                                                    type = "text/plain"
                                                    putExtra(Intent.EXTRA_SUBJECT, "Aetheris full crash diagnostics")
                                                    putExtra(Intent.EXTRA_TEXT, CrashDiagnostics.readReport(this@MainActivity))
                                                }
                                                CrashDiagnostics.acknowledgeCrashReport(this@MainActivity)
                                                showCrashReportPrompt = false
                                                startActivity(Intent.createChooser(share, "Export Aetheris crash diagnostics"))
                                            }
                                        ) { Text("Export report") }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GameScreen(vm: GameViewModel, navController: androidx.navigation.NavController) {
    val state by vm.boardState
    val eval by vm.evaluation
    val thinking by vm.engineThinking
    val opts by vm.options
    val mode by vm.mode
    val evalVisible by vm.evalVisible
    val bestMoveUci = vm.bestMoveUci.value
    val liveAnalysisLines = vm.analysisLines.value
    val flipped by vm.flipped
    val settings by vm.settings
    val whiteTime by vm.whiteTimeMs
    val blackTime by vm.blackTimeMs
    val timeControl = vm.timeControlSeconds.value
    var showNewGame by remember { mutableStateOf(false) }
    var showPlayFromHere by remember { mutableStateOf(false) }
    var showConditionalDialog by remember { mutableStateOf(false) }
    var showQuickEngineOptions by remember { mutableStateOf(false) }
    var conditionalOpponent by remember { mutableStateOf("e7e5") }
    var conditionalReply by remember { mutableStateOf("g1f3") }
    val configuration = LocalConfiguration.current
    var pgnExpanded by remember { mutableStateOf(configuration.screenWidthDp >= 600 && settings.showPgnPanel) }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0E0C14), Color(0xFF141020), Color(0xFF0A0910))
                )
            )
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 14.dp).verticalScroll(rememberScrollState())) {
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Aetheris", fontSize = 23.sp, fontWeight = FontWeight.Black, color = Color(0xFFF2EEFF))
                    Text(
                        if (mode == PlayMode.PLAY) "Play vs Engine" else "Analysis Board",
                        fontSize = 10.sp,
                        color = Color(0xFF9B93B8)
                    )
                }
                IconButton(onClick = { showNewGame = true }, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Outlined.Refresh, "New game", tint = Color(0xFFB9A8FF), modifier = Modifier.size(19.dp))
                }
                IconButton(onClick = { vm.undoMove() }, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Outlined.Undo, "Undo move", tint = Color(0xFFB9A8FF), modifier = Modifier.size(19.dp))
                }
                IconButton(onClick = { vm.forceEngineMove() }, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Outlined.AutoAwesome, "Force engine move", tint = Color(0xFFB9A8FF), modifier = Modifier.size(19.dp))
                }
                IconButton(onClick = { vm.toggleFlip() }, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Outlined.SwapVert, "Flip board", tint = if (flipped) Color(0xFFB9A8FF) else Color(0xFFC9C1E5), modifier = Modifier.size(19.dp))
                }
                IconButton(onClick = { vm.toggleEval() }, modifier = Modifier.size(34.dp)) {
                    Icon(
                        if (evalVisible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                        "Toggle evaluation bar",
                        tint = if (evalVisible) Color(0xFFB9A8FF) else Color(0xFFC9C1E5),
                        modifier = Modifier.size(19.dp)
                    )
                }
                IconButton(onClick = { navController.navigate("settings") }, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Outlined.Settings, "Settings", tint = Color(0xFFC9C1E5), modifier = Modifier.size(19.dp))
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ModeChip("Play", mode == PlayMode.PLAY, Modifier.weight(1f)) { vm.setMode(PlayMode.PLAY) }
                Button(
                    onClick = { vm.setMode(PlayMode.ANALYSIS); navController.navigate("analysis") },
                    modifier = Modifier.weight(1f)
                ) { Text("Analysis") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { navController.navigate("sim") },
                    modifier = Modifier.weight(1f)
                ) { Text("Engine match") }
                OutlinedButton(
                    onClick = { showPlayFromHere = true },
                    modifier = Modifier.weight(1f)
                ) { Text("Play from Here") }
            }
            if (settings.conditionalMoves) {
                OutlinedButton(
                    onClick = {
                        if (vm.conditionalSetupActive.value) vm.cancelConditionalSetup()
                        else showConditionalDialog = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (vm.conditionalSetupActive.value) "Cancel Conditional Setup" else "Configure Conditional Reply")
                }
            }

            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFF1A1626),
                tonalElevation = 0.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.padding(horizontal = 15.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = state.statusText,
                            color = Color(0xFFF2EEFF),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = if (thinking) "Aetheris is thinking..." else "Depth ${opts.depth} · Aetheris Classical",
                            color = if (thinking) Color(0xFF7C4DFF) else Color(0xFF9B93B8),
                            fontSize = 11.sp
                        )
                    }
                    if (timeControl > 0 && mode == PlayMode.PLAY) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            ClockPill(formatClock(whiteTime), state.sideToMove == PieceColor.WHITE)
                            ClockPill(formatClock(blackTime), state.sideToMove == PieceColor.BLACK)
                        }
                    } else {
                        Box(
                            Modifier
                                .size(12.dp)
                                .background(
                                    if (state.sideToMove == PieceColor.WHITE) Color(0xFFEDE7DC) else Color(0xFF2B2438),
                                    RoundedCornerShape(50)
                                )
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))

            Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                BoxWithConstraints(Modifier.fillMaxWidth().heightIn(min = 160.dp), contentAlignment = Alignment.Center) {
                    // This screen is scrollable, so a larger board pushes the controls down
                    // instead of drawing over them. Keep the board inside the device width.
                    val widthLimit = (maxWidth - if (evalVisible) 52.dp else 22.dp).coerceAtLeast(160.dp)
                    // Prioritize a large, readable board. The screen scrolls vertically,
                    // so the board does not need to be shrunk to leave room for controls.
                    val boardSize = (widthLimit * settings.boardScale.coerceIn(0.75f, 3f))
                        .coerceIn(200.dp, widthLimit)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (evalVisible) {
                            EvaluationBar(score = eval, modifier = Modifier.height(boardSize).width(22.dp))
                            Spacer(Modifier.width(10.dp))
                        }
                        ChessBoard(
                            board = state,
                            onSquareClick = { vm.onSquareClick(it) },
                            modifier = Modifier.size(boardSize),
                            pieceSet = settings.pieceSet,
                            boardTheme = BoardThemes.byId(settings.boardTheme),
                            flipped = flipped,
                            blindfold = settings.blindfoldMode,
                            onBoardLongPress = { showQuickEngineOptions = true },
                            arrows = if (!settings.showBestMoveArrow && !settings.showEngineThinkingArrows) emptyList() else {
                                val candidates = if (settings.showEngineThinkingArrows) liveAnalysisLines.take(5) else liveAnalysisLines.take(1)
                                val colors = listOf(Color(0xFF35D07F), Color(0xFF3399FF), Color(0xFFFFB020), Color(0xFFB56BFF), Color(0xFFFF5252))
                                candidates.mapIndexedNotNull { lineIndex, line ->
                                    val uci = line.moves.firstOrNull() ?: return@mapIndexedNotNull null
                                    if (uci.length < 4) return@mapIndexedNotNull null
                                    BoardArrow((uci[1] - '1') * 8 + (uci[0] - 'a'), (uci[3] - '1') * 8 + (uci[2] - 'a'), colors[lineIndex % colors.size])
                                }
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { pgnExpanded = !pgnExpanded }, modifier = Modifier.fillMaxWidth()) {
                Text(if (pgnExpanded) "Hide PGN / move notebook" else "Show PGN / move notebook")
            }
            if (pgnExpanded) {
                Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), color = Color(0xFF1A1626)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Game notation (PGN)", color = Color(0xFFF2EEFF), fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text(vm.currentPgn(), color = Color(0xFFB9B2CE), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { vm.copyPgn() }, modifier = Modifier.weight(1f)) { Text("Copy PGN") }
                            Button(onClick = { vm.saveCurrentGame() }, modifier = Modifier.weight(1f)) { Text("Save game") }
                        }
                        if (vm.pgnStatus.value.isNotBlank()) Text(vm.pgnStatus.value, color = Color(0xFFB9A8FF), fontSize = 12.sp)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

        }
    }

    if (showQuickEngineOptions) {
        QuickEngineOptionsDialog(
            current = opts,
            onDismiss = { showQuickEngineOptions = false },
            onApply = { updated ->
                vm.updateOptions(updated)
                showQuickEngineOptions = false
            }
        )
    }

    if (showConditionalDialog) {
        AlertDialog(
            onDismissRequest = { showConditionalDialog = false },
            title = { Text("Conditional move") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Preview an opponent move without playing it, then make your reply on the preview board. When the engine later plays that exact move, your saved reply is played automatically.")
                    OutlinedButton(
                        onClick = {
                            vm.beginConditionalSetup()
                            showConditionalDialog = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Set up by moving pieces on the board") }
                    Text("Or enter UCI moves manually:", style = MaterialTheme.typography.labelMedium)
                    OutlinedTextField(value = conditionalOpponent, onValueChange = { conditionalOpponent = it.lowercase() }, label = { Text("Opponent move (UCI)") })
                    OutlinedTextField(value = conditionalReply, onValueChange = { conditionalReply = it.lowercase() }, label = { Text("Your reply (UCI)") })
                }
            },
            confirmButton = {
                Button(onClick = {
                    vm.setConditionalMove(conditionalOpponent, conditionalReply)
                    showConditionalDialog = false
                }) { Text("Save conditional reply") }
            },
            dismissButton = {
                TextButton(onClick = {
                    vm.clearConditionalMove()
                    showConditionalDialog = false
                }) { Text("Clear") }
            }
        )
    }

    if (showPlayFromHere) {
        PlayFromHereDialog(
            currentTimeControl = settings.timeControl,
            currentDepth = opts.depth,
            onDismiss = { showPlayFromHere = false },
            onStart = { color, tc, depth ->
                vm.startGameFromHere(color, tc, depth, EngineMode.PURE_CLASSICAL)
                showPlayFromHere = false
            }
        )
    }

    if (showNewGame) {
        NewGameDialog(
            defaultColor = when (vm.playerColor.value) {
                PieceColor.WHITE -> "white"
                PieceColor.BLACK -> "black"
            },
            currentTimeControl = settings.timeControl,
            onDismiss = { showNewGame = false },
            onStart = { color, tc, depth ->
                if (tc != settings.timeControl) vm.updateSettings(vm.settings.value.copy(timeControl = tc))
                vm.updateOptions(vm.options.value.copy(depth = depth))
                vm.newGame(color)
                showNewGame = false
            }
        )
    }
}

@Composable
private fun QuickEngineOptionsDialog(
    current: EngineOptions,
    onDismiss: () -> Unit,
    onApply: (EngineOptions) -> Unit
) {
    var aggression by remember(current) { mutableFloatStateOf(current.aggression.toFloat()) }
    var positional by remember(current) { mutableFloatStateOf(current.positional.toFloat()) }
    var complexity by remember(current) { mutableFloatStateOf(current.complexity.toFloat()) }
    var solidity by remember(current) { mutableFloatStateOf(current.solidity.toFloat()) }
    var depth by remember(current) { mutableFloatStateOf(current.depth.toFloat()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Quick engine controls") },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                Text("Engine: Aetheris", color = Color(0xFFB9B2CE), style = MaterialTheme.typography.labelLarge)
                Text("Persona", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 10.dp))
                QuickPersonalitySlider("Defensive", "Aggressive", aggression) { aggression = it }
                QuickPersonalitySlider("Positional", "Tactical", positional) { positional = it }
                QuickPersonalitySlider("Simplify", "Complexity", complexity) { complexity = it }
                QuickPersonalitySlider("Solid", "Active", solidity) { solidity = it }
                Text("Search depth: ${current.depth}", style = MaterialTheme.typography.labelLarge, color = Color(0xFFEDE8FF))
                Slider(value = depth, onValueChange = { depth = it }, valueRange = 1f..20f, steps = 18)
                Text("Long-press the board again any time to reopen these controls.", color = Color(0xFF9B93B8), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(onClick = {
                onApply(current.copy(
                    aggression = aggression.toInt(),
                    positional = positional.toInt(),
                    complexity = complexity.toInt(),
                    solidity = solidity.toInt(),
                    depth = depth.toInt(),
                    mode = EngineMode.PURE_CLASSICAL
                ))
            }) { Text("Apply") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun QuickPersonalitySlider(left: String, right: String, value: Float, onChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(left, color = Color(0xFFB9B2CE), style = MaterialTheme.typography.labelSmall)
            Text(right, color = Color(0xFFB9B2CE), style = MaterialTheme.typography.labelSmall)
        }
        Slider(value = value, onValueChange = onChange, valueRange = 0f..100f)
    }
}

@Composable
private fun PlayFromHereDialog(
    currentTimeControl: String,
    currentDepth: Int,
    onDismiss: () -> Unit,
    onStart: (String, String, Int) -> Unit
) {
    var color by remember { mutableStateOf("white") }
    var timeControl by remember { mutableStateOf(currentTimeControl) }
    var depth by remember { mutableStateOf(currentDepth.toFloat().coerceIn(1f, 20f)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Play from this position", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Your color", fontSize = 12.sp, color = Color(0xFF9B93B8))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("white" to "White", "black" to "Black", "random" to "Random").forEach { (id, label) ->
                        ModeChip(label, color == id, Modifier.weight(1f)) { color = id }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("Time control", fontSize = 12.sp, color = Color(0xFF9B93B8))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TimeControl.all.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            row.forEach { tc -> ModeChip(tc.label, timeControl == tc.id, Modifier.weight(1f)) { timeControl = tc.id } }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Engine level", fontSize = 12.sp, color = Color(0xFF9B93B8))
                    Text("Depth ${depth.toInt()}", fontWeight = FontWeight.Bold)
                }
                Slider(value = depth, onValueChange = { depth = it }, valueRange = 1f..20f, steps = 18)
                Text("Engine: Aetheris Classical", fontSize = 12.sp, color = Color(0xFF9B93B8))
            }
        },
        confirmButton = { Button(onClick = { onStart(color, timeControl, depth.toInt()) }) { Text("Start from Here") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ClockPill(text: String, active: Boolean) {
    Surface(
        shape = RoundedCornerShape(9.dp),
        color = if (active) Color(0xFF7C4DFF) else Color(0xFF262136)
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            color = if (active) Color.White else Color(0xFFB9B2CE),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun formatClock(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val m = total / 60
    val s = total % 60
    return "%d:%02d".format(m, s)
}

@Composable
private fun ModeChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) Color(0xFF7C4DFF) else Color(0xFF1E1930),
        onClick = onClick
    ) {
        Text(
            label,
            modifier = Modifier.padding(vertical = 9.dp),
            textAlign = TextAlign.Center,
            color = if (selected) Color.White else Color(0xFFB9B2CE),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun NewGameDialog(
    defaultColor: String,
    currentTimeControl: String,
    onDismiss: () -> Unit,
    onStart: (String, String, Int) -> Unit
) {
    var color by remember { mutableStateOf(defaultColor) }
    var timeControl by remember { mutableStateOf(currentTimeControl) }
    var depth by remember { mutableFloatStateOf(6f) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Game", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("Play as", fontSize = 12.sp, color = Color(0xFF9B93B8))
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("white" to "White", "black" to "Black", "random" to "Random").forEach { (id, label) ->
                        ModeChip(label, color == id, Modifier.weight(1f)) { color = id }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text("Engine strength · depth ${depth.toInt()}", fontSize = 12.sp, color = Color(0xFF9B93B8))
                Slider(value = depth, onValueChange = { depth = it }, valueRange = 1f..20f, steps = 18)
                Spacer(Modifier.height(14.dp))
                Text("Time control", fontSize = 12.sp, color = Color(0xFF9B93B8))
                Spacer(Modifier.height(6.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    TimeControl.all.forEach { tc ->
                        ModeChip(tc.label, timeControl == tc.id) { timeControl = tc.id }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onStart(color, timeControl, depth.toInt()) }) { Text("Start") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun ControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF1E1930),
        onClick = onClick
    ) {
        Row(
            Modifier.padding(vertical = 13.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = Color(0xFFB9A8FF), modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(7.dp))
            Text(label, color = Color(0xFFEDE8FF), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

class GameViewModelFactory(private val context: android.content.Context) : androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return GameViewModel(context) as T
    }
}
class SimViewModelFactory(private val context: android.content.Context) : androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return SimViewModel(context) as T
    }
}
class NotebookViewModelFactory(private val context: android.content.Context) : androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return GameNotebookViewModel(context) as T
    }
}
