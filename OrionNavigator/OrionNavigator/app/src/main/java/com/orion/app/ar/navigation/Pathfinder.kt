package com.orion.app.ar.navigation

import java.util.PriorityQueue

/**
 * A* Pathfinding over [NavigationGraph].
 */
object Pathfinder {
    data class PathResult(
        val waypointIds: List<String>,
        val totalCost: Float,
    )

    fun findPath(
        graph: NavigationGraph,
        startId: String,
        goalId: String,
        heuristic: (fromId: String, toId: String) -> Float = { _, _ -> 0f },
    ): PathResult? {
        if (startId == goalId) return PathResult(listOf(startId), 0f)

        data class Node(val id: String, val g: Float, val f: Float)

        val open = PriorityQueue<Node>(compareBy { it.f })
        val cameFrom = mutableMapOf<String, String>()
        val gScore = mutableMapOf(startId to 0f)
        open.add(Node(startId, 0f, heuristic(startId, goalId)))
        val closed = mutableSetOf<String>()

        while (open.isNotEmpty()) {
            val current = open.poll() ?: break
            if (current.id in closed) continue
            if (current.id == goalId) {
                return PathResult(reconstruct(cameFrom, goalId), current.g)
            }
            closed += current.id
            for ((neighbor, weight) in graph.neighbors(current.id)) {
                if (neighbor in closed) continue
                val tentative = current.g + weight
                if (tentative < (gScore[neighbor] ?: Float.POSITIVE_INFINITY)) {
                    cameFrom[neighbor] = current.id
                    gScore[neighbor] = tentative
                    open.add(Node(neighbor, tentative, tentative + heuristic(neighbor, goalId)))
                }
            }
        }
        return null
    }

    private fun reconstruct(cameFrom: Map<String, String>, goalId: String): List<String> {
        val path = mutableListOf(goalId)
        var cur = goalId
        while (cameFrom.containsKey(cur)) {
            cur = cameFrom.getValue(cur)
            path.add(0, cur)
        }
        return path
    }
}
