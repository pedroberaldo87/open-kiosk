package com.openkiosk.domain.security

/** Minimal key/value view of the config table so the migration can be tested without Room. */
interface LegacyConfigStore {
    fun get(key: String): String?
    fun put(key: String, value: String)
}

object PinMigration {
    /**
     * Schema 1 stored the PIN as plain text, or not at all (the reader then assumed 0000).
     * Hash what is there so the owner's PIN keeps working, and make the implicit default explicit
     * when protection was already on so nobody is locked out of their own drawer by the upgrade.
     */
    fun hashLegacyPin(store: LegacyConfigStore, iterations: Int = PinHasher.DEFAULT_ITERATIONS) {
        val current = store.get(PinKeys.PIN)
        if (PinHasher.isHashed(current)) return
        val plain = current
            ?: if (store.get(PinKeys.ENABLED)?.toBooleanStrictOrNull() == true) PinPolicy.DEFAULT_PIN else return
        store.put(PinKeys.PIN, PinHasher.hash(plain, iterations))
    }
}
