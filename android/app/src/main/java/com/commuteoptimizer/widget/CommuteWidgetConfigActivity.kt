package com.commuteoptimizer.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Color
import android.location.Geocoder
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.lifecycle.lifecycleScope
import com.commuteoptimizer.widget.data.models.LocalStation
import com.commuteoptimizer.widget.service.LocalDataSource
import com.commuteoptimizer.widget.util.MtaColors
import com.commuteoptimizer.widget.util.WidgetPreferences
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.util.Log
import java.util.*
import kotlin.math.*

class CommuteWidgetConfigActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "WidgetConfig"
    }

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    private lateinit var textApiStatus: TextView
    private lateinit var inputOriginName: TextInputEditText
    private lateinit var inputHomeAddress: TextInputEditText
    private lateinit var inputDestName: TextInputEditText
    private lateinit var inputWorkAddress: TextInputEditText
    private lateinit var textHomeCoords: TextView
    private lateinit var textWorkCoords: TextView
    private lateinit var btnGeocodeHome: Button
    private lateinit var btnGeocodeWork: Button
    private lateinit var chipGroupStations: ChipGroup
    private lateinit var switchBikeOptions: SwitchCompat
    private lateinit var btnSave: Button
    private lateinit var btnCancel: Button
    private lateinit var textStatus: TextView
    private lateinit var stationsHeader: View
    private lateinit var stationsExpandIcon: ImageView
    private lateinit var textStationCount: TextView

    private lateinit var prefs: WidgetPreferences
    private lateinit var localDataSource: LocalDataSource
    private lateinit var stations: List<LocalStation>

    private var stationsExpanded = false
    private var homeLat: Double = 0.0
    private var homeLng: Double = 0.0
    private var workLat: Double = 0.0
    private var workLng: Double = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate started")

        // Set result to CANCELED in case the user backs out
        setResult(RESULT_CANCELED)

        setContentView(R.layout.activity_config)

        prefs = WidgetPreferences(this)
        localDataSource = LocalDataSource(this)
        stations = localDataSource.getStations()
        Log.d(TAG, "Loaded ${stations.size} stations")

        // Find the widget ID from the intent
        appWidgetId = intent.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        Log.d(TAG, "Widget ID: $appWidgetId")

        // If the widget ID is invalid, finish
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            Log.e(TAG, "Invalid widget ID, finishing")
            finish()
            return
        }

        try {
            initViews()
            loadExistingSettings()
            setupStationChips()
            setupClickListeners()
            Log.d(TAG, "onCreate completed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate: ${e.message}", e)
        }
    }

    private fun initViews() {
        textApiStatus = findViewById(R.id.text_api_status)
        inputOriginName = findViewById(R.id.input_origin_name)
        inputHomeAddress = findViewById(R.id.input_home_address)
        inputDestName = findViewById(R.id.input_dest_name)
        inputWorkAddress = findViewById(R.id.input_work_address)
        textHomeCoords = findViewById(R.id.text_home_coords)
        textWorkCoords = findViewById(R.id.text_work_coords)
        btnGeocodeHome = findViewById(R.id.btn_geocode_home)
        btnGeocodeWork = findViewById(R.id.btn_geocode_work)
        chipGroupStations = findViewById(R.id.chip_group_stations)
        switchBikeOptions = findViewById(R.id.switch_bike_options)
        btnSave = findViewById(R.id.btn_save)
        btnCancel = findViewById(R.id.btn_cancel)
        textStatus = findViewById(R.id.text_status)
        stationsHeader = findViewById(R.id.stations_header)
        stationsExpandIcon = findViewById(R.id.stations_expand_icon)
        textStationCount = findViewById(R.id.text_station_count)

        // Set up collapsible station section
        stationsHeader.setOnClickListener {
            stationsExpanded = !stationsExpanded
            chipGroupStations.visibility = if (stationsExpanded) View.VISIBLE else View.GONE
            stationsExpandIcon.rotation = if (stationsExpanded) 180f else 0f
        }
    }

    private fun loadExistingSettings() {
        // Show API key status (keys are managed in main app Settings)
        val hasWeatherKey = !prefs.getOpenWeatherApiKey().isNullOrBlank()
        val hasGoogleKey = !prefs.getGoogleApiKey().isNullOrBlank()
        val statusText = when {
            hasWeatherKey && hasGoogleKey -> "API keys: ✓ Configured in Settings"
            hasWeatherKey -> "API keys: Weather ✓, Google ✗ (set in Settings)"
            hasGoogleKey -> "API keys: Weather ✗, Google ✓ (set in Settings)"
            else -> "API keys: Not configured (set in main app Settings)"
        }
        textApiStatus.text = statusText

        // Load origin (per-widget, falls back to global home location)
        if (prefs.hasWidgetOrigin(appWidgetId)) {
            // Load widget-specific origin
            homeLat = prefs.getWidgetOriginLat(appWidgetId)
            homeLng = prefs.getWidgetOriginLng(appWidgetId)
            inputOriginName.setText(prefs.getWidgetOriginName(appWidgetId))
        } else {
            // Fall back to global home location for new widgets
            homeLat = prefs.getHomeLat()
            homeLng = prefs.getHomeLng()
            prefs.getHomeAddress()?.let { inputHomeAddress.setText(it) }
            inputOriginName.setText("Home")
        }
        if (homeLat != 0.0) {
            textHomeCoords.text = "%.4f, %.4f".format(homeLat, homeLng)
        }

        // Load destination (per-widget, falls back to global work location)
        if (prefs.hasWidgetDestination(appWidgetId)) {
            // Load widget-specific destination
            workLat = prefs.getWidgetDestinationLat(appWidgetId)
            workLng = prefs.getWidgetDestinationLng(appWidgetId)
            inputDestName.setText(prefs.getWidgetDestinationName(appWidgetId))
        } else {
            // Fall back to global work location for new widgets
            workLat = prefs.getWorkLat()
            workLng = prefs.getWorkLng()
            prefs.getWorkAddress()?.let { inputWorkAddress.setText(it) }
            inputDestName.setText("Work")
        }
        if (workLat != 0.0) {
            textWorkCoords.text = "%.4f, %.4f".format(workLat, workLng)
        }

        // Load bike options preference
        switchBikeOptions.isChecked = prefs.getShowBikeOptions()
    }

    private fun setupStationChips() {
        chipGroupStations.removeAllViews()
        val selectedStations = prefs.getBikeStations().toSet()

        // Sort by distance from home if home is set, otherwise alphabetically
        val sortedStations = if (homeLat != 0.0 && homeLng != 0.0) {
            stations.sortedBy { calculateDistance(homeLat, homeLng, it.lat, it.lng) }
        } else {
            stations.sortedBy { it.name }
        }

        for (station in sortedStations) {
            val distance = if (homeLat != 0.0 && homeLng != 0.0) {
                calculateDistance(homeLat, homeLng, station.lat, station.lng)
            } else null

            val distanceText = distance?.let { " - %.1f mi".format(it) } ?: ""

            val chip = Chip(this).apply {
                text = "${station.name} (${station.lines.joinToString(",")})$distanceText"
                isCheckable = true
                isChecked = selectedStations.contains(station.id)
                tag = station.id
                setTextColor(Color.parseColor("#eeeeee"))

                // Color the chip based on primary line
                val lineColor = MtaColors.getLineColor(station.lines.firstOrNull() ?: "G")
                if (isChecked) {
                    setChipBackgroundColorResource(android.R.color.transparent)
                    chipStrokeWidth = 2f
                    chipStrokeColor = android.content.res.ColorStateList.valueOf(lineColor)
                }

                setOnCheckedChangeListener { _, checked ->
                    if (checked) {
                        setChipBackgroundColorResource(android.R.color.transparent)
                        chipStrokeWidth = 2f
                        chipStrokeColor = android.content.res.ColorStateList.valueOf(lineColor)
                    } else {
                        chipStrokeWidth = 1f
                        chipStrokeColor = android.content.res.ColorStateList.valueOf(Color.LTGRAY)
                    }
                    updateStationCount()
                }
            }
            chipGroupStations.addView(chip)
        }
        updateStationCount()
    }

    private fun updateStationCount() {
        var count = 0
        for (i in 0 until chipGroupStations.childCount) {
            val chip = chipGroupStations.getChildAt(i) as? Chip
            if (chip?.isChecked == true) count++
        }
        textStationCount.text = "$count selected"
    }

    private fun calculateDistance(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val earthRadiusMiles = 3959.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLng / 2) * sin(dLng / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadiusMiles * c
    }

    private fun setupClickListeners() {
        btnGeocodeHome.setOnClickListener {
            geocodeAddress(inputHomeAddress.text?.toString(), isHome = true)
        }

        btnGeocodeWork.setOnClickListener {
            geocodeAddress(inputWorkAddress.text?.toString(), isHome = false)
        }

        btnSave.setOnClickListener {
            saveConfiguration()
        }

        btnCancel.setOnClickListener {
            finish()
        }
    }

    private fun geocodeAddress(address: String?, isHome: Boolean) {
        if (address.isNullOrBlank()) {
            showStatus("Please enter an address", isError = true)
            return
        }

        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val geocoder = Geocoder(this@CommuteWidgetConfigActivity, Locale.getDefault())
                    @Suppress("DEPRECATION")
                    val addresses = geocoder.getFromLocationName(address, 1)
                    addresses?.firstOrNull()
                }

                if (result != null) {
                    if (isHome) {
                        homeLat = result.latitude
                        homeLng = result.longitude
                        textHomeCoords.text = "%.4f, %.4f".format(homeLat, homeLng)
                        // Re-sort stations by distance from new home location
                        setupStationChips()
                    } else {
                        workLat = result.latitude
                        workLng = result.longitude
                        textWorkCoords.text = "%.4f, %.4f".format(workLat, workLng)
                    }
                    showStatus("Location found!", isError = false)
                } else {
                    showStatus("Could not find address", isError = true)
                }
            } catch (e: Exception) {
                showStatus("Geocoding failed: ${e.message}", isError = true)
            }
        }
    }

    private fun saveConfiguration() {
        Log.d(TAG, "saveConfiguration called")

        // API keys are managed in main app Settings, not here

        // Validate origin location (required)
        if (homeLat == 0.0 || homeLng == 0.0) {
            Log.d(TAG, "Validation failed: no origin location")
            showStatus("Please set your origin location", isError = true)
            return
        }

        // Validate origin name
        val originName = inputOriginName.text?.toString()?.trim()
        if (originName.isNullOrEmpty()) {
            Log.d(TAG, "Validation failed: no origin name")
            showStatus("Please enter an origin name", isError = true)
            return
        }

        // Validate destination location (required)
        if (workLat == 0.0 || workLng == 0.0) {
            Log.d(TAG, "Validation failed: no destination location")
            showStatus("Please set your destination location", isError = true)
            return
        }

        // Validate destination name
        val destName = inputDestName.text?.toString()?.trim()
        if (destName.isNullOrEmpty()) {
            Log.d(TAG, "Validation failed: no destination name")
            showStatus("Please enter a destination name", isError = true)
            return
        }

        // Get selected stations
        val selectedStations = mutableListOf<String>()
        for (i in 0 until chipGroupStations.childCount) {
            val chip = chipGroupStations.getChildAt(i) as? Chip
            if (chip?.isChecked == true) {
                selectedStations.add(chip.tag as String)
            }
        }

        if (selectedStations.isEmpty()) {
            Log.d(TAG, "Validation failed: no stations selected")
            showStatus("Please select at least one station", isError = true)
            return
        }

        Log.d(TAG, "Validation passed, saving ${selectedStations.size} stations")

        try {
            // Get optional settings
            val showBikeOptions = switchBikeOptions.isChecked

            // Save origin per widget (allows different origins per widget)
            prefs.setWidgetOrigin(appWidgetId, originName, homeLat, homeLng)
            // Also save as global home location for backwards compatibility
            prefs.setHomeLocation(homeLat, homeLng, inputHomeAddress.text?.toString() ?: "")
            // Save destination per widget (allows different destinations per widget)
            prefs.setWidgetDestination(appWidgetId, destName, workLat, workLng)
            // Also save as global work location for backwards compatibility
            prefs.setWorkLocation(workLat, workLng, inputWorkAddress.text?.toString() ?: "")
            prefs.setBikeStations(selectedStations)
            prefs.setShowBikeOptions(showBikeOptions)

            Log.d(TAG, "Settings saved, triggering widget update for ID: $appWidgetId")

            // Trigger initial widget update
            val intent = Intent(this, CommuteWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(appWidgetId))
            }
            sendBroadcast(intent)

            // Return success
            val resultValue = Intent().apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            Log.d(TAG, "Setting result OK and finishing")
            setResult(RESULT_OK, resultValue)
            finish()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving configuration: ${e.message}", e)
            showStatus("Error: ${e.message}", isError = true)
        }
    }

    private fun showStatus(message: String, isError: Boolean) {
        textStatus.visibility = View.VISIBLE
        textStatus.text = message
        textStatus.setTextColor(
            if (isError) Color.parseColor("#F44336") else Color.parseColor("#4CAF50")
        )
    }
}
