package com.opencapdown.core.telegram

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

internal data class TelegramMessage(
    val messageId: Long,
    val fileId: String?
)

internal data class TelegramMediaItem(
    val imageBytes: ByteArray? = null,
    val imageFile: File? = null,
    val caption: String? = null
) {
    fun hasImage(): Boolean = imageBytes != null || imageFile != null
}

internal class TelegramApiClient(
    private val client: OkHttpClient = DEFAULT_CLIENT,
    private val rateLimiter: TelegramRateLimiter = TelegramRateLimiter()
) {
    fun createForumTopic(botToken: String, chatId: Long, name: String): Int {
        val json = """
            {"chat_id":$chatId,"name":"${name.replace("\"", "\\\"")}"}
        """.trimIndent()
        val body = json.toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url("$API_BASE/bot$botToken/createForumTopic")
            .post(body)
            .build()
        val response = rateLimiter.execute {
            client.newCall(request).execute()
        }
        val responseBody = response.body?.string() ?: throw TelegramApiException("Empty response body")
        return TelegramMessageParser.parseTopicId(responseBody)
    }

    fun sendMediaGroup(
        botToken: String,
        chatId: Long,
        topicId: Int?,
        media: List<TelegramMediaItem>
    ): List<TelegramMessage> {
        require(media.isNotEmpty()) { "media list must not be empty" }
        require(media.size <= 10) { "max 10 media items per group" }

        val multipart = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("chat_id", chatId.toString())
            .addFormDataPart("method", "sendMediaGroup")

        if (topicId != null) {
            multipart.addFormDataPart("message_thread_id", topicId.toString())
        }

        val attachments = media.mapIndexed { index, item ->
            val attachName = "attach$index"
            val fileName = "photo_${index + 1}.jpg"

            when {
                item.imageBytes != null -> {
                    val fileBody = item.imageBytes.toRequestBody(IMAGE_JPEG)
                    multipart.addFormDataPart(attachName, fileName, fileBody)
                }
                item.imageFile != null -> {
                    val fileBody = item.imageFile.asRequestBody(IMAGE_JPEG)
                    multipart.addFormDataPart(attachName, fileName, fileBody)
                }
                else -> throw IllegalArgumentException("Media item $index has no image data")
            }

            buildString {
                append("{\"type\":\"photo\",\"media\":\"attach://$attachName\"")
                if (item.caption != null && index == 0) {
                    val escaped = item.caption
                        .replace("\\", "\\\\")
                        .replace("\"", "\\\"")
                    append(",\"caption\":\"$escaped\"")
                    append(",\"parse_mode\":\"HTML\"")
                }
                append("}")
            }
        }

        val mediaArray = attachments.joinToString(",", "[", "]")
        multipart.addFormDataPart("media", mediaArray)

        val request = Request.Builder()
            .url("$API_BASE/bot$botToken/sendMediaGroup")
            .post(multipart.build())
            .build()

        val response = rateLimiter.execute {
            client.newCall(request).execute()
        }
        val responseBody = response.body?.string() ?: throw TelegramApiException("Empty response body")

        val messageIds = TelegramMessageParser.parseMessageIdsFromAlbum(responseBody)
        return messageIds.map { id ->
            TelegramMessage(messageId = id, fileId = null)
        }
    }

    fun sendMessage(
        botToken: String,
        chatId: Long,
        text: String,
        topicId: Int? = null
    ): Long {
        val json = buildString {
            append("{\"chat_id\":$chatId,\"text\":\"${text.replace("\"", "\\\"")}\"")
            if (topicId != null) append(",\"message_thread_id\":$topicId")
            append("}")
        }
        val body = json.toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url("$API_BASE/bot$botToken/sendMessage")
            .post(body)
            .build()
        val response = rateLimiter.execute {
            client.newCall(request).execute()
        }
        val responseBody = response.body?.string() ?: throw TelegramApiException("Empty response body")
        return TelegramMessageParser.parseMessageId(responseBody)
    }

    fun getFileUrl(botToken: String, fileId: String): String {
        val request = Request.Builder()
            .url("$API_BASE/bot$botToken/getFile?file_id=$fileId")
            .get()
            .build()
        val response = rateLimiter.execute {
            client.newCall(request).execute()
        }
        val responseBody = response.body?.string() ?: throw TelegramApiException("Empty response body")
        val filePath = TelegramMessageParser.parseFilePath(responseBody)
        return "$FILE_BASE/bot$botToken/$filePath"
    }

    fun getFileBytes(botToken: String, fileId: String): ByteArray {
        val url = getFileUrl(botToken, fileId)
        val request = Request.Builder()
            .url(url)
            .get()
            .build()
        val response = rateLimiter.execute {
            client.newCall(request).execute()
        }
        return response.body?.bytes() ?: throw TelegramApiException("Empty file body")
    }

    companion object {
        private const val API_BASE = "https://api.telegram.org"
        private const val FILE_BASE = "https://api.telegram.org/file"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val IMAGE_JPEG = "image/jpeg".toMediaType()

        private val DEFAULT_CLIENT = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }
}

private fun File.asRequestBody(contentType: okhttp3.MediaType): okhttp3.RequestBody {
    return okhttp3.RequestBody.create(contentType, this)
}

internal class TelegramApiException(message: String) : Exception(message)
