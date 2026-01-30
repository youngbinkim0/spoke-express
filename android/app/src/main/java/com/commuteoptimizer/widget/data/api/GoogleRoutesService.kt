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
