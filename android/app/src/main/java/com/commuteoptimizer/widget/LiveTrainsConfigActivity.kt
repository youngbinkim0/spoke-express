package com.commuteoptimizer.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.commuteoptimizer.widget.data.models.LocalStation
import com.commuteoptimizer.widget.service.LocalDataSource
import com.commuteoptimizer.widget.util.MtaColors
import com.commuteoptimizer.widget.util.WidgetPreferences
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText

class LiveTrainsConfigActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "LiveTrainsConfig"
    }

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    private lateinit var scrollView: ScrollView
    private lateinit var inputSearch: TextInputEditText
    private lateinit var chipGroupStations: ChipGroup
    private lateinit var btnSave: Button
    private lateinit var btnCancel: Button
    private lateinit var textStatus: TextView

    private lateinit var prefs: WidgetPreferences
    private lateinit var localDataSource: LocalDataSource
    private lateinit var stations: List<LocalStation>

    private var selectedStationId: String? = null
    private var currentSearchQuery: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate started")

        // Set result to CANCELED in case the user backs out
        setResult(RESULT_CANCELED)

        setContentView(R.layout.activity_live_trains_config)

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
            setupStationChips()
            setupClickListeners()
            Log.d(TAG, "onCreate completed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate: ${e.message}", e)
        }
    }

    private fun initViews() {
        scrollView = findViewById(R.id.scroll_view)
        inputSearch = findViewById(R.id.input_search)
        chipGroupStations = findViewById(R.id.chip_group_stations)
        btnSave = findViewById(R.id.btn_save)
        btnCancel = findViewById(R.id.btn_cancel)
        textStatus = findViewById(R.id.text_status)

        // Setup search functionality
        inputSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                currentSearchQuery = s?.toString()?.trim() ?: ""
                filterStations(currentSearchQuery)
            }
        })
    }

    private fun setupStationChips() {
        displayStations(stations, currentSearchQuery)
    }

    private fun filterStations(query: String) {
        displayStations(stations, query)
    }

    private fun displayStations(allStations: List<LocalStation>, query: String) {
        chipGroupStations.removeAllViews()

        // Load existing selection if editing
        val existingStation = prefs.getLiveTrainsWidgetStation(appWidgetId)

        // Filter and sort stations
        val filteredStations = if (query.isEmpty()) {
            allStations
        } else {
            allStations.filter { station ->
                station.name.contains(query, ignoreCase = true) ||
                station.lines.any { it.contains(query, ignoreCase = true) }
            }
        }.sortedBy { it.name }

        for (station in filteredStations) {
            val chip = Chip(this).apply {
                text = "${station.name} (${station.lines.joinToString(",")})"
                isCheckable = true
                isChecked = station.id == existingStation || station.id == selectedStationId
                tag = station.id
                setTextColor(Color.parseColor("#eeeeee"))

                // Color the chip based on primary line
                val lineColor = MtaColors.getLineColor(station.lines.firstOrNull() ?: "G")
                if (isChecked) {
                    selectedStationId = station.id
                    setChipBackgroundColorResource(android.R.color.transparent)
                    chipStrokeWidth = 2f
                    chipStrokeColor = android.content.res.ColorStateList.valueOf(lineColor)
                }

                setOnCheckedChangeListener { _, checked ->
                    if (checked) {
                        // Uncheck all other chips (single selection)
                        for (i in 0 until chipGroupStations.childCount) {
                            val otherChip = chipGroupStations.getChildAt(i) as? Chip
                            if (otherChip != this && otherChip?.isChecked == true) {
                                otherChip.isChecked = false
                            }
                        }
                        selectedStationId = station.id
                        setChipBackgroundColorResource(android.R.color.transparent)
                        chipStrokeWidth = 2f
                        chipStrokeColor = android.content.res.ColorStateList.valueOf(lineColor)

                        // Scroll to save button after selection
                        scrollToSaveButton()
                    } else {
                        if (selectedStationId == station.id) {
                            selectedStationId = null
                        }
                        chipStrokeWidth = 1f
                        chipStrokeColor = android.content.res.ColorStateList.valueOf(Color.LTGRAY)
                    }
                }
            }
            chipGroupStations.addView(chip)
        }
    }

    private fun scrollToSaveButton() {
        scrollView.post {
            scrollView.fullScroll(ScrollView.FOCUS_DOWN)
        }
    }

    private fun setupClickListeners() {
        btnSave.setOnClickListener {
            saveConfiguration()
        }

        btnCancel.setOnClickListener {
            finish()
        }
    }

    private fun saveConfiguration() {
        Log.d(TAG, "saveConfiguration called")

        if (selectedStationId == null) {
            Log.d(TAG, "Validation failed: no station selected")
            showStatus("Please select a station", isError = true)
            return
        }

        Log.d(TAG, "Saving station: $selectedStationId")

        try {
            // Save the station for this widget
            prefs.setLiveTrainsWidgetStation(appWidgetId, selectedStationId!!)

            Log.d(TAG, "Settings saved, triggering widget update for ID: $appWidgetId")

            // Trigger initial widget update
            val intent = Intent(this, LiveTrainsWidgetProvider::class.java).apply {
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
