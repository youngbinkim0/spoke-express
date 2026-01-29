package com.commuteoptimizer.widget.data

import android.content.Context
import com.commuteoptimizer.widget.data.models.CommuteResponse
import com.commuteoptimizer.widget.service.CommuteCalculator
import com.commuteoptimizer.widget.util.WidgetPreferences

sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val message: String, val exception: Exception? = null) : Result<Nothing>()
}

class CommuteRepository(context: Context) {

    private val calculator = CommuteCalculator(context)
    private val prefs = WidgetPreferences(context)

    suspend fun getCommuteOptions(): Result<CommuteResponse> {
        return calculator.calculateCommute()
    }

    fun isConfigured(): Boolean {
        return prefs.isConfigured()
    }
}
