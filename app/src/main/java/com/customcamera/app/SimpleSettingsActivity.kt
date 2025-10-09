package com.customcamera.app

import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.customcamera.app.engine.SettingsManager

/**
 * Simple working settings activity for camera configuration
 */
class SimpleSettingsActivity : AppCompatActivity() {

    private lateinit var settingsManager: SettingsManager
    private lateinit var settingsContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "SimpleSettingsActivity onCreate")

        // Create simple layout programmatically
        createSimpleLayout()

        // Setup toolbar safely
        try {
            supportActionBar?.apply {
                setDisplayHomeAsUpEnabled(true)
                title = "Camera Settings"
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not setup action bar", e)
        }

        // Initialize settings safely
        try {
            settingsManager = SettingsManager(this)
            createSettingsUI()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize settings", e)

            // Create fallback UI
            createFallbackUI(e.message ?: "Unknown error")
        }
    }

    private fun createSimpleLayout() {
        settingsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }
        setContentView(settingsContainer)
    }

    private fun createSettingsUI() {
        try {
            // Add title
            addTitle("Camera Settings")

        // Grid overlay setting
        addSwitchSetting(
            "Grid Overlay (9x3)",
            "Show 9 tall x 3 wide composition grid",
            settingsManager.gridOverlay.value
        ) { enabled ->
            // Update StateFlow - plugins will reactively observe the change
            settingsManager.setGridOverlay(enabled)

            Toast.makeText(this, "Grid overlay ${if (enabled) "enabled" else "disabled"} - restart camera to apply", Toast.LENGTH_LONG).show()
            Log.i(TAG, "Grid overlay setting changed via StateFlow: $enabled")
        }

        // Debug logging setting
        addSwitchSetting(
            "Debug Logging",
            "Enable verbose logging",
            settingsManager.debugLogging.value
        ) { enabled ->
            settingsManager.setDebugLogging(enabled)
            Toast.makeText(this, "Debug logging ${if (enabled) "enabled" else "disabled"}", Toast.LENGTH_SHORT).show()
        }

        // Photo and video settings
        addTitle("Photo & Video Settings")

        addSwitchSetting(
            "High Quality Photos",
            "Use maximum photo quality (95%)",
            settingsManager.photoQuality.value > 90
        ) { enabled ->
            val quality = if (enabled) 95 else 85
            settingsManager.setPhotoQuality(quality)
            Toast.makeText(this, "Photo quality set to $quality%", Toast.LENGTH_SHORT).show()
        }

        addSwitchSetting(
            "Video Stabilization",
            "Enable electronic image stabilization",
            settingsManager.getVideoStabilization()
        ) { enabled ->
            settingsManager.setVideoStabilization(enabled)
            Toast.makeText(this, "Video stabilization ${if (enabled) "enabled" else "disabled"}", Toast.LENGTH_SHORT).show()
        }

        addInfoSetting("Photo Quality", "${settingsManager.photoQuality.value}%")
        addInfoSetting("Video Quality", settingsManager.getVideoQuality())

        // Plugin controls
        addTitle("Plugin Controls")

        addSwitchSetting(
            "AutoFocus Plugin",
            "Enable tap-to-focus and continuous autofocus",
            settingsManager.isPluginEnabled("AutoFocus")
        ) { enabled ->
            settingsManager.setPluginEnabled("AutoFocus", enabled)

            // Send broadcast to camera interface
            val intent = android.content.Intent("com.customcamera.PLUGIN_TOGGLE")
            intent.putExtra("plugin", "AutoFocus")
            intent.putExtra("enabled", enabled)
            sendBroadcast(intent)

            Toast.makeText(this, "AutoFocus plugin ${if (enabled) "enabled" else "disabled"} - restart camera to apply", Toast.LENGTH_LONG).show()
            Log.i(TAG, "AutoFocus plugin setting changed: $enabled")
        }

        addSwitchSetting(
            "Grid Overlay Plugin",
            "Enable composition grid overlays",
            settingsManager.isPluginEnabled("GridOverlay")
        ) { enabled ->
            settingsManager.setPluginEnabled("GridOverlay", enabled)
            Toast.makeText(this, "Grid plugin ${if (enabled) "enabled" else "disabled"}", Toast.LENGTH_SHORT).show()
        }

        addSwitchSetting(
            "Camera Info Plugin",
            "Enable frame analysis and monitoring",
            settingsManager.isPluginEnabled("CameraInfo")
        ) { enabled ->
            settingsManager.setPluginEnabled("CameraInfo", enabled)
            Toast.makeText(this, "Camera info plugin ${if (enabled) "enabled" else "disabled"}", Toast.LENGTH_SHORT).show()
        }

        // Debug interface
        addTitle("Debug & Testing")

        val debugButton = android.widget.Button(this).apply {
            text = "Open Debug Interface"
            setOnClickListener {
                try {
                    val intent = android.content.Intent(this@SimpleSettingsActivity, DebugActivity::class.java)
                    startActivity(intent)
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "Failed to open debug interface", e)
                    android.widget.Toast.makeText(this@SimpleSettingsActivity, "Debug interface error", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
        settingsContainer.addView(debugButton)

        // Manual controls settings
        addTitle("Manual Controls")

        addSwitchSetting(
            "Camera2 Manual Controls",
            "Enable professional manual controls (ISO, shutter, focus)",
            true // Always enabled since they're implemented
        ) { enabled ->
            Toast.makeText(this, "Manual controls are ${if (enabled) "available" else "disabled"} through settings button in camera", Toast.LENGTH_LONG).show()
        }

        addSwitchSetting(
            "Pinch-to-Zoom",
            "Enable pinch gesture zoom control",
            true // Always enabled
        ) { enabled ->
            Toast.makeText(this, "Pinch-to-zoom is ${if (enabled) "available" else "disabled"} in camera preview", Toast.LENGTH_LONG).show()
        }

        addInfoSetting("Gesture Controls", "Double tap: Grid, Triple tap: Barcode, Pinch: Zoom")
        addInfoSetting("Manual Panel", "Settings button → Manual controls")

        Log.i(TAG, "Settings UI created")

        } catch (e: Exception) {
            Log.e(TAG, "Error creating settings UI", e)
            Toast.makeText(this, "Settings error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun addTitle(title: String) {
        val titleView = TextView(this).apply {
            text = title
            textSize = 20f
            setPadding(0, 24, 0, 16)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        settingsContainer.addView(titleView)
    }

    private fun addSwitchSetting(
        title: String,
        description: String,
        initialValue: Boolean,
        onChanged: (Boolean) -> Unit
    ) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 8, 0, 8)
        }

        val textContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val titleView = TextView(this).apply {
            text = title
            textSize = 16f
        }
        textContainer.addView(titleView)

        val descriptionView = TextView(this).apply {
            text = description
            textSize = 14f
            alpha = 0.7f
        }
        textContainer.addView(descriptionView)

        val switch = Switch(this).apply {
            isChecked = initialValue
            setOnCheckedChangeListener { _, isChecked ->
                onChanged(isChecked)
            }
        }

        container.addView(textContainer)
        container.addView(switch)
        settingsContainer.addView(container)
    }

    private fun addInfoSetting(title: String, value: String) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 8, 0, 8)
        }

        val titleView = TextView(this).apply {
            text = title
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val valueView = TextView(this).apply {
            text = value
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        container.addView(titleView)
        container.addView(valueView)
        settingsContainer.addView(container)
    }

    private fun createFallbackUI(errorMessage: String) {
        try {
            addTitle("Settings Error")

            val errorView = TextView(this).apply {
                text = "Settings initialization failed:\n$errorMessage"
                textSize = 14f
                setTextColor(android.graphics.Color.RED)
                setPadding(16, 16, 16, 16)
            }
            settingsContainer.addView(errorView)

            addTitle("Basic Information")

            addInfoSetting("App Version", "1.0.0-professional")
            addInfoSetting("Build Status", "Debug")
            addInfoSetting("Plugins", "12+ registered")

            val refreshButton = android.widget.Button(this).apply {
                text = "Retry Settings Initialization"
                setOnClickListener {
                    recreate() // Restart activity
                }
            }
            settingsContainer.addView(refreshButton)

            Log.i(TAG, "Fallback UI created due to settings error")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to create fallback UI", e)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    companion object {
        private const val TAG = "SimpleSettingsActivity"
    }
}