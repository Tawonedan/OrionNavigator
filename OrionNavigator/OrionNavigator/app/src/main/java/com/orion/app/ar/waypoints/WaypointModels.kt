package com.orion.app.ar.waypoints

import java.util.UUID

enum class WaypointType(val label: String) {
    DOOR("Door"),
    ROOM("Room"),
    HALLWAY("Hallway"),
    OTHER("Spot"),
}

/**
 * Named place in the facility. Independent of which localization backend created the anchor.
 *
 * [backendAnchorId] is Google Cloud Anchor ID.
 */
data class Waypoint(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val type: WaypointType,
    val backendAnchorId: String? = null,
    val floor: Int = 0,
    val metadata: Map<String, String> = emptyMap(),
) {
    val isLocalizedAcrossSessions: Boolean
        get() = !backendAnchorId.isNullOrBlank()
}

/** List row for Navigation UI — no ARCore types. */
data class UiMarker(
    val id: String,
    val name: String,
    val type: WaypointType,
    val cloudReady: Boolean,
)

/** One undirected connection for the Connections UI. */
data class UiEdge(
    val fromId: String,
    val toId: String,
    val fromName: String,
    val toName: String,
    val weightMeters: Float,
)
