# Serverless Android Widget Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Convert the Android widget to work fully offline without requiring a backend server, by calling external APIs directly from the device.

**Architecture:** The app will call OpenWeatherMap, Google Routes API, and Transiter Demo API directly. User provides API keys in settings. All logic (weather parsing, routing, ranking) is ported to Kotlin. Settings stored in SharedPreferences.

**Tech Stack:** Kotlin, Retrofit, Coroutines, SharedPreferences, WorkManager

---

## Task 1: Add API Key Settings to Preferences

**Files:**
- Modify: `android/app/src/main/java/com/commuteoptimizer/widget/util/WidgetPreferences.kt`

**Step 1: Add API key storage methods**

Add these methods to `WidgetPreferences.kt`:

```kotlin
companion object {
    private const val PREFS_NAME = "commute_widget_prefs"
    private const val KEY_API_URL = "api_url_"
    private const val KEY_LAST_UPDATE = "last_update_"
    private const val KEY_OPENWEATHER_API_KEY = "openweather_api_key"
    private const val KEY_GOOGLE_MAPS_API_KEY = "google_maps_api_key"
    private const val KEY_HOME_LAT = "home_lat"
    private const val KEY_HOME_LNG = "home_lng"
    private const val KEY_HOME_ADDRESS = "home_address"
    private const val KEY_WORK_LAT = "work_lat"
    private const val KEY_WORK_LNG = "work_lng"
    private const val KEY_WORK_ADDRESS = "work_address"
    private const val KEY_MODE = "mode" // "serverless" or "server"

    const val DEFAULT_API_URL = "http://192.168.1.100:8888"
    const val TRANSITER_DEMO_URL = "https://demo.transiter.dev"
}

// Mode
fun getMode(): String = prefs.getString(KEY_MODE, "serverless") ?: "serverless"
fun setMode(mode: String) = prefs.edit().putString(KEY_MODE, mode).apply()

// API Keys
fun getOpenWeatherApiKey(): String = prefs.getString(KEY_OPENWEATHER_API_KEY, "") ?: ""
fun setOpenWeatherApiKey(key: String) = prefs.edit().putString(KEY_OPENWEATHER_API_KEY, key).apply()

fun getGoogleMapsApiKey(): String = prefs.getString(KEY_GOOGLE_MAPS_API_KEY, "") ?: ""
fun setGoogleMapsApiKey(key: String) = prefs.edit().putString(KEY_GOOGLE_MAPS_API_KEY, key).apply()

// Home location
fun getHomeLat(): Double = prefs.getFloat(KEY_HOME_LAT, 0f).toDouble()
fun getHomeLng(): Double = prefs.getFloat(KEY_HOME_LNG, 0f).toDouble()
fun getHomeAddress(): String = prefs.getString(KEY_HOME_ADDRESS, "") ?: ""
fun setHome(lat: Double, lng: Double, address: String) {
    prefs.edit()
        .putFloat(KEY_HOME_LAT, lat.toFloat())
        .putFloat(KEY_HOME_LNG, lng.toFloat())
        .putString(KEY_HOME_ADDRESS, address)
        .apply()
}

// Work location
fun getWorkLat(): Double = prefs.getFloat(KEY_WORK_LAT, 0f).toDouble()
fun getWorkLng(): Double = prefs.getFloat(KEY_WORK_LNG, 0f).toDouble()
fun getWorkAddress(): String = prefs.getString(KEY_WORK_ADDRESS, "") ?: ""
fun setWork(lat: Double, lng: Double, address: String) {
    prefs.edit()
        .putFloat(KEY_WORK_LAT, lat.toFloat())
        .putFloat(KEY_WORK_LNG, lng.toFloat())
        .putString(KEY_WORK_ADDRESS, address)
        .apply()
}

fun hasRequiredSettings(): Boolean {
    return getHomeLat() != 0.0 && getWorkLat() != 0.0 && getOpenWeatherApiKey().isNotEmpty()
}
```

**Step 2: Run build to verify**

Run: `cd android && ./gradlew assembleDebug --no-daemon 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add android/app/src/main/java/com/commuteoptimizer/widget/util/WidgetPreferences.kt
git commit -m "feat(android): add API key and location storage to preferences"
```

---

## Task 2: Create Weather Service

**Files:**
- Create: `android/app/src/main/java/com/commuteoptimizer/widget/data/api/WeatherApiService.kt`
- Create: `android/app/src/main/java/com/commuteoptimizer/widget/data/models/WeatherModels.kt`
- Create: `android/app/src/main/java/com/commuteoptimizer/widget/data/WeatherRepository.kt`

**Step 1: Create weather API models**

Create `WeatherModels.kt`:

```kotlin
package com.commuteoptimizer.widget.data.models

import com.google.gson.annotations.SerializedName

data class OpenWeatherResponse(
    val current: CurrentWeather,
    val hourly: List<HourlyWeather>
)

data class CurrentWeather(
    val temp: Double,
    val weather: List<WeatherCondition>
)

data class WeatherCondition(
    val id: Int,
    val main: String,
    val description: String
)

data class HourlyWeather(
    val pop: Double // Probability of precipitation
)
```

**Step 2: Create weather API service**

Create `WeatherApiService.kt`:

```kotlin
package com.commuteoptimizer.widget.data.api

import com.commuteoptimizer.widget.data.models.OpenWeatherResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface WeatherApiService {
    @GET("data/3.0/onecall")
    suspend fun getWeather(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("units") units: String = "imperial",
        @Query("exclude") exclude: String = "minutely,daily,alerts",
        @Query("appid") apiKey: String
    ): Response<OpenWeatherResponse>
}
```

**Step 3: Create weather repository**

Create `WeatherRepository.kt`:

```kotlin
package com.commuteoptimizer.widget.data

import com.commuteoptimizer.widget.data.api.WeatherApiService
import com.commuteoptimizer.widget.data.models.OpenWeatherResponse
import com.commuteoptimizer.widget.data.models.Weather
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class WeatherRepository(private val apiKey: String) {

    private val apiService: WeatherApiService by lazy {
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        Retrofit.Builder()
            .baseUrl("https://api.openweathermap.org/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(WeatherApiService::class.java)
    }

    suspend fun getWeather(lat: Double, lng: Double): Result<Weather> = withContext(Dispatchers.IO) {
        if (apiKey.isEmpty()) {
            return@withContext Result.Success(getDefaultWeather())
        }

        try {
            val response = apiService.getWeather(lat, lng, apiKey = apiKey)
            if (response.isSuccessful && response.body() != null) {
                Result.Success(parseWeatherResponse(response.body()!!))
            } else {
                Result.Success(getDefaultWeather())
            }
        } catch (e: Exception) {
            Result.Success(getDefaultWeather())
        }
    }

    private fun parseWeatherResponse(data: OpenWeatherResponse): Weather {
        val weatherId = data.current.weather.firstOrNull()?.id ?: 800
        val weatherMain = data.current.weather.firstOrNull()?.main ?: "Clear"

        val precipitationType = when {
            weatherId in 200..599 -> "rain"
            weatherId in 600..609 -> "snow"
            weatherId in 610..619 -> "mix"
            weatherId in 620..699 -> "snow"
            else -> "none"
        }

        val precipProbability = (data.hourly.firstOrNull()?.pop ?: 0.0).toInt()
        val isBad = precipitationType != "none" || precipProbability > 50

        return Weather(
            tempF = data.current.temp.toInt(),
            conditions = weatherMain,
            precipitationType = precipitationType,
            precipitationProbability = precipProbability,
            isBad = isBad
        )
    }

    private fun getDefaultWeather(): Weather = Weather(
        tempF = 65,
        conditions = "Unknown",
        precipitationType = "none",
        precipitationProbability = 0,
        isBad = false
    )
}
```

**Step 4: Run build to verify**

Run: `cd android && ./gradlew assembleDebug --no-daemon 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```bash
git add android/app/src/main/java/com/commuteoptimizer/widget/data/
git commit -m "feat(android): add serverless weather service"
```

---

## Task 3: Create Transit API Service (Transiter Demo)

**Files:**
- Create: `android/app/src/main/java/com/commuteoptimizer/widget/data/api/TransiterApiService.kt`
- Create: `android/app/src/main/java/com/commuteoptimizer/widget/data/models/TransiterModels.kt`
- Create: `android/app/src/main/java/com/commuteoptimizer/widget/data/TransiterRepository.kt`

**Step 1: Create Transiter models**

Create `TransiterModels.kt`:

```kotlin
package com.commuteoptimizer.widget.data.models

import com.google.gson.annotations.SerializedName

data class TransiterStopResponse(
    val id: String,
    val name: String,
    val stopTimes: List<TransiterStopTime>?
)

data class TransiterStopTime(
    val arrival: TransiterArrivalTime?,
    val departure: TransiterArrivalTime?,
    val trip: TransiterTrip?
)

data class TransiterArrivalTime(
    val time: String? // Unix timestamp as string
)

data class TransiterTrip(
    val route: TransiterRoute?,
    val direction: String?,
    val destination: TransiterDestination?
)

data class TransiterRoute(
    val id: String,
    val color: String?
)

data class TransiterDestination(
    val id: String,
    val name: String
)

data class TransiterStopsResponse(
    val stops: List<TransiterStop>,
    val nextId: String?
)

data class TransiterStop(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val type: String,
    val serviceMaps: List<TransiterServiceMap>?
)

data class TransiterServiceMap(
    val configId: String,
    val routes: List<TransiterRoute>?
)
```

**Step 2: Create Transiter API service**

Create `TransiterApiService.kt`:

```kotlin
package com.commuteoptimizer.widget.data.api

import com.commuteoptimizer.widget.data.models.TransiterStopResponse
import com.commuteoptimizer.widget.data.models.TransiterStopsResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface TransiterApiService {
    @GET("systems/us-ny-subway/stops/{stopId}")
    suspend fun getStopArrivals(
        @Path("stopId") stopId: String
    ): Response<TransiterStopResponse>

    @GET("systems/us-ny-subway/stops")
    suspend fun getStops(
        @Query("limit") limit: Int = 100,
        @Query("first_id") firstId: String? = null
    ): Response<TransiterStopsResponse>
}
```

**Step 3: Create Transiter repository**

Create `TransiterRepository.kt`:

```kotlin
package com.commuteoptimizer.widget.data

import com.commuteoptimizer.widget.data.api.TransiterApiService
import com.commuteoptimizer.widget.data.models.Station
import com.commuteoptimizer.widget.data.models.TransiterStopTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class TransiterRepository {

    companion object {
        const val TRANSITER_DEMO_URL = "https://demo.transiter.dev/"
    }

    private val apiService: TransiterApiService by lazy {
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        Retrofit.Builder()
            .baseUrl(TRANSITER_DEMO_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(TransiterApiService::class.java)
    }

    suspend fun getStopArrivals(stopId: String): Result<List<TransiterStopTime>> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getStopArrivals(stopId)
            if (response.isSuccessful && response.body() != null) {
                Result.Success(response.body()!!.stopTimes ?: emptyList())
            } else {
                Result.Error("Failed to get arrivals: ${response.code()}")
            }
        } catch (e: Exception) {
            Result.Error("Network error: ${e.message}", e)
        }
    }

    suspend fun getAllStations(): Result<List<Station>> = withContext(Dispatchers.IO) {
        try {
            val stations = mutableListOf<Station>()
            var nextId: String? = null

            do {
                val response = apiService.getStops(limit = 100, firstId = nextId)
                if (!response.isSuccessful || response.body() == null) break

                val data = response.body()!!
                for (stop in data.stops) {
                    if (stop.type != "STATION") continue

                    val serviceMap = stop.serviceMaps?.find { it.configId == "alltimes" }
                        ?: stop.serviceMaps?.firstOrNull()
                    val lines = serviceMap?.routes?.map { it.id } ?: emptyList()
                    val borough = getBoroughFromCoords(stop.latitude, stop.longitude)

                    stations.add(Station(
                        id = stop.id.lowercase(),
                        name = stop.name,
                        transiterId = stop.id,
                        lines = lines,
                        lat = stop.latitude,
                        lng = stop.longitude,
                        borough = borough
                    ))
                }
                nextId = data.nextId
            } while (nextId != null)

            Result.Success(stations)
        } catch (e: Exception) {
            Result.Error("Failed to get stations: ${e.message}", e)
        }
    }

    fun getNextArrival(arrivals: List<TransiterStopTime>, direction: String? = null): TransiterStopTime? {
        val now = System.currentTimeMillis()

        return arrivals
            .filter { stopTime ->
                val arrivalTime = stopTime.arrival?.time?.toLongOrNull()?.times(1000) ?: return@filter false
                if (arrivalTime < now - 60000) return@filter false
                if (direction != null && stopTime.trip?.direction != direction) return@filter false
                true
            }
            .minByOrNull { it.arrival?.time?.toLongOrNull() ?: Long.MAX_VALUE }
    }

    fun formatArrivalTime(stopTime: TransiterStopTime): String {
        val timestamp = stopTime.arrival?.time?.toLongOrNull()?.times(1000) ?: return "N/A"
        val date = java.util.Date(timestamp)
        val format = java.text.SimpleDateFormat("h:mm a", java.util.Locale.US)
        return format.format(date)
    }

    fun getMinutesUntilArrival(stopTime: TransiterStopTime): Int {
        val timestamp = stopTime.arrival?.time?.toLongOrNull()?.times(1000) ?: return 0
        val now = System.currentTimeMillis()
        return maxOf(0, ((timestamp - now) / 60000).toInt())
    }

    private fun getBoroughFromCoords(lat: Double, lng: Double): String {
        return when {
            lat > 40.7 && lng > -74.02 && lng < -73.93 -> "Manhattan"
            lat < 40.71 && lng > -74.04 && lng < -73.85 -> "Brooklyn"
            lat > 40.7 && lng > -73.96 -> "Queens"
            lat > 40.8 -> "Bronx"
            lng < -74.05 -> "Staten Island"
            else -> "Brooklyn"
        }
    }
}
```

**Step 4: Run build to verify**

Run: `cd android && ./gradlew assembleDebug --no-daemon 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```bash
git add android/app/src/main/java/com/commuteoptimizer/widget/data/
git commit -m "feat(android): add Transiter demo API client"
```

---

## Task 4: Create Google Routes Service

**Files:**
- Create: `android/app/src/main/java/com/commuteoptimizer/widget/data/api/GoogleRoutesApiService.kt`
- Create: `android/app/src/main/java/com/commuteoptimizer/widget/data/models/GoogleRoutesModels.kt`
- Create: `android/app/src/main/java/com/commuteoptimizer/widget/data/RoutingRepository.kt`

**Step 1: Create Google Routes models**

Create `GoogleRoutesModels.kt`:

```kotlin
package com.commuteoptimizer.widget.data.models

import com.google.gson.annotations.SerializedName

// Request models
data class GoogleRoutesRequest(
    val origin: GoogleRouteLocation,
    val destination: GoogleRouteLocation,
    val travelMode: String,
    val transitPreferences: GoogleTransitPreferences? = null
)

data class GoogleRouteLocation(
    val location: GoogleLatLng
)

data class GoogleLatLng(
    val latLng: GoogleCoordinates
)

data class GoogleCoordinates(
    val latitude: Double,
    val longitude: Double
)

data class GoogleTransitPreferences(
    val allowedTravelModes: List<String>,
    val routingPreference: String
)

// Response models
data class GoogleRoutesResponse(
    val routes: List<GoogleRoute>?
)

data class GoogleRoute(
    val duration: String?,
    val distanceMeters: Int?,
    val legs: List<GoogleRouteLeg>?
)

data class GoogleRouteLeg(
    val steps: List<GoogleRouteStep>?
)

data class GoogleRouteStep(
    val staticDuration: String?,
    val travelMode: String?,
    val transitDetails: GoogleTransitDetails?
)

data class GoogleTransitDetails(
    val stopDetails: GoogleStopDetails?,
    val transitLine: GoogleTransitLine?,
    val stopCount: Int?,
    val headsign: String?
)

data class GoogleStopDetails(
    val arrivalStop: GoogleStop?,
    val departureStop: GoogleStop?
)

data class GoogleStop(
    val name: String?
)

data class GoogleTransitLine(
    val name: String?,
    val nameShort: String?,
    val color: String?,
    val vehicle: GoogleVehicle?
)

data class GoogleVehicle(
    val type: String?
)
```

**Step 2: Create Google Routes API service**

Create `GoogleRoutesApiService.kt`:

```kotlin
package com.commuteoptimizer.widget.data.api

import com.commuteoptimizer.widget.data.models.GoogleRoutesRequest
import com.commuteoptimizer.widget.data.models.GoogleRoutesResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface GoogleRoutesApiService {
    @POST("directions/v2:computeRoutes")
    suspend fun computeRoutes(
        @Header("X-Goog-Api-Key") apiKey: String,
        @Header("X-Goog-FieldMask") fieldMask: String,
        @Body request: GoogleRoutesRequest
    ): Response<GoogleRoutesResponse>
}
```

**Step 3: Create routing repository**

Create `RoutingRepository.kt`:

```kotlin
package com.commuteoptimizer.widget.data

import com.commuteoptimizer.widget.data.api.GoogleRoutesApiService
import com.commuteoptimizer.widget.data.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import kotlin.math.*

data class TransitRoute(
    val steps: List<TransitStep>,
    val totalDuration: Int,
    val transfers: Int
)

data class TransitStep(
    val mode: String, // "walk", "subway", "bus", "rail"
    val duration: Int,
    val route: String? = null,
    val fromStop: String? = null,
    val toStop: String? = null
)

class RoutingRepository(private val apiKey: String) {

    companion object {
        private const val WALKING_SPEED_MPH = 3.0
        private const val BIKING_SPEED_MPH = 10.0
    }

    private val apiService: GoogleRoutesApiService by lazy {
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        Retrofit.Builder()
            .baseUrl("https://routes.googleapis.com/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GoogleRoutesApiService::class.java)
    }

    suspend fun getBikeTime(fromLat: Double, fromLng: Double, toLat: Double, toLng: Double): Int = withContext(Dispatchers.IO) {
        if (apiKey.isEmpty()) {
            return@withContext estimateBikeTimeFromDistance(fromLat, fromLng, toLat, toLng)
        }

        try {
            val request = GoogleRoutesRequest(
                origin = GoogleRouteLocation(GoogleLatLng(GoogleCoordinates(fromLat, fromLng))),
                destination = GoogleRouteLocation(GoogleLatLng(GoogleCoordinates(toLat, toLng))),
                travelMode = "BICYCLE"
            )

            val response = apiService.computeRoutes(
                apiKey = apiKey,
                fieldMask = "routes.duration,routes.distanceMeters",
                request = request
            )

            if (response.isSuccessful && response.body()?.routes?.isNotEmpty() == true) {
                val durationStr = response.body()!!.routes!!.first().duration ?: "0s"
                val seconds = durationStr.replace("s", "").toIntOrNull() ?: 0
                return@withContext (seconds / 60.0).toInt().coerceAtLeast(1)
            }
        } catch (e: Exception) {
            // Fall back to estimate
        }

        estimateBikeTimeFromDistance(fromLat, fromLng, toLat, toLng)
    }

    suspend fun getTransitRoute(fromLat: Double, fromLng: Double, toLat: Double, toLng: Double): TransitRoute? = withContext(Dispatchers.IO) {
        if (apiKey.isEmpty()) return@withContext null

        try {
            val request = GoogleRoutesRequest(
                origin = GoogleRouteLocation(GoogleLatLng(GoogleCoordinates(fromLat, fromLng))),
                destination = GoogleRouteLocation(GoogleLatLng(GoogleCoordinates(toLat, toLng))),
                travelMode = "TRANSIT",
                transitPreferences = GoogleTransitPreferences(
                    allowedTravelModes = listOf("SUBWAY", "RAIL"),
                    routingPreference = "FEWER_TRANSFERS"
                )
            )

            val response = apiService.computeRoutes(
                apiKey = apiKey,
                fieldMask = "routes.duration,routes.legs.steps.staticDuration,routes.legs.steps.travelMode,routes.legs.steps.transitDetails",
                request = request
            )

            if (!response.isSuccessful || response.body()?.routes.isNullOrEmpty()) {
                return@withContext null
            }

            val route = response.body()!!.routes!!.first()
            val steps = mutableListOf<TransitStep>()
            var transfers = -1

            route.legs?.forEach { leg ->
                leg.steps?.forEach { step ->
                    val durationSeconds = step.staticDuration?.replace("s", "")?.toIntOrNull() ?: 0
                    val durationMinutes = (durationSeconds / 60.0).toInt().coerceAtLeast(1)

                    when (step.travelMode) {
                        "WALK" -> steps.add(TransitStep(mode = "walk", duration = durationMinutes))
                        "TRANSIT" -> {
                            val td = step.transitDetails
                            val vehicleType = td?.transitLine?.vehicle?.type
                            val mode = when (vehicleType) {
                                "BUS" -> "bus"
                                "HEAVY_RAIL", "COMMUTER_TRAIN" -> "rail"
                                else -> "subway"
                            }
                            val routeName = normalizeRouteName(td?.transitLine?.nameShort ?: td?.transitLine?.name ?: "")

                            steps.add(TransitStep(
                                mode = mode,
                                duration = durationMinutes,
                                route = routeName,
                                fromStop = td?.stopDetails?.departureStop?.name,
                                toStop = td?.stopDetails?.arrivalStop?.name
                            ))
                            transfers++
                        }
                    }
                }
            }

            val totalDurationSeconds = route.duration?.replace("s", "")?.toIntOrNull() ?: 0
            val totalDuration = (totalDurationSeconds / 60.0).toInt()

            TransitRoute(
                steps = steps,
                totalDuration = totalDuration,
                transfers = maxOf(0, transfers)
            )
        } catch (e: Exception) {
            null
        }
    }

    fun calculateWalkTime(fromLat: Double, fromLng: Double, toLat: Double, toLng: Double): Int {
        val distanceMiles = haversineDistance(fromLat, fromLng, toLat, toLng) * 1.1
        return ceil(distanceMiles / WALKING_SPEED_MPH * 60).toInt()
    }

    private fun estimateBikeTimeFromDistance(fromLat: Double, fromLng: Double, toLat: Double, toLng: Double): Int {
        val distanceMiles = haversineDistance(fromLat, fromLng, toLat, toLng) * 1.2
        return ceil(distanceMiles / BIKING_SPEED_MPH * 60).toInt() + 2
    }

    private fun haversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 3959.0 // Earth radius in miles
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    private fun normalizeRouteName(name: String): String {
        val mappings = mapOf(
            "Lexington Avenue Express" to "4",
            "Lexington Avenue Local" to "6",
            "Broadway Express" to "N",
            "Broadway Local" to "R",
            "Eighth Avenue Express" to "A",
            "Eighth Avenue Local" to "C",
            "Sixth Avenue Express" to "B",
            "Sixth Avenue Local" to "F",
            "Crosstown" to "G",
            "Canarsie" to "L",
            "Flushing Express" to "7",
            "Flushing Local" to "7"
        )

        for ((pattern, replacement) in mappings) {
            if (name.contains(pattern)) return replacement
        }

        if (name.matches(Regex("^[A-Z0-9]$"))) return name

        val match = Regex("\\b([A-Z0-9])\\b").find(name)
        return match?.groupValues?.get(1) ?: name
    }
}
```

**Step 4: Run build to verify**

Run: `cd android && ./gradlew assembleDebug --no-daemon 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```bash
git add android/app/src/main/java/com/commuteoptimizer/widget/data/
git commit -m "feat(android): add Google Routes API client for bike/transit routing"
```

---

## Task 5: Create Ranking Service

**Files:**
- Create: `android/app/src/main/java/com/commuteoptimizer/widget/data/RankingService.kt`

**Step 1: Create ranking service**

Create `RankingService.kt`:

```kotlin
package com.commuteoptimizer.widget.data

import com.commuteoptimizer.widget.data.models.CommuteOption
import com.commuteoptimizer.widget.data.models.Weather

object RankingService {

    fun rankOptions(options: List<CommuteOption>, weather: Weather): List<CommuteOption> {
        if (options.isEmpty()) return emptyList()

        // Sort by total duration (fastest first)
        val sorted = options.sortedBy { it.durationMinutes }.toMutableList()

        // If bad weather, ensure bike options aren't #1
        if (weather.isBad) {
            val firstBikeIndex = sorted.indexOfFirst { it.type == "bike_to_transit" }
            val firstTransitIndex = sorted.indexOfFirst { it.type == "transit_only" }

            if (firstBikeIndex == 0 && firstTransitIndex > 0) {
                // Move first transit option to #1
                val transit = sorted.removeAt(firstTransitIndex)
                sorted.add(0, transit)
            }
        }

        // Assign ranks
        return sorted.mapIndexed { index, option ->
            option.copy(rank = index + 1)
        }
    }
}
```

**Step 2: Run build to verify**

Run: `cd android && ./gradlew assembleDebug --no-daemon 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add android/app/src/main/java/com/commuteoptimizer/widget/data/RankingService.kt
git commit -m "feat(android): add weather-aware ranking service"
```

---

## Task 6: Create Serverless Commute Repository

**Files:**
- Create: `android/app/src/main/java/com/commuteoptimizer/widget/data/ServerlessCommuteRepository.kt`

**Step 1: Create serverless repository**

Create `ServerlessCommuteRepository.kt`:

```kotlin
package com.commuteoptimizer.widget.data

import com.commuteoptimizer.widget.data.models.*
import com.commuteoptimizer.widget.util.WidgetPreferences
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

class ServerlessCommuteRepository(private val prefs: WidgetPreferences) {

    private val weatherRepo by lazy { WeatherRepository(prefs.getOpenWeatherApiKey()) }
    private val transiterRepo by lazy { TransiterRepository() }
    private val routingRepo by lazy { RoutingRepository(prefs.getGoogleMapsApiKey()) }

    suspend fun getCommuteOptions(): Result<CommuteResponse> = coroutineScope {
        if (!prefs.hasRequiredSettings()) {
            return@coroutineScope Result.Error("Please configure home/work addresses and API keys")
        }

        val homeLat = prefs.getHomeLat()
        val homeLng = prefs.getHomeLng()
        val workLat = prefs.getWorkLat()
        val workLng = prefs.getWorkLng()

        // Fetch weather
        val weatherResult = weatherRepo.getWeather(homeLat, homeLng)
        val weather = when (weatherResult) {
            is Result.Success -> weatherResult.data
            is Result.Error -> Weather(65, "Unknown", "none", 0, false)
        }

        // Get nearby stations
        val stationsResult = transiterRepo.getAllStations()
        val allStations = when (stationsResult) {
            is Result.Success -> stationsResult.data
            is Result.Error -> return@coroutineScope Result.Error(stationsResult.message)
        }

        // Find walkable stations (within ~30 min walk)
        val walkableStations = allStations.filter { station ->
            val walkTime = routingRepo.calculateWalkTime(homeLat, homeLng, station.lat, station.lng)
            walkTime <= 30
        }.take(10) // Limit to avoid too many API calls

        val options = mutableListOf<CommuteOption>()

        // Calculate options for each walkable station
        for (station in walkableStations) {
            try {
                val walkTime = routingRepo.calculateWalkTime(homeLat, homeLng, station.lat, station.lng)

                // Get transit route from station to work
                val transitRoute = routingRepo.getTransitRoute(station.lat, station.lng, workLat, workLng)
                    ?: continue

                if (transitRoute.steps.isEmpty()) continue

                // Get next train from Transiter
                val arrivalsResult = transiterRepo.getStopArrivals(station.transiterId)
                val arrivals = when (arrivalsResult) {
                    is Result.Success -> arrivalsResult.data
                    is Result.Error -> emptyList()
                }
                val nextArrival = transiterRepo.getNextArrival(arrivals)

                val totalTime = walkTime + transitRoute.totalDuration

                // Build legs
                val legs = mutableListOf<Leg>()
                legs.add(Leg(mode = "walk", duration = walkTime, to = station.name))

                val transitIndices = transitRoute.steps.mapIndexedNotNull { i, s ->
                    if (s.mode == "subway" || s.mode == "rail") i else null
                }
                val firstTransitIndex = transitIndices.firstOrNull() ?: transitRoute.steps.size
                val lastTransitIndex = transitIndices.lastOrNull() ?: -1

                transitRoute.steps.forEachIndexed { i, step ->
                    when {
                        step.mode == "walk" && step.duration > 1 && i > firstTransitIndex && i < lastTransitIndex -> {
                            legs.add(Leg(mode = "walk", duration = step.duration, to = step.toStop ?: "Transfer"))
                        }
                        step.mode == "subway" || step.mode == "rail" -> {
                            legs.add(Leg(mode = "subway", duration = step.duration, to = step.toStop ?: "Destination", route = step.route))
                        }
                    }
                }

                // Build summary
                val transitLegs = transitRoute.steps.filter { it.mode == "subway" || it.mode == "rail" }
                val routeNames = transitLegs.mapNotNull { it.route }.joinToString(" → ")
                val summary = if (transitRoute.transfers > 0) {
                    "Walk → ${station.name} → $routeNames (${transitRoute.transfers} transfer${if (transitRoute.transfers > 1) "s" else ""})"
                } else {
                    "Walk → ${station.name} → $routeNames"
                }

                val now = System.currentTimeMillis()
                val arrivalTime = java.util.Date(now + totalTime * 60000)
                val timeFormat = java.text.SimpleDateFormat("h:mm a", java.util.Locale.US)

                options.add(CommuteOption(
                    id = "walk_${station.id}",
                    rank = 0,
                    type = "transit_only",
                    durationMinutes = totalTime,
                    summary = summary,
                    legs = legs,
                    nextTrain = nextArrival?.let { transiterRepo.formatArrivalTime(it) } ?: "N/A",
                    arrivalTime = timeFormat.format(arrivalTime),
                    station = station
                ))
            } catch (e: Exception) {
                // Skip this station on error
            }
        }

        // Rank options
        val rankedOptions = RankingService.rankOptions(options, weather)

        Result.Success(CommuteResponse(
            options = rankedOptions,
            weather = weather,
            alerts = emptyList(),
            generatedAt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).format(java.util.Date())
        ))
    }
}
```

**Step 2: Run build to verify**

Run: `cd android && ./gradlew assembleDebug --no-daemon 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add android/app/src/main/java/com/commuteoptimizer/widget/data/ServerlessCommuteRepository.kt
git commit -m "feat(android): add serverless commute repository orchestrating all services"
```

---

## Task 7: Update Configuration Activity UI

**Files:**
- Modify: `android/app/src/main/res/layout/activity_config.xml`
- Modify: `android/app/src/main/res/values/strings.xml`

**Step 1: Update activity_config.xml**

Replace the entire content:

```xml
<?xml version="1.0" encoding="utf-8"?>
<ScrollView xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:fillViewport="true">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:padding="24dp">

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@string/config_title"
            android:textSize="24sp"
            android:textStyle="bold"
            android:textColor="@color/widget_text_primary"
            android:layout_marginBottom="8dp" />

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="Configure your commute settings"
            android:textColor="@color/widget_text_secondary"
            android:layout_marginBottom="24dp" />

        <!-- Mode Selection -->
        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="Mode"
            android:textStyle="bold"
            android:textColor="@color/widget_text_primary"
            android:layout_marginBottom="8dp" />

        <RadioGroup
            android:id="@+id/radio_mode"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginBottom="16dp">

            <RadioButton
                android:id="@+id/radio_serverless"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:text="Serverless (recommended)"
                android:checked="true" />

            <RadioButton
                android:id="@+id/radio_server"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:text="Use backend server" />

        </RadioGroup>

        <!-- Serverless Settings -->
        <LinearLayout
            android:id="@+id/serverless_settings"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical">

            <!-- Home Address -->
            <com.google.android.material.textfield.TextInputLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:hint="Home Address"
                style="@style/Widget.MaterialComponents.TextInputLayout.OutlinedBox"
                android:layout_marginBottom="16dp">

                <com.google.android.material.textfield.TextInputEditText
                    android:id="@+id/input_home_address"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:inputType="textPostalAddress" />

            </com.google.android.material.textfield.TextInputLayout>

            <!-- Work Address -->
            <com.google.android.material.textfield.TextInputLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:hint="Work Address"
                style="@style/Widget.MaterialComponents.TextInputLayout.OutlinedBox"
                android:layout_marginBottom="16dp">

                <com.google.android.material.textfield.TextInputEditText
                    android:id="@+id/input_work_address"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:inputType="textPostalAddress" />

            </com.google.android.material.textfield.TextInputLayout>

            <!-- OpenWeatherMap API Key -->
            <com.google.android.material.textfield.TextInputLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:hint="OpenWeatherMap API Key"
                style="@style/Widget.MaterialComponents.TextInputLayout.OutlinedBox"
                android:layout_marginBottom="8dp">

                <com.google.android.material.textfield.TextInputEditText
                    android:id="@+id/input_openweather_key"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:inputType="text" />

            </com.google.android.material.textfield.TextInputLayout>

            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="Free at openweathermap.org"
                android:textColor="@color/widget_text_secondary"
                android:textSize="12sp"
                android:layout_marginBottom="16dp" />

            <!-- Google Maps API Key -->
            <com.google.android.material.textfield.TextInputLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:hint="Google Maps API Key (optional)"
                style="@style/Widget.MaterialComponents.TextInputLayout.OutlinedBox"
                android:layout_marginBottom="8dp">

                <com.google.android.material.textfield.TextInputEditText
                    android:id="@+id/input_google_key"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:inputType="text" />

            </com.google.android.material.textfield.TextInputLayout>

            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="For accurate bike/transit times (uses estimates without)"
                android:textColor="@color/widget_text_secondary"
                android:textSize="12sp"
                android:layout_marginBottom="16dp" />

        </LinearLayout>

        <!-- Server Settings -->
        <LinearLayout
            android:id="@+id/server_settings"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical"
            android:visibility="gone">

            <com.google.android.material.textfield.TextInputLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:hint="@string/config_api_url_label"
                style="@style/Widget.MaterialComponents.TextInputLayout.OutlinedBox">

                <com.google.android.material.textfield.TextInputEditText
                    android:id="@+id/input_api_url"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:inputType="textUri"
                    android:text="http://192.168.1.100:8888" />

            </com.google.android.material.textfield.TextInputLayout>

            <Button
                android:id="@+id/btn_test_connection"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="16dp"
                android:text="@string/config_test_connection"
                style="@style/Widget.MaterialComponents.Button.OutlinedButton" />

        </LinearLayout>

        <TextView
            android:id="@+id/text_connection_status"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="8dp"
            android:gravity="center"
            android:textSize="14sp"
            android:visibility="gone" />

        <View
            android:layout_width="match_parent"
            android:layout_height="0dp"
            android:layout_weight="1"
            android:minHeight="24dp" />

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="horizontal">

            <Button
                android:id="@+id/btn_cancel"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:layout_marginEnd="8dp"
                android:text="@string/config_cancel"
                style="@style/Widget.MaterialComponents.Button.TextButton" />

            <Button
                android:id="@+id/btn_save"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:layout_marginStart="8dp"
                android:text="@string/config_save" />

        </LinearLayout>

    </LinearLayout>

</ScrollView>
```

**Step 2: Run build to verify**

Run: `cd android && ./gradlew assembleDebug --no-daemon 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add android/app/src/main/res/layout/activity_config.xml
git commit -m "feat(android): update config UI with serverless settings"
```

---

## Task 8: Update Configuration Activity Logic

**Files:**
- Modify: `android/app/src/main/java/com/commuteoptimizer/widget/CommuteWidgetConfigActivity.kt`

**Step 1: Update CommuteWidgetConfigActivity.kt**

Replace the entire content with the updated activity that handles both modes:

```kotlin
package com.commuteoptimizer.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.commuteoptimizer.widget.data.CommuteRepository
import com.commuteoptimizer.widget.data.Result
import com.commuteoptimizer.widget.data.RoutingRepository
import com.commuteoptimizer.widget.util.WidgetPreferences
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CommuteWidgetConfigActivity : AppCompatActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    private lateinit var radioMode: RadioGroup
    private lateinit var radioServerless: RadioButton
    private lateinit var radioServer: RadioButton
    private lateinit var serverlessSettings: LinearLayout
    private lateinit var serverSettings: LinearLayout

    private lateinit var inputHomeAddress: TextInputEditText
    private lateinit var inputWorkAddress: TextInputEditText
    private lateinit var inputOpenWeatherKey: TextInputEditText
    private lateinit var inputGoogleKey: TextInputEditText

    private lateinit var inputApiUrl: TextInputEditText
    private lateinit var btnTestConnection: Button
    private lateinit var btnSave: Button
    private lateinit var btnCancel: Button
    private lateinit var textConnectionStatus: TextView

    private lateinit var prefs: WidgetPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        setContentView(R.layout.activity_config)

        prefs = WidgetPreferences(this)

        appWidgetId = intent.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        initViews()
        loadExistingSettings()
        setupClickListeners()
    }

    private fun initViews() {
        radioMode = findViewById(R.id.radio_mode)
        radioServerless = findViewById(R.id.radio_serverless)
        radioServer = findViewById(R.id.radio_server)
        serverlessSettings = findViewById(R.id.serverless_settings)
        serverSettings = findViewById(R.id.server_settings)

        inputHomeAddress = findViewById(R.id.input_home_address)
        inputWorkAddress = findViewById(R.id.input_work_address)
        inputOpenWeatherKey = findViewById(R.id.input_openweather_key)
        inputGoogleKey = findViewById(R.id.input_google_key)

        inputApiUrl = findViewById(R.id.input_api_url)
        btnTestConnection = findViewById(R.id.btn_test_connection)
        btnSave = findViewById(R.id.btn_save)
        btnCancel = findViewById(R.id.btn_cancel)
        textConnectionStatus = findViewById(R.id.text_connection_status)
    }

    private fun loadExistingSettings() {
        val mode = prefs.getMode()
        if (mode == "server") {
            radioServer.isChecked = true
            serverlessSettings.visibility = View.GONE
            serverSettings.visibility = View.VISIBLE
        } else {
            radioServerless.isChecked = true
            serverlessSettings.visibility = View.VISIBLE
            serverSettings.visibility = View.GONE
        }

        inputHomeAddress.setText(prefs.getHomeAddress())
        inputWorkAddress.setText(prefs.getWorkAddress())
        inputOpenWeatherKey.setText(prefs.getOpenWeatherApiKey())
        inputGoogleKey.setText(prefs.getGoogleMapsApiKey())
        inputApiUrl.setText(prefs.getApiUrl(appWidgetId))
    }

    private fun setupClickListeners() {
        radioMode.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.radio_serverless -> {
                    serverlessSettings.visibility = View.VISIBLE
                    serverSettings.visibility = View.GONE
                }
                R.id.radio_server -> {
                    serverlessSettings.visibility = View.GONE
                    serverSettings.visibility = View.VISIBLE
                }
            }
            textConnectionStatus.visibility = View.GONE
        }

        btnTestConnection.setOnClickListener { testConnection() }
        btnSave.setOnClickListener { saveConfiguration() }
        btnCancel.setOnClickListener { finish() }
    }

    private fun testConnection() {
        val url = inputApiUrl.text?.toString()?.trim()
        if (url.isNullOrEmpty()) {
            showStatus(getString(R.string.config_url_required), isError = true)
            return
        }

        showStatus(getString(R.string.config_testing), isError = false)
        btnTestConnection.isEnabled = false

        lifecycleScope.launch {
            val repository = CommuteRepository(url)
            when (val result = repository.testConnection()) {
                is Result.Success -> showStatus(getString(R.string.config_success), isError = false)
                is Result.Error -> showStatus(getString(R.string.config_error, result.message), isError = true)
            }
            btnTestConnection.isEnabled = true
        }
    }

    private fun saveConfiguration() {
        val isServerless = radioServerless.isChecked

        if (isServerless) {
            val homeAddress = inputHomeAddress.text?.toString()?.trim() ?: ""
            val workAddress = inputWorkAddress.text?.toString()?.trim() ?: ""
            val openWeatherKey = inputOpenWeatherKey.text?.toString()?.trim() ?: ""
            val googleKey = inputGoogleKey.text?.toString()?.trim() ?: ""

            if (homeAddress.isEmpty() || workAddress.isEmpty()) {
                showStatus("Please enter home and work addresses", isError = true)
                return
            }

            if (openWeatherKey.isEmpty()) {
                showStatus("Please enter OpenWeatherMap API key", isError = true)
                return
            }

            showStatus("Geocoding addresses...", isError = false)
            btnSave.isEnabled = false

            lifecycleScope.launch {
                val routingRepo = RoutingRepository(googleKey)

                // Geocode addresses using Google Geocoding API
                val homeCoords = geocodeAddress(homeAddress, googleKey)
                val workCoords = geocodeAddress(workAddress, googleKey)

                if (homeCoords == null) {
                    showStatus("Could not find home address", isError = true)
                    btnSave.isEnabled = true
                    return@launch
                }

                if (workCoords == null) {
                    showStatus("Could not find work address", isError = true)
                    btnSave.isEnabled = true
                    return@launch
                }

                prefs.setMode("serverless")
                prefs.setHome(homeCoords.first, homeCoords.second, homeAddress)
                prefs.setWork(workCoords.first, workCoords.second, workAddress)
                prefs.setOpenWeatherApiKey(openWeatherKey)
                prefs.setGoogleMapsApiKey(googleKey)

                finishWithSuccess()
            }
        } else {
            val url = inputApiUrl.text?.toString()?.trim()
            if (url.isNullOrEmpty()) {
                showStatus(getString(R.string.config_url_required), isError = true)
                return
            }

            prefs.setMode("server")
            prefs.setApiUrl(appWidgetId, url)
            finishWithSuccess()
        }
    }

    private suspend fun geocodeAddress(address: String, apiKey: String): Pair<Double, Double>? = withContext(Dispatchers.IO) {
        if (apiKey.isEmpty()) {
            // Use a simple lookup for common NYC addresses (fallback)
            return@withContext null
        }

        try {
            val url = "https://maps.googleapis.com/maps/api/geocode/json?address=${java.net.URLEncoder.encode(address, "UTF-8")}&key=$apiKey"
            val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "GET"

            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().readText()
                val json = org.json.JSONObject(response)
                val results = json.getJSONArray("results")
                if (results.length() > 0) {
                    val location = results.getJSONObject(0)
                        .getJSONObject("geometry")
                        .getJSONObject("location")
                    return@withContext Pair(location.getDouble("lat"), location.getDouble("lng"))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        null
    }

    private fun finishWithSuccess() {
        val intent = Intent(this, CommuteWidgetProvider::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(appWidgetId))
        }
        sendBroadcast(intent)

        val resultValue = Intent().apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        setResult(RESULT_OK, resultValue)
        finish()
    }

    private fun showStatus(message: String, isError: Boolean) {
        textConnectionStatus.visibility = View.VISIBLE
        textConnectionStatus.text = message
        textConnectionStatus.setTextColor(
            if (isError) Color.parseColor("#F44336") else Color.parseColor("#4CAF50")
        )
    }
}
```

**Step 2: Run build to verify**

Run: `cd android && ./gradlew assembleDebug --no-daemon 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add android/app/src/main/java/com/commuteoptimizer/widget/CommuteWidgetConfigActivity.kt
git commit -m "feat(android): update config activity for serverless mode"
```

---

## Task 9: Update Widget Provider for Serverless Mode

**Files:**
- Modify: `android/app/src/main/java/com/commuteoptimizer/widget/CommuteWidgetProvider.kt`

**Step 1: Update CommuteWidgetProvider.kt**

Add serverless repository support. Find the `updateWidget` function and update it:

```kotlin
private fun updateWidget(
    context: Context,
    appWidgetManager: AppWidgetManager,
    widgetId: Int
) {
    val prefs = WidgetPreferences(context)

    scope.launch {
        val result = if (prefs.getMode() == "serverless") {
            val serverlessRepo = ServerlessCommuteRepository(prefs)
            serverlessRepo.getCommuteOptions()
        } else {
            val apiUrl = prefs.getApiUrl(widgetId)
            val repository = CommuteRepository(apiUrl)
            repository.getCommuteOptions()
        }

        when (result) {
            is Result.Success -> {
                prefs.setLastUpdate(widgetId, System.currentTimeMillis())
                val views = buildWidgetViews(context, result.data, widgetId)
                appWidgetManager.updateAppWidget(widgetId, views)
            }
            is Result.Error -> {
                val views = buildErrorViews(context, result.message, widgetId)
                appWidgetManager.updateAppWidget(widgetId, views)
            }
        }
    }
}
```

Also add the import at the top:

```kotlin
import com.commuteoptimizer.widget.data.ServerlessCommuteRepository
```

**Step 2: Run build to verify**

Run: `cd android && ./gradlew assembleDebug --no-daemon 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add android/app/src/main/java/com/commuteoptimizer/widget/CommuteWidgetProvider.kt
git commit -m "feat(android): update widget provider to support serverless mode"
```

---

## Task 10: Build and Test

**Step 1: Full build**

Run: `cd android && ./gradlew clean assembleDebug --no-daemon 2>&1 | tail -30`
Expected: BUILD SUCCESSFUL

**Step 2: Push and verify CI**

```bash
git push
gh run watch --exit-status
```

Expected: All checks pass

**Step 3: Commit final**

```bash
git add .
git commit -m "feat(android): complete serverless widget implementation"
```

---

## Summary

After completing all tasks, the Android widget will:

1. **Serverless mode (default)**: Calls APIs directly:
   - OpenWeatherMap for weather
   - Transiter Demo API for subway times
   - Google Routes API for bike/transit routing (optional)

2. **Server mode**: Works as before with backend URL

3. **User configures**:
   - Home/work addresses (geocoded via Google)
   - OpenWeatherMap API key (required, free)
   - Google Maps API key (optional, for better routing)

4. **No backend server required** in serverless mode!
