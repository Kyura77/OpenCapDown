package com.opencapdown.core.domain.models
data class MangaDetail(
    val sourceId: String,
    val url: String,
    val title: String,
    val coverUrl: String,
    val description: String,
    val status: String,
    val chapters: List<ChapterInfo>
)

data class ChapterInfo(
    val id: String,
    val title: String,
    val url: String,
    val number: Float
)
