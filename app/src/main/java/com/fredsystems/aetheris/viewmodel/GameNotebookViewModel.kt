package com.fredsystems.aetheris.viewmodel

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.fredsystems.aetheris.data.GameRepository
import com.fredsystems.aetheris.domain.model.GameRecord

class GameNotebookViewModel(private val context: Context) : ViewModel() {
    private val repo = GameRepository(context)
    val games = mutableStateOf(repo.load())
    val selected = mutableStateOf<GameRecord?>(null)
    val ply = mutableStateOf(0)
    val activeVariation = mutableStateOf<List<String>>(emptyList())

    fun refresh() { games.value = repo.load() }

    fun saveGame(title: String, pgn: String, moves: List<String>) {
        val rec = GameRecord(title = title, pgn = pgn, moves = moves)
        val list = games.value.toMutableList()
        list.add(0, rec)
        games.value = list
        repo.save(list)
    }

    fun select(g: GameRecord) { selected.value = g; ply.value = 0; activeVariation.value = emptyList() }

    fun saveVariation(line: List<String>) {
        val g = selected.value ?: return
        if (line.isEmpty()) return
        val updated = g.copy(variations = g.variations + (ply.value to line))
        replace(updated)
    }

    fun viewVariation(line: List<String>) { val g = selected.value ?: return; activeVariation.value = g.moves.take(ply.value) + line; ply.value = 0 }
    fun returnToMainLine() { activeVariation.value = emptyList(); ply.value = 0 }
    fun displayedMoves(): List<String> = activeVariation.value.ifEmpty { selected.value?.moves ?: emptyList() }

    fun addComment(text: String) {
        val g = selected.value ?: return
        if (text.isBlank()) return
        val newComments = g.comments.toMutableMap()
        newComments[ply.value] = text
        val updated = g.copy(comments = newComments)
        replace(updated)
    }

    fun addGlyph(glyph: String) {
        val g = selected.value ?: return
        val newGlyphs = g.glyphs.toMutableMap()
        newGlyphs[ply.value] = glyph
        val updated = g.copy(glyphs = newGlyphs)
        replace(updated)
    }

    private fun replace(updated: GameRecord) {
        val list = games.value.toMutableList()
        val idx = list.indexOfFirst { it.id == updated.id }
        if (idx >= 0) list[idx] = updated
        selected.value = updated
        games.value = list
        repo.save(list)
    }
}