package com.fredsystems.aetheris.viewmodel

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fredsystems.aetheris.data.GameRepository
import com.fredsystems.aetheris.domain.model.GameRecord
import com.fredsystems.aetheris.domain.model.BoardState
import com.fredsystems.aetheris.domain.model.Piece
import com.fredsystems.aetheris.domain.model.PieceColor
import com.fredsystems.aetheris.domain.model.PieceType
import com.fredsystems.aetheris.engine.NativeEngine
import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.Piece as LibPiece
import com.github.bhlangonijr.chesslib.PieceType as LibPieceType
import com.github.bhlangonijr.chesslib.Side
import com.github.bhlangonijr.chesslib.Square as LibSquare
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SimViewModel(private val context: Context) : ViewModel() {
    private var board = Board()
    val boardState = mutableStateOf(toUi(board))
    val eval = mutableStateOf(0)
    val status = mutableStateOf("Idle")
    val running = mutableStateOf(false)
    private val matchMoves = mutableListOf<String>()

    var depthA = 6
    var depthB = 6
    var timeMs = 800
    var games = 1
    var modeA = "PureClassical"
    var modeB = "PureClassical"
    var personaA = "Balanced"
    var personaB = "Balanced"
    var aggressionA = 50
    var positionalA = 50
    var complexityA = 50
    var solidityA = 50
    var aggressionB = 50
    var positionalB = 50
    var complexityB = 50
    var solidityB = 50
    init {
        runCatching {
            NativeEngine.initialize()
            NativeEngine.setParams(50, 50, 50, 50, 50, 2)
            status.value = "Aetheris Classical ready"
        }.onFailure {
            status.value = "Classical engine initialization failed"
        }
    }

    fun start() {
        if (running.value) return
        running.value = true
        viewModelScope.launch {
            try {
                for (g in 1..games) {
                    board = Board()
                    matchMoves.clear()
                    boardState.value = toUi(board)
                    status.value = "Game $g"
                    var plies = 0
                    while (running.value && plies < 160 && !board.isMated && !board.isDraw()) {
                        val white = board.sideToMove == Side.WHITE
                        val depth = if (white) depthA else depthB
                        val fen = board.fen
                        val mode = if (white) modeA else modeB
                        val mv = withContext(Dispatchers.Default) {
                            runCatching {
                                NativeEngine.searchConfiguredPosition(
                                    fen = fen,
                                    depth = depth.coerceIn(1, 24),
                                    timeMs = timeMs.coerceIn(100, 30000),
                                    aggression = if (white) aggressionA else aggressionB,
                                    positional = if (white) positionalA else positionalB,
                                    complexity = if (white) complexityA else complexityB,
                                    solidity = if (white) solidityA else solidityB,
                                    materialWeight = 50,
                                    mode = modeOrdinal(mode)
                                )
                            }.getOrElse { error ->
                                android.util.Log.e("AetherisSim", "Engine search failed at ply $plies", error)
                                status.value = "Engine error at ply $plies: " + (error.message ?: error.javaClass.simpleName)
                                null
                            }
                        }
                        if (mv.isNullOrBlank() || mv.length < 4) break
                        val from = runCatching { LibSquare.valueOf(mv.substring(0, 2).uppercase()) }.getOrNull() ?: break
                        val to = runCatching { LibSquare.valueOf(mv.substring(2, 4).uppercase()) }.getOrNull() ?: break
                        val candidates = board.legalMoves().filter { it.from == from && it.to == to }
                        if (candidates.isEmpty()) break
                        val promoChar = if (mv.length > 4) mv[4].lowercaseChar() else 'q'
                        val desired = when (promoChar) {
                            'n' -> LibPieceType.KNIGHT
                            'b' -> LibPieceType.BISHOP
                            'r' -> LibPieceType.ROOK
                            else -> LibPieceType.QUEEN
                        }
                        val move = candidates.firstOrNull { it.promotion == LibPiece.NONE }
                            ?: candidates.firstOrNull { it.promotion.pieceType == desired }
                            ?: candidates.first()
                        if (!board.doMove(move)) break
                        matchMoves.add(move.toString().lowercase())
                        boardState.value = toUi(board)
                        eval.value = withContext(Dispatchers.Default) {
                            runCatching { NativeEngine.evaluateFen(board.fen) }.getOrDefault(eval.value)
                        }
                        plies++
                        delay(220)
                    }
                    if (g < games) delay(400)
                }
            } finally {
                if (running.value && status.value.startsWith("Game ")) status.value = "Finished"
                running.value = false
            }
        }
    }

    fun currentPgn(): String {
        val replay = Board()
        val sanMoves = mutableListOf<String>()
        matchMoves.forEach { uci ->
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
        return "[Event \"Aetheris Engine Match\"]\n[Site \"Aetheris Android\"]\n[Result \"$result\"]\n\n$body $result".trim()
    }

    fun saveMatchToNotebook(editedPgn: String, comment: String) {
        runCatching {
            val repository = GameRepository(context)
            val games = repository.load()
            val comments = if (comment.isBlank()) emptyMap() else mapOf(0 to comment.trim())
            val record = GameRecord(
                title = "Engine match " + System.currentTimeMillis(),
                pgn = editedPgn.trim().ifBlank { currentPgn() },
                moves = matchMoves.toList(),
                comments = comments
            )
            games.add(0, record)
            repository.save(games)
            status.value = "Saved engine match to Notebook"
        }.onFailure { status.value = "Could not save match: " + (it.message ?: "unknown error") }
    }
    fun stop() {
        running.value = false
        status.value = "Stopped"
    }

    private fun modeOrdinal(mode: String): Int = 2

    private fun ensureNnue(): String? = try {
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

    private fun toUi(b: Board): BoardState {
        val arr = b.boardToArray()
        val pieces = mutableMapOf<Int, Piece>()
        for (i in 0 until 64) {
            val p = arr[i]
            if (p == LibPiece.NONE) continue
            pieces[i] = Piece(pieceType(p.pieceType), if (p.pieceSide == Side.WHITE) PieceColor.WHITE else PieceColor.BLACK)
        }
        val check = if (b.isKingAttacked()) b.getKingSquare(b.sideToMove).ordinal else null
        val statusText = when {
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
            checkSquare = check,
            gameOver = b.isMated() || b.isDraw() || b.isStaleMate(),
            statusText = statusText
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
}
