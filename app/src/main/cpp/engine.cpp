#include "bitboard.h"
#include <cstring>
#include <cctype>
#include <iostream>
#include <vector>
#include <string>
#include <cstdlib>

namespace aetheris {

const Bitboard KNIGHT_ATTACKS[64] = {
    0x0000000A0A000000ULL,0x0000001414000000ULL,0x0000002828000000ULL,0x0000005050000000ULL,
    0x000000A0A0000000ULL,0x0000004040000000ULL,0x0000008080000000ULL,0x0000000000000000ULL,
};
const Bitboard KING_ATTACKS[64] = {0};
const Bitboard PASSED_PAWN_MASK[2][64] = {{{0}}};
const Bitboard ISOLATED_PAWN_MASK[64] = {0};

void initBitboards() {
}

static Board g_board;

void resetBoard() {
    std::memset(&g_board, 0, sizeof(g_board));
    g_board.pieces[WHITE][ROOK] = 0x81ULL;
    g_board.pieces[WHITE][KNIGHT] = 0x42ULL;
    g_board.pieces[WHITE][BISHOP] = 0x24ULL;
    g_board.pieces[WHITE][QUEEN] = 0x08ULL;
    g_board.pieces[WHITE][KING] = 0x10ULL;
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

static int parseIntOr(const std::string& text, int fallback) {
    if (text.empty()) return fallback;
    char* end = nullptr;
    long value = std::strtol(text.c_str(), &end, 10);
    if (end == text.c_str()) return fallback;
    return static_cast<int>(value);
}

void setPosition(const std::string& fen) {
    std::memset(&g_board, 0, sizeof(g_board));
    size_t first = fen.find_first_not_of(" \t\r\n");
    if (first == std::string::npos) return;
    std::string f = fen.substr(first);
    int rank = 7, file = 0;
    size_t i = 0;
    for (; i < f.size() && f[i] != ' '; ++i) {
        char c = f[i];
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
    while (i < f.size() && f[i] == ' ') i++;
    if (i < f.size()) g_board.side = (f[i] == 'b') ? BLACK : WHITE;
    while (i < f.size() && f[i] != ' ') i++;
    while (i < f.size() && f[i] == ' ') i++;
    if (i < f.size() && f[i] != '-') {
        for (; i < f.size() && f[i] != ' '; ++i) {
            switch (f[i]) {
                case 'K': g_board.castleRights |= 1; break;
                case 'Q': g_board.castleRights |= 2; break;
                case 'k': g_board.castleRights |= 4; break;
                case 'q': g_board.castleRights |= 8; break;
                default: break;
            }
        }
    } else {
        while (i < f.size() && f[i] != ' ') i++;
    }
    while (i < f.size() && f[i] == ' ') i++;
    if (i < f.size() && f[i] != '-') {
        int ef = f[i] - 'a';
        int er = (i + 1 < f.size()) ? (f[i + 1] - '1') : 0;
        if (ef >= 0 && ef < 8 && er >= 0 && er < 8) g_board.epSquare = er * 8 + ef;
    }
    while (i < f.size() && f[i] != ' ') i++;
    while (i < f.size() && f[i] == ' ') i++;
    if (i < f.size()) {
        size_t start2 = i;
        while (i < f.size() && f[i] != ' ') i++;
        g_board.halfmove = parseIntOr(f.substr(start2, i - start2), 0);
    }
    while (i < f.size() && f[i] == ' ') i++;
    if (i < f.size()) {
        g_board.fullmove = parseIntOr(f.substr(i), 1);
    }
    for (int c = 0; c < 2; c++) {
        g_board.occupied[c] = 0;
        for (int p = 0; p < 6; p++) g_board.occupied[c] |= g_board.pieces[c][p];
    }
    g_board.all = g_board.occupied[WHITE] | g_board.occupied[BLACK];
}

} // namespace aetheris
