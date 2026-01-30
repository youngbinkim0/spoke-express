package com.commuteoptimizer.widget.ui

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.commuteoptimizer.widget.R
import com.commuteoptimizer.widget.data.CommuteRepository
import com.commuteoptimizer.widget.data.Result
import com.commuteoptimizer.widget.data.models.CommuteOption
import com.commuteoptimizer.widget.data.models.CommuteResponse
import com.commuteoptimizer.widget.util.MtaColors
import com.commuteoptimizer.widget.util.WidgetPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class CommuteFragment : Fragment() {

    private val handler = Handler(Looper.getMainLooper())
    private val refreshInterval = 30000L

    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var weatherTemp: TextView
    private lateinit var weatherConditions: TextView
    private lateinit var weatherWarning: TextView
    private lateinit var currentTime: TextView
    private lateinit var alertsContainer: LinearLayout
    private lateinit var optionsContainer: LinearLayout
    private lateinit var progress: ProgressBar
    private lateinit var errorText: TextView
    private lateinit var bikeToggle: CheckBox

    private val refreshRunnable = object : Runnable {
        override fun run() {
            loadData()
            handler.postDelayed(this, refreshInterval)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_commute, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        swipeRefresh = view.findViewById(R.id.swipe_refresh)
        weatherTemp = view.findViewById(R.id.weather_temp)
        weatherConditions = view.findViewById(R.id.weather_conditions)
        weatherWarning = view.findViewById(R.id.weather_warning)
        currentTime = view.findViewById(R.id.current_time)
        alertsContainer = view.findViewById(R.id.alerts_container)
        optionsContainer = view.findViewById(R.id.options_container)
        progress = view.findViewById(R.id.progress)
        errorText = view.findViewById(R.id.error_text)
        bikeToggle = view.findViewById(R.id.bike_toggle)

        val prefs = WidgetPreferences(requireContext())
        bikeToggle.isChecked = prefs.getShowBikeOptions()

        swipeRefresh.setOnRefreshListener { loadData() }
        view.findViewById<ImageButton>(R.id.btn_refresh).setOnClickListener { loadData() }
        bikeToggle.setOnCheckedChangeListener { _, isChecked ->
            prefs.setShowBikeOptions(isChecked)
            loadData()
        }

        loadData()
    }

    override fun onResume() {
        super.onResume()
        handler.postDelayed(refreshRunnable, refreshInterval)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(refreshRunnable)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacks(refreshRunnable)
    }

    private fun loadData() {
        val ctx = context ?: return
        val repository = CommuteRepository(ctx)

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                progress.visibility = View.VISIBLE
                errorText.visibility = View.GONE
            }

            when (val result = repository.getCommuteOptions()) {
                is Result.Success -> {
                    withContext(Dispatchers.Main) {
                        swipeRefresh.isRefreshing = false
                        progress.visibility = View.GONE
                        displayData(result.data)
                    }
                }
                is Result.Error -> {
                    withContext(Dispatchers.Main) {
                        swipeRefresh.isRefreshing = false
                        progress.visibility = View.GONE
                        errorText.text = result.message
                        errorText.visibility = View.VISIBLE
                    }
                }
            }
        }
    }

    private fun displayData(data: CommuteResponse) {
        // Weather
        weatherTemp.text = "${data.weather.tempF}\u00B0F"
        weatherConditions.text = data.weather.conditions
        weatherWarning.visibility = if (data.weather.isBad) View.VISIBLE else View.GONE
        currentTime.text = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())

        // Alerts
        alertsContainer.removeAllViews()
        for (alert in data.alerts) {
            val alertView = layoutInflater.inflate(R.layout.item_alert, alertsContainer, false) as TextView
            alertView.text = "${alert.routeIds.joinToString("/")} - ${alert.headerText}"
            alertsContainer.addView(alertView)
        }

        // Options
        optionsContainer.removeAllViews()
        for (option in data.options) {
            val optionView = layoutInflater.inflate(R.layout.item_commute_option, optionsContainer, false)
            bindOption(optionView, option)
            optionsContainer.addView(optionView)
        }
    }

    private fun bindOption(view: View, option: CommuteOption) {
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
            // Add mode icon
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

            // Add line badge for subway legs
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

            // Add arrow between legs
            if (index < option.legs.size - 1) {
                val arrow = TextView(context).apply {
                    text = " \u2192 "
                    textSize = 14f
                    setTextColor(Color.parseColor("#888888"))
                }
                legsContainer.addView(arrow)
            }
        }

        // Expandable
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

            // Build info text with optional from and numStops (like webapp)
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
}
