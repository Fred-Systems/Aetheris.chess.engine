package com.fredsystems.aetheris.domain.model

data class GameRecord(
    val id: Long = System.currentTimeMillis(),
    val title: String,
    val pgn: String,
    val moves: List<String> = emptyList(),
    val comments: Map<Int, String> = emptyMap(),
    val glyphs: Map<Int, String> = emptyMap(),
    val arrows: Map<Int, List<String>> = emptyMap(),
    val variations: Map<Int, List<String>> = emptyMap()
)