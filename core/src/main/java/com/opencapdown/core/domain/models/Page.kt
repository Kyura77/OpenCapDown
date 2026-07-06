package com.opencapdown.core.domain.models
data class Page(
    val id: String,
    val chapterId: String,
    val index: Int,
    val localPath: String? = null,
    val telegramFileId: String? = null,
    val telegramMessageId: Long? = null
)
