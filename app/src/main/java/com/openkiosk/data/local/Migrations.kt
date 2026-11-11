package com.openkiosk.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.openkiosk.domain.security.LegacyConfigStore
import com.openkiosk.domain.security.PinMigration

/** 1 -> 2: the PIN stops being stored as plain text. Tables are unchanged. */
val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        PinMigration.hashLegacyPin(SqliteConfigStore(db))
    }
}

private class SqliteConfigStore(private val db: SupportSQLiteDatabase) : LegacyConfigStore {
    override fun get(key: String): String? =
        db.query("SELECT value FROM config WHERE `key` = ?", arrayOf<Any>(key)).use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }

    override fun put(key: String, value: String) {
        db.execSQL("INSERT OR REPLACE INTO config (`key`, `value`) VALUES (?, ?)", arrayOf<Any>(key, value))
    }
}
