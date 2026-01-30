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
import com.commuteoptimizer.widget.data.api.MtaApiService
import com.commuteoptimizer.widget.data.models.LocalStation
import com.commuteoptimizer.widget.service.LocalDataSource
import com.commuteoptimizer.widget.util.MtaColors
import com.commuteoptimizer.widget.util.WidgetPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class LiveTrainsFragment : Fragment() {

    private val handler = Handler(Looper.getMainLooper())
    private val refreshInterval = 30000L

    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var stationsContainer: LinearLayout
    private lateinit var progress: ProgressBar
    private lateinit var noStations: TextView
    private lateinit var lastUpdated: TextView

    private val refreshRunnable = object : Runnable {
        override fun run() {
            loadData()
            handler.postDelayed(this, refreshInterval)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_live_trains, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        swipeRefresh = view.findViewById(R.id.swipe_refresh)
        stationsContainer = view.findViewById(R.id.stations_container)
        progress = view.findViewById(R.id.progress)
        noStations = view.findViewById(R.id.no_stations)
        lastUpdated = view.findViewById(R.id.last_updated)

        swipeRefresh.setOnRefreshListener { loadData() }
        view.findViewById<ImageButton>(R.id.btn_refresh).setOnClickListener { loadData() }

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
        val prefs = WidgetPreferences(ctx)
        val localDataSource = LocalDataSource(ctx)

        val stationIds = prefs.getLiveStations()
        if (stationIds.isEmpty()) {
            stationsContainer.visibility = View.GONE
            noStations.visibility = View.VISIBLE
            swipeRefresh.isRefreshing = false
            return
        }

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                progress.visibility = View.VISIBLE
                noStations.visibility = View.GONE
            }

            try {
                val stationData = stationIds.map { stationId ->
                    val station = localDataSource.getStation(stationId)
                    val groups = MtaApiService.getGroupedArrivals(stationId, station?.lines ?: emptyList())
                    Pair(station, groups)
                }

                withContext(Dispatchers.Main) {
                    swipeRefresh.isRefreshing = false
                    progress.visibility = View.GONE
                    stationsContainer.visibility = View.VISIBLE
                    displayStations(stationData)
                    lastUpdated.text = "Last updated: ${SimpleDateFormat("h:mm:ss a", Locale.getDefault()).format(Date())}"
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    swipeRefresh.isRefreshing = false
                    progress.visibility = View.GONE
                    noStations.text = "Error loading trains: ${e.message}"
                    noStations.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun displayStations(stationData: List<Pair<LocalStation?, List<MtaApiService.ArrivalGroup>>>) {
        stationsContainer.removeAllViews()

        for ((station, groups) in stationData) {
            if (station == null) continue

            val stationView = layoutInflater.inflate(R.layout.item_station_card, stationsContainer, false)
            stationView.findViewById<TextView>(R.id.station_name).text = station.name

            // Line badges
            val linesContainer = stationView.findViewById<LinearLayout>(R.id.station_lines)
            for (line in station.lines.take(4)) {
                val badge = TextView(context).apply {
                    text = line
                    textSize = 11f
                    setTextColor(MtaColors.getTextColorForLine(line))
                    setBackgroundColor(MtaColors.getLineColor(line))
                    setPadding(8, 4, 8, 4)
                    val params = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    params.marginEnd = 4
                    layoutParams = params
                }
                linesContainer.addView(badge)
            }

            // Directions
            val directionsContainer = stationView.findViewById<LinearLayout>(R.id.directions_container)
            for (group in groups) {
                val dirView = layoutInflater.inflate(R.layout.item_direction, directionsContainer, false)

                val arrow = when (group.direction) {
                    "N" -> "\u2191"
                    "S" -> "\u2193"
                    else -> "\u2192"
                }
                dirView.findViewById<TextView>(R.id.direction_arrow).text = arrow
                dirView.findViewById<TextView>(R.id.headsign).text = group.headsign

                val arrivalsContainer = dirView.findViewById<LinearLayout>(R.id.arrivals_container)
                for (arrival in group.arrivals.take(3)) {
                    val arrivalText = if (arrival.minutesAway == 0) "Now" else "${arrival.minutesAway}min"
                    val badge = TextView(context).apply {
                        text = arrivalText
                        textSize = 12f
                        if (arrival.minutesAway <= 2) {
                            setBackgroundResource(R.drawable.arrival_badge_soon)
                            setTextColor(Color.parseColor("#1a1a2e"))
                        } else {
                            setBackgroundResource(R.drawable.arrival_badge_normal)
                            setTextColor(Color.parseColor("#4ecca3"))
                        }
                        setPadding(16, 4, 16, 4)
                        val params = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                        params.marginStart = 8
                        layoutParams = params
                    }
                    arrivalsContainer.addView(badge)
                }

                directionsContainer.addView(dirView)
            }

            if (groups.isEmpty()) {
                val noTrains = TextView(context).apply {
                    text = "No trains scheduled"
                    setTextColor(Color.parseColor("#888888"))
                    gravity = android.view.Gravity.CENTER
                    setPadding(0, 20, 0, 20)
                }
                directionsContainer.addView(noTrains)
            }

            stationsContainer.addView(stationView)
        }
    }
}
