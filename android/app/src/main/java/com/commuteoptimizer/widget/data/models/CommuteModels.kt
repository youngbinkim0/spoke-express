package com.commuteoptimizer.widget.data.models

import com.google.gson.annotations.SerializedName

data class CommuteResponse(
    val options: List<CommuteOption>,
    val weather: Weather,
    val alerts: List<Alert>,
    @SerializedName("generated_at")
    val generatedAt: String
)

data class CommuteOption(
    val id: String,
    val rank: Int,
    val type: String, // "bike_to_transit" or "transit_only"
    @SerializedName("duration_minutes")
    val durationMinutes: Int,
    val summary: String,
    val legs: List<Leg>,
    val nextTrain: String,
    @SerializedName("arrival_time")
    val arrivalTime: String,
    val station: Station
)

data class Leg(
    val mode: String, // "bike", "walk", or "subway"
    val duration: Int,
    val to: String,
    val route: String? = null,
    val from: String? = null,
    val numStops: Int? = null
)

data class Station(
    val id: String,
    val name: String,
    val transiterId: String,
    val lines: List<String>,
    val lat: Double,
    val lng: Double,
    val borough: String
)

data class Weather(
    @SerializedName("temp_f")
    val tempF: Int?, // null when weather unavailable
    val conditions: String,
    @SerializedName("precipitation_type")
    val precipitationType: String, // "none", "rain", "snow", "mix"
    @SerializedName("precipitation_probability")
    val precipitationProbability: Int,
    @SerializedName("is_bad")
    val isBad: Boolean
)

data class Alert(
    val routeIds: List<String>,
    val effect: String,
    val headerText: String
)
