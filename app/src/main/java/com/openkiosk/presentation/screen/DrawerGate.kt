package com.openkiosk.presentation.screen

import com.openkiosk.domain.model.KioskConfig

enum class DrawerAccess { OPEN, REQUIRE_PIN, BLOCK }

object DrawerGate {
    /**
     * [realConfig] is null until Room has answered. The factory placeholder has
     * pinEnabled=false, so deciding on it would open the drawer without a PIN for the first
     * moments after a cold start; wait for the stored config instead.
     */
    fun decide(realConfig: KioskConfig?, pinVerified: Boolean): DrawerAccess = when {
        realConfig == null -> DrawerAccess.BLOCK
        realConfig.pinEnabled && !pinVerified -> DrawerAccess.REQUIRE_PIN
        else -> DrawerAccess.OPEN
    }
}
