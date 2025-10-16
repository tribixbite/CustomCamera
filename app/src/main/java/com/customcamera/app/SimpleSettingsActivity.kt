package com.customcamera.app

import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.lifecycle.lifecycleScope
import com.customcamera.app.engine.SettingsManager
import com.customcamera.app.engine.plugins.PluginRegistry
import kotlinx.coroutines.launch

/**
 * Simple working settings activity for camera configuration
 */
class SimpleSettingsActivity : AppCompatActivity() {

    private lateinit var settingsManager: SettingsManager
    private lateinit var settingsContainer: LinearLayout
    private var availableCameras: List<Pair<Int, String>> = emptyList()

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

            // Detect available cameras asynchronously
            lifecycleScope.launch {
                try {
                    detectAvailableCameras()
                    createSettingsUI()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to detect cameras", e)
                    createSettingsUI() // Still create UI even if camera detection fails
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize settings", e)

            // Create fallback UI
            createFallbackUI(e.message ?: "Unknown error")
        }
    }

    private suspend fun detectAvailableCameras() {
        try {
            val cameraProvider = ProcessCameraProvider.getInstance(this).get()
            val cameras = cameraProvider.availableCameraInfos

            availableCameras = cameras.mapIndexed { index, cameraInfo ->
                val facing = when (cameraInfo.lensFacing) {
                    androidx.camera.core.CameraSelector.LENS_FACING_FRONT -> "Front"
                    androidx.camera.core.CameraSelector.LENS_FACING_BACK -> "Back"
                    else -> "External"
                }
                Pair(index, "Camera $index ($facing)")
            }

            Log.i(TAG, "Detected ${availableCameras.size} cameras")
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting cameras", e)
            availableCameras = emptyList()
        }
    }

    private fun createSimpleLayout() {
        // Create scrollable container
        val scrollView = android.widget.ScrollView(this).apply {
            isFillViewport = true
        }

        settingsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        scrollView.addView(settingsContainer)
        setContentView(scrollView)
    }

    private fun createSettingsUI() {
        try {
            // Add title
            addTitle("Camera Settings")

            // Camera selection section
            if (availableCameras.isNotEmpty()) {
                addTitle("Camera Selection")

                // Main camera selection
                addSpinnerSetting(
                    "Main Camera",
                    "Default camera to use when opening camera",
                    availableCameras.map { it.second },
                    settingsManager.defaultCameraIndex.value
                ) { selectedIndex ->
                    settingsManager.setDefaultCameraIndex(selectedIndex)
                    Toast.makeText(this, "Main camera set to: ${availableCameras[selectedIndex].second}", Toast.LENGTH_SHORT).show()
                    Log.i(TAG, "Main camera changed to index: $selectedIndex")
                }

                // PiP camera selection
                if (availableCameras.size > 1) {
                    addSpinnerSetting(
                        "PiP Camera",
                        "Secondary camera for Picture-in-Picture mode",
                        availableCameras.map { it.second },
                        settingsManager.pipCameraIndex.value
                    ) { selectedIndex ->
                        settingsManager.setPipCameraIndex(selectedIndex)
                        Toast.makeText(this, "PiP camera set to: ${availableCameras[selectedIndex].second}", Toast.LENGTH_SHORT).show()
                        Log.i(TAG, "PiP camera changed to index: $selectedIndex")
                    }
                }

                addInfoSetting("Available Cameras", "${availableCameras.size} detected")
            }

        // Grid overlay setting
        addSwitchSetting(
            "Grid Overlay (9x3)",
            "Show 9 tall x 3 wide composition grid",
            settingsManager.gridOverlay.value
        ) { enabled ->
            // Update StateFlow - GridOverlayPlugin reads from this centralized state
            settingsManager.setGridOverlay(enabled)

            Toast.makeText(this, "Grid overlay ${if (enabled) "enabled" else "disabled"}", Toast.LENGTH_SHORT).show()
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

        // Auto-generate plugin settings from PluginRegistry
        addPluginSettings()

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

        // Gesture controls info
        addTitle("Gesture Controls")
        addInfoSetting("Grid Overlay", "Double tap camera preview")
        addInfoSetting("Barcode Scanner", "Triple tap camera preview")
        addInfoSetting("Crop Mode", "Quadruple tap camera preview")
        addInfoSetting("Smart Scene", "Five tap camera preview")
        addInfoSetting("Object Detection", "Six tap camera preview")
        addInfoSetting("Zoom Control", "Pinch camera preview")

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
            setTextColor(android.graphics.Color.WHITE)
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
            setTextColor(android.graphics.Color.WHITE)
        }
        textContainer.addView(titleView)

        val descriptionView = TextView(this).apply {
            text = description
            textSize = 14f
            setTextColor(android.graphics.Color.LTGRAY)
            alpha = 0.9f
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
            setTextColor(android.graphics.Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val valueView = TextView(this).apply {
            text = value
            textSize = 16f
            setTextColor(android.graphics.Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        container.addView(titleView)
        container.addView(valueView)
        settingsContainer.addView(container)
    }

    private fun addSpinnerSetting(
        title: String,
        description: String,
        options: List<String>,
        initialSelection: Int,
        onChanged: (Int) -> Unit
    ) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 8, 0, 8)
        }

        val titleView = TextView(this).apply {
            text = title
            textSize = 16f
            setTextColor(android.graphics.Color.WHITE)
        }
        container.addView(titleView)

        val descriptionView = TextView(this).apply {
            text = description
            textSize = 14f
            setTextColor(android.graphics.Color.LTGRAY)
            alpha = 0.9f
            setPadding(0, 0, 0, 8)
        }
        container.addView(descriptionView)

        val spinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@SimpleSettingsActivity,
                android.R.layout.simple_spinner_item,
                options
            ).also { adapter ->
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }

            // Set initial selection safely
            val safeSelection = initialSelection.coerceIn(0, options.size - 1)
            setSelection(safeSelection)

            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                    // Only trigger callback if it's not the initial setup
                    if (tag == "initialized") {
                        onChanged(position)
                    } else {
                        tag = "initialized"
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {
                    // Do nothing
                }
            }
        }
        container.addView(spinner)

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
                setBackgroundColor(android.graphics.Color.parseColor("#FFEEEE"))
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

    /**
     * Auto-generate plugin settings from PluginRegistry
     * Displays all user-toggleable plugins grouped by category
     */
    private fun addPluginSettings() {
        try {
            val pluginsByCategory = PluginRegistry.getPluginsByCategory()

            // Category display names
            val categoryNames = mapOf(
                com.customcamera.app.engine.plugins.PluginCategory.OVERLAYS to "Overlay Plugins",
                com.customcamera.app.engine.plugins.PluginCategory.ANALYSIS to "Analysis Plugins",
                com.customcamera.app.engine.plugins.PluginCategory.CONTROLS to "Control Plugins",
                com.customcamera.app.engine.plugins.PluginCategory.AI to "AI-Powered Features",
                com.customcamera.app.engine.plugins.PluginCategory.CAPTURE to "Capture Features"
            )

            // Iterate through categories in order
            val orderedCategories = listOf(
                com.customcamera.app.engine.plugins.PluginCategory.OVERLAYS,
                com.customcamera.app.engine.plugins.PluginCategory.ANALYSIS,
                com.customcamera.app.engine.plugins.PluginCategory.CONTROLS,
                com.customcamera.app.engine.plugins.PluginCategory.AI,
                com.customcamera.app.engine.plugins.PluginCategory.CAPTURE
            )

            for (category in orderedCategories) {
                val plugins = pluginsByCategory[category] ?: continue
                if (plugins.isEmpty()) continue

                // Add category title
                addTitle(categoryNames[category] ?: category.name)

                // Add plugin settings
                for (plugin in plugins) {
                    addSwitchSetting(
                        plugin.displayName,
                        plugin.description,
                        settingsManager.isPluginEnabled(plugin.name)
                    ) { enabled ->
                        settingsManager.setPluginEnabled(plugin.name, enabled)
                        Toast.makeText(
                            this,
                            "${plugin.displayName} ${if (enabled) "enabled" else "disabled"}",
                            Toast.LENGTH_SHORT
                        ).show()
                        Log.i(TAG, "${plugin.name} plugin changed to: $enabled")
                    }
                }
            }

            Log.i(TAG, "Auto-generated settings for ${pluginsByCategory.values.sumOf { it.size }} plugins")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to auto-generate plugin settings", e)
            Toast.makeText(this, "Plugin settings error: ${e.message}", Toast.LENGTH_SHORT).show()
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