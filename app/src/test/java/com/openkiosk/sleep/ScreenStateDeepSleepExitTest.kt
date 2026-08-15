package com.openkiosk.sleep

import com.openkiosk.domain.model.ScreenState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Invariante: desligar a agenda noturna com o painel em DEEP_SLEEP tem que soltar a tela.
 * Sem isso o unico caminho de volta (o ramo "janela terminou" do monitor) deixa de rodar
 * e o tablet fica preto para sempre, atravessando reinicio de processo e reboot.
 */
class ScreenStateDeepSleepExitTest {

    @Test
    fun `desligar a agenda com o painel em sono profundo solta a tela`() {
        assertTrue(strandedInDeepSleep(deepSleepEnabled = false, state = ScreenState.DEEP_SLEEP))
    }

    @Test
    fun `agenda ligada nao solta a tela`() {
        assertFalse(strandedInDeepSleep(deepSleepEnabled = true, state = ScreenState.DEEP_SLEEP))
    }

    @Test
    fun `fora do sono profundo nao mexe no estado`() {
        assertFalse(strandedInDeepSleep(deepSleepEnabled = false, state = ScreenState.SLEEP))
        assertFalse(strandedInDeepSleep(deepSleepEnabled = false, state = ScreenState.DIM))
        assertFalse(strandedInDeepSleep(deepSleepEnabled = false, state = ScreenState.ACTIVE))
    }
}
