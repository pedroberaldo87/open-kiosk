package com.openkiosk.webview

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Invariante: "desativado" no menu (0 minutos) NAO agenda nada. Antes, o atraso 0 virava
 * recarga em laco continuo — o oposto do que o dono pediu ao desligar.
 */
class AutoRefreshDisabledTest {

    @Test
    fun `intervalo zero nao agenda recarga`() {
        val manager = WebViewRecoveryManager()
        manager.autoRefreshIntervalMs = 0L
        var refreshes = 0

        manager.startAutoRefresh { refreshes++ }

        assertEquals(0, refreshes)
        assertEquals(false, manager.isAutoRefreshScheduled())
    }

    @Test
    fun `intervalo positivo agenda recarga`() {
        val manager = WebViewRecoveryManager()
        manager.autoRefreshIntervalMs = 60_000L

        manager.startAutoRefresh { }

        assertEquals(true, manager.isAutoRefreshScheduled())
        manager.stop()
    }
}
