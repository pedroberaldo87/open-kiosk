package com.openkiosk.sensors

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import kotlin.math.abs

class MotionDetectionAnalyzer(
    private val pollingIntervalMs: Long = 5000L,
    @Volatile private var motionThreshold: Double = 0.05,
    private val onMotionDetected: () -> Unit
) : ImageAnalysis.Analyzer {

    private var lastAnalysisTimestamp = 0L
    private var previousFrame: ByteArray? = null

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

            val prev = previousFrame
            if (prev != null && prev.size == currentFrame.size) {
                var changedPixels = 0
                val totalPixels = currentFrame.size
                for (i in currentFrame.indices) {
                    val diff = abs((currentFrame[i].toInt() and 0xFF) - (prev[i].toInt() and 0xFF))
                    if (diff > 30) {
                        changedPixels++
                    }
                }
                val changeRatio = changedPixels.toDouble() / totalPixels
                if (changeRatio > motionThreshold) {
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
}
