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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SimViewModel(private val context: Context) : ViewModel() {
    val engineA = EngineManager(EngineProcess(context))
    val engineB = EngineManager(EngineProcess(context))
    val board = mutableStateOf(initialBoard())
    val eval = mutableStateOf(0)
    val status = mutableStateOf("Idle")
    val running = mutableStateOf(false)

    var depthA = 6
    var depthB = 6
    var timeMs = 0
    var games = 1

    init {
        engineA.init()
        engineB.init()
    }

    private fun initialBoard(): BoardState {
        val pieces = mutableMapOf<Int, Piece>()
        val back = listOf(PieceType.ROOK, PieceType.KNIGHT, PieceType.BISHOP, PieceType.QUEEN, PieceType.KING, PieceType.BISHOP, PieceType.KNIGHT, PieceType.ROOK)
        for (f in 0..7) {
            pieces[f] = Piece(back[f], PieceColor.WHITE)
            pieces[56 + f] = Piece(back[f], PieceColor.BLACK)
            pieces[8 + f] = Piece(PieceType.PAWN, PieceColor.WHITE)
            pieces[48 + f] = Piece(PieceType.PAWN, PieceColor.BLACK)
        }
        return BoardState(pieces = pieces)
    }

    fun start() {
        if (running.value) return
        running.value = true
        viewModelScope.launch {
            for (g in 1..games) {
                board.value = initialBoard()
                engineA.newGame(); engineB.newGame()
                status.value = "Game $g"
                var plies = 0
                while (running.value && plies < 120) {
                    val side = board.value.sideToMove
                    val eng = if (side == PieceColor.WHITE) engineA else engineB
                    val depth = if (side == PieceColor.WHITE) depthA else depthB
                    eng.go(depth, timeMs)
                    // wait for bestmove
                    var mv: String? = null
                    for (i in 0..200) {
                        delay(50)
                        val bm = eng.bestMove.value
                        if (bm != null) { mv = bm; break }
                    }
                    if (mv == null || mv.length < 4) break
                    // apply
                    val from = mv[0] - 'a' + (mv[1] - '1') * 8
                    val to = mv[2] - 'a' + (mv[3] - '1') * 8
                    val newPieces = board.value.pieces.toMutableMap()
                    val piece = newPieces.remove(from)
                    if (piece != null) newPieces[to] = piece
                    board.value = board.value.copy(pieces = newPieces, sideToMove = if (side == PieceColor.WHITE) PieceColor.BLACK else PieceColor.WHITE)
                    eval.value = eng.score.value
                    eng.bestMove.value = null
                    plies++
                    delay(300)
                }
            }
            status.value = "Finished"
            running.value = false
        }
    }

    fun stop() { running.value = false }

    override fun onCleared() {
        engineA.stop(); engineB.stop()
        super.onCleared()
    }
}