package com.opencapdown.core.domain.models
data class PageResult(
    val index: Int,
    val imageUrl: String,
    val headers: Map<String, String> = emptyMap()
)
