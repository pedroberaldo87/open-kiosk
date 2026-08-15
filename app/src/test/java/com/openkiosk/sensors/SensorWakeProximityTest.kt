package com.openkiosk.sensors

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SensorWakeProximityTest {

    @Test
    fun `leitura perto sustentada nao redispara`() {
        val maxRange = 5f
        // primeira aproximacao dispara
        assertTrue(isApproach(prevNear = false, value = 0f, maxRange = maxRange))
        // mao parada em cima do sensor nao dispara de novo
        assertFalse(isApproach(prevNear = true, value = 0f, maxRange = maxRange))
    }

    @Test
    fun `leitura longe constante nunca dispara`() {
        val maxRange = 100f
        assertFalse(isApproach(prevNear = false, value = 99f, maxRange = maxRange))
        // valor abaixo do maximo mas ainda longe (reflexo espurio) nao acende a tela
        assertFalse(isApproach(prevNear = false, value = 40f, maxRange = maxRange))
    }

    @Test
    fun `registro com leitura perto sustentada nao dispara`() {
        // aparelho em nicho: a primeira entrega apos registrar ja vem "perto" e e so linha de base
        assertFalse(isApproach(prevNear = null, value = 0f, maxRange = 5f))
    }

    @Test
    fun `transicao longe para perto dispara`() {
        assertTrue(isApproach(prevNear = false, value = 2f, maxRange = 5f))
        assertTrue(isApproach(prevNear = false, value = 3f, maxRange = 100f))
    }
}
