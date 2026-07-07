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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

internal class OpenCapDownCoreImpl(
    override val version: String,
    private val sourceManager: SourceManager,
    private val downloadManager: DownloadManager,
    private val telegramSync: TelegramSync,
    private val readerEngine: ReaderEngine,
    private val settingDao: SettingDao,
    private val libraryMangaDao: LibraryMangaDao,
    private val telegramConfigProvider: TelegramConfigProvider,
    private val chapterDao: ChapterDao
) : OpenCapDownCore {

    override suspend fun getSources(): List<SourceInfo> =
        sourceManager.listSources().map { SourceInfo(it.id, it.name, it.lang) }

    override suspend fun search(query: String): List<SearchResult> =
        sourceManager.listSources().flatMap { source ->
            when (val result = sourceManager.search(source.id, query)) {
                is OpenCapDownResult.Success -> result.data
                is OpenCapDownResult.Failure -> emptyList()
            }
        }

    override suspend fun search(sourceId: String, query: String): List<SearchResult> =
        when (val result = sourceManager.search(sourceId, query)) {
            is OpenCapDownResult.Success -> result.data
            is OpenCapDownResult.Failure -> emptyList()
        }

    override suspend fun getMangaDetail(sourceId: String, mangaUrl: String): MangaDetail =
        when (val result = sourceManager.getMangaDetail(sourceId, mangaUrl)) {
            is OpenCapDownResult.Success -> result.data
            is OpenCapDownResult.Failure -> throw IllegalStateException(result.error.toString())
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
        return mapOf(
            "version" to version,
            "botToken" to botToken,
            "chatId" to chatId
        )
    }

    override suspend fun updateTelegramConfig(botToken: String, chatId: String) {
        val id = chatId.toLongOrNull() ?: 0L
        telegramConfigProvider.setConfig(botToken, id)
    }
}
