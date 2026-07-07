package com.opencapdown.core.telegram

import com.opencapdown.core.domain.models.TelegramBackup

interface TelegramSync {
    suspend fun backupChapter(chapterId: String, mangaTitle: String): Result<Unit>
    suspend fun listBackups(mangaId: String): List<TelegramBackup>
    suspend fun restoreChapter(chapterId: String, mangaId: String): Result<Unit>
}
