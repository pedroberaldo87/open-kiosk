package com.openkiosk.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Invariante: o processo só reinicia para devolver memória de vídeo quando ela passou do teto
 * E a tela está apagada — com a tela acesa, reiniciar piscaria na frente de quem está olhando.
 */
class GraphicsRestartTest {

    @Test
    fun `acima do teto com a tela apagada reinicia`() {
        assertTrue(shouldRestartForGraphics(GRAPHICS_RESTART_KB, "SLEEP"))
        assertTrue(shouldRestartForGraphics(974_452L, "DEEP_SLEEP"))
    }

    @Test
    fun `acima do teto com a tela acesa espera`() {
        assertFalse(shouldRestartForGraphics(974_452L, "ACTIVE"))
        assertFalse(shouldRestartForGraphics(974_452L, "DIM"))
        // estado ilegível conta como tela acesa: na dúvida, não pisca na frente de ninguém
        assertFalse(shouldRestartForGraphics(974_452L, null))
    }

    @Test
    fun `abaixo do teto nunca reinicia`() {
        assertFalse(shouldRestartForGraphics(GRAPHICS_RESTART_KB - 1, "SLEEP"))
    }
}
