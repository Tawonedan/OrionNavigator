package com.orion.app.ar.helpers

import android.app.Activity
import android.os.Build
import android.view.WindowManager
import com.google.ar.core.Camera
import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.TrackingState

/** Gets human readable tracking failure reasons and TTS hints for visually impaired users. */
class TrackingStateHelper(private val activity: Activity) {
    companion object {
        private const val INSUFFICIENT_FEATURES_MESSAGE =
            "Pencahayaan kurang atau area terlalu polos. Arahkan kamera ke area bertekstur."
        private const val EXCESSIVE_MOTION_MESSAGE = "Pergerakan terlalu cepat. Kurangi kecepatan pergerakan kamera."
        private const val INSUFFICIENT_LIGHT_MESSAGE =
            "Area terlalu gelap. Pindahlah ke tempat yang lebih terang."
        private const val BAD_STATE_MESSAGE =
            "Pelacakan AR terganggu. Silakan mulai ulang pemindaian."
        private const val CAMERA_UNAVAILABLE_MESSAGE =
            "Kamera sedang digunakan oleh aplikasi lain."

        fun getTrackingFailureReasonString(camera: Camera): String {
            return when (camera.trackingFailureReason) {
                TrackingFailureReason.NONE -> ""
                TrackingFailureReason.BAD_STATE -> BAD_STATE_MESSAGE
                TrackingFailureReason.INSUFFICIENT_LIGHT -> INSUFFICIENT_LIGHT_MESSAGE
                TrackingFailureReason.EXCESSIVE_MOTION -> EXCESSIVE_MOTION_MESSAGE
                TrackingFailureReason.INSUFFICIENT_FEATURES -> INSUFFICIENT_FEATURES_MESSAGE
                TrackingFailureReason.CAMERA_UNAVAILABLE -> CAMERA_UNAVAILABLE_MESSAGE
                else -> "Gangguan pelacakan AR."
            }
        }
    }

    private var previousTrackingState: TrackingState? = null

    fun updateKeepScreenOnFlag(trackingState: TrackingState) {
        if (trackingState == previousTrackingState) return
        previousTrackingState = trackingState
        when (trackingState) {
            TrackingState.PAUSED, TrackingState.STOPPED -> {
                activity.runOnUiThread {
                    activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
            TrackingState.TRACKING -> {
                activity.runOnUiThread {
                    activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
        }
    }
}
