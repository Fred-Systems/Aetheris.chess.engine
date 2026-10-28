package com.fredsystems.aetheris.engine

object NativeEngine {
    init {
        System.loadLibrary("aetheris")
    }

    external fun nativeInstallCrashHandler(reportPath: String)
    fun installCrashHandler(reportPath: String) = nativeInstallCrashHandler(reportPath)

    external fun nativeInit()
    @Synchronized
    fun initialize() = nativeInit()
    external fun nativeReset()
    external fun nativeSetPosition(fen: String)
    external fun nativeSetParams(
        aggression: Int,
        positional: Int,
        complexity: Int,
        solidity: Int,
        materialWeight: Int,
        mode: Int
    )
    external fun nativeSetNnue(path: String)
    external fun nativeNnueLoaded(): Boolean
    external fun nativeGo(depth: Int, timeMs: Int): String
    external fun nativeAnalyzeFen(fen: String, depth: Int, timeMs: Int, count: Int): String
    external fun nativeEvaluate(): Int
    external fun nativeEvaluateFen(fen: String): Int
    external fun nativeRawNnueEvaluateFen(fen: String): Int
    external fun nativeNnueSanityCheck(): String
    external fun nativeSearchEvaluationFen(fen: String, depth: Int, timeMs: Int): Int

    @Synchronized
    fun setPosition(fen: String) = nativeSetPosition(fen)

    @Synchronized
    fun setParams(aggression: Int, positional: Int, complexity: Int, solidity: Int, materialWeight: Int, mode: Int) =
        nativeSetParams(aggression, positional, complexity, solidity, materialWeight, mode)

    @Synchronized
    fun setNnue(path: String) = nativeSetNnue(path)

    @Synchronized
    fun isNnueLoaded(): Boolean = nativeNnueLoaded()

    @Synchronized
    fun go(depth: Int, timeMs: Int): String = nativeGo(depth, timeMs)

    // The native engine owns one process-global board. Keep position setup and
    // search in one monitor transaction so evaluation cannot replace the board mid-search.
    @Synchronized
    fun searchPosition(fen: String, depth: Int, timeMs: Int): String {
        nativeSetPosition(fen)
        return nativeGo(depth, timeMs)
    }

    // Keep style, position and search in one transaction because native state is process-global.
    @Synchronized
    fun searchConfiguredPosition(fen: String, depth: Int, timeMs: Int, aggression: Int, positional: Int, complexity: Int, solidity: Int, materialWeight: Int, mode: Int): String {
        nativeSetParams(aggression, positional, complexity, solidity, materialWeight, mode)
        nativeSetPosition(fen)
        return nativeGo(depth, timeMs)
    }

    @Synchronized
    fun searchPositionConfigured(
        fen: String, depth: Int, timeMs: Int,
        aggression: Int, positional: Int, complexity: Int, solidity: Int,
        materialWeight: Int, mode: Int
    ): String {
        // Keep engine options, board setup, and search in one monitor transaction.
        // The engine uses process-global native state, so separate calls can race
        // when an engine-vs-engine match runs beside the main game ViewModel.
        nativeSetParams(aggression, positional, complexity, solidity, materialWeight, mode)
        nativeSetPosition(fen)
        return nativeGo(depth, timeMs)
    }

    @Synchronized
    fun analyzeFen(fen: String, depth: Int, timeMs: Int, count: Int): String = nativeAnalyzeFen(fen, depth, timeMs, count)

    @Synchronized
    fun evaluate(): Int = nativeEvaluate()

    @Synchronized
    fun evaluateFen(fen: String): Int = nativeEvaluateFen(fen)

    @Synchronized
    fun rawNnueEvaluateFen(fen: String): Int = nativeRawNnueEvaluateFen(fen)

    @Synchronized
    fun nnueSanityCheck(): String = nativeNnueSanityCheck()

    @Synchronized
    fun searchEvaluationFen(fen: String, depth: Int, timeMs: Int): Int =
        nativeSearchEvaluationFen(fen, depth, timeMs)

    @Synchronized
    fun reset() = nativeReset()
}
