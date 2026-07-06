package com.opencapdown.core.database
import androidx.room.Database
import androidx.room.RoomDatabase
import com.opencapdown.core.database.daos.*
import com.opencapdown.core.database.entities.*

@Database(
    entities = [
        LibraryMangaEntity::class,
        ChapterEntity::class,
        PageEntity::class,
        DownloadJobEntity::class,
        SettingEntity::class
    ],
    version = 1,
    exportSchema = true
)
internal abstract class AppDatabase : RoomDatabase() {
    abstract fun libraryMangaDao(): LibraryMangaDao
    abstract fun chapterDao(): ChapterDao
    abstract fun pageDao(): PageDao
    abstract fun downloadJobDao(): DownloadJobDao
    abstract fun settingDao(): SettingDao
}
