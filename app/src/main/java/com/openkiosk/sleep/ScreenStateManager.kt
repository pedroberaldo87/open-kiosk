package com.openkiosk.sleep

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.WindowManager
import com.openkiosk.domain.model.ScreenState
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import com.openkiosk.data.local.KioskPrefs
import java.lang.ref.WeakReference
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "ScreenState"
private const val DEEP_SLEEP_CHECK_INTERVAL_MS = 60_000L // check every minute

/**
 * Usada pelo watchdog (via shouldWakeOnRelaunch) para decidir se a notificação de relance
 * leva full screen intent: só quando o estado guardado era de painel visível (ACTIVE/DIM).
 * Em SLEEP/DEEP_SLEEP o relance é silencioso — acender seria ligar o painel de madrugada
 * sem ninguém na frente.
 */
fun wakesScreenOnLaunch(state: ScreenState): Boolean =
    state == ScreenState.ACTIVE || state == ScreenState.DIM

/**
 * Brilho do painel por estado de tela: cheio em ACTIVE, o valor configurado em DIM,
 * preto em SLEEP/DEEP_SLEEP. Pura para o teste cobrir os quatro estados.
 */
internal fun brightnessFor(state: ScreenState, dimBrightness: Float): Float = when (state) {
    ScreenState.ACTIVE -> WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
    ScreenState.DIM -> dimBrightness
    ScreenState.SLEEP, ScreenState.DEEP_SLEEP -> 0.0f
}

/**
 * Painel preso em sono profundo: com a agenda noturna desligada, o monitor que faria a
 * tela voltar nunca mais roda, entao o desligamento tem que soltar a tela na hora.
 */
fun strandedInDeepSleep(deepSleepEnabled: Boolean, state: ScreenState): Boolean =
    !deepSleepEnabled && state == ScreenState.DEEP_SLEEP

@Singleton
class ScreenStateManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = KioskPrefs.of(context)

    // O estado sobrevive à morte do processo: quando o watchdog relança a activity,
    // ela volta apagada se estava apagada, em vez de voltar ACTIVE com brilho cheio.
    private val _screenState = MutableStateFlow(
        runCatching { ScreenState.valueOf(prefs.getString(KioskPrefs.KEY_SCREEN_STATE, null) ?: "") }
            .getOrDefault(ScreenState.ACTIVE)
    )
    val screenState: StateFlow<ScreenState> = _screenState.asStateFlow()

    private val handler = Handler(Looper.getMainLooper())
    private var activityRef: WeakReference<Activity>? = null

    private var activeTimeoutMs: Long = 30_000L
    private var dimTimeoutMs: Long = 60_000L
    private var dimBrightness: Float = 0.1f
    private var deepSleepEnabled: Boolean = false
    private var deepSleepStartHour: Int = 22
    private var deepSleepEndHour: Int = 6

    private val dimRunnable = Runnable { transitionToDim() }
    private val sleepRunnable = Runnable { transitionToSleep() }

    private val deepSleepCheckRunnable: Runnable = object : Runnable {
        override fun run() {
            if (!deepSleepEnabled) return
            if (isInDeepSleepWindow()) {
                val current = _screenState.value
                if (current != ScreenState.ACTIVE && current != ScreenState.DEEP_SLEEP) {
                    transitionToDeepSleep()
                }
            } else if (_screenState.value == ScreenState.DEEP_SLEEP) {
                // Exited deep sleep window — wake up
                Log.d(TAG, "Deep sleep window ended — waking")
                onUserActivity()
            }
            handler.postDelayed(this, DEEP_SLEEP_CHECK_INTERVAL_MS)
        }
    }

    fun configure(
        activeTimeoutMs: Long,
        dimTimeoutMs: Long,
        dimBrightness: Float,
        deepSleepEnabled: Boolean = false,
        deepSleepStartHour: Int = 22,
        deepSleepEndHour: Int = 6
    ) {
        this.activeTimeoutMs = activeTimeoutMs
        this.dimTimeoutMs = dimTimeoutMs
        this.dimBrightness = dimBrightness.coerceIn(0.01f, 1.0f)
        this.deepSleepEnabled = deepSleepEnabled
        this.deepSleepStartHour = deepSleepStartHour
        this.deepSleepEndHour = deepSleepEndHour

        // attachActivity roda ANTES da config real no arranque frio: o brilho de DIM foi
        // aplicado com o default. Reaplica com o valor do banco.
        applyBrightness(brightnessFor(_screenState.value))

        // Restart deep sleep monitoring
        handler.removeCallbacks(deepSleepCheckRunnable)
        if (deepSleepEnabled) {
            handler.post(deepSleepCheckRunnable)
        } else if (strandedInDeepSleep(deepSleepEnabled, _screenState.value)) {
            // So com a janela na frente: destacado, onUserActivity() gravaria ACTIVE sem
            // painel e o cronometro seguiria ate SLEEP fantasma. attachActivity solta.
            if (activityRef?.get() != null) {
                Log.d(TAG, "Deep sleep desligado com o painel apagado — soltando a tela")
                onUserActivity()
            }
        }
    }

    fun attachActivity(activity: Activity) {
        activityRef = WeakReference(activity)
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Activity recriada (relançamento do watchdog) entra no brilho do estado guardado.
        applyBrightness(brightnessFor(_screenState.value))
        // Sono profundo preso sem agenda ligada: a soltura ficou esperando a janela voltar
        // (configure() nao pode solta-la destacada, senao grava estado sem painel).
        if (strandedInDeepSleep(deepSleepEnabled, _screenState.value)) {
            Log.d(TAG, "Deep sleep preso e activity de volta — soltando a tela")
            onUserActivity()
        }
    }

    /**
     * Brilho do estado guardado aplicado ANTES da primeira exibição da activity: o display
     * é sempre ligado no relance (senão não há câmera, sensor nem toque), mas o painel volta
     * preto se o estado guardado era SLEEP/DEEP_SLEEP — mesmo visual do app vivo dormindo.
     */
    fun applyStoredBrightness(activity: Activity) {
        activity.window.attributes = activity.window.attributes.apply {
            screenBrightness = brightnessFor(_screenState.value)
        }
    }

    private fun brightnessFor(state: ScreenState): Float = brightnessFor(state, dimBrightness)

    private fun applyBrightness(value: Float) {
        val activity = activityRef?.get() ?: return
        activity.runOnUiThread {
            val window = activity.window
            window.attributes = window.attributes.apply { screenBrightness = value }
        }
    }

    private fun setState(state: ScreenState) {
        _screenState.value = state
        prefs.edit().putString(KioskPrefs.KEY_SCREEN_STATE, state.name).apply()
    }

    fun detachActivity() {
        activityRef = null
        // Sem activity nao ha painel para escurecer nem sensores a religar: transicao que
        // dispara destacada so grava estado fantasma. resumeTimers() rearma na volta.
        handler.removeCallbacks(dimRunnable)
        handler.removeCallbacks(sleepRunnable)
        handler.removeCallbacks(deepSleepCheckRunnable)
    }

    fun onUserActivity() {
        handler.removeCallbacks(dimRunnable)
        handler.removeCallbacks(sleepRunnable)

        // Sem janela na frente a maquina de estado FICA PARADA. Avancar aqui gravaria em
        // disco um ACTIVE→DIM→SLEEP que nenhum painel viveu, e o watchdog leria esse
        // SLEEP fantasma como "nao acenda" — deixando o quiosque atras do anuncio.
        if (activityRef?.get() == null) {
            Log.d(TAG, "Atividade sem activity anexada — maquina de estado congelada")
            return
        }

        if (_screenState.value != ScreenState.ACTIVE) {
            transitionToActive()
        }

        handler.postDelayed(dimRunnable, activeTimeoutMs)
    }

    /**
     * Religa o cronometro de sono a partir do estado de tela atual, sem carimbar
     * presenca de usuario (usado no onResume da activity).
     */
    fun resumeTimers() {
        handler.removeCallbacks(dimRunnable)
        handler.removeCallbacks(sleepRunnable)
        when (_screenState.value) {
            ScreenState.ACTIVE -> handler.postDelayed(dimRunnable, activeTimeoutMs)
            ScreenState.DIM -> handler.postDelayed(sleepRunnable, dimTimeoutMs)
            ScreenState.SLEEP, ScreenState.DEEP_SLEEP -> {}
        }
        // detachActivity() retirou o monitor de sono profundo; sem repor aqui, a saida
        // da janela noturna (unico caminho de volta do DEEP_SLEEP) nunca roda.
        if (deepSleepEnabled) {
            handler.removeCallbacks(deepSleepCheckRunnable)
            handler.post(deepSleepCheckRunnable)
        }
    }

    fun isInDeepSleepWindow(): Boolean {
        if (!deepSleepEnabled) return false
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return if (deepSleepStartHour > deepSleepEndHour) {
            // Crosses midnight: e.g., 22:00-06:00
            hour >= deepSleepStartHour || hour < deepSleepEndHour
        } else {
            // Same day: e.g., 13:00-17:00
            hour >= deepSleepStartHour && hour < deepSleepEndHour
        }
    }

    private fun transitionToActive() {
        activityRef?.get()?.let { activity ->
            activity.runOnUiThread {
                activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
        applyBrightness(brightnessFor(ScreenState.ACTIVE))

        setState(ScreenState.ACTIVE)
        Log.d(TAG, "→ ACTIVE")
    }

    private fun transitionToDim() {
        // If in deep sleep window, skip DIM and go straight to DEEP_SLEEP
        if (isInDeepSleepWindow()) {
            transitionToDeepSleep()
            return
        }

        applyBrightness(brightnessFor(ScreenState.DIM))

        setState(ScreenState.DIM)
        Log.d(TAG, "→ DIM")

        handler.postDelayed(sleepRunnable, dimTimeoutMs)
    }

    private fun transitionToSleep() {
        // If in deep sleep window, go to DEEP_SLEEP instead
        if (isInDeepSleepWindow()) {
            transitionToDeepSleep()
            return
        }

        applyBrightness(brightnessFor(ScreenState.SLEEP))

        setState(ScreenState.SLEEP)
        Log.d(TAG, "→ SLEEP")
    }

    private fun transitionToDeepSleep() {
        handler.removeCallbacks(dimRunnable)
        handler.removeCallbacks(sleepRunnable)

        applyBrightness(brightnessFor(ScreenState.DEEP_SLEEP))

        setState(ScreenState.DEEP_SLEEP)
        Log.d(TAG, "→ DEEP_SLEEP (scheduled: ${deepSleepStartHour}:00-${deepSleepEndHour}:00)")
    }
}
