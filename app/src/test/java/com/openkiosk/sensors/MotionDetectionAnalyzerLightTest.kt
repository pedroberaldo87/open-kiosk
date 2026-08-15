package com.openkiosk.sensors

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Invariante: mudanca de luz que atinge o QUADRO INTEIRO (a propria tela apagando, a lampada da
 * sala) nao e movimento; mudanca em uma REGIAO do quadro e.
 */
class MotionDetectionAnalyzerLightTest {

    private val total = 1000

    private fun flat(value: Int) = ByteArray(total) { value.toByte() }

    @Test
    fun `frame inteiro deslocado 40 de luz nao e movimento`() {
        val prev = flat(100)
        val current = flat(140)
        assertEquals(0.0, changedPixelRatio(current, prev), 0.0001)
    }

    @Test
    fun `frame inteiro escurecido 40 nao e movimento`() {
        val prev = flat(140)
        val current = flat(100)
        assertEquals(0.0, changedPixelRatio(current, prev), 0.0001)
    }

    @Test
    fun `regiao de 10 por cento mudando e movimento`() {
        val prev = flat(100)
        val current = flat(100)
        for (i in 0 until total / 10) current[i] = 160.toByte()
        assertTrue(changedPixelRatio(current, prev) > 0.05)
    }

    @Test
    fun `regiao mudando sob deslocamento global de luz continua sendo movimento`() {
        val prev = flat(100)
        val current = flat(140)
        for (i in 0 until total / 10) current[i] = 220.toByte()
        assertTrue(changedPixelRatio(current, prev) > 0.05)
    }

    @Test
    fun `ruido de ganho acima do limiar por pixel nao e movimento`() {
        // cada pixel muda 38..42 (acima de PIXEL_THRESHOLD=30): so a subtracao do deslocamento
        // global salva este caso — sem ela o quadro inteiro conta como alterado.
        val prev = ByteArray(total) { (8 + it % 3).toByte() }
        val current = ByteArray(total) { (8 + it % 3 + 40 + (it % 5 - 2)).toByte() }
        assertEquals(0.0, changedPixelRatio(current, prev), 0.0001)
    }

    // LIMITE CONHECIDO, sem teste porque nao ha resposta certa nesta funcao: a tela apagando
    // escurece SO a regiao que ela iluminava (ex.: 30% do quadro caindo de 90 para 20, o resto de
    // 12 para 10) e isso conta como movimento — a mediana unica so cancela deslocamento de luz que
    // atinge a maioria dos pixels. Cancelar por faixa de brilho apagaria junto a pessoa passando na
    // frente, que produz exatamente o mesmo padrao (ver MotionDetectionAnalyzerResetTest, onde 30%
    // do quadro indo de 255 para 0 TEM que disparar). Separar os dois casos exige sinal temporal ou
    // de borda, nao cabe em uma comparacao de dois frames.
}
