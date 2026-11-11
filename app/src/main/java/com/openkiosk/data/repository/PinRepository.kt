package com.openkiosk.data.repository

import com.openkiosk.data.local.dao.ConfigDao
import com.openkiosk.data.local.entity.ConfigEntity
import com.openkiosk.domain.security.PinHasher
import com.openkiosk.domain.security.PinKeys
import com.openkiosk.domain.security.PinLockout
import com.openkiosk.domain.security.PinPolicy
import com.openkiosk.domain.security.PinValidation
import com.openkiosk.domain.security.PinVerifyResult
import com.openkiosk.domain.security.SetPinResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The only writer of the PIN, the "protection on" flag and the attempt counters. Keeping the
 * rules here means no settings path can enable protection with the default PIN, store a PIN
 * in clear, or reset the lockout.
 */
@Singleton
class PinRepository(
    private val dao: ConfigDao,
    private val clock: () -> Long,
    private val iterations: Int
) {
    @Inject
    constructor(dao: ConfigDao) : this(dao, System::currentTimeMillis, PinHasher.DEFAULT_ITERATIONS)

    // One guess at a time: two parallel wrong guesses must not read the same counter and both
    // write failures+1.
    private val mutex = Mutex()

    /**
     * Counts the attempt before anyone can see the outcome, and survives the dialog being
     * dismissed mid-check (NonCancellable): closing the dialog must not be a free retry.
     */
    suspend fun verifyPin(pin: String): PinVerifyResult = mutex.withLock {
        withContext(NonCancellable) {
            val remaining = lockoutRemainingMs()
            if (remaining > 0L) {
                PinVerifyResult.LockedOut(remaining)
            } else if (withContext(Dispatchers.Default) { matches(pin) }) {
                resetCounters()
                PinVerifyResult.Success
            } else {
                val failures = (dao.getValue(PinKeys.FAILED_ATTEMPTS)?.toIntOrNull() ?: 0)
                    .coerceAtLeast(0).let { if (it == Int.MAX_VALUE) it else it + 1 }
                val lockoutMs = PinLockout.delayAfterFailures(failures)
                dao.setAll(
                    listOf(
                        ConfigEntity(PinKeys.FAILED_ATTEMPTS, failures.toString()),
                        ConfigEntity(PinKeys.LOCKED_UNTIL_MS, (clock() + lockoutMs).toString())
                    )
                )
                PinVerifyResult.Wrong(failures, lockoutMs)
            }
        }
    }

    suspend fun lockoutRemainingMs(): Long =
        PinLockout.remainingMs(dao.getValue(PinKeys.LOCKED_UNTIL_MS)?.toLongOrNull() ?: 0L, clock())

    suspend fun setPin(newPin: String): SetPinResult {
        val rejected = reject(newPin)
        if (rejected != null) return rejected
        val hash = withContext(Dispatchers.Default) { PinHasher.hash(newPin, iterations) }
        dao.setAll(listOf(ConfigEntity(PinKeys.PIN, hash)) + counterReset())
        return SetPinResult.OK
    }

    /** True while there is no PIN, or the stored PIN is the factory 0000. */
    suspend fun isPinDefault(): Boolean {
        val stored = dao.getValue(PinKeys.PIN) ?: return true
        // Unreadable value: refuse to call it a chosen PIN.
        if (!PinHasher.isHashed(stored)) return true
        return withContext(Dispatchers.Default) { PinHasher.verify(PinPolicy.DEFAULT_PIN, stored) }
    }

    /** Turns protection on only when a non-default PIN already exists. */
    suspend fun tryEnableProtection(): Boolean {
        if (isPinDefault()) return false
        dao.set(ConfigEntity(PinKeys.ENABLED, "true"))
        return true
    }

    /** Sets the PIN and turns protection on in one transaction, or does neither. */
    suspend fun enableProtectionWithPin(newPin: String): SetPinResult {
        val rejected = reject(newPin)
        if (rejected != null) return rejected
        val hash = withContext(Dispatchers.Default) { PinHasher.hash(newPin, iterations) }
        dao.setAll(
            listOf(ConfigEntity(PinKeys.PIN, hash), ConfigEntity(PinKeys.ENABLED, "true")) + counterReset()
        )
        return SetPinResult.OK
    }

    suspend fun disableProtection() {
        dao.set(ConfigEntity(PinKeys.ENABLED, "false"))
    }

    private fun reject(pin: String): SetPinResult? = when (PinPolicy.validate(pin)) {
        PinValidation.OK -> null
        PinValidation.BAD_FORMAT -> SetPinResult.INVALID_FORMAT
        PinValidation.DEFAULT_PIN -> SetPinResult.DEFAULT_REJECTED
    }

    private suspend fun matches(pin: String): Boolean {
        val stored = dao.getValue(PinKeys.PIN)
        return when {
            // Protection on with no PIN row (never reached after the 1->2 migration): the old
            // reader treated that as 0000, keep that instead of locking the owner out.
            stored == null ->
                MessageDigest.isEqual(pin.toByteArray(), PinPolicy.DEFAULT_PIN.toByteArray())
            PinHasher.isHashed(stored) -> PinHasher.verify(pin, stored)
            else -> false
        }
    }

    private fun counterReset() = listOf(
        ConfigEntity(PinKeys.FAILED_ATTEMPTS, "0"),
        ConfigEntity(PinKeys.LOCKED_UNTIL_MS, "0")
    )

    private suspend fun resetCounters() {
        val failures = dao.getValue(PinKeys.FAILED_ATTEMPTS)?.toIntOrNull() ?: 0
        val lockedUntil = dao.getValue(PinKeys.LOCKED_UNTIL_MS)?.toLongOrNull() ?: 0L
        if (failures != 0 || lockedUntil != 0L) dao.setAll(counterReset())
    }
}
