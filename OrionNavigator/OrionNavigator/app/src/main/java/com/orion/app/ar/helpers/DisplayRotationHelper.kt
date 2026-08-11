package com.orion.app.ar.helpers

import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.display.DisplayManager
import android.hardware.display.DisplayManager.DisplayListener
import android.view.Display
import android.view.Surface
import android.view.WindowManager
import com.google.ar.core.Session

/** Helper to track display rotations for ARCore rendering. */
class DisplayRotationHelper(context: Context) : DisplayListener {
    private var viewportChanged = false
    private var viewportWidth = 0
    private var viewportHeight = 0
    private val display: Display
    private val displayManager: DisplayManager =
        context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    private val cameraManager: CameraManager =
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    init {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        display = windowManager.defaultDisplay
    }

    fun onResume() {
        displayManager.registerDisplayListener(this, null)
    }

    fun onPause() {
        displayManager.unregisterDisplayListener(this)
    }

    fun onSurfaceChanged(width: Int, height: Int) {
        viewportWidth = width
        viewportHeight = height
        viewportChanged = true
    }

    fun updateSessionIfNeeded(session: Session) {
        if (viewportChanged) {
            val displayRotation = display.rotation
            session.setDisplayGeometry(displayRotation, viewportWidth, viewportHeight)
            viewportChanged = false
        }
    }

    fun getCameraSensorRelativeViewportAspectRatio(cameraId: String): Float {
        return when (val rotation = getCameraSensorToDisplayRotation(cameraId)) {
            90, 270 -> viewportHeight.toFloat() / viewportWidth.toFloat()
            0, 180 -> viewportWidth.toFloat() / viewportHeight.toFloat()
            else -> throw RuntimeException("Unhandled rotation: $rotation")
        }
    }

    fun getCameraSensorToDisplayRotation(cameraId: String): Int {
        val characteristics: CameraCharacteristics = try {
            cameraManager.getCameraCharacteristics(cameraId)
        } catch (e: CameraAccessException) {
            throw RuntimeException("Unable to determine display orientation", e)
        }
        val sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
        val displayOrientation = toDegrees(display.rotation)
        return (sensorOrientation - displayOrientation + 360) % 360
    }

    private fun toDegrees(rotation: Int): Int {
        return when (rotation) {
            Surface.ROTATION_0 -> 0
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> throw RuntimeException("Unknown rotation $rotation")
        }
    }

    override fun onDisplayAdded(displayId: Int) {}
    override fun onDisplayRemoved(displayId: Int) {}
    override fun onDisplayChanged(displayId: Int) {
        viewportChanged = true
    }
}
