package com.orion.helios.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.google.android.gms.location.DeviceOrientation
import com.google.android.gms.location.DeviceOrientationListener
import com.google.android.gms.location.DeviceOrientationRequest
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.concurrent.Executors
import kotlin.math.roundToInt

data class DualOrientation(
    val compass: Float,
    val gyro: Float,
    val rawGyro: Float
)

enum class OrientationMode {
    FOP,
    FUSED_STANDARD,
    GYRO_ONLY
}

class OrientationManager(private val context: Context) {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val fusedOrientationClient = LocationServices.getFusedOrientationProviderClient(context)
    private val executor = Executors.newSingleThreadExecutor()

    private var gyroOffset = 0f

    fun calibrateGyroZero(currentRawGyro: Float, targetHeading: Float = 0f) {
        gyroOffset = (currentRawGyro - targetHeading + 360f) % 360f
    }

    fun alignGyroToCompass(currentRawGyro: Float, currentCompass: Float) {
        gyroOffset = (currentRawGyro - currentCompass + 360f) % 360f
    }

    fun getDualOrientationFlow(): Flow<DualOrientation> = callbackFlow {
        var lastCompass = 0f
        var lastRawGyro = 0f

        fun emitState() {
            val calibratedGyro = (lastRawGyro - gyroOffset + 360f) % 360f
            trySend(DualOrientation(compass = lastCompass, gyro = calibratedGyro, rawGyro = lastRawGyro))
        }

        val gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

        val gyroListener = object : SensorEventListener {
            private val rotationMatrix = FloatArray(9)
            private val orientationValues = FloatArray(3)

            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                SensorManager.getOrientation(rotationMatrix, orientationValues)
                var azimuthDeg = Math.toDegrees(orientationValues[0].toDouble()).toFloat()
                if (azimuthDeg < 0f) {
                    azimuthDeg += 360f
                }
                lastRawGyro = azimuthDeg
                emitState()
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        if (gyroSensor != null) {
            sensorManager.registerListener(gyroListener, gyroSensor, SensorManager.SENSOR_DELAY_UI)
        }

        var fopListener: DeviceOrientationListener? = null
        var compassSensorListener: SensorEventListener? = null

        val request = DeviceOrientationRequest.Builder(DeviceOrientationRequest.OUTPUT_PERIOD_DEFAULT).build()
        val listener = DeviceOrientationListener { orientation: DeviceOrientation ->
            val heading = orientation.headingDegrees
            lastCompass = if (heading < 0f) heading + 360f else heading
            emitState()
        }

        try {
            fusedOrientationClient.requestOrientationUpdates(request, executor, listener)
            fopListener = listener
        } catch (_: Exception) {
            val rotVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            val fallbackListener = object : SensorEventListener {
                private val rotationMatrix = FloatArray(9)
                private val orientationValues = FloatArray(3)

                override fun onSensorChanged(event: SensorEvent) {
                    SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                    SensorManager.getOrientation(rotationMatrix, orientationValues)
                    var azimuthDeg = Math.toDegrees(orientationValues[0].toDouble()).toFloat()
                    if (azimuthDeg < 0f) {
                        azimuthDeg += 360f
                    }
                    lastCompass = azimuthDeg
                    emitState()
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            }
            if (rotVectorSensor != null) {
                sensorManager.registerListener(fallbackListener, rotVectorSensor, SensorManager.SENSOR_DELAY_UI)
                compassSensorListener = fallbackListener
            }
        }

        awaitClose {
            sensorManager.unregisterListener(gyroListener)
            if (fopListener != null) {
                try {
                    fusedOrientationClient.removeOrientationUpdates(fopListener)
                } catch (_: Exception) {}
            }
            if (compassSensorListener != null) {
                sensorManager.unregisterListener(compassSensorListener)
            }
        }
    }

    fun getOrientationFlow(mode: OrientationMode): Flow<Float> = callbackFlow {
        when (mode) {
            OrientationMode.FOP -> {
                val listener = DeviceOrientationListener { orientation: DeviceOrientation ->
                    val heading = orientation.headingDegrees
                    val normalized = if (heading < 0f) heading + 360f else heading
                    trySend(normalized)
                }

                val request = DeviceOrientationRequest.Builder(DeviceOrientationRequest.OUTPUT_PERIOD_DEFAULT)
                    .build()

                try {
                    fusedOrientationClient.requestOrientationUpdates(request, executor, listener)
                } catch (e: Exception) {
                    val unregister = listenHardwareSensor(Sensor.TYPE_ROTATION_VECTOR) { trySend(it) }
                    awaitClose { unregister() }
                    return@callbackFlow
                }

                awaitClose {
                    try {
                        fusedOrientationClient.removeOrientationUpdates(listener)
                    } catch (_: Exception) {}
                }
            }

            OrientationMode.FUSED_STANDARD -> {
                val unregister = listenHardwareSensor(Sensor.TYPE_ROTATION_VECTOR) { trySend(it) }
                awaitClose { unregister() }
            }

            OrientationMode.GYRO_ONLY -> {
                val sensorType = Sensor.TYPE_GAME_ROTATION_VECTOR
                val unregister = listenHardwareSensor(sensorType) { rawHeading ->
                    val calibrated = (rawHeading - gyroOffset + 360f) % 360f
                    trySend(calibrated)
                }
                awaitClose { unregister() }
            }
        }
    }

    private fun listenHardwareSensor(sensorType: Int, onAzimuth: (Float) -> Unit): () -> Unit {
        val sensor = sensorManager.getDefaultSensor(sensorType)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

        val listener = object : SensorEventListener {
            private val rotationMatrix = FloatArray(9)
            private val orientationValues = FloatArray(3)

            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                SensorManager.getOrientation(rotationMatrix, orientationValues)
                var azimuthDeg = Math.toDegrees(orientationValues[0].toDouble()).toFloat()
                if (azimuthDeg < 0f) {
                    azimuthDeg += 360f
                }
                onAzimuth(azimuthDeg)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        if (sensor != null) {
            sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        }

        return {
            sensorManager.unregisterListener(listener)
        }
    }

    companion object {
        fun snapToGrid(bearing: Double, baselineAngle: Double = 0.0): Double {
            val diff = (bearing - baselineAngle + 360.0) % 360.0
            val nearestQuadrant = (diff / 90.0).roundToInt() * 90.0
            val snapped = (baselineAngle + nearestQuadrant) % 360.0
            return (snapped + 360.0) % 360.0
        }

        fun getReciprocalHeading(bearing: Double): Double {
            return (bearing + 180.0) % 360.0
        }
    }
}
