package com.fredsystems.aetheris.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * Uses Lichess's hosted Standard sound files without bundling their non-free audio
 * assets in the APK. The files are cached privately after first download.
 */
class LichessSoundPlayer(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()
    private val soundIds = ConcurrentHashMap<String, Int>()
    private val loadedIds = ConcurrentHashMap.newKeySet<Int>()
    private val cacheDir = File(appContext.cacheDir, "lichess-standard-sounds")

    private val files = mapOf(
        "move" to "Move.mp3",
        "capture" to "Capture.mp3"
    )

    init {
        pool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) loadedIds.add(sampleId)
            else Log.w("AetherisAudio", "Could not load Lichess sound sample=$sampleId status=$status")
        }
        files.forEach { (event, filename) ->
            scope.launch {
                runCatching {
                    cacheDir.mkdirs()
                    val file = File(cacheDir, filename)
                    if (!file.isFile || file.length() < 256L) {
                        val connection = URL("https://lichess1.org/assets/sound/standard/$filename")
                            .openConnection() as HttpURLConnection
                        try {
                            connection.connectTimeout = 8000
                            connection.readTimeout = 8000
                            connection.instanceFollowRedirects = true
                            connection.setRequestProperty("User-Agent", "Aetheris-Android-Chess/1.0")
                            connection.connect()
                            if (connection.responseCode !in 200..299) {
                                error("Lichess sound download returned HTTP ${connection.responseCode}")
                            }
                            val temp = File(cacheDir, "$filename.part")
                            connection.inputStream.use { input ->
                                temp.outputStream().use { output -> input.copyTo(output) }
                            }
                            if (temp.length() < 256L) error("Lichess sound file was empty or invalid")
                            if (file.exists()) file.delete()
                            if (!temp.renameTo(file)) error("Could not cache Lichess sound")
                        } finally {
                            connection.disconnect()
                        }
                    }
                    val sampleId = pool.load(file.absolutePath, 1)
                    if (sampleId != 0) soundIds[event] = sampleId
                    else Log.w("AetherisAudio", "SoundPool rejected $filename")
                }.onFailure {
                    Log.w("AetherisAudio", "Lichess Standard sound '$event' is unavailable until internet access works", it)
                }
            }
        }
    }

    fun play(event: String, volumePercent: Int) {
        val id = soundIds[event] ?: (if (event == "check") soundIds["move"] else null) ?: return
        if (!loadedIds.contains(id)) return
        val volume = volumePercent.coerceIn(0, 100) / 100f
        if (volume <= 0f) return
        pool.play(id, volume, volume, 1, 0, 1f)
    }

    fun release() {
        scope.cancel()
        pool.release()
    }
}
