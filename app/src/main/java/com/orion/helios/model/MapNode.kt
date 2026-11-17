package com.orion.helios.model

import com.google.gson.annotations.SerializedName

data class MapNode(
    @SerializedName("id")
    val id: Int,
    @SerializedName("name")
    val name: String,
    @SerializedName("isDestination")
    val isDestination: Boolean = false
)

