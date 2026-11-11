package com.openkiosk.domain.security

enum class PinValidation { OK, BAD_FORMAT, DEFAULT_PIN }

object PinPolicy {
    const val PIN_LENGTH = 4

    // The factory PIN every older install shipped with and the README used to publish. It can
    // still be the stored PIN of an upgraded install, but it can never be chosen again.
    const val DEFAULT_PIN = "0000"

    // ASCII digits only: Char.isDigit() also accepts other scripts' digits, which the numeric
    // keyboard never produces and which would make two different inputs look alike.
    fun validate(pin: String): PinValidation = when {
        pin.length != PIN_LENGTH || !pin.all { it in '0'..'9' } -> PinValidation.BAD_FORMAT
        pin == DEFAULT_PIN -> PinValidation.DEFAULT_PIN
        else -> PinValidation.OK
    }
}
