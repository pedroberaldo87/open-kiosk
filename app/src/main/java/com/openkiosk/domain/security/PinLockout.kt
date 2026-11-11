package com.openkiosk.domain.security

object PinLockout {
    const val FREE_ATTEMPTS = 4
    const val BASE_DELAY_MS = 30_000L
    const val MAX_DELAY_MS = 3_600_000L

    fun delayAfterFailures(failures: Int): Long = 0L

    fun remainingMs(lockedUntilMs: Long, nowMs: Long): Long = 0L

    fun formatRemaining(ms: Long): String = ""
}
