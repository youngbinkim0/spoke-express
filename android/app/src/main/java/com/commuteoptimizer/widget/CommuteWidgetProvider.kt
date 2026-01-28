package com.commuteoptimizer.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
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
        for (widgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, widgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        if (intent.action == ACTION_REFRESH) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, CommuteWidgetProvider::class.java)
            val widgetIds = appWidgetManager.getAppWidgetIds(componentName)

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
        val prefs = WidgetPreferences(context)
        val apiUrl = prefs.getApiUrl(widgetId)

        scope.launch {
            val repository = CommuteRepository(apiUrl)
            when (val result = repository.getCommuteOptions()) {
                is Result.Success -> {
                    prefs.setLastUpdate(widgetId, System.currentTimeMillis())
                    val views = buildWidgetViews(context, result.data, widgetId)
                    appWidgetManager.updateAppWidget(widgetId, views)
                }
                is Result.Error -> {
                    val views = buildErrorViews(context, result.message, widgetId)
                    appWidgetManager.updateAppWidget(widgetId, views)
                }
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
        val views = RemoteViews(context.packageName, R.layout.widget_commute)

        // Set weather
        val weather = data.weather
        val weatherEmoji = MtaColors.getWeatherEmoji(weather.conditions, weather.precipitationType)
        val weatherText = "${weather.tempF}°F $weatherEmoji"
        views.setTextViewText(R.id.widget_weather, weatherText)
        views.setTextColor(
            R.id.widget_weather,
            if (weather.isBad) Color.parseColor("#F44336") else Color.parseColor("#757575")
        )

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
        val optionViewIds = listOf(
            Triple(R.id.option_1, "option_1", 0),
            Triple(R.id.option_2, "option_2", 1),
            Triple(R.id.option_3, "option_3", 2)
        )

        for ((containerResName, _, index) in optionViewIds) {
            if (index < options.size) {
                bindOptionRow(context, views, containerResName, options[index])
            } else {
                // Hide unused option rows
                views.setViewVisibility(containerResName, android.view.View.GONE)
            }
        }

        // Set last updated time
        val prefs = WidgetPreferences(context)
        val lastUpdate = prefs.getLastUpdate(widgetId)
        val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
        val updateText = "Updated ${timeFormat.format(Date(lastUpdate))}"
        views.setTextViewText(R.id.widget_updated, updateText)

        return views
    }

    private fun bindOptionRow(
        context: Context,
        views: RemoteViews,
        containerResId: Int,
        option: CommuteOption
    ) {
        // Get the view IDs within the included layout
        val rankId = getNestedViewId(context, containerResId, "option_rank")
        val modeIconId = getNestedViewId(context, containerResId, "option_mode_icon")
        val summaryId = getNestedViewId(context, containerResId, "option_summary")
        val durationId = getNestedViewId(context, containerResId, "option_duration")
        val nextTrainId = getNestedViewId(context, containerResId, "option_next_train")

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

        // Set summary (simplified route description)
        views.setTextViewText(summaryId, option.summary)

        // Set duration
        views.setTextViewText(durationId, "${option.durationMinutes} min")

        // Set next train time
        views.setTextViewText(nextTrainId, option.nextTrain)

        // Color the next train badge based on the subway line
        val subwayLeg = option.legs.find { it.mode == "subway" }
        val lineColor = subwayLeg?.route?.let { MtaColors.getLineColor(it) }
            ?: Color.parseColor("#6CBE45")
        views.setInt(nextTrainId, "setBackgroundColor", lineColor)

        // Set text color based on line
        val textColor = subwayLeg?.route?.let { MtaColors.getTextColorForLine(it) }
            ?: Color.WHITE
        views.setTextColor(nextTrainId, textColor)
    }

    private fun getNestedViewId(context: Context, containerId: Int, viewName: String): Int {
        // For included layouts, we access views directly by their IDs
        return context.resources.getIdentifier(viewName, "id", context.packageName)
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
