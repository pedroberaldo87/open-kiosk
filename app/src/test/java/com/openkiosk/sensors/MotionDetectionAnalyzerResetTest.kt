package com.openkiosk.sensors

import androidx.camera.core.ImageProxy
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer

/**
 * Invariante: apos reset(), o primeiro frame da nova janela nao e comparado com o frame
 * antigo — frame velho nao pode gerar deteccao de movimento.
 */
class MotionDetectionAnalyzerResetTest {

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
    fun `primeiro frame apos o bind e descartado e nao vira referencia`() {
        var hits = 0
        val analyzer = MotionDetectionAnalyzer(0L, 0.05) { hits++ }

        // frame de aquecimento (exposicao automatica ainda convergindo) + frame de referencia
        analyzer.analyze(frame(0))
        analyzer.analyze(frame(255))
        assertEquals("o frame de aquecimento nao pode virar referencia", 0, hits)

        analyzer.analyze(frame(0))
        assertEquals("depois do aquecimento, a mudanca real dispara", 1, hits)
    }

    @Test
    fun `frame antigo comparado apos reset nao dispara movimento`() {
        var hits = 0
        val analyzer = MotionDetectionAnalyzer(0L, 0.05) { hits++ }

        analyzer.analyze(frame(0)) // aquecimento
        analyzer.analyze(frame(0))
        analyzer.analyze(frame(255))
        assertEquals("sem reset, a mudanca deve disparar movimento", 1, hits)

        hits = 0
        val analyzer2 = MotionDetectionAnalyzer(0L, 0.05) { hits++ }
        analyzer2.analyze(frame(0)) // aquecimento
        analyzer2.analyze(frame(0))
        analyzer2.reset()
        analyzer2.analyze(frame(255)) // aquecimento da nova janela
        analyzer2.analyze(frame(255))
        assertEquals("apos reset, o frame velho nao pode disparar movimento", 0, hits)
    }
}
