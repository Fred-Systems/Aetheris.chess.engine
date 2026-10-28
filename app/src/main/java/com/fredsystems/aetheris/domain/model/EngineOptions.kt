package com.fredsystems.aetheris.domain.model

data class EngineOptions(
    val aggression: Int = 50,
    val positional: Int = 50,
    val complexity: Int = 50,
    val solidity: Int = 50,
    val materialWeight: Int = 50,
    val depth: Int = 8,
    val mode: EngineMode = EngineMode.PURE_CLASSICAL
)