@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.fredsystems.aetheris

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.fredsystems.aetheris.domain.model.PieceColor
import com.fredsystems.aetheris.ui.analysis.AnalysisScreen
import com.fredsystems.aetheris.ui.board.ChessBoard
import com.fredsystems.aetheris.ui.board.EvaluationBar
import com.fredsystems.aetheris.ui.notebook.NotebookScreen
import com.fredsystems.aetheris.ui.settings.ExternalEnginesScreen
import com.fredsystems.aetheris.ui.settings.SettingsScreen
import com.fredsystems.aetheris.ui.sim.SimScreen
import com.fredsystems.aetheris.ui.theme.AetherisTheme
import com.fredsystems.aetheris.viewmodel.GameNotebookViewModel
import com.fredsystems.aetheris.viewmodel.GameViewModel
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
                NavHost(navController = navController, startDestination = "game") {
                    composable("game") { GameScreen(vm, navController) }
                    composable("settings") {
                        SettingsScreen(vm,
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

@Composable
fun GameScreen(vm: GameViewModel, navController: androidx.navigation.NavController) {
    val state by vm.boardState
    val eval by vm.evaluation
    val thinking by vm.engineThinking
    val opts by vm.options

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0E0C14), Color(0xFF141020), Color(0xFF0A0910))
                )
            )
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 14.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Aetheris", fontSize = 26.sp, fontWeight = FontWeight.Black, color = Color(0xFFF2EEFF))
                    Text(opts.mode.label, fontSize = 11.sp, color = Color(0xFF9B93B8))
                }
                Row {
                    IconButton(onClick = { navController.navigate("analysis") }) {
                        Icon(Icons.Outlined.Analytics, null, tint = Color(0xFFC9C1E5))
                    }
                    IconButton(onClick = { navController.navigate("settings") }) {
                        Icon(Icons.Outlined.Settings, null, tint = Color(0xFFC9C1E5))
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFF1A1626),
                tonalElevation = 0.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.padding(horizontal = 15.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
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

            Spacer(Modifier.height(12.dp))

            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    val maxBoard = if (maxWidth < maxHeight) maxWidth else maxHeight
                    val boardSize = maxBoard - 34.dp
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        EvaluationBar(score = eval, modifier = Modifier.height(boardSize).width(20.dp))
                        Spacer(Modifier.width(10.dp))
                        ChessBoard(
                            board = state,
                            onSquareClick = { vm.onSquareClick(it) },
                            modifier = Modifier.size(boardSize)
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(
                Modifier.fillMaxWidth().padding(bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ControlButton(Icons.Outlined.Refresh, "New", Modifier.weight(1f)) { vm.newGame() }
                ControlButton(Icons.Outlined.Undo, "Undo", Modifier.weight(1f)) { vm.undoMove() }
                ControlButton(Icons.Outlined.AutoAwesome, "Engine", Modifier.weight(1.2f)) { vm.requestBestMove() }
            }
        }
    }
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
