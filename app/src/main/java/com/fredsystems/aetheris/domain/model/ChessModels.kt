package com.fredsystems.aetheris.domain.model

data class Square(val file: Int, val rank: Int) {
    val index: Int get() = rank * 8 + file
    companion object { fun fromIndex(i: Int) = Square(i % 8, i / 8) }
}

enum class PieceType { PAWN, KNIGHT, BISHOP, ROOK, QUEEN, KING }
enum class PieceColor { WHITE, BLACK }

data class Piece(val type: PieceType, val color: PieceColor)

data class Move(val from: Int, val to: Int, val promotion: PieceType? = null)

data class BoardState(
    val pieces: Map<Int, Piece> = emptyMap(),
    val sideToMove: PieceColor = PieceColor.WHITE,
    val castling: String = "KQkq",
    val enPassant: Int? = null,
    val halfMove: Int = 0,
    val fullMove: Int = 1,
    val selectedSquare: Int? = null,
    val legalTargets: Set<Int> = emptySet(),
    val lastMoveFrom: Int? = null,
    val lastMoveTo: Int? = null,
    val premoveHighlights: Set<Int> = emptySet(),
    val checkSquare: Int? = null,
    val moveNumber: Int = 0,
    val gameOver: Boolean = false,
    val statusText: String = ""
)
