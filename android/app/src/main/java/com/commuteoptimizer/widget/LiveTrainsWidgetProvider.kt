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

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        val prefs = WidgetPreferences(context)
        for (widgetId in appWidgetIds) {
            prefs.clearWidgetData(widgetId)
        }
    }

    private fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, widgetId: Int) {
        val prefs = WidgetPreferences(context)
        val localDataSource = LocalDataSource(context)

        // Get station for this specific widget
        val stationId = prefs.getLiveTrainsWidgetStation(widgetId)
        if (stationId == null) {
            val views = RemoteViews(context.packageName, R.layout.widget_error)
            views.setTextViewText(R.id.error_message, "Tap to configure")
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

                // Fetch arrivals for this widget's station
                val station = localDataSource.getStation(stationId)

                // Set station name as widget title
                val stationName = station?.name ?: "Unknown Station"
                views.setTextViewText(R.id.widget_title, stationName)

                val arrivals = MtaApiService.getGroupedArrivals(stationId, station?.lines ?: emptyList())

                // Bind each row with unique IDs
                if (arrivals.size > 0) {
                    bindTrainRow1(views, arrivals[0])
                } else {
                    views.setViewVisibility(R.id.train_1, View.GONE)
                }

                if (arrivals.size > 1) {
                    bindTrainRow2(views, arrivals[1])
                } else {
                    views.setViewVisibility(R.id.train_2, View.GONE)
                }

                if (arrivals.size > 2) {
                    bindTrainRow3(views, arrivals[2])
                } else {
                    views.setViewVisibility(R.id.train_3, View.GONE)
                }

                if (arrivals.size > 3) {
                    bindTrainRow4(views, arrivals[3])
                } else {
                    views.setViewVisibility(R.id.train_4, View.GONE)
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

    private fun bindTrainRow1(views: RemoteViews, group: MtaApiService.ArrivalGroup) {
        bindTrainRowWithIds(views, R.id.train_1, R.id.train_1_line_badge, R.id.train_1_direction,
            R.id.train_1_headsign, R.id.train_1_arrival_1, R.id.train_1_arrival_2, group)
    }

    private fun bindTrainRow2(views: RemoteViews, group: MtaApiService.ArrivalGroup) {
        bindTrainRowWithIds(views, R.id.train_2, R.id.train_2_line_badge, R.id.train_2_direction,
            R.id.train_2_headsign, R.id.train_2_arrival_1, R.id.train_2_arrival_2, group)
    }

    private fun bindTrainRow3(views: RemoteViews, group: MtaApiService.ArrivalGroup) {
        bindTrainRowWithIds(views, R.id.train_3, R.id.train_3_line_badge, R.id.train_3_direction,
            R.id.train_3_headsign, R.id.train_3_arrival_1, R.id.train_3_arrival_2, group)
    }

    private fun bindTrainRow4(views: RemoteViews, group: MtaApiService.ArrivalGroup) {
        bindTrainRowWithIds(views, R.id.train_4, R.id.train_4_line_badge, R.id.train_4_direction,
            R.id.train_4_headsign, R.id.train_4_arrival_1, R.id.train_4_arrival_2, group)
    }

    private fun bindTrainRowWithIds(
        views: RemoteViews,
        rowId: Int,
        lineBadgeId: Int,
        directionId: Int,
        headsignId: Int,
        arrival1Id: Int,
        arrival2Id: Int,
        group: MtaApiService.ArrivalGroup
    ) {
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
