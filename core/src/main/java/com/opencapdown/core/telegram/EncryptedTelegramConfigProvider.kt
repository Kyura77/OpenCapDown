package com.opencapdown.core.telegram

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

internal class EncryptedTelegramConfigProvider(context: Context) : TelegramConfigProvider {

    private val prefs: SharedPreferences

    init {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        prefs = EncryptedSharedPreferences.create(
            PREFS_NAME,
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    override fun getBotToken(): String? = prefs.getString(KEY_BOT_TOKEN, null)

    override fun getChatId(): Long? {
        val raw = prefs.getString(KEY_CHAT_ID, null) ?: return null
        return raw.toLongOrNull()
    }

    override fun setConfig(botToken: String, chatId: Long) {
        prefs.edit()
            .putString(KEY_BOT_TOKEN, botToken)
            .putString(KEY_CHAT_ID, chatId.toString())
            .apply()
    }

    override fun clearConfig() {
        prefs.edit()
            .remove(KEY_BOT_TOKEN)
            .remove(KEY_CHAT_ID)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "telegram_encrypted_config"
        private const val KEY_BOT_TOKEN = "bot_token"
        private const val KEY_CHAT_ID = "chat_id"
    }
}
