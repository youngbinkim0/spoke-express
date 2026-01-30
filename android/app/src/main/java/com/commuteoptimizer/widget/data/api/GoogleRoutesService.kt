package com.commuteoptimizer.widget.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Service to call Google Directions API directly for transit routes.
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
        apiKey: String,
        originLat: Double,
        originLng: Double,
        destLat: Double,
        destLng: Double
    ): RouteResult = withContext(Dispatchers.IO) {
        val url = "https://maps.googleapis.com/maps/api/directions/json" +
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

            val routes = json.optJSONArray("routes")
            if (routes == null || routes.length() == 0) {
                return@withContext RouteResult("NO_ROUTES", null, null, emptyList())
            }

            val route = routes.getJSONObject(0)
            val legs = route.optJSONArray("legs")
            if (legs == null || legs.length() == 0) {
                return@withContext RouteResult("NO_LEGS", null, null, emptyList())
            }

            val leg = legs.getJSONObject(0)
            val durationSeconds = leg.optJSONObject("duration")?.optInt("value", 0) ?: 0
            val durationMinutes = durationSeconds / 60
            val distance = leg.optJSONObject("distance")?.optString("text", null)

            val steps = leg.optJSONArray("steps") ?: return@withContext RouteResult(
                status, durationMinutes, distance, emptyList()
            )

            val transitSteps = mutableListOf<TransitStep>()
            for (i in 0 until steps.length()) {
                val step = steps.getJSONObject(i)
                val travelMode = step.optString("travel_mode", "")

                if (travelMode == "TRANSIT") {
                    val transitDetails = step.optJSONObject("transit_details") ?: continue
                    val line = transitDetails.optJSONObject("line")
                    val shortName = line?.optString("short_name", null)
                        ?: line?.optString("name", "?")
                        ?: "?"

                    val vehicle = line?.optJSONObject("vehicle")?.optString("type", null)
                    val departureStop = transitDetails.optJSONObject("departure_stop")?.optString("name", null)
                    val arrivalStop = transitDetails.optJSONObject("arrival_stop")?.optString("name", null)
                    val numStops = if (transitDetails.has("num_stops")) transitDetails.optInt("num_stops") else null
                    val stepDuration = step.optJSONObject("duration")?.optInt("value", 0)?.let { it / 60 }

                    transitSteps.add(TransitStep(
                        line = cleanLineName(shortName),
                        vehicle = vehicle,
                        departureStop = departureStop,
                        arrivalStop = arrivalStop,
                        numStops = numStops,
                        duration = stepDuration
                    ))
                }
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
