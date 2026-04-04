package com.openkiosk.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

@Singleton
class SensorWakeManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private var proximityListener: SensorEventListener? = null
    private var accelerometerListener: SensorEventListener? = null
    private var lastProximityTrigger = 0L
    private var lastShakeTrigger = 0L

    fun start(wakeOnProximity: Boolean, wakeOnShake: Boolean, onWake: () -> Unit) {
        stop()

        if (wakeOnProximity) {
            val proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
            if (proximitySensor != null) {
                val maxRange = proximitySensor.maximumRange
                proximityListener = object : SensorEventListener {
                    override fun onSensorChanged(event: SensorEvent) {
                        val now = System.currentTimeMillis()
                        if (event.values[0] < maxRange && now - lastProximityTrigger > 1000L) {
                            lastProximityTrigger = now
                            onWake()
                        }
                    }

                    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
                }
                sensorManager.registerListener(
                    proximityListener,
                    proximitySensor,
                    SensorManager.SENSOR_DELAY_NORMAL
                )
            }
        }

        if (wakeOnShake) {
            val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            if (accelerometer != null) {
                accelerometerListener = object : SensorEventListener {
                    override fun onSensorChanged(event: SensorEvent) {
                        val x = event.values[0]
                        val y = event.values[1]
                        val z = event.values[2]
                        val magnitude = sqrt((x * x + y * y + z * z).toDouble())
                        val netAcceleration = magnitude - SensorManager.GRAVITY_EARTH

                        val now = System.currentTimeMillis()
                        if (netAcceleration > 12.0 && now - lastShakeTrigger > 2000L) {
                            lastShakeTrigger = now
                            onWake()
                        }
                    }

                    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
                }
                sensorManager.registerListener(
                    accelerometerListener,
                    accelerometer,
                    SensorManager.SENSOR_DELAY_NORMAL
                )
            }
        }
    }

    fun stop() {
        proximityListener?.let { sensorManager.unregisterListener(it) }
        accelerometerListener?.let { sensorManager.unregisterListener(it) }
        proximityListener = null
        accelerometerListener = null
    }
}
