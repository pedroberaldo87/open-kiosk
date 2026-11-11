package com.openkiosk.domain.security

object PinHasher {
    const val DEFAULT_ITERATIONS = 60_000

    fun hash(pin: String, iterations: Int = DEFAULT_ITERATIONS): String = pin

    fun verify(pin: String, stored: String): Boolean = pin == stored

    fun isHashed(stored: String?): Boolean = false
}
