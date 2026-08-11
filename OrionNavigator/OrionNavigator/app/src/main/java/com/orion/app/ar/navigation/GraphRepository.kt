package com.orion.app.ar.navigation

import android.content.Context

class GraphRepository(private val context: Context) {
    @Volatile
    var graph: NavigationGraph = NavigationGraph()
        private set

    fun load() {
        graph = GraphPersistence.load(context)
    }

    fun save() {
        GraphPersistence.save(context, graph)
    }

    fun setGraph(newGraph: NavigationGraph) {
        graph = newGraph
        save()
    }

    fun connect(fromId: String, toId: String, weight: Float = 1f) {
        graph = graph.withEdge(fromId, toId, weight)
        save()
    }

    fun disconnect(fromId: String, toId: String) {
        graph = graph.withoutEdge(fromId, toId)
        save()
    }

    fun removeWaypoint(id: String) {
        graph = graph.withoutWaypoint(id)
        save()
    }

    fun clear() {
        graph = NavigationGraph()
        GraphPersistence.clear(context)
    }
}
