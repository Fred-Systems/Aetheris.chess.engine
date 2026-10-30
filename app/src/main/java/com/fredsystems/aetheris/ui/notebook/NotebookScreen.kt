package com.fredsystems.aetheris.ui.notebook

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.fredsystems.aetheris.domain.model.BoardState
import com.fredsystems.aetheris.domain.model.GameRecord
import com.fredsystems.aetheris.domain.model.Piece
import com.fredsystems.aetheris.domain.model.PieceColor
import com.fredsystems.aetheris.domain.model.PieceType
import com.fredsystems.aetheris.ui.board.ChessBoard
import com.fredsystems.aetheris.viewmodel.GameNotebookViewModel

@Composable
fun NotebookScreen(viewModel: GameNotebookViewModel, onBack: () -> Unit, onOpenAnalysis: (GameRecord) -> Unit = {}) {
    val games by viewModel.games
    val selected by viewModel.selected
    val ply by viewModel.ply
    var comment by remember { mutableStateOf(TextFieldValue("")) }
    var arrowMode by remember { mutableStateOf(false) }
    var variationText by remember { mutableStateOf("") }
    var showEditor by remember { mutableStateOf(false) }

    if (selected == null) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Text("Game Notebook", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Button(onClick = onBack) { Text("Back") }
            Spacer(Modifier.height(8.dp))
            games.forEach { g ->
                TextButton(onClick = { viewModel.select(g); onOpenAnalysis(g) }) { Text(g.title) }
            }
            if (games.isEmpty()) Text("No saved games yet.")
        }
        return
    }
    val g = selected!!
    val activeMoves = viewModel.displayedMoves()
    val boardState = remember(g, ply, activeMoves) { buildBoard(g.copy(moves = activeMoves), ply) }
    Column(Modifier.fillMaxSize().padding(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(g.title, style = MaterialTheme.typography.titleMedium)
            Row { TextButton(onClick = { onOpenAnalysis(g) }) { Text("Open Analysis") }; TextButton(onClick = { viewModel.selected.value = null }) { Text("Back") } }
        }
        Box(Modifier.weight(1f)) {
            ChessBoard(board = boardState, onSquareClick = {}, modifier = Modifier.fillMaxSize())
            // draw arrows for current ply
            val arrows = g.arrows[ply] ?: emptyList()
            Canvas(Modifier.fillMaxSize()) {
                arrows.forEach { a ->
                    if (a.length >= 4) {
                        val from = a.substring(0,2)
                        val to = a.substring(2,4)
                        val f = squareCenter(from, size.width, size.height)
                        val t = squareCenter(to, size.width, size.height)
                        drawLine(Color.Red, f, t, strokeWidth = 8f)
                    }
                }
            }
        }
        Row {
            Button(onClick = { if (ply > 0) viewModel.ply.value = ply - 1 }) { Text("Prev") }
            Spacer(Modifier.width(8.dp))
            Button(onClick = { if (ply < activeMoves.size) viewModel.ply.value = ply + 1 }) { Text("Next") }
            Spacer(Modifier.width(8.dp))
            Text("Ply ${ply}/${activeMoves.size}")
        }
        Text("Glyphs: ${g.glyphs[ply] ?: ""}")
        Row {
            listOf("!", "?", "!!", "??", "!?").forEach { gl ->
                TextButton(onClick = { viewModel.addGlyph(gl) }) { Text(gl) }
            }
        }
        OutlinedTextField(value = comment, onValueChange = { comment = it }, label = { Text("Comment") }, modifier = Modifier.fillMaxWidth())
        Button(onClick = { viewModel.addComment(comment.text); comment = TextFieldValue("") }) { Text("Save Comment") }
        Text(g.comments[ply] ?: "")
        TextButton(onClick = { showEditor = !showEditor }) { Text(if (showEditor) "Close annotation editor" else "Open annotation and variation editor") }
        if (showEditor) {
            OutlinedTextField(value = variationText, onValueChange = { variationText = it }, label = { Text("Alternative UCI line: e2e4 e7e5") }, modifier = Modifier.fillMaxWidth())
            Button(onClick = { val line = variationText.trim().split(Regex("\\\\s+")).filter { it.matches(Regex("[a-h][1-8][a-h][1-8][qrbn]?")) }; viewModel.saveVariation(line); variationText = "" }, enabled = variationText.isNotBlank()) { Text("Save line at this position") }
            g.variations.forEach { (at, line) -> TextButton(onClick = { viewModel.viewVariation(line) }) { Text("View line from ply " + at + ": " + line.joinToString(" ")) } }
            if (viewModel.activeVariation.value.isNotEmpty()) TextButton(onClick = { viewModel.returnToMainLine() }) { Text("Return to main game") }
        }
    }
}

private fun buildBoard(g: GameRecord, ply: Int): BoardState {
    val pieces = mutableMapOf<Int, Piece>()
    val back = listOf(PieceType.ROOK, PieceType.KNIGHT, PieceType.BISHOP, PieceType.QUEEN, PieceType.KING, PieceType.BISHOP, PieceType.KNIGHT, PieceType.ROOK)
    for (f in 0..7) {
        pieces[f] = Piece(back[f], PieceColor.WHITE)
        pieces[56 + f] = Piece(back[f], PieceColor.BLACK)
        pieces[8 + f] = Piece(PieceType.PAWN, PieceColor.WHITE)
        pieces[48 + f] = Piece(PieceType.PAWN, PieceColor.BLACK)
    }
    var side = PieceColor.WHITE
    for (i in 0 until ply.coerceAtMost(g.moves.size)) {
        val mv = g.moves[i]
        if (mv.length >= 4) {
            val from = mv[0] - 'a' + (mv[1] - '1') * 8
            val to = mv[2] - 'a' + (mv[3] - '1') * 8
            val piece = pieces.remove(from)
            if (piece != null) {
                pieces.remove(to)
                pieces[to] = piece
            }
        }
        side = if (side == PieceColor.WHITE) PieceColor.BLACK else PieceColor.WHITE
    }
    return BoardState(pieces = pieces, sideToMove = side)
}

private fun squareCenter(sq: String, w: Float, h: Float): Offset {
    val file = sq[0] - 'a'
    val rank = sq[1] - '1'
    val sqSize = w / 8f
    val x = (file + 0.5f) * sqSize
    val y = (7 - rank + 0.5f) * sqSize
    return Offset(x, y)
}