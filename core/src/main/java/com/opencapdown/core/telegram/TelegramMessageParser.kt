package com.opencapdown.core.telegram

/**
 * Parser JSON minimalista para respostas da API Telegram.
 * Usa regex em vez de org.json para evitar dependências do Android SDK
 * em testes de JVM pura (unit tests sem Robolectric).
 */
internal object TelegramMessageParser {

    private fun checkOk(json: String) {
        val ok = Regex(""""ok"\s*:\s*(true|false)""").find(json)?.groupValues?.get(1)
        if (ok != "true") {
            val desc = Regex(""""description"\s*:\s*"([^"]*)"?""").find(json)?.groupValues?.get(1)
                ?: "Unknown error"
            throw IllegalArgumentException("Telegram API error: $desc")
        }
    }

    private fun extractLong(json: String, key: String): Long? {
        return Regex(""""$key"\s*:\s*(\d+)""").find(json)?.groupValues?.get(1)?.toLongOrNull()
    }

    private fun extractString(json: String, key: String): String? {
        return Regex(""""$key"\s*:\s*"([^"]*)"""").find(json)?.groupValues?.get(1)
    }

    fun parseTopicId(responseJson: String): Int {
        checkOk(responseJson)
        return extractLong(responseJson, "message_thread_id")?.toInt()
            ?: throw IllegalArgumentException("No message_thread_id in response")
    }

    fun parseMessageId(responseJson: String): Long {
        checkOk(responseJson)
        return extractLong(responseJson, "message_id")
            ?: throw IllegalArgumentException("No message_id in response")
    }

    fun parseFileId(responseJson: String): String {
        checkOk(responseJson)
        // Telegram returns photos sorted smallest→largest; last file_id = largest
        return Regex(""""file_id"\s*:\s*"([^"]*)"""")
            .findAll(responseJson)
            .lastOrNull()?.groupValues?.get(1)
            ?: throw IllegalArgumentException("No file_id found in response")
    }

    fun parseFilePath(responseJson: String): String {
        checkOk(responseJson)
        return extractString(responseJson, "file_path")
            ?: throw IllegalArgumentException("No file_path in response")
    }

    fun parseMessageIdsFromAlbum(responseJson: String): List<Long> {
        checkOk(responseJson)
        return Regex(""""message_id"\s*:\s*(\d+)""")
            .findAll(responseJson)
            .map { it.groupValues[1].toLong() }
            .toList()
    }
}
