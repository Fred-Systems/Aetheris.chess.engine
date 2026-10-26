package com.fredsystems.aetheris.ui.theme

import androidx.compose.ui.graphics.Color

data class BoardTheme(
    val id: String,
    val label: String,
    val light: Color,
    val dark: Color
)

object BoardThemes {
    val all = listOf(
        BoardTheme("brown", "Brown", Color(0xFFF0D9B5), Color(0xFFB58863)),
        BoardTheme("blue", "Blue", Color(0xFFDEE3E6), Color(0xFF8CA2AD)),
        BoardTheme("green", "Green", Color(0xFFEBECD0), Color(0xFF739552)),
        BoardTheme("purple", "Purple", Color(0xFFE8E1F0), Color(0xFF9E8DB5)),
        BoardTheme("pink", "Pink", Color(0xFFF4E1E8), Color(0xFFC48C9F)),
        BoardTheme("grey", "Grey", Color(0xFFE8E8E8), Color(0xFFA0A0A0)),
        BoardTheme("wood", "Wood", Color(0xFFD9B382), Color(0xFF8A5A2B)),
        BoardTheme("marble", "Marble", Color(0xFFE6E3DC), Color(0xFFAFA79B)),
        BoardTheme("olive", "Olive", Color(0xFFDDD6B8), Color(0xFF8C8A5A)),
        BoardTheme("leather", "Leather", Color(0xFFC8A27A), Color(0xFF7A4E2D)),
        BoardTheme("maple", "Maple", Color(0xFFF4E3C8), Color(0xFFC6A57A)),
        BoardTheme("metal", "Metal", Color(0xFFD6D8DC), Color(0xFF8B9098)),
        BoardTheme("newspaper", "Newspaper", Color(0xFFF5F2E8), Color(0xFFB8B2A3)),
        BoardTheme("tan", "Tan", Color(0xFFF2D7B6), Color(0xFFB98B5E)),
        BoardTheme("walnut", "Walnut", Color(0xFFE0C9A6), Color(0xFF8C6239)),
        BoardTheme("ice", "Ice", Color(0xFFE4EEF5), Color(0xFF8FB3C7)),
        BoardTheme("midnight", "Midnight", Color(0xFF6E7B8B), Color(0xFF38495A)),
        BoardTheme("coral", "Coral", Color(0xFFF7D9CF), Color(0xFFC97460)),
        BoardTheme("mint", "Mint", Color(0xFFDDF0E3), Color(0xFF6FA98A)),
        BoardTheme("sand", "Sand", Color(0xFFEEE2C8), Color(0xFFC2A878)),
        BoardTheme("slate", "Slate", Color(0xFFDCE1E6), Color(0xFF6F7C8C)),
        BoardTheme("royal", "Royal", Color(0xFFD8D3F0), Color(0xFF6C5CA8)),
        BoardTheme("coffee", "Coffee", Color(0xFFE7D7C9), Color(0xFF8B6B5A)),
        BoardTheme("forest", "Forest", Color(0xFFD9E8CF), Color(0xFF5F8A5B)),
        BoardTheme("ruby", "Ruby", Color(0xFFEED6D6), Color(0xFFA54C4C))
    )

    fun byId(id: String): BoardTheme = all.firstOrNull { it.id == id } ?: all.first()
}
