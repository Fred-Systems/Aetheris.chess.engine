package com.fredsystems.aetheris.engine

object NativeEngine {
    init {
        System.loadLibrary("aetheris")
    }

    external fun nativeInit()
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

    @Synchronized
    fun evaluate(): Int = nativeEvaluate()

    @Synchronized
    fun evaluateFen(fen: String): Int = nativeEvaluateFen(fen)

    @Synchronized
    fun reset() = nativeReset()
}
