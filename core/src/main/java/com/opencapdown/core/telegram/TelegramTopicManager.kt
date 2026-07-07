package com.opencapdown.core.telegram

import com.opencapdown.core.database.daos.LibraryMangaDao
import com.opencapdown.core.database.entities.LibraryMangaEntity

internal class TelegramTopicManager(
    private val mangaDao: LibraryMangaDao,
    private val apiClient: TelegramApiClient,
    private val configProvider: TelegramConfigProvider
) {
    suspend fun ensureTopic(mangaId: String, mangaTitle: String): Int {
        val existing = mangaDao.getById(mangaId)
        if (existing?.telegramTopicId != null) {
            return existing.telegramTopicId
        }

        val botToken = configProvider.getBotToken()
            ?: throw TelegramConfigException("Bot token not configured")
        val chatId = configProvider.getChatId()
            ?: throw TelegramConfigException("Chat ID not configured")

        val topicId = apiClient.createForumTopic(botToken, chatId, mangaTitle)

        val updated = (existing ?: LibraryMangaEntity(
            id = mangaId,
            sourceId = "",
            title = mangaTitle,
            coverUrl = "",
            status = "",
            telegramTopicId = null
        )).copy(telegramTopicId = topicId)

        mangaDao.insert(updated)
        return topicId
    }

    suspend fun getTopicId(mangaId: String): Int? {
        return mangaDao.getById(mangaId)?.telegramTopicId
    }
}

internal class TelegramConfigException(message: String) : Exception(message)
