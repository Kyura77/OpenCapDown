package com.opencapdown.core.downloads

import com.opencapdown.core.domain.models.DownloadJob
import kotlinx.coroutines.flow.Flow
import java.io.File

internal class DownloadManagerImpl(
    private val imageDownloader: ImageDownloader,
    private val repository: DownloadRepository,
    private val cacheDir: File,
    private val constraints: DownloadConstraints = DownloadConstraints()
) : DownloadManager {
    override suspend fun enqueueChapter(mangaId: String, chapterId: String) {
        repository.createJob(chapterId)
        processQueue()
    }

    override fun observeQueue(): Flow<List<DownloadJob>> = repository.observeQueue()

    override suspend fun cancel(jobId: String) = repository.deleteJob(jobId)

    private suspend fun processQueue() {
        // Process one job at a time; WorkManager handles real background in a follow-up task
    }
}
