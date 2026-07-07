package com.opencapdown.core.reader

import com.opencapdown.core.database.daos.ChapterDao
import com.opencapdown.core.database.daos.PageDao
import com.opencapdown.core.database.entities.toDomain
import com.opencapdown.core.domain.models.ChapterWithPages
import com.opencapdown.core.domain.models.Page
import com.opencapdown.core.domain.models.ReadingProgress

internal class ReaderEngineImpl(
    private val chapterDao: ChapterDao,
    private val pageDao: PageDao,
    private val pageResolver: PageResolver,
    private val progressTracker: ReadingProgressTracker
) : ReaderEngine {

    override suspend fun getChapter(chapterId: String): ChapterWithPages {
        val chapter = chapterDao.getById(chapterId)?.toDomain()
            ?: throw IllegalStateException("Chapter not found: $chapterId")
        val pages = pageDao.getByChapter(chapterId).map { entity ->
            Page(
                id = entity.id,
                chapterId = entity.chapterId,
                index = entity.index,
                localPath = entity.localPath,
                telegramFileId = entity.telegramFileId,
                telegramMessageId = entity.telegramMessageId
            )
        }
        return ChapterWithPages(chapter, pages)
    }

    override suspend fun markAsRead(chapterId: String) {
        chapterDao.updateRead(chapterId, true)
    }

    override suspend fun getReadingProgress(mangaId: String): ReadingProgress? {
        return progressTracker.load(mangaId)
    }

    override suspend fun updateProgress(mangaId: String, chapterId: String, pageIndex: Int) {
        progressTracker.save(mangaId, chapterId, pageIndex)
    }
}
