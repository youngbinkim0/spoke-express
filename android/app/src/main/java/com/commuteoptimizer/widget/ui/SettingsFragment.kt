package com.commuteoptimizer.widget.ui

import android.graphics.Color
import android.location.Geocoder
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import com.commuteoptimizer.widget.R
import com.commuteoptimizer.widget.data.models.LocalStation
import com.commuteoptimizer.widget.service.LocalDataSource
import com.commuteoptimizer.widget.util.MtaColors
import com.commuteoptimizer.widget.util.WidgetPreferences
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
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
    private lateinit var inputHomeAddress: TextInputEditText
    private lateinit var inputWorkAddress: TextInputEditText
    private lateinit var textHomeCoords: TextView
    private lateinit var textWorkCoords: TextView
    private lateinit var chipGroupLiveStations: ChipGroup
    private lateinit var textLiveStationCount: TextView
    private lateinit var switchBikeOptions: SwitchCompat
    private lateinit var textStatus: TextView
    private lateinit var liveStationsHeader: View
    private lateinit var liveStationsExpandIcon: ImageView
    private lateinit var liveSearchLayout: TextInputLayout
    private lateinit var liveSearchInput: TextInputEditText

    private var liveStationsExpanded = false
    private var allLiveStations: List<LocalStation> = emptyList()
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
        setupLiveStationChips()
        setupClickListeners()
    }

    private fun initViews(view: View) {
        inputApiKey = view.findViewById(R.id.input_api_key)
        inputGoogleApiKey = view.findViewById(R.id.input_google_api_key)
        inputHomeAddress = view.findViewById(R.id.input_home_address)
        inputWorkAddress = view.findViewById(R.id.input_work_address)
        textHomeCoords = view.findViewById(R.id.text_home_coords)
        textWorkCoords = view.findViewById(R.id.text_work_coords)
        chipGroupLiveStations = view.findViewById(R.id.chip_group_live_stations)
        textLiveStationCount = view.findViewById(R.id.text_live_station_count)
        switchBikeOptions = view.findViewById(R.id.switch_bike_options)
        textStatus = view.findViewById(R.id.text_status)

        // Collapsible section headers
        liveStationsHeader = view.findViewById(R.id.live_stations_header)
        liveStationsExpandIcon = view.findViewById(R.id.live_stations_expand_icon)

        liveSearchLayout = view.findViewById(R.id.live_search_layout)
        liveSearchInput = view.findViewById(R.id.live_search_input)

        liveStationsHeader.setOnClickListener {
            liveStationsExpanded = !liveStationsExpanded
            chipGroupLiveStations.visibility = if (liveStationsExpanded) View.VISIBLE else View.GONE
            liveSearchLayout.visibility = if (liveStationsExpanded) View.VISIBLE else View.GONE
            liveStationsExpandIcon.rotation = if (liveStationsExpanded) 180f else 0f
        }

        // Add search text watcher
        liveSearchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                filterLiveStations(s?.toString()?.trim() ?: "")
            }
        })
    }

    private fun loadExistingSettings() {
        prefs.getOpenWeatherApiKey()?.let { inputApiKey.setText(it) }
        prefs.getGoogleApiKey()?.let { inputGoogleApiKey.setText(it) }

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

    private fun setupLiveStationChips() {
        // Sort by distance from home if home is set, otherwise alphabetically
        allLiveStations = if (homeLat != 0.0 && homeLng != 0.0) {
            stations.sortedBy { calculateDistance(homeLat, homeLng, it.lat, it.lng) }
        } else {
            stations.sortedBy { it.name }
        }
        renderLiveStationChips(allLiveStations)
    }

    private fun renderLiveStationChips(stationsToRender: List<LocalStation>) {
        chipGroupLiveStations.removeAllViews()
        val selectedStations = prefs.getLiveStations().toSet()

        for (station in stationsToRender) {
            val distance = if (homeLat != 0.0 && homeLng != 0.0) {
                calculateDistance(homeLat, homeLng, station.lat, station.lng)
            } else null

            val distanceText = distance?.let { " - %.1f mi".format(it) } ?: ""

            val chip = Chip(requireContext()).apply {
                text = "${station.name} (${station.lines.joinToString(",")})$distanceText"
                isCheckable = true
                isChecked = selectedStations.contains(station.id)
                tag = station.id
                setTextColor(Color.parseColor("#eeeeee"))
                val lineColor = MtaColors.getLineColor(station.lines.firstOrNull() ?: "G")
                if (isChecked) {
                    chipStrokeWidth = 2f
                    chipStrokeColor = android.content.res.ColorStateList.valueOf(lineColor)
                }
                setOnCheckedChangeListener { chip, checked ->
                    // Enforce max 3 limit
                    if (checked && getSelectedLiveStationCount() > 3) {
                        chip.isChecked = false
                        showStatus("Maximum 3 stations for Live Trains", true)
                        return@setOnCheckedChangeListener
                    }
                    chipStrokeWidth = if (checked) 2f else 1f
                    chipStrokeColor = android.content.res.ColorStateList.valueOf(
                        if (checked) lineColor else Color.LTGRAY
                    )
                    updateLiveStationCount()
                }
            }
            chipGroupLiveStations.addView(chip)
        }
        updateLiveStationCount()
    }

    private fun getSelectedLiveStationCount(): Int {
        var count = 0
        for (i in 0 until chipGroupLiveStations.childCount) {
            val chip = chipGroupLiveStations.getChildAt(i) as? Chip
            if (chip?.isChecked == true) count++
        }
        return count
    }

    private fun updateLiveStationCount() {
        val count = getSelectedLiveStationCount()
        textLiveStationCount.text = "$count/3 selected"
    }

    private fun filterLiveStations(query: String) {
        val filtered = if (query.isEmpty()) {
            allLiveStations
        } else {
            allLiveStations.filter { station ->
                station.name.contains(query, ignoreCase = true) ||
                station.lines.any { it.contains(query, ignoreCase = true) }
            }
        }
        renderLiveStationChips(filtered)
    }

    private fun calculateDistance(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val earthRadiusMiles = 3959.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLng / 2) * Math.sin(dLng / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return earthRadiusMiles * c
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

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val geocoder = Geocoder(requireContext(), Locale.getDefault())
                    geocodeWithCompat(geocoder, address)
                }

                if (result != null) {
                    if (isHome) {
                        homeLat = result.first
                        homeLng = result.second
                        textHomeCoords.text = "%.4f, %.4f".format(homeLat, homeLng)
                        // Re-sort stations by distance from new home location
                        setupLiveStationChips()
                    } else {
                        workLat = result.first
                        workLng = result.second
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

    private suspend fun geocodeWithCompat(geocoder: Geocoder, address: String): Pair<Double, Double>? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCoroutine { continuation ->
                geocoder.getFromLocationName(address, 1) { addresses ->
                    val result = addresses.firstOrNull()?.let {
                        Pair(it.latitude, it.longitude)
                    }
                    continuation.resume(result)
                }
            }
        } else {
            @Suppress("DEPRECATION")
            geocoder.getFromLocationName(address, 1)?.firstOrNull()?.let {
                Pair(it.latitude, it.longitude)
            }
        }
    }

    private fun saveSettings() {
        val apiKey = inputApiKey.text?.toString()?.trim()
        val googleApiKey = inputGoogleApiKey.text?.toString()?.trim()

        if (googleApiKey.isNullOrBlank()) {
            showStatus("Please enter your Google API Key", true)
            return
        }

        if (homeLat == 0.0 || homeLng == 0.0) {
            showStatus("Please set your home location", true)
            return
        }

        if (workLat == 0.0 || workLng == 0.0) {
            showStatus("Please set your work location", true)
            return
        }

        val liveStations = mutableListOf<String>()
        for (i in 0 until chipGroupLiveStations.childCount) {
            val chip = chipGroupLiveStations.getChildAt(i) as? Chip
            if (chip?.isChecked == true) {
                liveStations.add(chip.tag as? String ?: continue)
            }
        }

        if (!apiKey.isNullOrBlank()) prefs.setOpenWeatherApiKey(apiKey)
        if (!googleApiKey.isNullOrBlank()) prefs.setGoogleApiKey(googleApiKey)
        prefs.setHomeLocation(homeLat, homeLng, inputHomeAddress.text?.toString() ?: "")
        prefs.setWorkLocation(workLat, workLng, inputWorkAddress.text?.toString() ?: "")
        prefs.setLiveStations(liveStations)
        prefs.setShowBikeOptions(switchBikeOptions.isChecked)

        showStatus("Settings saved!", false)
    }

    private fun showStatus(message: String, isError: Boolean) {
        textStatus.visibility = View.VISIBLE
        textStatus.text = message
        textStatus.setTextColor(if (isError) Color.parseColor("#F44336") else Color.parseColor("#4CAF50"))
    }
}
