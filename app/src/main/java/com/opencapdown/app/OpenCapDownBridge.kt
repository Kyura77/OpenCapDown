package com.opencapdown.app

import android.util.Base64
import android.webkit.JavascriptInterface
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
    private val core: OpenCapDownCore,
    private val client: OkHttpClient,
    private val cacheDir: File
) {
    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

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

    private fun <T> wrap(block: suspend () -> T): String {
        return try {
            val data = block()
            gson.toJson(mapOf("ok" to true, "data" to data))
        } catch (e: Exception) {
            gson.toJson(mapOf("ok" to false, "error" to (e.message ?: "Unknown error")))
        }
    }

    private fun <T> wrapNullable(block: suspend () -> T?): String {
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

    private fun wrapResult(block: suspend () -> Result<Unit>): String {
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
