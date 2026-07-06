package com.opencapdown.core.database.daos
import androidx.room.*
import com.opencapdown.core.database.entities.DownloadJobEntity
import kotlinx.coroutines.flow.Flow

@Dao
internal interface DownloadJobDao {
    @Query("SELECT * FROM download_jobs ORDER BY id ASC")
    fun observeAll(): Flow<List<DownloadJobEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(job: DownloadJobEntity)

    @Query("DELETE FROM download_jobs WHERE id = :id")
    suspend fun delete(id: String)
}
