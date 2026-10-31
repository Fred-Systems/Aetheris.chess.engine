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
            header.split('\t').let { it.size >= 3 && it[0].equals("eco", true) && it[2].equals("pgn", true) } &&
                reader.lineSequence().take(20).any { it.split('\t').size >= 3 && it.split('\t')[2].isNotBlank() }
        }
        if (!validTsv) {
            temp.delete()
            throw IllegalArgumentException("Import a Lichess openings TSV file with eco, name and pgn columns")
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
            lines.forEach { line ->
                val cols = line.split('\t')
                if (cols.firstOrNull()?.equals("eco", true) == true) return@forEach
                val pgn = cols.getOrNull(2)?.trim().orEmpty()
                if (pgn.isBlank()) return@forEach
                val moves = runCatching { MoveList().apply { loadFromSan(pgn) } }.getOrNull() ?: return@forEach
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

        // "random" samples from established, well-supported continuations rather
        // than selecting arbitrary moves. Keep the choice within the stronger
        // half of the book so variety does not mean random blunders.
        val candidates = when (policy.lowercase()) {
            "unorthodox" -> weighted.sortedBy { it.second }.take((weighted.size / 2).coerceAtLeast(1))
            "aggressive" -> {
                val position = runCatching { Board().apply { loadFromFen(fen) } }.getOrNull()
                val scored = weighted.map { entry ->
                    val move = runCatching {
                        val from = com.github.bhlangonijr.chesslib.Square.valueOf(entry.first.take(2).uppercase())
                        val to = com.github.bhlangonijr.chesslib.Square.valueOf(entry.first.substring(2, 4).uppercase())
                        position?.legalMoves()?.firstOrNull { it.from == from && it.to == to }
                    }.getOrNull()
                    val score = if (position == null || move == null) 0 else {
                        val capture = position.getPiece(move.to) != com.github.bhlangonijr.chesslib.Piece.NONE
                        val mover = position.getPiece(move.from)
                        val centerAdvance = kotlin.math.abs(3.5 - move.to.file.ordinal) + kotlin.math.abs(3.5 - move.to.rank.ordinal)
                        (if (capture) 5 else 0) +
                            (if (mover.pieceType == com.github.bhlangonijr.chesslib.PieceType.PAWN) 2 else 0) +
                            (if (centerAdvance < 3.0) 2 else 0)
                    }
                    Triple(entry, score, entry.second)
                }
                val bestScore = scored.maxOfOrNull { it.second } ?: 0
                scored.filter { it.second >= bestScore - 1 }.map { it.first }
                    .ifEmpty { weighted.take((weighted.size / 2).coerceAtLeast(1)) }
            }
            else -> weighted.sortedByDescending { it.second }.take((weighted.size / 2).coerceAtLeast(1))
        }
        val total = candidates.sumOf { it.second.coerceAtLeast(1) }
        var pick = Random.nextInt(total)
        for ((move, count) in candidates) {
            pick -= count.coerceAtLeast(1)
            if (pick < 0) return move
        }
        return candidates.first().first
    }

    private fun positionKey(fen: String): String = fen.split(' ').take(4).joinToString(" ")
}
