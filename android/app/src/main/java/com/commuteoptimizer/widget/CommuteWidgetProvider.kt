package com.commuteoptimizer.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.util.Log
import android.widget.RemoteViews
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.commuteoptimizer.widget.data.CommuteRepository
import com.commuteoptimizer.widget.data.Result
import com.commuteoptimizer.widget.data.models.CommuteOption
import com.commuteoptimizer.widget.data.models.CommuteResponse
import com.commuteoptimizer.widget.util.MtaColors
import com.commuteoptimizer.widget.util.WidgetPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class CommuteWidgetProvider : AppWidgetProvider() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        private const val TAG = "CommuteWidget"
        const val ACTION_REFRESH = "com.commuteoptimizer.widget.ACTION_REFRESH"
        private const val WORK_NAME = "commute_widget_update"

        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, CommuteWidgetProvider::class.java)
            val widgetIds = appWidgetManager.getAppWidgetIds(componentName)

            val intent = Intent(context, CommuteWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, widgetIds)
            }
            context.sendBroadcast(intent)
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        Log.d(TAG, "onUpdate called with ${appWidgetIds.size} widgets: ${appWidgetIds.toList()}")
        for (widgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, widgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "onReceive: action=${intent.action}")
        super.onReceive(context, intent)

        if (intent.action == ACTION_REFRESH) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, CommuteWidgetProvider::class.java)
            val widgetIds = appWidgetManager.getAppWidgetIds(componentName)
            Log.d(TAG, "ACTION_REFRESH for widgets: ${widgetIds.toList()}")

            for (widgetId in widgetIds) {
                // Show loading state
                showLoadingState(context, appWidgetManager, widgetId)
                // Then update
                updateWidget(context, appWidgetManager, widgetId)
            }
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        schedulePeriodicUpdates(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        val prefs = WidgetPreferences(context)
        for (widgetId in appWidgetIds) {
            prefs.clearWidgetData(widgetId)
        }
    }

    private fun schedulePeriodicUpdates(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest = PeriodicWorkRequestBuilder<CommuteUpdateWorker>(
            15, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }

    private fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        widgetId: Int
    ) {
        Log.d(TAG, "updateWidget called for widgetId: $widgetId")
        val prefs = WidgetPreferences(context)
        val repository = CommuteRepository(context)

        // Check if widget is configured
        if (!repository.isConfigured()) {
            Log.d(TAG, "Widget $widgetId not configured, showing error")
            val views = buildErrorViews(context, "Tap to configure widget", widgetId)
            appWidgetManager.updateAppWidget(widgetId, views)
            return
        }

        Log.d(TAG, "Widget $widgetId is configured, fetching commute options")
        scope.launch {
            try {
                when (val result = repository.getCommuteOptions(widgetId)) {
                    is Result.Success -> {
                        Log.d(TAG, "Widget $widgetId got ${result.data.options.size} options")
                        prefs.setLastUpdate(widgetId, System.currentTimeMillis())
                        try {
                            val views = buildWidgetViews(context, result.data, widgetId)
                            Log.d(TAG, "Widget $widgetId calling updateAppWidget")
                            appWidgetManager.updateAppWidget(widgetId, views)
                            Log.d(TAG, "Widget $widgetId updateAppWidget completed")
                        } catch (e: Exception) {
                            Log.e(TAG, "Widget $widgetId failed to build/update views: ${e.message}", e)
                            val errorViews = buildErrorViews(context, "View error: ${e.message}", widgetId)
                            appWidgetManager.updateAppWidget(widgetId, errorViews)
                        }
                    }
                    is Result.Error -> {
                        Log.e(TAG, "Widget $widgetId error: ${result.message}")
                        val views = buildErrorViews(context, result.message, widgetId)
                        appWidgetManager.updateAppWidget(widgetId, views)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Widget $widgetId exception: ${e.message}", e)
                val views = buildErrorViews(context, "Error: ${e.message}", widgetId)
                appWidgetManager.updateAppWidget(widgetId, views)
            }
        }
    }

    private fun showLoadingState(
        context: Context,
        appWidgetManager: AppWidgetManager,
        widgetId: Int
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_loading)
        appWidgetManager.updateAppWidget(widgetId, views)
    }

    private fun buildWidgetViews(
        context: Context,
        data: CommuteResponse,
        widgetId: Int
    ): RemoteViews {
        Log.d(TAG, "buildWidgetViews started for widget $widgetId")
        val views = RemoteViews(context.packageName, R.layout.widget_commute)
        Log.d(TAG, "RemoteViews created")

        // Set origin and destination names
        val prefs = WidgetPreferences(context)
        val originName = prefs.getWidgetOriginName(widgetId)
        val destName = prefs.getWidgetDestinationName(widgetId)
        views.setTextViewText(R.id.widget_origin, originName)
        views.setTextViewText(R.id.widget_destination, destName)

        // Set weather
        val weather = data.weather
        val weatherEmoji = MtaColors.getWeatherEmoji(weather.conditions, weather.precipitationType)
        val tempDisplay = weather.tempF?.let { "$it°F" } ?: "--"
        val weatherText = "$tempDisplay $weatherEmoji"
        views.setTextViewText(R.id.widget_weather, weatherText)
        views.setTextColor(
            R.id.widget_weather,
            if (weather.isBad) Color.parseColor("#F44336") else Color.parseColor("#757575")
        )

        // Show alert indicator if there are alerts
        if (data.alerts.isNotEmpty()) {
            views.setViewVisibility(R.id.alert_indicator, android.view.View.VISIBLE)
        } else {
            views.setViewVisibility(R.id.alert_indicator, android.view.View.GONE)
        }

        // Set refresh button click action
        val refreshIntent = Intent(context, CommuteWidgetProvider::class.java).apply {
            action = ACTION_REFRESH
        }
        val refreshPendingIntent = PendingIntent.getBroadcast(
            context,
            widgetId,
            refreshIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.btn_refresh, refreshPendingIntent)

        // Set options
        val options = data.options.take(3)

        // Option 1
        if (options.size > 0) {
            bindOption1(views, options[0])
        } else {
            views.setViewVisibility(R.id.option_1, android.view.View.GONE)
        }

        // Option 2
        if (options.size > 1) {
            bindOption2(views, options[1])
        } else {
            views.setViewVisibility(R.id.option_2, android.view.View.GONE)
        }

        // Option 3
        if (options.size > 2) {
            bindOption3(views, options[2])
        } else {
            views.setViewVisibility(R.id.option_3, android.view.View.GONE)
        }

        // Set last updated time
        val lastUpdate = prefs.getLastUpdate(widgetId)
        val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
        val updateText = "Updated ${timeFormat.format(Date(lastUpdate))}"
        views.setTextViewText(R.id.widget_updated, updateText)

        Log.d(TAG, "buildWidgetViews completed for widget $widgetId")
        return views
    }

    private fun bindOption1(views: RemoteViews, option: CommuteOption) {
        bindOptionViews(views, option,
            R.id.option_1_rank, R.id.option_1_mode_icon,
            R.id.option_1_line1, R.id.option_1_line2,
            R.id.option_1_arrival, R.id.option_1_duration, R.id.option_1_next_train)
    }

    private fun bindOption2(views: RemoteViews, option: CommuteOption) {
        bindOptionViews(views, option,
            R.id.option_2_rank, R.id.option_2_mode_icon,
            R.id.option_2_line1, R.id.option_2_line2,
            R.id.option_2_arrival, R.id.option_2_duration, R.id.option_2_next_train)
    }

    private fun bindOption3(views: RemoteViews, option: CommuteOption) {
        bindOptionViews(views, option,
            R.id.option_3_rank, R.id.option_3_mode_icon,
            R.id.option_3_line1, R.id.option_3_line2,
            R.id.option_3_arrival, R.id.option_3_duration, R.id.option_3_next_train)
    }

    private fun bindOptionViews(
        views: RemoteViews,
        option: CommuteOption,
        rankId: Int,
        modeIconId: Int,
        line1Id: Int,
        line2Id: Int,
        arrivalId: Int,
        durationId: Int,
        nextTrainId: Int
    ) {
        // Set rank badge
        views.setTextViewText(rankId, option.rank.toString())
        val rankColor = when (option.rank) {
            1 -> Color.parseColor("#FFD700")
            2 -> Color.parseColor("#C0C0C0")
            3 -> Color.parseColor("#CD7F32")
            else -> Color.parseColor("#1976D2")
        }
        views.setInt(rankId, "setBackgroundColor", rankColor)

        // Set mode icon based on first leg
        val firstLeg = option.legs.firstOrNull()
        val modeIconRes = when (firstLeg?.mode) {
            "bike" -> R.drawable.ic_bike
            "walk" -> R.drawable.ic_walk
            else -> R.drawable.ic_subway
        }
        views.setImageViewResource(modeIconId, modeIconRes)

        // Get unique subway lines from all legs (like iOS)
        val subwayLines = option.legs
            .filter { it.mode == "subway" && !it.route.isNullOrEmpty() }
            .mapNotNull { it.route }
            .distinct()
            .take(2)

        // Set line badges
        if (subwayLines.isNotEmpty()) {
            val line1 = MtaColors.cleanExpressLine(subwayLines[0])
            views.setTextViewText(line1Id, line1)
            views.setInt(line1Id, "setBackgroundColor", MtaColors.getLineColor(line1))
            views.setTextColor(line1Id, MtaColors.getTextColorForLine(line1))
            views.setViewVisibility(line1Id, android.view.View.VISIBLE)
        } else {
            views.setViewVisibility(line1Id, android.view.View.GONE)
        }

        if (subwayLines.size > 1) {
            val line2 = MtaColors.cleanExpressLine(subwayLines[1])
            views.setTextViewText(line2Id, line2)
            views.setInt(line2Id, "setBackgroundColor", MtaColors.getLineColor(line2))
            views.setTextColor(line2Id, MtaColors.getTextColorForLine(line2))
            views.setViewVisibility(line2Id, android.view.View.VISIBLE)
        } else {
            views.setViewVisibility(line2Id, android.view.View.GONE)
        }

        // Set arrival time
        views.setTextViewText(arrivalId, "Arrive: ${option.arrivalTime}")

        // Set duration
        views.setTextViewText(durationId, "${option.durationMinutes}m")

        // Set next train time (plain text, not colored badge)
        views.setTextViewText(nextTrainId, option.nextTrain)
    }

    private fun buildErrorViews(
        context: Context,
        errorMessage: String,
        widgetId: Int
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_error)
        views.setTextViewText(R.id.error_message, errorMessage)

        // Set tap to refresh
        val refreshIntent = Intent(context, CommuteWidgetProvider::class.java).apply {
            action = ACTION_REFRESH
        }
        val refreshPendingIntent = PendingIntent.getBroadcast(
            context,
            widgetId,
            refreshIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_error_container, refreshPendingIntent)

        return views
    }
}
