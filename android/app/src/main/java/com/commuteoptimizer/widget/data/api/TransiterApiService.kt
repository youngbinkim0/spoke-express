package com.commuteoptimizer.widget.data.api

import com.commuteoptimizer.widget.data.models.TransiterStopResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * API service for Transiter demo API.
 * Base URL: https://demo.transiter.dev/
 */
interface TransiterApiService {

    @GET("systems/us-ny-subway/stops/{stopId}")
    suspend fun getStopArrivals(
        @Path("stopId") stopId: String
    ): Response<TransiterStopResponse>
}
