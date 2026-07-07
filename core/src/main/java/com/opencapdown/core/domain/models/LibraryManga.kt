package com.opencapdown.core.domain.models
data class LibraryManga(
    val id: String,
    val sourceId: String,
    val title: String,
    val coverUrl: String,
    val status: String,
    val telegramTopicId: Int? = null,
    val mangaUrl: String = ""
)
