package com.commuteoptimizer.widget.data

import com.commuteoptimizer.widget.data.api.RetrofitClient
import com.commuteoptimizer.widget.data.models.CommuteResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val message: String, val exception: Exception? = null) : Result<Nothing>()
}

class CommuteRepository(private val baseUrl: String) {

    private val apiService = RetrofitClient.getApiService(baseUrl)

    suspend fun getCommuteOptions(): Result<CommuteResponse> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getCommuteOptions()
            if (response.isSuccessful && response.body() != null) {
                Result.Success(response.body()!!)
            } else {
                Result.Error("API error: ${response.code()} ${response.message()}")
            }
        } catch (e: Exception) {
            Result.Error("Network error: ${e.message}", e)
        }
    }

    suspend fun testConnection(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.healthCheck()
            if (response.isSuccessful) {
                Result.Success(true)
            } else {
                Result.Error("Server returned: ${response.code()}")
            }
        } catch (e: Exception) {
            Result.Error("Connection failed: ${e.message}", e)
        }
    }
}
