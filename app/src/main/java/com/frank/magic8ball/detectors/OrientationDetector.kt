package com.frank.magic8ball.detectors

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
 * @param onOrientationChange A callback invoked with the moving average of  pitch and roll angle.
 *
 * @author Fr4nKB
 */
class OrientationDetector(
    private val context: Context,
    private val onOrientationChange: (Double, Double) -> Unit = { _, _ -> }
) {
    private lateinit var sensorEventListener: SensorEventListener

    private val slidingWindowPitch = ArrayDeque<Double>()
    private val slidingWindowRoll = ArrayDeque<Double>()

    private var basePitchAngle = 0.0
    private var baseRollAngle = 0.0

    private var currentState = State.ACTIVE
    enum class State {
        ACTIVE, PAUSED, CALIBRATION
    }

    companion object {
        const val TAG = "Magic8Ball:OrientationDetector"
        const val SLIDING_WINDOW_SAMPLES = 12
        const val CALIBRATION_WINDOW_SAMPLES = 5
    }

    fun register() {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

        sensorEventListener = object : SensorEventListener {
            private var isFacingDown = false

            private fun ArrayDeque<Double>.calculateMovingAverage(
                newSample: Double,
                calibration: Boolean = false
            ): Double {
                this.add(newSample)
                if(this.size > SLIDING_WINDOW_SAMPLES) this.removeFirst()

                return if(calibration) this.slice(0..CALIBRATION_WINDOW_SAMPLES).average()
                else this.average()
            }

            private fun normalizeRollAngle(roll: Double): Double {
                val newValue = if(roll > 90) roll - 180 else if(roll < -90) roll + 180 else roll
                return if(isFacingDown) newValue else -newValue
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}

            override fun onSensorChanged(event: SensorEvent) {
                if(currentState == State.PAUSED) return

                when(event.sensor.type) {
                    Sensor.TYPE_ROTATION_VECTOR -> {
                        val rotationMatrix = FloatArray(9)
                        val orientationValues = FloatArray(3)

                        // convert rotation vector to rotation matrix and get orientation
                        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                        SensorManager.getOrientation(rotationMatrix, orientationValues)

                        // convert from radians to degrees
                        val pitch = Math.toDegrees(orientationValues[1].toDouble())
                        val roll = normalizeRollAngle(Math.toDegrees(orientationValues[2].toDouble()))

                        if(currentState == State.ACTIVE) {
                            val avgPitch = slidingWindowPitch.calculateMovingAverage(pitch - basePitchAngle)
                            val avgRoll = slidingWindowRoll.calculateMovingAverage(roll - baseRollAngle)
                            onOrientationChange(avgPitch, avgRoll)
                        }
                        else {
                            val avgPitch = slidingWindowPitch.calculateMovingAverage(pitch)
                            val avgRoll = slidingWindowRoll.calculateMovingAverage(roll)
                            basePitchAngle = avgPitch
                            baseRollAngle = avgRoll
                        }
                    }
                    Sensor.TYPE_GRAVITY -> {
                        isFacingDown = (event.values[2] < 0)
                    }
                }
            }
        }

        unregister(context)   // ensure only one listener is active
        sensorManager.registerListener(
            sensorEventListener,
            sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR),
            SensorManager.SENSOR_DELAY_UI
        )

        sensorManager.registerListener(
            sensorEventListener,
            sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY),
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
        currentState = State.ACTIVE
    }

    fun deactivate() {
        currentState = State.PAUSED
        slidingWindowPitch.clear()
        slidingWindowRoll.clear()
    }

    fun startCalibration() {
        basePitchAngle = 0.0
        baseRollAngle = 0.0
        slidingWindowPitch.clear()
        slidingWindowRoll.clear()
        currentState = State.CALIBRATION
    }

    fun endCalibration(activate: Boolean = true) {
        slidingWindowPitch.clear()
        slidingWindowRoll.clear()
        currentState = if(activate) State.ACTIVE else State.PAUSED
    }
}