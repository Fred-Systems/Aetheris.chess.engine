#pragma once
#include <cstdint>
#include <array>
#include <string>

namespace aetheris {

using Bitboard = std::uint64_t;
using Square = int;
using Move = std::uint32_t;

constexpr int SQ_NONE = -1;
constexpr int A1 = 0, B1 = 1, C1 = 2, D1 = 3, E1 = 4, F1 = 5, G1 = 6, H1 = 7;
constexpr int A8 = 56;

enum Piece { PAWN, KNIGHT, BISHOP, ROOK, QUEEN, KING, NONE_PIECE };
enum Color { WHITE, BLACK, NO_COLOR };

struct MoveStruct {
    Square from, to;
    Piece promotion;
    bool isCapture;
    bool isCastle;
    bool isEnPassant;
};

struct Board {
    Bitboard pieces[2][6]{};
    Bitboard occupied[2]{};
    Bitboard all{};
    int side = 0;
    int epSquare = -1;
    int castleRights = 0;
    int halfmove = 0;
    int fullmove = 1;
    std::uint64_t hash = 0;
};

Board& getBoard();
void resetBoard();
void setPosition(const std::string& fen);
int evaluate(const Board& b);
void setAggression(int a);
void setPositional(int v);
void setComplexity(int v);
void setSolidity(int v);
void setMaterialWeight(int m);

enum EngineMode { ENGINE_DUAL_CORE = 0, ENGINE_PURE_NNUE = 1, ENGINE_PURE_CLASSICAL = 2 };
extern int engineMode;
void setEngineMode(int m);
void setNNUEPath(const std::string& p);
void setNNUECalibrationValid(bool valid);

inline Square sq(int file, int rank) { return rank * 8 + file; }
inline int fileOf(Square s) { return s & 7; }
inline int rankOf(Square s) { return s >> 3; }

constexpr Bitboard FILE_A = 0x0101010101010101ULL;
constexpr Bitboard RANK_1 = 0x00000000000000FFULL;

extern const Bitboard KNIGHT_ATTACKS[64];
extern const Bitboard KING_ATTACKS[64];
extern const Bitboard PASSED_PAWN_MASK[2][64];
extern const Bitboard ISOLATED_PAWN_MASK[64];

void initBitboards();

} // namespace aetheris