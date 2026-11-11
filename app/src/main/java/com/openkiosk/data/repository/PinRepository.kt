package com.openkiosk.data.repository

import com.openkiosk.data.local.dao.ConfigDao
import com.openkiosk.data.local.entity.ConfigEntity
import com.openkiosk.domain.security.PinVerifyResult
import com.openkiosk.domain.security.SetPinResult
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PinRepository(
    private val dao: ConfigDao,
    private val clock: () -> Long,
    private val iterations: Int
) {
    @Inject constructor(dao: ConfigDao) : this(dao, System::currentTimeMillis, 60_000)

    suspend fun verifyPin(pin: String): PinVerifyResult {
        val stored = dao.getValue("pin") ?: "0000"
        return if (stored == pin) PinVerifyResult.Success else PinVerifyResult.Wrong(1, 0L)
    }

    suspend fun lockoutRemainingMs(): Long = 0L

    suspend fun setPin(newPin: String): SetPinResult {
        dao.set(ConfigEntity("pin", newPin))
        return SetPinResult.OK
    }

    suspend fun isPinDefault(): Boolean {
        val stored = dao.getValue("pin")
        return stored == null || stored == "0000"
    }

    suspend fun tryEnableProtection(): Boolean {
        dao.set(ConfigEntity("pinEnabled", "true"))
        return true
    }

    suspend fun enableProtectionWithPin(newPin: String): SetPinResult {
        dao.setAll(listOf(ConfigEntity("pin", newPin), ConfigEntity("pinEnabled", "true")))
        return SetPinResult.OK
    }
}
