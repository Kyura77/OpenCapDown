package com.opencapdown.core.database.entities
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "library_manga")
internal data class LibraryMangaEntity(
    @PrimaryKey val id: String,
    val sourceId: String,
    val title: String,
    val coverUrl: String,
    val status: String,
    val telegramTopicId: Int?,
    val mangaUrl: String = ""
)
