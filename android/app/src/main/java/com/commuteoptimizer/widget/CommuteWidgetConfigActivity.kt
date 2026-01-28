package com.commuteoptimizer.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.commuteoptimizer.widget.data.CommuteRepository
import com.commuteoptimizer.widget.data.Result
import com.commuteoptimizer.widget.util.WidgetPreferences
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch

class CommuteWidgetConfigActivity : AppCompatActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    private lateinit var inputApiUrl: TextInputEditText
    private lateinit var btnTestConnection: Button
    private lateinit var btnSave: Button
    private lateinit var btnCancel: Button
    private lateinit var textConnectionStatus: TextView

    private lateinit var prefs: WidgetPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set result to CANCELED in case the user backs out
        setResult(RESULT_CANCELED)

        setContentView(R.layout.activity_config)

        prefs = WidgetPreferences(this)

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

        // Initialize views
        inputApiUrl = findViewById(R.id.input_api_url)
        btnTestConnection = findViewById(R.id.btn_test_connection)
        btnSave = findViewById(R.id.btn_save)
        btnCancel = findViewById(R.id.btn_cancel)
        textConnectionStatus = findViewById(R.id.text_connection_status)

        // Load existing URL if configured
        val existingUrl = prefs.getApiUrl(appWidgetId)
        inputApiUrl.setText(existingUrl)

        setupClickListeners()
    }

    private fun setupClickListeners() {
        btnTestConnection.setOnClickListener {
            testConnection()
        }

        btnSave.setOnClickListener {
            saveConfiguration()
        }

        btnCancel.setOnClickListener {
            finish()
        }
    }

    private fun testConnection() {
        val url = inputApiUrl.text?.toString()?.trim()

        if (url.isNullOrEmpty()) {
            showStatus(getString(R.string.config_url_required), isError = true)
            return
        }

        showStatus(getString(R.string.config_testing), isError = false)
        btnTestConnection.isEnabled = false

        lifecycleScope.launch {
            val repository = CommuteRepository(url)
            when (val result = repository.testConnection()) {
                is Result.Success -> {
                    showStatus(getString(R.string.config_success), isError = false)
                }
                is Result.Error -> {
                    showStatus(
                        getString(R.string.config_error, result.message),
                        isError = true
                    )
                }
            }
            btnTestConnection.isEnabled = true
        }
    }

    private fun saveConfiguration() {
        val url = inputApiUrl.text?.toString()?.trim()

        if (url.isNullOrEmpty()) {
            showStatus(getString(R.string.config_url_required), isError = true)
            return
        }

        // Save the URL
        prefs.setApiUrl(appWidgetId, url)

        // Trigger initial widget update
        val appWidgetManager = AppWidgetManager.getInstance(this)
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
        textConnectionStatus.visibility = View.VISIBLE
        textConnectionStatus.text = message
        textConnectionStatus.setTextColor(
            if (isError) Color.parseColor("#F44336") else Color.parseColor("#4CAF50")
        )
    }
}
