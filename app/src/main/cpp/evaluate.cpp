#include "bitboard.h"
#include "nnue.h"
#include <algorithm>
#include <cstdlib>
#ifdef __ANDROID__
#include <android/log.h>
#else
#define ANDROID_LOG_INFO 0
#define ANDROID_LOG_ERROR 0
#define __android_log_print(...) ((void)0)
#endif
// v1.0.14 evaluation diagnostics and mode separation.

namespace aetheris {

static const int PIECE_VALUE[6] = {100, 320, 335, 500, 950, 20000};
static int aggression = 50;
static int positional = 50;
static int complexity = 50;
static int solidity = 50;
static int materialWeight = 50;
int engineMode = ENGINE_DUAL_CORE;
static bool nnueCalibrationValid = false;
void setNNUECalibrationValid(bool valid) { nnueCalibrationValid = valid; }

void setAggression(int a) { aggression = std::max(0, std::min(100, a)); }
void setPositional(int v) { positional = std::max(0, std::min(100, v)); }
void setComplexity(int v) { complexity = std::max(0, std::min(100, v)); }
void setSolidity(int v) { solidity = std::max(0, std::min(100, v)); }
void setMaterialWeight(int m) { materialWeight = std::max(0, std::min(100, m)); }
void setEngineMode(int m) { engineMode = m; }
void setNNUEPath(const std::string& p) {
    setNNUECalibrationValid(false);
    if (loadNNUE(p)) __android_log_print(ANDROID_LOG_INFO, "AetherisEngine", "NNUE loaded successfully from %s", p.c_str());
    else __android_log_print(ANDROID_LOG_ERROR, "AetherisEngine", "NNUE Load Failed: %s", p.c_str());
}

static int centerDistance(int sq) {
    const int f = sq & 7, r = sq >> 3;
    return std::abs(2 * f - 7) + std::abs(2 * r - 7); // 0..14; smaller is more central
}

static bool fileHasPawn(const Board& b, int file, int side) {
    const Bitboard fileMask = 0x0101010101010101ULL << file;
    return (b.pieces[side][PAWN] & fileMask) != 0;
}

static bool pawnSupportsSquare(Bitboard pawns, int square, int side) {
    const int file = square & 7, rank = square >> 3;
    const int sourceRank = side == WHITE ? rank - 1 : rank + 1;
    if (sourceRank < 0 || sourceRank > 7) return false;
    if (file > 0 && (pawns & (1ULL << (sourceRank * 8 + file - 1)))) return true;
    if (file < 7 && (pawns & (1ULL << (sourceRank * 8 + file + 1)))) return true;
    return false;
}

static bool enemyPawnCanAttackSquare(Bitboard pawns, int square, int enemySide) {
    const int file = square & 7, rank = square >> 3;
    const int sourceRank = enemySide == WHITE ? rank - 1 : rank + 1;
    if (sourceRank < 0 || sourceRank > 7) return false;
    if (file > 0 && (pawns & (1ULL << (sourceRank * 8 + file - 1)))) return true;
    if (file < 7 && (pawns & (1ULL << (sourceRank * 8 + file + 1)))) return true;
    return false;
}

static bool rookPairConnected(const Board& b, int a, int c) {
    if ((a >> 3) == (c >> 3)) {
        const int low = std::min(a & 7, c & 7) + 1;
        const int high = std::max(a & 7, c & 7);
        for (int f = low; f < high; ++f)
            if (b.all & (1ULL << ((a >> 3) * 8 + f))) return false;
        return true;
    }
    if ((a & 7) == (c & 7)) {
        const int low = std::min(a >> 3, c >> 3) + 1;
        const int high = std::max(a >> 3, c >> 3);
        for (int r = low; r < high; ++r)
            if (b.all & (1ULL << (r * 8 + (a & 7)))) return false;
        return true;
    }
    return false;
}

static int classicalWhiteScore(const Board& b) {
    int score = 0;
    int phase = 0; // tapered king evaluation: 0=endgame, 24=opening
    const int phaseWeight[6] = {0, 1, 1, 2, 4, 0};
    for (int side = 0; side < 2; ++side) {
        for (int p = KNIGHT; p <= QUEEN; ++p)
            phase += __builtin_popcountll(b.pieces[side][p]) * phaseWeight[p];
    }
    phase = std::min(24, phase);

    for (int side = 0; side < 2; ++side) {
        const int sign = side == WHITE ? 1 : -1;
        const Bitboard ownPawns = b.pieces[side][PAWN];
        const Bitboard enemyPawns = b.pieces[side ^ 1][PAWN];
        for (int p = 0; p < 6; ++p) {
            Bitboard pieces = b.pieces[side][p];
            const int count = __builtin_popcountll(pieces);
            int value = PIECE_VALUE[p];
            if (p != KING) value = value * (materialWeight + 50) / 100;
            score += sign * count * value;

            while (pieces) {
                const int sq = __builtin_ctzll(pieces);
                pieces &= pieces - 1;
                const int file = sq & 7, rank = sq >> 3;
                const int relativeRank = side == WHITE ? rank : 7 - rank;
                const int cd = centerDistance(sq);
                int positionalBonus = 0;
                switch (p) {
                    case PAWN:
                        positionalBonus += relativeRank * (3 + positional / 25);
                        positionalBonus += (7 - cd) * (positional / 30);
                        // Doubled pawns are less flexible and harder to defend.
                        if (__builtin_popcountll(ownPawns & (0x0101010101010101ULL << file)) > 1)
                            positionalBonus -= 12 + solidity / 5;
                        // Isolated pawns have no neighbouring pawn support.
                        {
                            Bitboard neighbours = 0;
                            if (file > 0) neighbours |= 0x0101010101010101ULL << (file - 1);
                            if (file < 7) neighbours |= 0x0101010101010101ULL << (file + 1);
                            if (!(ownPawns & neighbours)) positionalBonus -= 10 + solidity / 6;
                        }
                        // Passed-pawn bonus grows rapidly as the pawn approaches promotion.
                        {
                            bool passed = true;
                            for (int ef = std::max(0, file - 1); ef <= std::min(7, file + 1); ++ef) {
                                Bitboard enemyFile = enemyPawns & (0x0101010101010101ULL << ef);
                                while (enemyFile) {
                                    int esq = __builtin_ctzll(enemyFile);
                                    enemyFile &= enemyFile - 1;
                                    int er = esq >> 3;
                                    if ((side == WHITE && er > rank) || (side == BLACK && er < rank)) passed = false;
                                }
                            }
                            if (passed) positionalBonus += 10 + relativeRank * relativeRank * 3;
                        }
                        break;
                    case KNIGHT:
                        positionalBonus += (7 - cd) * (positional / 50 + 2);
                        if (relativeRank == 0 || relativeRank == 7) positionalBonus -= 12;
                        // A supported outpost that enemy pawns cannot chase is a durable asset.
                        if (relativeRank >= 3 && relativeRank <= 5 &&
                            pawnSupportsSquare(ownPawns, sq, side) &&
                            !enemyPawnCanAttackSquare(enemyPawns, sq, side ^ 1))
                            positionalBonus += 12 + positional / 10;
                        break;
                    case BISHOP:
                        positionalBonus += (7 - cd) * (positional / 25 + 1);
                        if (relativeRank >= 3 && relativeRank <= 5 &&
                            pawnSupportsSquare(ownPawns, sq, side) &&
                            !enemyPawnCanAttackSquare(enemyPawns, sq, side ^ 1))
                            positionalBonus += 7 + positional / 14;
                        break;
                    case ROOK:
                        if (!fileHasPawn(b, file, side)) positionalBonus += 10 + positional / 8;
                        if (!fileHasPawn(b, file, side) && !fileHasPawn(b, file, side ^ 1))
                            positionalBonus += 8;
                        if (relativeRank == 6) positionalBonus += 18;
                        break;
                    case QUEEN:
                        positionalBonus += (7 - cd) * (positional / 20);
                        // Avoid encouraging premature queen adventures in the opening.
                        if (phase > 16 && relativeRank > 1) positionalBonus -= (relativeRank - 1) * 2;
                        break;
                    case KING: {
                        const int kingOpening = (7 - cd) * 3;
                        const int kingEndgame = (7 - cd) * 8;
                        positionalBonus += (kingOpening * phase + kingEndgame * (24 - phase)) / 24;
                        if (phase > 12) {
                            const int homeRank = side == WHITE ? 0 : 7;
                            if (rank == homeRank && (file == 6 || file == 2)) positionalBonus += 22;
                        }
                        break;
                    }
                }
                score += sign * positionalBonus;
            }
        }

        // Bishop pair is a meaningful long-term asset.
        if (__builtin_popcountll(b.pieces[side][BISHOP]) >= 2)
            score += sign * (26 + positional / 5);

        // Connected rooks coordinate on open files and rank-seven pressure.
        {
            Bitboard rooks = b.pieces[side][ROOK];
            int squares[2] = {-1, -1}, count = 0;
            while (rooks && count < 2) {
                squares[count++] = __builtin_ctzll(rooks);
                rooks &= rooks - 1;
            }
            if (count == 2 && rookPairConnected(b, squares[0], squares[1]))
                score += sign * (12 + positional / 8);
        }

        // Pawn chains and protected passers are harder to undermine tactically.
        {
            Bitboard pawns = ownPawns;
            while (pawns) {
                const int sq = __builtin_ctzll(pawns);
                pawns &= pawns - 1;
                if (pawnSupportsSquare(ownPawns, sq, side))
                    score += sign * (5 + solidity / 20);
                const int relativeRank = side == WHITE ? (sq >> 3) : 7 - (sq >> 3);
                if (relativeRank >= 4 && pawnSupportsSquare(ownPawns, sq, side))
                    score += sign * (relativeRank - 3) * 3;
            }
        }

        // King shelter is valuable before the queens come off. Missing shield pawns
        // and open files adjacent to the king create long-term attack targets.
        if (phase > 8) {
            const Bitboard kingBits = b.pieces[side][KING];
            if (kingBits) {
                const int kingSq = __builtin_ctzll(kingBits);
                const int kingFile = kingSq & 7, kingRank = kingSq >> 3;
                const int shieldRank = kingRank + (side == WHITE ? 1 : -1);
                for (int df = -1; df <= 1; ++df) {
                    const int sf = kingFile + df;
                    if (sf < 0 || sf > 7) continue;
                    if (shieldRank < 0 || shieldRank > 7) {
                        score -= sign * 6;
                        continue;
                    }
                    const bool shieldPawn = (ownPawns & (1ULL << (shieldRank * 8 + sf))) != 0;
                    score += sign * (shieldPawn ? 10 + phase / 3 : -8);
                    if (!fileHasPawn(b, sf, side) && !fileHasPawn(b, sf, side ^ 1))
                        score -= sign * 5;
                }
            }
        }

        // Space: pieces that occupy the opponent's half support safer manoeuvring.
        for (int p = KNIGHT; p <= QUEEN; ++p) {
            Bitboard pieces = b.pieces[side][p];
            while (pieces) {
                const int sq = __builtin_ctzll(pieces);
                pieces &= pieces - 1;
                const int rank = sq >> 3;
                if ((side == WHITE && rank >= 3) || (side == BLACK && rank <= 4))
                    score += sign * (p == KNIGHT || p == BISHOP ? 3 : 1);
            }
        }

        // Small mobility term rewards active minor/major pieces without requiring attack tables.
        for (int p = KNIGHT; p <= QUEEN; ++p) {
            Bitboard pieces = b.pieces[side][p];
            while (pieces) {
                int sq = __builtin_ctzll(pieces);
                pieces &= pieces - 1;
                int f = sq & 7, r = sq >> 3;
                int mobility = 0;
                if (p == KNIGHT) {
                    const int df[8] = {-2,-1,1,2,2,1,-1,-2};
                    const int dr[8] = {1,2,2,1,-1,-2,-2,-1};
                    for (int i=0;i<8;i++) {
                        int nf=f+df[i], nr=r+dr[i];
                        if (nf>=0&&nf<8&&nr>=0&&nr<8 && !(b.occupied[side] & (1ULL << (nr*8+nf)))) mobility++;
                    }
                } else {
                    const int dirs[8][2] = {{1,0},{-1,0},{0,1},{0,-1},{1,1},{1,-1},{-1,1},{-1,-1}};
                    for (int d=0;d<8;d++) {
                        if (p==BISHOP && d<4) continue;
                        if (p==ROOK && d>=4) continue;
                        int nf=f+dirs[d][0], nr=r+dirs[d][1];
                        while(nf>=0&&nf<8&&nr>=0&&nr<8) {
                            int target=nr*8+nf;
                            if (b.occupied[side] & (1ULL<<target)) break;
                            mobility++;
                            if (b.occupied[side^1] & (1ULL<<target)) break;
                            nf+=dirs[d][0]; nr+=dirs[d][1];
                        }
                    }
                }
                const int activityScale = 150 - solidity;
                score += sign * mobility * (positional + 25) * activityScale / 4000;
            }
        }
    }

    // Aggression rewards coordinated pieces close to the opposing king.
    // This bounded proximity heuristic complements tactical search without
    // pretending the neural weights themselves were retrained.
    for (int side = 0; side < 2; ++side) {
        Bitboard enemyKing = b.pieces[side ^ 1][KING];
        if (!enemyKing) continue;
        const int kingSq = __builtin_ctzll(enemyKing);
        const int kf = kingSq & 7, kr = kingSq >> 3;
        int pressure = 0;
        for (int p = KNIGHT; p <= QUEEN; ++p) {
            Bitboard attackers = b.pieces[side][p];
            while (attackers) {
                const int sq = __builtin_ctzll(attackers);
                attackers &= attackers - 1;
                const int distance = std::max(std::abs((sq & 7) - kf), std::abs((sq >> 3) - kr));
                if (distance <= 2) pressure += (3 - distance) * (p == QUEEN ? 2 : 1);
            }
        }
        score += (side == WHITE ? 1 : -1) * pressure * aggression / 8;
    }

    // Initiative is useful, but deliberately modest so it cannot dominate material.
    score += (b.side == WHITE ? 1 : -1) * (2 + aggression / 20);
    // The style controls influence evaluation in a bounded way, rather than adding
    // the old constant "noise" that could not represent a chess preference.
    score += (complexity - 50) / 5;
    score -= (solidity - 50) / 5;
    return score;
}

int evaluate(const Board& b) {
    // All evaluation terms are returned from the side-to-move perspective for negamax.
    const int classical = classicalWhiteScore(b) * (b.side == WHITE ? 1 : -1);
    if (engineMode == ENGINE_PURE_CLASSICAL || !nnueLoaded() || !nnueCalibrationValid) return classical;

    // The bundled AETHNNUE network is a small absolute-square model, not a
    // production HalfKP/HalfKA net. Keep it as a bounded learned residual until
    // a properly trained and benchmarked replacement is bundled. This avoids
    // catastrophic play when a valid file has poor calibration.
    int neural = nnueEvaluate(b);
    const int neuralScale = 100 + (positional - 50) / 3 + (50 - solidity) / 5;
    neural = neural * neuralScale / 100;
    neural = std::max(-600, std::min(600, neural));

    // "NNUE" mode deliberately remains NNUE-assisted rather than raw-network-only:
    // 55% classical anchor + 45% bounded network. Dual Core is more conservative
    // at 75/25. Both continue to benefit from the search tree and the loaded net.
    const int nnuePercent = engineMode == ENGINE_PURE_NNUE ? 45 : 25;
    return (classical * (100 - nnuePercent) + neural * nnuePercent) / 100;
}

} // namespace aetheris
