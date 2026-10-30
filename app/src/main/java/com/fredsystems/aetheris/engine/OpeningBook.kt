package com.fredsystems.aetheris.engine

import android.content.Context
import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.move.MoveList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.random.Random

/** Lichess public opening database adapter. */
class OpeningBook(private val context: Context) {
    private val file = File(context.filesDir, "opening-book/lichess-openings.tsv")
    @Volatile private var indexedForLength: Long = -1
    @Volatile private var index: Map<String, List<Pair<String, Int>>> = emptyMap()

    fun installed(): Boolean = file.exists() && file.length() > 0L

    suspend fun download(onProgress: (String) -> Unit = {}) = withContext(Dispatchers.IO) {
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, "lichess-openings.tmp")
        val sources = listOf("a.tsv", "b.tsv", "c.tsv", "d.tsv", "e.tsv")
        temp.outputStream().buffered().use { out ->
            sources.forEachIndexed { i, name ->
                onProgress("Downloading opening database ${i + 1}/${sources.size}…")
                val connection = (URL("https://raw.githubusercontent.com/lichess-org/chess-openings/master/$name").openConnection() as HttpURLConnection).apply {
                    connectTimeout = 20000
                    readTimeout = 60000
                    setRequestProperty("User-Agent", "Aetheris-Android")
                }
                try { connection.inputStream.buffered().use { it.copyTo(out) } }
                finally { connection.disconnect() }
            }
        }
        if (temp.length() < 100_000L) throw IllegalStateException("Opening database download was incomplete")
        if (file.exists()) file.delete()
        if (!temp.renameTo(file)) throw IllegalStateException("Could not install opening database")
        indexedForLength = -1
        onProgress("Lichess opening database ready")
    }

    fun importFile(source: java.io.InputStream) {
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, "imported-openings.tmp")
        temp.outputStream().buffered().use { source.copyTo(it) }
        if (temp.length() == 0L) throw IllegalArgumentException("The selected book is empty")
        val validTsv = temp.bufferedReader().use { reader ->
            val header = reader.readLine().orEmpty().removePrefix("\\uFEFF")
            header.split('\\t').let { it.size >= 3 && it[0].equals("eco", true) && it[2].equals("pgn", true) } &&
                reader.lineSequence().take(20).any { it.split('\\t').size >= 3 && it.split('\\t')[2].isNotBlank() }
        }
        if (!validTsv) {
            temp.delete()
            throw IllegalArgumentException("Import a Lichess openings TSV file with eco, name, pgn and uci columns")
        }
        if (file.exists()) file.delete()
        if (!temp.renameTo(file)) throw IllegalStateException("Could not save imported book")
        indexedForLength = -1
    }

    @Synchronized private fun ensureIndex() {
        if (!installed()) { index = emptyMap(); return }
        if (indexedForLength == file.length()) return
        val counts = HashMap<String, HashMap<String, Int>>(160_000)
        file.bufferedReader().useLines { lines ->
            lines.forEachIndexed { lineNo, line ->
                val cols = line.split('\\t')
                if (cols.firstOrNull()?.equals("eco", true) == true) return@forEachIndexed
                val pgn = cols.getOrNull(2)?.trim().orEmpty()
                if (pgn.isBlank()) return@forEachIndexed
                val moves = runCatching { MoveList().apply { loadFromSan(pgn) } }.getOrNull() ?: return@forEachIndexed
                val replay = Board()
                for (move in moves) {
                    val key = positionKey(replay.fen)
                    val uci = move.toString().lowercase()
                    val bucket = counts.getOrPut(key) { HashMap() }
                    bucket[uci] = (bucket[uci] ?: 0) + 1
                    if (!replay.doMove(move)) break
                }
            }
        }
        index = counts.mapValues { (_, moves) -> moves.entries.map { it.key to it.value }.sortedByDescending { it.second } }
        indexedForLength = file.length()
    }

    fun continuations(fen: String): List<String> {
        ensureIndex()
        return index[positionKey(fen)]?.map { it.first }.orEmpty()
    }

    fun choose(fen: String, legalUci: Set<String>, policy: String): String? {
        ensureIndex()
        val weighted = index[positionKey(fen)].orEmpty().filter { it.first in legalUci }
        if (weighted.isEmpty()) return null
        if (policy == "random") {
            val total = weighted.sumOf { it.second.coerceAtLeast(1) }
            var pick = Random.nextInt(total)
            for ((move, count) in weighted) {
                pick -= count.coerceAtLeast(1)
                if (pick < 0) return move
            }
        }
        return weighted.first().first
    }

    private fun positionKey(fen: String): String = fen.split(' ').take(4).joinToString(" ")
}
