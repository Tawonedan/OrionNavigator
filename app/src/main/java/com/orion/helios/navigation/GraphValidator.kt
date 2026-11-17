package com.orion.helios.navigation

import com.orion.helios.model.BuildingMap
import com.orion.helios.model.MapEdge
import com.orion.helios.model.MapNode

data class ValidationIssue(
    val title: String,
    val description: String,
    val isCritical: Boolean
)

object GraphValidator {

    fun validate(map: BuildingMap): List<ValidationIssue> {
        val issues = mutableListOf<ValidationIssue>()
        val nodeMap = map.nodes.associateBy { it.id }

        for (node in map.nodes) {
            val hasEdge = map.edges.any { it.from == node.id || it.to == node.id }
            if (!hasEdge) {
                issues.add(
                    ValidationIssue(
                        title = "Orphan Node",
                        description = "Node ${node.id} (${node.name}) has no connected edges.",
                        isCritical = true
                    )
                )
            }
        }

        for (edge in map.edges) {
            if (!nodeMap.containsKey(edge.from)) {
                issues.add(
                    ValidationIssue(
                        title = "Invalid Source Node",
                        description = "Edge starts at non-existent node ID ${edge.from}.",
                        isCritical = true
                    )
                )
            }
            if (!nodeMap.containsKey(edge.to)) {
                issues.add(
                    ValidationIssue(
                        title = "Invalid Target Node",
                        description = "Edge ends at non-existent node ID ${edge.to}.",
                        isCritical = true
                    )
                )
            }

            val hasReverse = map.edges.any { it.from == edge.to && it.to == edge.from }
            if (!hasReverse) {
                issues.add(
                    ValidationIssue(
                        title = "One-Way Corridor",
                        description = "Edge from ${edge.from} to ${edge.to} has no reverse return edge.",
                        isCritical = false
                    )
                )
            }
        }

        return issues
    }
}

