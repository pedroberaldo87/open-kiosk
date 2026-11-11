package com.openkiosk.domain.security

interface LegacyConfigStore {
    fun get(key: String): String?
    fun put(key: String, value: String)
}

object PinMigration {
    fun hashLegacyPin(store: LegacyConfigStore, iterations: Int = PinHasher.DEFAULT_ITERATIONS) {
        // RED stub: leaves the plaintext PIN untouched.
    }
}
