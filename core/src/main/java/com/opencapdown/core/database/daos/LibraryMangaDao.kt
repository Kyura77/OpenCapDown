package com.opencapdown.core.database.daos
import androidx.room.*
import com.opencapdown.core.database.entities.LibraryMangaEntity
import kotlinx.coroutines.flow.Flow

@Dao
internal interface LibraryMangaDao {
    @Query("SELECT * FROM library_manga ORDER BY title ASC")
    fun observeAll(): Flow<List<LibraryMangaEntity>>

    @Query("SELECT * FROM library_manga WHERE id = :id")
    suspend fun getById(id: String): LibraryMangaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(manga: LibraryMangaEntity)

    @Query("DELETE FROM library_manga WHERE id = :id")
    suspend fun delete(id: String)
}
