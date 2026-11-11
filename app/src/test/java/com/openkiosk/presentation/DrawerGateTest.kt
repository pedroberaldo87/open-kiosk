package com.openkiosk.presentation

import com.openkiosk.domain.model.KioskConfig
import com.openkiosk.presentation.screen.DrawerAccess
import com.openkiosk.presentation.screen.DrawerGate
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Invariant: the settings drawer is gated on the config persisted in Room, never on the factory
 * placeholder (pinEnabled=false) that the UI sees before the database answers.
 */
class DrawerGateTest {

    @Test
    fun `before the real config arrives the drawer stays blocked`() {
        assertEquals(DrawerAccess.BLOCK, DrawerGate.decide(realConfig = null, pinVerified = false))
    }

    @Test
    fun `with protection on and no verified PIN the PIN is required`() {
        val cfg = KioskConfig(pinEnabled = true)
        assertEquals(DrawerAccess.REQUIRE_PIN, DrawerGate.decide(cfg, pinVerified = false))
    }

    @Test
    fun `with protection off the drawer opens`() {
        assertEquals(DrawerAccess.OPEN, DrawerGate.decide(KioskConfig(pinEnabled = false), pinVerified = false))
    }

    @Test
    fun `after a verified PIN the drawer opens`() {
        val cfg = KioskConfig(pinEnabled = true)
        assertEquals(DrawerAccess.OPEN, DrawerGate.decide(cfg, pinVerified = true))
    }

    @Test
    fun `protection stays off by default - owner decision`() {
        assertEquals(false, KioskConfig().pinEnabled)
    }
}
