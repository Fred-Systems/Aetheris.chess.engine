package com.fredsystems.aetheris.viewmodel

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fredsystems.aetheris.domain.model.*
import com.fredsystems.aetheris.domain.model.EngineMode
import com.fredsystems.aetheris.engine.process.EngineManager
import com.fredsystems.aetheris.engine.process.EngineProcess
import android.content.Context
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class GameViewModel(private val context: Context) : ViewModel() {
    private val engineProcess = EngineProcess(context)
    val engineManager = EngineManager(engineProcess)

    val board = mutableStateOf(initialBoard())

    private fun initialBoard(): BoardState {
        val pieces = mutableMapOf<Int, Piece>()
        val backWhite = listOf(PieceType.ROOK, PieceType.KNIGHT, PieceType.BISHOP, PieceType.QUEEN, PieceType.KING, PieceType.BISHOP, PieceType.KNIGHT, PieceType.ROOK)
        val backBlack = backWhite
        for (f in 0..7) {
            pieces[f] = Piece(backWhite[f], PieceColor.WHITE)
            pieces[56 + f] = Piece(backBlack[f], PieceColor.BLACK)
            pieces[8 + f] = Piece(PieceType.PAWN, PieceColor.WHITE)
            pieces[48 + f] = Piece(PieceType.PAWN, PieceColor.BLACK)
        }
        return BoardState(pieces = pieces)
    }
    val evaluation = mutableStateOf(0)
    val bestMove = mutableStateOf<String?>(null)
    val options = mutableStateOf(EngineOptions())

    init {
        try { engineManager.init() } catch (e: Exception) { }
        viewModelScope.launch {
            engineManager.score.collectLatest { evaluation.value = it }
        }
        viewModelScope.launch {
            engineManager.bestMove.collectLatest { bestMove.value = it }
        }
    }

    fun onSquareClick(index: Int) {
        // simplified: no move validation; placeholder selection
    }

    fun requestBestMove() {
        engineManager.go(options.value.depth, 0)
    }

    fun applyEngineMove() {
        bestMove.value?.let { mv ->
            // parse uci move and update board minimally
            if (mv.length >= 4) {
                val from = mv[0] - 'a' + (mv[1] - '1') * 8
                val to = mv[2] - 'a' + (mv[3] - '1') * 8
                val newPieces = board.value.pieces.toMutableMap()
                val piece = newPieces.remove(from)
                if (piece != null) newPieces[to] = piece
                board.value = board.value.copy(pieces = newPieces, sideToMove = if (board.value.sideToMove == PieceColor.WHITE) PieceColor.BLACK else PieceColor.WHITE)
            }
        }
    }

    fun updateOptions(o: EngineOptions) {
        options.value = o
        engineManager.setOption("Aggression", o.aggression.toString())
        engineManager.setOption("Positional", o.positional.toString())
        engineManager.setOption("Complexity", o.complexity.toString())
        engineManager.setOption("Solidity", o.solidity.toString())
        engineManager.setOption("MaterialWeight", o.materialWeight.toString())
        engineManager.setOption("Depth", o.depth.toString())
        engineManager.setOption("EngineMode", o.mode.uci)
        val path = ensureNnueAsset()
        if (path != null) engineManager.setOption("NNUEFile", path)
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

    override fun onCleared() {
        engineManager.stop()
        super.onCleared()
    }
}