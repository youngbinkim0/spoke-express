package com.commuteoptimizer.widget.service

import com.commuteoptimizer.widget.data.models.CommuteOption
import com.commuteoptimizer.widget.data.models.Weather

/**
 * Ranks commute options with weather-awareness.
 * In bad weather, bike options are demoted below transit options.
 */
object RankingService {

    /**
     * Rank commute options by duration, with weather-aware adjustments.
     * - Sort by total duration (fastest first)
     * - If weather is bad, move first transit option above bike option
     */
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
