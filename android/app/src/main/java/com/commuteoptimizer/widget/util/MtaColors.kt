package com.commuteoptimizer.widget.util

import android.graphics.Color

object MtaColors {

    // Official MTA line colors
    private val lineColors = mapOf(
        // Red lines (1, 2, 3)
        "1" to Color.parseColor("#EE352E"),
        "2" to Color.parseColor("#EE352E"),
        "3" to Color.parseColor("#EE352E"),

        // Green lines (4, 5, 6)
        "4" to Color.parseColor("#00933C"),
        "5" to Color.parseColor("#00933C"),
        "6" to Color.parseColor("#00933C"),

        // Purple line (7)
        "7" to Color.parseColor("#B933AD"),

        // Blue lines (A, C, E)
        "A" to Color.parseColor("#0039A6"),
        "C" to Color.parseColor("#0039A6"),
        "E" to Color.parseColor("#0039A6"),

        // Orange lines (B, D, F, M)
        "B" to Color.parseColor("#FF6319"),
        "D" to Color.parseColor("#FF6319"),
        "F" to Color.parseColor("#FF6319"),
        "M" to Color.parseColor("#FF6319"),

        // Lime green line (G)
        "G" to Color.parseColor("#6CBE45"),

        // Brown lines (J, Z)
        "J" to Color.parseColor("#996633"),
        "Z" to Color.parseColor("#996633"),

        // Gray line (L)
        "L" to Color.parseColor("#A7A9AC"),

        // Yellow lines (N, Q, R, W)
        "N" to Color.parseColor("#FCCC0A"),
        "Q" to Color.parseColor("#FCCC0A"),
        "R" to Color.parseColor("#FCCC0A"),
        "W" to Color.parseColor("#FCCC0A"),

        // Shuttle (S)
        "S" to Color.parseColor("#808183")
    )

    private val defaultColor = Color.parseColor("#808183")

    fun getLineColor(line: String): Int {
        return lineColors[line.uppercase()] ?: defaultColor
    }

    fun getTextColorForLine(line: String): Int {
        // Yellow lines need dark text
        return when (line.uppercase()) {
            "N", "Q", "R", "W" -> Color.BLACK
            else -> Color.WHITE
        }
    }

    // Get weather emoji
    fun getWeatherEmoji(conditions: String, precipType: String): String {
        return when {
            precipType == "rain" -> "\uD83C\uDF27" // rain cloud
            precipType == "snow" -> "\u2744" // snowflake
            precipType == "mix" -> "\uD83C\uDF28" // cloud with snow
            conditions.contains("cloud", ignoreCase = true) -> "\u2601" // cloud
            conditions.contains("sun", ignoreCase = true) ||
                conditions.contains("clear", ignoreCase = true) -> "\u2600" // sun
            else -> ""
        }
    }

    // Get mode icon resource name
    fun getModeIcon(mode: String): String {
        return when (mode.lowercase()) {
            "bike" -> "ic_bike"
            "walk" -> "ic_walk"
            "subway" -> "ic_subway"
            else -> "ic_subway"
        }
    }

    // Clean express line suffix (e.g., "6X" -> "6")
    fun cleanExpressLine(line: String): String {
        return if (line.length == 2 && line.endsWith("X")) {
            line.dropLast(1)
        } else {
            line
        }
    }
}
