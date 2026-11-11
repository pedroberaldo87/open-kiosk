package com.openkiosk.data.repository

import com.openkiosk.data.FakeConfigDao
import com.openkiosk.domain.model.KioskConfig
import com.openkiosk.domain.security.PinHasher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Invariant: no generic config write path can bypass the PIN rules (sibling paths of the PIN fix). */
class ConfigRepositoryPinTest {
    private val dao = FakeConfigDao()
    private val pins = PinRepository(dao, { 1_000_000L }, 1_000)
    private val config = ConfigRepository(dao, pins)

    @Test
    fun `generic pinEnabled=true is ignored while the PIN is the default`() = runTest {
        config.updateConfig("pinEnabled", "true")
        assertNotEquals("true", dao.rows.value["pinEnabled"])
        assertFalse(config.observeConfig().first().pinEnabled)
    }

    @Test
    fun `generic pinEnabled=true works once a non default PIN exists`() = runTest {
        pins.setPin("2580")
        config.updateConfig("pinEnabled", "true")
        assertTrue(config.observeConfig().first().pinEnabled)
    }

    @Test
    fun `generic pinEnabled=false always works`() = runTest {
        pins.enableProtectionWithPin("2580")
        config.updateConfig("pinEnabled", "false")
        assertFalse(config.observeConfig().first().pinEnabled)
    }

    @Test
    fun `generic pin write is hashed and refuses 0000`() = runTest {
        config.updateConfig("pin", "2580")
        val stored = dao.rows.value.getValue("pin")
        assertTrue(PinHasher.isHashed(stored))
        assertFalse(stored.contains("2580"))

        config.updateConfig("pin", "0000")
        assertEquals(stored, dao.rows.value.getValue("pin"))
    }

    @Test
    fun `generic writes cannot touch the lockout counters`() = runTest {
        config.updateConfig("pinFailedAttempts", "0")
        config.updateConfig("pinLockedUntilMs", "0")
        assertTrue(dao.rows.value.isEmpty())
    }

    @Test
    fun `full config write does not enable protection with the default PIN or store a PIN`() = runTest {
        config.updateConfig(KioskConfig(pinEnabled = true))
        assertNotEquals("true", dao.rows.value["pinEnabled"])
        assertFalse(dao.rows.value.containsKey("pin"))
    }

    @Test
    fun `observed config carries no PIN material`() = runTest {
        pins.enableProtectionWithPin("2580")
        val cfg = config.observeConfig().first()
        assertFalse(cfg.toString().contains("pbkdf2"))
        assertFalse(cfg.toString().contains("2580"))
    }
}
