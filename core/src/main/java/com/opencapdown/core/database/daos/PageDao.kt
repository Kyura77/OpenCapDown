package com.opencapdown.core.database.daos
import androidx.room.*
import com.opencapdown.core.database.entities.PageEntity

@Dao
internal interface PageDao {
    @Query("SELECT * FROM pages WHERE chapterId = :chapterId ORDER BY index ASC")
    suspend fun getByChapter(chapterId: String): List<PageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(pages: List<PageEntity>)

    @Query("UPDATE pages SET localPath = :path WHERE id = :id")
    suspend fun updateLocalPath(id: String, path: String)
}
