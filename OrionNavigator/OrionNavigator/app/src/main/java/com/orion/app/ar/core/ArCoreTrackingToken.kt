package com.orion.app.ar.core

import com.google.ar.core.Anchor
import com.google.ar.core.Session
import com.orion.app.ar.backend.LocalTrackingToken

/**
 * ARCore-specific [LocalTrackingToken].
 */
class ArCoreTrackingToken(
    val session: Session,
    val anchor: Anchor,
) : LocalTrackingToken

/**
 * Live ARCore anchors for drawing navigation pins. Keyed by waypoint id.
 */
class LiveAnchorRegistry {
    private val lock = Any()
    private val anchors = mutableMapOf<String, Anchor>()

    fun put(waypointId: String, anchor: Anchor) {
        synchronized(lock) {
            anchors[waypointId]?.let {
                try {
                    it.detach()
                } catch (_: Exception) {
                }
            }
            anchors[waypointId] = anchor
        }
    }

    fun get(waypointId: String): Anchor? = synchronized(lock) { anchors[waypointId] }

    fun remove(waypointId: String) {
        synchronized(lock) {
            anchors.remove(waypointId)?.let {
                try {
                    it.detach()
                } catch (_: Exception) {
                }
            }
        }
    }

    fun clear() {
        synchronized(lock) {
            anchors.values.forEach {
                try {
                    it.detach()
                } catch (_: Exception) {
                }
            }
            anchors.clear()
        }
    }

    fun snapshot(): Map<String, Anchor> = synchronized(lock) { anchors.toMap() }
}
