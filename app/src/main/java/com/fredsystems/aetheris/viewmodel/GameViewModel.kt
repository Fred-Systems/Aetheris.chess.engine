package com.fredsystems.aetheris.viewmodel

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fredsystems.aetheris.CrashDiagnostics
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
import kotlin.random.Random

enum class PlayMode { PLAY, ANALYSIS }

class GameViewModel(private val context: Context) : ViewModel() {
    private val store = SettingsStore(context)
    private var board = Board()
    private val moveList = mutableListOf<String>()
    private val settingsStore = store
    val settings = mutableStateOf(store.load())

    val boardState = mutableStateOf(toUi(board))
    val evaluation = mutableStateOf(0)
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

    init {
        runCatching {
            NativeEngine.initialize()
            applyNativeOptions(options.value)
            ensureNnueAsset()?.let { NativeEngine.setNnue(it) }
        }.onFailure { engineAvailable.value = false }
        evalVisible.value = settings.value.showEvalPlay
        moveTimeSeconds.value = settings.value.moveTimeSeconds
        resetClocks()
        startClockLoop()
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
        val from = move.from.ordinal
        val to = move.to.ordinal
        if (!board.doMove(move)) return
        moveList.add(move.toString())
        val next = toUi(board).copy(
            selectedSquare = null,
            legalTargets = emptySet(),
            lastMoveFrom = from,
            lastMoveTo = to,
            moveNumber = boardState.value.moveNumber + 1
        )
        boardState.value = next
        if (moveTimeSeconds.value > 0) resetClocks()
        updateEvaluationAsync()
        if (allowEngineReply) maybeEngineMove()
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
        val depth = options.value.depth
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
                    NativeEngine.searchPosition(fen, depth, timeMs.toInt()).also { move ->
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
                if (force) engineAvailable.value = false
                val fallback = board.legalMoves().randomOrNull()
                if (fallback != null) makeMove(fallback, allowEngineReply = false)
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
                CrashDiagnostics.recordException(context, "applyUciMove uci=$move", error)
                throw error
            }
        }
    }

    fun stopEngine() {
        engineGeneration++
        engineThinking.value = false
    }

    fun newGame(colorChoice: String = settings.value.playerColor) {
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
        if (board.backup.isEmpty() && moveList.isEmpty()) return
        engineGeneration++
        engineThinking.value = false
        if (mode.value == PlayMode.PLAY) {
            var guard = 0
            do {
                if (board.backup.isEmpty()) break
                board.undoMove()
                if (moveList.isNotEmpty()) moveList.removeAt(moveList.size - 1)
                guard++
            } while (guard < 4 && board.sideToMove != (if (playerColor.value == PieceColor.WHITE) Side.WHITE else Side.BLACK) && board.backup.isNotEmpty())
        } else {
            if (board.backup.isEmpty()) return
            board.undoMove()
            if (moveList.isNotEmpty()) moveList.removeAt(moveList.size - 1)
        }
        boardState.value = toUi(board).copy(moveNumber = boardState.value.moveNumber + 1)
        updateEvaluationAsync()
        maybeEngineMove()
    }

    private fun applyUciMove(uci: String) {
        if (uci.length < 4) return
        val from = LibSquare.fromValue(uci.substring(0, 2))
        val to = LibSquare.fromValue(uci.substring(2, 4))
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

    fun updateOptions(o: EngineOptions) {
        options.value = o
        applyNativeOptions(o)
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
        saveSettings(s)
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
        val fen = board.fen
        viewModelScope.launch {
            val score = withContext(Dispatchers.Default) {
                runCatching { NativeEngine.evaluateFen(fen) }.getOrDefault(0)
            }
            evaluation.value = score
        }
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

    private fun ensureNnueAsset(): String? {
        return try {
            val out = java.io.File(context.filesDir, "nnue/weights.bin")
            out.parentFile?.mkdirs()
            if (!out.exists()) {
                context.assets.open("nnue/weights.bin").use { input ->
                    out.outputStream().use { output -> input.copyTo(output) }
                }
            }
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
