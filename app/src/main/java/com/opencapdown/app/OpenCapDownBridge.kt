package com.opencapdown.app

import android.content.Context
import android.content.Intent
import android.util.Base64
import android.webkit.JavascriptInterface
import androidx.core.content.FileProvider
import com.google.gson.Gson
import com.opencapdown.core.OpenCapDownCore
import com.opencapdown.core.domain.models.*
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.net.URL

internal class OpenCapDownBridge(
    private val context: Context,
    private val core: OpenCapDownCore,
    private val client: OkHttpClient,
    private val cacheDir: File
) {
    companion object {
        private const val DEFAULT_GH_OWNER = "opencapdown"
        private const val DEFAULT_GH_REPO = "opencapdown"
        private const val UPDATE_APK_NAME = "opencapdown-update.apk"
    }
    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val prefs = context.getSharedPreferences("opencapdown_prefs", Context.MODE_PRIVATE)

    @Volatile
    private var cachedDownloadQueueJson = "[]"

    init {
        scope.launch {
            core.observeDownloadQueue().collect { queue ->
                cachedDownloadQueueJson = gson.toJson(
                    queue.map { DownloadJobJson(it.id, it.chapterId, it.status.name, it.progress, it.errorMessage) }
                )
            }
        }
    }

    fun destroy() {
        scope.cancel()
    }

    @JavascriptInterface
    fun search(query: String): String = runBlocking(Dispatchers.IO) {
        wrap { core.search(query) }
    }

    @JavascriptInterface
    fun getMangaDetail(sourceId: String, mangaUrl: String): String = runBlocking(Dispatchers.IO) {
        wrap { core.getMangaDetail(sourceId, mangaUrl) }
    }

    @JavascriptInterface
    fun getChapterPages(sourceId: String, chapterUrl: String): String = runBlocking(Dispatchers.IO) {
        wrap { core.getChapterPages(sourceId, chapterUrl) }
    }

    @JavascriptInterface
    fun getLibrary(): String = runBlocking(Dispatchers.IO) {
        wrap { core.getLibrary() }
    }

    @JavascriptInterface
    fun addToLibrary(json: String): String = runBlocking(Dispatchers.IO) {
        try {
            val detail = gson.fromJson(json, MangaDetail::class.java)
            core.addToLibrary(detail)
            gson.toJson(mapOf("ok" to true))
        } catch (e: Exception) {
            gson.toJson(mapOf("ok" to false, "error" to (e.message ?: "")))
        }
    }

    @JavascriptInterface
    fun removeFromLibrary(mangaId: String): String = runBlocking(Dispatchers.IO) {
        try {
            core.removeFromLibrary(mangaId)
            gson.toJson(mapOf("ok" to true))
        } catch (e: Exception) {
            gson.toJson(mapOf("ok" to false, "error" to (e.message ?: "")))
        }
    }

    @JavascriptInterface
    fun downloadChapter(mangaId: String, chapterId: String): String = runBlocking(Dispatchers.IO) {
        try {
            core.downloadChapter(mangaId, chapterId)
            gson.toJson(mapOf("ok" to true))
        } catch (e: Exception) {
            gson.toJson(mapOf("ok" to false, "error" to (e.message ?: "")))
        }
    }

    @JavascriptInterface
    fun getDownloadQueue(): String = cachedDownloadQueueJson

    @JavascriptInterface
    fun cancelDownload(jobId: String): String = runBlocking(Dispatchers.IO) {
        try {
            core.cancelDownload(jobId)
            gson.toJson(mapOf("ok" to true))
        } catch (e: Exception) {
            gson.toJson(mapOf("ok" to false, "error" to (e.message ?: "")))
        }
    }

    @JavascriptInterface
    fun backupChapter(chapterId: String): String = runBlocking(Dispatchers.IO) {
        wrapResult { core.backupChapter(chapterId) }
    }

    @JavascriptInterface
    fun listTelegramBackups(mangaId: String): String = runBlocking(Dispatchers.IO) {
        wrap { core.listTelegramBackups(mangaId) }
    }

    @JavascriptInterface
    fun restoreChapter(messageId: Long): String = runBlocking(Dispatchers.IO) {
        wrapResult { core.restoreChapter(messageId) }
    }

    @JavascriptInterface
    fun getChapter(chapterId: String): String = runBlocking(Dispatchers.IO) {
        wrap { core.getChapter(chapterId) }
    }

    @JavascriptInterface
    fun markAsRead(chapterId: String): String = runBlocking(Dispatchers.IO) {
        try {
            core.markAsRead(chapterId)
            gson.toJson(mapOf("ok" to true))
        } catch (e: Exception) {
            gson.toJson(mapOf("ok" to false, "error" to (e.message ?: "")))
        }
    }

    @JavascriptInterface
    fun getReadingProgress(mangaId: String): String = runBlocking(Dispatchers.IO) {
        wrapNullable { core.getReadingProgress(mangaId) }
    }

    @JavascriptInterface
    fun updateReadingProgress(mangaId: String, chapterId: String, pageIndex: Int): String = runBlocking(Dispatchers.IO) {
        try {
            core.updateReadingProgress(mangaId, chapterId, pageIndex)
            gson.toJson(mapOf("ok" to true))
        } catch (e: Exception) {
            gson.toJson(mapOf("ok" to false, "error" to (e.message ?: "")))
        }
    }

    @JavascriptInterface
    fun getSettings(): String = runBlocking(Dispatchers.IO) {
        wrap { core.getSettings() }
    }

    @JavascriptInterface
    fun updateTelegramConfig(botToken: String, chatId: String): String = runBlocking(Dispatchers.IO) {
        try {
            core.updateTelegramConfig(botToken, chatId)
            gson.toJson(mapOf("ok" to true))
        } catch (e: Exception) {
            gson.toJson(mapOf("ok" to false, "error" to (e.message ?: "")))
        }
    }

    @JavascriptInterface
    fun getAppVersion(): String = gson.toJson(mapOf("ok" to true, "data" to mapOf(
        "versionName" to BuildConfig.VERSION_NAME,
        "versionCode" to BuildConfig.VERSION_CODE
    )))

    @JavascriptInterface
    fun getUpdateRepo(): String {
        val owner = prefs.getString("gh_owner", DEFAULT_GH_OWNER) ?: DEFAULT_GH_OWNER
        val repo = prefs.getString("gh_repo", DEFAULT_GH_REPO) ?: DEFAULT_GH_REPO
        return gson.toJson(mapOf("ok" to true, "data" to mapOf("owner" to owner, "repo" to repo)))
    }

    @JavascriptInterface
    fun setUpdateRepo(owner: String, repo: String): String {
        prefs.edit().putString("gh_owner", owner).putString("gh_repo", repo).apply()
        return gson.toJson(mapOf("ok" to true))
    }

    @JavascriptInterface
    fun checkForUpdate(): String {
        return try {
            val owner = prefs.getString("gh_owner", DEFAULT_GH_OWNER) ?: DEFAULT_GH_OWNER
            val repo = prefs.getString("gh_repo", DEFAULT_GH_REPO) ?: DEFAULT_GH_REPO

            val request = Request.Builder()
                .url("https://api.github.com/repos/$owner/$repo/releases/latest")
                .header("Accept", "application/vnd.github.v3+json")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return gson.toJson(mapOf("ok" to false, "error" to "Empty response"))
            val json = gson.fromJson(body, Map::class.java) as Map<String, Any?>

            val tagName = json["tag_name"] as? String ?: return gson.toJson(mapOf("ok" to false, "error" to "No tag_name"))
            val changelog = json["body"] as? String ?: ""
            val assets = json["assets"] as? List<Map<String, Any?>> ?: emptyList()
            val downloadUrl = assets.firstOrNull()?.get("browser_download_url") as? String ?: ""
            val size = (assets.firstOrNull()?.get("size") as? Number)?.toLong() ?: 0L

            val currentVer = BuildConfig.VERSION_NAME
            val latestVer = tagName.removePrefix("v")
            val hasUpdate = compareVersions(latestVer, currentVer) > 0

            gson.toJson(mapOf("ok" to true, "data" to mapOf(
                "hasUpdate" to hasUpdate,
                "currentVersion" to currentVer,
                "latestVersion" to latestVer,
                "tagName" to tagName,
                "downloadUrl" to downloadUrl,
                "changelog" to changelog,
                "size" to size
            )))
        } catch (e: Exception) {
            gson.toJson(mapOf("ok" to false, "error" to (e.message ?: "Check failed")))
        }
    }

    @JavascriptInterface
    fun installUpdate(downloadUrl: String): String {
        return try {
            val apkDir = File(cacheDir, "update").apply { mkdirs() }
            val apkFile = File(apkDir, UPDATE_APK_NAME)

            val request = Request.Builder().url(URL(downloadUrl)).build()
            val response = client.newCall(request).execute()
            val bytes = response.body?.bytes() ?: return gson.toJson(mapOf("ok" to false, "error" to "Download failed"))

            apkFile.outputStream().use { it.write(bytes) }

            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)

            gson.toJson(mapOf("ok" to true))
        } catch (e: Exception) {
            gson.toJson(mapOf("ok" to false, "error" to (e.message ?: "Install failed")))
        }
    }

    private fun compareVersions(a: String, b: String): Int {
        val partsA = a.split(".").map { it.toIntOrNull() ?: 0 }
        val partsB = b.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(partsA.size, partsB.size)) {
            val va = partsA.getOrElse(i) { 0 }
            val vb = partsB.getOrElse(i) { 0 }
            if (va != vb) return va - vb
        }
        return 0
    }

    @JavascriptInterface
    fun fetchImage(url: String, headersJson: String): String {
        return try {
            val headers = gson.fromJson(headersJson, Map::class.java) as? Map<String, String> ?: emptyMap()
            val requestBuilder = Request.Builder().url(URL(url))
            headers.forEach { (k, v) -> requestBuilder.header(k, v) }
            val response = client.newCall(requestBuilder.build()).execute()
            val bytes = response.body?.bytes() ?: return ""

            val mime = response.header("Content-Type") ?: "image/jpeg"
            val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            "data:$mime;base64,$b64"
        } catch (e: Exception) {
            ""
        }
    }

    @JavascriptInterface
    fun getLocalImage(path: String): String {
        return try {
            val file = File(path)
            if (!file.exists()) return ""
            val bytes = file.readBytes()
            val mime = when {
                path.endsWith(".png") -> "image/png"
                path.endsWith(".webp") -> "image/webp"
                else -> "image/jpeg"
            }
            val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            "data:$mime;base64,$b64"
        } catch (e: Exception) {
            ""
        }
    }

    private suspend fun <T> wrap(block: suspend () -> T): String {
        return try {
            val data = block()
            gson.toJson(mapOf("ok" to true, "data" to data))
        } catch (e: Exception) {
            gson.toJson(mapOf("ok" to false, "error" to (e.message ?: "Unknown error")))
        }
    }

    private suspend fun <T> wrapNullable(block: suspend () -> T?): String {
        return try {
            val data = block()
            if (data != null) {
                gson.toJson(mapOf("ok" to true, "data" to data))
            } else {
                gson.toJson(mapOf("ok" to true, "data" to null))
            }
        } catch (e: Exception) {
            gson.toJson(mapOf("ok" to false, "error" to (e.message ?: "Unknown error")))
        }
    }

    private suspend fun wrapResult(block: suspend () -> Result<Unit>): String {
        return try {
            val result = block()
            if (result.isSuccess) {
                gson.toJson(mapOf("ok" to true))
            } else {
                gson.toJson(mapOf("ok" to false, "error" to (result.exceptionOrNull()?.message ?: "Unknown error")))
            }
        } catch (e: Exception) {
            gson.toJson(mapOf("ok" to false, "error" to (e.message ?: "Unknown error")))
        }
    }
}

private data class DownloadJobJson(
    val id: String,
    val chapterId: String,
    val status: String,
    val progress: Int,
    val errorMessage: String?
)
