package com.commuteoptimizer.widget.data.api

import com.commuteoptimizer.widget.data.models.OpenWeatherResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * API service for OpenWeatherMap One Call API 3.0.
 * Base URL: https://api.openweathermap.org/
 */
interface WeatherApiService {

    @GET("data/3.0/onecall")
    suspend fun getWeather(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("units") units: String = "imperial",
        @Query("exclude") exclude: String = "minutely,daily,alerts",
        @Query("appid") apiKey: String
    ): Response<OpenWeatherResponse>
}
