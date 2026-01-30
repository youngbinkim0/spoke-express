package com.commuteoptimizer.widget.service

import android.content.Context
import com.commuteoptimizer.widget.data.Result
import com.commuteoptimizer.widget.data.api.ApiClientFactory
import com.commuteoptimizer.widget.data.api.GoogleRoutesService
import com.commuteoptimizer.widget.data.api.MtaAlertsService
import com.commuteoptimizer.widget.data.api.MtaApiService
import com.commuteoptimizer.widget.data.models.*
import com.commuteoptimizer.widget.util.WidgetPreferences
import java.text.SimpleDateFormat
import java.util.*

/**
 * Main calculator that orchestrates API calls and builds commute options.
 * Uses MTA GTFS-realtime API directly (no rate limits).
 * Optionally uses Google Routes API via Cloudflare Worker for full transit routes.
 */
class CommuteCalculator(private val context: Context) {

    private val prefs = WidgetPreferences(context)
    private val weatherApi = ApiClientFactory.weatherApi
    private val localDataSource = LocalDataSource(context)

    suspend fun calculateCommute(): Result<CommuteResponse> {
        try {
            val homeLat = prefs.getHomeLat()
            val homeLng = prefs.getHomeLng()
            val workLat = prefs.getWorkLat()
            val workLng = prefs.getWorkLng()
            val selectedStations = prefs.getSelectedStations()
            val apiKey = prefs.getOpenWeatherApiKey()
            val googleApiKey = prefs.getGoogleApiKey()
            val workerUrl = prefs.getWorkerUrl()
            val showBikeOptions = prefs.getShowBikeOptions()

            if (homeLat == 0.0 || workLat == 0.0 || selectedStations.isEmpty()) {
                return Result.Error("Please configure home, work, and stations in settings")
            }

            val weather = fetchWeather(homeLat, homeLng, apiKey)
            val stations = localDataSource.getStations()
            val stationMap = stations.associateBy { it.id }

            val destStation = findClosestStation(workLat, workLng, stations)
                ?: return Result.Error("No station found near work location")

            val options = mutableListOf<CommuteOption>()

            // Add walk-only option if work is close
            val homeToWorkDist = DistanceCalculator.haversineDistance(homeLat, homeLng, workLat, workLng)
            if (homeToWorkDist < 2.0) {
                options.add(buildWalkOnlyOption(homeLat, homeLng, workLat, workLng))
            }

            for ((index, stationId) in selectedStations.withIndex()) {
                val station = stationMap[stationId] ?: continue

                try {
                    if (showBikeOptions) {
                        buildBikeToTransitOption(
                            station, homeLat, homeLng, destStation, workLat, workLng,
                            googleApiKey, workerUrl, index
                        )?.let { options.add(it) }
                    }

                    val walkTime = DistanceCalculator.estimateWalkTime(
                        homeLat, homeLng, station.lat, station.lng
                    )
                    if (walkTime <= 20) {
                        buildTransitOnlyOption(
                            station, homeLat, homeLng, destStation, workLat, workLng,
                            googleApiKey, workerUrl, walkTime, index + 100
                        )?.let { options.add(it) }
                    }
                } catch (e: Exception) {
                    continue
                }
            }

            if (options.isEmpty()) {
                return Result.Error("No commute options available")
            }

            val ranked = RankingService.rankOptions(options, weather)

            val routeIds = selectedStations.flatMap { id ->
                stationMap[id]?.lines ?: emptyList()
            }.distinct()
            val alerts = fetchAlerts(routeIds)

            val response = CommuteResponse(
                options = ranked.take(3),
                weather = weather,
                alerts = alerts,
                generatedAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                    .apply { timeZone = TimeZone.getTimeZone("UTC") }
                    .format(Date())
            )

            return Result.Success(response)

        } catch (e: Exception) {
            return Result.Error("Failed to calculate commute: ${e.message}", e)
        }
    }

    private fun findClosestStation(lat: Double, lng: Double, stations: List<LocalStation>): LocalStation? {
        return stations.minByOrNull { station ->
            DistanceCalculator.haversineDistance(lat, lng, station.lat, station.lng)
        }
    }

    private suspend fun fetchWeather(lat: Double, lng: Double, apiKey: String?): Weather {
        if (apiKey.isNullOrBlank()) return getDefaultWeather()

        return try {
            val response = weatherApi.getWeather(lat, lng, apiKey = apiKey)
            if (response.isSuccessful && response.body() != null) {
                parseWeatherResponse(response.body()!!)
            } else getDefaultWeather()
        } catch (e: Exception) {
            getDefaultWeather()
        }
    }

    private fun parseWeatherResponse(data: OpenWeatherResponse): Weather {
        val current = data.current
        val weatherId = current.weather.firstOrNull()?.id ?: 800
        val weatherMain = current.weather.firstOrNull()?.main ?: "Clear"

        val precipitationType = when {
            weatherId in 200..599 -> "rain"
            weatherId in 600..610 -> "snow"
            weatherId in 611..699 -> "mix"
            else -> "none"
        }

        val precipProbability = data.hourly?.firstOrNull()?.pop ?: 0.0
        val isBad = precipitationType != "none" || precipProbability > 0.5

        return Weather(
            tempF = current.temp.toInt(),
            conditions = weatherMain,
            precipitationType = precipitationType,
            precipitationProbability = (precipProbability * 100).toInt(),
            isBad = isBad
        )
    }

    private fun getDefaultWeather() = Weather(65, "Unknown", "none", 0, false)

    private suspend fun fetchAlerts(routeIds: List<String>): List<Alert> {
        return try {
            MtaAlertsService.fetchAlerts(routeIds)
                .filter { it.effect in listOf("NO_SERVICE", "REDUCED_SERVICE", "SIGNIFICANT_DELAYS") }
                .take(3)
                .map { Alert(it.routeIds, it.effect, it.headerText) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun buildWalkOnlyOption(homeLat: Double, homeLng: Double, workLat: Double, workLng: Double): CommuteOption {
        val walkTime = DistanceCalculator.estimateWalkTime(homeLat, homeLng, workLat, workLng)
        val arrivalTime = Calendar.getInstance().apply { add(Calendar.MINUTE, walkTime) }
        val arrivalTimeStr = SimpleDateFormat("h:mm a", Locale.US).format(arrivalTime.time)

        return CommuteOption(
            id = "walk_only", rank = 0, type = "walk_only",
            durationMinutes = walkTime, summary = "Walk to Work",
            legs = listOf(Leg(mode = "walk", duration = walkTime, to = "Work", route = null)),
            nextTrain = "N/A", arrivalTime = arrivalTimeStr,
            station = Station("", "Work", "", emptyList(), workLat, workLng, "")
        )
    }

    private suspend fun buildBikeToTransitOption(
        station: LocalStation, homeLat: Double, homeLng: Double,
        destStation: LocalStation, workLat: Double, workLng: Double,
        googleApiKey: String?, workerUrl: String?, index: Int
    ): CommuteOption? {
        val bikeTime = DistanceCalculator.estimateBikeTime(homeLat, homeLng, station.lat, station.lng)
        val (nextTrainText, _, routeId) = getNextArrival(station.id, station.lines)
        val (transitTime, transitLegs) = getTransitRoute(station, destStation, workLat, workLng, googleApiKey, workerUrl)

        val totalDuration = bikeTime + transitTime
        val legs = mutableListOf(Leg(mode = "bike", duration = bikeTime, to = station.name, route = null))
        legs.addAll(transitLegs)

        val summary = buildSummary("Bike", transitLegs, routeId ?: station.lines.firstOrNull())
        val arrivalTime = Calendar.getInstance().apply { add(Calendar.MINUTE, totalDuration) }
        val arrivalTimeStr = SimpleDateFormat("h:mm a", Locale.US).format(arrivalTime.time)

        return CommuteOption(
            id = "bike_$index", rank = 0, type = "bike_to_transit",
            durationMinutes = totalDuration, summary = summary, legs = legs,
            nextTrain = nextTrainText, arrivalTime = arrivalTimeStr,
            station = Station(station.id, station.name, station.id, station.lines, station.lat, station.lng, station.borough)
        )
    }

    private suspend fun buildTransitOnlyOption(
        station: LocalStation, homeLat: Double, homeLng: Double,
        destStation: LocalStation, workLat: Double, workLng: Double,
        googleApiKey: String?, workerUrl: String?, walkTime: Int, index: Int
    ): CommuteOption? {
        val (nextTrainText, _, routeId) = getNextArrival(station.id, station.lines)
        val (transitTime, transitLegs) = getTransitRoute(station, destStation, workLat, workLng, googleApiKey, workerUrl)

        val totalDuration = walkTime + transitTime
        val legs = mutableListOf(Leg(mode = "walk", duration = walkTime, to = station.name, route = null))
        legs.addAll(transitLegs)

        val summary = buildSummary("Walk", transitLegs, routeId ?: station.lines.firstOrNull())
        val arrivalTime = Calendar.getInstance().apply { add(Calendar.MINUTE, totalDuration) }
        val arrivalTimeStr = SimpleDateFormat("h:mm a", Locale.US).format(arrivalTime.time)

        return CommuteOption(
            id = "transit_$index", rank = 0, type = "transit_only",
            durationMinutes = totalDuration, summary = summary, legs = legs,
            nextTrain = nextTrainText, arrivalTime = arrivalTimeStr,
            station = Station(station.id, station.name, station.id, station.lines, station.lat, station.lng, station.borough)
        )
    }

    private suspend fun getTransitRoute(
        fromStation: LocalStation, toStation: LocalStation,
        workLat: Double, workLng: Double,
        googleApiKey: String?, workerUrl: String?
    ): Pair<Int, List<Leg>> {
        if (!googleApiKey.isNullOrBlank() && !workerUrl.isNullOrBlank()) {
            try {
                val result = GoogleRoutesService.getTransitRoute(
                    workerUrl, googleApiKey, fromStation.lat, fromStation.lng, workLat, workLng
                )
                if (result.status == "OK" && result.durationMinutes != null) {
                    val legs = result.transitSteps.map { step ->
                        Leg(mode = "subway", duration = step.duration ?: 0, to = step.arrivalStop ?: "?", route = step.line)
                    }
                    return Pair(result.durationMinutes, legs.ifEmpty {
                        listOf(Leg("subway", result.durationMinutes, toStation.name, fromStation.lines.firstOrNull()))
                    })
                }
            } catch (e: Exception) { }
        }

        val transitTime = DistanceCalculator.estimateTransitTime(fromStation.id, toStation.id)
        return Pair(transitTime, listOf(Leg("subway", transitTime, toStation.name, fromStation.lines.firstOrNull())))
    }

    private fun buildSummary(firstMode: String, transitLegs: List<Leg>, firstLine: String?): String {
        val lines = transitLegs.mapNotNull { it.route }.distinct()
        return if (lines.isNotEmpty()) "$firstMode → ${lines.joinToString(" → ")}"
        else "$firstMode → ${firstLine ?: "?"}"
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
