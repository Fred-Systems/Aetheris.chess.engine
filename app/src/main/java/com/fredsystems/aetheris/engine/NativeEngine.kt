package com.fredsystems.aetheris.engine

object NativeEngine {
    init {
        System.loadLibrary("aetheris")
    }

    external fun nativeInit()\n    external fun nativeSetCrashReportPath(path: String)\n\n    fun setCrashReportPath(path: String) = nativeSetCrashReportPath(path)

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
    external fun nativeGo(depth: Int, timeMs: Int): String
    external fun nativeEvaluate(): Int
    external fun nativeEvaluateFen(fen: String): Int

    @Synchronized
    fun setPosition(fen: String) = nativeSetPosition(fen)

    @Synchronized
    fun setParams(aggression: Int, positional: Int, complexity: Int, solidity: Int, materialWeight: Int, mode: Int) =
        nativeSetParams(aggression, positional, complexity, solidity, materialWeight, mode)

    @Synchronized
    fun setNnue(path: String) = nativeSetNnue(path)

    @Synchronized
    fun go(depth: Int, timeMs: Int): String = nativeGo(depth, timeMs)

    // The native engine owns one process-global board. Keep position setup and
    // search in one monitor transaction so evaluation cannot replace the board mid-search.
    @Synchronized
    fun searchPosition(fen: String, depth: Int, timeMs: Int): String {
        nativeSetPosition(fen)
        return nativeGo(depth, timeMs)
    }

    @Synchronized
    fun evaluate(): Int = nativeEvaluate()

    @Synchronized
    fun evaluateFen(fen: String): Int = nativeEvaluateFen(fen)

    @Synchronized
    fun reset() = nativeReset()
}
