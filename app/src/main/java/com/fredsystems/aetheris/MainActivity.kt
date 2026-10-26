@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.fredsystems.aetheris

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
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
    val board by vm.board
    val eval by vm.evaluation
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Aetheris") }, actions = {
                TextButton(onClick = { navController.navigate("settings") }) { Text("Settings") }
                TextButton(onClick = { navController.navigate("analysis") }) { Text("Analysis") }
            })
        }
    ) { padding ->
        Row(modifier = Modifier.fillMaxSize().padding(padding)) {
            EvaluationBar(score = eval, modifier = Modifier.fillMaxHeight())
            ChessBoard(board = board, onSquareClick = { vm.onSquareClick(it) }, modifier = Modifier.weight(1f))
        }
        FloatingActionButton(onClick = { vm.requestBestMove(); vm.applyEngineMove() }, modifier = Modifier.padding(16.dp)) {
            Text("Engine")
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