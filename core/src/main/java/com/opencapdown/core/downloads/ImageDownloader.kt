package com.opencapdown.core.downloads

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

internal class ImageDownloader(private val client: OkHttpClient) {
    suspend fun download(url: String, headers: Map<String, String>, destination: File): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder().url(url).apply {
                    headers.forEach { (k, v) -> addHeader(k, v) }
                }.build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                    destination.parentFile?.mkdirs()
                    destination.writeBytes(response.body!!.bytes())
                }
            }
        }
}
