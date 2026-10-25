#include "bitboard.h"
#include "nnue.h"
#include <string>

namespace aetheris {

static const int PIECE_VALUE[6] = {100, 320, 330, 500, 900, 20000};
static int aggression = 50;
static int materialWeight = 50;
int engineMode = ENGINE_NNUE;

void setAggression(int a) { aggression = a; }
void setMaterialWeight(int m) { materialWeight = m; }
void setEngineMode(int m) { engineMode = m; }
void setNNUEPath(const std::string& p) { loadNNUE(p); }

int evaluate(const Board& b) {
    if (engineMode == ENGINE_NNUE && nnueLoaded()) {
        return nnueEvaluate(b);
    }
    int score = 0;
    for (int c = 0; c < 2; c++) {
        for (int p = 0; p < 6; p++) {
            int cnt = __builtin_popcountll(b.pieces[c][p]);
            int val = PIECE_VALUE[p] * (materialWeight + (p==0?0: (aggression/2))) / 100;
            if (c == 0) score += cnt * val; else score -= cnt * val;
        }
    }
    return (b.side == 0) ? score : -score;
}

} // namespace aetheris