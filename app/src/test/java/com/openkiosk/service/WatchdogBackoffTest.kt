package com.openkiosk.service

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Invariante: relance que nao surte efeito espaca as tentativas (dobra ate 60s) em vez
 * de repostar notificacao identica a cada 5s por cima do app que cobriu o quiosque.
 */
class WatchdogBackoffTest {

    @Test
    fun `app na frente ou primeira falha mantem o tick base`() {
        assertEquals(5_000L, relaunchDelayMs(0))
        assertEquals(5_000L, relaunchDelayMs(1))
    }

    @Test
    fun `falhas seguidas dobram o espacamento`() {
        assertEquals(10_000L, relaunchDelayMs(2))
        assertEquals(20_000L, relaunchDelayMs(3))
        assertEquals(40_000L, relaunchDelayMs(4))
    }

    @Test
    fun `espacamento tem teto de 60s`() {
        assertEquals(60_000L, relaunchDelayMs(5))
        assertEquals(60_000L, relaunchDelayMs(50))
    }
}
