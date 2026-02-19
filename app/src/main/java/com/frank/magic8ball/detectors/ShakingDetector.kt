package com.frank.magic8ball.detectors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.frank.magic8ball.util.SynchronizedFlag
import kotlin.math.sqrt

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
    private val onShakeEnd: (Double) -> Unit = {},
) {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private lateinit var sensorEventListener: SensorEventListener
    private var isActive by SynchronizedFlag(false) // ensure thread synchronization safety

    companion object {
        private const val TAG = "Magic8Ball:ShakingDetector"
        private const val SHAKE_FORCE_THRESHOLD = 2F
        private const val ON_START_THRESHOLD = 10
        private const val ON_END_THRESHOLD = 6
        private const val SLIDING_WINDOW_SAMPLES = 5
        private const val NUM_SAMPLES_THRESHOLD = 25
        private const val ALPHA = 0.75F
    }

    fun register() {
        sensorEventListener = object : SensorEventListener {
            // shake-related state variables
            private var avgShakeForce = 0.0
            private val slidingWindow = ArrayDeque<Float>()
            private var isShaking = false
            private var consecutiveNumShakeEvents = 0
            private var consecutiveNumStillEvents = 0
            private var smoothedValues = FloatArray(3)
            private var lastValuesNotInit = true

            // calculates moving average from sliding window
            private fun calculateMovingAverage(newSample: Float): Double {
                slidingWindow.add(newSample)

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

                    if(lastValuesNotInit) {
                        smoothedValues[0] = x
                        smoothedValues[1] = y
                        smoothedValues[2] = z
                        lastValuesNotInit = false
                    }

                    smoothedValues[0] = ALPHA * smoothedValues[0] + (1 - ALPHA) * x
                    smoothedValues[1] = ALPHA * smoothedValues[1] + (1 - ALPHA) * y
                    smoothedValues[2] = ALPHA * smoothedValues[2] + (1 - ALPHA) * z

                    val filteredX = x - smoothedValues[0]
                    val filteredY = y - smoothedValues[1]
                    val filteredZ = z - smoothedValues[2]

                    // handle shake detection logic
                    val shakeForce = sqrt(filteredX * filteredX + filteredY * filteredY + filteredZ * filteredZ)
                    if(shakeForce >= SHAKE_FORCE_THRESHOLD) handleShakeDetection(shakeForce - SHAKE_FORCE_THRESHOLD)
                    else handleStillDetection()
                }
            }

            private fun handleShakeDetection(shakeForce: Float) {
                consecutiveNumShakeEvents += 1
                consecutiveNumStillEvents = 0

                if(consecutiveNumShakeEvents >= ON_START_THRESHOLD) {
                    if(!isShaking) {
                        isShaking = true
                        onShakeStart() // called only once
                    }

                    // calculate online avg for shake from start to finish
                    val numRecordedSamples = consecutiveNumShakeEvents - ON_START_THRESHOLD - 1
                    if(numRecordedSamples == 1) avgShakeForce = shakeForce.toDouble()   // avoid NaN values
                    else avgShakeForce += (shakeForce - avgShakeForce) / numRecordedSamples
                }

                if(isShaking) onShake(calculateMovingAverage(shakeForce)) // moving avg
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
                else consecutiveNumShakeEvents = 0
            }

            private fun resetStateVariables() {
                consecutiveNumShakeEvents = 0
                consecutiveNumStillEvents = 0
                avgShakeForce = 0.0
                slidingWindow.clear()
                isShaking = false
            }
        }

        unregister()   // ensure only one listener is active
        sensorManager.registerListener(
            sensorEventListener,
            sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
            SensorManager.SENSOR_DELAY_UI
        )
    }

    fun unregister() {
        try {
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