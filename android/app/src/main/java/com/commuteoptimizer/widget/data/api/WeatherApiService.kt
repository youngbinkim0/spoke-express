package com.commuteoptimizer.widget.data.api

import com.commuteoptimizer.widget.data.models.OpenWeatherResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * API service for OpenWeatherMap Current Weather API 2.5 (Free tier).
 * Base URL: https://api.openweathermap.org/
 */
interface WeatherApiService {

    @GET("data/2.5/weather")
    suspend fun getWeather(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("units") units: String = "imperial",
        @Query("appid") apiKey: String
    ): Response<OpenWeatherResponse>
}
