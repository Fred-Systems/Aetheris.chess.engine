#include "bitboard.h"
#include "nnue.h"
#include <jni.h>
#include <string>
#include <sstream>
#include <android/log.h>
#include <signal.h>
#include <fcntl.h>
#include <unistd.h>
#include <sys/types.h>

#define AETH_LOGI(...) __android_log_print(ANDROID_LOG_INFO, "AetherisEngine", __VA_ARGS__)
#define AETH_LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "AetherisEngine", __VA_ARGS__)

using namespace aetheris;
namespace aetheris { Move searchBestMove(Board& b, int depth, int timeMs); int searchEvaluation(Board& b, int depth, int timeMs); std::string searchTopLines(Board& b, int depth, int timeMs, int count); void clearSearchTables(); }

namespace {
int g_crash_report_fd = -1;
volatile sig_atomic_t g_native_stage = 0;
bool g_engine_params_initialized = false;
int g_last_engine_params[6] = {0, 0, 0, 0, 0, 0};

static void aetherisFatalSignalHandler(int signal_number, siginfo_t*, void*) {
    if (g_crash_report_fd >= 0) {
        const char* stage = "NATIVE_STAGE=unknown\n";
        size_t stage_len = sizeof("NATIVE_STAGE=unknown\n") - 1;
        if (g_native_stage == 1) { stage = "NATIVE_STAGE=nativeGo-enter\n"; stage_len = sizeof("NATIVE_STAGE=nativeGo-enter\n") - 1; }
        else if (g_native_stage == 2) { stage = "NATIVE_STAGE=searchBestMove-running\n"; stage_len = sizeof("NATIVE_STAGE=searchBestMove-running\n") - 1; }
        else if (g_native_stage == 3) { stage = "NATIVE_STAGE=searchBestMove-returned\n"; stage_len = sizeof("NATIVE_STAGE=searchBestMove-returned\n") - 1; }
        else if (g_native_stage == 4) { stage = "NATIVE_STAGE=move-string-conversion\n"; stage_len = sizeof("NATIVE_STAGE=move-string-conversion\n") - 1; }
        (void)write(g_crash_report_fd, "NATIVE_FATAL_SIGNAL\n", sizeof("NATIVE_FATAL_SIGNAL\n") - 1);
        (void)write(g_crash_report_fd, stage, stage_len);
        const char* signal_text = "SIGNAL=UNKNOWN\n";
        size_t signal_len = sizeof("SIGNAL=UNKNOWN\n") - 1;
        if (signal_number == SIGSEGV) { signal_text = "SIGNAL=SIGSEGV\n"; signal_len = sizeof("SIGNAL=SIGSEGV\n") - 1; }
        else if (signal_number == SIGABRT) { signal_text = "SIGNAL=SIGABRT\n"; signal_len = sizeof("SIGNAL=SIGABRT\n") - 1; }
        else if (signal_number == SIGBUS) { signal_text = "SIGNAL=SIGBUS\n"; signal_len = sizeof("SIGNAL=SIGBUS\n") - 1; }
        else if (signal_number == SIGILL) { signal_text = "SIGNAL=SIGILL\n"; signal_len = sizeof("SIGNAL=SIGILL\n") - 1; }
        else if (signal_number == SIGFPE) { signal_text = "SIGNAL=SIGFPE\n"; signal_len = sizeof("SIGNAL=SIGFPE\n") - 1; }
        (void)write(g_crash_report_fd, signal_text, signal_len);
    }
    struct sigaction action{};
    action.sa_handler = SIG_DFL;
    sigemptyset(&action.sa_mask);
    (void)sigaction(signal_number, &action, nullptr);
    (void)kill(getpid(), signal_number);
}
}

extern "C" void aetherisSetNativeCrashStage(int stage) { g_native_stage = stage; }

extern "C" {

JNIEXPORT void JNICALL
Java_com_fredsystems_aetheris_engine_NativeEngine_nativeInstallCrashHandler(JNIEnv* env, jobject, jstring reportPath) {
    const char* path = env->GetStringUTFChars(reportPath, nullptr);
    if (!path) return;
    int fd = open(path, O_WRONLY | O_CREAT | O_APPEND | O_CLOEXEC, 0600);
    env->ReleaseStringUTFChars(reportPath, path);
    if (fd < 0) return;
    if (g_crash_report_fd >= 0) close(g_crash_report_fd);
    g_crash_report_fd = fd;
    const int signals[] = { SIGSEGV, SIGABRT, SIGBUS, SIGILL, SIGFPE };
    struct sigaction action{};
    action.sa_sigaction = aetherisFatalSignalHandler;
    sigemptyset(&action.sa_mask);
    action.sa_flags = SA_SIGINFO | SA_RESETHAND;
    for (int signal_number : signals) (void)sigaction(signal_number, &action, nullptr);
    (void)write(g_crash_report_fd, "NATIVE_CRASH_HANDLER_INSTALLED\n", sizeof("NATIVE_CRASH_HANDLER_INSTALLED\n") - 1);
}

JNIEXPORT void JNICALL
Java_com_fredsystems_aetheris_engine_NativeEngine_nativeInit(JNIEnv*, jobject) {
    clearSearchTables();
    g_engine_params_initialized = false;
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
    const int params[6] = { aggression, positional, complexity, solidity, materialWeight, mode };
    bool changed = !g_engine_params_initialized;
    for (int i = 0; i < 6; ++i) {
        if (g_last_engine_params[i] != params[i]) changed = true;
        g_last_engine_params[i] = params[i];
    }
    g_engine_params_initialized = true;
    // TT scores depend on evaluation mode/style. Preserve them for consecutive
    // searches with identical options, but clear them whenever the configuration changes.
    if (changed) clearSearchTables();
    setAggression(aggression);
    setPositional(positional);
    setComplexity(complexity);
    setSolidity(solidity);
    setMaterialWeight(materialWeight);
    setEngineMode(mode);
}

JNIEXPORT jboolean JNICALL
Java_com_fredsystems_aetheris_engine_NativeEngine_nativeNnueLoaded(JNIEnv*, jobject) {
    return nnueLoaded() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_fredsystems_aetheris_engine_NativeEngine_nativeSetNnue(JNIEnv* env, jobject, jstring path) {
    const char* chars = env->GetStringUTFChars(path, nullptr);
    if (chars) {
        clearSearchTables();
        setNNUEPath(std::string(chars));
        if (nnueLoaded()) AETH_LOGI("NNUE loaded successfully: %s", chars);
        else AETH_LOGE("NNUE Load Failed: %s", chars);
        env->ReleaseStringUTFChars(path, chars);
    }
}

JNIEXPORT jstring JNICALL
Java_com_fredsystems_aetheris_engine_NativeEngine_nativeGo(JNIEnv* env, jobject, jint depth, jint timeMs) {
    aetherisSetNativeCrashStage(1);
    AETH_LOGI("nativeGo ENTER depth=%d timeMs=%d", (int)depth, (int)timeMs);
    Board& b = getBoard();
    aetherisSetNativeCrashStage(2);
    Move m = searchBestMove(b, depth, timeMs);
    aetherisSetNativeCrashStage(3);
    AETH_LOGI("nativeGo SEARCH_RETURN move=%u", (unsigned int)m);
    if (m == 0) return env->NewStringUTF("");
    aetherisSetNativeCrashStage(4);
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

JNIEXPORT jstring JNICALL
Java_com_fredsystems_aetheris_engine_NativeEngine_nativeAnalyzeFen(JNIEnv* env, jobject, jstring fen, jint depth, jint timeMs, jint count) {
    const char* chars = env->GetStringUTFChars(fen, nullptr);
    if (!chars) return env->NewStringUTF("");
    setPosition(std::string(chars));
    env->ReleaseStringUTFChars(fen, chars);
    const std::string lines = searchTopLines(getBoard(), depth, timeMs, count);
    return env->NewStringUTF(lines.c_str());
}

JNIEXPORT jint JNICALL
Java_com_fredsystems_aetheris_engine_NativeEngine_nativeSearchEvaluationFen(JNIEnv* env, jobject, jstring fen, jint depth, jint timeMs) {
    const char* chars = env->GetStringUTFChars(fen, nullptr);
    if (!chars) return 0;
    setPosition(std::string(chars));
    env->ReleaseStringUTFChars(fen, chars);
    return searchEvaluation(getBoard(), depth, timeMs);
}

JNIEXPORT jint JNICALL
Java_com_fredsystems_aetheris_engine_NativeEngine_nativeEvaluate(JNIEnv*, jobject) {
    return evaluate(getBoard());
}

JNIEXPORT jint JNICALL
Java_com_fredsystems_aetheris_engine_NativeEngine_nativeRawNnueEvaluateFen(JNIEnv* env, jobject, jstring fen) {
    const char* chars = env->GetStringUTFChars(fen, nullptr);
    if (!chars) return 0;
    setPosition(std::string(chars));
    env->ReleaseStringUTFChars(fen, chars);
    return nnueEvaluate(getBoard());
}

JNIEXPORT jstring JNICALL
Java_com_fredsystems_aetheris_engine_NativeEngine_nativeNnueSanityCheck(JNIEnv*, jobject) {
    if (!nnueLoaded()) {
        AETH_LOGE("NNUE Load Failed: sanity check requested without a loaded network");
        return env->NewStringUTF("NNUE Load Failed");
    }
    struct CheckCase { const char* label; const char* fen; int expectedSign; };
    const CheckCase cases[] = {
        {"start", "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1", 0},
        {"white_queen_missing", "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNB1KBNR w KQkq - 0 1", -1},
        {"black_queen_missing", "rnb1kbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1", 1}
    };
    std::ostringstream report;
    bool sane = true;
    for (const auto& test : cases) {
        setPosition(test.fen);
        const int cp = nnueEvaluate(getBoard()); // raw, unsearched NNUE score in centipawns
        if (test.expectedSign == 0) sane = sane && std::abs(cp) <= 100;
        else sane = sane && ((test.expectedSign < 0 && cp < 0) || (test.expectedSign > 0 && cp > 0));
        if (test.expectedSign != 0 && (std::abs(cp) < 500 || std::abs(cp) > 1600)) sane = false;
        AETH_LOGI("NNUE_SANITY %s raw_cp=%d", test.label, cp);
        report << test.label << "=" << cp << "cp; ";
    }
    report << (sane ? "PASS" : "WARNING: weights/calibration failed sanity expectations");
    if (!sane) AETH_LOGE("NNUE_SANITY_FAILED %s", report.str().c_str());
    else AETH_LOGI("NNUE_SANITY_PASSED %s", report.str().c_str());
    resetBoard();
    return env->NewStringUTF(report.str().c_str());
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
