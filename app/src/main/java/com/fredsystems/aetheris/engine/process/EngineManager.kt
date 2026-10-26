package com.fredsystems.aetheris.engine.process

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.BufferedReader

class EngineManager(private val engine: EngineProcess) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    val bestMove = MutableStateFlow<String?>(null)
    private val _score = MutableStateFlow(0)
    val score: StateFlow<Int> = _score
    private val _info = MutableStateFlow("")
    val info: StateFlow<String> = _info

    fun init() {
        try { engine.start() } catch (e: Exception) { }
        scope.launch {
            var reader: BufferedReader? = null
            // obtain reader via reflection? Use engine readLine loop
            while (engine.isAlive()) {
                val line = try { engine.readLine() } catch (e: Exception) { null } ?: break
                try { parse(line) } catch (e: Exception) { }
            }
        }
    }

    private fun parse(line: String) {
        when {
            line.startsWith("bestmove") -> {
                val parts = line.split(" ")
                bestMove.value = parts.getOrElse(1) { "" }
            }
            line.startsWith("info") -> {
                _info.value = line
                val idx = line.indexOf("score cp")
                if (idx >= 0) {
                    val v = line.substring(idx + 8).trim().split(" ").firstOrNull()?.toIntOrNull()
                    if (v != null) _score.value = v
                }
                val midx = line.indexOf("score mate")
                if (midx >= 0) {
                    val v = line.substring(midx + 10).trim().split(" ").firstOrNull()?.toIntOrNull()
                    if (v != null) _score.value = if (v > 0) 100000 else -100000
                }
            }
        }
    }

    fun setPosition(fen: String) = engine.send("position fen $fen")
    fun go(depth: Int, timeMs: Int = 0) = engine.send("go depth $depth movetime $timeMs")
    fun setOption(name: String, value: String) = engine.send("setoption name $name value $value")
    fun isReady() = engine.send("isready")
    fun newGame() = engine.send("ucinewgame")
    fun stop() = engine.stop()
}