package com.commuteoptimizer.widget.data.models

data class GoogleWeatherResponse(
    val temperature: GoogleTemperature? = null,
    val weatherCondition: GoogleWeatherCondition? = null,
    val precipitation: GooglePrecipitation? = null
)

data class GoogleTemperature(
    val degrees: Double? = null
)

data class GoogleWeatherCondition(
    val type: String? = null,
    val description: GoogleDescription? = null
)

data class GoogleDescription(
    val text: String? = null
)

data class GooglePrecipitation(
    val type: String? = null,
    val probability: GoogleProbability? = null
)

data class GoogleProbability(
    val percent: Int? = null
)
