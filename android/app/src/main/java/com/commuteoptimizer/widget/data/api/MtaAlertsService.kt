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
                    0 -> readVarint(input)
                    1 -> input.skipBytes(8)
                    2 -> {
                        val len = readVarint(input)
                        if (fieldNum == 1) {
                            val entityBytes = ByteArray(len)
                            input.readFully(entityBytes)
                            parseEntity(entityBytes)?.let { alerts.add(it) }
                        } else {
                            input.skipBytes(len)
                        }
                    }
                    5 -> input.skipBytes(4)
                    else -> break
                }
            }
        } catch (e: Exception) {
            // Parsing complete
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
                        if (fieldNum == 2) {
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
            android.util.Log.e("MtaAlerts", "Error parsing alert", e)
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
                        if (fieldNum == 6) effect = ALERT_EFFECTS[value] ?: "UNKNOWN"
                    }
                    1 -> input.skipBytes(8)
                    2 -> {
                        val len = readVarint(input)
                        val data = ByteArray(len)
                        input.readFully(data)

                        when (fieldNum) {
                            5 -> parseInformedEntity(data)?.let { routeIds.add(it) }
                            10 -> headerText = parseTranslatedString(data)
                        }
                    }
                    5 -> input.skipBytes(4)
                    else -> break
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MtaAlerts", "Error parsing alert", e)
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
                        if (fieldNum == 3) return String(data)
                    }
                    else -> break
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MtaAlerts", "Error parsing alert", e)
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
                    if (fieldNum == 1) return parseTranslation(data)
                } else break
            }
        } catch (e: Exception) {
            android.util.Log.e("MtaAlerts", "Error parsing alert", e)
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
                    if (fieldNum == 1) return String(data)
                } else if (wireType == 0) {
                    readVarint(input)
                } else break
            }
        } catch (e: Exception) {
            android.util.Log.e("MtaAlerts", "Error parsing alert", e)
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
            if (shift >= 35) break
        }
        return result
    }
}
