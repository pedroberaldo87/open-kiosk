package com.openkiosk.domain.security

/** Config-table keys owned by the PIN code; generic config writes must not touch them. */
object PinKeys {
    const val ENABLED = "pinEnabled"
    const val PIN = "pin"
    const val FAILED_ATTEMPTS = "pinFailedAttempts"
    const val LOCKED_UNTIL_MS = "pinLockedUntilMs"
}
