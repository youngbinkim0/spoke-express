package com.commuteoptimizer.widget.ui

import android.graphics.Color
import android.view.inputmethod.EditorInfo
import android.location.Geocoder
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.commuteoptimizer.widget.R
import com.commuteoptimizer.widget.data.Result
import com.commuteoptimizer.widget.data.models.CommuteOption
import com.commuteoptimizer.widget.data.models.CommuteResponse
import com.commuteoptimizer.widget.service.CommuteCalculator
import com.commuteoptimizer.widget.util.MtaColors
import com.commuteoptimizer.widget.util.WidgetPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import kotlin.coroutines.suspendCoroutine
import kotlin.coroutines.resume

class SearchFragment : Fragment() {

    private lateinit var inputFrom: EditText
    private lateinit var inputTo: EditText
    private lateinit var btnSearch: Button
    private lateinit var bikeToggle: CheckBox
    private lateinit var searchStatus: TextView
    private lateinit var searchProgress: ProgressBar
    private lateinit var weatherBar: LinearLayout
    private lateinit var weatherTemp: TextView
    private lateinit var weatherConditions: TextView
    private lateinit var weatherWarning: TextView
    private lateinit var currentTime: TextView
    private lateinit var alertsContainer: LinearLayout
    private lateinit var optionsContainer: LinearLayout
    private lateinit var recentHeader: TextView
    private lateinit var recentContainer: LinearLayout

    private var lastFromCoords: Pair<Double, Double>? = null
    private var lastToCoords: Pair<Double, Double>? = null
    private var lastFromAddress: String? = null
    private var lastToAddress: String? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_search, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        inputFrom = view.findViewById(R.id.input_from)
        inputTo = view.findViewById(R.id.input_to)
        btnSearch = view.findViewById(R.id.btn_search)
        bikeToggle = view.findViewById(R.id.bike_toggle)
        searchStatus = view.findViewById(R.id.search_status)
        searchProgress = view.findViewById(R.id.search_progress)
        weatherBar = view.findViewById(R.id.weather_bar)
        weatherTemp = view.findViewById(R.id.weather_temp)
        weatherConditions = view.findViewById(R.id.weather_conditions)
        weatherWarning = view.findViewById(R.id.weather_warning)
        currentTime = view.findViewById(R.id.current_time)
        alertsContainer = view.findViewById(R.id.alerts_container)
        optionsContainer = view.findViewById(R.id.options_container)
        recentHeader = view.findViewById(R.id.recent_header)
        recentContainer = view.findViewById(R.id.recent_container)

        val prefs = WidgetPreferences(requireContext())
        bikeToggle.isChecked = prefs.getShowBikeOptions()
        bikeToggle.setOnCheckedChangeListener { _, isChecked ->
            prefs.setShowBikeOptions(isChecked)
            val fromCoords = lastFromCoords
            val toCoords = lastToCoords
            if (fromCoords != null && toCoords != null) {
                performSearchWithCoords(
                    fromCoords = fromCoords,
                    toCoords = toCoords,
                    fromAddress = lastFromAddress ?: inputFrom.text.toString().trim(),
                    toAddress = lastToAddress ?: inputTo.text.toString().trim(),
                    saveRecent = false
                )
            }
        }

        btnSearch.setOnClickListener { performSearch() }
        // Prefill from/to with saved home/work addresses
        val homeAddress = prefs.getHomeAddress()
        val workAddress = prefs.getWorkAddress()
        if (!homeAddress.isNullOrBlank()) {
            inputFrom.setText(homeAddress)
            val homeLat = prefs.getHomeLat()
            val homeLng = prefs.getHomeLng()
            if (homeLat != 0.0 && homeLng != 0.0) {
                lastFromCoords = Pair(homeLat, homeLng)
                lastFromAddress = homeAddress
            }
        }
        if (!workAddress.isNullOrBlank()) {
            inputTo.setText(workAddress)
            val workLat = prefs.getWorkLat()
            val workLng = prefs.getWorkLng()
            if (workLat != 0.0 && workLng != 0.0) {
                lastToCoords = Pair(workLat, workLng)
                lastToAddress = workAddress
            }
        }

        // Enter key in address fields triggers search
        inputFrom.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_NEXT) {
                performSearch()
                true
            } else false
        }
        inputTo.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_NEXT) {
                performSearch()
                true
            } else false
        }
        loadRecentSearches()
    }

    private fun performSearch() {
        val fromAddress = inputFrom.text.toString().trim()
        val toAddress = inputTo.text.toString().trim()

        if (fromAddress.isBlank() || toAddress.isBlank()) {
            showStatus("Please enter both origin and destination", true)
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            searchProgress.visibility = View.VISIBLE
            btnSearch.isEnabled = false
            optionsContainer.removeAllViews()
            alertsContainer.removeAllViews()
            weatherBar.visibility = View.GONE
            searchStatus.visibility = View.GONE

            try {
                val fromCoords = withContext(Dispatchers.IO) {
                    geocode(fromAddress)
                }
                if (fromCoords == null) {
                    showStatus("Could not find origin address", true)
                    searchProgress.visibility = View.GONE
                    btnSearch.isEnabled = true
                    return@launch
                }

                val toCoords = withContext(Dispatchers.IO) {
                    geocode(toAddress)
                }
                if (toCoords == null) {
                    showStatus("Could not find destination address", true)
                    searchProgress.visibility = View.GONE
                    btnSearch.isEnabled = true
                    return@launch
                }

                performSearchWithCoords(
                    fromCoords = fromCoords,
                    toCoords = toCoords,
                    fromAddress = fromAddress,
                    toAddress = toAddress,
                    saveRecent = true
                )
            } catch (e: Exception) {
                searchProgress.visibility = View.GONE
                btnSearch.isEnabled = true
                showStatus("Search failed: ${e.message}", true)
            }
        }
    }

    private fun performSearchWithCoords(
        fromCoords: Pair<Double, Double>,
        toCoords: Pair<Double, Double>,
        fromAddress: String,
        toAddress: String,
        saveRecent: Boolean
    ) {
        lastFromCoords = fromCoords
        lastToCoords = toCoords
        lastFromAddress = fromAddress
        lastToAddress = toAddress

        viewLifecycleOwner.lifecycleScope.launch {
            searchProgress.visibility = View.VISIBLE
            btnSearch.isEnabled = false
            optionsContainer.removeAllViews()
            alertsContainer.removeAllViews()
            weatherBar.visibility = View.GONE
            searchStatus.visibility = View.GONE

            try {
                if (saveRecent) {
                    val prefs = WidgetPreferences(requireContext())
                    prefs.addRecentSearch(
                        WidgetPreferences.RecentSearch(
                            fromAddress = fromAddress,
                            fromLat = fromCoords.first,
                            fromLng = fromCoords.second,
                            toAddress = toAddress,
                            toLat = toCoords.first,
                            toLng = toCoords.second,
                            timestamp = System.currentTimeMillis()
                        )
                    )
                }

                val calculator = CommuteCalculator(requireContext())
                val result = withContext(Dispatchers.IO) {
                    calculator.calculateCommute(
                        fromCoords.first, fromCoords.second,
                        toCoords.first, toCoords.second
                    )
                }

                searchProgress.visibility = View.GONE
                btnSearch.isEnabled = true

                when (result) {
                    is Result.Success -> displayData(result.data)
                    is Result.Error -> showStatus(result.message, true)
                }

                loadRecentSearches()
            } catch (e: Exception) {
                searchProgress.visibility = View.GONE
                btnSearch.isEnabled = true
                showStatus("Search failed: ${e.message}", true)
            }
        }
    }

    private fun displayData(data: CommuteResponse) {
        weatherTemp.text = data.weather.tempF?.let { "${it}\u00B0F" } ?: "--"
        weatherConditions.text = data.weather.conditions
        weatherWarning.visibility = if (data.weather.isBad) View.VISIBLE else View.GONE
        currentTime.text = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
        weatherBar.visibility = View.VISIBLE

        alertsContainer.removeAllViews()
        for (alert in data.alerts) {
            val alertView = layoutInflater.inflate(R.layout.item_alert, alertsContainer, false) as TextView
            alertView.text = "${alert.routeIds.joinToString("/")} - ${alert.headerText}"
            alertsContainer.addView(alertView)
        }

        optionsContainer.removeAllViews()
        if (data.options.isEmpty()) {
            showStatus("No commute options found", true)
            return
        }
        for (option in data.options) {
            val optionView = layoutInflater.inflate(R.layout.item_commute_option, optionsContainer, false)
            bindCommuteOption(optionView, option)
            optionsContainer.addView(optionView)
        }
    }

    private fun bindCommuteOption(view: View, option: CommuteOption) {
        val rankView = view.findViewById<TextView>(R.id.option_rank)
        rankView.text = option.rank.toString()
        val rankColor = when (option.rank) {
            1 -> Color.parseColor("#FFD700")
            2 -> Color.parseColor("#C0C0C0")
            else -> Color.parseColor("#CD7F32")
        }
        rankView.background.setTint(rankColor)

        view.findViewById<TextView>(R.id.option_summary).text = option.summary
        view.findViewById<TextView>(R.id.option_duration).text = "${option.durationMinutes}m"
        view.findViewById<TextView>(R.id.next_train_value).text = option.nextTrain
        view.findViewById<TextView>(R.id.arrival_value).text = option.arrivalTime

        val legsContainer = view.findViewById<LinearLayout>(R.id.legs_container)
        legsContainer.removeAllViews()
        for ((index, leg) in option.legs.withIndex()) {
            val icon = when (leg.mode) {
                "bike" -> "\uD83D\uDEB2"
                "walk" -> "\uD83D\uDEB6"
                else -> "\uD83D\uDE87"
            }
            val iconText = TextView(context).apply {
                text = icon
                textSize = 14f
                setPadding(0, 0, 4, 0)
            }
            legsContainer.addView(iconText)

            if (leg.route != null) {
                val badge = TextView(context).apply {
                    text = " ${leg.route} "
                    textSize = 12f
                    setTextColor(MtaColors.getTextColorForLine(leg.route))
                    setBackgroundColor(MtaColors.getLineColor(leg.route))
                    setPadding(8, 2, 8, 2)
                }
                legsContainer.addView(badge)
            }

            if (index < option.legs.size - 1) {
                val arrow = TextView(context).apply {
                    text = " \u2192 "
                    textSize = 14f
                    setTextColor(Color.parseColor("#888888"))
                }
                legsContainer.addView(arrow)
            }
        }

        // Expandable details
        val expandedDetails = view.findViewById<LinearLayout>(R.id.expanded_details)
        val expandIcon = view.findViewById<ImageView>(R.id.expand_icon)
        view.setOnClickListener {
            if (expandedDetails.visibility == View.GONE) {
                expandedDetails.visibility = View.VISIBLE
                expandIcon.rotation = 180f
                buildExpandedDetails(expandedDetails, option)
            } else {
                expandedDetails.visibility = View.GONE
                expandIcon.rotation = 0f
            }
        }
    }

    private fun buildExpandedDetails(container: LinearLayout, option: CommuteOption) {
        container.removeAllViews()
        for (leg in option.legs) {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 8, 0, 8)
            }
            val icon = when (leg.mode) {
                "bike" -> "\uD83D\uDEB2"
                "walk" -> "\uD83D\uDEB6"
                else -> "\uD83D\uDE87"
            }
            val iconText = TextView(context).apply {
                text = icon
                textSize = 16f
            }
            row.addView(iconText)

            if (leg.route != null) {
                val badge = TextView(context).apply {
                    text = " ${leg.route} "
                    setTextColor(MtaColors.getTextColorForLine(leg.route))
                    setBackgroundColor(MtaColors.getLineColor(leg.route))
                    setPadding(12, 4, 12, 4)
                }
                row.addView(badge)
            }

            val fromText = leg.from?.let { "From $it " } ?: ""
            val stopsText = leg.numStops?.let { " ($it stops)" } ?: ""
            val info = TextView(context).apply {
                text = "  ${fromText}\u2192 ${leg.to}${stopsText} (${leg.duration}m)"
                setTextColor(Color.parseColor("#888888"))
                textSize = 14f
            }
            row.addView(info)

            container.addView(row)
        }
    }

    private fun loadRecentSearches() {
        val prefs = WidgetPreferences(requireContext())
        val recent = prefs.getRecentSearches()

        recentContainer.removeAllViews()
        if (recent.isEmpty()) {
            recentHeader.visibility = View.GONE
            return
        }

        recentHeader.visibility = View.VISIBLE
        for (search in recent) {
            val itemView = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                background = resources.getDrawable(R.drawable.card_background, null)
                setPadding(16, 12, 16, 12)
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                params.bottomMargin = 8
                layoutParams = params
            }

            val fromLine = TextView(context).apply {
                text = "From: ${search.fromAddress}"
                setTextColor(Color.parseColor("#eeeeee"))
                textSize = 14f
            }
            itemView.addView(fromLine)

            val toLine = TextView(context).apply {
                text = "To: ${search.toAddress}"
                setTextColor(Color.parseColor("#888888"))
                textSize = 13f
            }
            itemView.addView(toLine)

            itemView.setOnClickListener {
                inputFrom.setText(search.fromAddress)
                inputTo.setText(search.toAddress)
                performSearchWithCoords(
                    fromCoords = Pair(search.fromLat, search.fromLng),
                    toCoords = Pair(search.toLat, search.toLng),
                    fromAddress = search.fromAddress,
                    toAddress = search.toAddress,
                    saveRecent = true
                )
            }

            recentContainer.addView(itemView)
        }
    }

    private suspend fun geocode(address: String): Pair<Double, Double>? {
        val geocoder = Geocoder(requireContext(), Locale.getDefault())
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

    private fun showStatus(message: String, isError: Boolean) {
        searchStatus.text = message
        searchStatus.setTextColor(
            if (isError) Color.parseColor("#ff6b6b") else Color.parseColor("#4ecca3")
        )
        searchStatus.visibility = View.VISIBLE
    }
}
