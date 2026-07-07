package com.opencapdown.core.sources.model

data class JsSourceManifest(
    val id: String,
    val name: String,
    val lang: String,
    val baseUrl: String = ""
)
