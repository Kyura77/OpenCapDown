package com.opencapdown.core.database.entities
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "download_jobs")
internal data class DownloadJobEntity(
    @PrimaryKey val id: String,
    val chapterId: String,
    val status: String,
    val progress: Int,
    val errorMessage: String?
)
