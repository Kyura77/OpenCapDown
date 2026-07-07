package com.opencapdown.core.database.daos
import androidx.room.*
import com.opencapdown.core.database.entities.ChapterEntity
import kotlinx.coroutines.flow.Flow

@Dao
internal interface ChapterDao {
    @Query("SELECT * FROM chapters WHERE mangaId = :mangaId ORDER BY number ASC")
    fun observeByManga(mangaId: String): Flow<List<ChapterEntity>>

    @Query("SELECT * FROM chapters WHERE id = :id")
    suspend fun getById(id: String): ChapterEntity?

    @Query("SELECT * FROM chapters WHERE telegramAlbumMessageId = :messageId LIMIT 1")
    suspend fun getByTelegramMessageId(messageId: Long): ChapterEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(chapter: ChapterEntity)

    @Query("UPDATE chapters SET isRead = :isRead WHERE id = :id")
    suspend fun updateRead(id: String, isRead: Boolean)

    @Query("UPDATE chapters SET integrityStatus = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: String)
}
