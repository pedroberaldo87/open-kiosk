package com.openkiosk.presentation.screen

import com.openkiosk.domain.model.KioskConfig

enum class DrawerAccess { OPEN, REQUIRE_PIN, BLOCK }

object DrawerGate {
    fun decide(realConfig: KioskConfig?, pinVerified: Boolean): DrawerAccess =
        if (realConfig?.pinEnabled == true && !pinVerified) DrawerAccess.REQUIRE_PIN else DrawerAccess.OPEN
}
