package com.openkiosk.sensors

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Size
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.core.CameraSelector
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "MotionDetection"
// janela longa o bastante para varias comparacoes: com amostra a cada PULSED_POLLING_MS, a
// primeira vira referencia e as demais sao comparadas (~12 comparacoes por pulso).
private const val CAPTURE_WINDOW_MS = 2500L
private const val PULSED_POLLING_MS = 200L
// teto do intervalo CEGO entre pulsos. Com CAPTURE_WINDOW_MS=2500 o ciclo enxerga ~38% do tempo
// (2500 de cada 6500ms), entao quem atravessa em ~2s cai dentro de uma janela na maioria das
// passagens. A camera fica ligada entre pulsos de qualquer jeito (nao ha unbind), entao gap maior
// nao economiza energia — so cega. cameraPulseIntervalSeconds ainda vale para valores menores.
// knob de campo: subir se a CPU do tablet reclamar; baixar se pessoa rapida escapar.
private const val MAX_PULSE_GAP_MS = 4000L
// cadencia de AMOSTRAGEM na analise continua (DIM): distancia entre os dois quadros comparados.
// Tem que ser curta o bastante para pegar alguem atravessando em 1-2s — a config
// cameraPollingIntervalSeconds (5s) e cadencia de PULSO, nunca de amostragem.
// knob de campo: subir se a CPU do tablet reclamar; baixar se pessoa rapida escapar.
private const val CONTINUOUS_POLLING_MS = 200L

@Singleton
class MotionDetectionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var cameraProvider: ProcessCameraProvider? = null
    private var analyzer: MotionDetectionAnalyzer? = null
    private var imageAnalysis: ImageAnalysis? = null
    private val analyzerExecutor = Executors.newSingleThreadExecutor()
    private val pulseHandler = Handler(Looper.getMainLooper())

    /** Incremented by every start()/stop(); an async bind aborts if it changed meanwhile. */
    private var generation = 0

    private var _pulsedMode = false
    private var _pulseIntervalMs = 5000L

    var isRunning: Boolean = false
        private set

    fun start(
        lifecycleOwner: LifecycleOwner,
        threshold: Double,
        onMotion: () -> Unit
    ) {
        Log.d(TAG, "Camera start requested (polling=${CONTINUOUS_POLLING_MS}ms, threshold=$threshold)")
        val myGeneration = ++generation

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                val provider = cameraProviderFuture.get()
                if (myGeneration != generation) {
                    Log.d(TAG, "Camera start aborted — stop() ran while provider was loading")
                    return@addListener
                }
                cameraProvider = provider
                Log.d(TAG, "Camera provider obtained successfully")

                val motionAnalyzer = MotionDetectionAnalyzer(
                    pollingIntervalMs = CONTINUOUS_POLLING_MS,
                    motionThreshold = threshold,
                    onMotionDetected = onMotion
                )
                analyzer = motionAnalyzer

                val analysis = ImageAnalysis.Builder()
                    .setTargetResolution(Size(320, 240))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                    .build()
                    .also { it.setAnalyzer(analyzerExecutor, motionAnalyzer) }
                imageAnalysis = analysis

                val cameraSelector = CameraSelector.Builder()
                    .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                    .build()

                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, cameraSelector, analysis)
                isRunning = true
                Log.d(TAG, "Camera bound to lifecycle — motion detection active")

                // If pulsed mode was requested before camera was ready, start it now
                if (_pulsedMode) {
                    startPulseCycle()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start camera motion detection", e)
                isRunning = false
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun stop() {
        if (isRunning) {
            Log.d(TAG, "Stopping camera motion detection")
        }
        generation++
        stopPulseCycle()
        _pulsedMode = false
        cameraProvider?.unbindAll()
        imageAnalysis = null
        analyzer = null
        isRunning = false
    }

    /** Reaplica a sensibilidade no analyzer vivo quando a config do banco muda. */
    fun updateConfig(threshold: Double) {
        analyzer?.updateThreshold(threshold)
    }

    /**
     * Enable pulsed mode: camera analyzes frames for [CAPTURE_WINDOW_MS] every [intervalMs].
     * Between pulses, the analyzer is cleared — camera stays bound but CPU is idle.
     */
    fun enablePulsedMode(intervalMs: Long) {
        _pulsedMode = true
        // O intervalo escolhido pelo dono MANDA: o menu oferece 5-60s e o teto antigo de
        // 4s engolia todos em silencio. Na tomada nem ha pulso (analise continua), entao
        // aqui e sempre bateria — onde economizar e o ponto.
        _pulseIntervalMs = intervalMs.coerceAtLeast(1_000L)
        if (isRunning) startPulseCycle()
        Log.d(TAG, "Pulsed mode enabled: capture ${CAPTURE_WINDOW_MS}ms every ${_pulseIntervalMs}ms")
    }

    /**
     * Disable pulsed mode: restore continuous analysis.
     */
    fun disablePulsedMode() {
        _pulsedMode = false
        stopPulseCycle()
        if (isRunning) {
            analyzer?.let { a ->
                a.reset()
                a.updatePollingInterval(CONTINUOUS_POLLING_MS)
                imageAnalysis?.setAnalyzer(analyzerExecutor, a)
            }
            Log.d(TAG, "Pulsed mode disabled — continuous analysis restored")
        }
    }

    private fun startPulseCycle() {
        stopPulseCycle()
        // Primeira janela AGORA: entrar em SLEEP nao pode custar um intervalo inteiro de cegueira.
        pulseHandler.post(captureRunnable)
        Log.d(TAG, "Pulse cycle started — capturing immediately")
    }

    private fun stopPulseCycle() {
        pulseHandler.removeCallbacks(captureRunnable)
        pulseHandler.removeCallbacks(sleepRunnable)
    }

    private val captureRunnable: Runnable = object : Runnable {
        override fun run() {
            if (!isRunning || !_pulsedMode) return
            analyzer?.let { a ->
                // a camera nunca foi desligada entre pulsos (so o analyzer): sem aquecimento
                a.reset(warmup = false)
                a.updatePollingInterval(PULSED_POLLING_MS)
                imageAnalysis?.setAnalyzer(analyzerExecutor, a)
            }
            Log.d(TAG, "Pulse: capturing for ${CAPTURE_WINDOW_MS}ms")
            pulseHandler.postDelayed(sleepRunnable, CAPTURE_WINDOW_MS)
        }
    }

    private val sleepRunnable: Runnable = object : Runnable {
        override fun run() {
            if (!isRunning || !_pulsedMode) return
            imageAnalysis?.clearAnalyzer()
            Log.d(TAG, "Pulse: sleeping for ${_pulseIntervalMs}ms")
            pulseHandler.postDelayed(captureRunnable, _pulseIntervalMs)
        }
    }
}
