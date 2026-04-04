package com.openkiosk.presentation.viewmodel

import android.app.Activity
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openkiosk.data.repository.ConfigRepository
import com.openkiosk.domain.PlaylistManager
import com.openkiosk.domain.model.KioskConfig
import com.openkiosk.domain.model.PlaylistItem
import com.openkiosk.domain.model.ScreenState
import com.openkiosk.kiosk.KioskLockManager
import com.openkiosk.sensors.MotionDetectionManager
import com.openkiosk.sensors.SensorWakeManager
import com.openkiosk.sleep.ScreenStateManager
import com.openkiosk.webview.WebViewRecoveryManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class KioskViewModel @Inject constructor(
    private val configRepository: ConfigRepository,
    private val screenStateManager: ScreenStateManager,
    private val kioskLockManager: KioskLockManager,
    private val motionDetectionManager: MotionDetectionManager,
    private val sensorWakeManager: SensorWakeManager,
    val playlistManager: PlaylistManager
) : ViewModel() {

    val config: StateFlow<KioskConfig> = configRepository.observeConfig()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), KioskConfig())

    val screenState: StateFlow<ScreenState> = screenStateManager.screenState

    val currentPlaylistItem: StateFlow<PlaylistItem?> = playlistManager.currentItem

    val currentUrl: StateFlow<String> get() {
        // Derived: playlist item URL, or startUrl from config
        return _currentUrl.asStateFlow()
    }
    private val _currentUrl = MutableStateFlow("")

    private val _isOnline = MutableStateFlow(true)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val _needsRefresh = MutableStateFlow(false)
    val needsRefresh: StateFlow<Boolean> = _needsRefresh.asStateFlow()

    val recoveryManager = WebViewRecoveryManager()

    private var connectivityCallback: ConnectivityManager.NetworkCallback? = null
    private var connectivityManager: ConnectivityManager? = null

    init {
        playlistManager.start(viewModelScope)

        // Observe config changes and propagate to managers
        viewModelScope.launch {
            config.collect { cfg ->
                screenStateManager.configure(
                    activeTimeoutMs = cfg.activeTimeoutSeconds * 1000L,
                    dimTimeoutMs = cfg.dimTimeoutSeconds * 1000L,
                    dimBrightness = cfg.dimBrightnessPercent / 100f
                )
                recoveryManager.autoRefreshIntervalMs = cfg.autoRefreshMinutes * 60 * 1000L
            }
        }

        // Observe current playlist item → update currentUrl
        viewModelScope.launch {
            currentPlaylistItem.collect { item ->
                _currentUrl.value = item?.url ?: config.value.startUrl
            }
        }

        // Observe screen state → manage sensors accordingly
        viewModelScope.launch {
            screenState.collect { state ->
                when (state) {
                    ScreenState.ACTIVE -> {
                        // In active state, camera motion detection is unnecessary
                        // (screen is already on). Keep sensors off to save battery.
                        motionDetectionManager.stop()
                        sensorWakeManager.stop()
                    }
                    ScreenState.DIM, ScreenState.SLEEP -> {
                        // Start sensors to detect user presence and wake up
                        val cfg = config.value
                        startSensorsIfNeeded(cfg)
                    }
                }
            }
        }
    }

    fun onUserInteraction() {
        screenStateManager.onUserActivity()
    }

    fun attachActivity(activity: Activity, lifecycleOwner: LifecycleOwner) {
        screenStateManager.attachActivity(activity)

        // Start kiosk lock
        val cfg = config.value
        if (cfg.lockTaskEnabled) {
            kioskLockManager.startLockTask(activity)
        } else {
            kioskLockManager.enterImmersiveMode(activity)
        }

        // Start auto-refresh
        recoveryManager.startAutoRefresh {
            _needsRefresh.value = true
        }

        // Start connectivity monitoring
        startConnectivityMonitoring(activity)

        // Kick off the sleep timer
        screenStateManager.onUserActivity()
    }

    fun detachActivity() {
        screenStateManager.detachActivity()
        motionDetectionManager.stop()
        sensorWakeManager.stop()
        recoveryManager.stop()
        stopConnectivityMonitoring()
    }

    fun onRefreshConsumed() {
        _needsRefresh.value = false
    }

    fun onWebViewError() {
        recoveryManager.onError {
            _needsRefresh.value = true
        }
    }

    fun onWebViewPageLoaded() {
        recoveryManager.onSuccess()
    }

    fun startMotionDetection(lifecycleOwner: LifecycleOwner) {
        val cfg = config.value
        if (cfg.wakeOnMotion && !motionDetectionManager.isRunning) {
            motionDetectionManager.start(
                lifecycleOwner = lifecycleOwner,
                pollingIntervalMs = cfg.cameraPollingIntervalSeconds * 1000L,
                threshold = cfg.motionSensitivity.threshold,
                onMotion = { onUserInteraction() }
            )
        }
    }

    private fun startSensorsIfNeeded(cfg: KioskConfig) {
        if (cfg.wakeOnProximity || cfg.wakeOnShake) {
            sensorWakeManager.start(
                wakeOnProximity = cfg.wakeOnProximity,
                wakeOnShake = cfg.wakeOnShake,
                onWake = { onUserInteraction() }
            )
        }
    }

    private fun startConnectivityMonitoring(activity: Activity) {
        val cm = activity.getSystemService(ConnectivityManager::class.java)
        connectivityManager = cm

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                _isOnline.value = true
                if (_needsRefresh.value.not()) {
                    _needsRefresh.value = true
                }
            }

            override fun onLost(network: Network) {
                _isOnline.value = false
            }
        }
        connectivityCallback = callback

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        cm.registerNetworkCallback(request, callback)

        // Check initial state
        val activeNetwork = cm.activeNetwork
        val capabilities = activeNetwork?.let { cm.getNetworkCapabilities(it) }
        _isOnline.value = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }

    private fun stopConnectivityMonitoring() {
        connectivityCallback?.let { callback ->
            connectivityManager?.unregisterNetworkCallback(callback)
        }
        connectivityCallback = null
        connectivityManager = null
    }

    override fun onCleared() {
        super.onCleared()
        playlistManager.stop()
        recoveryManager.stop()
        motionDetectionManager.stop()
        sensorWakeManager.stop()
        stopConnectivityMonitoring()
    }
}
