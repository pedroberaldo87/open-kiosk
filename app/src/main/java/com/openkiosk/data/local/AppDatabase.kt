package com.openkiosk.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.openkiosk.data.local.dao.ConfigDao
import com.openkiosk.data.local.dao.PlaylistDao
import com.openkiosk.data.local.entity.ConfigEntity
import com.openkiosk.data.local.entity.PlaylistEntity

@Database(
    entities = [ConfigEntity::class, PlaylistEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun configDao(): ConfigDao
    abstract fun playlistDao(): PlaylistDao
}
