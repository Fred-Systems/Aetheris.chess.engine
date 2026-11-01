package com.fredsystems.aetheris.engine

import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.Piece
import com.github.bhlangonijr.chesslib.PieceType
import com.github.bhlangonijr.chesslib.Side
import com.github.bhlangonijr.chesslib.Square
import com.github.bhlangonijr.chesslib.move.Move
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Morrow is an independently written Kotlin chess engine.
 *
 * It does not call Aetheris native search or Caissa. Its search is an iterative
 * deepening principal-variation search with a transposition table, quiescence,
 * killer/history ordering and a tapered handcrafted evaluator.
 */
object MorrowEngine {
    private const val INF = 1_000_000
    private const val MATE = 900_000
    private val value = intArrayOf(100, 325, 335, 510, 950, 20_000)
    private data class Entry(val depth: Int, val score: Int, val bound: Int, val best: String?)
    private val table = LinkedHashMap<String, Entry>(32_768, 0.75f, true)
    private val killers = Array(96) { arrayOfNulls<String>(2) }
    private val history = IntArray(2 * 64 * 64)
    @Volatile private var deadline = Long.MAX_VALUE
    private var nodes = 0L
    private var rootBest: String? = null

    fun evaluate(fen: String): Int = evaluate(Board().apply { loadFromFen(fen) })

    @Synchronized
    fun bestMove(fen: String, maxDepth: Int = 8, timeMs: Int = 1200): String? {
        val root = Board().apply { loadFromFen(fen) }
        if (root.isMated || root.isDraw || root.isStaleMate) return null
        deadline = System.nanoTime() + timeMs.coerceAtLeast(50) * 1_000_000L
        nodes = 0
        rootBest = null
        table.clear()
        for (i in killers.indices) { killers[i][0] = null; killers[i][1] = null }
        history.fill(0)
        var completed: String? = null
        for (depth in 1..maxDepth.coerceIn(1, 18)) {
            try {
                val result = searchRoot(root, depth)
                if (result != null) completed = result
            } catch (_: SearchTimeout) {
                break
            }
            if (System.nanoTime() >= deadline) break
        }
        return completed ?: rootBest
    }

    private class SearchTimeout : RuntimeException()
    private fun checkTime() { if ((nodes and 1023L) == 0L && System.nanoTime() >= deadline) throw SearchTimeout() }

    private fun searchRoot(position: Board, depth: Int): String? {
        var alpha = -INF
        val beta = INF
        var best: Move? = null
        val moves = ordered(position, position.legalMoves(), null, 0)
        for ((index, move) in moves.withIndex()) {
            checkTime()
            val child = Board().apply { loadFromFen(position.fen) }
            if (!child.doMove(move)) continue
            val score = if (index == 0) -negamax(child, depth - 1, -beta, -alpha, 1)
                else -negamax(child, depth - 1, -alpha - 1, -alpha, 1).let { probe ->
                    if (probe > alpha && probe < beta) -negamax(child, depth - 1, -beta, -alpha, 1) else probe
                }
            if (score > alpha) { alpha = score; best = move }
        }
        best?.toString()?.lowercase()?.let { rootBest = it }
        return rootBest
    }

    private fun negamax(b: Board, depth: Int, alphaIn: Int, beta: Int, ply: Int): Int {
        nodes++
        checkTime()
        if (b.isMated()) return -MATE + ply
        if (b.isDraw || b.isStaleMate) return 0
        if (depth <= 0) return quiescence(b, alphaIn, beta, ply)
        val key = b.fen
        val cached = table[key]
        var alpha = alphaIn
        if (cached != null && cached.depth >= depth) {
            when (cached.bound) {
                0 -> return cached.score
                -1 -> if (cached.score <= alpha) return cached.score
                1 -> if (cached.score >= beta) return cached.score
            }
        }
        val originalAlpha = alpha
        var bestScore = -INF
        var bestMove: Move? = null
        val legal = b.legalMoves()
        if (legal.isEmpty()) return if (b.isKingAttacked) -MATE + ply else 0
        val ttMove = cached?.best
        val ordered = ordered(b, legal, ttMove, ply)
        for ((index, move) in ordered.withIndex()) {
            val child = Board().apply { loadFromFen(key) }
            if (!child.doMove(move)) continue
            val quiet = b.getPiece(move.to) == Piece.NONE && move.promotion == Piece.NONE
            var score = if (index == 0) -negamax(child, depth - 1, -beta, -alpha, ply + 1)
                else -negamax(child, depth - 1, -alpha - 1, -alpha, ply + 1).let { probe ->
                    if (probe > alpha && probe < beta) -negamax(child, depth - 1, -beta, -alpha, ply + 1) else probe
                }
            if (score > bestScore) { bestScore = score; bestMove = move }
            if (score > alpha) alpha = score
            if (alpha >= beta) {
                if (quiet && ply < killers.size) {
                    val uci = move.toString().lowercase()
                    if (killers[ply][0] != uci) { killers[ply][1] = killers[ply][0]; killers[ply][0] = uci }
                    val side = if (b.sideToMove == Side.WHITE) 0 else 1
                    val idx = side * 4096 + move.from.ordinal * 64 + move.to.ordinal
                    history[idx] = (history[idx] + depth * depth).coerceAtMost(100_000)
                }
                break
            }
        }
        if (bestScore == -INF) bestScore = evaluate(b)
        val bound = if (bestScore <= originalAlpha) -1 else if (bestScore >= beta) 1 else 0
        table[key] = Entry(depth, bestScore, bound, bestMove?.toString()?.lowercase())
        if (table.size > 150_000) repeat(30_000) { table.entries.iterator().let { it.next(); it.remove() } }
        return bestScore
    }

    private fun quiescence(b: Board, alphaIn: Int, beta: Int, ply: Int): Int {
        nodes++
        checkTime()
        if (b.isMated()) return -MATE + ply
        val inCheck = b.isKingAttacked
        var alpha = alphaIn
        if (!inCheck) {
            val stand = evaluate(b)
            if (stand >= beta) return beta
            if (stand > alpha) alpha = stand
        }
        val moves = b.legalMoves()
        val tactical = if (inCheck) moves else moves.filter {
            b.getPiece(it.to) != Piece.NONE || it.promotion != Piece.NONE ||
                (b.getPiece(it.from).pieceType == PieceType.PAWN && it.from.file != it.to.file)
        }
        for (move in ordered(b, tactical, null, ply)) {
            val child = Board().apply { loadFromFen(b.fen) }
            if (!child.doMove(move)) continue
            val score = -quiescence(child, -beta, -alpha, ply + 1)
            if (score >= beta) return beta
            if (score > alpha) alpha = score
        }
        return alpha
    }

    private fun ordered(b: Board, moves: List<Move>, ttMove: String?, ply: Int): List<Move> {
        val side = if (b.sideToMove == Side.WHITE) 0 else 1
        return moves.sortedByDescending { move ->
            val uci = move.toString().lowercase()
            val victim = b.getPiece(move.to)
            when {
                uci == ttMove -> 2_000_000
                move.promotion != Piece.NONE -> 1_500_000 + value[move.promotion.pieceType.ordinal]
                victim != Piece.NONE -> 1_000_000 + value[victim.pieceType.ordinal] * 16 - value[b.getPiece(move.from).pieceType.ordinal]
                ply < killers.size && killers[ply][0] == uci -> 900_000
                ply < killers.size && killers[ply][1] == uci -> 800_000
                else -> history[side * 4096 + move.from.ordinal * 64 + move.to.ordinal]
            }
        }
    }

    /** Evaluation is from the side-to-move perspective. */
    private fun evaluate(b: Board): Int {
        var white = 0
        var black = 0
        var phase = 0
        for (sq in Square.values()) {
            val p = b.getPiece(sq)
            if (p == Piece.NONE) continue
            val type = p.pieceType.ordinal
            val material = value[type]
            if (type in 1..4) phase += when (type) { 1 -> 1; 2 -> 1; 3 -> 2; else -> 4 }
            val file = sq.file
            val rank = sq.rank
            val relativeRank = if (p.pieceSide == Side.WHITE) rank else 7 - rank
            val center = 7 - (abs(file * 2 - 7) + abs(rank * 2 - 7))
            val positional = when (type) {
                0 -> relativeRank * 7 + center * 2
                1 -> center * 7
                2 -> center * 4
                3 -> relativeRank * 2 + center
                4 -> center * 2
                else -> -center * (if (phase > 12) 3 else -4)
            }
            if (p.pieceSide == Side.WHITE) white += material + positional else black += material + positional
        }
        val mobility = b.legalMoves().size * 2
        val whiteScore = white - black + if (b.sideToMove == Side.WHITE) mobility else -mobility
        return if (b.sideToMove == Side.WHITE) whiteScore else -whiteScore
    }
}
