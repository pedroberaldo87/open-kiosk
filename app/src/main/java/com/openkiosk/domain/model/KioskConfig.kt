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
    val cameraPollingIntervalSeconds: Int = 5,
    val cameraPulseIntervalSeconds: Int = 10,

    // Deep Sleep — scheduled time range, touch-only wake, all sensors off
    val deepSleepEnabled: Boolean = false,
    val deepSleepStartHour: Int = 22,
    val deepSleepEndHour: Int = 6,

    // WebView
    val autoRefreshMinutes: Int = 30,

    // Kiosk
    val lockTaskEnabled: Boolean = true,
    val pinEnabled: Boolean = false,
    val pin: String = "0000",

    // General
    val startUrl: String = "https://www.google.com"
)

enum class MotionSensitivity(val threshold: Double) {
    LOW(0.08),
    MEDIUM(0.05),
    HIGH(0.03)
}
