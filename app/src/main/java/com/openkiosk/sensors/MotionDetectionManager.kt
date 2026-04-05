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
private const val CAPTURE_WINDOW_MS = 1500L
private const val PULSED_POLLING_MS = 500L

@Singleton
class MotionDetectionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var cameraProvider: ProcessCameraProvider? = null
    private var analyzer: MotionDetectionAnalyzer? = null
    private var imageAnalysis: ImageAnalysis? = null
    private val analyzerExecutor = Executors.newSingleThreadExecutor()
    private val pulseHandler = Handler(Looper.getMainLooper())

    private var _pulsedMode = false
    private var _pulseIntervalMs = 5000L
    private var originalPollingIntervalMs = 5000L

    var isRunning: Boolean = false
        private set

    fun start(
        lifecycleOwner: LifecycleOwner,
        pollingIntervalMs: Long,
        threshold: Double,
        onMotion: () -> Unit
    ) {
        Log.d(TAG, "Camera start requested (polling=${pollingIntervalMs}ms, threshold=$threshold)")
        originalPollingIntervalMs = pollingIntervalMs

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                val provider = cameraProviderFuture.get()
                cameraProvider = provider
                Log.d(TAG, "Camera provider obtained successfully")

                val motionAnalyzer = MotionDetectionAnalyzer(
                    pollingIntervalMs = pollingIntervalMs,
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
        stopPulseCycle()
        _pulsedMode = false
        cameraProvider?.unbindAll()
        imageAnalysis = null
        analyzer = null
        isRunning = false
    }

    fun updateConfig(pollingIntervalMs: Long, threshold: Double) {
        analyzer?.updateThreshold(threshold)
    }

    /**
     * Enable pulsed mode: camera analyzes frames for [CAPTURE_WINDOW_MS] every [intervalMs].
     * Between pulses, the analyzer is cleared — camera stays bound but CPU is idle.
     */
    fun enablePulsedMode(intervalMs: Long) {
        _pulsedMode = true
        _pulseIntervalMs = intervalMs
        if (isRunning) startPulseCycle()
        Log.d(TAG, "Pulsed mode enabled: capture ${CAPTURE_WINDOW_MS}ms every ${intervalMs}ms")
    }

    /**
     * Disable pulsed mode: restore continuous analysis.
     */
    fun disablePulsedMode() {
        _pulsedMode = false
        stopPulseCycle()
        if (isRunning) {
            analyzer?.let { a ->
                a.updatePollingInterval(originalPollingIntervalMs)
                imageAnalysis?.setAnalyzer(analyzerExecutor, a)
            }
            Log.d(TAG, "Pulsed mode disabled — continuous analysis restored")
        }
    }

    private fun startPulseCycle() {
        stopPulseCycle()
        // Immediately clear analyzer, schedule first capture
        imageAnalysis?.clearAnalyzer()
        pulseHandler.postDelayed(captureRunnable, _pulseIntervalMs)
        Log.d(TAG, "Pulse cycle started — sleeping for ${_pulseIntervalMs}ms")
    }

    private fun stopPulseCycle() {
        pulseHandler.removeCallbacks(captureRunnable)
        pulseHandler.removeCallbacks(sleepRunnable)
    }

    private val captureRunnable: Runnable = object : Runnable {
        override fun run() {
            if (!isRunning || !_pulsedMode) return
            analyzer?.let { a ->
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
