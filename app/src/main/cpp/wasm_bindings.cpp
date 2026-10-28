#include "bitboard.h"
#include "nnue.h"
#include <emscripten/emscripten.h>
#include <string>
#include <cstdlib>

namespace aetheris {
Move searchBestMove(Board& b, int depth, int timeMs);
std::string searchTopLines(Board& b, int depth, int timeMs, int count);
}

namespace {
bool validateNnueCalibration() {
    if (!aetheris::nnueLoaded()) {
        aetheris::setNNUECalibrationValid(false);
        return false;
    }
    struct CheckCase { const char* fen; int expectedSign; };
    const CheckCase cases[] = {
        {"rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1", 0},
        {"rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNB1KBNR w KQkq - 0 1", -1},
        {"rnb1kbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1", 1}
    };
    bool sane = true;
    for (const auto& test : cases) {
        aetheris::setPosition(std::string(test.fen));
        const int cp = aetheris::nnueEvaluate(aetheris::getBoard());
        if (test.expectedSign == 0) sane = sane && std::abs(cp) <= 40;
        else sane = sane && ((test.expectedSign < 0 && cp < 0) || (test.expectedSign > 0 && cp > 0));
        if (test.expectedSign != 0 && (std::abs(cp) < 800 || std::abs(cp) > 1200)) sane = false;
    }
    aetheris::setNNUECalibrationValid(sane);
    aetheris::resetBoard();
    return sane;
}
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
    const bool loaded = aetheris::loadNNUE("/weights.bin");
    if (loaded) validateNnueCalibration();
    return loaded ? 1 : 0;
}

EMSCRIPTEN_KEEPALIVE int aetheris_load_nnue(const char* path) {
    if (!path || !aetheris::loadNNUE(std::string(path))) {
        aetheris::setNNUECalibrationValid(false);
        return 0;
    }
    validateNnueCalibration();
    return 1;
}

EMSCRIPTEN_KEEPALIVE int aetheris_validate_nnue() {
    return validateNnueCalibration() ? 1 : 0;
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
