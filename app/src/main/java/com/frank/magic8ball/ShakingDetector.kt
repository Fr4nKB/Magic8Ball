package com.frank.magic8ball

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.abs

class ShakingDetector(
    private val context: Context,
    private val onShakeStart: () -> Unit = {},
    private val onShakeEnd: (Double) -> Unit = {}
) {
    private lateinit var sensorEventListener: SensorEventListener
    private var isActive by SynchronizedFlag(false)

    companion object {
        const val TAG = "Magic8Ball:ShakingDetector"
        const val SHAKE_FORCE_THRESHOLD = 1.0
        const val ON_START_THRESHOLD = 3
        const val ON_END_THRESHOLD = 10
        const val NUM_SAMPLE_THRESHOLD = 30
    }

    fun register() {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

        sensorEventListener = object : SensorEventListener {
            // hold sensor values of the previous event
            private var lastX = 0f
            private var lastY = 0f

            private var values: MutableList<Float> = mutableListOf()
            private var isShaking = false
            private var consecutiveNumShakeEvents = 0
            private var consecutiveNumStillEvents = 0

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}

            override fun onSensorChanged(event: SensorEvent) {
                if(!isActive) {
                    consecutiveNumShakeEvents = 0
                    consecutiveNumStillEvents = 0
                    values.clear()
                    isShaking = false
                    return
                }

                if(event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                    val x = event.values[0]
                    val y = event.values[1]

                    // calculate the force of shake
                    val deltaX = x - lastX
                    val deltaY = y - lastY
                    val shakeForce = abs(deltaX + deltaY)

                    lastX = x
                    lastY = y

                    if(shakeForce > SHAKE_FORCE_THRESHOLD) {
                        // count consecutive shake events
                        consecutiveNumShakeEvents += 1
                        consecutiveNumStillEvents = 0

                        if(consecutiveNumShakeEvents >= ON_START_THRESHOLD) {
                            if(!isShaking) {
                                isShaking = true
                                onShakeStart()  // called only once
                            }

                            // add values to the list for later postprocessing
                            values.add(shakeForce)
                        }
                    }
                    else {
                        if(isShaking) {
                            consecutiveNumStillEvents += 1

                            if(consecutiveNumStillEvents >= ON_END_THRESHOLD) {
                                if(values.size >= NUM_SAMPLE_THRESHOLD) onShakeEnd(values.average())

                                // reset state variables
                                consecutiveNumShakeEvents = 0
                                consecutiveNumStillEvents = 0
                                values.clear()
                                isShaking = false
                            }
                        }
                    }
                }
            }
        }

        unregister(context)   // ensure only one listener is active
        sensorManager.registerListener(
            sensorEventListener,
            sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
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