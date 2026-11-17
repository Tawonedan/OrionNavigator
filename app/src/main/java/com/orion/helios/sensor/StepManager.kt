package com.orion.helios.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class StepManager(private val context: Context) {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vibratorManager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    fun getStepEventsFlow(): Flow<Unit> = callbackFlow {
        val stepDetector = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
        val stepCounter = if (stepDetector == null) {
            sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        } else null

        var initialCounterValue = -1f

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type == Sensor.TYPE_STEP_DETECTOR) {
                    triggerHaptic()
                    trySend(Unit)
                } else if (event.sensor.type == Sensor.TYPE_STEP_COUNTER) {
                    val count = event.values[0]
                    if (initialCounterValue < 0f) {
                        initialCounterValue = count
                    } else if (count > initialCounterValue) {
                        val steps = (count - initialCounterValue).toInt()
                        initialCounterValue = count
                        repeat(steps) {
                            triggerHaptic()
                            trySend(Unit)
                        }
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        val targetSensor = stepDetector ?: stepCounter
        if (targetSensor != null) {
            sensorManager.registerListener(listener, targetSensor, SensorManager.SENSOR_DELAY_UI)
        }

        awaitClose {
            sensorManager.unregisterListener(listener)
        }
    }

    private fun triggerHaptic() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(35, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(35)
            }
        } catch (_: Exception) {}
    }
}

