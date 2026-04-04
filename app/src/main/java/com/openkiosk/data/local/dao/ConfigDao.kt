package com.openkiosk.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.openkiosk.data.local.entity.ConfigEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConfigDao {
    @Query("SELECT * FROM config")
    fun observeAll(): Flow<List<ConfigEntity>>

    @Query("SELECT value FROM config WHERE `key` = :key")
    suspend fun getValue(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun set(entity: ConfigEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setAll(entities: List<ConfigEntity>)
}
