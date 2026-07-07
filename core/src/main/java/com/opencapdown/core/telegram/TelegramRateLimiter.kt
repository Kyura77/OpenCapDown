package com.opencapdown.core.telegram

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class TelegramRateLimiter(
    private val maxRequests: Int = DEFAULT_MAX_REQUESTS,
    private val windowMs: Long = DEFAULT_WINDOW_MS
) {
    private val mutex = Mutex()
    private val timestamps = ArrayDeque<Long>()

    open suspend fun <T> execute(
        block: suspend () -> T,
        maxRetries: Int = DEFAULT_MAX_RETRIES
    ): T {
        var lastError: Exception? = null

        for (attempt in 0..maxRetries) {
            try {
                waitForSlot()
                return block()
            } catch (e: TelegramRateLimitException) {
                lastError = e
                val retryAfter = e.retryAfterMs
                delay(retryAfter)
            }
        }

        throw lastError ?: TelegramRateLimitException("Max retries exceeded", 0)
    }

    private suspend fun waitForSlot() {
        mutex.withLock {
            val now = System.currentTimeMillis()
            while (timestamps.isNotEmpty() && now - timestamps.first() > windowMs) {
                timestamps.removeFirst()
            }
            if (timestamps.size >= maxRequests) {
                val oldest = timestamps.first()
                val wait = windowMs - (now - oldest) + 1
                mutex.unlock()
                delay(wait)
                mutex.lock()
                waitForSlot()
                return
            }
            timestamps.addLast(now)
        }
    }

    companion object {
        const val DEFAULT_MAX_REQUESTS = 20
        const val DEFAULT_WINDOW_MS = 60_000L
        const val DEFAULT_MAX_RETRIES = 3
    }
}

internal class TelegramRateLimitException(
    message: String,
    val retryAfterMs: Long
) : Exception(message)
