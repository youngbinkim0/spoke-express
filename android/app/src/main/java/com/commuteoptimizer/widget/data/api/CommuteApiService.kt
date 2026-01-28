package com.commuteoptimizer.widget.data.api

import com.commuteoptimizer.widget.data.models.CommuteResponse
import retrofit2.Response
import retrofit2.http.GET

interface CommuteApiService {

    @GET("/api/commute")
    suspend fun getCommuteOptions(): Response<CommuteResponse>

    @GET("/health")
    suspend fun healthCheck(): Response<Map<String, Any>>
}
