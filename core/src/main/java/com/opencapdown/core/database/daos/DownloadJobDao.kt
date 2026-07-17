package com.opencapdown.core.database.daos
import androidx.room.*
import com.opencapdown.core.database.entities.DownloadJobEntity
import kotlinx.coroutines.flow.Flow

@Dao
internal interface DownloadJobDao {
    @Query("SELECT * FROM download_jobs ORDER BY id ASC")
    fun observeAll(): Flow<List<DownloadJobEntity>>

    @Query("SELECT * FROM download_jobs WHERE id = :id")
    suspend fun getById(id: String): DownloadJobEntity?

    @Query("SELECT * FROM download_jobs WHERE chapterId = :chapterId")
    suspend fun getByChapterId(chapterId: String): List<DownloadJobEntity>

    @Query("SELECT * FROM download_jobs WHERE status = 'QUEUED' ORDER BY id ASC LIMIT 1")
    suspend fun getNextQueued(): DownloadJobEntity?

    @Query("UPDATE download_jobs SET status = 'QUEUED' WHERE status = 'DOWNLOADING'")
    suspend fun resetDownloadingToQueued()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(job: DownloadJobEntity)

    @Query("DELETE FROM download_jobs WHERE id = :id")
    suspend fun delete(id: String)
}

