package com.openkiosk.domain.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Invariant: upgrading from the plaintext schema keeps the user's PIN working and leaves no plaintext behind. */
class PinMigrationTest {
    private val fast = 1_000

    private class MapStore(initial: Map<String, String> = emptyMap()) : LegacyConfigStore {
        val rows = initial.toMutableMap()
        override fun get(key: String): String? = rows[key]
        override fun put(key: String, value: String) { rows[key] = value }
    }

    @Test
    fun `an existing plaintext PIN is hashed and still verifies`() {
        val store = MapStore(mapOf("pin" to "4321", "pinEnabled" to "true"))
        PinMigration.hashLegacyPin(store, fast)

        val stored = store.rows.getValue("pin")
        assertTrue(PinHasher.isHashed(stored))
        assertFalse(stored.contains("4321"))
        assertTrue(PinHasher.verify("4321", stored))
        assertFalse(PinHasher.verify("1234", stored))
    }

    @Test
    fun `protection on with no PIN row keeps the implicit default so nobody is locked out`() {
        val store = MapStore(mapOf("pinEnabled" to "true"))
        PinMigration.hashLegacyPin(store, fast)

        assertTrue(PinHasher.verify("0000", store.rows.getValue("pin")))
    }

    @Test
    fun `no PIN row and protection off writes nothing`() {
        val store = MapStore(mapOf("startUrl" to "https://example.com"))
        PinMigration.hashLegacyPin(store, fast)

        assertNull(store.rows["pin"])
        assertEquals(1, store.rows.size)
    }

    @Test
    fun `an already hashed PIN is left untouched`() {
        val hashed = PinHasher.hash("4321", fast)
        val store = MapStore(mapOf("pin" to hashed))
        PinMigration.hashLegacyPin(store, fast)

        assertEquals(hashed, store.rows.getValue("pin"))
    }
}
