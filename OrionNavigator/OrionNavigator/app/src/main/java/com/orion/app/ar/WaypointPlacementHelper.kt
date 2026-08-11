package com.orion.app.ar

import com.google.ar.core.Camera
import com.google.ar.core.Frame
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.core.Point
import com.google.ar.core.Pose
import com.google.ar.core.TrackingState
import com.orion.app.ar.waypoints.WaypointType

/**
 * Finds a real-world surface under the screen center (crosshair).
 * [WaypointType] changes what we prefer: floor for rooms, walls for doors.
 */
object WaypointPlacementHelper {
    fun findPlacementHit(
        frame: Frame,
        camera: Camera,
        screenX: Float,
        screenY: Float,
        type: WaypointType,
    ): HitResult? {
        val hits = frame.hitTest(screenX, screenY).filter { isValidPlacementHit(it, camera) }
        if (hits.isEmpty()) return null

        return when (type) {
            WaypointType.ROOM, WaypointType.HALLWAY -> pickHorizontalFloor(hits) ?: hits.first()
            WaypointType.DOOR -> pickVerticalWall(hits) ?: hits.first()
            WaypointType.OTHER -> hits.first()
        }
    }

    private fun pickHorizontalFloor(hits: List<HitResult>): HitResult? {
        return hits.firstOrNull { hit ->
            val plane = hit.trackable as? Plane
            plane?.type == Plane.Type.HORIZONTAL_UPWARD_FACING
        }
    }

    private fun pickVerticalWall(hits: List<HitResult>): HitResult? {
        return hits.firstOrNull { hit ->
            val plane = hit.trackable as? Plane
            plane?.type == Plane.Type.VERTICAL
        }
    }

    private fun isValidPlacementHit(hit: HitResult, camera: Camera): Boolean {
        when (val trackable = hit.trackable) {
            is Plane ->
                return trackable.trackingState == TrackingState.TRACKING &&
                        trackable.isPoseInPolygon(hit.hitPose) &&
                        calculateDistanceToPlane(hit.hitPose, camera.pose) > 0f
            is Point ->
                return trackable.trackingState == TrackingState.TRACKING &&
                        trackable.orientationMode == Point.OrientationMode.ESTIMATED_SURFACE_NORMAL
            else -> return false
        }
    }

    private fun calculateDistanceToPlane(planePose: Pose, cameraPose: Pose): Float {
        val planeNormal = FloatArray(3)
        planePose.getTransformedAxis(1, 1.0f, planeNormal, 0)
        return (cameraPose.tx() - planePose.tx()) * planeNormal[0] +
                (cameraPose.ty() - planePose.ty()) * planeNormal[1] +
                (cameraPose.tz() - planePose.tz()) * planeNormal[2]
    }

    fun aimHint(type: WaypointType, canPlace: Boolean): String {
        if (!canPlace) {
            return when (type) {
                WaypointType.ROOM -> "Arahkan ke lantai di tengah ruangan."
                WaypointType.HALLWAY -> "Arahkan ke lantai koridor."
                WaypointType.DOOR -> "Arahkan ke bingkai pintu atau dinding."
                WaypointType.OTHER -> "Arahkan ke lantai atau dinding."
            }
        }
        return "Target terkunci — tekan Tandai Lokasi."
    }

    fun hintForMiss(type: WaypointType): String =
        when (type) {
            WaypointType.ROOM -> "Arahkan HP ke lantai ruangan, tunggu indikator hijau, lalu tekan Tandai Lokasi."
            WaypointType.HALLWAY -> "Arahkan HP ke lantai koridor, tunggu indikator hijau, lalu tekan Tandai Lokasi."
            WaypointType.DOOR -> "Arahkan HP ke bingkai pintu/dinding, tunggu indikator hijau, lalu tekan Tandai Lokasi."
            WaypointType.OTHER -> "Arahkan HP ke lantai/dinding, tunggu indikator hijau, lalu tekan Tandai Lokasi."
        }

    fun poseForMarker(hit: HitResult): Pose {
        return hit.hitPose.compose(Pose.makeTranslation(0f, 0.02f, 0f))
    }
}
