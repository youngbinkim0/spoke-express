package com.commuteoptimizer.widget.data.models

import com.google.gson.annotations.SerializedName

// Response from OpenWeatherMap One Call API 3.0
data class OpenWeatherResponse(
    val lat: Double,
    val lon: Double,
    val current: CurrentWeather,
    val hourly: List<HourlyWeather>? = null
)

data class CurrentWeather(
    val temp: Double,
    val weather: List<WeatherCondition>
)

data class WeatherCondition(
    val id: Int,
    val main: String,
    val description: String,
    val icon: String
)

data class HourlyWeather(
    val dt: Long,
    val temp: Double,
    val pop: Double = 0.0,  // Probability of precipitation (0-1)
    val rain: RainInfo? = null,
    val snow: SnowInfo? = null
)

data class RainInfo(
    @SerializedName("1h")
    val oneHour: Double? = null
)

data class SnowInfo(
    @SerializedName("1h")
    val oneHour: Double? = null
)
