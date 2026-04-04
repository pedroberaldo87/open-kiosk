package com.openkiosk.sensors

import android.content.Context
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
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val provider = cameraProviderFuture.get()
            cameraProvider = provider

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
        }, ContextCompat.getMainExecutor(context))
    }

    fun stop() {
        cameraProvider?.unbindAll()
        analyzer = null
        isRunning = false
    }

    fun updateConfig(pollingIntervalMs: Long, threshold: Double) {
        analyzer?.updateThreshold(threshold)
        // pollingIntervalMs requires a new analyzer instance since it's a constructor param;
        // for runtime changes, threshold update is the primary use case.
        // Full restart via stop()/start() handles pollingIntervalMs changes.
    }
}
