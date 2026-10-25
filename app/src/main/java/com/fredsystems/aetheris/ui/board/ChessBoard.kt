package com.fredsystems.aetheris.ui.board

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fredsystems.aetheris.domain.model.BoardState
import com.fredsystems.aetheris.domain.model.Piece
import com.fredsystems.aetheris.domain.model.PieceColor
import com.fredsystems.aetheris.domain.model.PieceType

@Composable
fun ChessBoard(
    board: BoardState,
    onSquareClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val light = Color(0xFFF0D9B5)
    val dark = Color(0xFFB58863)
    Column(modifier = modifier) {
        for (rank in 7 downTo 0) {
            Row {
                for (file in 0..7) {
                    val idx = rank * 8 + file
                    val isLight = (file + rank) % 2 == 0
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .background(if (isLight) light else dark)
                            .clickable { onSquareClick(idx) },
                        contentAlignment = Alignment.Center
                    ) {
                        board.pieces[idx]?.let { p ->
                            Text(
                                text = pieceSymbol(p),
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (p.color == PieceColor.WHITE) Color.White else Color.Black
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun pieceSymbol(p: Piece): String = when (p.type) {
    PieceType.KING -> if (p.color == PieceColor.WHITE) "♔" else "♚"
    PieceType.QUEEN -> if (p.color == PieceColor.WHITE) "♕" else "♛"
    PieceType.ROOK -> if (p.color == PieceColor.WHITE) "♖" else "♜"
    PieceType.BISHOP -> if (p.color == PieceColor.WHITE) "♗" else "♝"
    PieceType.KNIGHT -> if (p.color == PieceColor.WHITE) "♘" else "♞"
    PieceType.PAWN -> if (p.color == PieceColor.WHITE) "♙" else "♟"
}