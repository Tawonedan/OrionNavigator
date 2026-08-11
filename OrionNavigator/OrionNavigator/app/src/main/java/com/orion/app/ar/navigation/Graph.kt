package com.orion.app.ar.navigation

/**
 * Undirected navigation graph between waypoint ids.
 * Independent of localization backend.
 */
data class GraphEdge(
    val fromId: String,
    val toId: String,
    val weight: Float = 1f,
)

data class NavigationGraph(
    val edges: List<GraphEdge> = emptyList(),
) {
    fun neighbors(id: String): List<Pair<String, Float>> {
        val out = mutableListOf<Pair<String, Float>>()
        for (e in edges) {
            when (id) {
                e.fromId -> out += e.toId to e.weight
                e.toId -> out += e.fromId to e.weight
            }
        }
        return out
    }

    fun withEdge(fromId: String, toId: String, weight: Float = 1f): NavigationGraph {
        if (fromId == toId) return this
        if (hasEdge(fromId, toId)) return this
        return copy(edges = edges + GraphEdge(fromId, toId, weight))
    }

    fun hasEdge(fromId: String, toId: String): Boolean =
        edges.any {
            (it.fromId == fromId && it.toId == toId) || (it.fromId == toId && it.toId == fromId)
        }

    fun withoutEdge(fromId: String, toId: String): NavigationGraph =
        copy(
            edges =
                edges.filterNot {
                    (it.fromId == fromId && it.toId == toId) || (it.fromId == toId && it.toId == fromId)
                },
        )

    fun withoutWaypoint(id: String): NavigationGraph =
        copy(edges = edges.filter { it.fromId != id && it.toId != id })
}
