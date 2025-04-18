package com.frank.magic8ball

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.atan2

/**
 * A class for detecting orientation changes using gyroscope sensors.
 * This class monitors sensor events and applies a smoothing filter to gyroscope data,
 * triggering a callback with the pitch angle when significant changes are detected.
 *
 * @param context The application context used to access the sensor service.
 * @param onOrientationChange A callback invoked with the pitch angle when an orientation change occurs.
 *
 * @author Fr4nKB
 */
class OrientationDetector(
    private val context: Context,
    private val onOrientationChange: (Double) -> Unit = {}
) {
    private lateinit var sensorEventListener: SensorEventListener
    private var isActive by SynchronizedFlag(false) // ensure thread safety

    companion object {
        const val TAG = "Magic8Ball:OrientationDetector"
        const val ORIENTATION_CHANGE_THRESHOLD = 10
    }

    fun register() {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

        sensorEventListener = object : SensorEventListener {
            private val alpha = 0.8f // smoothing factor
            private var smoothedValues = DoubleArray(10) // stores smoothed gyroscope values

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}

            override fun onSensorChanged(event: SensorEvent) {
                if(!isActive) return

                if(event.sensor.type == Sensor.TYPE_GYROSCOPE) {
                    for(i in event.values.indices) {    // low-pass filter
                        smoothedValues[i] = alpha * smoothedValues[i] + (1 - alpha) * event.values[i]
                    }

                    val pitch = Math.toDegrees(atan2(-smoothedValues[0], smoothedValues[9]))

                    if(pitch > ORIENTATION_CHANGE_THRESHOLD) onOrientationChange(pitch)   // forward tilt
                    else if (pitch < -ORIENTATION_CHANGE_THRESHOLD) onOrientationChange(pitch)    // backward tilt
                }
            }
        }

        unregister(context)   // ensure only one listener is active
        sensorManager.registerListener(
            sensorEventListener,
            sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE),
            SensorManager.SENSOR_DELAY_NORMAL
        )
    }

    fun unregister(context: Context) {
        try {
            val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            sensorManager.unregisterListener(sensorEventListener)
        }
        catch (_: Exception) {}
    }

    fun activate() {
        isActive = true
    }

    fun deactivate() {
        isActive = false
    }
}