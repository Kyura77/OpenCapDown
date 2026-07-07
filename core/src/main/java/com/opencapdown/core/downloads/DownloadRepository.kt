package com.opencapdown.core.downloads

import com.opencapdown.core.database.daos.ChapterDao
import com.opencapdown.core.database.daos.DownloadJobDao
import com.opencapdown.core.database.daos.PageDao
import com.opencapdown.core.database.entities.DownloadJobEntity
import com.opencapdown.core.domain.models.DownloadJob
import com.opencapdown.core.domain.models.DownloadStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

internal class DownloadRepository(
    private val jobDao: DownloadJobDao,
    private val chapterDao: ChapterDao,
    private val pageDao: PageDao
) {
    fun observeQueue(): Flow<List<DownloadJob>> =
        jobDao.observeAll().map { entities ->
            entities.map { e ->
                DownloadJob(
                    id = e.id,
                    chapterId = e.chapterId,
                    status = DownloadStatus.valueOf(e.status),
                    progress = e.progress,
                    errorMessage = e.errorMessage
                )
            }
        }

    suspend fun createJob(chapterId: String) {
        jobDao.insert(
            DownloadJobEntity(
                id = UUID.randomUUID().toString(),
                chapterId = chapterId,
                status = DownloadStatus.QUEUED.name,
                progress = 0,
                errorMessage = null
            )
        )
    }

    suspend fun updateJob(job: DownloadJob) {
        jobDao.insert(
            DownloadJobEntity(
                id = job.id,
                chapterId = job.chapterId,
                status = job.status.name,
                progress = job.progress,
                errorMessage = job.errorMessage
            )
        )
    }

    suspend fun deleteJob(jobId: String) = jobDao.delete(jobId)
}
