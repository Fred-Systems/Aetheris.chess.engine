#include "nnue.h"
#include <utility>
#include <fstream>
#include <cstring>
#include <cmath>

namespace aetheris {

static const char* NNUE_MAGIC = "AETHNNUE";
static int NUM_FEATURES = 768;
static int HIDDEN = 128;

struct NNUE {
    std::vector<float> w1; // HIDDEN x NUM_FEATURES
    std::vector<float> b1; // HIDDEN
    std::vector<float> w2; // HIDDEN
    float b2 = 0.0f;
    bool loaded = false;
} g_nnue;

bool loadNNUE(const std::string& path) {
    std::ifstream f(path, std::ios::binary);
    if (!f) return false;
    char magic[9] = {0};
    f.read(magic, 8);
    if (std::strcmp(magic, NNUE_MAGIC) != 0) return false;
    int version = 0, nf = 0, nh = 0;
    f.read(reinterpret_cast<char*>(&version), sizeof(int));
    f.read(reinterpret_cast<char*>(&nf), sizeof(int));
    f.read(reinterpret_cast<char*>(&nh), sizeof(int));
    // Reject malformed or truncated network files before allocating or evaluating.
    // The bundled Aetheris network uses 768 piece-square features and 128 hidden units.
    if (nf != 768 || nh != 128) {
        g_nnue.loaded = false;
        return false;
    }
    std::vector<float> w1((size_t)nh * nf, 0.0f);
    std::vector<float> b1((size_t)nh, 0.0f);
    std::vector<float> w2((size_t)nh, 0.0f);
    float b2 = 0.0f;
    f.read(reinterpret_cast<char*>(w1.data()), w1.size() * sizeof(float));
    f.read(reinterpret_cast<char*>(b1.data()), b1.size() * sizeof(float));
    f.read(reinterpret_cast<char*>(w2.data()), w2.size() * sizeof(float));
    f.read(reinterpret_cast<char*>(&b2), sizeof(float));
    if (!f) {
        g_nnue.loaded = false;
        return false;
    }
    NUM_FEATURES = nf;
    HIDDEN = nh;
    g_nnue.w1 = std::move(w1);
    g_nnue.b1 = std::move(b1);
    g_nnue.w2 = std::move(w2);
    g_nnue.b2 = b2;
    g_nnue.loaded = true;
    return true;
}

bool nnueLoaded() { return g_nnue.loaded; }

static void extractFeatures(const Board& b, std::vector<float>& feat) {
    feat.assign(NUM_FEATURES, 0.0f);
    for (int c = 0; c < 2; c++) {
        for (int p = 0; p < 6; p++) {
            Bitboard bb = b.pieces[c][p];
            while (bb) {
                int sq = __builtin_ctzll(bb);
                bb &= bb - 1;
                int idx = (c * 6 + p) * 64 + sq;
                if (idx < NUM_FEATURES) feat[idx] = 1.0f;
            }
        }
    }
}

int nnueEvaluate(const Board& b) {
    if (!g_nnue.loaded) return 0;
    std::vector<float> feat;
    extractFeatures(b, feat);
    // hidden
    std::vector<float> hidden(HIDDEN, 0.0f);
    for (int h = 0; h < HIDDEN; h++) {
        float sum = g_nnue.b1[h];
        const float* w = &g_nnue.w1[h * NUM_FEATURES];
        for (int i = 0; i < NUM_FEATURES; i++) {
            if (feat[i] != 0.0f) sum += w[i];
        }
        hidden[h] = sum > 0.0f ? sum : 0.0f;
    }
    float out = g_nnue.b2;
    for (int h = 0; h < HIDDEN; h++) out += g_nnue.w2[h] * hidden[h];
    int cp = (int)(out * 100.0f);
    return (b.side == 0) ? cp : -cp;
}

} // namespace aetheris