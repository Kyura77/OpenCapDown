package com.opencapdown.core

import com.opencapdown.core.domain.models.*
import kotlinx.coroutines.flow.Flow

interface OpenCapDownCore {
    val version: String

    suspend fun getSources(): List<SourceInfo>
    suspend fun search(query: String): List<SearchResult>
    suspend fun search(sourceId: String, query: String): List<SearchResult>
    suspend fun getMangaDetail(sourceId: String, mangaUrl: String): MangaDetail
    suspend fun getChapterPages(sourceId: String, chapterUrl: String): List<PageResult>

    suspend fun getLibrary(): List<LibraryManga>
    suspend fun addToLibrary(manga: MangaDetail)
    suspend fun removeFromLibrary(mangaId: String)

    suspend fun downloadChapter(mangaId: String, chapterId: String)
    fun observeDownloadQueue(): Flow<List<DownloadJob>>
    suspend fun cancelDownload(jobId: String)
    suspend fun processDownloadQueue()

    suspend fun backupChapter(chapterId: String): Result<Unit>
    suspend fun listTelegramBackups(mangaId: String): List<TelegramBackup>
    suspend fun restoreChapter(messageId: Long): Result<Unit>

    suspend fun getChapter(chapterId: String): ChapterWithPages
    suspend fun resolvePage(page: com.opencapdown.core.domain.models.Page, sourceId: String, chapterUrl: String): Any
    suspend fun markAsRead(chapterId: String)
    suspend fun getReadingProgress(mangaId: String): ReadingProgress?
    suspend fun updateReadingProgress(mangaId: String, chapterId: String, pageIndex: Int)

    suspend fun getSettings(): Map<String, String>
    suspend fun updateTelegramConfig(botToken: String, chatId: String)
    suspend fun updateVerdinhaConfig(token: String)
    suspend fun updateVerdinhaMode(mode: String)
    suspend fun loginVerdinha(login: String, senha: String): Result<Unit>
}

