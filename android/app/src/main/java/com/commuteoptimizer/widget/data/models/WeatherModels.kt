package com.commuteoptimizer.widget.data.models

import com.google.gson.annotations.SerializedName

// Response from OpenWeatherMap Current Weather API 2.5 (Free tier)
data class OpenWeatherResponse(
    val main: MainWeather,
    val weather: List<WeatherCondition>,
    val rain: RainInfo? = null,
    val snow: SnowInfo? = null
)

data class MainWeather(
    val temp: Double,
    val humidity: Int? = null
)

data class WeatherCondition(
    val id: Int,
    val main: String,
    val description: String? = null,
    val icon: String? = null
)

data class RainInfo(
    @SerializedName("1h")
    val oneHour: Double? = null,
    @SerializedName("3h")
    val threeHour: Double? = null
)

data class SnowInfo(
    @SerializedName("1h")
    val oneHour: Double? = null,
    @SerializedName("3h")
    val threeHour: Double? = null
)
