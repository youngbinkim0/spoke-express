# Android Feature Parity Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Update the Android widget to match all new webapp features including Google Routes API integration, walk/transit options, service alerts, auto-inferred destination, and bike toggle.

**Architecture:** The Android app is already serverless (direct MTA API + OpenWeatherMap calls). We need to add Google Routes API integration via the Cloudflare Worker proxy, add walk-only and transit-only options, integrate service alerts, remove the destination station dropdown (auto-infer from work), and add a bike toggle preference.

**Tech Stack:** Kotlin, Android Widget, Retrofit, OkHttp, Coroutines, SharedPreferences

---

## Summary of Changes

| Feature | Webapp | Android Status |
|---------|--------|----------------|
| Google Routes API via Worker | Done | **Add** |
| Full transit route with transfers | Done | **Add** |
| Walk-only option | Done | **Add** |
| Transit-only option (walk to station) | Done | **Add** |
| MTA Service Alerts | Done | **Add** |
| Auto-infer destination from work | Done | **Add** |
| Bike toggle preference | Done | **Add** |
| Require API config (no fallbacks) | Done | Already done |

---

## Task 1: Add Google Routes API Service

**Files:**
- Create: `app/src/main/java/com/commuteoptimizer/widget/data/api/GoogleRoutesService.kt`
- Modify: `app/src/main/java/com/commuteoptimizer/widget/util/WidgetPreferences.kt`

**Purpose:** Add service to call the Cloudflare Worker proxy for Google Routes API to get full transit routes with transfers.

### Step 1: Add preferences for Google API key and Worker URL

In `WidgetPreferences.kt`, add after line 33:

```kotlin
private const val KEY_GOOGLE_API_KEY = "google_api_key"
private const val KEY_WORKER_URL = "worker_url"
```

Add methods after `setDestStation`:

```kotlin
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
```

### Step 2: Create Google Routes Service

Create `GoogleRoutesService.kt`:

```kotlin
package com.commuteoptimizer.widget.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Service to call the Cloudflare Worker proxy for Google Routes API.
 * Returns full transit routes with all transfer details.
 */
object GoogleRoutesService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    data class TransitStep(
        val line: String,
        val vehicle: String?,
        val departureStop: String?,
        val arrivalStop: String?,
        val numStops: Int?,
        val duration: Int?
    )

    data class RouteResult(
        val status: String,
        val durationMinutes: Int?,
        val distance: String?,
        val transitSteps: List<TransitStep>
    )

    suspend fun getTransitRoute(
        workerUrl: String,
        apiKey: String,
        originLat: Double,
        originLng: Double,
        destLat: Double,
        destLng: Double
    ): RouteResult = withContext(Dispatchers.IO) {
        val url = "$workerUrl/directions" +
            "?origin=$originLat,$originLng" +
            "&destination=$destLat,$destLng" +
            "&mode=transit" +
            "&departure_time=now" +
            "&key=$apiKey"

        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        try {
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext errorResult("No response")

            val json = JSONObject(body)
            val status = json.optString("status", "ERROR")

            if (status != "OK") {
                return@withContext RouteResult(status, null, null, emptyList())
            }

            val durationMinutes = json.optInt("durationMinutes", 0)
            val distance = json.optString("distance", null)

            val stepsArray = json.optJSONArray("transitSteps") ?: return@withContext RouteResult(
                status, durationMinutes, distance, emptyList()
            )

            val transitSteps = (0 until stepsArray.length()).map { i ->
                val step = stepsArray.getJSONObject(i)
                TransitStep(
                    line = cleanLineName(step.optString("line", "?")),
                    vehicle = step.optString("vehicle", null),
                    departureStop = step.optString("departureStop", null),
                    arrivalStop = step.optString("arrivalStop", null),
                    numStops = if (step.has("numStops")) step.optInt("numStops") else null,
                    duration = if (step.has("duration")) step.optInt("duration") else null
                )
            }

            RouteResult(status, durationMinutes, distance, transitSteps)
        } catch (e: Exception) {
            errorResult(e.message ?: "Unknown error")
        }
    }

    private fun cleanLineName(name: String): String {
        return name
            .replace(" Line", "")
            .replace(" Train", "")
            .replace("Exp", "")
            .trim()
    }

    private fun errorResult(error: String) = RouteResult("ERROR", null, null, emptyList())
}
```

### Verification

```bash
cd android && ./gradlew compileDebugKotlin
```

Expected: BUILD SUCCESSFUL

---

## Task 2: Add MTA Service Alerts

**Files:**
- Create: `app/src/main/java/com/commuteoptimizer/widget/data/api/MtaAlertsService.kt`
- Modify: `app/src/main/java/com/commuteoptimizer/widget/data/models/CommuteModels.kt`

**Purpose:** Fetch MTA service alerts from GTFS-alerts feed and display relevant disruptions.

### Step 1: Update Alert model

In `CommuteModels.kt`, update the Alert class:

```kotlin
data class Alert(
    val routeIds: List<String>,
    val effect: String,
    val headerText: String
)
```

### Step 2: Create MTA Alerts Service

Create `MtaAlertsService.kt`:

```kotlin
package com.commuteoptimizer.widget.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.util.concurrent.TimeUnit

/**
 * Service to fetch MTA service alerts from GTFS-realtime alerts feed.
 * Parses protobuf manually to avoid build complexity.
 */
object MtaAlertsService {

    private const val ALERTS_URL = "https://api-endpoint.mta.info/Dataservice/mtagtfsfeeds/camsys%2Fsubway-alerts"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    data class ServiceAlert(
        val routeIds: List<String>,
        val effect: String,
        val headerText: String
    )

    private val ALERT_EFFECTS = mapOf(
        1 to "NO_SERVICE",
        2 to "REDUCED_SERVICE",
        3 to "SIGNIFICANT_DELAYS",
        4 to "DETOUR",
        5 to "ADDITIONAL_SERVICE",
        6 to "MODIFIED_SERVICE"
    )

    suspend fun fetchAlerts(routeIds: List<String> = emptyList()): List<ServiceAlert> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(ALERTS_URL)
                .get()
                .build()

            val response = client.newCall(request).execute()
            val bytes = response.body?.bytes() ?: return@withContext emptyList()

            val alerts = parseAlertsFeed(bytes)

            // Filter by route IDs if provided
            if (routeIds.isEmpty()) {
                alerts
            } else {
                alerts.filter { alert ->
                    alert.routeIds.any { it in routeIds }
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseAlertsFeed(bytes: ByteArray): List<ServiceAlert> {
        val alerts = mutableListOf<ServiceAlert>()
        val input = DataInputStream(ByteArrayInputStream(bytes))

        try {
            while (input.available() > 0) {
                val tag = readVarint(input)
                val fieldNum = tag shr 3
                val wireType = tag and 0x7

                when (wireType) {
                    0 -> readVarint(input) // varint
                    1 -> input.skipBytes(8) // 64-bit
                    2 -> { // length-delimited
                        val len = readVarint(input)
                        if (fieldNum == 1) { // entity
                            val entityBytes = ByteArray(len)
                            input.readFully(entityBytes)
                            parseEntity(entityBytes)?.let { alerts.add(it) }
                        } else {
                            input.skipBytes(len)
                        }
                    }
                    5 -> input.skipBytes(4) // 32-bit
                    else -> break
                }
            }
        } catch (e: Exception) {
            // Parsing complete or error
        }

        return alerts
    }

    private fun parseEntity(bytes: ByteArray): ServiceAlert? {
        val input = DataInputStream(ByteArrayInputStream(bytes))
        var routeIds = mutableListOf<String>()
        var effect = "UNKNOWN"
        var headerText = ""

        try {
            while (input.available() > 0) {
                val tag = readVarint(input)
                val fieldNum = tag shr 3
                val wireType = tag and 0x7

                when (wireType) {
                    0 -> readVarint(input)
                    1 -> input.skipBytes(8)
                    2 -> {
                        val len = readVarint(input)
                        if (fieldNum == 2) { // alert
                            val alertBytes = ByteArray(len)
                            input.readFully(alertBytes)
                            val result = parseAlert(alertBytes)
                            routeIds = result.first.toMutableList()
                            effect = result.second
                            headerText = result.third
                        } else {
                            input.skipBytes(len)
                        }
                    }
                    5 -> input.skipBytes(4)
                    else -> break
                }
            }
        } catch (e: Exception) {
            // Done
        }

        return if (routeIds.isNotEmpty() && headerText.isNotEmpty()) {
            ServiceAlert(routeIds, effect, headerText)
        } else null
    }

    private fun parseAlert(bytes: ByteArray): Triple<List<String>, String, String> {
        val input = DataInputStream(ByteArrayInputStream(bytes))
        val routeIds = mutableListOf<String>()
        var effect = "UNKNOWN"
        var headerText = ""

        try {
            while (input.available() > 0) {
                val tag = readVarint(input)
                val fieldNum = tag shr 3
                val wireType = tag and 0x7

                when (wireType) {
                    0 -> {
                        val value = readVarint(input)
                        if (fieldNum == 6) { // effect
                            effect = ALERT_EFFECTS[value] ?: "UNKNOWN"
                        }
                    }
                    1 -> input.skipBytes(8)
                    2 -> {
                        val len = readVarint(input)
                        val data = ByteArray(len)
                        input.readFully(data)

                        when (fieldNum) {
                            5 -> { // informed_entity
                                parseInformedEntity(data)?.let { routeIds.add(it) }
                            }
                            10 -> { // header_text
                                headerText = parseTranslatedString(data)
                            }
                        }
                    }
                    5 -> input.skipBytes(4)
                    else -> break
                }
            }
        } catch (e: Exception) {
            // Done
        }

        return Triple(routeIds.distinct(), effect, headerText)
    }

    private fun parseInformedEntity(bytes: ByteArray): String? {
        val input = DataInputStream(ByteArrayInputStream(bytes))

        try {
            while (input.available() > 0) {
                val tag = readVarint(input)
                val fieldNum = tag shr 3
                val wireType = tag and 0x7

                when (wireType) {
                    0 -> readVarint(input)
                    2 -> {
                        val len = readVarint(input)
                        val data = ByteArray(len)
                        input.readFully(data)
                        if (fieldNum == 3) { // route_id
                            return String(data)
                        }
                    }
                    else -> break
                }
            }
        } catch (e: Exception) {
            // Done
        }

        return null
    }

    private fun parseTranslatedString(bytes: ByteArray): String {
        val input = DataInputStream(ByteArrayInputStream(bytes))

        try {
            while (input.available() > 0) {
                val tag = readVarint(input)
                val fieldNum = tag shr 3
                val wireType = tag and 0x7

                if (wireType == 2) {
                    val len = readVarint(input)
                    val data = ByteArray(len)
                    input.readFully(data)

                    if (fieldNum == 1) { // translation
                        // Parse translation to get text
                        return parseTranslation(data)
                    }
                } else {
                    break
                }
            }
        } catch (e: Exception) {
            // Done
        }

        return ""
    }

    private fun parseTranslation(bytes: ByteArray): String {
        val input = DataInputStream(ByteArrayInputStream(bytes))

        try {
            while (input.available() > 0) {
                val tag = readVarint(input)
                val fieldNum = tag shr 3
                val wireType = tag and 0x7

                if (wireType == 2) {
                    val len = readVarint(input)
                    val data = ByteArray(len)
                    input.readFully(data)

                    if (fieldNum == 1) { // text
                        return String(data)
                    }
                } else if (wireType == 0) {
                    readVarint(input)
                } else {
                    break
                }
            }
        } catch (e: Exception) {
            // Done
        }

        return ""
    }

    private fun readVarint(input: DataInputStream): Int {
        var result = 0
        var shift = 0
        while (true) {
            val b = input.readByte().toInt() and 0xFF
            result = result or ((b and 0x7F) shl shift)
            if ((b and 0x80) == 0) break
            shift += 7
        }
        return result
    }
}
```

### Verification

```bash
cd android && ./gradlew compileDebugKotlin
```

Expected: BUILD SUCCESSFUL

---

## Task 3: Update CommuteCalculator for New Features

**Files:**
- Modify: `app/src/main/java/com/commuteoptimizer/widget/service/CommuteCalculator.kt`

**Purpose:** Add walk-only options, transit-only options, service alerts, and Google Routes integration.

### Step 1: Update imports and add new methods

Replace the entire `CommuteCalculator.kt`:

```kotlin
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
            // Get user settings
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

            // 1. Fetch weather
            val weather = fetchWeather(homeLat, homeLng, apiKey)

            // 2. Load station data
            val stations = localDataSource.getStations()
            val stationMap = stations.associateBy { it.id }

            // 3. Find destination station (closest to work)
            val destStation = findClosestStation(workLat, workLng, stations)
                ?: return Result.Error("No station found near work location")

            // 4. Build commute options
            val options = mutableListOf<CommuteOption>()

            // Add walk-only option if work is close
            val homeToWorkDist = DistanceCalculator.haversineDistance(homeLat, homeLng, workLat, workLng)
            if (homeToWorkDist < 2.0) {
                val walkOption = buildWalkOnlyOption(homeLat, homeLng, workLat, workLng)
                options.add(walkOption)
            }

            // Build options for each selected station
            for ((index, stationId) in selectedStations.withIndex()) {
                val station = stationMap[stationId] ?: continue

                try {
                    // Bike to transit option
                    if (showBikeOptions) {
                        val bikeOption = buildBikeToTransitOption(
                            station = station,
                            homeLat = homeLat,
                            homeLng = homeLng,
                            destStation = destStation,
                            workLat = workLat,
                            workLng = workLng,
                            googleApiKey = googleApiKey,
                            workerUrl = workerUrl,
                            index = index
                        )
                        if (bikeOption != null) {
                            options.add(bikeOption)
                        }
                    }

                    // Walk to transit option
                    val walkTime = DistanceCalculator.estimateWalkTime(
                        homeLat, homeLng, station.lat, station.lng
                    )
                    if (walkTime <= 20) {
                        val transitOption = buildTransitOnlyOption(
                            station = station,
                            homeLat = homeLat,
                            homeLng = homeLng,
                            destStation = destStation,
                            workLat = workLat,
                            workLng = workLng,
                            googleApiKey = googleApiKey,
                            workerUrl = workerUrl,
                            walkTime = walkTime,
                            index = index + 100
                        )
                        if (transitOption != null) {
                            options.add(transitOption)
                        }
                    }
                } catch (e: Exception) {
                    continue
                }
            }

            if (options.isEmpty()) {
                return Result.Error("No commute options available")
            }

            // 5. Rank options
            val ranked = RankingService.rankOptions(options, weather)

            // 6. Fetch service alerts
            val routeIds = selectedStations.flatMap { id ->
                stationMap[id]?.lines ?: emptyList()
            }.distinct()
            val alerts = fetchAlerts(routeIds)

            // 7. Build response
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

    private fun getDefaultWeather(): Weather {
        return Weather(
            tempF = 65,
            conditions = "Unknown",
            precipitationType = "none",
            precipitationProbability = 0,
            isBad = false
        )
    }

    private suspend fun fetchAlerts(routeIds: List<String>): List<Alert> {
        return try {
            val alerts = MtaAlertsService.fetchAlerts(routeIds)
            alerts
                .filter { it.effect in listOf("NO_SERVICE", "REDUCED_SERVICE", "SIGNIFICANT_DELAYS") }
                .take(3)
                .map { Alert(it.routeIds, it.effect, it.headerText) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun buildWalkOnlyOption(
        homeLat: Double,
        homeLng: Double,
        workLat: Double,
        workLng: Double
    ): CommuteOption {
        val walkTime = DistanceCalculator.estimateWalkTime(homeLat, homeLng, workLat, workLng)
        val arrivalTime = Calendar.getInstance().apply {
            add(Calendar.MINUTE, walkTime)
        }
        val arrivalTimeStr = SimpleDateFormat("h:mm a", Locale.US).format(arrivalTime.time)

        return CommuteOption(
            id = "walk_only",
            rank = 0,
            type = "walk_only",
            durationMinutes = walkTime,
            summary = "Walk to Work",
            legs = listOf(
                Leg(mode = "walk", duration = walkTime, to = "Work", route = null)
            ),
            nextTrain = "N/A",
            arrivalTime = arrivalTimeStr,
            station = Station("", "Work", "", emptyList(), workLat, workLng, "")
        )
    }

    private suspend fun buildBikeToTransitOption(
        station: LocalStation,
        homeLat: Double,
        homeLng: Double,
        destStation: LocalStation,
        workLat: Double,
        workLng: Double,
        googleApiKey: String?,
        workerUrl: String?,
        index: Int
    ): CommuteOption? {
        val bikeTime = DistanceCalculator.estimateBikeTime(
            homeLat, homeLng, station.lat, station.lng
        )

        val (nextTrainText, _, routeId) = getNextArrival(station.id, station.lines)

        // Get transit route
        val (transitTime, transitLegs) = getTransitRoute(
            station, destStation, workLat, workLng, googleApiKey, workerUrl
        )

        val totalDuration = bikeTime + transitTime

        val legs = mutableListOf(
            Leg(mode = "bike", duration = bikeTime, to = station.name, route = null)
        )
        legs.addAll(transitLegs)

        val summary = buildSummary("Bike", transitLegs, routeId ?: station.lines.firstOrNull())

        val arrivalTime = Calendar.getInstance().apply {
            add(Calendar.MINUTE, totalDuration)
        }
        val arrivalTimeStr = SimpleDateFormat("h:mm a", Locale.US).format(arrivalTime.time)

        return CommuteOption(
            id = "bike_$index",
            rank = 0,
            type = "bike_to_transit",
            durationMinutes = totalDuration,
            summary = summary,
            legs = legs,
            nextTrain = nextTrainText,
            arrivalTime = arrivalTimeStr,
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

    private suspend fun buildTransitOnlyOption(
        station: LocalStation,
        homeLat: Double,
        homeLng: Double,
        destStation: LocalStation,
        workLat: Double,
        workLng: Double,
        googleApiKey: String?,
        workerUrl: String?,
        walkTime: Int,
        index: Int
    ): CommuteOption? {
        val (nextTrainText, _, routeId) = getNextArrival(station.id, station.lines)

        val (transitTime, transitLegs) = getTransitRoute(
            station, destStation, workLat, workLng, googleApiKey, workerUrl
        )

        val totalDuration = walkTime + transitTime

        val legs = mutableListOf(
            Leg(mode = "walk", duration = walkTime, to = station.name, route = null)
        )
        legs.addAll(transitLegs)

        val summary = buildSummary("Walk", transitLegs, routeId ?: station.lines.firstOrNull())

        val arrivalTime = Calendar.getInstance().apply {
            add(Calendar.MINUTE, totalDuration)
        }
        val arrivalTimeStr = SimpleDateFormat("h:mm a", Locale.US).format(arrivalTime.time)

        return CommuteOption(
            id = "transit_$index",
            rank = 0,
            type = "transit_only",
            durationMinutes = totalDuration,
            summary = summary,
            legs = legs,
            nextTrain = nextTrainText,
            arrivalTime = arrivalTimeStr,
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

    private suspend fun getTransitRoute(
        fromStation: LocalStation,
        toStation: LocalStation,
        workLat: Double,
        workLng: Double,
        googleApiKey: String?,
        workerUrl: String?
    ): Pair<Int, List<Leg>> {
        // Try Google Routes API if configured
        if (!googleApiKey.isNullOrBlank() && !workerUrl.isNullOrBlank()) {
            try {
                val result = GoogleRoutesService.getTransitRoute(
                    workerUrl, googleApiKey,
                    fromStation.lat, fromStation.lng,
                    workLat, workLng
                )

                if (result.status == "OK" && result.durationMinutes != null) {
                    val legs = result.transitSteps.map { step ->
                        Leg(
                            mode = "subway",
                            duration = step.duration ?: 0,
                            to = step.arrivalStop ?: "?",
                            route = step.line
                        )
                    }
                    return Pair(result.durationMinutes, legs.ifEmpty {
                        listOf(Leg("subway", result.durationMinutes, toStation.name, fromStation.lines.firstOrNull()))
                    })
                }
            } catch (e: Exception) {
                // Fall through to estimate
            }
        }

        // Fallback to estimate
        val transitTime = DistanceCalculator.estimateTransitTime(fromStation.id, toStation.id)
        val leg = Leg(
            mode = "subway",
            duration = transitTime,
            to = toStation.name,
            route = fromStation.lines.firstOrNull()
        )
        return Pair(transitTime, listOf(leg))
    }

    private fun buildSummary(firstMode: String, transitLegs: List<Leg>, firstLine: String?): String {
        val lines = transitLegs.mapNotNull { it.route }.distinct()
        return if (lines.isNotEmpty()) {
            "$firstMode → ${lines.joinToString(" → ")}"
        } else {
            "$firstMode → ${firstLine ?: "?"}"
        }
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
```

### Verification

```bash
cd android && ./gradlew compileDebugKotlin
```

Expected: BUILD SUCCESSFUL

---

## Task 4: Update WidgetPreferences for Bike Toggle

**Files:**
- Modify: `app/src/main/java/com/commuteoptimizer/widget/util/WidgetPreferences.kt`

**Purpose:** Add bike toggle preference (show/hide bike options).

### Step 1: Add bike toggle preference

Add after line 33 (with other KEY constants):

```kotlin
private const val KEY_SHOW_BIKE_OPTIONS = "show_bike_options"
```

Add methods after `setWorkerUrl`:

```kotlin
// ========== Bike Toggle ==========

fun getShowBikeOptions(): Boolean {
    return prefs.getBoolean(KEY_SHOW_BIKE_OPTIONS, true)
}

fun setShowBikeOptions(show: Boolean) {
    prefs.edit().putBoolean(KEY_SHOW_BIKE_OPTIONS, show).apply()
}
```

### Verification

```bash
cd android && ./gradlew compileDebugKotlin
```

Expected: BUILD SUCCESSFUL

---

## Task 5: Update Config Activity UI

**Files:**
- Modify: `app/src/main/res/layout/activity_config.xml`
- Modify: `app/src/main/java/com/commuteoptimizer/widget/CommuteWidgetConfigActivity.kt`

**Purpose:** Add Google API key, Worker URL, bike toggle; remove destination station dropdown.

### Step 1: Update layout XML

The layout should include:
- OpenWeatherMap API Key (existing)
- Google API Key (new)
- Worker URL (new)
- Home Address (existing)
- Work Address (existing)
- Station selection chips (existing)
- Bike toggle switch (new)
- Remove: Destination Station spinner

### Step 2: Update CommuteWidgetConfigActivity.kt

Replace the config activity with updated version that:
- Adds inputGoogleApiKey and inputWorkerUrl fields
- Adds switchBikeToggle
- Removes spinnerDestStation and related code
- Saves the new preferences

See full implementation in parallel task execution.

### Verification

```bash
cd android && ./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL

---

## Task 6: Build and Test

**Purpose:** Build the APK and verify all features work.

### Step 1: Build debug APK

```bash
cd /home/youngbin/GitHub/commute-optimizer/android && ./gradlew assembleDebug
```

### Step 2: Verify APK exists

```bash
ls -la app/build/outputs/apk/debug/app-debug.apk
```

### Step 3: Commit changes

```bash
git add -A
git commit -m "Add Android feature parity with webapp

- Add Google Routes API integration via Cloudflare Worker
- Add walk-only and transit-only commute options
- Add MTA service alerts display
- Auto-infer destination station from work location
- Add bike toggle preference to show/hide bike options
- Update config UI with new settings fields
- Remove destination station dropdown

Co-Authored-By: Claude Opus 4.5 <noreply@anthropic.com>"
```

---

## Parallel Execution Strategy

These tasks can be parallelized as follows:

| Group | Tasks | Dependencies |
|-------|-------|--------------|
| A | Task 1 (Google Routes Service) | None |
| B | Task 2 (MTA Alerts Service) | None |
| C | Task 4 (Bike Toggle Prefs) | None |
| D | Task 3 (CommuteCalculator) | Tasks 1, 2, 4 |
| E | Task 5 (Config UI) | Task 4 |
| F | Task 6 (Build & Test) | All above |

**Recommended parallel groups:**
1. **First wave:** Tasks 1, 2, 4 (all independent)
2. **Second wave:** Tasks 3, 5 (depend on first wave)
3. **Final:** Task 6 (build and commit)
