package com.frank.magic8ball

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.abs

/**
 * A class to detect shaking motion using accelerometer sensors.
 * It observes sensor events to determine the start and end of a shake gesture
 * based on configurable thresholds. It ensures proper handling of sensor listener
 * registration and state management.
 *
 * @param context The application context used to access the sensor service.
 * @param onShakeStart A callback triggered when the shaking is initiated.
 * @param onShake A callback triggered when the shake is already initiated and a shake motion is detected.
 * @param onShakeReset A callback triggered when shake was initiated but did not meet the thresholds.
 * @param onShakeEnd A callback triggered with the average shake force when shaking ends.
 *
 * @author Fr4nKB
 */
class ShakingDetector(
    private val context: Context,
    private val onShakeStart: () -> Unit = {},
    private val onShake: (Double) -> Unit = {},
    private val onShakeReset: () -> Unit = {},
    private val onShakeEnd: (Double) -> Unit = {}
) {
    private lateinit var sensorEventListener: SensorEventListener
    private var isActive by SynchronizedFlag(false) // ensure thread safety

    companion object {
        const val TAG = "Magic8Ball:ShakingDetector"
        const val SHAKE_FORCE_THRESHOLD = 0.5
        const val ON_START_THRESHOLD = 3
        const val ON_END_THRESHOLD = 6
        const val SLIDING_WINDOW_SAMPLES = 5
        const val NUM_SAMPLES_THRESHOLD = 20
    }

    fun register() {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

        sensorEventListener = object : SensorEventListener {
            // previous sensor values
            private var lastX = 0f
            private var lastY = 0f
            private var lastZ = 0f

            // shake-related state variables
            private var avgShakeForce = 0.0
            private val slidingWindow = ArrayDeque<Float>()
            private var isShaking = false
            private var consecutiveNumShakeEvents = 0
            private var consecutiveNumStillEvents = 0

            // calculates moving average from sliding window
            private fun calculateMovingAverage(newSample: Float): Double {
                slidingWindow.addLast(newSample)

                // remove oldest sample if queue exceeds window size
                if(slidingWindow.size > SLIDING_WINDOW_SAMPLES) {
                    slidingWindow.removeFirst()
                }

                return slidingWindow.average()
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}

            override fun onSensorChanged(event: SensorEvent) {
                if(!isActive) {
                    resetStateVariables()
                    return
                }

                if(event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                    val x = event.values[0]
                    val y = event.values[1]
                    val z = event.values[2]

                    val deltaX = x - lastX
                    val deltaY = y - lastY
                    val deltaZ = z - lastZ
                    val shakeForce = abs(deltaX + deltaY + deltaZ)

                    lastX = x
                    lastY = y
                    lastZ = z

                    // handle shake detection logic
                    handleShakeDetection(shakeForce)
                }
            }

            private fun handleShakeDetection(shakeForce: Float) {
                if(shakeForce > SHAKE_FORCE_THRESHOLD) {
                    consecutiveNumShakeEvents += 1
                    consecutiveNumStillEvents = 0

                    if(consecutiveNumShakeEvents >= ON_START_THRESHOLD) {
                        if(!isShaking) {
                            isShaking = true
                            onShakeStart() // called only once
                        }

                        // online avg
                        val numRecordedSamples = consecutiveNumShakeEvents - ON_START_THRESHOLD - 1
                        if(numRecordedSamples == 1) avgShakeForce = shakeForce.toDouble()   // avoid NaN values
                        else avgShakeForce += (shakeForce - avgShakeForce) / numRecordedSamples

                        onShake(calculateMovingAverage(shakeForce)) // moving avg
                    }
                }
                else handleStillDetection()
            }

            private fun handleStillDetection() {
                if(isShaking) {
                    consecutiveNumStillEvents += 1

                    if(consecutiveNumStillEvents >= ON_END_THRESHOLD) {
                        if(consecutiveNumShakeEvents >= NUM_SAMPLES_THRESHOLD) onShakeEnd(avgShakeForce)
                        else onShakeReset()

                        resetStateVariables()
                    }
                }
            }

            private fun resetStateVariables() {
                consecutiveNumShakeEvents = 0
                consecutiveNumStillEvents = 0
                avgShakeForce = 0.0
                slidingWindow.clear()
                isShaking = false
            }
        }

        unregister(context)   // ensure only one listener is active
        sensorManager.registerListener(
            sensorEventListener,
            sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
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