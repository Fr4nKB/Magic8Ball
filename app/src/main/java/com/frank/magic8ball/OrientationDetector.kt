package com.frank.magic8ball

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

/**
 * A class for detecting orientation changes using rotation vector sensor.
 * This class monitors the sensor events and triggers a callback with the exponential moving average
 * for pitch and roll angles.
 *
 * @param context The application context used to access the sensor service.
 * @param movingAvgWindowSize Specifies the window size for the moving average of pitch and roll values
 * @param onOrientationChange A callback invoked with the moving average of  pitch and roll angle.
 *
 * @author Fr4nKB
 */
class OrientationDetector(
    private val context: Context,
    movingAvgWindowSize: Int,
    private val onOrientationChange: (Double, Double) -> Unit = { _, _ -> }
) {
    private lateinit var sensorEventListener: SensorEventListener
    private var isActive = false // ensure thread safety

    // to implement exponential moving average
    // lag effect if movingAvgWindowSize is big enough
    private var exponentialFactor = 2.0 / (movingAvgWindowSize + 1)
    private var exponentialMovingAveragePitch: Double = 0.0
    private var exponentialMovingAverageRoll: Double = 0.0

    companion object {
        const val TAG = "Magic8Ball:OrientationDetector"
    }

    fun register() {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

        sensorEventListener = object : SensorEventListener {
            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}

            override fun onSensorChanged(event: SensorEvent) {
                if(!isActive) return

                if(event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
                    val rotationMatrix = FloatArray(9)
                    val orientationValues = FloatArray(3)

                    // convert rotation vector to rotation matrix and get orientation
                    SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                    SensorManager.getOrientation(rotationMatrix, orientationValues)

                    // convert from radians to degrees
                    val pitch = Math.toDegrees(orientationValues[1].toDouble())
                    val roll = Math.toDegrees(orientationValues[2].toDouble())

                    val complementFactor = 1 - exponentialFactor
                    exponentialMovingAveragePitch =
                        exponentialFactor * pitch + complementFactor * exponentialMovingAveragePitch
                    exponentialMovingAverageRoll =
                        exponentialFactor * roll + complementFactor * exponentialMovingAverageRoll

                    onOrientationChange(exponentialMovingAveragePitch, exponentialMovingAverageRoll)
                }
            }
        }

        unregister(context)   // ensure only one listener is active
        sensorManager.registerListener(
            sensorEventListener,
            sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR),
            SensorManager.SENSOR_DELAY_UI
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