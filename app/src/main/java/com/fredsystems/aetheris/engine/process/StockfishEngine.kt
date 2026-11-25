package com.fredsystems.aetheris.engine.process

import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

/**
 * Installs and runs the official Stockfish Android UCI binary.
 * The release metadata is resolved at install time so the app always offers
 * the latest stable release rather than a hard-coded engine version.
 */
class StockfishEngine(private val context: Context) {
    private val engineRoot = File(context.filesDir, "engines/stockfish").apply { mkdirs() }
    private var process: Process? = null
    private var reader: BufferedReader? = null
    private var writer: BufferedWriter? = null
    @Volatile private var ready = false

    fun isInstalled(): Boolean = findBinary() != null
    fun installedVersion(): String = File(engineRoot, "version.txt").takeIf { it.exists() }?.readText()?.trim().orEmpty()

    suspend fun installLatest(onProgress: (String) -> Unit = {}): String = withContext(Dispatchers.IO) {
        onProgress("Checking the latest official Stockfish release…")
        val release = getJson("https://api.github.com/repos/official-stockfish/Stockfish/releases/latest")
        val tag = release.optString("tag_name").ifBlank { error("Stockfish release did not include a version tag") }
        val assets = release.optJSONArray("assets") ?: error("No Stockfish release assets were found")
        val abi = Build.SUPPORTED_ABIS.firstOrNull { it == "arm64-v8a" || it == "armeabi-v7a" }
            ?: error("This device does not have a supported ARM Android ABI")
        val wanted = if (abi == "arm64-v8a") "stockfish-android-arm64-universal.tar.gz" else "stockfish-android-armv7-neon.tar.gz"
        var downloadUrl: String? = null
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            if (asset.optString("name") == wanted) {
                downloadUrl = asset.optString("browser_download_url")
                break
            }
        }
        val source = downloadUrl ?: error("The latest Stockfish release has no $abi Android build")
        val staging = File(engineRoot, "install.tmp").apply { deleteRecursively(); mkdirs() }
        val archive = File(engineRoot, "stockfish-download.tmp")
        try {
            onProgress("Downloading Stockfish $tag for $abi…")
            val connection = (URL(source).openConnection() as HttpURLConnection).apply {
                connectTimeout = 20000
                readTimeout = 60000
                setRequestProperty("User-Agent", "Aetheris-Android")
            }
            connection.inputStream.use { input -> FileOutputStream(archive).use { output -> input.copyTo(output) } }
            connection.disconnect()
            onProgress("Installing Stockfish $tag…")
            extractTarGz(archive, staging)
            val binary = staging.walkTopDown().firstOrNull { file ->
                file.isFile && runCatching { FileInputStream(file).use { input -> input.read() == 0x7f && input.read() == 0x45 && input.read() == 0x4c && input.read() == 0x46 } }.getOrDefault(false)
            } ?: error("The Android Stockfish archive did not contain an executable")
            binary.setExecutable(true, true)
            val installed = File(engineRoot, "current")
            installed.deleteRecursively()
            if (!staging.renameTo(installed)) error("Could not finish installing Stockfish")
            val installedBinary = installed.walkTopDown().firstOrNull { it.isFile && runCatching { FileInputStream(it).use { input -> input.read() == 0x7f && input.read() == 0x45 && input.read() == 0x4c && input.read() == 0x46 } }.getOrDefault(false) }
                ?: error("Installed Stockfish executable could not be located")
            installedBinary.setExecutable(true, true)
            File(engineRoot, "binary.path").writeText(installedBinary.absolutePath)
            File(engineRoot, "version.txt").writeText(tag)
            archive.delete()
            onProgress("Stockfish $tag installed and ready")
            tag
        } catch (e: Exception) {
            archive.delete()
            staging.deleteRecursively()
            throw e
        }
    }

    suspend fun bestMove(fen: String, depth: Int, timeMs: Int): String = withContext(Dispatchers.IO) {
        val binary = findBinary() ?: error("Stockfish is not installed. Download it from Settings first.")
        if (process?.isAlive != true || !ready) start(binary)
        send("position fen $fen")
        send("go depth ${depth.coerceIn(1, 40)} movetime ${timeMs.coerceIn(100, 30000)}")
        var result: String? = null
        while (true) {
            val line = reader?.readLine() ?: error("Stockfish stopped before returning a move")
            if (line.startsWith("bestmove ")) {
                result = line.split(Regex("\\s+")).getOrNull(1)
                break
            }
        }
        result?.takeIf { it.length >= 4 && it != "(none)" } ?: error("Stockfish did not return a legal move")
    }

    private fun start(binary: File) {
        stop()
        val p = ProcessBuilder(binary.absolutePath).directory(binary.parentFile).redirectErrorStream(true).start()
        process = p
        writer = BufferedWriter(OutputStreamWriter(p.outputStream))
        reader = BufferedReader(InputStreamReader(p.inputStream))
        ready = false
        send("uci")
        awaitLine("uciok")
        send("isready")
        awaitLine("readyok")
        ready = true
    }

    private fun awaitLine(expected: String) {
        while (true) {
            val line = reader?.readLine() ?: error("Stockfish did not respond to $expected")
            if (line.trim() == expected) return
        }
    }

    private fun send(command: String) {
        val out = writer ?: error("Stockfish is not running")
        out.write(command)
        out.newLine()
        out.flush()
    }

    fun stop() {
        runCatching { writer?.write("quit\n"); writer?.flush() }
        runCatching { process?.destroy() }
        process = null
        reader = null
        writer = null
        ready = false
    }

    private fun findBinary(): File? {
        val pathFile = File(engineRoot, "binary.path")
        val saved = pathFile.takeIf { it.exists() }?.readText()?.trim()?.let(::File)
        if (saved?.isFile == true) return saved
        return File(engineRoot, "current").walkTopDown().firstOrNull { it.isFile && runCatching { FileInputStream(it).use { input -> input.read() == 0x7f && input.read() == 0x45 && input.read() == 0x4c && input.read() == 0x46 } }.getOrDefault(false) }
    }

    private fun getJson(address: String): JSONObject {
        val connection = (URL(address).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20000
            readTimeout = 30000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "Aetheris-Android")
        }
        return try {
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            JSONObject(body)
        } finally { connection.disconnect() }
    }

    private fun extractTarGz(archive: File, destination: File) {
        GZIPInputStream(FileInputStream(archive)).use { input ->
            val header = ByteArray(512)
            while (readBlock(input, header)) {
                if (header.all { it == 0.toByte() }) break
                val name = field(header, 0, 100)
                val prefix = field(header, 345, 155)
                val relative = listOf(prefix, name).filter { it.isNotBlank() }.joinToString("/")
                val sizeText = field(header, 124, 12).trim().trim('\u0000')
                val size = sizeText.toLongOrNull(8) ?: 0L
                val type = header[156].toInt().toChar()
                val safe = relative.replace('\\', '/').split('/').filter { it.isNotBlank() && it != "." && it != ".." }.joinToString("/")
                val target = File(destination, safe)
                if ((type == '\u0000' || type == '0') && safe.isNotBlank() && size in 1..150_000_000L) {
                    target.parentFile?.mkdirs()
                    FileOutputStream(target).use { output -> copyExactly(input, output, size) }
                } else {
                    skipExactly(input, size)
                }
                val padding = (512 - (size % 512)) % 512
                skipExactly(input, padding)
            }
        }
    }

    private fun field(bytes: ByteArray, offset: Int, length: Int): String =
        bytes.copyOfRange(offset, offset + length).takeWhile { it != 0.toByte() }.toByteArray().toString(Charsets.UTF_8).trim()

    private fun readBlock(input: InputStream, block: ByteArray): Boolean {
        var offset = 0
        while (offset < block.size) {
            val count = input.read(block, offset, block.size - offset)
            if (count < 0) return offset == 0
            offset += count
        }
        return true
    }

    private fun copyExactly(input: InputStream, output: FileOutputStream, count: Long) {
        var remaining = count
        val buffer = ByteArray(64 * 1024)
        while (remaining > 0) {
            val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (read < 0) error("Unexpected end of Stockfish archive")
            output.write(buffer, 0, read)
            remaining -= read
        }
    }

    private fun skipExactly(input: InputStream, count: Long) {
        var remaining = count
        while (remaining > 0) {
            val skipped = input.skip(remaining)
            if (skipped > 0) remaining -= skipped
            else if (input.read() < 0) error("Unexpected end of Stockfish archive")
            else remaining--
        }
    }
}
