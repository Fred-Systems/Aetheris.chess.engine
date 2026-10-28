package com.fredsystems.aetheris.domain.model

enum class EngineMode(val uci: String, val label: String) {
    DUAL_CORE("DualCore", "Aetheris Dual-Core"),
    PURE_NNUE("PureNNUE", "Aetheris NNUE Hybrid"),
    PURE_CLASSICAL("PureClassical", "Aetheris Pure Classical")
}