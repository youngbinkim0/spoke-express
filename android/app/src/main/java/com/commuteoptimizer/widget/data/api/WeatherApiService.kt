package com.commuteoptimizer.widget.data.api

import com.commuteoptimizer.widget.data.models.GoogleWeatherResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface WeatherApiService {

    @GET("v1/currentConditions:lookup")
    suspend fun getWeather(
        @Query("location.latitude") lat: Double,
        @Query("location.longitude") lon: Double,
        @Query("unitsSystem") units: String = "IMPERIAL",
        @Query("key") apiKey: String
    ): Response<GoogleWeatherResponse>
}
