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
import java.util.*

class CommuteWidgetConfigActivity : AppCompatActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    private lateinit var inputApiKey: TextInputEditText
    private lateinit var inputGoogleApiKey: TextInputEditText
    private lateinit var inputWorkerUrl: TextInputEditText
    private lateinit var inputHomeAddress: TextInputEditText
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

    private lateinit var prefs: WidgetPreferences
    private lateinit var localDataSource: LocalDataSource
    private lateinit var stations: List<LocalStation>

    private var homeLat: Double = 0.0
    private var homeLng: Double = 0.0
    private var workLat: Double = 0.0
    private var workLng: Double = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set result to CANCELED in case the user backs out
        setResult(RESULT_CANCELED)

        setContentView(R.layout.activity_config)

        prefs = WidgetPreferences(this)
        localDataSource = LocalDataSource(this)
        stations = localDataSource.getStations()

        // Find the widget ID from the intent
        appWidgetId = intent.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        // If the widget ID is invalid, finish
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        initViews()
        loadExistingSettings()
        setupStationChips()
        setupClickListeners()
    }

    private fun initViews() {
        inputApiKey = findViewById(R.id.input_api_key)
        inputGoogleApiKey = findViewById(R.id.input_google_api_key)
        inputWorkerUrl = findViewById(R.id.input_worker_url)
        inputHomeAddress = findViewById(R.id.input_home_address)
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
    }

    private fun loadExistingSettings() {
        // Load API keys
        prefs.getOpenWeatherApiKey()?.let { inputApiKey.setText(it) }
        prefs.getGoogleApiKey()?.let { inputGoogleApiKey.setText(it) }
        prefs.getWorkerUrl()?.let { inputWorkerUrl.setText(it) }

        // Load home location
        homeLat = prefs.getHomeLat()
        homeLng = prefs.getHomeLng()
        prefs.getHomeAddress()?.let { inputHomeAddress.setText(it) }
        if (homeLat != 0.0) {
            textHomeCoords.text = "%.4f, %.4f".format(homeLat, homeLng)
        }

        // Load work location
        workLat = prefs.getWorkLat()
        workLng = prefs.getWorkLng()
        prefs.getWorkAddress()?.let { inputWorkAddress.setText(it) }
        if (workLat != 0.0) {
            textWorkCoords.text = "%.4f, %.4f".format(workLat, workLng)
        }

        // Load bike options preference
        switchBikeOptions.isChecked = prefs.getShowBikeOptions()
    }

    private fun setupStationChips() {
        val selectedStations = prefs.getBikeStations().toSet()

        // Sort stations by name for easier selection
        val sortedStations = stations.sortedBy { it.name }

        for (station in sortedStations) {
            val chip = Chip(this).apply {
                text = "${station.name} (${station.lines.joinToString(",")})"
                isCheckable = true
                isChecked = selectedStations.contains(station.id)
                tag = station.id

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
                }
            }
            chipGroupStations.addView(chip)
        }
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
        // Validate API key
        val apiKey = inputApiKey.text?.toString()?.trim()
        if (apiKey.isNullOrBlank()) {
            showStatus("Please enter an OpenWeatherMap API key", isError = true)
            return
        }

        // Validate home location
        if (homeLat == 0.0 || homeLng == 0.0) {
            showStatus("Please set your home location", isError = true)
            return
        }

        // Validate work location
        if (workLat == 0.0 || workLng == 0.0) {
            showStatus("Please set your work location", isError = true)
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
            showStatus("Please select at least one station", isError = true)
            return
        }

        // Get optional settings
        val googleApiKey = inputGoogleApiKey.text?.toString()?.trim()
        val workerUrl = inputWorkerUrl.text?.toString()?.trim()
        val showBikeOptions = switchBikeOptions.isChecked

        // Save all settings
        prefs.setOpenWeatherApiKey(apiKey)
        prefs.setHomeLocation(homeLat, homeLng, inputHomeAddress.text?.toString() ?: "")
        prefs.setWorkLocation(workLat, workLng, inputWorkAddress.text?.toString() ?: "")
        prefs.setBikeStations(selectedStations)
        prefs.setShowBikeOptions(showBikeOptions)

        // Save optional settings if provided
        if (!googleApiKey.isNullOrBlank()) {
            prefs.setGoogleApiKey(googleApiKey)
        }
        if (!workerUrl.isNullOrBlank()) {
            prefs.setWorkerUrl(workerUrl)
        }

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
        setResult(RESULT_OK, resultValue)
        finish()
    }

    private fun showStatus(message: String, isError: Boolean) {
        textStatus.visibility = View.VISIBLE
        textStatus.text = message
        textStatus.setTextColor(
            if (isError) Color.parseColor("#F44336") else Color.parseColor("#4CAF50")
        )
    }
}
