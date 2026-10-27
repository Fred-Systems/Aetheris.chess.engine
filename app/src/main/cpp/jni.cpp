#include "bitboard.h"
#include <jni.h>
#include <string>
#include <sstream>
#include <android/log.h>
#include <csignal>
#include <cstdint>
#include <cstring>
#include <fcntl.h>
#include <unistd.h>
#include <sys/types.h>

#define AETH_LOGI(...) __android_log_print(ANDROID_LOG_INFO, "AetherisEngine", __VA_ARGS__)
#define AETH_LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "AetherisEngine", __VA_ARGS__)

using namespace aetheris;

namespace aetheris { Move searchBestMove(Board& b, int depth, int timeMs); }

namespace {
char g_crash_report_path[1024] = {0};

void appendLiteral(char* out, size_t& pos, const char* s) {
    while (*s && pos < 1000) out[pos++] = *s++;
}
void appendUnsigned(char* out, size_t& pos, unsigned long value) {
    char digits[24]; size_t n = 0;
    do { digits[n++] = static_cast<char>('0' + (value % 10)); value /= 10; } while (value && n < sizeof(digits));
    while (n && pos < 1000) out[pos++] = digits[--n];
}
void appendHex(char* out, size_t& pos, uintptr_t value) {
    static const char hex[] = "0123456789abcdef";
    char digits[2 * sizeof(uintptr_t)]; size_t n = 0;
    do { digits[n++] = hex[value & 0xf]; value >>= 4; } while (value && n < sizeof(digits));
    appendLiteral(out, pos, "0x");
    while (n && pos < 1000) out[pos++] = digits[--n];
}

// Keep this handler deliberately small and use low-level file operations so a
// fatal native signal leaves a durable marker before Android terminates the process.
void fatalSignalHandler(int signalNumber, siginfo_t* info, void*) {
    const char* path = g_crash_report_path;
    if (path[0]) {
        char line[1024]; size_t pos = 0;
        appendLiteral(line, pos, "NATIVE_FATAL_SIGNAL signal=");
        appendUnsigned(line, pos, static_cast<unsigned long>(signalNumber));
        appendLiteral(line, pos, " faultAddress=");
        appendHex(line, pos, reinterpret_cast<uintptr_t>(info ? info->si_addr : nullptr));
        appendLiteral(line, pos, " pid=");
        appendUnsigned(line, pos, static_cast<unsigned long>(getpid()));
        if (pos < sizeof(line) - 1) line[pos++] = '\\n';
        int fd = open(path, O_WRONLY | O_CREAT | O_APPEND, 0600);
        if (fd >= 0) {
            (void)write(fd, line, pos);
            (void)fsync(fd);
            (void)close(fd);
        }
    }
    // SA_RESETHAND restores the platform handler. Returning lets the original
    // fault be delivered again so Android still generates its native tombstone.
}

void installFatalSignalHandlers() {
    struct sigaction action{};
    action.sa_sigaction = fatalSignalHandler;
    sigemptyset(&action.sa_mask);
    action.sa_flags = SA_SIGINFO | SA_RESETHAND;
    sigaction(SIGSEGV, &action, nullptr);
    sigaction(SIGABRT, &action, nullptr);
    sigaction(SIGBUS, &action, nullptr);
    sigaction(SIGILL, &action, nullptr);
    sigaction(SIGFPE, &action, nullptr);
}
}

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
