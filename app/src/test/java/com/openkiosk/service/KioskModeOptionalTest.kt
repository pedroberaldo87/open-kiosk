package com.openkiosk.service

import com.openkiosk.domain.model.KioskConfig
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Modo quiosque e opcional e vem desligado: o dono pode sair do app quando quiser. */
class KioskModeOptionalTest {

    @Test
    fun `modo quiosque vem desligado`() {
        assertFalse(KioskConfig().lockTaskEnabled)
    }

    @Test
    fun `desligado o watchdog nao relanca quem saiu do app`() {
        assertFalse(shouldRelaunch(isForeground = false, keepInFront = false))
    }

    @Test
    fun `ligado o watchdog relanca quando o app sai da frente`() {
        assertTrue(shouldRelaunch(isForeground = false, keepInFront = true))
        assertFalse(shouldRelaunch(isForeground = true, keepInFront = true))
    }

    @Test
    fun `reinicio por memoria nao abre o quiosque por cima de outro app`() {
        assertFalse(mayRestartProcess(isForeground = false, keepInFront = false))
        assertTrue(mayRestartProcess(isForeground = true, keepInFront = false))
        assertTrue(mayRestartProcess(isForeground = false, keepInFront = true))
    }
}
