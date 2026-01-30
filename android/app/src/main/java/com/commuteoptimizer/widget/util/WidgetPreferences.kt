package com.commuteoptimizer.widget.util

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class WidgetPreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    private val gson = Gson()

    companion object {
        private const val PREFS_NAME = "commute_widget_prefs"

        // Legacy keys (kept for backwards compatibility)
        private const val KEY_API_URL = "api_url_"
        private const val KEY_LAST_UPDATE = "last_update_"

        // Serverless mode keys (global, not per-widget)
        private const val KEY_OPENWEATHER_API_KEY = "openweather_api_key"
        private const val KEY_HOME_LAT = "home_lat"
        private const val KEY_HOME_LNG = "home_lng"
        private const val KEY_HOME_ADDRESS = "home_address"
        private const val KEY_WORK_LAT = "work_lat"
        private const val KEY_WORK_LNG = "work_lng"
        private const val KEY_WORK_ADDRESS = "work_address"
        private const val KEY_SELECTED_STATIONS = "selected_stations"
        private const val KEY_BIKE_STATIONS = "bike_stations"
        private const val KEY_LIVE_STATIONS = "live_stations"
        private const val KEY_DEST_STATION = "dest_station"
        private const val KEY_SHOW_BIKE_OPTIONS = "show_bike_options"
        private const val KEY_GOOGLE_API_KEY = "google_api_key"
        private const val KEY_WORKER_URL = "worker_url"

        const val DEFAULT_API_URL = "http://192.168.1.100:8888"
    }

    // ========== OpenWeatherMap API Key ==========

    fun getOpenWeatherApiKey(): String? {
        return prefs.getString(KEY_OPENWEATHER_API_KEY, null)
    }

    fun setOpenWeatherApiKey(apiKey: String) {
        prefs.edit().putString(KEY_OPENWEATHER_API_KEY, apiKey).apply()
    }

    // ========== Home Location ==========

    fun getHomeLat(): Double {
        return prefs.getFloat(KEY_HOME_LAT, 0f).toDouble()
    }

    fun getHomeLng(): Double {
        return prefs.getFloat(KEY_HOME_LNG, 0f).toDouble()
    }

    fun getHomeAddress(): String? {
        return prefs.getString(KEY_HOME_ADDRESS, null)
    }

    fun setHomeLocation(lat: Double, lng: Double, address: String) {
        prefs.edit()
            .putFloat(KEY_HOME_LAT, lat.toFloat())
            .putFloat(KEY_HOME_LNG, lng.toFloat())
            .putString(KEY_HOME_ADDRESS, address)
            .apply()
    }

    // ========== Work Location ==========

    fun getWorkLat(): Double {
        return prefs.getFloat(KEY_WORK_LAT, 0f).toDouble()
    }

    fun getWorkLng(): Double {
        return prefs.getFloat(KEY_WORK_LNG, 0f).toDouble()
    }

    fun getWorkAddress(): String? {
        return prefs.getString(KEY_WORK_ADDRESS, null)
    }

    fun setWorkLocation(lat: Double, lng: Double, address: String) {
        prefs.edit()
            .putFloat(KEY_WORK_LAT, lat.toFloat())
            .putFloat(KEY_WORK_LNG, lng.toFloat())
            .putString(KEY_WORK_ADDRESS, address)
            .apply()
    }

    // ========== Bike-to Stations (for commute routing) ==========

    fun getBikeStations(): List<String> {
        // Try new key first, fall back to legacy key for migration
        val json = prefs.getString(KEY_BIKE_STATIONS, null)
            ?: prefs.getString(KEY_SELECTED_STATIONS, null)
            ?: return emptyList()
        return try {
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun setBikeStations(stations: List<String>) {
        val json = gson.toJson(stations)
        prefs.edit().putString(KEY_BIKE_STATIONS, json).apply()
    }

    // ========== Live Train Stations (max 3, for live trains screen) ==========

    fun getLiveStations(): List<String> {
        val json = prefs.getString(KEY_LIVE_STATIONS, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<String>>() {}.type
            val stations: List<String> = gson.fromJson(json, type)
            stations.take(3) // Enforce max 3
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun setLiveStations(stations: List<String>) {
        val json = gson.toJson(stations.take(3)) // Enforce max 3
        prefs.edit().putString(KEY_LIVE_STATIONS, json).apply()
    }

    // ========== Legacy Selected Stations (alias for backward compatibility) ==========

    @Deprecated("Use getBikeStations() instead", ReplaceWith("getBikeStations()"))
    fun getSelectedStations(): List<String> = getBikeStations()

    @Deprecated("Use setBikeStations() instead", ReplaceWith("setBikeStations(stations)"))
    fun setSelectedStations(stations: List<String>) = setBikeStations(stations)

    // ========== Destination Station ==========

    fun getDestStation(): String {
        return prefs.getString(KEY_DEST_STATION, "G22") ?: "G22" // Default to Court Sq
    }

    fun setDestStation(stationId: String) {
        prefs.edit().putString(KEY_DEST_STATION, stationId).apply()
    }

    // ========== Google API Key ==========

    fun getGoogleApiKey(): String? {
        return prefs.getString(KEY_GOOGLE_API_KEY, null)
    }

    fun setGoogleApiKey(apiKey: String) {
        prefs.edit().putString(KEY_GOOGLE_API_KEY, apiKey).apply()
    }

    // ========== Worker URL ==========

    fun getWorkerUrl(): String? {
        return prefs.getString(KEY_WORKER_URL, null)
    }

    fun setWorkerUrl(url: String) {
        prefs.edit().putString(KEY_WORKER_URL, url).apply()
    }

    // ========== Bike Toggle ==========

    fun getShowBikeOptions(): Boolean {
        return prefs.getBoolean(KEY_SHOW_BIKE_OPTIONS, true)
    }

    fun setShowBikeOptions(show: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_BIKE_OPTIONS, show).apply()
    }

    // ========== Widget-specific settings ==========

    fun getLastUpdate(widgetId: Int): Long {
        return prefs.getLong(KEY_LAST_UPDATE + widgetId, 0)
    }

    fun setLastUpdate(widgetId: Int, timestamp: Long) {
        prefs.edit().putLong(KEY_LAST_UPDATE + widgetId, timestamp).apply()
    }

    // ========== Legacy API URL (for backwards compatibility) ==========

    fun getApiUrl(widgetId: Int): String {
        return prefs.getString(KEY_API_URL + widgetId, DEFAULT_API_URL) ?: DEFAULT_API_URL
    }

    fun setApiUrl(widgetId: Int, url: String) {
        prefs.edit().putString(KEY_API_URL + widgetId, url).apply()
    }

    // ========== Cleanup ==========

    fun clearWidgetData(widgetId: Int) {
        prefs.edit()
            .remove(KEY_API_URL + widgetId)
            .remove(KEY_LAST_UPDATE + widgetId)
            .apply()
    }

    fun getAllWidgetIds(): Set<Int> {
        return prefs.all.keys
            .filter { it.startsWith(KEY_API_URL) || it.startsWith(KEY_LAST_UPDATE) }
            .mapNotNull {
                it.removePrefix(KEY_API_URL)
                    .removePrefix(KEY_LAST_UPDATE)
                    .toIntOrNull()
            }
            .toSet()
    }

    // ========== Validation ==========

    fun isConfigured(): Boolean {
        return getHomeLat() != 0.0 &&
               getWorkLat() != 0.0 &&
               getBikeStations().isNotEmpty()
    }
}
