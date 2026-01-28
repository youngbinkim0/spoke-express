package com.commuteoptimizer.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class CommuteUpdateWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            // Trigger widget update
            CommuteWidgetProvider.updateAllWidgets(applicationContext)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
