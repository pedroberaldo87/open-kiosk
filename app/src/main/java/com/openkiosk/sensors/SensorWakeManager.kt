package com.openkiosk.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

private const val TAG = "SensorWake"

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

        // Initialize debounce timestamps to now — prevents immediate trigger on first event
        val now = System.currentTimeMillis()
        lastProximityTrigger = now
        lastShakeTrigger = now

        if (wakeOnProximity) {
            val proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
            if (proximitySensor != null) {
                val maxRange = proximitySensor.maximumRange
                Log.d(TAG, "Proximity sensor registered (maxRange=$maxRange)")
                proximityListener = object : SensorEventListener {
                    override fun onSensorChanged(event: SensorEvent) {
                        val eventTime = System.currentTimeMillis()
                        if (event.values[0] < maxRange && eventTime - lastProximityTrigger > 1000L) {
                            lastProximityTrigger = eventTime
                            Log.d(TAG, "Proximity wake triggered (value=${event.values[0]}, maxRange=$maxRange)")
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
            } else {
                Log.w(TAG, "Proximity sensor not available on this device")
            }
        }

        if (wakeOnShake) {
            val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            if (accelerometer != null) {
                Log.d(TAG, "Accelerometer registered for shake detection")
                accelerometerListener = object : SensorEventListener {
                    override fun onSensorChanged(event: SensorEvent) {
                        val x = event.values[0]
                        val y = event.values[1]
                        val z = event.values[2]
                        val magnitude = sqrt((x * x + y * y + z * z).toDouble())
                        val netAcceleration = magnitude - SensorManager.GRAVITY_EARTH

                        val eventTime = System.currentTimeMillis()
                        if (netAcceleration > 12.0 && eventTime - lastShakeTrigger > 2000L) {
                            lastShakeTrigger = eventTime
                            Log.d(TAG, "Shake wake triggered (netAcceleration=%.1f)".format(netAcceleration))
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
            } else {
                Log.w(TAG, "Accelerometer not available on this device")
            }
        }
    }

    fun stop() {
        proximityListener?.let {
            sensorManager.unregisterListener(it)
            Log.d(TAG, "Proximity sensor unregistered")
        }
        accelerometerListener?.let {
            sensorManager.unregisterListener(it)
            Log.d(TAG, "Accelerometer unregistered")
        }
        proximityListener = null
        accelerometerListener = null
    }
}
