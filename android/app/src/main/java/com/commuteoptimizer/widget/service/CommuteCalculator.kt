package com.commuteoptimizer.widget.service

import android.content.Context
import com.commuteoptimizer.widget.data.Result
import com.commuteoptimizer.widget.data.api.ApiClientFactory
import com.commuteoptimizer.widget.data.api.MtaApiService
import com.commuteoptimizer.widget.data.models.*
import com.commuteoptimizer.widget.util.WidgetPreferences
import java.text.SimpleDateFormat
import java.util.*

/**
 * Main calculator that orchestrates API calls and builds commute options.
 * This replaces the need for a backend server.
 * Uses MTA GTFS-realtime API directly (no rate limits).
 */
class CommuteCalculator(private val context: Context) {

    private val prefs = WidgetPreferences(context)
    private val weatherApi = ApiClientFactory.weatherApi
    private val localDataSource = LocalDataSource(context)

    suspend fun calculateCommute(): Result<CommuteResponse> {
        try {
            // Get user settings
            val homeLat = prefs.getHomeLat()
            val homeLng = prefs.getHomeLng()
            val workLat = prefs.getWorkLat()
            val workLng = prefs.getWorkLng()
            val selectedStations = prefs.getSelectedStations()
            val destStationId = prefs.getDestStation()
            val apiKey = prefs.getOpenWeatherApiKey()

            if (homeLat == 0.0 || workLat == 0.0 || selectedStations.isEmpty()) {
                return Result.Error("Please configure home, work, and stations in settings")
            }

            // 1. Fetch weather
            val weather = fetchWeather(homeLat, homeLng, apiKey)

            // 2. Load station data
            val stations = localDataSource.getStations()
            val stationMap = stations.associateBy { it.id }

            // 3. Build commute options for each selected station
            val options = mutableListOf<CommuteOption>()

            for ((index, stationId) in selectedStations.withIndex()) {
                val station = stationMap[stationId] ?: continue

                try {
                    val option = buildCommuteOption(
                        stationId = stationId,
                        station = station,
                        homeLat = homeLat,
                        homeLng = homeLng,
                        destStationId = destStationId,
                        index = index
                    )
                    if (option != null) {
                        options.add(option)
                    }
                } catch (e: Exception) {
                    // Skip this station on error, continue with others
                    continue
                }
            }

            if (options.isEmpty()) {
                return Result.Error("No commute options available")
            }

            // 4. Rank options
            val ranked = RankingService.rankOptions(options, weather)

            // 5. Build response
            val response = CommuteResponse(
                options = ranked,
                weather = weather,
                alerts = emptyList(),
                generatedAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                    .apply { timeZone = TimeZone.getTimeZone("UTC") }
                    .format(Date())
            )

            return Result.Success(response)

        } catch (e: Exception) {
            return Result.Error("Failed to calculate commute: ${e.message}", e)
        }
    }

    private suspend fun fetchWeather(lat: Double, lng: Double, apiKey: String?): Weather {
        if (apiKey.isNullOrBlank()) {
            return getDefaultWeather()
        }

        return try {
            val response = weatherApi.getWeather(lat, lng, apiKey = apiKey)
            if (response.isSuccessful && response.body() != null) {
                parseWeatherResponse(response.body()!!)
            } else {
                getDefaultWeather()
            }
        } catch (e: Exception) {
            getDefaultWeather()
        }
    }

    private fun parseWeatherResponse(data: OpenWeatherResponse): Weather {
        val current = data.current
        val weatherId = current.weather.firstOrNull()?.id ?: 800
        val weatherMain = current.weather.firstOrNull()?.main ?: "Clear"

        // Determine precipitation type based on weather condition codes
        val precipitationType = when {
            weatherId in 200..599 -> "rain"
            weatherId in 600..610 -> "snow"
            weatherId in 611..699 -> "mix"
            else -> "none"
        }

        // Get precipitation probability from next hour
        val precipProbability = data.hourly?.firstOrNull()?.pop ?: 0.0

        // Determine if weather is bad for biking
        val isBad = precipitationType != "none" || precipProbability > 0.5

        return Weather(
            tempF = current.temp.toInt(),
            conditions = weatherMain,
            precipitationType = precipitationType,
            precipitationProbability = (precipProbability * 100).toInt(),
            isBad = isBad
        )
    }

    private fun getDefaultWeather(): Weather {
        return Weather(
            tempF = 65,
            conditions = "Unknown",
            precipitationType = "none",
            precipitationProbability = 0,
            isBad = false
        )
    }

    private suspend fun buildCommuteOption(
        stationId: String,
        station: LocalStation,
        homeLat: Double,
        homeLng: Double,
        destStationId: String,
        index: Int
    ): CommuteOption? {
        // Calculate bike time to station
        val bikeTime = DistanceCalculator.estimateBikeTime(
            homeLat, homeLng,
            station.lat, station.lng
        )

        // Get next train arrival from MTA API
        val (nextTrainText, arrivalTime, routeId) = getNextArrival(stationId, station.lines)

        // Estimate transit time to destination
        val transitTime = DistanceCalculator.estimateTransitTime(stationId, destStationId)

        // Total duration
        val totalDuration = bikeTime + transitTime

        // Build legs
        val legs = listOf(
            Leg(
                mode = "bike",
                duration = bikeTime,
                to = station.name,
                route = null
            ),
            Leg(
                mode = "subway",
                duration = transitTime,
                to = "Work",
                route = routeId ?: station.lines.firstOrNull()
            )
        )

        // Build summary (e.g., "Bike → G")
        val summary = "Bike → ${routeId ?: station.lines.firstOrNull() ?: "?"}"

        return CommuteOption(
            id = "option_$index",
            rank = 0, // Will be set by RankingService
            type = "bike_to_transit",
            durationMinutes = totalDuration,
            summary = summary,
            legs = legs,
            nextTrain = nextTrainText,
            arrivalTime = arrivalTime,
            station = Station(
                id = station.id,
                name = station.name,
                transiterId = station.id,
                lines = station.lines,
                lat = station.lat,
                lng = station.lng,
                borough = station.borough
            )
        )
    }

    private suspend fun getNextArrival(stationId: String, lines: List<String>): Triple<String, String, String?> {
        return try {
            val result = MtaApiService.getNextArrival(stationId, lines)
            Triple(result.nextTrain, result.arrivalTime, result.routeId)
        } catch (e: Exception) {
            Triple("--", "--", null)
        }
    }
}

/**
 * Local data source for loading bundled station data.
 */
class LocalDataSource(private val context: Context) {

    private var cachedStations: List<LocalStation>? = null

    fun getStations(): List<LocalStation> {
        cachedStations?.let { return it }

        return try {
            val json = context.assets.open("stations.json").bufferedReader().use { it.readText() }
            val stations = com.google.gson.Gson().fromJson(json, Array<LocalStation>::class.java).toList()
            cachedStations = stations
            stations
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getStation(id: String): LocalStation? {
        return getStations().find { it.id == id }
    }
}
