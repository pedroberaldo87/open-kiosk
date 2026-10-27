package com.openkiosk.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Invariante: o vigia só deixa a chave quieta quando ela está em "0". O pacote de ofertas
 * escreve "1" no boot, e chave ausente (null) é o padrão da Amazon, que também mostra anúncio.
 */
class LockscreenAdTest {

    @Test
    fun `zero e o unico valor desligado`() {
        assertFalse(lockscreenAdIsOn("0"))
    }

    @Test
    fun `um, ausente ou lixo contam como anuncio ligado`() {
        assertTrue(lockscreenAdIsOn("1"))
        assertTrue(lockscreenAdIsOn(null))
        assertTrue(lockscreenAdIsOn("true"))
    }
}
