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
import com.fredsystems.aetheris.audio.LichessSoundPlayer
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
import com.fredsystems.aetheris.engine.OpeningBook
import com.fredsystems.aetheris.engine.CaissaEngine
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
    private val soundPlayer = LichessSoundPlayer(context.applicationContext)
    private val openingBook = OpeningBook(context.applicationContext)
    val openingBookStatus = mutableStateOf(if (openingBook.installed()) "Opening book installed" else "No opening book installed")
    val openingBookMoves = mutableStateOf<List<String>>(emptyList())
    private var board = Board()
    private val moveList = mutableListOf<String>()
    private val positionHistory = mutableListOf<String>()
    private var initialFen: String = board.fen
    private val pendingPremoves = mutableListOf<String>()
    private var conditionalOpponentUci: String? = null
    private var conditionalReplyUci: String? = null
    private var conditionalBaseFen: String? = null
    val conditionalSetupActive = mutableStateOf(false)
    val conditionalSetupStep = mutableStateOf("opponent")
    private var conditionalDraftBoard: Board? = null
    private var conditionalDraftOpponentUci: String? = null
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
            options.value = options.value.copy(mode = EngineMode.PURE_CLASSICAL)
            applyNativeOptions(options.value)
            nnueStatus.value = "Aetheris Classical active"
        }.onFailure {
            engineAvailable.value = false
            nnueStatus.value = "Native classical engine initialization failed"
        }
        evalVisible.value = settings.value.showEvalPlay
        moveTimeSeconds.value = settings.value.moveTimeSeconds
        resetClocks()
        startClockLoop()
        updateEvaluationAsync()
        refreshOpeningBookMoves(board.fen)
        maybeEngineMove()
    }

    fun currentPositionFen(): String = positionHistory.getOrNull(historyIndex.value) ?: board.fen

    fun refreshOpeningBookMoves(fen: String = currentPositionFen()) {
        if (!settings.value.openingBookEnabled || !openingBook.installed()) {
            openingBookMoves.value = emptyList()
            return
        }
        viewModelScope.launch {
            val moves = withContext(Dispatchers.IO) { runCatching { openingBook.continuations(fen) }.getOrDefault(emptyList()) }
            if (currentPositionFen() == fen) openingBookMoves.value = moves
        }
    }

    fun downloadOpeningBook() {
        viewModelScope.launch {
            openingBookStatus.value = "Downloading public Lichess opening database…"
            runCatching { openingBook.download { openingBookStatus.value = it } }
                .onSuccess { openingBookStatus.value = "Lichess opening database installed"; refreshOpeningBookMoves() }
                .onFailure { openingBookStatus.value = "Opening book download failed: ${it.message ?: "unknown error"}" }
        }
    }

    fun importOpeningBook(uri: android.net.Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching {
                context.contentResolver.openInputStream(uri)?.use { openingBook.importFile(it) }
                    ?: error("Could not open selected file")
            }
            withContext(Dispatchers.Main) {
                openingBookStatus.value = result.fold({ "Opening book imported" }, { "Import failed: ${it.message}" })
                if (result.isSuccess) refreshOpeningBookMoves()
            }
        }
    }

    fun removeOpeningBook() {
        context.getSharedPreferences("aetheris_settings", Context.MODE_PRIVATE).edit().apply { }.apply()
        runCatching { java.io.File(context.filesDir, "opening-book/lichess-openings.tsv").delete() }
        openingBookMoves.value = emptyList()
        openingBookStatus.value = "Opening book removed"
    }

    fun onSquareClick(index: Int) {
        if (conditionalSetupActive.value) {
            handleConditionalSetupClick(index)
            return
        }
        if (boardState.value.gameOver) return
        val userSide = if (playerColor.value == PieceColor.WHITE) Side.WHITE else Side.BLACK
        if (mode.value == PlayMode.PLAY && board.sideToMove != userSide) {
            if (settings.value.premoveEnabled) handlePremoveClick(index, userSide)
            return
        }
        val sq = LibSquare.squareAt(index)
        val selected = boardState.value.selectedSquare
        if (selected != null) {
            val from = LibSquare.squareAt(selected)
            val legal = findLegalMove(from, sq)
            if (legal != null) { makeMove(legal, allowEngineReply = true); return }
            val clicked = board.getPiece(sq)
            if (clicked != LibPiece.NONE && clicked.pieceSide == board.sideToMove) selectSquare(index)
            else boardState.value = boardState.value.copy(selectedSquare = null, legalTargets = emptySet())
        } else {
            val clicked = board.getPiece(sq)
            if (clicked != LibPiece.NONE && clicked.pieceSide == board.sideToMove) selectSquare(index)
        }
    }

    private fun queuedPremoveSquares(): Set<Int> = pendingPremoves.flatMap { uci ->
        if (uci.length < 4) emptyList() else {
            val from = runCatching { LibSquare.valueOf(uci.substring(0, 2).uppercase()).ordinal }.getOrNull()
            val to = runCatching { LibSquare.valueOf(uci.substring(2, 4).uppercase()).ordinal }.getOrNull()
            listOfNotNull(from, to)
        }
    }.toSet()

    private fun handlePremoveClick(index: Int, userSide: Side) {
        val sq = LibSquare.squareAt(index)
        val selected = boardState.value.selectedSquare
        if (selected == null) {
            val piece = board.getPiece(sq)
            if (piece != LibPiece.NONE && piece.pieceSide == userSide) {
                boardState.value = boardState.value.copy(selectedSquare = index, legalTargets = emptySet(), statusText = "Premove: choose destination")
            }
            return
        }
        val from = LibSquare.squareAt(selected)
        if (from == sq) { boardState.value = boardState.value.copy(selectedSquare = null, legalTargets = emptySet()); return }
        val uci = from.toString().lowercase() + sq.toString().lowercase()
        // FIFO sequence for successive user turns, not alternative candidate moves.
        if (!settings.value.multiplePremoves) pendingPremoves.clear()
        if (pendingPremoves.size < (if (settings.value.multiplePremoves) 12 else 1)) pendingPremoves.add(uci)
        boardState.value = boardState.value.copy(
            selectedSquare = null,
            legalTargets = emptySet(),
            lastMoveFrom = selected,
            lastMoveTo = index,
            statusText = if (settings.value.multiplePremoves) "Premove ${pendingPremoves.size} queued · in order" else "Premove queued",
            premoveHighlights = queuedPremoveSquares()
        )
    }

    fun beginConditionalSetup() {
        if (mode.value != PlayMode.PLAY) return
        val engineSide = if (playerColor.value == PieceColor.WHITE) Side.BLACK else Side.WHITE
        if (board.sideToMove != engineSide) {
            boardState.value = boardState.value.copy(statusText = "Conditional setup is available during the engine's turn")
            return
        }
        // Pause the live search without changing the position so the user can
        // choose a hypothetical opponent move from the exact current position.
        stopEngine()
        ponderJob?.cancel()
        ponderJob = null
        val draft = Board()
        runCatching { draft.loadFromFen(board.fen) }.onFailure { return }
        conditionalDraftBoard = draft
        conditionalBaseFen = board.fen
        conditionalDraftOpponentUci = null
        conditionalSetupStep.value = "opponent"
        conditionalSetupActive.value = true
        boardState.value = toUi(draft).copy(statusText = "Conditional setup: make the opponent's hypothetical move")
    }

    fun cancelConditionalSetup() {
        conditionalSetupActive.value = false
        conditionalDraftBoard = null
        conditionalDraftOpponentUci = null
        conditionalBaseFen = null
        boardState.value = toUi(board).copy(statusText = "Conditional setup cancelled")
        maybeEngineMove()
    }

    private fun handleConditionalSetupClick(index: Int) {
        val draft = conditionalDraftBoard ?: return
        val sq = LibSquare.squareAt(index)
        val selected = boardState.value.selectedSquare
        if (selected == null) {
            val piece = draft.getPiece(sq)
            if (piece != LibPiece.NONE && piece.pieceSide == draft.sideToMove) {
                val targets = draft.legalMoves().filter { it.from == sq }.map { it.to.ordinal }.toSet()
                boardState.value = toUi(draft).copy(selectedSquare = index, legalTargets = targets, statusText = if (conditionalSetupStep.value == "opponent") "Choose the opponent's destination" else "Choose your conditional reply")
            }
            return
        }
        val from = LibSquare.squareAt(selected)
        val candidates = draft.legalMoves().filter { it.from == from && it.to == sq }
        val move = candidates.firstOrNull { it.promotion != LibPiece.NONE && it.promotion.pieceType == LibPieceType.QUEEN }
            ?: candidates.firstOrNull()
        if (move == null) {
            val piece = draft.getPiece(sq)
            if (piece != LibPiece.NONE && piece.pieceSide == draft.sideToMove) {
                val targets = draft.legalMoves().filter { it.from == sq }.map { it.to.ordinal }.toSet()
                boardState.value = toUi(draft).copy(selectedSquare = index, legalTargets = targets, statusText = "Choose a destination")
            } else {
                boardState.value = boardState.value.copy(selectedSquare = null, legalTargets = emptySet())
            }
            return
        }
        val uci = move.toString().lowercase()
        if (!draft.doMove(move)) return
        if (conditionalSetupStep.value == "opponent") {
            conditionalDraftOpponentUci = uci
            conditionalSetupStep.value = "reply"
            boardState.value = toUi(draft).copy(
                lastMoveFrom = move.from.ordinal,
                lastMoveTo = move.to.ordinal,
                statusText = "Opponent move previewed (not played). Now make your conditional reply."
            )
        } else {
            val opponentMove = conditionalDraftOpponentUci ?: run { cancelConditionalSetup(); return }
            conditionalOpponentUci = opponentMove
            conditionalReplyUci = uci
            conditionalBaseFen = board.fen
            conditionalSetupActive.value = false
            conditionalDraftBoard = null
            conditionalDraftOpponentUci = null
            pendingPremoves.clear()
            boardState.value = toUi(board).copy(statusText = "Conditional reply saved: $opponentMove → $uci")
            maybeEngineMove()
        }
    }

    fun setConditionalMove(opponentUci: String, replyUci: String) {
        conditionalOpponentUci = opponentUci.trim().lowercase().takeIf { it.length >= 4 }
        conditionalReplyUci = replyUci.trim().lowercase().takeIf { it.length >= 4 }
        conditionalBaseFen = board.fen
    }

    fun clearConditionalMove() {
        conditionalOpponentUci = null
        conditionalReplyUci = null
        conditionalBaseFen = null
    }

    private fun runPendingPremove() {
        if (mode.value != PlayMode.PLAY || boardState.value.gameOver) return
        val userSide = if (playerColor.value == PieceColor.WHITE) Side.WHITE else Side.BLACK
        if (board.sideToMove != userSide) return
        val uci = pendingPremoves.firstOrNull() ?: return
        // Validate only the next queued move against the real position. If it
        // fails, cancel the entire remaining sequence rather than skipping ahead.
        val from = runCatching { LibSquare.valueOf(uci.take(2).uppercase()) }.getOrNull()
        val to = runCatching { LibSquare.valueOf(uci.substring(2, 4).uppercase()) }.getOrNull()
        val candidates = if (from != null && to != null) board.legalMoves().filter { it.from == from && it.to == to } else emptyList()
        val promotion = uci.getOrNull(4)?.lowercaseChar() ?: 'q'
        val desired = when (promotion) {
            'n' -> LibPieceType.KNIGHT
            'b' -> LibPieceType.BISHOP
            'r' -> LibPieceType.ROOK
            else -> LibPieceType.QUEEN
        }
        val legal = candidates.firstOrNull { it.promotion != LibPiece.NONE && it.promotion.pieceType == desired }
            ?: candidates.firstOrNull { it.promotion == LibPiece.NONE }
        if (legal == null) {
            pendingPremoves.clear()
            boardState.value = boardState.value.copy(
                statusText = "Premove sequence cancelled: next move is no longer legal",
                selectedSquare = null,
                legalTargets = emptySet(),
                premoveHighlights = emptySet()
            )
            return
        }
        pendingPremoves.removeAt(0)
        val remaining = pendingPremoves.size
        makeMove(legal, allowEngineReply = true)
        boardState.value = boardState.value.copy(
            statusText = if (remaining > 0) "Premove played · $remaining remaining" else boardState.value.statusText,
            premoveHighlights = queuedPremoveSquares()
        )
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
        val isCapture = board.getPiece(move.to) != LibPiece.NONE || (board.getPiece(move.from).pieceType == LibPieceType.PAWN && move.from.file != move.to.file && board.getEnPassant() == move.to)
        val isCastle = board.getPiece(move.from).pieceType == LibPieceType.KING && runCatching { board.context.isCastleMove(move) }.getOrDefault(false)
        val isPromotion = move.promotion != LibPiece.NONE
        if (!board.doMove(move)) return
        playMoveSound(board.isKingAttacked(), isCapture, isCastle, isPromotion)
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
        refreshOpeningBookMoves(board.fen)
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

    private fun playMoveSound(check: Boolean, capture: Boolean, castle: Boolean, promotion: Boolean) {
        val prefs = settings.value
        if (!prefs.moveSoundsEnabled || prefs.moveSoundVolume <= 0) return
        // Match Lichess Standard's event sounds instead of synthesizing harsh system beeps.
        soundPlayer.play(when {
            check -> "check"
            capture -> "capture"
            else -> "move"
        }, prefs.moveSoundVolume)
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
        options.value = options.value.copy(depth = depth.coerceIn(1, 20), mode = EngineMode.PURE_CLASSICAL)
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
                runCatching {
                    val legal = Board().apply { loadFromFen(fen) }.legalMoves().map { it.toString().lowercase() }.toSet()
                    if (settings.value.openingBookEnabled && openingBook.installed()) {
                        openingBook.choose(fen, legal, settings.value.openingBookPolicy)
                    } else null
                }.getOrNull() ?: runCatching {
                    if (settings.value.selectedEngine == "caissa") {
                        CaissaEngine.bestMove(fen, depth.coerceIn(1, 4))
                    } else {
                        CrashDiagnostics.searchStarted(context, fen, depth, timeMs.toInt())
                        NativeEngine.searchPositionConfigured(
                            fen, depth, timeMs.toInt(),
                            searchOptions.aggression, searchOptions.positional, searchOptions.complexity,
                            searchOptions.solidity, searchOptions.materialWeight, searchOptions.mode.ordinal
                        ).also { CrashDiagnostics.searchCompleted(context, it) }
                    }
                }.onFailure { error -> CrashDiagnostics.recordException(context, "Engine.searchPosition", error) }.getOrNull()
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
        return Board().also { it.loadFromFen(fen) }
    }
    fun openNotebookRecord(record: GameRecord) {
        ponderJob?.cancel()
        engineGeneration++
        board = Board()
        initialFen = board.fen
        moveList.clear()
        positionHistory.clear()
        positionHistory.add(board.fen)
        historyIndex.value = 0
        historyLength.value = 1
        pendingPremoves.clear()
        setMode(PlayMode.ANALYSIS)
        record.moves.forEach { uci ->
            val from = runCatching { LibSquare.valueOf(uci.take(2).uppercase()) }.getOrNull() ?: return@forEach
            val to = runCatching { LibSquare.valueOf(uci.substring(2, 4).uppercase()) }.getOrNull() ?: return@forEach
            val candidates = board.legalMoves().filter { it.from == from && it.to == to }
            val promo = uci.getOrNull(4)?.lowercaseChar() ?: 'q'
            val desired = when (promo) { 'n' -> LibPieceType.KNIGHT; 'b' -> LibPieceType.BISHOP; 'r' -> LibPieceType.ROOK; else -> LibPieceType.QUEEN }
            val move = candidates.firstOrNull { it.promotion != LibPiece.NONE && it.promotion.pieceType == desired }
                ?: candidates.firstOrNull { it.promotion == LibPiece.NONE } ?: candidates.firstOrNull()
            if (move != null) makeMove(move, allowEngineReply = false)
        }
        goToStart()
        evalVisible.value = settings.value.showEvalAnalysis
    }

    fun newGame(colorChoice: String = settings.value.playerColor) {
        ponderJob?.cancel()
        ponderJob = null
        ponderCache.clear()
        pendingPremoves.clear()
        clearConditionalMove()
        conditionalSetupActive.value = false
        conditionalDraftBoard = null
        conditionalDraftOpponentUci = null
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
        board = if (settings.value.chess960Enabled) createChess960Board() else Board()
        initialFen = board.fen
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
        val move = candidates.firstOrNull { it.promotion != LibPiece.NONE && it.promotion.pieceType == desired }
            ?: candidates.firstOrNull { it.promotion == LibPiece.NONE }
            ?: candidates.first()
        val conditionalPositionMatches = conditionalBaseFen != null && board.fen == conditionalBaseFen
        val matchedConditional = settings.value.conditionalMoves &&
            conditionalPositionMatches && uci.equals(conditionalOpponentUci, ignoreCase = true)
        if (matchedConditional) {
            conditionalReplyUci?.let { reply ->
                pendingPremoves.clear()
                pendingPremoves.add(reply)
            }
            conditionalOpponentUci = null
            conditionalReplyUci = null
            conditionalBaseFen = null
        } else if (conditionalBaseFen != null && conditionalPositionMatches) {
            // The engine chose a different move; this conditional belongs only
            // to the exact position that was previewed and must not fire later.
            clearConditionalMove()
        }
        makeMove(move, allowEngineReply = false)
        runPendingPremove()
    }

    fun analyzePosition(count: Int = settings.value.analysisLineCount) {
        if (analysisLoading.value) return
        val fen = board.fen
        val requestedDepth = options.value.depth.coerceIn(4, 20)
        analysisLoading.value = true
        viewModelScope.launch {
            val raw = withContext(Dispatchers.Default) {
                runCatching {
                    if (settings.value.selectedEngine == "caissa") {
                        val line = CaissaEngine.principalLine(fen, requestedDepth.coerceIn(1, 5))
                        val score = CaissaEngine.evaluate(fen)
                        if (line.isEmpty()) "" else line.joinToString(" ") + "|" + score
                    } else {
                        NativeEngine.analyzeFen(fen, requestedDepth, (5000 + requestedDepth * 500).coerceAtMost(15000), count.coerceIn(1, 5))
                    }
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
        // NNUE weights are not sufficiently trained/calibrated for reliable play.
        // Keep the app on its verified classical evaluator until a validated net exists.
        val classical = o.copy(mode = EngineMode.PURE_CLASSICAL)
        options.value = classical
        applyNativeOptions(classical)
        nnueStatus.value = "Aetheris Classical active"
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
        if (s.openingBookEnabled != settings.value.openingBookEnabled || s.openingBookPolicy != settings.value.openingBookPolicy) refreshOpeningBookMoves()
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
                val raw = if (settings.value.selectedEngine == "caissa") {
                    runCatching { CaissaEngine.evaluate(fen) }.getOrDefault(0)
                } else {
                    val searchedCp = runCatching { NativeEngine.searchEvaluationFen(fen, 5, 900) }.getOrNull()
                    searchedCp ?: runCatching { NativeEngine.evaluateFen(fen) }.getOrDefault(0)
                }
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
        runCatching { replay.loadFromFen(initialFen) }
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
            val san = if (piece.pieceType == LibPieceType.KING && replay.context.isCastleMove(move)) {
                if (replay.context.isKingSideCastle(move)) "O-O" else "O-O-O"
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
        val variantTag = if (settings.value.chess960Enabled) "[Variant \"Chess960\"]\n" else ""
        val setupTag = if (settings.value.chess960Enabled) "[SetUp \"1\"]\n[FEN \"$initialFen\"]\n" else ""
        return "[Event \"Aetheris Game\"]\n[Site \"Aetheris Android\"]\n$variantTag$setupTag[Result \"$result\"]\n\n$body $result".trim()
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

    fun runNnueDiagnostics() {
        nnueStatus.value = runCatching { NativeEngine.nnueSanityCheck() }
            .getOrElse { "NNUE diagnostic failed: ${it.message ?: "unknown error"}" }
    }

    fun loadCustomNnue(sourcePath: String) {
        val custom = java.io.File(context.filesDir, "nnue/custom_weights.bin")
        runCatching {
            custom.parentFile?.mkdirs()
            val source = java.io.File(sourcePath)
            if (source.absolutePath != custom.absolutePath) source.copyTo(custom, overwrite = true)
            NativeEngine.setNnue(custom.absolutePath)
            if (NativeEngine.isNnueLoaded()) {
                val sanity = NativeEngine.nnueSanityCheck()
                if (sanity.contains("WARNING") || sanity.contains("NNUE Load Failed")) {
                    custom.delete()
                    ensureNnueAsset()?.let { NativeEngine.setNnue(it); NativeEngine.nnueSanityCheck() }
                    options.value = options.value.copy(mode = EngineMode.PURE_CLASSICAL)
                    applyNativeOptions(options.value)
                    nnueStatus.value = "Custom NNUE rejected by static checks; Classical fallback active · $sanity"
                    return@runCatching
                }
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
            val out = java.io.File(context.filesDir, "nnue/weights.bin")
            out.parentFile?.mkdirs()
            val assetBytes = context.assets.open("weights.bin").use { it.readBytes() }
            val assetDigest = java.security.MessageDigest.getInstance("SHA-256")
                .digest(assetBytes).joinToString("") { "%02x".format(it) }
            val cachedDigest = if (out.isFile) {
                java.io.FileInputStream(out).use { input ->
                    val digest = java.security.MessageDigest.getInstance("SHA-256")
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        digest.update(buffer, 0, count)
                    }
                    digest.digest().joinToString("") { "%02x".format(it) }
                }
            } else ""
            if (!out.isFile || cachedDigest != assetDigest) {
                val temp = java.io.File(out.parentFile, "weights.bin.tmp")
                temp.outputStream().use { it.write(assetBytes) }
                if (out.exists() && !out.delete()) throw java.io.IOException("Unable to replace stale NNUE cache")
                if (!temp.renameTo(out)) throw java.io.IOException("Unable to install bundled NNUE weights")
                android.util.Log.i("AetherisNNUE", "Updated cached NNUE weights, bytes=${out.length()}, sha256=$assetDigest")
            }
            if (out.length() < 1_000_000L) throw java.io.IOException("Bundled NNUE file is unexpectedly small")
            out.absolutePath
        } catch (e: Exception) {
            android.util.Log.e("AetherisNNUE", "NNUE Load Failed while preparing the bundled weights", e)
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
        soundPlayer.release()
        super.onCleared()
    }
}
