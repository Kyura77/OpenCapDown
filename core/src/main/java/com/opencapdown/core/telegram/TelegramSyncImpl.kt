package com.opencapdown.core.telegram

import com.opencapdown.core.database.daos.ChapterDao
import com.opencapdown.core.database.daos.LibraryMangaDao
import com.opencapdown.core.database.daos.PageDao
import com.opencapdown.core.database.entities.ChapterEntity
import com.opencapdown.core.domain.models.TelegramBackup
import kotlinx.coroutines.flow.first
import java.io.File

internal class TelegramSyncImpl(
    private val apiClient: TelegramApiClient,
    private val topicManager: TelegramTopicManager,
    private val configProvider: TelegramConfigProvider,
    private val chapterDao: ChapterDao,
    private val pageDao: PageDao,
    private val mangaDao: LibraryMangaDao,
    private val cacheDir: File
) : TelegramSync {

    override suspend fun backupChapter(chapterId: String, mangaTitle: String): Result<Unit> {
        return try {
            val botToken = configProvider.getBotToken()
                ?: return Result.failure(TelegramConfigException("Bot token not configured"))
            val chatId = configProvider.getChatId()
                ?: return Result.failure(TelegramConfigException("Chat ID not configured"))

            val chapter = chapterDao.getById(chapterId)
                ?: return Result.failure(IllegalArgumentException("Chapter not found: $chapterId"))

            if (chapter.telegramAlbumMessageId != null) {
                return Result.success(Unit)
            }

            val pages = pageDao.getByChapter(chapterId)
            if (pages.isEmpty()) {
                return Result.failure(IllegalArgumentException("No pages to backup for chapter: $chapterId"))
            }

            val topicId = topicManager.ensureTopic(chapter.mangaId, mangaTitle)

            val allMessageIds = mutableListOf<Long>()

            pages.chunked(10).forEach { batch ->
                val mediaItems = batch.map { page ->
                    val path = page.localPath
                    if (path == null) {
                        TelegramMediaItem(
                            caption = "Page ${page.index + 1} (missing file)"
                        )
                    } else {
                        TelegramMediaItem(
                            imageFile = File(path),
                            caption = page.index + 1
                        )
                    }
                }

                val captionBase = "<b>${chapter.title}</b>"
                val captionPrefix = if (batch.size < pages.size) {
                    val batchIndex = pages.chunked(10).indexOf(batch) + 1
                    "$captionBase (part $batchIndex)"
                } else {
                    captionBase
                }

                val itemsWithCaption = mediaItems.toMutableList()
                if (itemsWithCaption.isNotEmpty()) {
                    itemsWithCaption[0] = itemsWithCaption[0].copy(
                        caption = captionPrefix
                    )
                }

                val validItems = itemsWithCaption.filter { it.hasImage() }

                if (validItems.isEmpty()) {
                    apiClient.sendMessage(botToken, chatId, captionPrefix, topicId)
                    continue
                }

                val messages = apiClient.sendMediaGroup(
                    botToken = botToken,
                    chatId = chatId,
                    topicId = topicId,
                    media = validItems
                )
                allMessageIds.addAll(messages.map { it.messageId })
            }

            if (allMessageIds.isNotEmpty()) {
                chapterDao.updateStatus(chapterId, "BACKED_UP")
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun listBackups(mangaId: String): List<TelegramBackup> {
        return chapterDao.observeByManga(mangaId).first()
            .filter { it.integrityStatus == "BACKED_UP" || it.telegramAlbumMessageId != null }
            .map {
                TelegramBackup(
                    messageId = it.telegramAlbumMessageId ?: 0L,
                    chapterTitle = it.title,
                    pageCount = it.pageCount,
                    createdAt = 0L
                )
            }
    }

    override suspend fun restoreChapter(chapterId: String, mangaId: String): Result<Unit> {
        return try {
            val botToken = configProvider.getBotToken()
                ?: return Result.failure(TelegramConfigException("Bot token not configured"))

            val pages = pageDao.getByChapter(chapterId)
            if (pages.isEmpty()) {
                return Result.failure(IllegalArgumentException("No pages found for chapter: $chapterId"))
            }

            chapterDao.updateStatus(chapterId, "PENDING")

            for (page in pages) {
                val fileId = page.telegramFileId ?: continue
                val bytes = apiClient.getFileBytes(botToken, fileId)

                val pageDir = File(cacheDir, "restore/$chapterId")
                pageDir.mkdirs()
                val destFile = File(pageDir, "page_${page.index + 1}.jpg")
                destFile.writeBytes(bytes)

                pageDao.updateLocalPath(page.id, destFile.absolutePath)
            }

            chapterDao.updateStatus(chapterId, "COMPLETE")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

private fun TelegramMediaItem.copy(caption: String?): TelegramMediaItem {
    return TelegramMediaItem(
        imageBytes = this.imageBytes,
        imageFile = this.imageFile,
        caption = caption
    )
}
