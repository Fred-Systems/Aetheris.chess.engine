#include "bitboard.h"
#include "nnue.h"
#include <algorithm>
#include <cstdlib>
#include <android/log.h>
// v1.0.14 evaluation diagnostics and mode separation.

namespace aetheris {

static const int PIECE_VALUE[6] = {100, 320, 335, 500, 950, 20000};
static int aggression = 50;
static int positional = 50;
static int complexity = 50;
static int solidity = 50;
static int materialWeight = 50;
int engineMode = ENGINE_DUAL_CORE;

void setAggression(int a) { aggression = std::max(0, std::min(100, a)); }
void setPositional(int v) { positional = std::max(0, std::min(100, v)); }
void setComplexity(int v) { complexity = std::max(0, std::min(100, v)); }
void setSolidity(int v) { solidity = std::max(0, std::min(100, v)); }
void setMaterialWeight(int m) { materialWeight = std::max(0, std::min(100, m)); }
void setEngineMode(int m) { engineMode = m; }
void setNNUEPath(const std::string& p) {
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
                        break;
                    case BISHOP:
                        positionalBonus += (7 - cd) * (positional / 25 + 1);
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
    const int classical = classicalWhiteScore(b) * (b.side == WHITE ? 1 : -1);
    const bool loaded = nnueLoaded();
    if (!loaded || engineMode == ENGINE_PURE_CLASSICAL) return classical;
    int nn = nnueEvaluate(b);
    // Personality sliders scale the learned signal modestly; they do not mutate weights.
    const int nnScale = 100 + (positional - 50) / 2 + (50 - solidity) / 4;
    nn = nn * nnScale / 100;
    if (engineMode == ENGINE_PURE_NNUE) return nn;
    // Dual Core combines the two evaluation sources equally; it is distinct from both single modes.
    return (nn + classical) / 2;
}

} // namespace aetheris
