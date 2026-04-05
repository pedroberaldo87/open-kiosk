package com.openkiosk.sensors

import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import kotlin.math.abs

private const val TAG = "MotionDetection"
private const val PIXEL_THRESHOLD = 15 // individual pixel change threshold (was 30, lowered for low-light)

class MotionDetectionAnalyzer(
    @Volatile private var pollingIntervalMs: Long = 5000L,
    @Volatile private var motionThreshold: Double = 0.05,
    private val onMotionDetected: () -> Unit
) : ImageAnalysis.Analyzer {

    private var lastAnalysisTimestamp = 0L
    private var previousFrame: ByteArray? = null
    private var frameCount = 0

    override fun analyze(image: ImageProxy) {
        try {
            val currentTimestamp = System.currentTimeMillis()
            if (currentTimestamp - lastAnalysisTimestamp < pollingIntervalMs) {
                return
            }
            lastAnalysisTimestamp = currentTimestamp

            val yPlane = image.planes[0]
            val buffer = yPlane.buffer
            val ySize = buffer.remaining()
            val currentFrame = ByteArray(ySize)
            buffer.get(currentFrame)

            frameCount++

            // Diagnostic: log pixel values to verify camera is returning real data
            if (frameCount <= 3 || frameCount % 20 == 0) {
                val avgPixel = currentFrame.map { it.toInt() and 0xFF }.average()
                val first10 = currentFrame.take(10).map { it.toInt() and 0xFF }
                Log.d(TAG, "Frame #$frameCount: size=$ySize, avgPixel=%.1f, first10=$first10".format(avgPixel))
            }

            if (frameCount == 1) {
                Log.d(TAG, "First frame captured (${ySize} bytes, ${image.width}x${image.height})")
            }

            val prev = previousFrame
            if (prev != null && prev.size == currentFrame.size) {
                var changedPixels = 0
                val totalPixels = currentFrame.size
                for (i in currentFrame.indices) {
                    val diff = abs((currentFrame[i].toInt() and 0xFF) - (prev[i].toInt() and 0xFF))
                    if (diff > PIXEL_THRESHOLD) {
                        changedPixels++
                    }
                }
                val changeRatio = changedPixels.toDouble() / totalPixels
                // Log every frame for debugging (temporary)
                Log.d(TAG, "Frame #$frameCount: changeRatio=%.6f, threshold=%.4f, changed=$changedPixels/$totalPixels".format(changeRatio, motionThreshold))
                if (changeRatio > motionThreshold) {
                    Log.d(TAG, "MOTION DETECTED! changeRatio=%.4f > threshold=%.4f — triggering wake".format(changeRatio, motionThreshold))
                    onMotionDetected()
                }
            }

            previousFrame = currentFrame
        } finally {
            image.close()
        }
    }

    fun updateThreshold(threshold: Double) {
        motionThreshold = threshold
    }

    fun updatePollingInterval(intervalMs: Long) {
        pollingIntervalMs = intervalMs
    }
}
