package com.commuteoptimizer.widget.data.models

import com.google.gson.annotations.SerializedName

// Response from Transiter demo API: https://demo.transiter.dev/systems/us-ny-subway/stops/{stopId}
data class TransiterStopResponse(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val stopTimes: List<TransiterStopTime>? = null
)

data class TransiterStopTime(
    val arrival: TransiterArrivalTime?,
    val departure: TransiterArrivalTime?,
    val trip: TransiterTrip
)

data class TransiterArrivalTime(
    val time: String  // Unix timestamp as string
)

data class TransiterTrip(
    val id: String,
    val route: TransiterRoute,
    val direction: String? = null,
    val destination: TransiterDestination? = null
)

data class TransiterRoute(
    val id: String,
    val color: String? = null
)

data class TransiterDestination(
    val id: String,
    val name: String
)

// Local station data (bundled in assets)
data class LocalStation(
    val id: String,
    val name: String,
    val lines: List<String>,
    val lat: Double,
    val lng: Double,
    val borough: String = "Brooklyn"
)
