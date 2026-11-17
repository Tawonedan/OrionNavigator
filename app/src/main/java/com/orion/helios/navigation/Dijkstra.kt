package com.orion.helios.navigation

import com.orion.helios.model.MapEdge
import com.orion.helios.model.MapNode
import java.util.PriorityQueue

data class RouteSegment(
    val fromNode: MapNode,
    val toNode: MapNode,
    val steps: Int,
    val degree: Double,
    val turnDirection: String
)

data class RouteResult(
    val pathNodes: List<MapNode>,
    val segments: List<RouteSegment>,
    val totalSteps: Int
)

class Dijkstra(
    private val nodes: Map<Int, MapNode>,
    private val edges: List<MapEdge>
) {

    private data class CostNode(val id: Int, val cost: Int) : Comparable<CostNode> {
        override fun compareTo(other: CostNode): Int = this.cost.compareTo(other.cost)
    }

    fun findPath(startId: Int, destId: Int): RouteResult? {
        if (!nodes.containsKey(startId) || !nodes.containsKey(destId)) return null
        if (startId == destId) {
            val node = nodes[startId] ?: return null
            return RouteResult(listOf(node), emptyList(), 0)
        }

        val adjacency = mutableMapOf<Int, MutableList<MapEdge>>()
        for (edge in edges) {
            adjacency.getOrPut(edge.from) { mutableListOf() }.add(edge)
        }

        val distances = mutableMapOf<Int, Int>()
        val previous = mutableMapOf<Int, Pair<Int, MapEdge>>()
        val pq = PriorityQueue<CostNode>()

        nodes.keys.forEach { distances[it] = Int.MAX_VALUE }
        distances[startId] = 0
        pq.add(CostNode(startId, 0))

        while (pq.isNotEmpty()) {
            val current = pq.poll() ?: break
            val currId = current.id
            val currCost = current.cost

            if (currId == destId) break
            if (currCost > (distances[currId] ?: Int.MAX_VALUE)) continue

            val neighbors = adjacency[currId] ?: emptyList()
            for (edge in neighbors) {
                val nextId = edge.to
                val newDist = currCost + edge.steps
                if (newDist < (distances[nextId] ?: Int.MAX_VALUE)) {
                    distances[nextId] = newDist
                    previous[nextId] = Pair(currId, edge)
                    pq.add(CostNode(nextId, newDist))
                }
            }
        }

        if (!previous.containsKey(destId)) return null

        val pathIds = mutableListOf<Int>()
        val traversedEdges = mutableListOf<MapEdge>()
        var curr = destId
        pathIds.add(curr)

        while (curr != startId) {
            val stepInfo = previous[curr] ?: return null
            traversedEdges.add(0, stepInfo.second)
            curr = stepInfo.first
            pathIds.add(0, curr)
        }

        val pathNodes = pathIds.mapNotNull { nodes[it] }
        val segments = mutableListOf<RouteSegment>()
        var prevBearing: Double? = null

        for (edge in traversedEdges) {
            val fromNode = nodes[edge.from] ?: continue
            val toNode = nodes[edge.to] ?: continue
            val turn = calculateTurn(prevBearing, edge.degree)
            prevBearing = edge.degree
            segments.add(
                RouteSegment(
                    fromNode = fromNode,
                    toNode = toNode,
                    steps = edge.steps,
                    degree = edge.degree,
                    turnDirection = turn
                )
            )
        }

        val totalSteps = segments.sumOf { it.steps }
        return RouteResult(pathNodes, segments, totalSteps)
    }

    private fun calculateTurn(prevBearing: Double?, currentBearing: Double): String {
        if (prevBearing == null) return "Start forward"
        var diff = currentBearing - prevBearing
        while (diff < -180.0) diff += 360.0
        while (diff > 180.0) diff -= 360.0

        return when {
            diff in -20.0..20.0 -> "Continue straight"
            diff in 20.0..160.0 -> "Turn right (${diff.toInt()} deg)"
            diff in -160.0..-20.0 -> "Turn left (${(-diff).toInt()} deg)"
            else -> "Turn around"
        }
    }
}

