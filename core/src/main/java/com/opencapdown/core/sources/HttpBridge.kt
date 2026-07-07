package com.opencapdown.core.sources

import okhttp3.OkHttpClient
import okhttp3.Request

internal class HttpBridge(private val client: OkHttpClient) {
    fun fetch(url: String, headers: Map<String, String> = emptyMap()): String {
        val request = Request.Builder()
            .url(url)
            .apply { headers.forEach { (k, v) -> addHeader(k, v) } }
            .build()
        return client.newCall(request).execute().use { it.body?.string() ?: "" }
    }
}
