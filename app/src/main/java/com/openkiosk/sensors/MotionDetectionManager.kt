package com.openkiosk.sensors

import android.content.Context
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

@Singleton
class MotionDetectionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var cameraProvider: ProcessCameraProvider? = null
    private var analyzer: MotionDetectionAnalyzer? = null
    private val analyzerExecutor = Executors.newSingleThreadExecutor()

    var isRunning: Boolean = false
        private set

    fun start(
        lifecycleOwner: LifecycleOwner,
        pollingIntervalMs: Long,
        threshold: Double,
        onMotion: () -> Unit
    ) {
        Log.d(TAG, "Camera start requested (polling=${pollingIntervalMs}ms, threshold=$threshold)")

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

                val imageAnalysis = ImageAnalysis.Builder()
                    .setTargetResolution(Size(320, 240))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                    .build()
                    .also { it.setAnalyzer(analyzerExecutor, motionAnalyzer) }

                val cameraSelector = CameraSelector.Builder()
                    .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                    .build()

                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, cameraSelector, imageAnalysis)
                isRunning = true
                Log.d(TAG, "Camera bound to lifecycle — motion detection active")
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
        cameraProvider?.unbindAll()
        analyzer = null
        isRunning = false
    }

    fun updateConfig(pollingIntervalMs: Long, threshold: Double) {
        analyzer?.updateThreshold(threshold)
    }
}
