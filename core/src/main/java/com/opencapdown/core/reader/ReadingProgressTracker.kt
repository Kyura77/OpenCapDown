package com.opencapdown.core.reader

import com.opencapdown.core.database.daos.SettingDao
import com.opencapdown.core.database.entities.SettingEntity
import com.opencapdown.core.domain.models.ReadingProgress

internal class ReadingProgressTracker(private val settingDao: SettingDao) {
    suspend fun save(mangaId: String, chapterId: String, pageIndex: Int) {
        settingDao.set(SettingEntity("progress:$mangaId", "$chapterId:$pageIndex"))
    }

    suspend fun load(mangaId: String): ReadingProgress? {
        return settingDao.get("progress:$mangaId")?.let {
            val (chapterId, pageIndex) = it.split(":")
            ReadingProgress(mangaId, chapterId, pageIndex.toInt())
        }
    }
}
