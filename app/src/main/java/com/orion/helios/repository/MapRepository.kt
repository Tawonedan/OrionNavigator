package com.orion.helios.repository

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.orion.helios.model.BuildingMap
import com.orion.helios.model.MapEdge
import com.orion.helios.model.MapNode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class MapRepository(private val context: Context) {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val draftFile = File(context.filesDir, "helios_map_draft.json")

    private val _mapState = MutableStateFlow(loadInitialMap())
    val mapState: StateFlow<BuildingMap> = _mapState.asStateFlow()

    private fun loadInitialMap(): BuildingMap {
        if (draftFile.exists()) {
            try {
                val json = draftFile.readText()
                val parsed = gson.fromJson(json, BuildingMap::class.java)
                if (parsed != null) return parsed
            } catch (_: Exception) {}
        }
        return BuildingMap(
            buildingName = "New Building",
            nodes = emptyList(),
            edges = emptyList()
        )
    }

    private fun persistDraft() {
        try {
            val json = gson.toJson(_mapState.value)
            draftFile.writeText(json)
        } catch (_: Exception) {}
    }

    fun updateBuildingName(name: String) {
        _mapState.value = _mapState.value.copy(buildingName = name)
        persistDraft()
    }

    fun addNode(name: String, isDestination: Boolean): MapNode {
        val currentNodes = _mapState.value.nodes
        val nextId = (currentNodes.maxOfOrNull { it.id } ?: 0) + 1
        val newNode = MapNode(id = nextId, name = name, isDestination = isDestination)
        _mapState.value = _mapState.value.copy(nodes = currentNodes + newNode)
        persistDraft()
        return newNode
    }

    fun updateNode(node: MapNode) {
        val currentNodes = _mapState.value.nodes.toMutableList()
        val index = currentNodes.indexOfFirst { it.id == node.id }
        if (index >= 0) {
            currentNodes[index] = node
            _mapState.value = _mapState.value.copy(nodes = currentNodes)
            persistDraft()
        }
    }

    fun deleteNode(nodeId: Int) {
        val updatedNodes = _mapState.value.nodes.filterNot { it.id == nodeId }
        val updatedEdges = _mapState.value.edges.filterNot { it.from == nodeId || it.to == nodeId }
        _mapState.value = _mapState.value.copy(nodes = updatedNodes, edges = updatedEdges)
        persistDraft()
    }

    fun addEdge(
        from: Int,
        to: Int,
        steps: Int,
        degree: Double,
        gyro: Double? = null,
        departureDegree: Double? = null,
        departureGyro: Double? = null,
        arrivalDegree: Double? = null,
        arrivalGyro: Double? = null,
        autoCreateReverse: Boolean = true
    ): Pair<MapEdge, MapEdge?> {
        val effectiveDepDeg = departureDegree ?: degree
        val effectiveDepGyro = departureGyro ?: gyro

        val forwardEdge = MapEdge(
            from = from,
            to = to,
            steps = steps,
            degree = effectiveDepDeg,
            gyro = effectiveDepGyro,
            departureDegree = effectiveDepDeg,
            departureGyro = effectiveDepGyro,
            arrivalDegree = arrivalDegree,
            arrivalGyro = arrivalGyro
        )
        val edgeList = _mapState.value.edges.filterNot { it.from == from && it.to == to }.toMutableList()
        edgeList.add(forwardEdge)

        var reverseEdge: MapEdge? = null
        if (autoCreateReverse) {
            val revDegree = (effectiveDepDeg + 180.0) % 360.0
            val revGyro = effectiveDepGyro?.let { (it + 180.0) % 360.0 }
            val revArrDegree = arrivalDegree?.let { (it + 180.0) % 360.0 }
            val revArrGyro = arrivalGyro?.let { (it + 180.0) % 360.0 }

            reverseEdge = MapEdge(
                from = to,
                to = from,
                steps = steps,
                degree = revDegree,
                gyro = revGyro,
                departureDegree = revDegree,
                departureGyro = revGyro,
                arrivalDegree = revArrDegree,
                arrivalGyro = revArrGyro
            )
            edgeList.removeAll { it.from == to && it.to == from }
            edgeList.add(reverseEdge)
        }

        _mapState.value = _mapState.value.copy(edges = edgeList)
        persistDraft()
        return Pair(forwardEdge, reverseEdge)
    }

    fun deleteEdge(from: Int, to: Int) {
        val updatedEdges = _mapState.value.edges.filterNot { it.from == from && it.to == to }
        _mapState.value = _mapState.value.copy(edges = updatedEdges)
        persistDraft()
    }

    fun parseMapFromJson(jsonString: String): BuildingMap? {
        return try {
            gson.fromJson(jsonString, BuildingMap::class.java)
        } catch (_: Exception) {
            null
        }
    }

    fun toJsonString(): String {
        return gson.toJson(_mapState.value)
    }

    fun loadFromJson(jsonString: String): Boolean {
        return try {
            val loaded = gson.fromJson(jsonString, BuildingMap::class.java)
            if (loaded != null && loaded.nodes.isNotEmpty()) {
                _mapState.value = loaded
                persistDraft()
                true
            } else false
        } catch (_: Exception) {
            false
        }
    }

    fun resetMap(name: String = "New Building") {
        _mapState.value = BuildingMap(buildingName = name, nodes = emptyList(), edges = emptyList())
        persistDraft()
    }
}

