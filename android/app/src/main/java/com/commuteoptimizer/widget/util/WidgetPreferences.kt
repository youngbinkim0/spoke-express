package com.commuteoptimizer.widget.util

import android.content.Context
import android.content.SharedPreferences

class WidgetPreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    companion object {
        private const val PREFS_NAME = "commute_widget_prefs"
        private const val KEY_API_URL = "api_url_"
        private const val KEY_LAST_UPDATE = "last_update_"

        const val DEFAULT_API_URL = "http://192.168.1.100:8888"
    }

    fun getApiUrl(widgetId: Int): String {
        return prefs.getString(KEY_API_URL + widgetId, DEFAULT_API_URL) ?: DEFAULT_API_URL
    }

    fun setApiUrl(widgetId: Int, url: String) {
        prefs.edit().putString(KEY_API_URL + widgetId, url).apply()
    }

    fun getLastUpdate(widgetId: Int): Long {
        return prefs.getLong(KEY_LAST_UPDATE + widgetId, 0)
    }

    fun setLastUpdate(widgetId: Int, timestamp: Long) {
        prefs.edit().putLong(KEY_LAST_UPDATE + widgetId, timestamp).apply()
    }

    fun clearWidgetData(widgetId: Int) {
        prefs.edit()
            .remove(KEY_API_URL + widgetId)
            .remove(KEY_LAST_UPDATE + widgetId)
            .apply()
    }

    fun getAllWidgetIds(): Set<Int> {
        return prefs.all.keys
            .filter { it.startsWith(KEY_API_URL) }
            .mapNotNull { it.removePrefix(KEY_API_URL).toIntOrNull() }
            .toSet()
    }
}
