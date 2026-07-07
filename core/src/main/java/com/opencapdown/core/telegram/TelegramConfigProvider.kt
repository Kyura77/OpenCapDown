package com.opencapdown.core.telegram

interface TelegramConfigProvider {
    fun getBotToken(): String?
    fun getChatId(): Long?
    fun setConfig(botToken: String, chatId: Long)
    fun clearConfig()
}
