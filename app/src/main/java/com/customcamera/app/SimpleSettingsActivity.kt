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

        // Setup toolbar
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "Camera Settings"
        }

        // Initialize settings
        settingsManager = SettingsManager(this)
        createSettingsUI()
    }

    private fun createSimpleLayout() {
        settingsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }
        setContentView(settingsContainer)
    }

    private fun createSettingsUI() {
        // Add title
        addTitle("Camera Settings")

        // Grid overlay setting
        addSwitchSetting(
            "Grid Overlay",
            "Show composition grid",
            settingsManager.gridOverlay.value
        ) { enabled ->
            settingsManager.setGridOverlay(enabled)
            Toast.makeText(this, "Grid overlay ${if (enabled) "enabled" else "disabled"}", Toast.LENGTH_SHORT).show()
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

        // Photo quality setting
        addTitle("Photo Settings")
        addInfoSetting("Photo Quality", "${settingsManager.photoQuality.value}%")

        // Plugin controls
        addTitle("Plugin Controls")

        addSwitchSetting(
            "AutoFocus Plugin",
            "Enable advanced focus controls",
            settingsManager.isPluginEnabled("AutoFocus")
        ) { enabled ->
            settingsManager.setPluginEnabled("AutoFocus", enabled)
            Toast.makeText(this, "AutoFocus plugin ${if (enabled) "enabled" else "disabled"}", Toast.LENGTH_SHORT).show()
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

        Log.i(TAG, "Settings UI created")
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