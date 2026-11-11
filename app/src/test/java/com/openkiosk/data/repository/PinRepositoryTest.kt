package com.openkiosk.data.repository

import com.openkiosk.data.FakeConfigDao
import com.openkiosk.domain.security.PinHasher
import com.openkiosk.domain.security.PinVerifyResult
import com.openkiosk.domain.security.SetPinResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Invariants: protection cannot be on while the PIN is the default 0000; the stored PIN is a
 * hash; wrong guesses are rate limited and the limit survives a process restart.
 */
class PinRepositoryTest {
    private val dao = FakeConfigDao()
    private var now = 1_000_000L

    private fun repo() = PinRepository(dao, { now }, 1_000)

    private fun pinEnabledRow() = dao.rows.value["pinEnabled"]

    // ---- finding: protection on with the default 0000 ----

    @Test
    fun `cannot enable protection while the PIN is the default`() = runTest {
        val r = repo()
        assertTrue(r.isPinDefault())
        assertFalse(r.tryEnableProtection())
        assertNotEquals("true", pinEnabledRow())
    }

    @Test
    fun `cannot enable protection by choosing 0000`() = runTest {
        val r = repo()
        assertEquals(SetPinResult.DEFAULT_REJECTED, r.enableProtectionWithPin("0000"))
        assertNotEquals("true", pinEnabledRow())
        assertNull(dao.rows.value["pin"])
    }

    @Test
    fun `cannot enable protection with a malformed PIN`() = runTest {
        val r = repo()
        assertEquals(SetPinResult.INVALID_FORMAT, r.enableProtectionWithPin("12"))
        assertNotEquals("true", pinEnabledRow())
    }

    @Test
    fun `choosing a non default PIN enables protection and stores only a hash`() = runTest {
        val r = repo()
        assertEquals(SetPinResult.OK, r.enableProtectionWithPin("2580"))
        assertEquals("true", pinEnabledRow())
        val stored = dao.rows.value.getValue("pin")
        assertTrue(PinHasher.isHashed(stored))
        assertFalse(stored.contains("2580"))
        assertFalse(r.isPinDefault())
    }

    @Test
    fun `protection can be re-enabled once a non default PIN exists`() = runTest {
        val r = repo()
        r.setPin("2580")
        assertTrue(r.tryEnableProtection())
        assertEquals("true", pinEnabledRow())
    }

    @Test
    fun `a stored hash of 0000 still counts as the default`() = runTest {
        dao.rows.value = mapOf("pin" to PinHasher.hash("0000", 1_000))
        val r = repo()
        assertTrue(r.isPinDefault())
        assertFalse(r.tryEnableProtection())
        assertNotEquals("true", pinEnabledRow())
    }

    @Test
    fun `changing the PIN to 0000 is refused and keeps the old PIN`() = runTest {
        val r = repo()
        r.setPin("2580")
        val before = dao.rows.value.getValue("pin")
        assertEquals(SetPinResult.DEFAULT_REJECTED, r.setPin("0000"))
        assertEquals(before, dao.rows.value.getValue("pin"))
        assertTrue(r.verifyPin("2580") is PinVerifyResult.Success)
    }

    // ---- finding: plaintext PIN ----

    @Test
    fun `setPin stores a hash that verifies`() = runTest {
        val r = repo()
        assertEquals(SetPinResult.OK, r.setPin("2580"))
        val stored = dao.rows.value.getValue("pin")
        assertTrue(PinHasher.isHashed(stored))
        assertTrue(r.verifyPin("2580") is PinVerifyResult.Success)
    }

    // ---- finding: unlimited guesses ----

    @Test
    fun `wrong PIN is reported as wrong with no delay for the first four failures`() = runTest {
        val r = repo()
        r.setPin("2580")
        repeat(4) { i ->
            val res = r.verifyPin("1111")
            assertEquals(PinVerifyResult.Wrong(failures = i + 1, lockoutMs = 0L), res)
        }
    }

    @Test
    fun `fifth wrong guess locks out and even the right PIN is refused during the lockout`() = runTest {
        val r = repo()
        r.setPin("2580")
        repeat(4) { r.verifyPin("1111") }
        assertEquals(PinVerifyResult.Wrong(5, 30_000L), r.verifyPin("1111"))

        now += 10_000
        assertEquals(PinVerifyResult.LockedOut(20_000L), r.verifyPin("2580"))
        assertEquals(20_000L, r.lockoutRemainingMs())
    }

    @Test
    fun `after the lockout expires the right PIN works and clears the counter`() = runTest {
        val r = repo()
        r.setPin("2580")
        repeat(5) { r.verifyPin("1111") }
        now += 30_001
        assertEquals(0L, r.lockoutRemainingMs())
        assertTrue(r.verifyPin("2580") is PinVerifyResult.Success)
        // counter was reset: four more wrong guesses are free again
        repeat(4) { assertEquals(0L, (r.verifyPin("1111") as PinVerifyResult.Wrong).lockoutMs) }
    }

    @Test
    fun `second lockout is twice as long`() = runTest {
        val r = repo()
        r.setPin("2580")
        repeat(5) { r.verifyPin("1111") }
        now += 30_001
        assertEquals(PinVerifyResult.Wrong(6, 60_000L), r.verifyPin("1111"))
    }

    @Test
    fun `lockout survives a process restart`() = runTest {
        val first = repo()
        first.setPin("2580")
        repeat(5) { first.verifyPin("1111") }

        val afterRestart = repo()
        now += 5_000
        assertEquals(25_000L, afterRestart.lockoutRemainingMs())
        assertEquals(PinVerifyResult.LockedOut(25_000L), afterRestart.verifyPin("2580"))
    }

    @Test
    fun `a success resets the failure counter`() = runTest {
        val r = repo()
        r.setPin("2580")
        repeat(3) { r.verifyPin("1111") }
        assertTrue(r.verifyPin("2580") is PinVerifyResult.Success)
        repeat(4) { assertEquals(0L, (r.verifyPin("1111") as PinVerifyResult.Wrong).lockoutMs) }
    }

    @Test
    fun `a guess made while locked out does not extend the lockout`() = runTest {
        val r = repo()
        r.setPin("2580")
        repeat(5) { r.verifyPin("1111") }
        now += 1_000
        r.verifyPin("1111")
        assertEquals(29_000L, r.lockoutRemainingMs())
    }
}
