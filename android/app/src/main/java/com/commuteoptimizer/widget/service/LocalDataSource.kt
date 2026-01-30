package com.commuteoptimizer.widget.service

import android.content.Context
import com.commuteoptimizer.widget.data.models.LocalStation

class LocalDataSource(private val context: Context) {
    private var cachedStations: List<LocalStation>? = null

    fun getStations(): List<LocalStation> {
        cachedStations?.let { return it }
        return try {
            val json = context.assets.open("stations.json").bufferedReader().use { it.readText() }
            val stations = com.google.gson.Gson().fromJson(json, Array<LocalStation>::class.java).toList()
            cachedStations = stations
            stations
        } catch (e: Exception) { emptyList() }
    }

    fun getStation(id: String): LocalStation? = getStations().find { it.id == id }
}
