package com.fredsystems.aetheris.engine

import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.Piece
import com.github.bhlangonijr.chesslib.PieceType
import com.github.bhlangonijr.chesslib.Side
import com.github.bhlangonijr.chesslib.Square
import com.github.bhlangonijr.chesslib.move.Move
import kotlin.math.max

/** Independent Kotlin alpha-beta engine; does not call the Aetheris native search. */
object CaissaEngine {
    private val values = mapOf(PieceType.PAWN to 100, PieceType.KNIGHT to 320, PieceType.BISHOP to 335, PieceType.ROOK to 500, PieceType.QUEEN to 950, PieceType.KING to 20000)

    fun bestMove(fen: String, depth: Int = 3): String? {
        val board = Board().apply { loadFromFen(fen) }
        val rootSide = board.sideToMove
        var best: Move? = null
        var alpha = -1_000_000
        for (move in ordered(board)) {
            val child = Board().apply { loadFromFen(board.fen) }
            if (!child.doMove(move)) continue
            val score = -search(child, (depth - 1).coerceIn(0, 4), -1_000_000, -alpha, rootSide.flip())
            if (score > alpha) { alpha = score; best = move }
        }
        return best?.toString()?.lowercase()
    }

    fun evaluate(fen: String): Int = evaluateSideToMove(Board().apply { loadFromFen(fen) })

    fun principalLine(fen: String, depth: Int): List<String> {
        val line = mutableListOf<String>()
        val b = Board().apply { loadFromFen(fen) }
        repeat(depth.coerceIn(1, 5)) {
            val moveText = bestMove(b.fen, 2) ?: return@repeat
            val move = parseLegal(b, moveText) ?: return@repeat
            if (!b.doMove(move)) return@repeat
            line.add(moveText)
        }
        return line
    }

    private fun search(b: Board, depth: Int, alphaIn: Int, beta: Int, side: Side): Int {
        if (b.isMated()) return -900_000 - depth
        if (b.isDraw() || b.isStaleMate()) return 0
        if (depth <= 0) return evaluateSideToMove(b)
        var alpha = alphaIn
        var best = -1_000_000
        for (move in ordered(b)) {
            val child = Board().apply { loadFromFen(b.fen) }
            if (!child.doMove(move)) continue
            val score = -search(child, depth - 1, -beta, -alpha, side.flip())
            best = max(best, score)
            alpha = max(alpha, score)
            if (alpha >= beta) break
        }
        return if (best == -1_000_000) evaluateSideToMove(b) else best
    }

    private fun evaluateSideToMove(b: Board): Int {
        if (b.isMated()) return -900_000
        var score = 0
        for (sq in Square.values()) {
            val p = b.getPiece(sq)
            if (p == Piece.NONE) continue
            val v = values[p.pieceType] ?: 0
            score += if (p.pieceSide == b.sideToMove) v else -v
        }
        score += b.legalMoves().size * 2
        return score
    }

    private fun ordered(b: Board): List<Move> = b.legalMoves().sortedByDescending { move ->
        val victim = b.getPiece(move.to)
        (if (victim == Piece.NONE) 0 else (values[victim.pieceType] ?: 0) * 10) + (values[b.getPiece(move.from).pieceType] ?: 0) / 10
    }

    private fun parseLegal(b: Board, uci: String): Move? {
        val from = runCatching { Square.valueOf(uci.take(2).uppercase()) }.getOrNull() ?: return null
        val to = runCatching { Square.valueOf(uci.substring(2, 4).uppercase()) }.getOrNull() ?: return null
        return b.legalMoves().firstOrNull { it.from == from && it.to == to }
    }
}
