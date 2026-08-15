package com.openkiosk.sensors

import androidx.camera.core.ImageProxy
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer

/**
 * Invariante: no ciclo pulsado a camera NUNCA e desligada entre pulsos, entao a exposicao ja
 * esta convergida — o primeiro frame da nova janela e referencia valida, nao lixo de aquecimento.
 * Descartar esse frame custa uma comparacao inteira de uma janela curta.
 */
class MotionDetectionAnalyzerPulseWarmupTest {

    // 30% do quadro carrega o valor, o resto fica fixo: mudanca de REGIAO, nao de luz global
    private fun frame(value: Int): ImageProxy {
        val bytes = ByteArray(100) { if (it < 30) value.toByte() else 0 }
        val plane = mock<ImageProxy.PlaneProxy>()
        whenever(plane.buffer).thenReturn(ByteBuffer.wrap(bytes))
        val image = mock<ImageProxy>()
        whenever(image.planes).thenReturn(arrayOf(plane))
        whenever(image.width).thenReturn(10)
        whenever(image.height).thenReturn(10)
        return image
    }

    @Test
    fun `com a camera ligada entre pulsos o primeiro frame da janela ja vale como referencia`() {
        var hits = 0
        val analyzer = MotionDetectionAnalyzer(0L, 0.05) { hits++ }

        analyzer.analyze(frame(0)) // aquecimento do bind
        analyzer.analyze(frame(0)) // referencia da primeira janela

        analyzer.reset(warmup = false) // fim do pulso: analyzer religado, camera nunca desligou
        analyzer.analyze(frame(0)) // referencia imediata da nova janela
        analyzer.analyze(frame(255))
        assertEquals("a segunda amostra da janela ja tem que ser comparada", 1, hits)
    }

    @Test
    fun `reset apos bind continua descartando o frame de aquecimento`() {
        var hits = 0
        val analyzer = MotionDetectionAnalyzer(0L, 0.05) { hits++ }

        analyzer.analyze(frame(0))
        analyzer.reset()
        analyzer.analyze(frame(255)) // aquecimento: nao pode virar referencia nem comparar
        assertEquals("apos reset() padrao o frame de aquecimento continua descartado", 0, hits)
    }
}
