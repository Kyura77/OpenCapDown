package com.opencapdown.core.domain.models
data class DownloadJob(
    val id: String,
    val chapterId: String,
    val status: DownloadStatus,
    val progress: Int = 0,
    val errorMessage: String? = null
)

enum class DownloadStatus {
    QUEUED, DOWNLOADING, COMPLETE, FAILED, CANCELLED
}
