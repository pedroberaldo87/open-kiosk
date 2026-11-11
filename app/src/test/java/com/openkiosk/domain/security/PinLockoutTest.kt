package com.openkiosk.domain.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Invariant: wrong guesses get exponentially slower, capped, and a skewed clock cannot extend the wait forever. */
class PinLockoutTest {

    @Test
    fun `first four failures carry no delay`() {
        (0..4).forEach { assertEquals("failures=$it", 0L, PinLockout.delayAfterFailures(it)) }
    }

    @Test
    fun `delay starts at 30 seconds on the fifth failure and doubles`() {
        assertEquals(30_000L, PinLockout.delayAfterFailures(5))
        assertEquals(60_000L, PinLockout.delayAfterFailures(6))
        assertEquals(120_000L, PinLockout.delayAfterFailures(7))
        assertEquals(240_000L, PinLockout.delayAfterFailures(8))
        assertEquals(480_000L, PinLockout.delayAfterFailures(9))
    }

    @Test
    fun `delay is capped at one hour and never overflows`() {
        assertEquals(3_600_000L, PinLockout.delayAfterFailures(12))
        assertEquals(3_600_000L, PinLockout.delayAfterFailures(100))
        assertEquals(3_600_000L, PinLockout.delayAfterFailures(Int.MAX_VALUE))
    }

    @Test
    fun `delay never shrinks as failures grow`() {
        var previous = 0L
        for (n in 0..60) {
            val d = PinLockout.delayAfterFailures(n)
            assertTrue("failures=$n", d >= previous)
            previous = d
        }
    }

    @Test
    fun `remaining time counts down and is zero once the deadline passed`() {
        assertEquals(10_000L, PinLockout.remainingMs(lockedUntilMs = 110_000L, nowMs = 100_000L))
        assertEquals(0L, PinLockout.remainingMs(lockedUntilMs = 100_000L, nowMs = 100_000L))
        assertEquals(0L, PinLockout.remainingMs(lockedUntilMs = 50_000L, nowMs = 100_000L))
    }

    @Test
    fun `remaining time is clamped when the clock was moved backwards`() {
        val remaining = PinLockout.remainingMs(lockedUntilMs = 10_000_000_000L, nowMs = 1_000L)
        assertEquals(PinLockout.MAX_DELAY_MS, remaining)
    }

    @Test
    fun `remaining time is formatted as m ss or h mm ss rounded up`() {
        assertEquals("0:01", PinLockout.formatRemaining(1L))
        assertEquals("0:30", PinLockout.formatRemaining(30_000L))
        assertEquals("1:05", PinLockout.formatRemaining(65_000L))
        assertEquals("1:00:00", PinLockout.formatRemaining(3_600_000L))
    }
}
