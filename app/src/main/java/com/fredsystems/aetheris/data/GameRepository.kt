package com.fredsystems.aetheris.data

import android.content.Context
import com.fredsystems.aetheris.domain.model.GameRecord
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class GameRepository(private val context: Context) {
    private val file = File(context.filesDir, "games.json")

    fun load(): MutableList<GameRecord> {
        if (!file.exists()) return mutableListOf()
        val arr = JSONArray(file.readText())
        val out = mutableListOf<GameRecord>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val movesArr = o.optJSONArray("moves") ?: JSONArray()
            val moves = (0 until movesArr.length()).map { movesArr.getString(it) }
            val commentsObj = o.optJSONObject("comments") ?: JSONObject()
            val comments = mutableMapOf<Int, String>()
            commentsObj.keys().forEach { k -> comments[k.toInt()] = commentsObj.getString(k) }
            val glyphsObj = o.optJSONObject("glyphs") ?: JSONObject()
            val glyphs = mutableMapOf<Int, String>()
            glyphsObj.keys().forEach { k -> glyphs[k.toInt()] = glyphsObj.getString(k) }
            val arrowsObj = o.optJSONObject("arrows") ?: JSONObject()
            val arrows = mutableMapOf<Int, List<String>>()
            arrowsObj.keys().forEach { k ->
                val a = arrowsObj.getJSONArray(k)
                arrows[k.toInt()] = (0 until a.length()).map { a.getString(it) }
            }
            out.add(GameRecord(o.getLong("id"), o.getString("title"), o.getString("pgn"), moves, comments, glyphs, arrows))
        }
        return out
    }

    fun save(records: List<GameRecord>) {
        val arr = JSONArray()
        records.forEach { r ->
            val o = JSONObject()
            o.put("id", r.id)
            o.put("title", r.title)
            o.put("pgn", r.pgn)
            val ma = JSONArray()
            r.moves.forEach { ma.put(it) }
            o.put("moves", ma)
            val co = JSONObject()
            r.comments.forEach { (k, v) -> co.put(k.toString(), v) }
            o.put("comments", co)
            val go = JSONObject()
            r.glyphs.forEach { (k, v) -> go.put(k.toString(), v) }
            o.put("glyphs", go)
            val ao = JSONObject()
            r.arrows.forEach { (k, v) ->
                val a = JSONArray()
                v.forEach { a.put(it) }
                ao.put(k.toString(), a)
            }
            o.put("arrows", ao)
            arr.put(o)
        }
        file.writeText(arr.toString())
    }
}