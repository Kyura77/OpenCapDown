package com.opencapdown.core.telegram

import org.json.JSONObject

internal object TelegramMessageParser {

    fun parseTopicId(responseJson: String): Int {
        val json = JSONObject(responseJson)
        require(json.optBoolean("ok", false)) {
            "Telegram API error: ${json.optString("description")}"
        }
        return json.getJSONObject("result").getInt("message_thread_id")
    }

    fun parseMessageId(responseJson: String): Long {
        val json = JSONObject(responseJson)
        require(json.optBoolean("ok", false)) {
            "Telegram API error: ${json.optString("description")}"
        }
        return json.getJSONObject("result").getLong("message_id")
    }

    fun parseFileId(responseJson: String): String {
        val json = JSONObject(responseJson)
        require(json.optBoolean("ok", false)) {
            "Telegram API error: ${json.optString("description")}"
        }
        val result = json.getJSONObject("result")
        val photos = result.optJSONArray("photo")
        if (photos != null && photos.length() > 0) {
            val lastPhoto = photos.getJSONObject(photos.length() - 1)
            return lastPhoto.getString("file_id")
        }
        val document = result.optJSONObject("document")
        if (document != null) {
            return document.getString("file_id")
        }
        throw IllegalArgumentException("No file_id found in response")
    }

    fun parseFilePath(responseJson: String): String {
        val json = JSONObject(responseJson)
        require(json.optBoolean("ok", false)) {
            "Telegram API error: ${json.optString("description")}"
        }
        return json.getJSONObject("result").getString("file_path")
    }

    fun parseMessageIdsFromAlbum(responseJson: String): List<Long> {
        val json = JSONObject(responseJson)
        require(json.optBoolean("ok", false)) {
            "Telegram API error: ${json.optString("description")}"
        }
        val result = json.getJSONArray("result")
        val ids = mutableListOf<Long>()
        for (i in 0 until result.length()) {
            ids.add(result.getJSONObject(i).getLong("message_id"))
        }
        return ids
    }
}
