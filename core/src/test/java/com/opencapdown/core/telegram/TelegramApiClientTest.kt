package com.opencapdown.core.telegram

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.TimeUnit
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TelegramApiClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: TelegramApiClient

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        val httpClient = OkHttpClient.Builder()
            .connectTimeout(1, TimeUnit.SECONDS)
            .readTimeout(1, TimeUnit.SECONDS)
            .writeTimeout(1, TimeUnit.SECONDS)
            .build()
        client = TelegramApiClient(
            client = httpClient,
            rateLimiter = NoOpRateLimiter(),
            baseUrl = server.url("/").toString().trimEnd('/')
        )
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `createForumTopic parses topic id from response`() = runTest {
        server.enqueue(
            MockResponse().setBody("""
                {"ok":true,"result":{"message_thread_id":42}}
            """.trimIndent())
        )

        val topicId = client.createForumTopic("test:token", 123L, "My Manga")

        assertEquals(42, topicId)

        val recorded = server.takeRequest()
        assertContains(recorded.path!!, "createForumTopic")
        assertContains(recorded.body.readUtf8(), "My Manga")
    }

    @Test
    fun `createForumTopic throws on error response`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(400).setBody("""
                {"ok":false,"description":"Bad Request: topic already exists"}
            """.trimIndent())
        )

        assertThrows<Exception> {
            client.createForumTopic("test:token", 123L, "Duplicate")
        }
    }

    @Test
    fun `sendMessage returns message id`() = runTest {
        server.enqueue(
            MockResponse().setBody("""
                {"ok":true,"result":{"message_id":99}}
            """.trimIndent())
        )

        val msgId = client.sendMessage("test:token", 123L, "Hello", topicId = 5)

        assertEquals(99L, msgId)

        val recorded = server.takeRequest()
        assertContains(recorded.path!!, "sendMessage")
    }

    @Test
    fun `sendMediaGroup returns message ids`() = runTest {
        server.enqueue(
            MockResponse().setBody("""
                {"ok":true,"result":[{"message_id":10},{"message_id":11}]}
            """.trimIndent())
        )

        val media = listOf(
            TelegramMediaItem(imageBytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47), caption = "Page 1"),
            TelegramMediaItem(imageBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()), caption = "Page 2")
        )

        val messages = client.sendMediaGroup("test:token", 123L, topicId = 1, media = media)

        assertEquals(2, messages.size)
        assertEquals(10L, messages[0].messageId)
        assertEquals(11L, messages[1].messageId)

        val recorded = server.takeRequest()
        assertContains(recorded.path!!, "sendMediaGroup")
    }

    @Test
    fun `sendMediaGroup rejects empty list`() = runTest {
        assertThrows<IllegalArgumentException> {
            client.sendMediaGroup("test:token", 123L, topicId = 1, media = emptyList())
        }
    }

    @Test
    fun `sendMediaGroup rejects more than 10 items`() = runTest {
        val items = List(11) { TelegramMediaItem(imageBytes = byteArrayOf(0x00)) }

        assertThrows<IllegalArgumentException> {
            client.sendMediaGroup("test:token", 123L, topicId = 1, media = items)
        }
    }

    @Test
    fun `getFileUrl constructs correct url`() = runTest {
        server.enqueue(
            MockResponse().setBody("""
                {"ok":true,"result":{"file_id":"abc123","file_path":"photos/photo.jpg","file_size":1234}}
            """.trimIndent())
        )

        val url = client.getFileUrl("test:token", "abc123")

        val expectedUrl = "${server.url("/").toString().trimEnd('/')}/file/bottest:token/photos/photo.jpg"
        assertEquals(expectedUrl, url)
    }

    @Test
    fun `getFileBytes downloads file content`() = runTest {
        server.enqueue(
            MockResponse().setBody("""
                {"ok":true,"result":{"file_id":"abc","file_path":"test.jpg","file_size":4}}
            """.trimIndent())
        )
        server.enqueue(
            MockResponse().setBody("fake_image_bytes")
        )

        val bytes = client.getFileBytes("test:token", "abc")

        assertEquals("fake_image_bytes", bytes.decodeToString())
    }

    @Test
    fun `parseMessageIdsFromAlbum extracts all ids`() {
        val json = """
            {"ok":true,"result":[{"message_id":1},{"message_id":2}]}
        """.trimIndent()

        val ids = TelegramMessageParser.parseMessageIdsFromAlbum(json)

        assertEquals(listOf(1L, 2L), ids)
    }

    @Test
    fun `parseFileId works for photo array`() {
        val json = """
            {"ok":true,"result":{"photo":[{"file_id":"big","width":800},{"file_id":"small","width":400}]}}
        """.trimIndent()

        val fileId = TelegramMessageParser.parseFileId(json)

        assertEquals("small", fileId)
    }

    @Test
    fun `parseTopicId extracts thread id`() {
        val json = """
            {"ok":true,"result":{"message_thread_id":77}}
        """.trimIndent()

        assertEquals(77, TelegramMessageParser.parseTopicId(json))
    }
}

private class NoOpRateLimiter : TelegramRateLimiter(maxRequests = Int.MAX_VALUE, windowMs = 0) {
    override suspend fun <T> execute(maxRetries: Int, block: suspend () -> T): T {
        return block()
    }
}
