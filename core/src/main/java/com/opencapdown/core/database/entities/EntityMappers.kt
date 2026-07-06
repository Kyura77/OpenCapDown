package com.opencapdown.core.database.entities
import com.opencapdown.core.domain.models.*

internal fun ChapterEntity.toDomain() = Chapter(
    id = id,
    mangaId = mangaId,
    number = number,
    title = title,
    pageCount = pageCount,
    integrityStatus = IntegrityStatus.valueOf(integrityStatus),
    telegramAlbumMessageId = telegramAlbumMessageId,
    isRead = isRead
)

internal fun Chapter.toEntity() = ChapterEntity(
    id = id,
    mangaId = mangaId,
    number = number,
    title = title,
    pageCount = pageCount,
    integrityStatus = integrityStatus.name,
    telegramAlbumMessageId = telegramAlbumMessageId,
    isRead = isRead
)
