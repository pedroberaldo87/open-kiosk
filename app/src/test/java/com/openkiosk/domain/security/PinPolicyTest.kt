package com.openkiosk.domain.security

import org.junit.Assert.assertEquals
import org.junit.Test

/** Invariant: the well-known default PIN can never be chosen, and a PIN is exactly 4 ASCII digits. */
class PinPolicyTest {

    @Test
    fun `default PIN 0000 is rejected`() {
        assertEquals(PinValidation.DEFAULT_PIN, PinPolicy.validate("0000"))
    }

    @Test
    fun `a 4 digit non default PIN is accepted`() {
        assertEquals(PinValidation.OK, PinPolicy.validate("2580"))
        assertEquals(PinValidation.OK, PinPolicy.validate("0001"))
    }

    @Test
    fun `wrong length or non digits are rejected`() {
        listOf("", "1", "123", "12345", "abcd", "12 4", "١٢٣٤").forEach {
            assertEquals("input '$it'", PinValidation.BAD_FORMAT, PinPolicy.validate(it))
        }
    }
}
