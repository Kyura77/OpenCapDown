package com.opencapdown.core

import com.opencapdown.core.common.OpenCapDownResult
import com.opencapdown.core.database.daos.LibraryMangaDao
import com.opencapdown.core.database.daos.SettingDao
import com.opencapdown.core.database.entities.LibraryMangaEntity
import com.opencapdown.core.database.entities.SettingEntity
import com.opencapdown.core.domain.models.*
import com.opencapdown.core.downloads.DownloadManager
import com.opencapdown.core.reader.ReaderEngine
import com.opencapdown.core.sources.SourceManager
import com.opencapdown.core.telegram.TelegramSync
import com.opencapdown.core.telegram.TelegramConfigProvider
import com.opencapdown.core.database.daos.ChapterDao
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

internal class OpenCapDownCoreImpl(
    override val version: String,
    private val sourceManager: SourceManager,
    private val downloadManager: DownloadManager,
    private val telegramSync: TelegramSync,
    private val readerEngine: ReaderEngine,
    private val settingDao: SettingDao,
    private val libraryMangaDao: LibraryMangaDao,
    private val telegramConfigProvider: TelegramConfigProvider,
    private val chapterDao: ChapterDao,
    private val client: OkHttpClient
) : OpenCapDownCore {

    override suspend fun getSources(): List<SourceInfo> =
        sourceManager.listSources().map { SourceInfo(it.id, it.name, it.lang) }

    override suspend fun search(query: String): List<SearchResult> = kotlinx.coroutines.coroutineScope {
        sourceManager.listSources().map { source ->
            async {
                try {
                    kotlinx.coroutines.withTimeoutOrNull(3000L) {
                        when (val result = sourceManager.search(source.id, query)) {
                            is com.opencapdown.core.common.OpenCapDownResult.Success -> result.data
                            is com.opencapdown.core.common.OpenCapDownResult.Failure -> emptyList()
                        }
                    } ?: emptyList()
                } catch (e: Exception) {
                    e.printStackTrace()
                    emptyList()
                }
            }
        }.awaitAll().flatten()
    }

    override suspend fun search(sourceId: String, query: String): List<SearchResult> =
        when (val result = sourceManager.search(sourceId, query)) {
            is OpenCapDownResult.Success -> result.data
            is OpenCapDownResult.Failure -> emptyList()
        }

    private val mangaDetailCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, MangaDetail>>()

    override suspend fun getMangaDetail(sourceId: String, mangaUrl: String): MangaDetail {
        val cacheKey = "$sourceId|$mangaUrl"
        val cached = mangaDetailCache[cacheKey]
        val now = System.currentTimeMillis()
        if (cached != null && (now - cached.first) < 5 * 60 * 1000L) { // 5 minutos de cache
            return cached.second
        }
        val result = when (val res = sourceManager.getMangaDetail(sourceId, mangaUrl)) {
            is OpenCapDownResult.Success -> res.data
            is OpenCapDownResult.Failure -> throw IllegalStateException(res.error.toString())
        }
        mangaDetailCache[cacheKey] = Pair(now, result)
        return result
    }

    override suspend fun getChapterPages(sourceId: String, chapterUrl: String): List<PageResult> =
        when (val result = sourceManager.getChapterPages(sourceId, chapterUrl)) {
            is OpenCapDownResult.Success -> result.data
            is OpenCapDownResult.Failure -> emptyList()
        }

    override suspend fun getLibrary(): List<LibraryManga> =
        libraryMangaDao.observeAll().first().map { entity ->
            LibraryManga(
                id = entity.id,
                sourceId = entity.sourceId,
                title = entity.title,
                coverUrl = entity.coverUrl,
                status = entity.status,
                telegramTopicId = entity.telegramTopicId,
                mangaUrl = entity.mangaUrl
            )
        }

    override suspend fun addToLibrary(manga: MangaDetail) {
        libraryMangaDao.insert(
            LibraryMangaEntity(
                id = "${manga.sourceId}-${manga.title.hashCode()}",
                sourceId = manga.sourceId,
                title = manga.title,
                coverUrl = manga.coverUrl,
                status = manga.status,
                telegramTopicId = null,
                mangaUrl = manga.url
            )
        )
    }

    override suspend fun removeFromLibrary(mangaId: String) =
        libraryMangaDao.delete(mangaId)

    override suspend fun downloadChapter(mangaId: String, chapterId: String) =
        downloadManager.enqueueChapter(mangaId, chapterId)

    override fun observeDownloadQueue(): Flow<List<DownloadJob>> =
        downloadManager.observeQueue()

    override suspend fun cancelDownload(jobId: String) =
        downloadManager.cancel(jobId)

    override suspend fun backupChapter(chapterId: String): Result<Unit> {
        val chapter = readerEngine.getChapter(chapterId).chapter
        val manga = libraryMangaDao.getById(chapter.mangaId)
        val title = manga?.title ?: "Manga ${chapter.mangaId}"
        return telegramSync.backupChapter(chapterId, title)
    }

    override suspend fun listTelegramBackups(mangaId: String): List<TelegramBackup> =
        telegramSync.listBackups(mangaId)

    override suspend fun restoreChapter(messageId: Long): Result<Unit> {
        val chapter = chapterDao.getByTelegramMessageId(messageId)
            ?: return Result.failure(IllegalArgumentException("Backup not found for message: $messageId"))
        return telegramSync.restoreChapter(chapter.id, chapter.mangaId)
    }

    override suspend fun getChapter(chapterId: String): ChapterWithPages =
        readerEngine.getChapter(chapterId)

    override suspend fun markAsRead(chapterId: String) =
        readerEngine.markAsRead(chapterId)

    override suspend fun getReadingProgress(mangaId: String): ReadingProgress? =
        readerEngine.getReadingProgress(mangaId)

    override suspend fun updateReadingProgress(mangaId: String, chapterId: String, pageIndex: Int) =
        readerEngine.updateProgress(mangaId, chapterId, pageIndex)

    override suspend fun getSettings(): Map<String, String> {
        val botToken = telegramConfigProvider.getBotToken() ?: ""
        val chatId = telegramConfigProvider.getChatId()?.toString() ?: ""
        val verdinhaToken = settingDao.get("verdinhaToken") ?: ""
        val verdinhaMode = settingDao.get("verdinhaMode") ?: "cdn"
        return mapOf(
            "version" to version,
            "botToken" to botToken,
            "chatId" to chatId,
            "telegram_bot_token" to botToken,
            "telegram_chat_id" to chatId,
            "verdinha_token" to verdinhaToken,
            "verdinha_mode" to verdinhaMode
        )
    }

    override suspend fun updateTelegramConfig(botToken: String, chatId: String) {
        val id = chatId.toLongOrNull() ?: return
        telegramConfigProvider.setConfig(botToken, id)
    }

    override suspend fun updateVerdinhaConfig(token: String) {
        settingDao.set(SettingEntity("verdinhaToken", token))
    }

    override suspend fun updateVerdinhaMode(mode: String) {
        settingDao.set(SettingEntity("verdinhaMode", mode))
    }

    override suspend fun loginVerdinha(login: String, senha: String): Result<Unit> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val requestBodyJson = JSONObject().apply {
            put("login", login)
            put("senha", senha)
            put("tipo_usuario", "usuario")
        }.toString()

        val mediaType = "application/json".toMediaTypeOrNull()
        val body = requestBodyJson.toRequestBody(mediaType)
        val request = Request.Builder()
            .url("https://api.verdinha.wtf/auth/login")
            .post(body)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val resString = response.body?.string() ?: ""
                    val json = JSONObject(resString)
                    val token = if (json.has("access_token")) {
                        json.getString("access_token")
                    } else if (json.has("token")) {
                        json.getString("token")
                    } else {
                        return@withContext Result.failure(IllegalStateException("Token de acesso não encontrado na resposta"))
                    }
                    settingDao.set(SettingEntity("verdinhaToken", token))
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("Falha no login: código ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
