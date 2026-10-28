#include "bitboard.h"
#include "nnue.h"
#include <emscripten/emscripten.h>
#include <string>

namespace aetheris {
Move searchBestMove(Board& b, int depth, int timeMs);
std::string searchTopLines(Board& b, int depth, int timeMs, int count);
}

namespace {
std::string resultBuffer;
std::string moveToUci(aetheris::Move m) {
    if (!m) return "";
    const int from = m & 63, to = (m >> 6) & 63, promo = (m >> 12) & 7;
    std::string s;
    s += static_cast<char>('a' + (from & 7));
    s += static_cast<char>('1' + (from >> 3));
    s += static_cast<char>('a' + (to & 7));
    s += static_cast<char>('1' + (to >> 3));
    if (promo == aetheris::KNIGHT) s += 'n';
    else if (promo == aetheris::BISHOP) s += 'b';
    else if (promo == aetheris::ROOK) s += 'r';
    else if (promo == aetheris::QUEEN) s += 'q';
    return s;
}
}

extern "C" {
EMSCRIPTEN_KEEPALIVE int aetheris_init() {
    aetheris::initBitboards();
    aetheris::resetBoard();
    return aetheris::loadNNUE("/weights.bin") ? 1 : 0;
}

EMSCRIPTEN_KEEPALIVE int aetheris_load_nnue(const char* path) {
    return path && aetheris::loadNNUE(std::string(path)) ? 1 : 0;
}

EMSCRIPTEN_KEEPALIVE void aetheris_set_mode(int mode) {
    aetheris::setEngineMode(mode);
}

EMSCRIPTEN_KEEPALIVE void aetheris_set_personality(int aggression, int positional, int complexity, int solidity, int materialWeight) {
    aetheris::setAggression(aggression);
    aetheris::setPositional(positional);
    aetheris::setComplexity(complexity);
    aetheris::setSolidity(solidity);
    aetheris::setMaterialWeight(materialWeight);
}

EMSCRIPTEN_KEEPALIVE int aetheris_eval(const char* fen) {
    if (!fen) return 0;
    aetheris::setPosition(std::string(fen));
    return aetheris::evaluate(aetheris::getBoard());
}

EMSCRIPTEN_KEEPALIVE const char* aetheris_best_move(const char* fen, int depth, int timeMs) {
    if (!fen) return "";
    aetheris::setPosition(std::string(fen));
    resultBuffer = moveToUci(aetheris::searchBestMove(aetheris::getBoard(), depth, timeMs));
    return resultBuffer.c_str();
}

EMSCRIPTEN_KEEPALIVE const char* aetheris_analyze(const char* fen, int depth, int timeMs, int count) {
    if (!fen) return "";
    aetheris::setPosition(std::string(fen));
    resultBuffer = aetheris::searchTopLines(aetheris::getBoard(), depth, timeMs, count);
    return resultBuffer.c_str();
}
}
