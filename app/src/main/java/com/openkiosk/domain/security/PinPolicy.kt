package com.openkiosk.domain.security

enum class PinValidation { OK, BAD_FORMAT, DEFAULT_PIN }

object PinPolicy {
    const val PIN_LENGTH = 4
    const val DEFAULT_PIN = "0000"

    fun validate(pin: String): PinValidation = PinValidation.OK
}
