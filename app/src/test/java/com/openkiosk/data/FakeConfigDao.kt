package com.openkiosk.data

import com.openkiosk.data.local.dao.ConfigDao
import com.openkiosk.data.local.entity.ConfigEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory ConfigDao: the repository tests exercise the real persistence rules without Room. */
class FakeConfigDao : ConfigDao {
    val rows = MutableStateFlow<Map<String, String>>(emptyMap())

    override fun observeAll(): Flow<List<ConfigEntity>> =
        rows.map { m -> m.map { ConfigEntity(it.key, it.value) } }

    override suspend fun getValue(key: String): String? = rows.value[key]

    override suspend fun set(entity: ConfigEntity) {
        rows.value = rows.value + (entity.key to entity.value)
    }

    override suspend fun setAll(entities: List<ConfigEntity>) {
        rows.value = rows.value + entities.map { it.key to it.value }
    }
}
