package com.commuteoptimizer.widget.service

import kotlin.math.*

/**
 * Calculates distances and travel time estimates using the Haversine formula.
 */
object DistanceCalculator {

    private const val EARTH_RADIUS_MILES = 3959.0
    private const val BIKING_SPEED_MPH = 10.0
    private const val WALKING_SPEED_MPH = 3.0

    /**
     * Calculate the great-circle distance between two points using Haversine formula.
     * @return Distance in miles
     */
    fun haversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = toRadians(lat2 - lat1)
        val dLon = toRadians(lon2 - lon1)

        val a = sin(dLat / 2).pow(2) +
                cos(toRadians(lat1)) * cos(toRadians(lat2)) * sin(dLon / 2).pow(2)

        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return EARTH_RADIUS_MILES * c
    }

    /**
     * Estimate bike travel time between two points.
     * Uses 30% padding to account for NYC grid, traffic, and locking (matches webapp).
     * @return Time in minutes
     */
    fun estimateBikeTime(
        fromLat: Double, fromLng: Double,
        toLat: Double, toLng: Double
    ): Int {
        val distanceMiles = haversineDistance(fromLat, fromLng, toLat, toLng)
        // Match webapp: ceil((distance / 10) * 60 * 1.3)
        val timeHours = distanceMiles / BIKING_SPEED_MPH
        return ceil(timeHours * 60 * 1.3).toInt()
    }

    /**
     * Estimate walking time between two points.
     * Uses 20% padding for walking routes (matches webapp).
     * @return Time in minutes
     */
    fun estimateWalkTime(
        fromLat: Double, fromLng: Double,
        toLat: Double, toLng: Double
    ): Int {
        val distanceMiles = haversineDistance(fromLat, fromLng, toLat, toLng)
        // Match webapp: ceil((distance / 3) * 60 * 1.2)
        val timeHours = distanceMiles / WALKING_SPEED_MPH
        return ceil(timeHours * 60 * 1.2).toInt()
    }

    /**
     * Estimate subway transit time between two stations.
     * Uses average of 2.5 minutes per stop as baseline.
     */
    fun estimateTransitTime(fromStopId: String, toStopId: String): Int {
        // Known routes with pre-calculated times
        val knownRoutes = mapOf(
            "G33_G22" to 18, // Bedford-Nostrand to Court Sq (G)
            "G34_G22" to 16, // Classon to Court Sq (G)
            "G35_G22" to 14, // Clinton-Washington to Court Sq (G)
            "G36_G22" to 12, // Fulton to Court Sq (G)
            "A42_G22" to 15  // Hoyt-Schermerhorn to Court Sq
        )

        val key = "${fromStopId}_${toStopId}"
        return knownRoutes[key] ?: 0 // Unknown - no valid data
    }

    private fun toRadians(degrees: Double): Double = degrees * PI / 180
}
