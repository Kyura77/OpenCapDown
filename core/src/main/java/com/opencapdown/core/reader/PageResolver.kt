package com.opencapdown.core.reader

import com.opencapdown.core.domain.models.Page
import com.opencapdown.core.sources.SourceManager
import com.opencapdown.core.telegram.TelegramSync
import java.io.File

internal class PageResolver(
    private val sourceManager: SourceManager,
    private val telegramSync: TelegramSync,
    private val cacheDir: File
) {
    suspend fun resolve(page: Page, chapterUrl: String): File {
        page.localPath?.let { return File(it) }
        page.telegramFileId?.let {
            telegramSync.restoreChapter(page.chapterId, "").getOrThrow()
            return File(page.localPath!!)
        }
        val pagesResult = sourceManager.getChapterPages("", chapterUrl)
        throw IllegalStateException("No local or telegram source for page ${page.id}")
    }
}
