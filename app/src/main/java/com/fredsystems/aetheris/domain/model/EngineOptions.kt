package com.fredsystems.aetheris.domain.model

data class EngineOptions(
    val aggression: Int = 50,
    val materialWeight: Int = 50,
    val useOpeningBook: Boolean = false,
    val depth: Int = 6,
    val mode: EngineMode = EngineMode.NNUE
)