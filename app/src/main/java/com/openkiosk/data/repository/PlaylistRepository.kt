package com.openkiosk.data.repository

import com.openkiosk.data.local.dao.PlaylistDao
import com.openkiosk.data.local.entity.PlaylistEntity
import com.openkiosk.domain.model.PlaylistItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistRepository @Inject constructor(
    private val playlistDao: PlaylistDao
) {
    fun observePlaylist(): Flow<List<PlaylistItem>> = playlistDao.observeAll().map { entities ->
        entities.map { it.toDomain() }
    }

    suspend fun getPlaylist(): List<PlaylistItem> = playlistDao.getAll().map { it.toDomain() }

    suspend fun addItem(url: String, durationSeconds: Int = 30): Long {
        val position = playlistDao.nextPosition()
        return playlistDao.insert(
            PlaylistEntity(url = url, durationSeconds = durationSeconds, position = position)
        )
    }

    suspend fun updateItem(item: PlaylistItem) {
        playlistDao.update(item.toEntity())
    }

    suspend fun removeItem(item: PlaylistItem) {
        playlistDao.delete(item.toEntity())
    }

    suspend fun clearAll() {
        playlistDao.deleteAll()
    }

    private fun PlaylistEntity.toDomain() = PlaylistItem(
        id = id,
        url = url,
        durationSeconds = durationSeconds,
        position = position
    )

    private fun PlaylistItem.toEntity() = PlaylistEntity(
        id = id,
        url = url,
        durationSeconds = durationSeconds,
        position = position
    )
}
