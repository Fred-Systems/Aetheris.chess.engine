package com.fredsystems.aetheris.viewmodel

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fredsystems.aetheris.domain.model.BoardState
import com.fredsystems.aetheris.domain.model.EngineMode
import com.fredsystems.aetheris.domain.model.EngineOptions
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
import com.github.bhlangonijr.chesslib.move.Move as LibMove
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class GameViewModel(private val context: Context) : ViewModel() {
    private val engineProcess = EngineProcess(context)
    val engineManager = EngineManager(engineProcess)
    private var board = Board()
    private val moveList = mutableListOf<String>()
    private var pendingEngineMove = false

    val boardState = mutableStateOf(toUi(board))
    val evaluation = mutableStateOf(0)
    val bestMove = mutableStateOf<String?>(null)
    val options = mutableStateOf(EngineOptions())
    val engineThinking = mutableStateOf(false)
    val engineAvailable = mutableStateOf(true)

    init {
        try { engineManager.init() } catch (e: Exception) { engineAvailable.value = false }
        viewModelScope.launch {
            engineManager.score.collectLatest { evaluation.value = it }
        }
        viewModelScope.launch {
            engineManager.bestMove.collectLatest { mv ->
                if (mv.isNullOrBlank()) return@collectLatest
                if (pendingEngineMove) {
                    pendingEngineMove = false
                    engineThinking.value = false
                    applyUciMove(mv)
                }
            }
        }
        applyOptions(options.value)
    }

    fun onSquareClick(index: Int) {
        if (boardState.value.gameOver) return
        val sq = LibSquare.squareAt(index)
        val selected = boardState.value.selectedSquare
        if (selected != null) {
            val from = LibSquare.squareAt(selected)
            val legal = findLegalMove(from, sq)
            if (legal != null) {
                makeMove(legal)
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

    private fun makeMove(move: LibMove) {
        val from = move.from.ordinal
        val to = move.to.ordinal
        if (!board.doMove(move)) return
        moveList.add(move.toString())
        pendingEngineMove = false
        engineThinking.value = false
        val next = toUi(board).copy(
            selectedSquare = null,
            legalTargets = emptySet(),
            lastMoveFrom = from,
            lastMoveTo = to,
            moveNumber = boardState.value.moveNumber + 1
        )
        boardState.value = next
    }

    fun requestBestMove() {
        if (engineThinking.value || board.isMated || board.isDraw()) return
        pendingEngineMove = true
        engineThinking.value = true
        engineManager.newGame()
        engineManager.setPosition(board.fen)
        engineManager.go(options.value.depth, 0)
    }

    fun stopEngine() {
        pendingEngineMove = false
        engineThinking.value = false
        engineManager.sendStop()
    }

    fun newGame() {
        board = Board()
        moveList.clear()
        pendingEngineMove = false
        engineThinking.value = false
        boardState.value = toUi(board)
        engineManager.newGame()
    }

    fun undoMove() {
        if (board.backup.isEmpty()) return
        board.undoMove()
        if (moveList.isNotEmpty()) moveList.removeAt(moveList.size - 1)
        boardState.value = toUi(board).copy(moveNumber = boardState.value.moveNumber + 1)
        engineManager.newGame()
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
        makeMove(move)
    }

    fun updateOptions(o: EngineOptions) {
        options.value = o
        applyOptions(o)
    }

    private fun applyOptions(o: EngineOptions) {
        engineManager.setOption("Aggression", o.aggression.toString())
        engineManager.setOption("Positional", o.positional.toString())
        engineManager.setOption("Complexity", o.complexity.toString())
        engineManager.setOption("Solidity", o.solidity.toString())
        engineManager.setOption("MaterialWeight", o.materialWeight.toString())
        engineManager.setOption("Depth", o.depth.toString())
        engineManager.setOption("EngineMode", o.mode.uci)
        ensureNnueAsset()?.let { engineManager.setOption("NNUEFile", it) }
    }

    fun resetOptions() {
        updateOptions(EngineOptions())
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
        engineManager.stop()
        super.onCleared()
    }
}
