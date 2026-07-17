package com.opencapdown.core.downloads

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

internal class ImageDownloader(private val client: OkHttpClient) {
    suspend fun download(url: String, headers: Map<String, String>, destination: File): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val parent = destination.parentFile ?: File(".")
                if (parent.freeSpace > 0 && parent.freeSpace < 4_000_000L) {
                    throw IOException("Insufficient storage space: only ${parent.freeSpace} bytes available")
                }

                val customClient = client.newBuilder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .build()

                var lastException: Exception? = null
                for (attempt in 0 until 3) {
                    try {
                        val request = Request.Builder().url(url).apply {
                            headers.forEach { (k, v) -> addHeader(k, v) }
                        }.build()
                        
                        customClient.newCall(request).execute().use { response ->
                            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                            val bodyBytes = response.body?.bytes() ?: throw IOException("Empty response body")
                            
                            if (bodyBytes.size < 4) {
                                throw IOException("Invalid image file size: ${bodyBytes.size} bytes")
                            }
                            
                            // Check magic bytes: JPEG (FF D8), PNG (89 50 4E 47), WebP (52 49 46 46)
                            val isJpg = bodyBytes[0] == 0xFF.toByte() && bodyBytes[1] == 0xD8.toByte()
                            val isPng = bodyBytes[0] == 0x89.toByte() && bodyBytes[1] == 0x50.toByte() && bodyBytes[2] == 0x4E.toByte() && bodyBytes[3] == 0x47.toByte()
                            val isWebp = bodyBytes[0] == 'R'.toByte() && bodyBytes[1] == 'I'.toByte() && bodyBytes[2] == 'F'.toByte() && bodyBytes[3] == 'F'.toByte()
                            
                            if (!isJpg && !isPng && !isWebp) {
                                throw IOException("Corrupted image header: unknown format")
                            }

                            destination.parentFile?.mkdirs()
                            destination.writeBytes(bodyBytes)
                            return@withContext
                        }
                    } catch (e: Exception) {
                        lastException = e
                        if (attempt < 2) {
                            delay(1000L * (1 shl attempt))
                        }
                    }
                }
                throw lastException ?: IOException("Unknown download failure")
            }
        }
}

