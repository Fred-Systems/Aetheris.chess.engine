package com.fredsystems.aetheris.engine.process

import android.content.Context
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter

class EngineProcess(private val context: Context) {
    private var process: Process? = null
    private var writer: BufferedWriter? = null
    private var reader: BufferedReader? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun start() {
        if (process != null) return
        val nativeDir = context.applicationInfo.nativeLibraryDir
        val bin = "$nativeDir/libaetheris_engine.so"
        process = ProcessBuilder(bin).redirectErrorStream(true).start()
        writer = BufferedWriter(OutputStreamWriter(process!!.outputStream))
        reader = BufferedReader(InputStreamReader(process!!.inputStream))
        send("uci")
    }

    fun send(cmd: String) {
        writer?.apply { write(cmd + "\n"); flush() }
    }

    fun readLine(): String? = reader?.readLine()

    fun stop() {
        send("quit")
        process?.destroy()
        process = null
    }

    fun isAlive(): Boolean = process?.isAlive == true
}