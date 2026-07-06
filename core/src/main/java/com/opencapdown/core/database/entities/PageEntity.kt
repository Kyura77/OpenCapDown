package com.opencapdown.core.database.entities
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "pages",
    foreignKeys = [
        ForeignKey(
            entity = ChapterEntity::class,
            parentColumns = ["id"],
            childColumns = ["chapterId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["chapterId"])]
)
internal data class PageEntity(
    @PrimaryKey val id: String,
    val chapterId: String,
    val index: Int,
    val localPath: String?,
    val telegramFileId: String?,
    val telegramMessageId: Long?
)
