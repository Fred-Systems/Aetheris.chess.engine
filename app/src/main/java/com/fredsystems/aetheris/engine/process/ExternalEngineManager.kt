package com.fredsystems.aetheris.engine.process

import android.content.Context
import android.net.Uri
import java.io.File

class ExternalEngineManager(private val context: Context) {
    val dir = File(context.filesDir, "engines").apply { mkdirs() }

    fun import(uri: Uri): File? {
        return try {
            val name = "engine_${System.currentTimeMillis()}"
            val out = File(dir, name)
            context.contentResolver.openInputStream(uri)?.use { input ->
                out.outputStream().use { output -> input.copyTo(output) }
            }
            out.setExecutable(true)
            out
        } catch (e: Exception) {
            null
        }
    }

    fun list(): List<File> = dir.listFiles()?.toList() ?: emptyList()

    fun remove(file: File) { file.delete() }
}