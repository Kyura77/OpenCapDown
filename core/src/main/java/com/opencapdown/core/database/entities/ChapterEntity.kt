package com.opencapdown.core.database.entities
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "chapters",
    foreignKeys = [
        ForeignKey(
            entity = LibraryMangaEntity::class,
            parentColumns = ["id"],
            childColumns = ["mangaId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["mangaId"])]
)
internal data class ChapterEntity(
    @PrimaryKey val id: String,
    val mangaId: String,
    val number: Float,
    val title: String,
    val pageCount: Int,
    val integrityStatus: String,
    val telegramAlbumMessageId: Long?,
    val isRead: Boolean
)
