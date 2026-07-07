package com.opencapdown.core.database
import android.content.Context
import androidx.room.Room

internal object DatabaseModule {
    fun provideDatabase(context: Context): AppDatabase {
        return Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            "opencapdown.db"
        ).fallbackToDestructiveMigration()
        .build()
    }
}
