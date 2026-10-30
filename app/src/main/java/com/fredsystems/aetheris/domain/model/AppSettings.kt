package com.fredsystems.aetheris.domain.model

import android.content.Context

data class TimeControl(val id: String, val label: String, val seconds: Int) {
    companion object {
        val all = listOf(
            TimeControl("unlimited", "Unlimited", 0),
            TimeControl("1", "1 min", 60),
            TimeControl("3", "3 min", 180),
            TimeControl("5", "5 min", 300),
            TimeControl("10", "10 min", 600),
            TimeControl("15", "15 min", 900),
            TimeControl("30", "30 min", 1800)
        )
        fun byId(id: String) = all.firstOrNull { it.id == id } ?: all.first()
    }
}

data class AppSettings(
    val pieceSet: String = "cburnett",
    val boardTheme: String = "brown",
    val uiScale: Float = 1.0f,
    val boardScale: Float = 1.8f,
    val showEvalPlay: Boolean = false,
    val showEvalAnalysis: Boolean = true,
    val flipped: Boolean = false,
    val playerColor: String = "white",
    val timeControl: String = "unlimited",
    val moveTimeSeconds: Int = 0,
    val analysisAutoplaySeconds: Int = 2,
    val analysisLineCount: Int = 3,
    val showBestMoveArrow: Boolean = true,
    val showEngineThinkingArrows: Boolean = false,
    val showPgnPanel: Boolean = true,
    val pondering: Boolean = false,
    val moveSoundsEnabled: Boolean = true,
    val moveSoundVolume: Int = 70,
    val blindfoldMode: Boolean = false,
    val premoveEnabled: Boolean = true,
    val multiplePremoves: Boolean = false,
    val conditionalMoves: Boolean = false,
    val chess960Enabled: Boolean = false,
    val selectedEngine: String = "aetheris",
    val openingBookEnabled: Boolean = false,
    val openingBookPolicy: String = "best",
    val showOpeningBookArrows: Boolean = false
)

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("aetheris_settings", Context.MODE_PRIVATE)

    fun load(): AppSettings = AppSettings(
        pieceSet = prefs.getString("pieceSet", "cburnett") ?: "cburnett",
        boardTheme = prefs.getString("boardTheme", "brown") ?: "brown",
        uiScale = prefs.getFloat("uiScale", 1.0f),
        boardScale = prefs.getFloat("boardScale", 1.8f),
        showEvalPlay = prefs.getBoolean("showEvalPlay", false),
        showEvalAnalysis = prefs.getBoolean("showEvalAnalysis", true),
        flipped = prefs.getBoolean("flipped", false),
        playerColor = prefs.getString("playerColor", "white") ?: "white",
        timeControl = prefs.getString("timeControl", "unlimited") ?: "unlimited",
        moveTimeSeconds = prefs.getInt("moveTimeSeconds", 0),
        analysisAutoplaySeconds = prefs.getInt("analysisAutoplaySeconds", 2).coerceIn(1, 15),
        analysisLineCount = prefs.getInt("analysisLineCount", 3).coerceIn(1, 5),
        showBestMoveArrow = prefs.getBoolean("showBestMoveArrow", true),
        showEngineThinkingArrows = prefs.getBoolean("showEngineThinkingArrows", false),
        showPgnPanel = prefs.getBoolean("showPgnPanel", true),
        pondering = prefs.getBoolean("pondering", false),
        moveSoundsEnabled = prefs.getBoolean("moveSoundsEnabled", true),
        moveSoundVolume = prefs.getInt("moveSoundVolume", 70).coerceIn(0, 100),
        blindfoldMode = prefs.getBoolean("blindfoldMode", false),
        premoveEnabled = prefs.getBoolean("premoveEnabled", true),
        multiplePremoves = prefs.getBoolean("multiplePremoves", false),
        conditionalMoves = prefs.getBoolean("conditionalMoves", false),
        chess960Enabled = prefs.getBoolean("chess960Enabled", false),
        selectedEngine = prefs.getString("selectedEngine", "aetheris") ?: "aetheris",
        openingBookEnabled = prefs.getBoolean("openingBookEnabled", false),
        openingBookPolicy = prefs.getString("openingBookPolicy", "best") ?: "best",
        showOpeningBookArrows = prefs.getBoolean("showOpeningBookArrows", false)
    )

    fun save(s: AppSettings) {
        prefs.edit()
            .putString("pieceSet", s.pieceSet)
            .putString("boardTheme", s.boardTheme)
            .putFloat("uiScale", s.uiScale)
            .putFloat("boardScale", s.boardScale)
            .putBoolean("showEvalPlay", s.showEvalPlay)
            .putBoolean("showEvalAnalysis", s.showEvalAnalysis)
            .putBoolean("flipped", s.flipped)
            .putString("playerColor", s.playerColor)
            .putString("timeControl", s.timeControl)
            .putInt("moveTimeSeconds", s.moveTimeSeconds)
            .putInt("analysisAutoplaySeconds", s.analysisAutoplaySeconds.coerceIn(1, 15))
            .putInt("analysisLineCount", s.analysisLineCount.coerceIn(1, 5))
            .putBoolean("showBestMoveArrow", s.showBestMoveArrow)
            .putBoolean("showEngineThinkingArrows", s.showEngineThinkingArrows)
            .putBoolean("showPgnPanel", s.showPgnPanel)
            .putBoolean("pondering", s.pondering)
            .putBoolean("moveSoundsEnabled", s.moveSoundsEnabled)
            .putInt("moveSoundVolume", s.moveSoundVolume.coerceIn(0, 100))
            .putBoolean("blindfoldMode", s.blindfoldMode)
            .putBoolean("premoveEnabled", s.premoveEnabled)
            .putBoolean("multiplePremoves", s.multiplePremoves)
            .putBoolean("conditionalMoves", s.conditionalMoves)
            .putBoolean("chess960Enabled", s.chess960Enabled)
            .putString("selectedEngine", s.selectedEngine)
            .putBoolean("openingBookEnabled", s.openingBookEnabled)
            .putString("openingBookPolicy", s.openingBookPolicy)
            .putBoolean("showOpeningBookArrows", s.showOpeningBookArrows)
            .apply()
    }
}
