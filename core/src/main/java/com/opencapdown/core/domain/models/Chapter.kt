package com.opencapdown.core.domain.models
data class Chapter(
    val id: String,
    val mangaId: String,
    val number: Float,
    val title: String,
    val pageCount: Int,
    val integrityStatus: IntegrityStatus,
    val telegramAlbumMessageId: Long? = null,
    val isRead: Boolean = false
)

enum class IntegrityStatus {
    PENDING, COMPLETE, PARTIAL, BACKED_UP
}
