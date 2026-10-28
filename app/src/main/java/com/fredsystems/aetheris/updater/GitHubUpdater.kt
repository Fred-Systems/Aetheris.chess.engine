package com.fredsystems.aetheris.updater

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.FileProvider
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File

class GitHubUpdater(private val context: Context) {
    private val client = OkHttpClient()
    private val repo = "Fred-Systems/Aetheris.chess.engine"

    suspend fun fetchLatest(): JSONObject? = withContext(Dispatchers.IO) {
        // Do not depend on GitHub's mutable /releases/latest pointer: an older
        // version-specific workflow can accidentally mark its old release latest.
        // Pick the highest published stable semantic version from the release list.
        val url = "https://api.github.com/repos/$repo/releases?per_page=50"
        val req = Request.Builder()
            .url(url)
            .header("Cache-Control", "no-cache")
            .header("Accept", "application/vnd.github+json")
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext null
            val releases = org.json.JSONArray(resp.body!!.string())
            (0 until releases.length())
                .map { releases.getJSONObject(it) }
                .filter { !it.optBoolean("draft", true) && !it.optBoolean("prerelease", true) }
                .filter { it.optJSONArray("assets")?.let { assets ->
                    (0 until assets.length()).any { i -> assets.getJSONObject(i).optString("name").endsWith(".apk") }
                } == true }
                .maxByOrNull { versionSortKey(it.optString("tag_name")) }
        }
    }

    private fun versionSortKey(tag: String): Long {
        val parts = tag.trimStart('v', 'V').split(".")
        if (parts.isEmpty() || parts.any { it.toIntOrNull() == null }) return -1L
        return parts.take(3).fold(0L) { acc, part -> acc * 1000L + part.toInt() }
    }

    // Releases use semantic version tags (v1.0.4), so compare the same
    // normalized version representation on both sides rather than Android's
    // monotonically increasing integer versionCode.
    fun currentVersionCode(): Long {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return info.versionName?.trimStart('v')?.replace(".", "")?.toLongOrNull()
            ?: info.longVersionCode
    }

    suspend fun checkForUpdate(): Pair<Boolean, String?> = withContext(Dispatchers.IO) {
        val json = fetchLatest() ?: return@withContext Pair(false, null)
        val tag = json.optString("tag_name")
        val remoteCode = tag.trimStart('v').replace(".", "").toLongOrNull() ?: 0L
        val isNewer = remoteCode > currentVersionCode()
        val apkUrl = json.optJSONArray("assets")?.let { arr ->
            for (i in 0 until arr.length()) {
                val a = arr.getJSONObject(i)
                if (a.optString("name").endsWith(".apk")) return@let a.optString("browser_download_url")
            }
            null
        }
        Pair(isNewer, apkUrl)
    }

    fun downloadAndInstall(apkUrl: String) {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val target = File(context.cacheDir, "aetheris-update.apk")
            runCatching {
                val request = Request.Builder().url(apkUrl).header("Accept", "application/vnd.android.package-archive").build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) error("Download failed (HTTP ${response.code})")
                    val body = response.body ?: error("The release returned an empty APK")
                    body.byteStream().use { input -> target.outputStream().use { output -> input.copyTo(output) } }
                }
                if (target.length() < 100_000L) error("Downloaded APK is too small to be valid")
                if (!hasMatchingSigner(target)) {
                    error("Android cannot update this installation because the APK signing certificate differs. Uninstall the old CI-signed build once, then install the new APK. Configure a stable private signing key for future in-place updates.")
                }
                withContext(Dispatchers.Main) { installApk(target) }
            }.onFailure { error ->
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Aetheris update failed: ${error.message ?: "unknown error"}", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    @Suppress("DEPRECATION")
    private fun hasMatchingSigner(apk: File): Boolean {
        val pm = context.packageManager
        val installed = if (Build.VERSION.SDK_INT >= 28) {
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        } else {
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
        }
        val archive = if (Build.VERSION.SDK_INT >= 28) {
            pm.getPackageArchiveInfo(apk.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
        } else {
            pm.getPackageArchiveInfo(apk.absolutePath, PackageManager.GET_SIGNATURES)
        } ?: return false
        if (archive.packageName != context.packageName) return false
        return if (Build.VERSION.SDK_INT >= 28) {
            val installedSigners = installed.signingInfo?.apkContentsSigners?.map { it.toCharsString() }?.toSet()
            val archiveSigners = archive.signingInfo?.apkContentsSigners?.map { it.toCharsString() }?.toSet()
            !installedSigners.isNullOrEmpty() && installedSigners == archiveSigners
        } else {
            installed.signatures?.map { it.toCharsString() }?.toSet() ==
                archive.signatures?.map { it.toCharsString() }?.toSet()
        }
    }

    fun installApk(file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(intent)
    }
}