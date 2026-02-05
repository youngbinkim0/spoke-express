package com.commuteoptimizer.widget.service

import android.content.Context
import com.commuteoptimizer.widget.data.models.LocalStation
import com.google.gson.Gson
import com.google.gson.JsonObject

class LocalDataSource(private val context: Context) {
    private var cachedStations: List<LocalStation>? = null

    fun getStations(): List<LocalStation> {
        cachedStations?.let { return it }
        return try {
            val json = context.assets.open("stations.json").bufferedReader().use { it.readText() }
            val gson = Gson()
            // Handle both formats: {"stations": [...]} or direct array [...]
            val stations = try {
                val wrapper = gson.fromJson(json, JsonObject::class.java)
                val stationsArray = wrapper.getAsJsonArray("stations")
                gson.fromJson(stationsArray, Array<LocalStation>::class.java).toList()
            } catch (e: Exception) {
                // Fallback to direct array format
                gson.fromJson(json, Array<LocalStation>::class.java).toList()
            }
            cachedStations = stations
            stations
        } catch (e: Exception) { emptyList() }
    }

    fun getStation(id: String): LocalStation? = getStations().find { it.id == id }

    fun autoSelectStations(homeLat: Double, homeLng: Double, workLat: Double, workLng: Double): List<String> {
        val radiusMiles = 4.0
        val avgSubwaySpeedMph = 15.0
        val topPerLine = 3

        val allStations = getStations()

        // 1. Filter within radius
        val candidates = allStations.filter { station ->
            DistanceCalculator.haversineDistance(homeLat, homeLng, station.lat, station.lng) <= radiusMiles
        }

        // 2. Score each: bike_time + estimated_transit_time
        data class ScoredStation(val station: LocalStation, val score: Double)
        val scored = candidates.map { station ->
            val bikeTime = DistanceCalculator.estimateBikeTime(homeLat, homeLng, station.lat, station.lng).toDouble()
            val transitEst = (DistanceCalculator.haversineDistance(station.lat, station.lng, workLat, workLng) / avgSubwaySpeedMph) * 60
            ScoredStation(station, bikeTime + transitEst)
        }

        // 3. Group by line, top N per line
        val byLine = mutableMapOf<String, MutableList<ScoredStation>>()
        for (item in scored) {
            for (line in item.station.lines) {
                byLine.getOrPut(line) { mutableListOf() }.add(item)
            }
        }

        // 4. Collect top per line
        val selectedIds = mutableSetOf<String>()
        for ((_, stations) in byLine) {
            stations.sortBy { it.score }
            stations.take(topPerLine).forEach { selectedIds.add(it.station.id) }
        }

        // 5. Return sorted by score
        return scored
            .filter { it.station.id in selectedIds }
            .sortedBy { it.score }
            .map { it.station.id }
    }
}
