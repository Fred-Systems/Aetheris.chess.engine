package com.fredsystems.aetheris.ui.board

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.fredsystems.aetheris.R
import com.fredsystems.aetheris.domain.model.BoardState
import com.fredsystems.aetheris.domain.model.Piece
import com.fredsystems.aetheris.domain.model.PieceColor
import com.fredsystems.aetheris.domain.model.PieceType
import com.fredsystems.aetheris.ui.theme.BoardTheme
import com.fredsystems.aetheris.ui.theme.BoardThemes
import kotlin.math.roundToInt

private val SelectedGlow = Color(0xFF7C4DFF)
private val LegalDot = Color(0x662B1A0E)
private val LastMoveTint = Color(0x66F4C95D)
private val CheckTint = Color(0x99E53935)
private val BoardFrame = Color(0xFF17131F)

data class BoardArrow(val from: Int, val to: Int, val color: Color)

private object PieceBitmapCache {
    private val cache = LruCache<String, ImageBitmap>(96)
    fun get(context: android.content.Context, set: String, code: String): ImageBitmap? {
        val key = "$set/$code"
        cache.get(key)?.let { return it }
        return try {
            val bitmap = context.assets.open("pieces/$set/$code.png").use {
                BitmapFactory.decodeStream(it).asImageBitmap()
            }
            cache.put(key, bitmap)
            bitmap
        } catch (e: Exception) {
            null
        }
    }
}

@Composable
fun ChessBoard(
    board: BoardState,
    onSquareClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    pieceSet: String = "cburnett",
    boardTheme: BoardTheme = BoardThemes.byId("brown"),
    flipped: Boolean = false,
    arrows: List<BoardArrow> = emptyList(),
    blindfold: Boolean = false,
    onBoardLongPress: () -> Unit = {}
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val animation = remember { Animatable(1f) }
    var dragFrom by remember { mutableStateOf<Int?>(null) }
    var dragPosition by remember { mutableStateOf(Offset.Zero) }
    var dragging by remember { mutableStateOf(false) }
    var draggedMove by remember { mutableStateOf(false) }
    var boardWidthPx by remember { mutableStateOf(0) }
    var boardLongPressTriggered by remember { mutableStateOf(false) }

    LaunchedEffect(board.moveNumber) {
        if (board.moveNumber > 0 && draggedMove) {
            draggedMove = false
            animation.snapTo(1f)
        } else if (board.moveNumber > 0) {
            animation.snapTo(0f)
            animation.animateTo(1f, tween(190, easing = FastOutSlowInEasing))
        } else {
            animation.snapTo(1f)
        }
    }

    fun displayToIndex(row: Int, col: Int): Int =
        if (!flipped) (7 - row) * 8 + col else row * 8 + (7 - col)

    fun indexAt(position: Offset, widthPx: Int): Int? {
        if (widthPx <= 0) return null
        val cell = widthPx / 8f
        if (position.x < 0f || position.y < 0f || position.x >= widthPx || position.y >= widthPx) return null
        val col = (position.x / cell).toInt().coerceIn(0, 7)
        val row = (position.y / cell).toInt().coerceIn(0, 7)
        return displayToIndex(row, col)
    }

    BoxWithConstraints(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(18.dp))
            .background(BoardFrame)
            .onSizeChanged { boardWidthPx = it.width }
            .pointerInput(onBoardLongPress) {
                detectTapGestures(
                    onPress = {
                        boardLongPressTriggered = false
                        tryAwaitRelease()
                    },
                    onLongPress = {
                        boardLongPressTriggered = true
                        onBoardLongPress()
                    }
                )
            }
            .pointerInput(flipped, board.pieces, board.sideToMove, board.gameOver, blindfold, onBoardLongPress) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val start = indexAt(down.position, size.width) ?: return@awaitEachGesture
                    dragFrom = start
                    dragPosition = down.position
                    dragging = false
                    var released = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        dragPosition = change.position
                        if (!change.pressed) {
                            released = true
                            val target = indexAt(change.position, size.width)
                            if (boardLongPressTriggered) {
                                // The long press opens engine controls; don't also move/select a square.
                            } else if (dragging) {
                                if (target != null && target != start) {
                                    draggedMove = true
                                    onSquareClick(start)
                                    onSquareClick(target)
                                } else {
                                    onSquareClick(start)
                                }
                            } else {
                                onSquareClick(start)
                            }
                            break
                        }
                        if (!dragging && (change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                            val piece = board.pieces[start]
                            if (piece != null && piece.color == board.sideToMove) {
                                dragging = true
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                change.consume()
                            }
                        } else if (dragging) {
                            change.consume()
                        }
                    }
                    if (!released && dragFrom != null) dragFrom = null
                    dragFrom = null
                    dragging = false
                }
            }
    ) {
        val square = maxWidth / 8
        val squarePx = with(LocalDensity.current) { square.toPx() }
        val animating = board.lastMoveFrom != null && board.lastMoveTo != null && animation.value < 1f
        val light = boardTheme.light
        val dark = boardTheme.dark

        Canvas(Modifier.fillMaxSize()) {
            val cell = size.width / 8f
            for (row in 0..7) {
                for (col in 0..7) {
                    val topLeft = Offset(col * cell, row * cell)
                    drawRect(if ((row + col) % 2 == 0) light else dark, topLeft, Size(cell, cell))
                }
            }
            for (row in 0..7) {
                for (col in 0..7) {
                    val index = displayToIndex(row, col)
                    val topLeft = Offset(col * cell, row * cell)
                    val full = Size(cell, cell)
                    if (index == board.lastMoveFrom || index == board.lastMoveTo) {
                        drawRect(LastMoveTint, topLeft, full)
                    }
                    if (index == board.selectedSquare) {
                        drawRect(SelectedGlow.copy(alpha = 0.45f), topLeft, full)
                    }
                    if (index == board.checkSquare) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(CheckTint, Color.Transparent),
                                center = Offset(topLeft.x + cell / 2, topLeft.y + cell / 2),
                                radius = cell * 0.7f
                            ),
                            radius = cell * 0.7f,
                            center = Offset(topLeft.x + cell / 2, topLeft.y + cell / 2)
                        )
                    }
                    if (index in board.legalTargets) {
                        val center = Offset(topLeft.x + cell / 2, topLeft.y + cell / 2)
                        if (board.pieces.containsKey(index)) {
                            drawCircle(
                                color = Color(0x552B1A0E),
                                radius = cell * 0.42f,
                                center = center,
                                style = Stroke(width = cell * 0.09f)
                            )
                        } else {
                            drawCircle(
                                color = LegalDot,
                                radius = cell * 0.14f,
                                center = center
                            )
                        }
                    }
                }
            }
            // Lichess-style translucent arrows are drawn above the square highlights and below pieces.
            arrows.forEach { arrow ->
                fun point(index: Int): Offset {
                    val file = index % 8
                    val rank = index / 8
                    val col = if (!flipped) file else 7 - file
                    val row = if (!flipped) 7 - rank else rank
                    return Offset((col + 0.5f) * cell, (row + 0.5f) * cell)
                }
                val a = point(arrow.from)
                val b = point(arrow.to)
                val dx = b.x - a.x
                val dy = b.y - a.y
                val len = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
                val ux = dx / len
                val uy = dy / len
                val tip = Offset(b.x - ux * cell * 0.20f, b.y - uy * cell * 0.20f)
                drawLine(arrow.color.copy(alpha = 0.78f), a, tip, strokeWidth = cell * 0.13f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                val baseX = tip.x - ux * cell * 0.27f
                val baseY = tip.y - uy * cell * 0.27f
                val wing = cell * 0.17f
                val head = Path().apply {
                    moveTo(tip.x, tip.y)
                    lineTo(baseX - uy * wing, baseY + ux * wing)
                    lineTo(baseX + uy * wing, baseY - ux * wing)
                    close()
                }
                drawPath(head, arrow.color.copy(alpha = 0.88f))
            }
        }

        for (row in 0..7) {
            for (col in 0..7) {
                val index = displayToIndex(row, col)
                val piece = board.pieces[index]
                val isDragged = dragging && index == dragFrom
                if (piece != null && !blindfold && !isDragged && !(animating && index == board.lastMoveTo)) {
                    PieceImage(
                        piece = piece,
                        pieceSet = pieceSet,
                        modifier = Modifier
                            .offset(x = square * col, y = square * row)
                            .size(square)
                            .padding(square * 0.045f)
                    )
                }
                val actualFile = index % 8
                val actualRank = index / 8
                if (row == 7) {
                    Text(
                        text = ('a' + actualFile).toString(),
                        modifier = Modifier
                            .offset(x = square * col, y = square * row)
                            .size(square)
                            .padding(2.dp),
                        color = if ((row + col) % 2 == 0) Color(0x993B2A1A) else Color(0x99F6E7D2),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (col == 0) {
                    Text(
                        text = "${actualRank + 1}",
                        modifier = Modifier
                            .offset(x = square * col, y = square * row)
                            .size(square)
                            .padding(2.dp),
                        color = if ((row + col) % 2 == 0) Color(0x993B2A1A) else Color(0x99F6E7D2),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.End
                    )
                }
            }
        }

        if (animating && board.lastMoveFrom != null && board.lastMoveTo != null) {
            val fromIndex = board.lastMoveFrom
            val toIndex = board.lastMoveTo
            val from = if (!flipped) {
                Offset((fromIndex % 8).toFloat(), (7 - fromIndex / 8).toFloat())
            } else {
                Offset((7 - fromIndex % 8).toFloat(), (fromIndex / 8).toFloat())
            }
            val to = if (!flipped) {
                Offset((toIndex % 8).toFloat(), (7 - toIndex / 8).toFloat())
            } else {
                Offset((7 - toIndex % 8).toFloat(), (toIndex / 8).toFloat())
            }
            val movingPiece = board.pieces[toIndex]
            if (movingPiece != null) {
                val x = square * (from.x + (to.x - from.x) * animation.value)
                val y = square * (from.y + (to.y - from.y) * animation.value)
                PieceImage(
                    piece = movingPiece,
                    pieceSet = pieceSet,
                    modifier = Modifier
                        .offset(x = x, y = y)
                        .size(square)
                        .padding(square * 0.045f)
                        .zIndex(3f)
                )
            }
        }

        if (dragging && dragFrom != null) {
            val piece = board.pieces[dragFrom!!]
            if (piece != null && !blindfold) {
                PieceImage(
                    piece = piece,
                    pieceSet = pieceSet,
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (dragPosition.x - squarePx / 2f).roundToInt(),
                                (dragPosition.y - squarePx / 2f).roundToInt()
                            )
                        }
                        .size(square)
                        .padding(square * 0.02f)
                        .zIndex(5f)
                )
            }
        }
    }
}

@Composable
private fun PieceImage(piece: Piece, pieceSet: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val code = pieceCode(piece)
    val bitmap = remember(pieceSet, code) { PieceBitmapCache.get(context, pieceSet, code) }
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = modifier,
            filterQuality = FilterQuality.High
        )
    } else {
        Image(
            painter = painterResource(id = legacyPieceRes(piece)),
            contentDescription = null,
            modifier = modifier
        )
    }
}

private fun pieceCode(piece: Piece): String {
    val prefix = if (piece.color == PieceColor.WHITE) "w" else "b"
    val suffix = when (piece.type) {
        PieceType.KING -> "K"
        PieceType.QUEEN -> "Q"
        PieceType.ROOK -> "R"
        PieceType.BISHOP -> "B"
        PieceType.KNIGHT -> "N"
        PieceType.PAWN -> "P"
    }
    return prefix + suffix
}

private fun legacyPieceRes(piece: Piece): Int {
    return when (pieceCode(piece)) {
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
