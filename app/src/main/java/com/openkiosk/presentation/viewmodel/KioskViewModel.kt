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
import com.openkiosk.power.PowerStateMonitor
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
    private val powerStateMonitor: PowerStateMonitor,
    val playlistManager: PlaylistManager
) : ViewModel() {

    // Eagerly: o collect abaixo le o repositorio direto, entao esse stateIn deixou de ter
    // assinante permanente — sem Eagerly config.value ficaria preso no valor de fabrica.
    val config: StateFlow<KioskConfig> = configRepository.observeConfig()
        .stateIn(viewModelScope, SharingStarted.Eagerly, KioskConfig())

    // Nulo = "o banco ainda nao respondeu". Decisao que nao pode nascer do valor de
    // fabrica (pedir camera, entrar em lock task) espera este virar nao-nulo.
    private val _realConfig = MutableStateFlow<KioskConfig?>(null)
    val realConfig: StateFlow<KioskConfig?> = _realConfig.asStateFlow()

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
    private var activityRef: WeakReference<Activity>? = null
    private var cameraPermissionGranted = false

    init {
        playlistManager.start(viewModelScope)

        // Observe config changes and propagate to managers.
        // Le o repositorio DIRETO, nunca o stateIn: o valor de fabrica do placeholder
        // (deepSleepEnabled = false) decidiria a agenda noturna antes do Room responder.
        viewModelScope.launch {
            configRepository.observeConfig().collect { cfg ->
                screenStateManager.configure(
                    activeTimeoutMs = cfg.activeTimeoutSeconds * 1000L,
                    dimTimeoutMs = cfg.dimTimeoutSeconds * 1000L,
                    dimBrightness = cfg.dimBrightnessPercent / 100f,
                    deepSleepEnabled = cfg.deepSleepEnabled,
                    deepSleepStartHour = cfg.deepSleepStartHour,
                    deepSleepEndHour = cfg.deepSleepEndHour
                )
                recoveryManager.autoRefreshIntervalMs = cfg.autoRefreshMinutes * 60 * 1000L
                // Sensibilidade muda no analyzer VIVO: sem isto, trocar nas configuracoes
                // so vale depois de a camera religar.
                motionDetectionManager.updateConfig(cfg.motionSensitivity.threshold)
                val firstRealConfig = _realConfig.value == null
                _realConfig.value = cfg
                // O lock task esperou a config real; aplica agora se a activity ja subiu.
                if (firstRealConfig) {
                    activityRef?.get()?.let { applyKioskLock(it, cfg) }
                }
                // A config real do Room chega DEPOIS do valor de fabrica do stateIn:
                // sem reaplicar aqui, o processo inteiro roda com os padroes do
                // placeholder ate a tela trocar de estado.
                applySensorsFor(screenState.value, cfg)
            }
        }

        // Carregador entrou/saiu: em SLEEP isso troca camera continua <-> pulsada.
        powerStateMonitor.start {
            applySensorsFor(screenState.value, config.value)
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
                applySensorsFor(state, config.value)
            }
        }
    }

    fun onUserInteraction() {
        screenStateManager.onUserActivity()
    }

    /** Brilho do estado guardado, aplicado na criação da activity (antes da 1ª exibição). */
    fun applyLaunchBrightness(activity: Activity) {
        screenStateManager.applyStoredBrightness(activity)
    }

    fun attachActivity(activity: Activity, lifecycleOwner: LifecycleOwner) {
        lifecycleOwnerRef = WeakReference(lifecycleOwner)
        activityRef = WeakReference(activity)
        screenStateManager.attachActivity(activity)

        // Lock task so com a config REAL: o placeholder tem lockTaskEnabled=true e
        // travaria por um instante quem desligou. Sem config ainda, so imersivo.
        applyKioskLock(activity, realConfig.value)

        // Start auto-refresh
        recoveryManager.startAutoRefresh {
            _needsRefresh.value = true
        }

        // Start connectivity monitoring
        startConnectivityMonitoring(activity)

        // Religa o cronometro de sono a partir do estado de tela atual — voltar do
        // onResume nao e presenca de usuario, so toque/sensor/camera acendem a tela
        screenStateManager.resumeTimers()

        // detachActivity desligou camera e sensores; o collect de screenState nao
        // re-emite se o estado nao mudou, entao religa a partir do estado atual
        applySensorsFor(screenState.value, config.value)
    }

    private fun applyKioskLock(activity: Activity, cfg: KioskConfig?) {
        if (cfg?.lockTaskEnabled == true) {
            kioskLockManager.startLockTask(activity)
        } else {
            kioskLockManager.enterImmersiveMode(activity)
        }
    }

    fun detachActivity() {
        lifecycleOwnerRef = null
        activityRef = null
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

    /** Unico lugar que decide quais sensores rodam em cada estado de tela. */
    private fun applySensorsFor(state: ScreenState, cfg: KioskConfig) {
        // App pausado/coberto: detachActivity ja desligou tudo; ligar sensor aqui seria
        // rodar camera com o quiosque fora da frente. O attachActivity religa na volta.
        if (activityRef?.get() == null && (state == ScreenState.DIM || state == ScreenState.SLEEP)) {
            Log.d(TAG, "$state sem activity anexada — sensores ficam desligados")
            return
        }
        when (state) {
            ScreenState.ACTIVE -> {
                Log.d(TAG, "ACTIVE — stopping sensors")
                motionDetectionManager.stop()
                sensorWakeManager.stop()
            }
            ScreenState.DIM -> {
                Log.d(TAG, "DIM — starting wake sensors (continuous camera)")
                startWakeSensors(cfg)
                motionDetectionManager.disablePulsedMode()
            }
            ScreenState.SLEEP -> {
                startWakeSensors(cfg)
                // Decisao do dono: na tomada, analise continua (sem vao cego); na
                // bateria, pulso — economiza CPU aceitando perder quem passa rapido.
                if (powerStateMonitor.isPlugged()) {
                    Log.d(TAG, "SLEEP — na tomada: camera continua")
                    motionDetectionManager.disablePulsedMode()
                } else {
                    Log.d(TAG, "SLEEP — na bateria: camera pulsada")
                    motionDetectionManager.enablePulsedMode(cfg.cameraPulseIntervalSeconds * 1000L)
                }
            }
            ScreenState.DEEP_SLEEP -> {
                Log.d(TAG, "DEEP_SLEEP — stopping all sensors (touch-only wake)")
                motionDetectionManager.stop()
                sensorWakeManager.stop()
            }
        }
    }

    private fun startWakeSensors(cfg: KioskConfig) {
        // Desligar um sensor nas configuracoes tem que PARAR o que ja esta rodando: sem o
        // else, o ouvinte segue acordando o painel ate a tela trocar de estado.
        if (cfg.wakeOnProximity || cfg.wakeOnShake) {
            sensorWakeManager.start(
                wakeOnProximity = cfg.wakeOnProximity,
                wakeOnShake = cfg.wakeOnShake,
                onWake = { onUserInteraction() }
            )
        } else {
            sensorWakeManager.stop()
        }
        startCameraIfNeeded(cfg)
    }

    private fun startCameraIfNeeded(cfg: KioskConfig) {
        Log.d(TAG, "startCameraIfNeeded: wakeOnMotion=${cfg.wakeOnMotion}, permissionGranted=$cameraPermissionGranted, isRunning=${motionDetectionManager.isRunning}")
        if (!cfg.wakeOnMotion) {
            // Desmarcou "acordar por camera" com ela rodando: parar, nao so deixar de ligar.
            Log.d(TAG, "Camera skip: wakeOnMotion=false")
            motionDetectionManager.stop()
            return
        }
        if (!cameraPermissionGranted) { Log.d(TAG, "Camera skip: permission not granted"); return }
        val owner = lifecycleOwnerRef?.get()
        if (owner == null) { Log.d(TAG, "Camera skip: lifecycleOwner is null"); return }
        if (!motionDetectionManager.isRunning) {
            Log.d(TAG, "Starting camera motion detection (threshold=${cfg.motionSensitivity})")
            motionDetectionManager.start(
                lifecycleOwner = owner,
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
                // So recarrega na TRANSICAO offline->online: registerNetworkCallback
                // entrega onAvailable para a rede ja conectada a cada attachActivity
                // (onResume), e recarregar ali joga fora a pagina a cada volta de tela.
                val wasOnline = _isOnline.value
                _isOnline.value = true
                if (!wasOnline && _needsRefresh.value.not()) {
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
        powerStateMonitor.stop()
        stopConnectivityMonitoring()
    }
}
