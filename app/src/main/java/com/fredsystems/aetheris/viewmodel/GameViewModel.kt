package com.fredsystems.aetheris.viewmodel

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fredsystems.aetheris.CrashDiagnostics
import com.fredsystems.aetheris.data.GameRepository
import com.fredsystems.aetheris.domain.model.GameRecord
import android.content.ClipData
import android.content.ClipboardManager
import android.media.AudioManager
import android.media.ToneGenerator
import com.fredsystems.aetheris.domain.model.AppSettings
import com.fredsystems.aetheris.domain.model.BoardState
import com.fredsystems.aetheris.domain.model.EngineMode
import com.fredsystems.aetheris.domain.model.EngineOptions
import com.fredsystems.aetheris.domain.model.Piece
import com.fredsystems.aetheris.domain.model.PieceColor
import com.fredsystems.aetheris.domain.model.PieceType
import com.fredsystems.aetheris.domain.model.SettingsStore
import com.fredsystems.aetheris.domain.model.TimeControl
import com.fredsystems.aetheris.engine.NativeEngine
import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.Piece as LibPiece
import com.github.bhlangonijr.chesslib.PieceType as LibPieceType
import com.github.bhlangonijr.chesslib.Side
import com.github.bhlangonijr.chesslib.Square as LibSquare
import com.github.bhlangonijr.chesslib.move.Move as LibMove
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.math.abs
import kotlin.random.Random

enum class PlayMode { PLAY, ANALYSIS }

data class EngineAnalysisLine(val moves: List<String>, val scoreCp: Int)

class GameViewModel(private val context: Context) : ViewModel() {
    private val store = SettingsStore(context)
    private var board = Board()
    private val moveList = mutableListOf<String>()
    private val positionHistory = mutableListOf<String>()
    val historyIndex = mutableStateOf(0)
    val historyLength = mutableStateOf(1)
    private val settingsStore = store
    val settings = mutableStateOf(store.load())

    val boardState = mutableStateOf(toUi(board))
    val evaluation = mutableStateOf(0)
    val analysisLines = mutableStateOf<List<EngineAnalysisLine>>(emptyList())
    val bestMoveUci = mutableStateOf<String?>(null)
    val analysisLoading = mutableStateOf(false)
    val nnueStatus = mutableStateOf("Checking neural network…")
    val pgnStatus = mutableStateOf("")
    val options = mutableStateOf(EngineOptions())
    val engineThinking = mutableStateOf(false)
    val engineAvailable = mutableStateOf(true)
    val mode = mutableStateOf(PlayMode.PLAY)
    val evalVisible = mutableStateOf(false)
    val flipped = mutableStateOf(settings.value.flipped)
    val playerColor = mutableStateOf(if (settings.value.playerColor == "black") PieceColor.BLACK else PieceColor.WHITE)
    val whiteTimeMs = mutableStateOf(0L)
    val blackTimeMs = mutableStateOf(0L)
    val timeControlSeconds = mutableStateOf(TimeControl.byId(settings.value.timeControl).seconds)
    val moveTimeSeconds = mutableStateOf(settings.value.moveTimeSeconds)

    private var engineGeneration = 0
    private var clockJob: Job? = null
    private var ponderJob: Job? = null
    private val ponderCache = mutableMapOf<String, String>()
    private var bestAnalysisFen = ""
    private var bestAnalysisDepth = 0
    private var bestAnalysisScoreForMover = Int.MIN_VALUE

    init {
        positionHistory.add(board.fen)
        runCatching {
            NativeEngine.initialize()
            applyNativeOptions(options.value)
            val bundledNnue = ensureNnueAsset()
            val customNnue = java.io.File(context.filesDir, "nnue/custom_weights.bin")
            (if (customNnue.isFile) customNnue.absolutePath else bundledNnue)?.let { NativeEngine.setNnue(it) }
            if (NativeEngine.isNnueLoaded()) {
                val configuredOptions = options.value
                applyNativeOptions(configuredOptions.copy(mode = EngineMode.PURE_NNUE))
                val startPositionCp = NativeEngine.evaluateFen(Board().fen)
                applyNativeOptions(configuredOptions)
                nnueStatus.value = if (abs(startPositionCp) > 100) {
                    "NNUE loaded with calibration warning ($startPositionCp cp at start); NNUE mode remains available."
                } else {
                    "NNUE loaded successfully; start position $startPositionCp cp."
                }
            } else {
                nnueStatus.value = "NNUE Load Failed — Classical fallback active"
                android.util.Log.e("AetherisNNUE", "NNUE Load Failed: bundled and custom network unavailable")
            }
        }.onFailure { engineAvailable.value = false }
        evalVisible.value = settings.value.showEvalPlay
        moveTimeSeconds.value = settings.value.moveTimeSeconds
        resetClocks()
        startClockLoop()
        updateEvaluationAsync()
        maybeEngineMove()
    }

    fun onSquareClick(index: Int) {
        if (boardState.value.gameOver) return
        val playerSide = board.sideToMove == Side.WHITE
        if (mode.value == PlayMode.PLAY) {
            val userIsWhite = playerColor.value == PieceColor.WHITE
            if (playerSide != userIsWhite) return
        }
        val sq = LibSquare.squareAt(index)
        val selected = boardState.value.selectedSquare
        if (selected != null) {
            val from = LibSquare.squareAt(selected)
            val legal = findLegalMove(from, sq)
            if (legal != null) {
                makeMove(legal, allowEngineReply = true)
                return
            }
            val clicked = board.getPiece(sq)
            if (clicked != LibPiece.NONE && clicked.pieceSide == board.sideToMove) {
                selectSquare(index)
            } else {
                boardState.value = boardState.value.copy(selectedSquare = null, legalTargets = emptySet())
            }
        } else {
            val clicked = board.getPiece(sq)
            if (clicked != LibPiece.NONE && clicked.pieceSide == board.sideToMove) {
                selectSquare(index)
            }
        }
    }

    private fun selectSquare(index: Int) {
        val sq = LibSquare.squareAt(index)
        val targets = board.legalMoves()
            .filter { it.from == sq }
            .map { it.to.ordinal }
            .toSet()
        boardState.value = boardState.value.copy(selectedSquare = index, legalTargets = targets)
    }

    private fun findLegalMove(from: LibSquare, to: LibSquare): LibMove? {
        val candidates = board.legalMoves().filter { it.from == from && it.to == to }
        if (candidates.isEmpty()) return null
        return candidates.firstOrNull { it.promotion != LibPiece.NONE && it.promotion.pieceType == LibPieceType.QUEEN }
            ?: candidates.first()
    }

    private fun makeMove(move: LibMove, allowEngineReply: Boolean) {
        val beforeFen = board.fen
        val moveUci = move.toString().lowercase()
        val ponderKey = "${beforeFen}|${moveUci}"
        val cachedPonderReply = if (allowEngineReply && settings.value.pondering) ponderCache[ponderKey] else null
        ponderJob?.cancel()
        ponderJob = null
        ponderCache.keys.removeAll { it.startsWith("${beforeFen}|") }
        val from = move.from.ordinal
        val to = move.to.ordinal
        if (!board.doMove(move)) return
        playMoveSound(board.isKingAttacked())
        val currentIndex = historyIndex.value.coerceIn(0, positionHistory.lastIndex)
        while (positionHistory.size > currentIndex + 1) positionHistory.removeAt(positionHistory.lastIndex)
        while (moveList.size > currentIndex) moveList.removeAt(moveList.lastIndex)
        moveList.add(move.toString())
        positionHistory.add(board.fen)
        historyIndex.value = positionHistory.lastIndex
        historyLength.value = positionHistory.size
        val next = toUi(board).copy(
            selectedSquare = null,
            legalTargets = emptySet(),
            lastMoveFrom = from,
            lastMoveTo = to,
            moveNumber = historyIndex.value
        )
        boardState.value = next
        if (moveTimeSeconds.value > 0) resetClocks()
        updateEvaluationAsync()
        if (allowEngineReply) {
            val legalCachedReply = cachedPonderReply?.takeIf { uci ->
                board.legalMoves().any { it.toString().equals(uci, ignoreCase = true) }
            }
            if (legalCachedReply != null) applyUciMove(legalCachedReply)
            else maybeEngineMove()
        } else {
            startPonderingIfNeeded()
        }
    }

    private fun playMoveSound(check: Boolean) {
        val prefs = settings.value
        if (!prefs.moveSoundsEnabled || prefs.moveSoundVolume <= 0) return
        runCatching {
            val tone = ToneGenerator(AudioManager.STREAM_MUSIC, prefs.moveSoundVolume.coerceIn(1, 100))
            tone.startTone(if (check) ToneGenerator.TONE_PROP_BEEP else ToneGenerator.TONE_PROP_ACK, 110)
            viewModelScope.launch {
                delay(180)
                tone.release()
            }
        }.onFailure { android.util.Log.w("AetherisAudio", "Could not play move sound", it) }
    }

    private fun startPonderingIfNeeded() {
        if (!settings.value.pondering || mode.value != PlayMode.PLAY ||
            boardState.value.gameOver || !engineAvailable.value || engineThinking.value) return
        val userSide = if (playerColor.value == PieceColor.WHITE) Side.WHITE else Side.BLACK
        if (board.sideToMove != userSide) return
        val rootFen = board.fen
        val candidates = board.legalMoves().sortedByDescending { move ->
            if (board.getPiece(move.to) != LibPiece.NONE) 2 else if (move.promotion != LibPiece.NONE) 3 else 0
        }.take(6).map { it.toString().lowercase() }
        if (candidates.isEmpty()) return
        ponderJob?.cancel()
        ponderJob = viewModelScope.launch {
            for (userMove in candidates) {
                if (!isActive || board.fen != rootFen || !settings.value.pondering) break
                val reply = withContext(Dispatchers.Default) {
                    runCatching {
                        val speculative = Board()
                        speculative.loadFromFen(rootFen)
                        val candidate = speculative.legalMoves().firstOrNull {
                            it.toString().equals(userMove, ignoreCase = true)
                        } ?: return@runCatching ""
                        if (!speculative.doMove(candidate)) return@runCatching ""
                        NativeEngine.searchPosition(speculative.fen, 5, 350).lowercase()
                    }.getOrDefault("")
                }
                if (reply.length >= 4 && isActive && board.fen == rootFen && settings.value.pondering) {
                    ponderCache["$rootFen|$userMove"] = reply
                }
            }
        }
    }

    fun startGameFromHere(colorChoice: String, timeControlId: String, depth: Int, engineMode: EngineMode) {
        ponderJob?.cancel()
        ponderJob = null
        ponderCache.clear()
        val target = historyIndex.value.coerceIn(0, positionHistory.lastIndex)
        runCatching { board.loadFromFen(positionHistory[target]) }
        while (positionHistory.size > target + 1) positionHistory.removeAt(positionHistory.lastIndex)
        while (moveList.size > target) moveList.removeAt(moveList.lastIndex)
        historyIndex.value = target
        historyLength.value = positionHistory.size
        val chosen = when (colorChoice) {
            "black" -> PieceColor.BLACK
            "random" -> if (Random.nextBoolean()) PieceColor.WHITE else PieceColor.BLACK
            else -> PieceColor.WHITE
        }
        playerColor.value = chosen
        flipped.value = chosen == PieceColor.BLACK
        mode.value = PlayMode.PLAY
        engineGeneration++
        engineThinking.value = false
        analysisLines.value = emptyList()
        bestMoveUci.value = null
        options.value = options.value.copy(depth = depth.coerceIn(1, 20), mode = engineMode)
        applyNativeOptions(options.value)
        val tc = TimeControl.byId(timeControlId)
        timeControlSeconds.value = tc.seconds
        moveTimeSeconds.value = settings.value.moveTimeSeconds
        saveSettings(settings.value.copy(
            playerColor = if (chosen == PieceColor.WHITE) "white" else "black",
            timeControl = tc.id,
            flipped = flipped.value
        ))
        resetClocks()
        boardState.value = toUi(board).copy(moveNumber = target)
        updateEvaluationAsync()
        if ((board.sideToMove == Side.WHITE) == (chosen == PieceColor.WHITE)) startPonderingIfNeeded()
        else maybeEngineMove()
    }

    private fun maybeEngineMove() {
        if (mode.value != PlayMode.PLAY) return
        if (boardState.value.gameOver) return
        val userIsWhite = playerColor.value == PieceColor.WHITE
        val sideIsWhite = board.sideToMove == Side.WHITE
        if (sideIsWhite == userIsWhite) return
        startEngineSearch(force = false)
    }

    fun forceEngineMove() {
        if (boardState.value.gameOver || engineThinking.value) return
        startEngineSearch(force = true)
    }

    private fun startEngineSearch(force: Boolean) {
        if (!engineAvailable.value) return
        val fen = board.fen
        val searchOptions = options.value
        val depth = searchOptions.depth
        val perMove = moveTimeSeconds.value
        val timed = timeControlSeconds.value > 0
        val remaining = if (board.sideToMove == Side.WHITE) whiteTimeMs.value else blackTimeMs.value
        val timeMs = when {
            perMove > 0 -> (perMove * 1000L).coerceAtMost(30000L)
            timed && remaining > 0 -> min((250L + depth.toLong() * depth.toLong() * 35L).coerceAtMost(8000L), remaining / 12 + 80L)
            else -> (250L + depth.toLong() * depth.toLong() * 35L).coerceIn(500L, 8000L)
        }
        val generation = ++engineGeneration
        engineThinking.value = true
        viewModelScope.launch {
            val move = withContext(Dispatchers.Default) {
                CrashDiagnostics.searchStarted(context, fen, depth, timeMs.toInt())
                runCatching {
                    NativeEngine.searchPositionConfigured(
                        fen, depth, timeMs.toInt(),
                        searchOptions.aggression, searchOptions.positional, searchOptions.complexity,
                        searchOptions.solidity, searchOptions.materialWeight, searchOptions.mode.ordinal
                    ).also { move ->
                        CrashDiagnostics.searchCompleted(context, move)
                    }
                }.onFailure { error -> CrashDiagnostics.recordException(context, "NativeEngine.searchPosition", error) }.getOrNull()
            }
            CrashDiagnostics.recordStage(context, "SEARCH_RESULT_HANDOFF", "generation=$generation currentGeneration=$engineGeneration move=${move ?: "<null>"}")
            if (generation != engineGeneration) {
                CrashDiagnostics.recordStage(context, "SEARCH_RESULT_DISCARDED", "stale generation")
                return@launch
            }
            engineThinking.value = false
            if (move.isNullOrBlank()) {
                // Never substitute a random legal move for a failed engine search.
                engineAvailable.value = false
                nnueStatus.value = "Engine search failed. Switch to Classical mode or restart the app; no random move was played."
                CrashDiagnostics.recordStage(context, "SEARCH_FAILED_NO_MOVE", "force=$force fen=$fen")
                return@launch
            }
            if (generation != engineGeneration) {
                CrashDiagnostics.recordStage(context, "MOVE_APPLICATION_DISCARDED", "stale generation")
                return@launch
            }
            CrashDiagnostics.recordStage(context, "MOVE_APPLICATION_START", "uci=$move fen=${board.fen}")
            try {
                applyUciMove(move)
                CrashDiagnostics.recordStage(context, "MOVE_APPLICATION_COMPLETE", "uci=$move fen=${board.fen}")
            } catch (error: Throwable) {
                // A malformed/stale engine move must never crash the Activity.
                // Keep the current board intact and surface a diagnostic instead.
                CrashDiagnostics.recordException(context, "applyUciMove uci=$move", error)
                engineAvailable.value = false
                engineThinking.value = false
            }
        }
    }

    fun stopEngine() {
        engineGeneration++
        engineThinking.value = false
    }

    private fun createChess960Board(): Board {
        val back = CharArray(8) { ' ' }
        back[Random.nextInt(4) * 2 + 1] = 'B'
        back[Random.nextInt(4) * 2] = 'B'
        fun emptyFiles() = back.indices.filter { back[it] == ' ' }
        back[emptyFiles().random()] = 'Q'
        repeat(2) { back[emptyFiles().random()] = 'N' }
        val remaining = emptyFiles()
        back[remaining[0]] = 'R'; back[remaining[1]] = 'K'; back[remaining[2]] = 'R'
        val whiteBack = String(back)
        val blackBack = whiteBack.lowercase()
        val rooks = back.indices.filter { back[it] == 'R' }
        val rightRook = ('A'.code + rooks.max()).toChar()
        val leftRook = ('A'.code + rooks.min()).toChar()
        val rights = "" + rightRook + leftRook + rightRook.lowercaseChar() + leftRook.lowercaseChar()
        val fen = blackBack + "/pppppppp/8/8/8/8/PPPPPPPP/" + whiteBack + " w " + rights + " - 0 1"
        return Board().also { it.loadFromFen(fen, true) }
    }
    fun newGame(colorChoice: String = settings.value.playerColor) {
        ponderJob?.cancel()
        ponderJob = null
        ponderCache.clear()
        val chosen = when (colorChoice) {
            "black" -> PieceColor.BLACK
            "random" -> if (Random.nextBoolean()) PieceColor.WHITE else PieceColor.BLACK
            else -> PieceColor.WHITE
        }
        playerColor.value = chosen
        flipped.value = chosen == PieceColor.BLACK
        mode.value = PlayMode.PLAY
        evalVisible.value = settings.value.showEvalPlay
        engineGeneration++
        board = Board()
        moveList.clear()
        analysisLines.value = emptyList()
        positionHistory.clear()
        positionHistory.add(board.fen)
        historyIndex.value = 0
        historyLength.value = 1
        engineThinking.value = false
        boardState.value = toUi(board)
        evaluation.value = 0
        resetClocks()
        saveSettings(settings.value.copy(
            playerColor = if (chosen == PieceColor.WHITE) "white" else "black",
            flipped = flipped.value
        ))
        maybeEngineMove()
    }

    fun setMode(newMode: PlayMode) {
        if (mode.value == newMode) return
        ponderJob?.cancel()
        ponderJob = null
        ponderCache.clear()
        mode.value = newMode
        engineGeneration++
        engineThinking.value = false
        evalVisible.value = if (newMode == PlayMode.ANALYSIS) settings.value.showEvalAnalysis
        else settings.value.showEvalPlay
        if (newMode == PlayMode.PLAY) maybeEngineMove()
    }

    fun toggleEval() {
        evalVisible.value = !evalVisible.value
        val s = if (mode.value == PlayMode.ANALYSIS) {
            settings.value.copy(showEvalAnalysis = evalVisible.value)
        } else {
            settings.value.copy(showEvalPlay = evalVisible.value)
        }
        saveSettings(s)
    }

    fun toggleFlip() {
        flipped.value = !flipped.value
        saveSettings(settings.value.copy(flipped = flipped.value))
    }

    fun undoMove() {
        val stepBack = if (mode.value == PlayMode.PLAY) 2 else 1
        goToHistoryIndex((historyIndex.value - stepBack).coerceAtLeast(0))
        maybeEngineMove()
    }

    fun goToStart() = goToHistoryIndex(0)
    fun goBack() = goToHistoryIndex(historyIndex.value - 1)
    fun goForward() = goToHistoryIndex(historyIndex.value + 1)
    fun goToEnd() = goToHistoryIndex(historyLength.value - 1)

    fun goToHistoryIndex(targetIndex: Int) {
        if (positionHistory.isEmpty()) return
        ponderJob?.cancel()
        ponderJob = null
        ponderCache.clear()
        val target = targetIndex.coerceIn(0, positionHistory.lastIndex)
        engineGeneration++
        engineThinking.value = false
        runCatching {
            board.loadFromFen(positionHistory[target])
            historyIndex.value = target
            analysisLines.value = emptyList()
            bestMoveUci.value = null
            boardState.value = toUi(board).copy(moveNumber = target)
            evaluation.value = 0
            updateEvaluationAsync()
        }
    }

    private fun applyUciMove(uci: String) {
        if (uci.length < 4) return
        // Build Aetheris crash-report version 1.0.9: chesslib's Square enum constants are uppercase (A1..H8), while UCI moves are lowercase.
        // fromValue() passes lowercase UCI coordinates to Enum.valueOf() on this dependency version,
        // causing a fatal IllegalArgumentException such as "No enum constant ... b8".
        val from = runCatching { LibSquare.valueOf(uci.substring(0, 2).uppercase()) }.getOrNull() ?: return
        val to = runCatching { LibSquare.valueOf(uci.substring(2, 4).uppercase()) }.getOrNull() ?: return
        val candidates = board.legalMoves().filter { it.from == from && it.to == to }
        if (candidates.isEmpty()) return
        val promoChar = if (uci.length > 4) uci[4].lowercaseChar() else 'q'
        val desired = when (promoChar) {
            'n' -> LibPieceType.KNIGHT
            'b' -> LibPieceType.BISHOP
            'r' -> LibPieceType.ROOK
            else -> LibPieceType.QUEEN
        }
        val move = candidates.firstOrNull { it.promotion == LibPiece.NONE }
            ?: candidates.firstOrNull { it.promotion.pieceType == desired }
            ?: candidates.first()
        makeMove(move, allowEngineReply = false)
    }

    fun analyzePosition(count: Int = settings.value.analysisLineCount) {
        if (analysisLoading.value) return
        val fen = board.fen
        val requestedDepth = options.value.depth.coerceIn(4, 12)
        analysisLoading.value = true
        viewModelScope.launch {
            val raw = withContext(Dispatchers.Default) {
                runCatching {
                    NativeEngine.analyzeFen(fen, requestedDepth, 5000, count.coerceIn(1, 5))
                }.getOrDefault("")
            }
            if (board.fen == fen) {
                val whiteToMove = fen.split(" ").getOrNull(1) != "b"
                val parsed = raw.lineSequence().mapNotNull { row ->
                    val parts = row.split("|", limit = 2)
                    if (parts.size != 2) return@mapNotNull null
                    val moves = parts[0].trim().split(Regex("\\s+")).filter { it.length >= 4 }
                    val score = parts[1].toIntOrNull() ?: return@mapNotNull null
                    if (moves.isEmpty()) null else EngineAnalysisLine(moves, if (whiteToMove) score else -score)
                }.toList()
                val moverScore = parsed.firstOrNull()?.scoreCp?.let { if (whiteToMove) it else -it }
                val isNewPosition = bestAnalysisFen != fen
                val improves = isNewPosition || requestedDepth > bestAnalysisDepth ||
                    (requestedDepth == bestAnalysisDepth && moverScore != null && moverScore >= bestAnalysisScoreForMover)
                if (parsed.isNotEmpty() && improves) {
                    analysisLines.value = parsed
                    bestMoveUci.value = parsed.firstOrNull()?.moves?.firstOrNull()
                    bestAnalysisFen = fen
                    bestAnalysisDepth = requestedDepth
                    bestAnalysisScoreForMover = moverScore ?: Int.MIN_VALUE
                }
            } else if (bestAnalysisFen != board.fen) {
                analysisLines.value = emptyList()
                bestMoveUci.value = null
            }
            analysisLoading.value = false
        }
    }

    fun formatAnalysisLine(moves: List<String>): String {
        val replay = Board()
        runCatching {
            replay.loadFromFen(positionHistory.getOrNull(historyIndex.value) ?: board.fen)
        }
        val san = mutableListOf<String>()
        for (uci in moves) {
            if (uci.length < 4) break
            val from = runCatching { LibSquare.valueOf(uci.substring(0, 2).uppercase()) }.getOrNull() ?: break
            val to = runCatching { LibSquare.valueOf(uci.substring(2, 4).uppercase()) }.getOrNull() ?: break
            val legal = replay.legalMoves().filter { it.from == from && it.to == to }
            if (legal.isEmpty()) break
            val promo = if (uci.length > 4) uci[4].lowercaseChar() else 'q'
            val desired = when (promo) {
                'n' -> LibPieceType.KNIGHT
                'b' -> LibPieceType.BISHOP
                'r' -> LibPieceType.ROOK
                else -> LibPieceType.QUEEN
            }
            val move = legal.firstOrNull { it.promotion != LibPiece.NONE && it.promotion.pieceType == desired }
                ?: legal.firstOrNull { it.promotion == LibPiece.NONE } ?: legal.first()
            val piece = replay.getPiece(from)
            val capture = replay.getPiece(to) != LibPiece.NONE ||
                (piece.pieceType == LibPieceType.PAWN && from.file != to.file && replay.getPiece(to) == LibPiece.NONE)
            val alternatives = replay.legalMoves().filter {
                it.from != from && it.to == to && replay.getPiece(it.from).pieceType == piece.pieceType
            }
            val notation = if (piece.pieceType == LibPieceType.KING && kotlin.math.abs((to.ordinal % 8) - (from.ordinal % 8)) == 2) {
                if ((to.ordinal % 8) > (from.ordinal % 8)) "O-O" else "O-O-O"
            } else buildString {
                when (piece.pieceType) {
                    LibPieceType.KING -> append("K")
                    LibPieceType.QUEEN -> append("Q")
                    LibPieceType.ROOK -> append("R")
                    LibPieceType.BISHOP -> append("B")
                    LibPieceType.KNIGHT -> append("N")
                    else -> if (capture) append(from.toString().first().lowercaseChar())
                }
                if (piece.pieceType != LibPieceType.PAWN && alternatives.isNotEmpty()) {
                    val sameFile = alternatives.any { it.from.file == from.file }
                    val sameRank = alternatives.any { it.from.rank == from.rank }
                    if (!sameFile) append(from.toString().first().lowercaseChar())
                    else if (!sameRank) append(from.toString().last())
                    else append(from.toString())
                }
                if (capture) append("x")
                append(to.toString().lowercase())
                if (move.promotion != LibPiece.NONE) {
                    append("=")
                    append(when (move.promotion.pieceType) {
                        LibPieceType.KNIGHT -> "N"
                        LibPieceType.BISHOP -> "B"
                        LibPieceType.ROOK -> "R"
                        else -> "Q"
                    })
                }
            }
            if (!replay.doMove(move)) break
            san.add(notation + when {
                replay.isMated() -> "#"
                replay.isKingAttacked() -> "+"
                else -> ""
            })
        }
        return san.mapIndexed { i, move ->
            if (i % 2 == 0) "${(i / 2) + 1}. $move" else move
        }.joinToString(" ")
    }

    fun playAnalysisLine(line: EngineAnalysisLine) {
        if (mode.value != PlayMode.ANALYSIS || line.moves.isEmpty()) return
        engineGeneration++
        engineThinking.value = false
        line.moves.forEach { uci ->
            if (!boardState.value.gameOver) applyUciMove(uci)
        }
        analysisLines.value = emptyList()
    }

    fun updateOptions(o: EngineOptions) {
        val nnueRejected = nnueStatus.value.contains("rejected", ignoreCase = true) || nnueStatus.value.contains("failed calibration", ignoreCase = true)
        val safe = if (o.mode == EngineMode.PURE_NNUE && nnueRejected) o.copy(mode = EngineMode.PURE_CLASSICAL) else o
        options.value = safe
        applyNativeOptions(safe)
        if (safe.mode != o.mode) {
            nnueStatus.value = "Pure NNUE mode blocked because these weights failed calibration; Classical evaluation remains active. Import a validated trained network to enable Pure NNUE."
        }
    }

    private fun applyNativeOptions(o: EngineOptions) {
        runCatching {
            NativeEngine.setParams(
                o.aggression, o.positional, o.complexity, o.solidity,
                o.materialWeight, o.mode.ordinal
            )
        }
    }

    fun resetOptions() {
        updateOptions(EngineOptions())
    }

    fun updateSettings(s: AppSettings) {
        val playerColorChanged = s.playerColor != settings.value.playerColor
        val ponderingChanged = s.pondering != settings.value.pondering
        saveSettings(s)
        if (ponderingChanged) {
            ponderJob?.cancel()
            ponderCache.clear()
            if (s.pondering) startPonderingIfNeeded()
        }
        if (s.flipped != flipped.value) flipped.value = s.flipped
        timeControlSeconds.value = TimeControl.byId(s.timeControl).seconds
        moveTimeSeconds.value = s.moveTimeSeconds
        evalVisible.value = if (mode.value == PlayMode.ANALYSIS) s.showEvalAnalysis else s.showEvalPlay
        resetClocks()
        // Changing the default side is an immediate gameplay choice, not just a
        // preference for the next manually-started game.
        if (playerColorChanged) newGame(s.playerColor)
    }

    fun resetAllSettings() {
        val defaults = AppSettings()
        store.save(defaults)
        settings.value = defaults
        playerColor.value = PieceColor.WHITE
        flipped.value = defaults.flipped
        moveTimeSeconds.value = defaults.moveTimeSeconds
        evalVisible.value = if (mode.value == PlayMode.ANALYSIS) defaults.showEvalAnalysis else defaults.showEvalPlay
        timeControlSeconds.value = TimeControl.byId(defaults.timeControl).seconds
        resetOptions()
        resetClocks()
    }

    private fun saveSettings(s: AppSettings) {
        settings.value = s
        settingsStore.save(s)
    }

    private fun updateEvaluationAsync() {
        // Prefer a bounded searched score over static material-only evaluation so
        // immediate recaptures, forcing checks, and short mating tactics influence the bar.
        val fen = board.fen
        viewModelScope.launch {
            val score = withContext(Dispatchers.Default) {
                val whiteToMove = fen.split(" ").getOrNull(1) != "b"
                val searchedCp = runCatching { NativeEngine.searchEvaluationFen(fen, 5, 900) }.getOrNull()
                val raw = searchedCp ?: runCatching { NativeEngine.evaluateFen(fen) }.getOrDefault(0)
                if (whiteToMove) raw else -raw
            }
            if (board.fen == fen) {
                evaluation.value = score
                bestMoveUci.value = null
                analysisLines.value = emptyList()
            }
        }
    }

    fun currentPgn(): String {
        val replay = Board()
        val sanMoves = mutableListOf<String>()
        moveList.forEach { uci ->
            if (uci.length < 4) return@forEach
            val from = runCatching { LibSquare.valueOf(uci.substring(0, 2).uppercase()) }.getOrNull() ?: return@forEach
            val to = runCatching { LibSquare.valueOf(uci.substring(2, 4).uppercase()) }.getOrNull() ?: return@forEach
            val legal = replay.legalMoves().filter { it.from == from && it.to == to }
            if (legal.isEmpty()) return@forEach
            val promo = if (uci.length > 4) uci[4].lowercaseChar() else 'q'
            val desired = when (promo) {
                'n' -> LibPieceType.KNIGHT
                'b' -> LibPieceType.BISHOP
                'r' -> LibPieceType.ROOK
                else -> LibPieceType.QUEEN
            }
            val move = legal.firstOrNull { it.promotion != LibPiece.NONE && it.promotion.pieceType == desired }
                ?: legal.firstOrNull { it.promotion == LibPiece.NONE } ?: legal.first()
            val piece = replay.getPiece(from)
            val capture = replay.getPiece(to) != LibPiece.NONE ||
                (piece.pieceType == LibPieceType.PAWN && from.file != to.file && replay.getPiece(to) == LibPiece.NONE)
            val alternatives = replay.legalMoves().filter {
                it.from != from && it.to == to && replay.getPiece(it.from).pieceType == piece.pieceType
            }
            val san = buildString {
                when (piece.pieceType) {
                    LibPieceType.KING -> append("K")
                    LibPieceType.QUEEN -> append("Q")
                    LibPieceType.ROOK -> append("R")
                    LibPieceType.BISHOP -> append("B")
                    LibPieceType.KNIGHT -> append("N")
                    else -> if (capture) append(from.toString().first().lowercaseChar())
                }
                if (piece.pieceType != LibPieceType.PAWN && alternatives.isNotEmpty()) {
                    val sameFile = alternatives.any { it.from.file == from.file }
                    val sameRank = alternatives.any { it.from.rank == from.rank }
                    if (!sameFile) append(from.toString().first().lowercaseChar())
                    else if (!sameRank) append(from.toString().last())
                    else append(from.toString())
                }
                if (capture) append("x")
                append(to.toString().lowercase())
                if (move.promotion != LibPiece.NONE) {
                    append("=")
                    append(when (move.promotion.pieceType) {
                        LibPieceType.KNIGHT -> "N"
                        LibPieceType.BISHOP -> "B"
                        LibPieceType.ROOK -> "R"
                        else -> "Q"
                    })
                }
            }
            if (!replay.doMove(move)) return@forEach
            val suffix = when {
                replay.isMated() -> "#"
                replay.isKingAttacked() -> "+"
                else -> ""
            }
            sanMoves.add(san + suffix)
        }
        val body = sanMoves.mapIndexed { i, san ->
            if (i % 2 == 0) "${i / 2 + 1}. $san" else san
        }.joinToString(" ")
        val result = when {
            board.isMated() && board.sideToMove == Side.WHITE -> "0-1"
            board.isMated() && board.sideToMove == Side.BLACK -> "1-0"
            board.isDraw() || board.isStaleMate() -> "1/2-1/2"
            else -> "*"
        }
        return "[Event \"Aetheris Game\"]\n[Site \"Aetheris Android\"]\n[Result \"$result\"]\n\n$body $result".trim()
    }

    fun copyPgn() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Aetheris PGN", currentPgn()))
        pgnStatus.value = "PGN copied to clipboard"
    }

    fun saveCurrentGame() {
        runCatching {
            val repository = GameRepository(context)
            val games = repository.load()
            val stamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).format(java.util.Date())
            games.add(0, GameRecord(title = "Aetheris game $stamp", pgn = currentPgn(), moves = moveList.toList()))
            repository.save(games)
            pgnStatus.value = "Game saved to Notebook"
        }.onFailure { pgnStatus.value = "Could not save game: ${it.message ?: "unknown error"}" }
    }

    private fun resetClocks() {
        val ms = if (moveTimeSeconds.value > 0) moveTimeSeconds.value * 1000L else timeControlSeconds.value * 1000L
        whiteTimeMs.value = ms
        blackTimeMs.value = ms
    }

    private fun startClockLoop() {
        clockJob?.cancel()
        clockJob = viewModelScope.launch {
            while (isActive) {
                delay(100)
                if (mode.value != PlayMode.PLAY) continue
                if (timeControlSeconds.value <= 0 && moveTimeSeconds.value <= 0) continue
                if (boardState.value.gameOver) continue
                if (board.sideToMove == Side.WHITE) {
                    whiteTimeMs.value = (whiteTimeMs.value - 100).coerceAtLeast(0)
                    if (whiteTimeMs.value == 0L) timeOut(PieceColor.WHITE)
                } else {
                    blackTimeMs.value = (blackTimeMs.value - 100).coerceAtLeast(0)
                    if (blackTimeMs.value == 0L) timeOut(PieceColor.BLACK)
                }
            }
        }
    }

    private fun timeOut(side: PieceColor) {
        engineGeneration++
        engineThinking.value = false
        val winner = if (side == PieceColor.WHITE) "Black" else "White"
        boardState.value = boardState.value.copy(gameOver = true, statusText = "$winner wins on time")
    }

    fun loadCustomNnue(sourcePath: String) {
        val custom = java.io.File(context.filesDir, "nnue/custom_weights.bin")
        runCatching {
            custom.parentFile?.mkdirs()
            val source = java.io.File(sourcePath)
            if (source.absolutePath != custom.absolutePath) source.copyTo(custom, overwrite = true)
            NativeEngine.setNnue(custom.absolutePath)
            if (NativeEngine.isNnueLoaded()) {
                val configuredOptions = options.value
                applyNativeOptions(configuredOptions.copy(mode = EngineMode.PURE_NNUE))
                val startPositionCp = NativeEngine.evaluateFen(Board().fen)
                if (abs(startPositionCp) > 100) {
                    options.value = configuredOptions.copy(mode = EngineMode.PURE_CLASSICAL)
                    applyNativeOptions(options.value)
                    nnueStatus.value = "Custom NNUE rejected for play: start position scores $startPositionCp cp. Classical evaluation enabled."
                } else {
                    applyNativeOptions(configuredOptions)
                    nnueStatus.value = "Custom NNUE loaded; starting-position score $startPositionCp cp"
                }
                updateEvaluationAsync()
            } else {
                custom.delete()
                ensureNnueAsset()?.let { NativeEngine.setNnue(it) }
                nnueStatus.value = "That weights file was rejected; restored the bundled network"
            }
        }.onFailure {
            nnueStatus.value = "Could not load NNUE weights: ${it.message ?: "unknown error"}"
        }
    }

    private fun ensureNnueAsset(): String? {
        return try {
            // Prefer the current 512-hidden-unit network at the asset root. Replace
            // stale cached legacy weights so upgrades actually use the bundled model.
            val out = java.io.File(context.filesDir, "nnue/weights.bin")
            out.parentFile?.mkdirs()
            if (!out.exists() || out.length() < 1_000_000L) {
                val temp = java.io.File(out.parentFile, "weights.bin.tmp")
                context.assets.open("weights.bin").use { input ->
                    temp.outputStream().use { output -> input.copyTo(output) }
                }
                if (out.exists()) out.delete()
                if (!temp.renameTo(out)) throw java.io.IOException("Unable to install bundled NNUE weights")
            }
            if (out.length() < 1_000_000L) throw java.io.IOException("Bundled NNUE file is unexpectedly small")
            out.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    private fun toUi(b: Board): BoardState {
        val arr = b.boardToArray()
        val pieces = mutableMapOf<Int, Piece>()
        for (i in 0 until 64) {
            val p = arr[i]
            if (p == LibPiece.NONE) continue
            pieces[i] = Piece(pieceType(p.pieceType), if (p.pieceSide == Side.WHITE) PieceColor.WHITE else PieceColor.BLACK)
        }
        val check = if (b.isKingAttacked()) b.getKingSquare(b.sideToMove).ordinal else null
        val status = when {
            b.isMated() -> "Checkmate"
            b.isStaleMate() -> "Stalemate"
            b.isDraw() -> "Draw"
            check != null -> "Check"
            b.sideToMove == Side.WHITE -> "White to move"
            else -> "Black to move"
        }
        return BoardState(
            pieces = pieces,
            sideToMove = if (b.sideToMove == Side.WHITE) PieceColor.WHITE else PieceColor.BLACK,
            castling = b.fen.substringAfter(" ").substringBefore(" "),
            enPassant = null,
            halfMove = b.halfMoveCounter,
            fullMove = b.moveCounter,
            checkSquare = check,
            gameOver = b.isMated() || b.isDraw() || b.isStaleMate(),
            statusText = status
        )
    }

    private fun pieceType(t: LibPieceType): PieceType = when (t) {
        LibPieceType.PAWN -> PieceType.PAWN
        LibPieceType.KNIGHT -> PieceType.KNIGHT
        LibPieceType.BISHOP -> PieceType.BISHOP
        LibPieceType.ROOK -> PieceType.ROOK
        LibPieceType.QUEEN -> PieceType.QUEEN
        LibPieceType.KING -> PieceType.KING
        else -> PieceType.PAWN
    }

    override fun onCleared() {
        clockJob?.cancel()
        super.onCleared()
    }
}
