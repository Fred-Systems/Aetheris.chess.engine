@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.fredsystems.aetheris.ui.settings

import android.graphics.BitmapFactory
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fredsystems.aetheris.domain.model.EngineOptions
import com.fredsystems.aetheris.domain.model.TimeControl
import com.fredsystems.aetheris.domain.model.EngineMode
import com.fredsystems.aetheris.ui.theme.BoardThemes
import com.fredsystems.aetheris.ui.theme.PieceSets
import com.fredsystems.aetheris.updater.GitHubUpdater
import com.fredsystems.aetheris.viewmodel.GameViewModel
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    viewModel: GameViewModel,
    onCheckUpdate: () -> Unit,
    onOpenSim: () -> Unit,
    onOpenNotebook: () -> Unit,
    onOpenExternal: () -> Unit,
    onExportCrashReport: () -> Unit = {},
    onImportNnue: () -> Unit = {}
) {
    val opts by viewModel.options
    val settings by viewModel.settings
    var aggression by remember { mutableStateOf(opts.aggression.toFloat()) }
    var positional by remember { mutableStateOf(opts.positional.toFloat()) }
    var complexity by remember { mutableStateOf(opts.complexity.toFloat()) }
    var solidity by remember { mutableStateOf(opts.solidity.toFloat()) }
    var depth by remember { mutableStateOf(opts.depth.toFloat()) }
    var selectedLevel by remember { mutableStateOf(
        when (opts.depth) {
            1 -> 2f; 2 -> 4f; 3 -> 6f; 4 -> 8f; 5 -> 10f
            6 -> 11f; 7 -> 12f; 8 -> 13f; 9 -> 14f; 10 -> 15f
            12 -> 16f; 14 -> 17f; 16 -> 18f; 18 -> 19f; 20 -> 20f
            else -> 15f
        }
    ) }
    var exactDepthMode by remember { mutableStateOf(false) }
    var showAllLevels by remember { mutableStateOf(false) }
    var presetExpanded by remember { mutableStateOf(false) }
    var pieceSetExpanded by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val updater = remember { GitHubUpdater(context) }
    val scope = rememberCoroutineScope()
    var updateTag by remember { mutableStateOf<String?>(null) }
    var updateUrl by remember { mutableStateOf<String?>(null) }
    var updateIsNewer by remember { mutableStateOf(false) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var checkingUpdate by remember { mutableStateOf(false) }

    fun applyPreset(name: String) {
        when (name) {
            "The Grandmaster" -> { aggression = 40f; positional = 80f; complexity = 30f; solidity = 80f }
            "The Maverick" -> { aggression = 70f; positional = 30f; complexity = 90f; solidity = 20f }
            "The Iron Wall" -> { aggression = 10f; positional = 90f; complexity = 10f; solidity = 100f }
            "The Tal Prodigy" -> { aggression = 100f; positional = 20f; complexity = 100f; solidity = 0f }
        }
    }

    fun resetAll() {
        val d = EngineOptions()
        aggression = d.aggression.toFloat()
        positional = d.positional.toFloat()
        complexity = d.complexity.toFloat()
        solidity = d.solidity.toFloat()
        depth = d.depth.toFloat()
        selectedLevel = 15f
        exactDepthMode = false
        showAllLevels = false
        viewModel.resetAllSettings()
    }

    fun downloadApkToDownloads(url: String) {
        runCatching {
            val request = DownloadManager.Request(Uri.parse(url))
                .setTitle("Aetheris APK")
                .setDescription("Download the installer without installing it")
                .setMimeType("application/vnd.android.package-archive")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(false)
                .setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS,
                    "Aetheris-${System.currentTimeMillis()}.apk"
                )
            (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
            Toast.makeText(context, "APK download started. It will appear in Downloads.", Toast.LENGTH_LONG).show()
        }.onFailure {
            Toast.makeText(context, "Could not start APK download: ${it.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun checkUpdate() {
        checkingUpdate = true
        scope.launch {
            val json = updater.fetchLatest()
            val tag = json?.optString("tag_name")
            val remoteCode = tag?.trimStart('v')?.replace(".", "")?.toLongOrNull() ?: 0L
            val url = json?.optJSONArray("assets")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val a = arr.getJSONObject(i)
                    if (a.optString("name").endsWith(".apk")) return@let a.optString("browser_download_url")
                }
                null
            }
            updateTag = tag
            updateUrl = url
            updateIsNewer = remoteCode > updater.currentVersionCode()
            checkingUpdate = false
            showUpdateDialog = true
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp)
    ) {
        Text("Settings", fontSize = 28.sp, fontWeight = FontWeight.Black, color = Color(0xFFF2EEFF))
        Text("Appearance, gameplay and engine personality.", fontSize = 13.sp, color = Color(0xFF9B93B8))
        Spacer(Modifier.height(16.dp))

        SettingsCard("Gameplay") {
            Text("Default color", color = Color(0xFFF2EEFF), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(7.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf("white" to "White", "black" to "Black", "random" to "Random").forEach { (id, label) ->
                    ModeChip(label, settings.playerColor == id, Modifier.weight(1f)) {
                        viewModel.updateSettings(settings.copy(playerColor = id))
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("Time control", color = Color(0xFFF2EEFF), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(7.dp))
            TimeControl.all.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    row.forEach { tc ->
                        ModeChip(tc.label, settings.timeControl == tc.id, Modifier.weight(1f)) {
                            viewModel.updateSettings(settings.copy(timeControl = tc.id))
                        }
                    }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
                Spacer(Modifier.height(6.dp))
            }
            Spacer(Modifier.height(6.dp))
            Text("Time per move", color = Color(0xFFF2EEFF), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(7.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf(0 to "Off", 15 to "15s", 30 to "30s", 60 to "1m", 120 to "2m").forEach { (sec, label) ->
                    ModeChip(label, settings.moveTimeSeconds == sec, Modifier.weight(1f)) {
                        viewModel.updateSettings(settings.copy(moveTimeSeconds = sec))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            LabeledSlider(
                title = "Custom move time",
                valueText = if (settings.moveTimeSeconds == 0) "Off" else "${settings.moveTimeSeconds}s",
                value = settings.moveTimeSeconds.toFloat(),
                range = 0f..300f
            ) { viewModel.updateSettings(settings.copy(moveTimeSeconds = it.toInt())) }
            Spacer(Modifier.height(4.dp))
            SwitchRow("Show eval in Play", settings.showEvalPlay) {
                viewModel.updateSettings(settings.copy(showEvalPlay = it))
            }
            SwitchRow("Show eval in Analysis", settings.showEvalAnalysis) {
                viewModel.updateSettings(settings.copy(showEvalAnalysis = it))
            }
            SwitchRow("Show best-move arrow in Analysis", settings.showBestMoveArrow) {
                viewModel.updateSettings(settings.copy(showBestMoveArrow = it))
            }
            SwitchRow("Show multiple engine-thinking arrows", settings.showEngineThinkingArrows) {
                viewModel.updateSettings(settings.copy(showEngineThinkingArrows = it))
            }
            Text("Arrow legend", color = Color(0xFFB9B2CE), fontSize = 12.sp)
            Text("Green = engine's top line; blue = second candidate; amber = third candidate. Multi-arrow mode shows the engine's leading candidate moves, not a promise that every arrow is equally strong.", color = Color(0xFF9B93B8), fontSize = 11.sp)
            SwitchRow("Show PGN panel by default on large screens", settings.showPgnPanel) {
                viewModel.updateSettings(settings.copy(showPgnPanel = it))
            }
            Divider(color = Color(0xFF393248), modifier = Modifier.padding(vertical = 8.dp))
            Text("Advanced play", color = Color(0xFFF2EEFF), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            SwitchRow("Blindfold mode (hide pieces)", settings.blindfoldMode) {
                viewModel.updateSettings(settings.copy(blindfoldMode = it))
            }
            SwitchRow("Enable premove (default on)", settings.premoveEnabled) {
                viewModel.updateSettings(settings.copy(premoveEnabled = it))
            }
            SwitchRow("Allow multiple premoves", settings.multiplePremoves) {
                viewModel.updateSettings(settings.copy(multiplePremoves = it))
            }
            if (settings.multiplePremoves) {
                Text(
                    "Queue up to 12 premoves in order. Each is attempted on your next turn after the engine replies; if the next queued move is illegal, the entire remaining queue is cancelled.",
                    color = Color(0xFF9B93B8), fontSize = 11.sp
                )
            }
            SwitchRow("Enable conditional moves", settings.conditionalMoves) {
                viewModel.updateSettings(settings.copy(conditionalMoves = it))
            }
            if (settings.conditionalMoves) {
                Text(
                    "On the engine's turn, preview a hypothetical opponent move on the board, then make your reply. The preview is not played; your reply is triggered only if the engine later plays that exact move.",
                    color = Color(0xFF9B93B8), fontSize = 11.sp
                )
            }
            SwitchRow("Chess960 random starting position", settings.chess960Enabled) {
                viewModel.updateSettings(settings.copy(chess960Enabled = it))
            }
            Text(
                "Chess960 uses random back-rank positions. The current native engine does not yet support Chess960 castling, so this variant is experimental against the engine.",
                color = Color(0xFFE0B878), fontSize = 11.sp
            )
        }

        Spacer(Modifier.height(12.dp))
        SettingsCard("Board sounds") {
            SwitchRow("Mute board sounds", !settings.moveSoundsEnabled) {
                viewModel.updateSettings(settings.copy(moveSoundsEnabled = !it))
            }
            Text("Uses Lichess Standard move/capture audio, loaded from Lichess on first use and cached privately. Internet is needed for the initial sound download.",
                color = Color(0xFF9B93B8), fontSize = 11.sp)
            LabeledSlider(
                title = "Sound volume",
                valueText = "${settings.moveSoundVolume}%",
                value = settings.moveSoundVolume.toFloat(),
                range = 0f..100f
            ) { viewModel.updateSettings(settings.copy(moveSoundVolume = it.toInt().coerceIn(0, 100))) }
        }

        Spacer(Modifier.height(12.dp))
        SettingsCard("Engine") {
            SwitchRow("Ponder during my turn", settings.pondering) {
                viewModel.updateSettings(settings.copy(pondering = it))
            }
            Text(
                "Searches replies to several plausible moves while you think. Matching cached replies can be reused; this increases CPU use, battery drain, and heat.",
                color = Color(0xFF9B93B8), fontSize = 11.sp
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Persona presets", fontWeight = FontWeight.SemiBold, color = Color(0xFFF2EEFF), fontSize = 13.sp)
                Box {
                    OutlinedButton(onClick = { presetExpanded = true }, shape = RoundedCornerShape(11.dp)) { Text("Choose") }
                    DropdownMenu(expanded = presetExpanded, onDismissRequest = { presetExpanded = false }) {
                        listOf("The Grandmaster", "The Maverick", "The Iron Wall", "The Tal Prodigy").forEach { p ->
                            DropdownMenuItem(text = { Text(p) }, onClick = { applyPreset(p); presetExpanded = false })
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            val depthForLevel: (Int) -> Int = { level ->
                when (level) {
                    1 -> 1; 2 -> 1; 3 -> 2; 4 -> 2; 5 -> 3
                    6 -> 3; 7 -> 4; 8 -> 4; 9 -> 5; 10 -> 5
                    11 -> 6; 12 -> 7; 13 -> 8; 14 -> 9; 15 -> 10
                    16 -> 12; 17 -> 14; 18 -> 16; 19 -> 18; else -> 20
                }
            }
            val timeForDepth: (Int) -> Long = { d ->
                (250L + d.toLong() * d.toLong() * 35L).coerceIn(500L, 8000L)
            }
            SettingsCard(if (exactDepthMode) "Exact search depth: ${depth.toInt()}" else "Playing level: ${selectedLevel.toInt()} / 20") {
                SwitchRow("Adjust exact search depth instead", exactDepthMode) {
                    exactDepthMode = it
                    if (!it) {
                        selectedLevel = (1..20).minByOrNull { level -> kotlin.math.abs(depthForLevel(level) - depth.toInt()) }?.toFloat() ?: 6f
                        depth = depthForLevel(selectedLevel.toInt()).toFloat()
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (exactDepthMode) {
                    Text("Directly sets the engine's requested search depth.", color = Color(0xFF9B93B8), fontSize = 12.sp)
                    Slider(value = depth.coerceIn(1f, 24f), onValueChange = { depth = it }, valueRange = 1f..24f, steps = 22)
                    Text("Depth ${depth.toInt()} · suggested budget ~${timeForDepth(depth.toInt())} ms per move", color = Color(0xFFEDE8FF), fontSize = 12.sp)
                } else {
                    Text(
                        when {
                            selectedLevel < 4f -> "Beginner — quick, shallow searches"
                            selectedLevel < 7f -> "Casual — relaxed games"
                            selectedLevel < 11f -> "Club — deeper tactical calculation"
                            selectedLevel < 16f -> "Expert — stronger calculation"
                            else -> "Maximum — deepest available search; may take several seconds"
                        },
                        color = Color(0xFF9B93B8), fontSize = 12.sp
                    )
                    Slider(
                        value = selectedLevel,
                        onValueChange = { selectedLevel = it; depth = depthForLevel(it.toInt()).toFloat() },
                        valueRange = 1f..20f, steps = 18
                    )
                    Text("Selected parameters: search depth ${depth.toInt()} · nominal think budget ~${timeForDepth(depth.toInt())} ms per move",
                        color = Color(0xFFEDE8FF), fontSize = 12.sp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Beginner", color = Color(0xFF9B93B8), fontSize = 10.sp)
                        Text("Strongest", color = Color(0xFF9B93B8), fontSize = 10.sp)
                    }
                    TextButton(onClick = { showAllLevels = !showAllLevels }) {
                        Text(if (showAllLevels) "Hide level parameters" else "Show all 20 level parameters")
                    }
                    if (showAllLevels) {
                        Column(Modifier.fillMaxWidth().background(Color(0xFF171322), RoundedCornerShape(10.dp)).padding(9.dp)) {
                            (1..20).forEach { level ->
                                val d = depthForLevel(level)
                                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Level %02d".format(level), color = Color(0xFFB9B2CE), fontSize = 11.sp)
                                    Text("Depth $d", color = Color(0xFFEDE8FF), fontSize = 11.sp)
                                    Text("~${timeForDepth(d)} ms", color = Color(0xFFB9A8FF), fontSize = 11.sp)
                                }
                            }
                            Text("Think time is a budget, not a guarantee. Clock settings and device speed can change actual search time.",
                                color = Color(0xFF9B93B8), fontSize = 10.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        viewModel.updateOptions(
                            EngineOptions(aggression.toInt(), positional.toInt(), complexity.toInt(), solidity.toInt(), opts.materialWeight, depth.toInt(), EngineMode.PURE_CLASSICAL)
                        )
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp)
                ) { Text("Apply engine") }
                OutlinedButton(onClick = { resetAll() }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                    Text("Reset all defaults")
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        SettingsCard("Persona Customization") {
            PersonalitySlider("Aggression", "Defense", aggression) { aggression = it }
            PersonalitySlider("Positional", "Tactical", positional) { positional = it }
            PersonalitySlider("Complexity", "Simplification", complexity) { complexity = it }
            PersonalitySlider("Solidity", "Activity", solidity) { solidity = it }
        }

        Spacer(Modifier.height(12.dp))
        SettingsCard("Layout") {
            LabeledSlider(
                title = "App density",
                valueText = "${(settings.uiScale * 100).toInt()}%",
                value = settings.uiScale,
                range = 0.75f..1.5f
            ) { viewModel.updateSettings(settings.copy(uiScale = it)) }
            LabeledSlider(
                title = "Board size",
                valueText = "${(settings.boardScale * 100).toInt()}%",
                value = settings.boardScale,
                range = 0.75f..3.0f
            ) { viewModel.updateSettings(settings.copy(boardScale = it)) }
            LabeledSlider(
                title = "Analysis autoplay interval",
                valueText = "${settings.analysisAutoplaySeconds}s per move",
                value = settings.analysisAutoplaySeconds.toFloat(),
                range = 1f..15f
            ) { viewModel.updateSettings(settings.copy(analysisAutoplaySeconds = it.toInt().coerceIn(1, 15))) }
            LabeledSlider(
                title = "Engine analysis lines",
                valueText = settings.analysisLineCount.toString(),
                value = settings.analysisLineCount.toFloat(),
                range = 1f..5f
            ) { viewModel.updateSettings(settings.copy(analysisLineCount = it.toInt().coerceIn(1, 5))) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { viewModel.updateSettings(settings.copy(uiScale = 1f, boardScale = 1.8f)) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Reset layout") }
                OutlinedButton(
                    onClick = { viewModel.updateSettings(settings.copy(flipped = !settings.flipped)) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) { Text(if (settings.flipped) "Unflip board" else "Flip board") }
            }
        }

        Spacer(Modifier.height(12.dp))
        SettingsCard("Board & Pieces") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Piece style", color = Color(0xFFF2EEFF), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Box {
                    OutlinedButton(onClick = { pieceSetExpanded = true }, shape = RoundedCornerShape(11.dp)) {
                        Text(PieceSets.byId(settings.pieceSet).label)
                    }
                    DropdownMenu(expanded = pieceSetExpanded, onDismissRequest = { pieceSetExpanded = false }) {
                        PieceSets.all.forEach { set ->
                            DropdownMenuItem(
                                text = { Text(set.label) },
                                onClick = {
                                    viewModel.updateSettings(settings.copy(pieceSet = set.id))
                                    pieceSetExpanded = false
                                }
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            PieceStrip(settings.pieceSet)
            Spacer(Modifier.height(14.dp))
            Text("Board theme", color = Color(0xFFF2EEFF), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            BoardThemes.all.chunked(5).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { theme ->
                        ThemeSwatch(
                            light = theme.light,
                            dark = theme.dark,
                            selected = settings.boardTheme == theme.id,
                            modifier = Modifier.weight(1f)
                        ) { viewModel.updateSettings(settings.copy(boardTheme = theme.id)) }
                    }
                    repeat(5 - row.size) { Spacer(Modifier.weight(1f)) }
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        Spacer(Modifier.height(20.dp))
        Text("More", color = Color(0xFF9B93B8), fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))
        MenuRow("Engine vs Engine Simulation", onOpenSim)
        MenuRow("Game Notebook", onOpenNotebook)
        MenuRow("External Engines", onOpenExternal)
        if (context.packageName.endsWith(".crashreport")) {
            MenuRow("Export crash report", onExportCrashReport)
        }
        MenuRow(if (checkingUpdate) "Checking..." else "Check for Updates", { checkUpdate() })
        MenuRow("Download Latest APK to Downloads", {
            scope.launch {
                val json = updater.fetchLatest()
                val url = json?.optJSONArray("assets")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val a = arr.getJSONObject(i)
                        if (a.optString("name") == "Aetheris.apk") return@let a.optString("browser_download_url")
                    }
                    null
                }
                if (!url.isNullOrBlank()) {
                    runCatching {
                        val request = DownloadManager.Request(Uri.parse(url))
                            .setTitle("Aetheris latest APK")
                            .setDescription("Download the latest Aetheris installer without installing it")
                            .setMimeType("application/vnd.android.package-archive")
                            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                            .setAllowedOverMetered(true)
                            .setAllowedOverRoaming(false)
                            .setDestinationInExternalPublicDir(
                                Environment.DIRECTORY_DOWNLOADS,
                                "Aetheris-${System.currentTimeMillis()}.apk"
                            )
                        (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
                        Toast.makeText(context, "APK download started. It will appear in Downloads.", Toast.LENGTH_LONG).show()
                    }.onFailure {
                        Toast.makeText(context, "Could not start APK download: ${it.message}", Toast.LENGTH_LONG).show()
                    }
                } else {
                    Toast.makeText(context, "No APK was found in the latest release.", Toast.LENGTH_LONG).show()
                }
            }
        })
        SettingsCard("Aetheris Neural Network") {
            Text(
                "This is the beginning of training for the Neural Network edition of Aetheris. The current playable engine remains the classical Aetheris engine; neural evaluation is not enabled in this release.",
                color = Color(0xFFB9B2CE), fontSize = 12.sp
            )
            Spacer(Modifier.height(8.dp))
            MenuRow("Download Neural Network training preview", {
                runCatching {
                    val url = "https://raw.githubusercontent.com/Fred-Systems/Aetheris.chess.engine/main/app/src/main/assets/nnue/weights.bin"
                    val request = DownloadManager.Request(Uri.parse(url))
                        .setTitle("Aetheris Neural Network training preview")
                        .setDescription("Early training asset; not an active engine")
                        .setMimeType("application/octet-stream")
                        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                        .setAllowedOverMetered(true)
                        .setAllowedOverRoaming(false)
                        .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "Aetheris-NN-Training-Preview.bin")
                    (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
                    Toast.makeText(context, "Neural Network training preview download started.", Toast.LENGTH_LONG).show()
                }.onFailure {
                    Toast.makeText(context, "Could not download preview: ${it.message}", Toast.LENGTH_LONG).show()
                }
            })
        }

        SettingsCard("Standalone Engines") {
            Text("Download the Aetheris Classic UCI engine for compatible external chess applications.", color = Color(0xFFB9B2CE), fontSize = 12.sp)
            MenuRow("Download Aetheris Classic", {
                runCatching {
                    val url = "https://github.com/Fred-Systems/Aetheris.chess.engine/releases/latest/download/Aetheris-Classical-Engine-Android.zip"
                    val request = DownloadManager.Request(Uri.parse(url))
                        .setTitle("Aetheris Classic")
                        .setDescription("Standalone Android UCI engine")
                        .setMimeType("application/zip")
                        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                        .setAllowedOverMetered(true)
                        .setAllowedOverRoaming(false)
                        .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "Aetheris-Classic-Android.zip")
                    (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
                    Toast.makeText(context, "Aetheris Classic download started.", Toast.LENGTH_LONG).show()
                }.onFailure { Toast.makeText(context, "Download failed: ${it.message}", Toast.LENGTH_LONG).show() }
            })
        }

        Spacer(Modifier.height(30.dp))
        Text(
            "Developed by Fred-Systems",
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF6F6887)
        )
        Spacer(Modifier.height(20.dp))
    }

    if (showUpdateDialog) {
        AlertDialog(
            onDismissRequest = { showUpdateDialog = false },
            title = { Text("Update", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Latest release: ${updateTag ?: "unknown"}")
                    Text(if (updateIsNewer) "A newer version is available." else "You are up to date.")
                    if (updateUrl != null) Text("APK: $updateUrl", fontSize = 11.sp, color = Color(0xFF9B93B8))
                }
            },
            confirmButton = {
                if (updateIsNewer && updateUrl != null) {
                    Button(onClick = {
                        updater.downloadAndInstall(updateUrl!!)
                        showUpdateDialog = false
                    }) { Text("Update now") }
                } else {
                    TextButton(onClick = { showUpdateDialog = false }) { Text("OK") }
                }
            },
            dismissButton = {
                if (updateUrl != null) {
                    TextButton(onClick = {
                        downloadApkToDownloads(updateUrl!!)
                        showUpdateDialog = false
                    }) { Text("Download APK to Downloads") }
                }
            }
        )
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = Color(0xFF1A1626), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, color = Color(0xFFF2EEFF), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun LabeledSlider(
    title: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, color = Color(0xFFF2EEFF), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Text(valueText, color = Color(0xFFB9A8FF), fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
    Slider(value = value, onValueChange = onChange, valueRange = range)
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color(0xFFE7E1FA), fontSize = 13.sp)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ThemeSwatch(
    light: Color,
    dark: Color,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .aspectRatio(1f)
            .then(if (selected) Modifier.border(2.dp, Color(0xFFB9A8FF), RoundedCornerShape(10.dp)) else Modifier),
        shape = RoundedCornerShape(10.dp),
        onClick = onClick,
        color = Color.Transparent
    ) {
        Column {
            Box(Modifier.fillMaxWidth().weight(1f).background(light))
            Box(Modifier.fillMaxWidth().weight(1f).background(dark))
        }
    }
}

@Composable
private fun PieceStrip(set: String) {
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        listOf("wK", "wQ", "wR", "wB", "wN", "wP", "bK", "bQ", "bR", "bB", "bN", "bP").forEach { code ->
            val bitmap = remember(set, code) {
                runCatching {
                    context.assets.open("pieces/$set/$code.png").use { BitmapFactory.decodeStream(it).asImageBitmap() }
                }.getOrNull()
            }
            if (bitmap != null) {
                Image(bitmap, null, Modifier.size(22.dp))
            }
        }
    }
}

@Composable
private fun ModeChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(11.dp),
        color = if (selected) Color(0xFF7C4DFF) else Color(0xFF262136),
        onClick = onClick
    ) {
        Text(
            label,
            modifier = Modifier.padding(vertical = 9.dp),
            textAlign = TextAlign.Center,
            color = if (selected) Color.White else Color(0xFFB9B2CE),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun PersonalitySlider(left: String, right: String, value: Float, onChange: (Float) -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = Color(0xFF1A1626), modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(left, color = Color(0xFFF2EEFF), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text("${value.toInt()}", color = Color(0xFFB9A8FF), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(right, color = Color(0xFF9B93B8), fontSize = 13.sp)
            }
            Slider(value = value, onValueChange = onChange, valueRange = 0f..100f)
        }
    }
}

@Composable
private fun MenuRow(label: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF1A1626),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        onClick = onClick
    ) {
        Text(label, Modifier.padding(15.dp), color = Color(0xFFE7E1FA), fontSize = 14.sp)
    }
}
