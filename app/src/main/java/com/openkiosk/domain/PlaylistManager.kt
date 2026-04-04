package com.openkiosk.domain

import com.openkiosk.data.repository.PlaylistRepository
import com.openkiosk.domain.model.PlaylistItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistManager @Inject constructor(
    private val playlistRepository: PlaylistRepository
) {
    private val _playlist = MutableStateFlow<List<PlaylistItem>>(emptyList())
    val playlist: StateFlow<List<PlaylistItem>> = _playlist.asStateFlow()

    private val _currentItem = MutableStateFlow<PlaylistItem?>(null)
    val currentItem: StateFlow<PlaylistItem?> = _currentItem.asStateFlow()

    private var currentIndex = 0
    private var collectionJob: Job? = null
    private var rotationJob: Job? = null
    private var scope: CoroutineScope? = null

    fun start(scope: CoroutineScope) {
        this.scope = scope
        collectionJob?.cancel()
        collectionJob = scope.launch {
            playlistRepository.observePlaylist().collect { items ->
                val sorted = items.sortedBy { it.position }
                _playlist.value = sorted
                currentIndex = 0
                startRotation(sorted)
            }
        }
    }

    fun stop() {
        collectionJob?.cancel()
        collectionJob = null
        rotationJob?.cancel()
        rotationJob = null
        scope = null
    }

    fun next() {
        val items = _playlist.value
        if (items.isEmpty()) return
        currentIndex = (currentIndex + 1) % items.size
        _currentItem.value = items[currentIndex]
        restartRotation()
    }

    fun previous() {
        val items = _playlist.value
        if (items.isEmpty()) return
        currentIndex = if (currentIndex - 1 < 0) items.size - 1 else currentIndex - 1
        _currentItem.value = items[currentIndex]
        restartRotation()
    }

    private fun startRotation(items: List<PlaylistItem>) {
        rotationJob?.cancel()
        if (items.isEmpty()) {
            _currentItem.value = null
            return
        }
        currentIndex = 0
        _currentItem.value = items[0]
        if (items.size <= 1) return
        rotationJob = scope?.launch {
            while (true) {
                val current = _currentItem.value ?: break
                delay(current.durationSeconds * 1000L)
                currentIndex = (currentIndex + 1) % items.size
                _currentItem.value = items[currentIndex]
            }
        }
    }

    private fun restartRotation() {
        val items = _playlist.value
        if (items.size <= 1) return
        rotationJob?.cancel()
        rotationJob = scope?.launch {
            while (true) {
                val current = _currentItem.value ?: break
                delay(current.durationSeconds * 1000L)
                currentIndex = (currentIndex + 1) % items.size
                _currentItem.value = items[currentIndex]
            }
        }
    }
}
