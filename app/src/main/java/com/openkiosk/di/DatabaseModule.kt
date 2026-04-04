package com.openkiosk.di

import android.content.Context
import androidx.room.Room
import com.openkiosk.data.local.AppDatabase
import com.openkiosk.data.local.dao.ConfigDao
import com.openkiosk.data.local.dao.PlaylistDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "openkiosk.db"
        ).build()
    }

    @Provides
    fun provideConfigDao(db: AppDatabase): ConfigDao = db.configDao()

    @Provides
    fun providePlaylistDao(db: AppDatabase): PlaylistDao = db.playlistDao()
}
