package com.orion.app.ar.ui

/** Map = mark / edit. Navigate = pick destination. */
enum class AppMode(val label: String) {
    MAP("Mode Peta"),
    NAVIGATE("Mode Navigasi"),
}

/** Turn classification for the 2D instruction banner (AR path uses world chevrons). */
enum class NavTurn {
    NONE,
    FORWARD,
    LEFT,
    RIGHT,
    ARRIVED,
    LOST,
}

data class WaypointScreenLabel(
    val id: String,
    val name: String,
    val typeLabel: String,
    val screenX: Float,
    val screenY: Float,
)

data class PlacementPreview(
    val canPlace: Boolean,
    val hint: String,
)
