package com.fredsystems.aetheris.ui.board

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.fredsystems.aetheris.R
import com.fredsystems.aetheris.domain.model.BoardState
import com.fredsystems.aetheris.domain.model.Piece
import com.fredsystems.aetheris.domain.model.PieceColor
import com.fredsystems.aetheris.domain.model.PieceType

private val LightSquare = Color(0xFFF0D9B5)
private val DarkSquare = Color(0xFFB58863)
private val SelectedGlow = Color(0xFF7C4DFF)
private val LegalDot = Color(0x662B1A0E)
private val LastMoveTint = Color(0x66F4C95D)
private val CheckTint = Color(0x99E53935)
private val BoardFrame = Color(0xFF17131F)

@Composable
fun ChessBoard(
    board: BoardState,
    onSquareClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val animation = remember { Animatable(1f) }
    LaunchedEffect(board.moveNumber) {
        if (board.moveNumber > 0) {
            animation.snapTo(0f)
            animation.animateTo(1f, tween(190, easing = FastOutSlowInEasing))
        } else {
            animation.snapTo(1f)
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(18.dp))
            .background(BoardFrame)
            .padding(4.dp)
            .clip(RoundedCornerShape(15.dp))
    ) {
        val square = maxWidth / 8
        val animating = board.lastMoveFrom != null && board.lastMoveTo != null && animation.value < 1f

        for (rank in 7 downTo 0) {
            for (file in 0..7) {
                val index = rank * 8 + file
                val isLight = (file + rank) % 2 == 0
                val tint = when {
                    index == board.lastMoveFrom || index == board.lastMoveTo -> LastMoveTint
                    else -> Color.Transparent
                }
                Box(
                    modifier = Modifier
                        .offset(x = square * file, y = square * (7 - rank))
                        .size(square)
                        .background(if (isLight) LightSquare else DarkSquare)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onSquareClick(index)
                        }
                ) {
                    if (tint != Color.Transparent) {
                        Box(Modifier.fillMaxSize().background(tint))
                    }
                    if (index == board.selectedSquare) {
                        Box(Modifier.fillMaxSize().background(SelectedGlow.copy(alpha = 0.45f)))
                    }
                    if (index == board.checkSquare) {
                        Box(Modifier.fillMaxSize().background(
                            Brush.radialGradient(listOf(CheckTint, Color.Transparent))
                        ))
                    }
                    if (index in board.legalTargets) {
                        val isCapture = board.pieces.containsKey(index) || index == board.lastMoveTo
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            if (isCapture) {
                                Box(Modifier.fillMaxSize(0.78f).clip(RoundedCornerShape(50)).background(Color(0x332B1A0E)))
                            } else {
                                Box(Modifier.size(square * 0.26f).clip(RoundedCornerShape(50)).background(LegalDot))
                            }
                        }
                    }
                    val piece = board.pieces[index]
                    if (piece != null && !(animating && index == board.lastMoveTo)) {
                        PieceImage(piece, Modifier.fillMaxSize().padding(square * 0.045f))
                    }
                    if (file == 0) {
                        Text(
                            text = "${rank + 1}",
                            modifier = Modifier.align(Alignment.TopStart).padding(2.dp),
                            color = if (isLight) Color(0x993B2A1A) else Color(0x99F6E7D2),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (rank == 0) {
                        Text(
                            text = ('a' + file).toString(),
                            modifier = Modifier.align(Alignment.BottomEnd).padding(2.dp),
                            color = if (isLight) Color(0x993B2A1A) else Color(0x99F6E7D2),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        if (animating && board.lastMoveFrom != null && board.lastMoveTo != null) {
            val fromFile = board.lastMoveFrom % 8
            val fromRank = board.lastMoveFrom / 8
            val toFile = board.lastMoveTo % 8
            val toRank = board.lastMoveTo / 8
            val movingPiece = board.pieces[board.lastMoveTo]
            if (movingPiece != null) {
                val x = lerp(square * fromFile, square * toFile, animation.value)
                val y = lerp(square * (7 - fromRank), square * (7 - toRank), animation.value)
                PieceImage(
                    piece = movingPiece,
                    modifier = Modifier
                        .offset(x = x, y = y)
                        .size(square)
                        .padding(square * 0.045f)
                        .zIndex(3f)
                )
            }
        }
    }
}

@Composable
private fun PieceImage(piece: Piece, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(id = pieceRes(piece)),
        contentDescription = null,
        modifier = modifier
    )
}

private fun pieceRes(piece: Piece): Int {
    val prefix = if (piece.color == PieceColor.WHITE) "w" else "b"
    val suffix = when (piece.type) {
        PieceType.KING -> "K"
        PieceType.QUEEN -> "Q"
        PieceType.ROOK -> "R"
        PieceType.BISHOP -> "B"
        PieceType.KNIGHT -> "N"
        PieceType.PAWN -> "P"
    }
    return when (prefix + suffix) {
        "wK" -> R.drawable.piece_wk
        "wQ" -> R.drawable.piece_wq
        "wR" -> R.drawable.piece_wr
        "wB" -> R.drawable.piece_wb
        "wN" -> R.drawable.piece_wn
        "wP" -> R.drawable.piece_wp
        "bK" -> R.drawable.piece_bk
        "bQ" -> R.drawable.piece_bq
        "bR" -> R.drawable.piece_br
        "bB" -> R.drawable.piece_bb
        "bN" -> R.drawable.piece_bn
        else -> R.drawable.piece_bp
    }
}
