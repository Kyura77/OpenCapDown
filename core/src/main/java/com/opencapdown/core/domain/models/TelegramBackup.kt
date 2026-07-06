package com.opencapdown.core.domain.models
data class TelegramBackup(
    val messageId: Long,
    val chapterTitle: String,
    val pageCount: Int,
    val createdAt: Long
)
