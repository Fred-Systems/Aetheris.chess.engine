package com.fredsystems.aetheris.updater

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
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
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val req = DownloadManager.Request(Uri.parse(apkUrl))
            .setTitle("Aetheris Update")
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "aetheris-update.apk")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        dm.enqueue(req)
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