package com.opencapdown.core.database.daos
import androidx.room.*
import com.opencapdown.core.database.entities.SettingEntity

@Dao
internal interface SettingDao {
    @Query("SELECT value FROM settings WHERE key = :key")
    suspend fun get(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun set(setting: SettingEntity)
}
