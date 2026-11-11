package com.openkiosk.domain.security

sealed interface PinVerifyResult {
    object Success : PinVerifyResult
    data class Wrong(val failures: Int, val lockoutMs: Long) : PinVerifyResult
    data class LockedOut(val remainingMs: Long) : PinVerifyResult
}

enum class SetPinResult { OK, INVALID_FORMAT, DEFAULT_REJECTED }
