package com.opencapdown.core.reader

import com.opencapdown.core.domain.models.ChapterWithPages
import com.opencapdown.core.domain.models.ReadingProgress

interface ReaderEngine {
    suspend fun getChapter(chapterId: String): ChapterWithPages
    suspend fun resolvePage(page: com.opencapdown.core.domain.models.Page, sourceId: String, chapterUrl: String): Any
    suspend fun markAsRead(chapterId: String)
    suspend fun getReadingProgress(mangaId: String): ReadingProgress?
    suspend fun updateProgress(mangaId: String, chapterId: String, pageIndex: Int)
}

