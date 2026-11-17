package com.orion.helios.model

import com.google.gson.annotations.SerializedName

data class BuildingMap(
    @SerializedName("buildingName")
    val buildingName: String,
    @SerializedName("nodes")
    val nodes: List<MapNode> = emptyList(),
    @SerializedName("edges")
    val edges: List<MapEdge> = emptyList()
)

