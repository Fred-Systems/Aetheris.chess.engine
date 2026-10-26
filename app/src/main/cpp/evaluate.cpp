#include "bitboard.h"
#include "nnue.h"
#include <string>

namespace aetheris {

static const int PIECE_VALUE[6] = {100, 320, 330, 500, 900, 20000};
static int aggression = 50;          // 0 defense, 100 aggression
static int positional = 50;          // 0 tactical, 100 positional
static int complexity = 50;          // 0 simplification, 100 complexity
static int solidity = 50;            // 0 activity, 100 solidity
static int materialWeight = 50;
int engineMode = ENGINE_DUAL_CORE;

void setAggression(int a) { aggression = a; }
void setPositional(int v) { positional = v; }
void setComplexity(int v) { complexity = v; }
void setSolidity(int v) { solidity = v; }
void setMaterialWeight(int m) { materialWeight = m; }
void setEngineMode(int m) { engineMode = m; }
void setNNUEPath(const std::string& p) { loadNNUE(p); }

int evaluate(const Board& b) {
    bool useNnue = (engineMode != ENGINE_PURE_CLASSICAL) && nnueLoaded();
    if (engineMode == ENGINE_PURE_NNUE && nnueLoaded()) {
        return nnueEvaluate(b);
    }
    if (engineMode == ENGINE_DUAL_CORE && nnueLoaded()) {
        int n = nnueEvaluate(b);
        // blend with classical
        int c = 0;
        for (int col = 0; col < 2; col++) {
            for (int p = 0; p < 6; p++) {
                int cnt = __builtin_popcountll(b.pieces[col][p]);
                int val = PIECE_VALUE[p] * materialWeight / 100;
                if (col == 0) c += cnt * val; else c -= cnt * val;
            }
        }
        c = (b.side == 0) ? c : -c;
        return (n + c) / 2;
    }
    // classical
    int score = 0;
    for (int col = 0; col < 2; col++) {
        for (int p = 0; p < 6; p++) {
            int cnt = __builtin_popcountll(b.pieces[col][p]);
            int val = PIECE_VALUE[p] * materialWeight / 100;
            // positional/tactical/solidity/activity tweaks
            val += (positional - 50) * (p >= 1 ? 2 : 0);
            val += (aggression - 50) * (p == 0 ? 0 : 1);
            if (col == 0) score += cnt * val; else score -= cnt * val;
        }
    }
    // complexity/solidity small noise to orientation (placeholder scaling)
    score += (complexity - 50) / 5;
    score -= (solidity - 50) / 5;
    return (b.side == 0) ? score : -score;
}

} // namespace aetheris