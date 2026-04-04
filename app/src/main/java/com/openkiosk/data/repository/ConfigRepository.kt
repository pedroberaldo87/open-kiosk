package com.openkiosk.data.repository

import com.openkiosk.data.local.dao.ConfigDao
import com.openkiosk.data.local.entity.ConfigEntity
import com.openkiosk.domain.model.KioskConfig
import com.openkiosk.domain.model.MotionSensitivity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConfigRepository @Inject constructor(
    private val configDao: ConfigDao
) {
    fun observeConfig(): Flow<KioskConfig> = configDao.observeAll().map { entities ->
        val map = entities.associate { it.key to it.value }
        KioskConfig(
            activeTimeoutSeconds = map["activeTimeoutSeconds"]?.toIntOrNull() ?: 30,
            dimTimeoutSeconds = map["dimTimeoutSeconds"]?.toIntOrNull() ?: 60,
            dimBrightnessPercent = map["dimBrightnessPercent"]?.toIntOrNull() ?: 20,
            wakeOnMotion = map["wakeOnMotion"]?.toBooleanStrictOrNull() ?: true,
            wakeOnProximity = map["wakeOnProximity"]?.toBooleanStrictOrNull() ?: true,
            wakeOnShake = map["wakeOnShake"]?.toBooleanStrictOrNull() ?: true,
            motionSensitivity = map["motionSensitivity"]?.let {
                try { MotionSensitivity.valueOf(it) } catch (_: Exception) { MotionSensitivity.MEDIUM }
            } ?: MotionSensitivity.MEDIUM,
            cameraPollingIntervalSeconds = map["cameraPollingIntervalSeconds"]?.toIntOrNull() ?: 5,
            autoRefreshMinutes = map["autoRefreshMinutes"]?.toIntOrNull() ?: 30,
            lockTaskEnabled = map["lockTaskEnabled"]?.toBooleanStrictOrNull() ?: true,
            pin = map["pin"] ?: "0000",
            startUrl = map["startUrl"] ?: "https://www.google.com"
        )
    }

    suspend fun updateConfig(key: String, value: String) {
        configDao.set(ConfigEntity(key, value))
    }

    suspend fun updateConfig(config: KioskConfig) {
        configDao.setAll(listOf(
            ConfigEntity("activeTimeoutSeconds", config.activeTimeoutSeconds.toString()),
            ConfigEntity("dimTimeoutSeconds", config.dimTimeoutSeconds.toString()),
            ConfigEntity("dimBrightnessPercent", config.dimBrightnessPercent.toString()),
            ConfigEntity("wakeOnMotion", config.wakeOnMotion.toString()),
            ConfigEntity("wakeOnProximity", config.wakeOnProximity.toString()),
            ConfigEntity("wakeOnShake", config.wakeOnShake.toString()),
            ConfigEntity("motionSensitivity", config.motionSensitivity.name),
            ConfigEntity("cameraPollingIntervalSeconds", config.cameraPollingIntervalSeconds.toString()),
            ConfigEntity("autoRefreshMinutes", config.autoRefreshMinutes.toString()),
            ConfigEntity("lockTaskEnabled", config.lockTaskEnabled.toString()),
            ConfigEntity("pin", config.pin),
            ConfigEntity("startUrl", config.startUrl)
        ))
    }
}
