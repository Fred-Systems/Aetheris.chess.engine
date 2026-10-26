package com.fredsystems.aetheris.ui.theme

data class PieceSet(val id: String, val label: String)

object PieceSets {
    val all = listOf(
        PieceSet("cburnett", "Cburnett"),
        PieceSet("alpha", "Alpha"),
        PieceSet("anarcandy", "Anarcandy"),
        PieceSet("caliente", "Caliente"),
        PieceSet("california", "California"),
        PieceSet("cardinal", "Cardinal"),
        PieceSet("celtic", "Celtic"),
        PieceSet("chess7", "Chess 7"),
        PieceSet("chessnut", "Chessnut"),
        PieceSet("companion", "Companion"),
        PieceSet("cooke", "Cooke"),
        PieceSet("disguised", "Disguised"),
        PieceSet("dubrovny", "Dubrovny"),
        PieceSet("fantasy", "Fantasy"),
        PieceSet("firi", "Firi"),
        PieceSet("fresca", "Fresca"),
        PieceSet("gioco", "Gioco"),
        PieceSet("governor", "Governor"),
        PieceSet("horsey", "Horsey"),
        PieceSet("icpieces", "IC Pieces"),
        PieceSet("kiwen-suwi", "Kiwen Suwi"),
        PieceSet("kosal", "Kosal"),
        PieceSet("leipzig", "Leipzig"),
        PieceSet("letter", "Letter"),
        PieceSet("maestro", "Maestro"),
        PieceSet("merida", "Merida"),
        PieceSet("minimal-warmth", "Minimal Warmth"),
        PieceSet("monarchy", "Monarchy"),
        PieceSet("mpchess", "MP Chess"),
        PieceSet("papercut", "Papercut"),
        PieceSet("pirouetti", "Pirouetti"),
        PieceSet("pixel", "Pixel"),
        PieceSet("reillycraig", "Reilly Craig"),
        PieceSet("rhosgfx", "Rhosgfx"),
        PieceSet("riohacha", "Riohacha"),
        PieceSet("shahi-ivory-brown", "Shahi Ivory Brown"),
        PieceSet("shapes", "Shapes"),
        PieceSet("spatial", "Spatial"),
        PieceSet("staunty", "Staunty"),
        PieceSet("tatiana", "Tatiana"),
        PieceSet("totoy", "Totoy"),
        PieceSet("xkcd", "xkcd")
    )

    fun byId(id: String): PieceSet = all.firstOrNull { it.id == id } ?: all.first()
}
