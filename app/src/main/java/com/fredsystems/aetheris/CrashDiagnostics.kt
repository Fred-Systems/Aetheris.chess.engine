package com.fredsystems.aetheris

import android.content.Context
import android.os.Build
import android.os.Debug
import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant

/**
 * Persistent crash evidence for the dedicated diagnostic build.
 * Java crashes record their full stack trace; native fatal signals are written by JNI.
 */
object CrashDiagnostics {
    const val FILE_NAME = "aetheris_crash_report.txt"
    private val lock = Any()
    @Volatile private var handlerInstalled = false

    private fun reportFile(context: Context) = File(context.filesDir, FILE_NAME)

    fun initialize(context: Context): Boolean {
        val app = context.applicationContext
        synchronized(lock) {
            val file = reportFile(app)
            val previous = runCatching { file.readText() }.getOrDefault("")
            val lastAcknowledged = previous.lastIndexOf("CRASH_REPORT_ACKNOWLEDGED")
            val lastStart = previous.lastIndexOf("SEARCH_START")
            val lastDone = previous.lastIndexOf("SEARCH_COMPLETE")
            val javaCrash = previous.lastIndexOf("JAVA_UNCAUGHT_EXCEPTION")
            val nativeCrash = previous.lastIndexOf("NATIVE_FATAL_SIGNAL")
            val crashPending = maxOf(javaCrash, nativeCrash) > lastAcknowledged ||
                (lastStart > lastDone && lastStart > lastAcknowledged)

            appendLocked(app, "APP_START time=${Instant.now()} version=${app.packageManager.getPackageInfo(app.packageName, 0).versionName} package=${app.packageName} device=${Build.MANUFACTURER} ${Build.MODEL} android=${Build.VERSION.RELEASE} sdk=${Build.VERSION.SDK_INT} abi=${Build.SUPPORTED_ABIS.joinToString(",")}")
            appendLocked(app, "RUNTIME memoryMaxBytes=${Runtime.getRuntime().maxMemory()} memoryFreeBytes=${Runtime.getRuntime().freeMemory()} nativeHeapBytes=${Debug.getNativeHeapAllocatedSize()}")
            installUncaughtExceptionHandler(app)
            return crashPending
        }
    }

    private fun installUncaughtExceptionHandler(context: Context) {
        if (handlerInstalled) return
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            recordException(context, "JAVA_UNCAUGHT_EXCEPTION thread=${thread.name} id=${thread.id}", error)
            synchronized(lock) {
                appendLocked(context, "JAVA_UNCAUGHT_EXCEPTION time=${Instant.now()} thread=${thread.name} id=${thread.id}")
            }
            previous?.uncaughtException(thread, error)
        }
        handlerInstalled = true
    }

    fun acknowledgeCrashReport(context: Context) {
        synchronized(lock) {
            appendLocked(context.applicationContext, "CRASH_REPORT_ACKNOWLEDGED time=${Instant.now()}")
        }
    }

    fun searchStarted(context: Context, fen: String, depth: Int, timeMs: Int) {
        synchronized(lock) {
            appendLocked(context.applicationContext, "SEARCH_START time=${Instant.now()} depth=$depth timeMs=$timeMs fen=$fen")
        }
    }

    fun searchCompleted(context: Context, move: String?) {
        synchronized(lock) {
            appendLocked(context.applicationContext, "SEARCH_COMPLETE time=${Instant.now()} move=${move ?: "<null>"}")
        }
    }

    fun recordStage(context: Context, stage: String, details: String = "") {
        synchronized(lock) {
            appendLocked(context.applicationContext, "STAGE time=${Instant.now()} name=$stage details=${details.replace('\\n', ' ')}")
        }
    }

    fun recordException(context: Context, where: String, error: Throwable) {
        val sw = StringWriter()
        error.printStackTrace(PrintWriter(sw))
        synchronized(lock) {
            appendLocked(context.applicationContext, "JAVA_EXCEPTION time=${Instant.now()} where=$where")
            appendLocked(context.applicationContext, sw.toString())
        }
    }

    fun readReport(context: Context): String {
        val report = runCatching { reportFile(context.applicationContext).readText() }.getOrDefault("No diagnostic events have been recorded.")
        return buildString {
            appendLine("AETHERIS CRASH DIAGNOSTICS")
            appendLine("Generated: ${Instant.now()}")
            appendLine("App version: ${runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrDefault("unknown")}")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}; Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("ABIs: ${Build.SUPPORTED_ABIS.joinToString()}")
            appendLine("Java VM: ${System.getProperty("java.vm.name")} ${System.getProperty("java.vm.version")}")
            appendLine()
            append(report)
        }
    }

    private fun appendLocked(context: Context, line: String) {
        runCatching {
            reportFile(context).parentFile?.mkdirs()
            FileOutputStream(reportFile(context), true).use { stream ->
                stream.write((line + "\n").toByteArray(Charsets.UTF_8))
                stream.fd.sync()
            }
        }
    }
}
