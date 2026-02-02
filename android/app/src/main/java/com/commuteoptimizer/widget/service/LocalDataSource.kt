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
}
