package com.openkiosk.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Invariante: o relance do watchdog decide pelo ESTADO GUARDADO, não pelo painel.
 * Estado guardado de tela visível (ACTIVE/DIM) => notificação com full screen intent;
 * estado de sono (SLEEP/DEEP_SLEEP) => só o tiro silencioso do startActivity.
 */
class KioskWatchdogRelaunchTest {

    @Test
    fun `relance com estado guardado de tela visivel usa full screen intent`() {
        assertTrue(shouldWakeOnRelaunch("ACTIVE"))
        assertTrue(shouldWakeOnRelaunch("DIM"))
    }

    @Test
    fun `relance com estado guardado de sono e silencioso`() {
        assertFalse(shouldWakeOnRelaunch("SLEEP"))
        assertFalse(shouldWakeOnRelaunch("DEEP_SLEEP"))
    }

    @Test
    fun `sem estado guardado o relance acende a tela`() {
        assertTrue(shouldWakeOnRelaunch(null))
        assertTrue(shouldWakeOnRelaunch("lixo"))
    }
}
