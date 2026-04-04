package com.openkiosk.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openkiosk.data.repository.ConfigRepository
import com.openkiosk.data.repository.PlaylistRepository
import com.openkiosk.domain.model.KioskConfig
import com.openkiosk.domain.model.PlaylistItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val configRepository: ConfigRepository,
    private val playlistRepository: PlaylistRepository
) : ViewModel() {

    val config: StateFlow<KioskConfig> = configRepository.observeConfig()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), KioskConfig())

    val playlist: StateFlow<List<PlaylistItem>> = playlistRepository.observePlaylist()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateConfig(key: String, value: String) {
        viewModelScope.launch {
            configRepository.updateConfig(key, value)
        }
    }

    fun addPlaylistItem(url: String, durationSeconds: Int) {
        viewModelScope.launch {
            playlistRepository.addItem(url, durationSeconds)
        }
    }

    fun removePlaylistItem(item: PlaylistItem) {
        viewModelScope.launch {
            playlistRepository.removeItem(item)
        }
    }

    fun updatePlaylistItem(item: PlaylistItem) {
        viewModelScope.launch {
            playlistRepository.updateItem(item)
        }
    }
}
