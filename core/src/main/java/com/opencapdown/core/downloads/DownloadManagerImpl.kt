package com.opencapdown.core.downloads

import android.content.Context
import androidx.work.*
import com.opencapdown.core.common.OpenCapDownResult
import com.opencapdown.core.domain.models.DownloadJob
import com.opencapdown.core.domain.models.DownloadStatus
import com.opencapdown.core.sources.SourceManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.util.concurrent.ThreadLocalRandom

internal class DownloadManagerImpl(
    private val context: Context,
    private val sourceManager: SourceManager,
    private val imageDownloader: ImageDownloader,
    private val repository: DownloadRepository,
    private val cacheDir: File,
    private val constraints: DownloadConstraints = DownloadConstraints()
) : DownloadManager {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var activeJobMutex = false

    override suspend fun enqueueChapter(mangaId: String, chapterId: String) {
        repository.createJob(chapterId)
        
        // Schedule WorkManager background task
        val workRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        
        try {
            WorkManager.getInstance(context).enqueueUniqueWork(
                "download_queue",
                ExistingWorkPolicy.KEEP,
                workRequest
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        // Run queue processing immediately
        scope.launch {
            processQueue()
        }
    }

    override fun observeQueue(): Flow<List<DownloadJob>> = repository.observeQueue()

    override suspend fun cancel(jobId: String) = repository.deleteJob(jobId)

    internal suspend fun processQueue() {
        synchronized(this) {
            if (activeJobMutex) return
            activeJobMutex = true
        }
        
        try {
            var job = repository.getNextQueuedJob()
            while (job != null) {
                processJob(job)
                job = repository.getNextQueuedJob()
            }
        } finally {
            synchronized(this) {
                activeJobMutex = false
            }
        }
    }

    private suspend fun processJob(job: DownloadJob) {
        repository.updateJob(job.copy(status = DownloadStatus.DOWNLOADING, progress = 0))
        
        try {
            val chapter = repository.getChapter(job.chapterId) 
                ?: throw IllegalStateException("Chapter not found in DB: ${job.chapterId}")
            val manga = repository.getManga(chapter.mangaId) 
                ?: throw IllegalStateException("Manga not found in DB: ${chapter.mangaId}")
            
            // Fetch chapter pages list from source
            val detailResult = sourceManager.getMangaDetail(manga.sourceId, manga.mangaUrl)
            val detail = when (detailResult) {
                is OpenCapDownResult.Success -> detailResult.data
                is OpenCapDownResult.Failure -> throw IllegalStateException("Failed to load manga details: ${detailResult.error}")
            }
            
            val chInfo = detail.chapters.firstOrNull { it.id == chapter.id }
                ?: throw IllegalStateException("Chapter ${chapter.id} not found in source details")
                
            val pagesResult = sourceManager.getChapterPages(manga.sourceId, chInfo.url)
            val sourcePages = when (pagesResult) {
                is OpenCapDownResult.Success -> pagesResult.data
                is OpenCapDownResult.Failure -> throw IllegalStateException("Failed to load chapter pages: ${pagesResult.error}")
            }
            
            val pages = repository.getPagesForChapter(chapter.id)
            if (pages.isEmpty()) {
                throw IllegalStateException("No page entities found in database for chapter ${chapter.id}")
            }
            
            val chapterDir = File(cacheDir, chapter.id)
            chapterDir.mkdirs()
            
            val semaphore = Semaphore(constraints.maxParallelPages)
            val progressMutex = Mutex()
            var completedCount = 0
            
            coroutineScope {
                val jobs = pages.map { page ->
                    val sourcePage = sourcePages.firstOrNull { it.index == page.index }
                        ?: throw IllegalStateException("Page index ${page.index} not found in source pages")
                    
                    val destFile = File(chapterDir, "page_${page.index + 1}.jpg")
                    
                    async {
                        semaphore.withPermit {
                            imageDownloader.download(sourcePage.imageUrl, sourcePage.headers, destFile).getOrThrow()
                            repository.updatePageLocalPath(page.id, destFile.absolutePath)
                            
                            val delayMs = ThreadLocalRandom.current().nextLong(
                                constraints.minDelayMs,
                                constraints.maxDelayMs + 1
                            )
                            delay(delayMs)
                            
                            progressMutex.withLock {
                                completedCount++
                                val progress = (completedCount * 100) / pages.size
                                repository.updateJob(job.copy(status = DownloadStatus.DOWNLOADING, progress = progress))
                            }
                        }
                    }
                }
                jobs.awaitAll()
            }
            
            repository.updateChapterStatus(chapter.id, "COMPLETE")
            repository.updateJob(job.copy(status = DownloadStatus.COMPLETE, progress = 100))

        } catch (e: Exception) {
            e.printStackTrace()
            repository.updateJob(job.copy(status = DownloadStatus.FAILED, errorMessage = e.message))
        }
    }
}
