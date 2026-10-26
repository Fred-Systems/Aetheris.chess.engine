package com.fredsystems.aetheris.viewmodel

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fredsystems.aetheris.domain.model.BoardState
import com.fredsystems.aetheris.domain.model.Piece
import com.fredsystems.aetheris.domain.model.PieceColor
import com.fredsystems.aetheris.domain.model.PieceType
import com.fredsystems.aetheris.engine.process.EngineManager
import com.fredsystems.aetheris.engine.process.EngineProcess
import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.Piece as LibPiece
import com.github.bhlangonijr.chesslib.PieceType as LibPieceType
import com.github.bhlangonijr.chesslib.Side
import com.github.bhlangonijr.chesslib.Square as LibSquare
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SimViewModel(private val context: Context) : ViewModel() {
    val engineA = EngineManager(EngineProcess(context))
    val engineB = EngineManager(EngineProcess(context))
    private var board = Board()
    val boardState = mutableStateOf(toUi(board))
    val eval = mutableStateOf(0)
    val status = mutableStateOf("Idle")
    val running = mutableStateOf(false)

    var depthA = 6
    var depthB = 6
    var timeMs = 0
    var games = 1
    var modeA = "DualCore"
    var modeB = "DualCore"

    init {
        try { engineA.init() } catch (e: Exception) { }
        try { engineB.init() } catch (e: Exception) { }
    }

    fun start() {
        if (running.value) return
        running.value = true
        viewModelScope.launch {
            try {
                engineA.setOption("EngineMode", modeA)
                engineB.setOption("EngineMode", modeB)
                for (g in 1..games) {
                    board = Board()
                    boardState.value = toUi(board)
                    engineA.newGame()
                    engineB.newGame()
                    status.value = "Game $g"
                    var plies = 0
                    while (running.value && plies < 160 && !board.isMated && !board.isDraw()) {
                        val white = board.sideToMove == Side.WHITE
                        val eng = if (white) engineA else engineB
                        val depth = if (white) depthA else depthB
                        eng.bestMove.value = null
                        eng.setPosition(board.fen)
                        eng.go(depth, timeMs)
                        var mv: String? = null
                        for (i in 0..240) {
                            delay(50)
                            val bm = eng.bestMove.value
                            if (!bm.isNullOrBlank()) { mv = bm; break }
                        }
                        if (mv == null || mv.length < 4) break
                        val from = LibSquare.fromValue(mv.substring(0, 2))
                        val to = LibSquare.fromValue(mv.substring(2, 4))
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
                        boardState.value = toUi(board)
                        eval.value = eng.score.value
                        plies++
                        delay(260)
                    }
                    if (g < games) delay(500)
                }
            } finally {
                status.value = "Finished"
                running.value = false
            }
        }
    }

    fun stop() { running.value = false }

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

    override fun onCleared() {
        engineA.stop(); engineB.stop()
        super.onCleared()
    }
}
