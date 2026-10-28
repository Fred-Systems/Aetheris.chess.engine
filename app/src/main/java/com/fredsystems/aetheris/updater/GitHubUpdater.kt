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
        val url = "https://api.github.com/repos/$repo/releases/latest"
        val req = Request.Builder().url(url).build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext null
            JSONObject(resp.body!!.string())
        }
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
    private fun hasMatchingSigner(apk: File): Boolean {
        val pm = context.packageManager
        val installed = if (Build.VERSION.SDK_INT >= 28) {
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
        }
        val archive = if (Build.VERSION.SDK_INT >= 28) {
            pm.getPackageArchiveInfo(apk.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageArchiveInfo(apk.absolutePath, PackageManager.GET_SIGNATURES)
        } ?: return false
        if (archive.packageName != context.packageName) return false
        return if (Build.VERSION.SDK_INT >= 28) {
            val installedSigners = installed.signingInfo?.apkContentsSigners?.map { it.toCharsString() }?.toSet()
            val archiveSigners = archive.signingInfo?.apkContentsSigners?.map { it.toCharsString() }?.toSet()
            !installedSigners.isNullOrEmpty() && installedSigners == archiveSigners
        } else {
            @Suppress("DEPRECATION")
            installed.signatures?.map { it.toCharsString() }?.toSet() ==
                @Suppress("DEPRECATION")
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