@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.fredsystems.aetheris

import android.os.Bundle
import androidx.activity.ComponentActivity
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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.fredsystems.aetheris.domain.model.PieceColor
import com.fredsystems.aetheris.domain.model.TimeControl
import com.fredsystems.aetheris.ui.analysis.AnalysisScreen
import com.fredsystems.aetheris.ui.board.ChessBoard
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AetherisTheme {
                val navController = rememberNavController()
                val vm: GameViewModel = viewModel(factory = GameViewModelFactory(this))
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
                                onOpenExternal = { navController.navigate("external") }
                            )
                        }
                        composable("analysis") { AnalysisScreen(vm) }
                        composable("sim") { SimScreen(simVm) { navController.popBackStack() } }
                        composable("notebook") { NotebookScreen(notebookVm) { navController.popBackStack() } }
                        composable("external") { ExternalEnginesScreen() }
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
    val flipped by vm.flipped
    val settings by vm.settings
    val whiteTime by vm.whiteTimeMs
    val blackTime by vm.blackTimeMs
    val timeControl = vm.timeControlSeconds.value
    var showNewGame by remember { mutableStateOf(false) }

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
                ModeChip("Analysis", mode == PlayMode.ANALYSIS, Modifier.weight(1f)) { vm.setMode(PlayMode.ANALYSIS) }
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
                            text = if (thinking) "Aetheris is thinking..." else "Depth ${opts.depth} · ${opts.mode.label}",
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
                    val heightLimit = (LocalConfiguration.current.screenHeightDp.dp - 440.dp).coerceAtLeast(160.dp)
                    val baseSize = minOf(widthLimit, heightLimit) * 0.72f
                    val boardSize = (baseSize * settings.boardScale.coerceIn(0.55f, 2f)).coerceIn(160.dp, widthLimit)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (evalVisible) {
                            EvaluationBar(score = eval, modifier = Modifier.height(boardSize).width(20.dp))
                            Spacer(Modifier.width(10.dp))
                        }
                        ChessBoard(
                            board = state,
                            onSquareClick = { vm.onSquareClick(it) },
                            modifier = Modifier.size(boardSize),
                            pieceSet = settings.pieceSet,
                            boardTheme = BoardThemes.byId(settings.boardTheme),
                            flipped = flipped
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }

    if (showNewGame) {
        NewGameDialog(
            defaultColor = when (vm.playerColor.value) {
                PieceColor.WHITE -> "white"
                PieceColor.BLACK -> "black"
            },
            currentTimeControl = settings.timeControl,
            onDismiss = { showNewGame = false },
            onStart = { color, tc ->
                if (tc != settings.timeControl) vm.updateSettings(vm.settings.value.copy(timeControl = tc))
                vm.newGame(color)
                showNewGame = false
            }
        )
    }
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
    onStart: (String, String) -> Unit
) {
    var color by remember { mutableStateOf(defaultColor) }
    var timeControl by remember { mutableStateOf(currentTimeControl) }
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
            Button(onClick = { onStart(color, timeControl) }) { Text("Start") }
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
