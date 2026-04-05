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
import android.util.Log
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
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

    val currentUrl: StateFlow<String> get() = _currentUrl.asStateFlow()
    private val _currentUrl = MutableStateFlow("")

    private val _isOnline = MutableStateFlow(true)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val _needsRefresh = MutableStateFlow(false)
    val needsRefresh: StateFlow<Boolean> = _needsRefresh.asStateFlow()

    val recoveryManager = WebViewRecoveryManager()

    private var connectivityCallback: ConnectivityManager.NetworkCallback? = null
    private var connectivityManager: ConnectivityManager? = null
    private var lifecycleOwnerRef: WeakReference<LifecycleOwner>? = null
    private var cameraPermissionGranted = false

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
                Log.d(TAG, "Screen state changed: $state")
                when (state) {
                    ScreenState.ACTIVE -> {
                        Log.d(TAG, "ACTIVE — stopping sensors")
                        motionDetectionManager.stop()
                        sensorWakeManager.stop()
                    }
                    ScreenState.DIM, ScreenState.SLEEP -> {
                        Log.d(TAG, "$state — starting wake sensors")
                        val cfg = config.value
                        startWakeSensors(cfg)
                    }
                }
            }
        }
    }

    fun onUserInteraction() {
        screenStateManager.onUserActivity()
    }

    fun attachActivity(activity: Activity, lifecycleOwner: LifecycleOwner) {
        lifecycleOwnerRef = WeakReference(lifecycleOwner)
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
        lifecycleOwnerRef = null
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

    fun onCameraPermissionGranted() {
        Log.d(TAG, "Camera permission granted")
        cameraPermissionGranted = true
        val state = screenState.value
        if (state == ScreenState.DIM || state == ScreenState.SLEEP) {
            startCameraIfNeeded(config.value)
        }
    }

    private fun startWakeSensors(cfg: KioskConfig) {
        // Start proximity + shake sensors
        if (cfg.wakeOnProximity || cfg.wakeOnShake) {
            sensorWakeManager.start(
                wakeOnProximity = cfg.wakeOnProximity,
                wakeOnShake = cfg.wakeOnShake,
                onWake = { onUserInteraction() }
            )
        }
        // Start camera motion detection
        startCameraIfNeeded(cfg)
    }

    private fun startCameraIfNeeded(cfg: KioskConfig) {
        Log.d(TAG, "startCameraIfNeeded: wakeOnMotion=${cfg.wakeOnMotion}, permissionGranted=$cameraPermissionGranted, isRunning=${motionDetectionManager.isRunning}")
        if (!cfg.wakeOnMotion) { Log.d(TAG, "Camera skip: wakeOnMotion=false"); return }
        if (!cameraPermissionGranted) { Log.d(TAG, "Camera skip: permission not granted"); return }
        val owner = lifecycleOwnerRef?.get()
        if (owner == null) { Log.d(TAG, "Camera skip: lifecycleOwner is null"); return }
        if (!motionDetectionManager.isRunning) {
            Log.d(TAG, "Starting camera motion detection (polling=${cfg.cameraPollingIntervalSeconds}s, threshold=${cfg.motionSensitivity})")
            motionDetectionManager.start(
                lifecycleOwner = owner,
                pollingIntervalMs = cfg.cameraPollingIntervalSeconds * 1000L,
                threshold = cfg.motionSensitivity.threshold,
                onMotion = {
                    Log.d(TAG, "MOTION DETECTED — waking screen")
                    onUserInteraction()
                }
            )
        }
    }

    companion object {
        private const val TAG = "KioskViewModel"
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
