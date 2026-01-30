package com.commuteoptimizer.widget.ui

import android.graphics.Color
import android.location.Geocoder
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.commuteoptimizer.widget.R
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

class SettingsFragment : Fragment() {

    private lateinit var prefs: WidgetPreferences
    private lateinit var localDataSource: LocalDataSource
    private lateinit var stations: List<LocalStation>

    private lateinit var inputApiKey: TextInputEditText
    private lateinit var inputGoogleApiKey: TextInputEditText
    private lateinit var inputWorkerUrl: TextInputEditText
    private lateinit var inputHomeAddress: TextInputEditText
    private lateinit var inputWorkAddress: TextInputEditText
    private lateinit var textHomeCoords: TextView
    private lateinit var textWorkCoords: TextView
    private lateinit var chipGroupStations: ChipGroup
    private lateinit var switchBikeOptions: SwitchCompat
    private lateinit var textStatus: TextView

    private var homeLat: Double = 0.0
    private var homeLng: Double = 0.0
    private var workLat: Double = 0.0
    private var workLng: Double = 0.0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        prefs = WidgetPreferences(requireContext())
        localDataSource = LocalDataSource(requireContext())
        stations = localDataSource.getStations()

        initViews(view)
        loadExistingSettings()
        setupStationChips()
        setupClickListeners()
    }

    private fun initViews(view: View) {
        inputApiKey = view.findViewById(R.id.input_api_key)
        inputGoogleApiKey = view.findViewById(R.id.input_google_api_key)
        inputWorkerUrl = view.findViewById(R.id.input_worker_url)
        inputHomeAddress = view.findViewById(R.id.input_home_address)
        inputWorkAddress = view.findViewById(R.id.input_work_address)
        textHomeCoords = view.findViewById(R.id.text_home_coords)
        textWorkCoords = view.findViewById(R.id.text_work_coords)
        chipGroupStations = view.findViewById(R.id.chip_group_stations)
        switchBikeOptions = view.findViewById(R.id.switch_bike_options)
        textStatus = view.findViewById(R.id.text_status)
    }

    private fun loadExistingSettings() {
        prefs.getOpenWeatherApiKey()?.let { inputApiKey.setText(it) }
        prefs.getGoogleApiKey()?.let { inputGoogleApiKey.setText(it) }
        prefs.getWorkerUrl()?.let { inputWorkerUrl.setText(it) }

        homeLat = prefs.getHomeLat()
        homeLng = prefs.getHomeLng()
        prefs.getHomeAddress()?.let { inputHomeAddress.setText(it) }
        if (homeLat != 0.0) textHomeCoords.text = "%.4f, %.4f".format(homeLat, homeLng)

        workLat = prefs.getWorkLat()
        workLng = prefs.getWorkLng()
        prefs.getWorkAddress()?.let { inputWorkAddress.setText(it) }
        if (workLat != 0.0) textWorkCoords.text = "%.4f, %.4f".format(workLat, workLng)

        switchBikeOptions.isChecked = prefs.getShowBikeOptions()
    }

    private fun setupStationChips() {
        val selectedStations = prefs.getSelectedStations().toSet()
        val sortedStations = stations.sortedBy { it.name }

        for (station in sortedStations) {
            val chip = Chip(requireContext()).apply {
                text = "${station.name} (${station.lines.joinToString(",")})"
                isCheckable = true
                isChecked = selectedStations.contains(station.id)
                tag = station.id
                setTextColor(Color.parseColor("#eeeeee"))
                val lineColor = MtaColors.getLineColor(station.lines.firstOrNull() ?: "G")
                if (isChecked) {
                    chipStrokeWidth = 2f
                    chipStrokeColor = android.content.res.ColorStateList.valueOf(lineColor)
                }
                setOnCheckedChangeListener { _, checked ->
                    chipStrokeWidth = if (checked) 2f else 1f
                    chipStrokeColor = android.content.res.ColorStateList.valueOf(
                        if (checked) lineColor else Color.LTGRAY
                    )
                }
            }
            chipGroupStations.addView(chip)
        }
    }

    private fun setupClickListeners() {
        view?.findViewById<Button>(R.id.btn_geocode_home)?.setOnClickListener {
            geocodeAddress(inputHomeAddress.text?.toString(), isHome = true)
        }

        view?.findViewById<Button>(R.id.btn_geocode_work)?.setOnClickListener {
            geocodeAddress(inputWorkAddress.text?.toString(), isHome = false)
        }

        view?.findViewById<Button>(R.id.btn_save)?.setOnClickListener {
            saveSettings()
        }
    }

    private fun geocodeAddress(address: String?, isHome: Boolean) {
        if (address.isNullOrBlank()) {
            showStatus("Please enter an address", true)
            return
        }

        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val geocoder = Geocoder(requireContext(), Locale.getDefault())
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocationName(address, 1)?.firstOrNull()
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
                    showStatus("Location found!", false)
                } else {
                    showStatus("Could not find address", true)
                }
            } catch (e: Exception) {
                showStatus("Geocoding failed: ${e.message}", true)
            }
        }
    }

    private fun saveSettings() {
        val apiKey = inputApiKey.text?.toString()?.trim()
        val googleApiKey = inputGoogleApiKey.text?.toString()?.trim()
        val workerUrl = inputWorkerUrl.text?.toString()?.trim()

        if (homeLat == 0.0 || homeLng == 0.0) {
            showStatus("Please set your home location", true)
            return
        }

        if (workLat == 0.0 || workLng == 0.0) {
            showStatus("Please set your work location", true)
            return
        }

        val selectedStations = mutableListOf<String>()
        for (i in 0 until chipGroupStations.childCount) {
            val chip = chipGroupStations.getChildAt(i) as? Chip
            if (chip?.isChecked == true) {
                selectedStations.add(chip.tag as String)
            }
        }

        if (selectedStations.isEmpty()) {
            showStatus("Please select at least one station", true)
            return
        }

        if (!apiKey.isNullOrBlank()) prefs.setOpenWeatherApiKey(apiKey)
        if (!googleApiKey.isNullOrBlank()) prefs.setGoogleApiKey(googleApiKey)
        if (!workerUrl.isNullOrBlank()) prefs.setWorkerUrl(workerUrl)
        prefs.setHomeLocation(homeLat, homeLng, inputHomeAddress.text?.toString() ?: "")
        prefs.setWorkLocation(workLat, workLng, inputWorkAddress.text?.toString() ?: "")
        prefs.setSelectedStations(selectedStations)
        prefs.setShowBikeOptions(switchBikeOptions.isChecked)

        showStatus("Settings saved!", false)
    }

    private fun showStatus(message: String, isError: Boolean) {
        textStatus.visibility = View.VISIBLE
        textStatus.text = message
        textStatus.setTextColor(if (isError) Color.parseColor("#F44336") else Color.parseColor("#4CAF50"))
    }
}
