package com.commuteoptimizer.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.View
import android.widget.RemoteViews
import com.commuteoptimizer.widget.data.api.MtaApiService
import com.commuteoptimizer.widget.service.LocalDataSource
import com.commuteoptimizer.widget.util.MtaColors
import com.commuteoptimizer.widget.util.WidgetPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LiveTrainsWidgetProvider : AppWidgetProvider() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        const val ACTION_REFRESH = "com.commuteoptimizer.widget.ACTION_REFRESH_TRAINS"
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (widgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, widgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, LiveTrainsWidgetProvider::class.java)
            val widgetIds = appWidgetManager.getAppWidgetIds(componentName)
            for (widgetId in widgetIds) {
                updateWidget(context, appWidgetManager, widgetId)
            }
        }
    }

    private fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, widgetId: Int) {
        val prefs = WidgetPreferences(context)
        val localDataSource = LocalDataSource(context)

        val liveStations = prefs.getSelectedStations()
        if (liveStations.isEmpty()) {
            val views = RemoteViews(context.packageName, R.layout.widget_error)
            views.setTextViewText(R.id.error_message, "No stations configured")
            appWidgetManager.updateAppWidget(widgetId, views)
            return
        }

        scope.launch {
            try {
                val views = RemoteViews(context.packageName, R.layout.widget_live_trains)

                // Set refresh click
                val refreshIntent = Intent(context, LiveTrainsWidgetProvider::class.java).apply {
                    action = ACTION_REFRESH
                }
                val refreshPendingIntent = PendingIntent.getBroadcast(
                    context, widgetId, refreshIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.btn_refresh, refreshPendingIntent)

                // Fetch arrivals for first station
                val stationId = liveStations.first()
                val station = localDataSource.getStation(stationId)
                val arrivals = MtaApiService.getGroupedArrivals(stationId, station?.lines ?: emptyList())

                val trainRowIds = listOf(R.id.train_1, R.id.train_2, R.id.train_3, R.id.train_4)

                arrivals.take(4).forEachIndexed { index, group ->
                    val rowId = trainRowIds[index]
                    bindTrainRow(context, views, rowId, group)
                }

                // Hide unused rows
                for (i in arrivals.size until 4) {
                    views.setViewVisibility(trainRowIds[i], View.GONE)
                }

                // Set update time
                val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
                views.setTextViewText(R.id.widget_updated, "Updated ${timeFormat.format(Date())}")

                appWidgetManager.updateAppWidget(widgetId, views)
            } catch (e: Exception) {
                val views = RemoteViews(context.packageName, R.layout.widget_error)
                views.setTextViewText(R.id.error_message, "Failed to load")
                appWidgetManager.updateAppWidget(widgetId, views)
            }
        }
    }

    private fun bindTrainRow(context: Context, views: RemoteViews, rowId: Int, group: MtaApiService.ArrivalGroup) {
        val lineBadgeId = context.resources.getIdentifier("line_badge", "id", context.packageName)
        val directionId = context.resources.getIdentifier("direction_arrow", "id", context.packageName)
        val headsignId = context.resources.getIdentifier("headsign", "id", context.packageName)
        val arrival1Id = context.resources.getIdentifier("arrival_1", "id", context.packageName)
        val arrival2Id = context.resources.getIdentifier("arrival_2", "id", context.packageName)

        views.setViewVisibility(rowId, View.VISIBLE)
        views.setTextViewText(lineBadgeId, group.line)
        views.setInt(lineBadgeId, "setBackgroundColor", MtaColors.getLineColor(group.line))
        views.setTextColor(lineBadgeId, MtaColors.getTextColorForLine(group.line))

        val arrow = when (group.direction) {
            "N" -> "↑"
            "S" -> "↓"
            else -> "→"
        }
        views.setTextViewText(directionId, arrow)
        views.setTextViewText(headsignId, group.headsign)

        if (group.arrivals.isNotEmpty()) {
            val first = group.arrivals[0]
            val text1 = if (first.minutesAway == 0) "Now" else "${first.minutesAway}m"
            views.setTextViewText(arrival1Id, text1)
            views.setViewVisibility(arrival1Id, View.VISIBLE)
            if (first.minutesAway <= 2) {
                views.setInt(arrival1Id, "setBackgroundResource", R.drawable.arrival_badge_soon)
                views.setTextColor(arrival1Id, Color.parseColor("#1a1a2e"))
            }
        } else {
            views.setViewVisibility(arrival1Id, View.GONE)
        }

        if (group.arrivals.size > 1) {
            val second = group.arrivals[1]
            views.setTextViewText(arrival2Id, "${second.minutesAway}m")
            views.setViewVisibility(arrival2Id, View.VISIBLE)
        } else {
            views.setViewVisibility(arrival2Id, View.GONE)
        }
    }
}
