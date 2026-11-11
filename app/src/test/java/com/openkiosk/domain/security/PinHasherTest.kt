package com.openkiosk.domain.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Invariant: the stored PIN is a salted PBKDF2 hash; the raw PIN never appears in it. */
class PinHasherTest {
    private val fast = 1_000

    @Test
    fun `hash does not contain the PIN and is recognised as a hash`() {
        val h = PinHasher.hash("4821", fast)
        assertFalse(h.contains("4821"))
        assertTrue(PinHasher.isHashed(h))
    }

    @Test
    fun `plain digits are not a hash`() {
        assertFalse(PinHasher.isHashed("4821"))
        assertFalse(PinHasher.isHashed(null))
        assertFalse(PinHasher.isHashed(""))
        assertFalse(PinHasher.isHashed("pbkdf2-sha256\$abc"))
    }

    @Test
    fun `same PIN hashed twice differs because of the random salt`() {
        assertNotEquals(PinHasher.hash("4821", fast), PinHasher.hash("4821", fast))
    }

    @Test
    fun `verify accepts the right PIN and rejects a wrong one`() {
        val h = PinHasher.hash("4821", fast)
        assertTrue(PinHasher.verify("4821", h))
        assertFalse(PinHasher.verify("4822", h))
        assertFalse(PinHasher.verify("", h))
    }

    @Test
    fun `verify uses the iteration count stored in the hash`() {
        val low = PinHasher.hash("4821", 500)
        val high = PinHasher.hash("4821", 2_000)
        assertTrue(PinHasher.verify("4821", low))
        assertTrue(PinHasher.verify("4821", high))
    }

    @Test
    fun `verify never matches a malformed stored value`() {
        assertFalse(PinHasher.verify("4821", "4821"))
        assertFalse(PinHasher.verify("4821", "pbkdf2-sha256\$1000\$!!\$!!"))
        assertFalse(PinHasher.verify("4821", ""))
    }

    @Test
    fun `default iteration count is at least 50000`() {
        assertTrue(PinHasher.DEFAULT_ITERATIONS >= 50_000)
        assertEquals("pbkdf2-sha256", PinHasher.hash("1234", fast).substringBefore('\$'))
    }
}
