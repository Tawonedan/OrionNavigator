package com.orion.app.ar.waypoints

import android.content.Context

/**
 * In-memory + disk waypoint store. Does not know about Google / ARCore Anchors.
 */
class WaypointRepository(private val context: Context) {
    private val lock = Any()
    private val _waypoints = mutableListOf<Waypoint>()

    val waypoints: List<Waypoint>
        get() = synchronized(lock) { _waypoints.toList() }

    fun loadFromDisk() {
        synchronized(lock) {
            _waypoints.clear()
            _waypoints.addAll(WaypointPersistence.load(context))
        }
    }

    fun add(waypoint: Waypoint) {
        synchronized(lock) { _waypoints.add(waypoint) }
        persist()
    }

    fun update(waypoint: Waypoint) {
        synchronized(lock) {
            val i = _waypoints.indexOfFirst { it.id == waypoint.id }
            if (i >= 0) _waypoints[i] = waypoint else _waypoints.add(waypoint)
        }
        persist()
    }

    fun setBackendAnchorId(id: String, backendAnchorId: String) {
        synchronized(lock) {
            val i = _waypoints.indexOfFirst { it.id == id }
            if (i < 0) return
            _waypoints[i] = _waypoints[i].copy(backendAnchorId = backendAnchorId)
        }
        persist()
    }

    fun remove(id: String) {
        synchronized(lock) { _waypoints.removeAll { it.id == id } }
        persist()
    }

    fun clearAll() {
        synchronized(lock) { _waypoints.clear() }
        WaypointPersistence.clear(context)
    }

    fun find(id: String): Waypoint? = synchronized(lock) { _waypoints.find { it.id == id } }

    fun resolvable(): List<Waypoint> =
        synchronized(lock) { _waypoints.filter { it.isLocalizedAcrossSessions } }

    private fun persist() {
        WaypointPersistence.save(context, waypoints)
    }
}
