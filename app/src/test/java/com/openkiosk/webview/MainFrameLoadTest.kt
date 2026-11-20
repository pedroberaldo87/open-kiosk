package com.openkiosk.webview

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Invariante: a pagina de erro do WebView tambem "termina de carregar". Contar esse fim como
 * sucesso cancelava a nova tentativa recem-agendada — medido no Fire em 2026-11-20: "Scheduling
 * retry #1" duas vezes e nenhum "Retry triggered" em 45 s, com a tela presa no erro.
 */
class MainFrameLoadTest {

    @Test
    fun `carga sem erro termina com sucesso`() {
        val load = MainFrameLoad()

        load.started()

        assertEquals(true, load.finishedOk())
    }

    @Test
    fun `fim depois de erro nao e sucesso`() {
        val load = MainFrameLoad()

        load.started()
        load.failed()

        assertEquals(false, load.finishedOk())
    }

    @Test
    fun `nova carga apaga o erro da anterior`() {
        val load = MainFrameLoad()
        load.started()
        load.failed()

        load.started()

        assertEquals(true, load.finishedOk())
    }
}
