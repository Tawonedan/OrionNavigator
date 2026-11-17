package com.orion.helios.model

import com.google.gson.annotations.SerializedName

data class MapEdge(
    @SerializedName("from")
    val from: Int,
    @SerializedName("to")
    val to: Int,
    @SerializedName("steps")
    val steps: Int,
    @SerializedName("degree")
    val degree: Double,
    @SerializedName("gyro")
    val gyro: Double? = null,
    @SerializedName("departureDegree")
    val departureDegree: Double? = null,
    @SerializedName("departureGyro")
    val departureGyro: Double? = null,
    @SerializedName("arrivalDegree")
    val arrivalDegree: Double? = null,
    @SerializedName("arrivalGyro")
    val arrivalGyro: Double? = null
)
