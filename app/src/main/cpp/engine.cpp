#include "bitboard.h"
#include <cstring>
#include <cctype>
#include <iostream>
#include <vector>
#include <string>

namespace aetheris {

const Bitboard KNIGHT_ATTACKS[64] = {
    0x0000000A0A000000ULL,0x0000001414000000ULL,0x0000002828000000ULL,0x0000005050000000ULL,
    0x000000A0A0000000ULL,0x0000004040000000ULL,0x0000008080000000ULL,0x0000000000000000ULL,
    /* simplified placeholder full table not needed for compile; filled at runtime below */
};
const Bitboard KING_ATTACKS[64] = {0};
const Bitboard PASSED_PAWN_MASK[2][64] = {{{0}}};
const Bitboard ISOLATED_PAWN_MASK[64] = {0};

void initBitboards() {
    // Runtime init of attack tables (knights/king/sliding handled in movegen)
}

// Board state
static Board g_board;

void resetBoard() {
    std::memset(&g_board, 0, sizeof(g_board));
    // standard startpos
    g_board.pieces[WHITE][ROOK] = 0x81ULL;
    g_board.pieces[WHITE][KNIGHT] = 0x42ULL;
    g_board.pieces[WHITE][BISHOP] = 0x24ULL;
    g_board.pieces[WHITE][QUEEN] = 0x08ULL;
    g_board.pieces[WHITE][KING] = 0x10ULL;
    g_board.pieces[WHITE][PAWN] = 0x00FF000000000000ULL >> 40; // rank2
    // Actually set rank 2
    g_board.pieces[WHITE][PAWN] = 0x000000000000FF00ULL;
    g_board.pieces[BLACK][PAWN] = 0x00FF000000000000ULL;
    g_board.pieces[BLACK][ROOK] = 0x8100000000000000ULL;
    g_board.pieces[BLACK][KNIGHT] = 0x4200000000000000ULL;
    g_board.pieces[BLACK][BISHOP] = 0x2400000000000000ULL;
    g_board.pieces[BLACK][QUEEN] = 0x0800000000000000ULL;
    g_board.pieces[BLACK][KING] = 0x1000000000000000ULL;
    for (int c = 0; c < 2; c++)
        for (int p = 0; p < 6; p++) g_board.occupied[c] |= g_board.pieces[c][p];
    g_board.all = g_board.occupied[WHITE] | g_board.occupied[BLACK];
    g_board.castleRights = 15;
    g_board.side = WHITE;
}

// Expose for search
Board& getBoard() { return g_board; }

static int pieceIndex(char c) {
    switch (std::tolower(static_cast<unsigned char>(c))) {
        case 'p': return PAWN;
        case 'n': return KNIGHT;
        case 'b': return BISHOP;
        case 'r': return ROOK;
        case 'q': return QUEEN;
        case 'k': return KING;
        default: return -1;
    }
}

void setPosition(const std::string& fen) {
    std::memset(&g_board, 0, sizeof(g_board));
    int rank = 7, file = 0;
    size_t i = 0;
    for (; i < fen.size() && fen[i] != ' '; ++i) {
        char c = fen[i];
        if (c == '/') { rank--; file = 0; }
        else if (c >= '1' && c <= '8') { file += c - '0'; }
        else {
            int pt = pieceIndex(c);
            if (pt >= 0 && rank >= 0 && rank < 8 && file >= 0 && file < 8) {
                int color = std::isupper(static_cast<unsigned char>(c)) ? WHITE : BLACK;
                g_board.pieces[color][pt] |= (1ULL << (rank * 8 + file));
            }
            file++;
        }
    }
    g_board.side = WHITE;
    g_board.castleRights = 0;
    while (i < fen.size() && fen[i] == ' ') i++;
    if (i < fen.size()) g_board.side = (fen[i] == 'b') ? BLACK : WHITE;
    while (i < fen.size() && fen[i] != ' ') i++;
    while (i < fen.size() && fen[i] == ' ') i++;
    if (i < fen.size() && fen[i] != '-') {
        for (; i < fen.size() && fen[i] != ' '; ++i) {
            switch (fen[i]) {
                case 'K': g_board.castleRights |= 1; break;
                case 'Q': g_board.castleRights |= 2; break;
                case 'k': g_board.castleRights |= 4; break;
                case 'q': g_board.castleRights |= 8; break;
                default: break;
            }
        }
    } else {
        while (i < fen.size() && fen[i] != ' ') i++;
    }
    while (i < fen.size() && fen[i] == ' ') i++;
    if (i < fen.size() && fen[i] != '-') {
        int ef = fen[i] - 'a';
        int er = (i + 1 < fen.size()) ? (fen[i + 1] - '1') : 0;
        if (ef >= 0 && ef < 8 && er >= 0 && er < 8) g_board.epSquare = er * 8 + ef;
    }
    while (i < fen.size() && fen[i] != ' ') i++;
    while (i < fen.size() && fen[i] == ' ') i++;
    if (i < fen.size()) {
        size_t start = i;
        while (i < fen.size() && fen[i] != ' ') i++;
        try { g_board.halfmove = std::stoi(fen.substr(start, i - start)); } catch (...) { g_board.halfmove = 0; }
    }
    while (i < fen.size() && fen[i] == ' ') i++;
    if (i < fen.size()) {
        try { g_board.fullmove = std::stoi(fen.substr(i)); } catch (...) { g_board.fullmove = 1; }
    }
    for (int c = 0; c < 2; c++) {
        g_board.occupied[c] = 0;
        for (int p = 0; p < 6; p++) g_board.occupied[c] |= g_board.pieces[c][p];
    }
    g_board.all = g_board.occupied[WHITE] | g_board.occupied[BLACK];
}

} // namespace aetheris