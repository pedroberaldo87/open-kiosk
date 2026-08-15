package com.openkiosk.sensors

import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import kotlin.math.abs

private const val TAG = "MotionDetection"
// Limiar por pixel APOS descontar o deslocamento global de luz. 30 descartava pessoa em luz
// fraca (delta real de 10-25 no Fire), 15 deixava passar ruido de ganho: 20 e o meio,
// calibrado com o piso de ruido medido no aparelho (ver changeRatio no log).
// knob de campo: subir se acordar sozinho no escuro; baixar se pessoa em luz fraca escapar.
private const val PIXEL_THRESHOLD = 20
// ponytail: 1 frame de descarte cobre a convergencia do auto-exposure logo apos o bind; subir se o
// device demorar mais de uma janela de polling para estabilizar o brilho.
private const val WARMUP_FRAMES = 1

/**
 * Fracao de pixels que mudou entre dois planos Y, DESCONTADO o deslocamento global de luz.
 * A tela apagando ou a lampada da sala mudam o brilho do quadro inteiro; isso desloca a mediana
 * das diferencas, nao a forma delas. Subtraindo a mediana, so sobra o que mudou em alguma REGIAO.
 */
fun changedPixelRatio(current: ByteArray, prev: ByteArray): Double {
    // histograma das diferencas (-255..255) para achar a mediana em uma passada
    val histogram = IntArray(511)
    for (i in current.indices) {
        val diff = (current[i].toInt() and 0xFF) - (prev[i].toInt() and 0xFF)
        histogram[diff + 255]++
    }
    var accumulated = 0
    var globalShift = 0
    val middle = current.size / 2
    for (bucket in histogram.indices) {
        accumulated += histogram[bucket]
        if (accumulated > middle) {
            globalShift = bucket - 255
            break
        }
    }
    var changed = 0
    for (bucket in histogram.indices) {
        if (abs((bucket - 255) - globalShift) > PIXEL_THRESHOLD) changed += histogram[bucket]
    }
    return changed.toDouble() / current.size
}

class MotionDetectionAnalyzer(
    @Volatile private var pollingIntervalMs: Long = 5000L,
    @Volatile private var motionThreshold: Double = 0.05,
    private val onMotionDetected: () -> Unit
) : ImageAnalysis.Analyzer {

    @Volatile private var lastAnalysisTimestamp = 0L
    @Volatile private var previousFrame: ByteArray? = null
    @Volatile private var frameCount = 0
    @Volatile private var warmupRemaining = WARMUP_FRAMES

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

            // Frames logo apos o bind saem com a exposicao automatica ainda convergindo: o quadro
            // inteiro muda de brilho e viraria "movimento". Descarta sem gravar como referencia.
            if (warmupRemaining > 0) {
                warmupRemaining--
                Log.d(TAG, "Frame #$frameCount descartado (aquecimento da camera)")
                return
            }

            val prev = previousFrame
            if (prev != null && prev.size == currentFrame.size) {
                val changeRatio = changedPixelRatio(currentFrame, prev)
                // Sinal cru para calibrar o limiar em campo: o piso de ruido do aparelho
                // (sala parada, luz baixa) tem que ficar bem abaixo da sensibilidade.
                if (changeRatio > 0.0) {
                    Log.d(TAG, "changeRatio=%.5f (limiar=%.4f)".format(changeRatio, motionThreshold))
                }
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

    /**
     * Descarta o frame de referencia: a proxima janela comeca do zero, sem comparar com o passado.
     * [warmup] = false so quando a CAMERA ficou ligada desde a janela anterior (ciclo pulsado, que
     * apenas desliga o analyzer): sem religar a camera nao ha exposicao convergindo, e o descarte
     * custaria uma comparacao inteira da janela curta. Apos bind, sempre true.
     */
    fun reset(warmup: Boolean = true) {
        previousFrame = null
        lastAnalysisTimestamp = 0L
        warmupRemaining = if (warmup) WARMUP_FRAMES else 0
    }

    fun updateThreshold(threshold: Double) {
        motionThreshold = threshold
    }

    fun updatePollingInterval(intervalMs: Long) {
        pollingIntervalMs = intervalMs
    }
}
