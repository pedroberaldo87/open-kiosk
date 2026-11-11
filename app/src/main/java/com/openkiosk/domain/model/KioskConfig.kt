package com.openkiosk.domain.model

data class KioskConfig(
    // Sleep & Wake
    val activeTimeoutSeconds: Int = 30,
    val dimTimeoutSeconds: Int = 60,
    val dimBrightnessPercent: Int = 20,

    // Sensors
    val wakeOnMotion: Boolean = true,
    val wakeOnProximity: Boolean = true,
    val wakeOnShake: Boolean = true,
    val motionSensitivity: MotionSensitivity = MotionSensitivity.MEDIUM,
    val cameraPulseIntervalSeconds: Int = 10,

    // Deep Sleep — scheduled time range, touch-only wake, all sensors off
    val deepSleepEnabled: Boolean = false,
    val deepSleepStartHour: Int = 22,
    val deepSleepEndHour: Int = 6,

    // WebView
    val autoRefreshMinutes: Int = 30,

    // Kiosk
    // Off by default: the device is the owner's to use. On = keep the kiosk in front
    // (watchdog relaunch, open on boot, Back blocked, lock task when device owner).
    val lockTaskEnabled: Boolean = false,
    // Off by default is an owner decision (the PIN must not be mandatory on first run). The PIN
    // itself is never part of this model: it only exists as a hash behind PinRepository.
    val pinEnabled: Boolean = false,

    // General
    val startUrl: String = "https://www.google.com"
)

enum class MotionSensitivity(val threshold: Double) {
    LOW(0.08),
    MEDIUM(0.05),
    HIGH(0.03)
}
