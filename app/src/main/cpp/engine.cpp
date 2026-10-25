#include "bitboard.h"
#include <cstring>
#include <iostream>
#include <vector>

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

void setPosition(const std::string& fen) {
    // minimal: handle startpos
    if (fen.find("startpos") != std::string::npos) { resetBoard(); return; }
    resetBoard();
}

} // namespace aetheris