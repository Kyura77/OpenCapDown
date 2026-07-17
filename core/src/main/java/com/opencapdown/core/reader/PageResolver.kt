package com.opencapdown.core.reader

import com.opencapdown.core.database.daos.PageDao
import com.opencapdown.core.domain.models.Page
import com.opencapdown.core.sources.SourceManager
import com.opencapdown.core.telegram.TelegramSync
import com.opencapdown.core.common.OpenCapDownResult
import java.io.File

internal class PageResolver(
    private val sourceManager: SourceManager,
    private val telegramSync: TelegramSync,
    private val pageDao: PageDao,
    private val cacheDir: File
) {
    suspend fun resolve(page: Page, sourceId: String, chapterUrl: String): Any {
        val translatedDir = File(cacheDir.parentFile, "translated/${page.chapterId}")
        val translatedJpg = File(translatedDir, "page_${page.index + 1}.jpg")
        val translatedPng = File(translatedDir, "page_${page.index + 1}.png")
        if (translatedJpg.exists()) return translatedJpg
        if (translatedPng.exists()) return translatedPng

        page.localPath?.let { path ->
            val file = File(path)
            if (file.exists()) return file
        }
        
        if (page.telegramFileId != null) {
            try {
                telegramSync.restoreChapter(page.chapterId, "").getOrThrow()
                // Fetch the updated local path from DB
                val restoredPage = pageDao.getByChapter(page.chapterId).firstOrNull { it.id == page.id }
                restoredPage?.localPath?.let { path ->
                    val file = File(path)
                    if (file.exists()) return file
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        // Fallback to streaming source URL
        val pagesResult = sourceManager.getChapterPages(sourceId, chapterUrl)
        val sourcePages = when (pagesResult) {
            is OpenCapDownResult.Success -> pagesResult.data
            is OpenCapDownResult.Failure -> throw IllegalStateException("Failed to load source pages: ${pagesResult.error}")
        }
        val matchingPage = sourcePages.firstOrNull { it.index == page.index }
        return matchingPage?.imageUrl ?: throw IllegalStateException("No local, Telegram, or source URL for page ${page.id}")
    }
}
