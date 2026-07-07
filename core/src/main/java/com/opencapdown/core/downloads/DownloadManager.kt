package com.opencapdown.core.downloads

import com.opencapdown.core.domain.models.DownloadJob
import kotlinx.coroutines.flow.Flow

interface DownloadManager {
    suspend fun enqueueChapter(mangaId: String, chapterId: String)
    fun observeQueue(): Flow<List<DownloadJob>>
    suspend fun cancel(jobId: String)
}
