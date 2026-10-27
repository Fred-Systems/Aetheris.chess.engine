#include "bitboard.h"
#include <jni.h>
#include <string>
#include <sstream>
#include <android/log.h>

#define AETH_LOGI(...) __android_log_print(ANDROID_LOG_INFO, "AetherisEngine", __VA_ARGS__)
#define AETH_LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "AetherisEngine", __VA_ARGS__)

using namespace aetheris;

namespace aetheris { Move searchBestMove(Board& b, int depth, int timeMs); }

extern "C" {

JNIEXPORT void JNICALL
Java_com_fredsystems_aetheris_engine_NativeEngine_nativeInit(JNIEnv*, jobject) {
    initBitboards();
    resetBoard();
}

JNIEXPORT void JNICALL
Java_com_fredsystems_aetheris_engine_NativeEngine_nativeReset(JNIEnv*, jobject) {
    resetBoard();
}

JNIEXPORT void JNICALL
Java_com_fredsystems_aetheris_engine_NativeEngine_nativeSetPosition(JNIEnv* env, jobject, jstring fen) {
    const char* chars = env->GetStringUTFChars(fen, nullptr);
    if (chars) {
        setPosition(std::string(chars));
        env->ReleaseStringUTFChars(fen, chars);
    }
}

JNIEXPORT void JNICALL
Java_com_fredsystems_aetheris_engine_NativeEngine_nativeSetParams(
    JNIEnv*, jobject, jint aggression, jint positional, jint complexity,
    jint solidity, jint materialWeight, jint mode) {
    setAggression(aggression);
    setPositional(positional);
    setComplexity(complexity);
    setSolidity(solidity);
    setMaterialWeight(materialWeight);
    setEngineMode(mode);
}

JNIEXPORT void JNICALL
Java_com_fredsystems_aetheris_engine_NativeEngine_nativeSetNnue(JNIEnv* env, jobject, jstring path) {
    const char* chars = env->GetStringUTFChars(path, nullptr);
    if (chars) {
        setNNUEPath(std::string(chars));
        env->ReleaseStringUTFChars(path, chars);
    }
}

JNIEXPORT jstring JNICALL
Java_com_fredsystems_aetheris_engine_NativeEngine_nativeGo(JNIEnv* env, jobject, jint depth, jint timeMs) {
    AETH_LOGI("nativeGo ENTER depth=%d timeMs=%d", (int)depth, (int)timeMs);
    Board& b = getBoard();
    Move m = searchBestMove(b, depth, timeMs);
    AETH_LOGI("nativeGo SEARCH_RETURN move=%u", (unsigned int)m);
    if (m == 0) return env->NewStringUTF("");
    int from = m & 63;
    int to = (m >> 6) & 63;
    int promo = (m >> 12) & 7;
    char promoChar = 0;
    if (promo == KNIGHT) promoChar = 'n';
    else if (promo == BISHOP) promoChar = 'b';
    else if (promo == ROOK) promoChar = 'r';
    else if (promo == QUEEN) promoChar = 'q';
    std::string uci;
    uci += static_cast<char>('a' + (from % 8));
    uci += static_cast<char>('1' + (from / 8));
    uci += static_cast<char>('a' + (to % 8));
    uci += static_cast<char>('1' + (to / 8));
    if (promoChar) uci += promoChar;
    return env->NewStringUTF(uci.c_str());
}

JNIEXPORT jint JNICALL
Java_com_fredsystems_aetheris_engine_NativeEngine_nativeEvaluate(JNIEnv*, jobject) {
    return evaluate(getBoard());
}

JNIEXPORT jint JNICALL
Java_com_fredsystems_aetheris_engine_NativeEngine_nativeEvaluateFen(JNIEnv* env, jobject, jstring fen) {
    const char* chars = env->GetStringUTFChars(fen, nullptr);
    if (!chars) return 0;
    setPosition(std::string(chars));
    env->ReleaseStringUTFChars(fen, chars);
    return evaluate(getBoard());
}

}
