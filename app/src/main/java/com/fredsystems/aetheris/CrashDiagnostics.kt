package com.fredsystems.aetheris

import android.content.Context
import android.os.Build
import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant

/**
 * Diagnostic breadcrumbs are persisted before entering native code so they survive
 * a SIGSEGV/abort that prevents the Kotlin exception handler from running.
 */
object CrashDiagnostics {
    private const val FILE_NAME = "aetheris_crash_report.txt"
    private val lock = Any()

    private fun reportFile(context: Context) = File(context.filesDir, FILE_NAME)

    fun initialize(context: Context): Boolean {
        val app = context.applicationContext
        synchronized(lock) {
            val file = reportFile(app)
            val previous = runCatching { file.readText() }.getOrDefault("")
            val lastStart = previous.lastIndexOf("SEARCH_START")
            val lastDone = previous.lastIndexOf("SEARCH_COMPLETE")
            val lastAcknowledged = previous.lastIndexOf("CRASH_REPORT_ACKNOWLEDGED")
            val crashPending = lastStart > maxOf(lastDone, lastAcknowledged)
            if (crashPending) {
                appendLocked(app, "SUSPECTED_NATIVE_CRASH: previous search started but never recorded completion")
            }
            appendLocked(app, "APP_START time=${Instant.now()} version=${app.packageManager.getPackageInfo(app.packageName, 0).versionName} package=${app.packageName} device=${Build.MANUFACTURER} ${Build.MODEL} android=${Build.VERSION.RELEASE} sdk=${Build.VERSION.SDK_INT}")
            return crashPending
        }
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

    fun recordException(context: Context, where: String, error: Throwable) {
        val sw = StringWriter()
        error.printStackTrace(PrintWriter(sw))
        synchronized(lock) {
            appendLocked(context.applicationContext, "JAVA_EXCEPTION time=${Instant.now()} where=$where\n$sw")
        }
    }

    fun readReport(context: Context): String {
        val report = runCatching { reportFile(context.applicationContext).readText() }.getOrDefault("")
        return "Aetheris crash diagnostics\nGenerated: ${Instant.now()}\n\n$report"
    }

    private fun appendLocked(context: Context, line: String) {
        runCatching {
            FileOutputStream(reportFile(context), true).use { stream ->
                stream.write((line + "\n").toByteArray(Charsets.UTF_8))
                stream.fd.sync()
            }
        }
    }
}
