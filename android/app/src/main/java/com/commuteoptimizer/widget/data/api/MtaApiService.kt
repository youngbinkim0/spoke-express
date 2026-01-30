package com.commuteoptimizer.widget.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * MTA GTFS-Realtime API Service
 * Fetches real-time subway data directly from MTA feeds
 * No API key required
 *
 * Uses manual protobuf parsing to avoid build complexity
 */
object MtaApiService {

    private const val MTA_BASE_URL = "https://api-endpoint.mta.info/Dataservice/mtagtfsfeeds/nyct%2F"

    // Map lines to their feed names
    private val LINE_TO_FEED = mapOf(
        "1" to "gtfs", "2" to "gtfs", "3" to "gtfs",
        "4" to "gtfs", "5" to "gtfs", "6" to "gtfs",
        "7" to "gtfs", "S" to "gtfs",
        "A" to "gtfs-ace", "C" to "gtfs-ace", "E" to "gtfs-ace",
        "B" to "gtfs-bdfm", "D" to "gtfs-bdfm", "F" to "gtfs-bdfm", "M" to "gtfs-bdfm",
        "G" to "gtfs-g",
        "J" to "gtfs-jz", "Z" to "gtfs-jz",
        "L" to "gtfs-l",
        "N" to "gtfs-nqrw", "Q" to "gtfs-nqrw", "R" to "gtfs-nqrw", "W" to "gtfs-nqrw"
    )

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "NYC-Commute-Optimizer/1.0")
                    .build()
                chain.proceed(request)
            }
            .build()
    }

    data class Arrival(
        val routeId: String,
        val direction: String, // "N" or "S"
        val arrivalTime: Long, // Unix timestamp in seconds
        val minutesAway: Int
    )

    data class NextArrivalResult(
        val nextTrain: String,
        val arrivalTime: String,
        val routeId: String?
    )

    data class ArrivalGroup(
        val line: String,
        val direction: String,
        val headsign: String,
        val arrivals: List<Arrival>
    )

    /**
     * Simple protobuf reader for GTFS-realtime format
     */
    private class ProtobufReader(data: ByteArray) {
        private val buffer = data
        private var pos = 0

        fun hasMore(): Boolean = pos < buffer.size

        fun readVarint(): Long {
            var result = 0L
            var shift = 0
            while (pos < buffer.size) {
                val byte = buffer[pos++].toInt() and 0xFF
                result = result or ((byte and 0x7F).toLong() shl shift)
                if ((byte and 0x80) == 0) break
                shift += 7
            }
            return result
        }

        fun readString(length: Int): String {
            val bytes = buffer.copyOfRange(pos, pos + length)
            pos += length
            return String(bytes, Charsets.UTF_8)
        }

        fun readBytes(length: Int): ByteArray {
            val bytes = buffer.copyOfRange(pos, pos + length)
            pos += length
            return bytes
        }

        fun skip(wireType: Int) {
            when (wireType) {
                0 -> readVarint() // Varint
                1 -> pos += 8     // 64-bit
                2 -> {            // Length-delimited
                    val len = readVarint().toInt()
                    pos += len
                }
                5 -> pos += 4     // 32-bit
            }
        }
    }

    /**
     * Parse GTFS-realtime feed and extract arrivals for specific stops
     */
    private fun parseFeed(data: ByteArray, targetStopIds: Set<String>): List<Arrival> {
        val arrivals = mutableListOf<Arrival>()
        val now = System.currentTimeMillis() / 1000

        try {
            val reader = ProtobufReader(data)

            while (reader.hasMore()) {
                val tag = reader.readVarint().toInt()
                val fieldNumber = tag shr 3
                val wireType = tag and 0x7

                if (fieldNumber == 2 && wireType == 2) {
                    // FeedEntity (repeated, field 2)
                    val length = reader.readVarint().toInt()
                    val entityData = reader.readBytes(length)

                    val entityArrivals = parseEntity(entityData, targetStopIds, now)
                    arrivals.addAll(entityArrivals)
                } else {
                    reader.skip(wireType)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return arrivals
    }

    private fun parseEntity(data: ByteArray, targetStopIds: Set<String>, now: Long): List<Arrival> {
        val arrivals = mutableListOf<Arrival>()
        val reader = ProtobufReader(data)

        while (reader.hasMore()) {
            val tag = reader.readVarint().toInt()
            val fieldNumber = tag shr 3
            val wireType = tag and 0x7

            if (fieldNumber == 3 && wireType == 2) {
                // TripUpdate (field 3)
                val length = reader.readVarint().toInt()
                val tripUpdateData = reader.readBytes(length)

                val tripArrivals = parseTripUpdate(tripUpdateData, targetStopIds, now)
                arrivals.addAll(tripArrivals)
            } else {
                reader.skip(wireType)
            }
        }

        return arrivals
    }

    private fun parseTripUpdate(data: ByteArray, targetStopIds: Set<String>, now: Long): List<Arrival> {
        val arrivals = mutableListOf<Arrival>()
        val reader = ProtobufReader(data)

        var routeId: String? = null
        val stopTimeUpdates = mutableListOf<ByteArray>()

        while (reader.hasMore()) {
            val tag = reader.readVarint().toInt()
            val fieldNumber = tag shr 3
            val wireType = tag and 0x7

            when {
                fieldNumber == 1 && wireType == 2 -> {
                    // TripDescriptor (field 1)
                    val length = reader.readVarint().toInt()
                    val tripData = reader.readBytes(length)
                    routeId = parseTripDescriptor(tripData)
                }
                fieldNumber == 2 && wireType == 2 -> {
                    // StopTimeUpdate (repeated, field 2)
                    val length = reader.readVarint().toInt()
                    stopTimeUpdates.add(reader.readBytes(length))
                }
                else -> reader.skip(wireType)
            }
        }

        // Process stop time updates
        if (routeId != null) {
            for (stuData in stopTimeUpdates) {
                val arrival = parseStopTimeUpdate(stuData, routeId, targetStopIds, now)
                if (arrival != null) {
                    arrivals.add(arrival)
                }
            }
        }

        return arrivals
    }

    private fun parseTripDescriptor(data: ByteArray): String? {
        val reader = ProtobufReader(data)

        while (reader.hasMore()) {
            val tag = reader.readVarint().toInt()
            val fieldNumber = tag shr 3
            val wireType = tag and 0x7

            if (fieldNumber == 5 && wireType == 2) {
                // route_id (field 5)
                val length = reader.readVarint().toInt()
                return reader.readString(length)
            } else {
                reader.skip(wireType)
            }
        }

        return null
    }

    private fun parseStopTimeUpdate(data: ByteArray, routeId: String, targetStopIds: Set<String>, now: Long): Arrival? {
        val reader = ProtobufReader(data)

        var stopId: String? = null
        var arrivalTime: Long? = null

        while (reader.hasMore()) {
            val tag = reader.readVarint().toInt()
            val fieldNumber = tag shr 3
            val wireType = tag and 0x7

            when {
                fieldNumber == 4 && wireType == 2 -> {
                    // stop_id (field 4)
                    val length = reader.readVarint().toInt()
                    stopId = reader.readString(length)
                }
                fieldNumber == 2 && wireType == 2 -> {
                    // arrival (field 2)
                    val length = reader.readVarint().toInt()
                    val arrivalData = reader.readBytes(length)
                    arrivalTime = parseStopTimeEvent(arrivalData)
                }
                fieldNumber == 3 && wireType == 2 -> {
                    // departure (field 3) - use if no arrival
                    val length = reader.readVarint().toInt()
                    val departureData = reader.readBytes(length)
                    if (arrivalTime == null) {
                        arrivalTime = parseStopTimeEvent(departureData)
                    }
                }
                else -> reader.skip(wireType)
            }
        }

        // Check if this stop matches our targets
        if (stopId != null && targetStopIds.contains(stopId) && arrivalTime != null && arrivalTime >= now - 60) {
            val direction = if (stopId.endsWith("N")) "N" else "S"
            val minutesAway = maxOf(0, ((arrivalTime - now) / 60).toInt())

            return Arrival(
                routeId = routeId,
                direction = direction,
                arrivalTime = arrivalTime,
                minutesAway = minutesAway
            )
        }

        return null
    }

    private fun parseStopTimeEvent(data: ByteArray): Long? {
        val reader = ProtobufReader(data)

        while (reader.hasMore()) {
            val tag = reader.readVarint().toInt()
            val fieldNumber = tag shr 3
            val wireType = tag and 0x7

            if (fieldNumber == 2 && wireType == 0) {
                // time (field 2)
                return reader.readVarint()
            } else {
                reader.skip(wireType)
            }
        }

        return null
    }

    /**
     * Get feeds needed for the given lines
     */
    private fun getFeedsForLines(lines: List<String>): Set<String> {
        return lines.mapNotNull { LINE_TO_FEED[it] }.toSet()
    }

    /**
     * Fetch a single MTA feed
     */
    private suspend fun fetchFeed(feedName: String): ByteArray? {
        return withContext(Dispatchers.IO) {
            try {
                val url = MTA_BASE_URL + feedName
                val request = Request.Builder()
                    .url(url)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext null
                    }
                    response.body?.bytes()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    /**
     * Get all arrivals for a specific station
     * @param stationId The station ID (e.g., "G33")
     * @param lines The lines at this station (e.g., ["G"])
     */
    suspend fun getStationArrivals(stationId: String, lines: List<String>): List<Arrival> {
        val feeds = getFeedsForLines(lines)
        if (feeds.isEmpty()) return emptyList()

        // MTA uses stop IDs with N/S suffix for direction
        val targetStopIds = setOf("${stationId}N", "${stationId}S")

        val allArrivals = mutableListOf<Arrival>()

        for (feedName in feeds) {
            val data = fetchFeed(feedName) ?: continue
            val arrivals = parseFeed(data, targetStopIds)
            allArrivals.addAll(arrivals)
        }

        // Sort by arrival time
        return allArrivals.sortedBy { it.arrivalTime }
    }

    /**
     * Get the next arrival at a station
     * Returns a formatted string like "3m" or "--" if no arrivals
     */
    suspend fun getNextArrival(stationId: String, lines: List<String>): NextArrivalResult {
        val arrivals = getStationArrivals(stationId, lines)

        if (arrivals.isEmpty()) {
            return NextArrivalResult(
                nextTrain = "--",
                arrivalTime = "--",
                routeId = null
            )
        }

        val next = arrivals.first()
        val arrivalDate = Date(next.arrivalTime * 1000)
        val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

        return NextArrivalResult(
            nextTrain = "${next.minutesAway}m",
            arrivalTime = timeFormat.format(arrivalDate),
            routeId = next.routeId
        )
    }

    /**
     * Get grouped arrivals for display (like the arrivals page)
     * Groups arrivals by route and direction
     */
    suspend fun getGroupedArrivals(stationId: String, lines: List<String>): List<ArrivalGroup> {
        val arrivals = getStationArrivals(stationId, lines)

        // Group by route and direction
        val groups = mutableMapOf<String, MutableList<Arrival>>()

        for (arrival in arrivals) {
            val key = "${arrival.routeId}-${arrival.direction}"
            if (!groups.containsKey(key)) {
                groups[key] = mutableListOf()
            }
            val groupList = groups[key]!!
            if (groupList.size < 3) {
                groupList.add(arrival)
            }
        }

        return groups.map { (key, groupArrivals) ->
            val parts = key.split("-")
            val line = parts[0]
            val direction = parts.getOrElse(1) { "N" }
            val headsign = if (direction == "N") "Northbound" else "Southbound"

            ArrivalGroup(
                line = line,
                direction = direction,
                headsign = headsign,
                arrivals = groupArrivals
            )
        }.sortedBy { it.line }
    }
}
