package com.openkiosk.domain.security

import java.util.Locale

/** Rate limit for wrong PIN guesses: free tries, then a delay that doubles and is capped. */
object PinLockout {
    // knob de campo: subir se o dono erra o PIN com frequencia e se irrita; baixar para endurecer.
    const val FREE_ATTEMPTS = 4

    // knob de campo: tempo do primeiro bloqueio; cada falha seguinte dobra ate MAX_DELAY_MS.
    const val BASE_DELAY_MS = 30_000L
    const val MAX_DELAY_MS = 3_600_000L

    fun delayAfterFailures(failures: Int): Long {
        if (failures <= FREE_ATTEMPTS) return 0L
        val doublings = failures - FREE_ATTEMPTS - 1
        // BASE * 2^7 ja passa do teto; parar aqui evita estouro do shift para contagens enormes.
        if (doublings >= 7) return MAX_DELAY_MS
        return minOf(BASE_DELAY_MS shl doublings, MAX_DELAY_MS)
    }

    // The deadline is a wall-clock instant. Clamping to MAX_DELAY_MS means that setting the
    // clock backwards (or a garbage value in the table) can never lock the owner out for longer
    // than the longest legitimate lockout.
    fun remainingMs(lockedUntilMs: Long, nowMs: Long): Long =
        (lockedUntilMs - nowMs).coerceIn(0L, MAX_DELAY_MS)

    fun formatRemaining(ms: Long): String {
        val totalSeconds = (ms.coerceAtLeast(0L) + 999L) / 1000L
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return if (h > 0) "%d:%02d:%02d".format(Locale.ROOT, h, m, s) else "%d:%02d".format(Locale.ROOT, m, s)
    }
}
