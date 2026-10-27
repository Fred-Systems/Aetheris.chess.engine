#include "nnue.h"
#include <utility>
#include <fstream>
#include <cstring>
#include <cmath>
#include <limits>

namespace aetheris {

static const char* NNUE_MAGIC = "AETHNNUE";
static int NUM_FEATURES = 768;
static int HIDDEN = 128;

struct NNUE {
    std::vector<float> w1;
    std::vector<float> b1;
    std::vector<float> w2;
    float b2 = 0.0f;
    bool loaded = false;
} g_nnue;

bool loadNNUE(const std::string& path) {
    // Never leave a previously loaded network active after a failed reload.
    g_nnue.loaded = false;
    std::ifstream f(path, std::ios::binary);
    if (!f) return false;

    char magic[9] = {0};
    f.read(magic, 8);
    if (!f || std::strcmp(magic, NNUE_MAGIC) != 0) return false;

    int version = 0, nf = 0, nh = 0;
    f.read(reinterpret_cast<char*>(&version), sizeof(version));
    f.read(reinterpret_cast<char*>(&nf), sizeof(nf));
    f.read(reinterpret_cast<char*>(&nh), sizeof(nh));
    // Training exports currently use 768 piece-square features and either 128
    // (bundled legacy network) or 512 (the newer trained network) hidden units.
    if (!f || version != 1 || nf != 768 || (nh != 128 && nh != 512)) return false;

    const size_t expectedBytes = 8 + sizeof(int) * 3
        + (static_cast<size_t>(nh) * nf + static_cast<size_t>(nh) * 2 + 1) * sizeof(float);
    f.seekg(0, std::ios::end);
    const auto actualBytes = f.tellg();
    if (actualBytes < 0 || static_cast<size_t>(actualBytes) != expectedBytes) return false;
    f.seekg(8 + sizeof(int) * 3, std::ios::beg);

    std::vector<float> w1(static_cast<size_t>(nh) * nf);
    std::vector<float> b1(static_cast<size_t>(nh));
    std::vector<float> w2(static_cast<size_t>(nh));
    float b2 = 0.0f;
    f.read(reinterpret_cast<char*>(w1.data()), static_cast<std::streamsize>(w1.size() * sizeof(float)));
    f.read(reinterpret_cast<char*>(b1.data()), static_cast<std::streamsize>(b1.size() * sizeof(float)));
    f.read(reinterpret_cast<char*>(w2.data()), static_cast<std::streamsize>(w2.size() * sizeof(float)));
    f.read(reinterpret_cast<char*>(&b2), sizeof(b2));
    if (!f) return false;

    // A corrupt/NaN checkpoint can otherwise poison every evaluation and search.
    const auto finite = [](float v) { return std::isfinite(v); };
    for (float v : w1) if (!finite(v)) return false;
    for (float v : b1) if (!finite(v)) return false;
    for (float v : w2) if (!finite(v)) return false;
    if (!finite(b2)) return false;

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

static void extractActiveFeatures(const Board& b, std::vector<int>& active) {
    active.clear();
    active.reserve(32);
    for (int color = 0; color < 2; ++color) {
        for (int piece = 0; piece < 6; ++piece) {
            Bitboard bb = b.pieces[color][piece];
            while (bb) {
                const int sq = __builtin_ctzll(bb);
                bb &= bb - 1;
                active.push_back((color * 6 + piece) * 64 + sq);
            }
        }
    }
}

int nnueEvaluate(const Board& b) {
    if (!g_nnue.loaded) return 0;
    std::vector<int> active;
    extractActiveFeatures(b, active);
    std::vector<float> hidden(static_cast<size_t>(HIDDEN), 0.0f);
    for (int h = 0; h < HIDDEN; h++) {
        float sum = g_nnue.b1[h];
        const float* w = &g_nnue.w1[static_cast<size_t>(h) * NUM_FEATURES];
        for (int feature : active) sum += w[feature];
        hidden[h] = sum > 0.0f ? sum : 0.0f;
    }
    float out = g_nnue.b2;
    for (int h = 0; h < HIDDEN; h++) out += g_nnue.w2[h] * hidden[h];
    if (!std::isfinite(out)) return 0;
    int cp = static_cast<int>(out * 100.0f);
    return (b.side == 0) ? cp : -cp;
}

} // namespace aetheris
