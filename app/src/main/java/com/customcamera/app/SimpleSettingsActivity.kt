package com.customcamera.app

import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.customcamera.app.databinding.ActivitySimpleSettingsRecyclerviewBinding
import com.customcamera.app.engine.SettingsManager
import com.customcamera.app.engine.plugins.PluginCategory
import com.customcamera.app.engine.plugins.PluginRegistry
import com.customcamera.app.ui.settings.SettingsAdapter
import com.customcamera.app.ui.settings.SettingsListItem
import kotlinx.coroutines.launch

/**
 * Settings activity with RecyclerView for smooth scrolling performance
 */
class SimpleSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySimpleSettingsRecyclerviewBinding
    private lateinit var settingsManager: SettingsManager
    private lateinit var pluginRegistry: PluginRegistry
    private lateinit var settingsAdapter: SettingsAdapter
    private var availableCameras: List<Pair<Int, String>> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "SimpleSettingsActivity onCreate")

        // Initialize ViewBinding
        binding = ActivitySimpleSettingsRecyclerviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Setup toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "Camera Settings"
        }

        // Initialize settings and adapter
        try {
            settingsManager = SettingsManager(this)
            pluginRegistry = PluginRegistry(this)

            // Setup RecyclerView
            setupRecyclerView()

            // Detect available cameras and build settings list
            lifecycleScope.launch {
                try {
                    detectAvailableCameras()
                    buildAndSubmitSettingsList()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to detect cameras", e)
                    buildAndSubmitSettingsList() // Still build UI even if camera detection fails
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize settings", e)
            Toast.makeText(this, "Settings error: ${e.message}", Toast.LENGTH_LONG).show()
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

    /**
     * Setup RecyclerView with adapter and layout manager
     */
    private fun setupRecyclerView() {
        // Create adapter with callbacks
        settingsAdapter = SettingsAdapter(
            onCameraSelected = { cameraIndex ->
                settingsManager.setDefaultCameraIndex(cameraIndex)
                Toast.makeText(
                    this,
                    "Main camera set to: ${availableCameras.getOrNull(cameraIndex)?.second ?: "Camera $cameraIndex"}",
                    Toast.LENGTH_SHORT
                ).show()
                Log.i(TAG, "Main camera changed to index: $cameraIndex")
            },
            onPluginToggled = { pluginId, isEnabled ->
                settingsManager.setPluginEnabled(pluginId, isEnabled)

                // Find plugin display name for toast
                val plugin = pluginRegistry.getPluginsByCategory().values
                    .flatten()
                    .find { it.name == pluginId }

                Toast.makeText(
                    this,
                    "${plugin?.displayName ?: pluginId} ${if (isEnabled) "enabled" else "disabled"}",
                    Toast.LENGTH_SHORT
                ).show()
                Log.i(TAG, "$pluginId plugin changed to: $isEnabled")
            },
            onSwitchToggled = { key, value ->
                handleSwitchToggle(key, value)
            },
            onDropdownChanged = { key, value ->
                handleDropdownChange(key, value)
            },
            onSliderChanged = { key, value ->
                handleSliderChange(key, value)
            },
            onButtonClicked = { key ->
                handleButtonClick(key)
            }
        )

        // Set up RecyclerView
        binding.settingsRecyclerview.apply {
            layoutManager = LinearLayoutManager(this@SimpleSettingsActivity)
            adapter = settingsAdapter
        }
    }

    /**
     * Build list of settings items and submit to adapter
     */
    private fun buildAndSubmitSettingsList() {
        try {
            val items = mutableListOf<SettingsListItem>()

            // Camera Selection Section
            if (availableCameras.isNotEmpty()) {
                items.add(SettingsListItem.CategoryHeader("Camera Selection"))

                val selectedCameraIndex = settingsManager.defaultCameraIndex.value
                availableCameras.forEach { (index, name) ->
                    items.add(
                        SettingsListItem.CameraItem(
                            cameraIndex = index,
                            cameraName = name,
                            isSelected = index == selectedCameraIndex
                        )
                    )
                }

                items.add(SettingsListItem.SectionDivider)
            }

            // Photo Settings Section
            items.add(SettingsListItem.CategoryHeader("Photo Settings"))
            items.add(SettingsListItem.SliderItem(
                key = "photo_quality",
                title = "Photo Quality",
                description = "JPEG compression quality (1-100%)",
                min = 1,
                max = 100,
                currentValue = settingsManager.photoQuality.value
            ))
            items.add(SettingsListItem.DropdownItem(
                key = "photo_resolution",
                title = "Photo Resolution",
                description = "Resolution for captured photos",
                options = listOf(
                    "Auto" to "auto",
                    "4K (4096×3072)" to "4k",
                    "Full HD (1920×1080)" to "1080p",
                    "HD (1280×720)" to "720p"
                ),
                currentValue = settingsManager.getPhotoResolution()
            ))
            items.add(SettingsListItem.SwitchItem(
                key = "grid_overlay",
                title = "Grid Overlay Default",
                description = "Show composition grid by default on app start",
                isChecked = settingsManager.gridOverlay.value
            ))
            items.add(SettingsListItem.SectionDivider)

            // Video Settings Section
            items.add(SettingsListItem.CategoryHeader("Video Settings"))
            items.add(SettingsListItem.DropdownItem(
                key = "video_quality",
                title = "Video Quality",
                description = "Resolution for video recording",
                options = listOf(
                    "4K UHD (3840×2160)" to "4k",
                    "Full HD (1920×1080)" to "1080p",
                    "HD (1280×720)" to "720p"
                ),
                currentValue = settingsManager.getVideoQuality()
            ))
            items.add(SettingsListItem.SwitchItem(
                key = "video_stabilization",
                title = "Video Stabilization",
                description = "Enable electronic image stabilization",
                isChecked = settingsManager.getVideoStabilization()
            ))
            items.add(SettingsListItem.SectionDivider)

            // Focus Settings Section
            items.add(SettingsListItem.CategoryHeader("Focus Settings"))
            items.add(SettingsListItem.DropdownItem(
                key = "auto_focus_mode",
                title = "Auto Focus Mode",
                description = "Default focus behavior",
                options = listOf(
                    "Continuous" to "continuous",
                    "Single Shot" to "single",
                    "Manual" to "manual"
                ),
                currentValue = settingsManager.getAutoFocusMode()
            ))
            items.add(SettingsListItem.SwitchItem(
                key = "tap_to_focus",
                title = "Tap to Focus",
                description = "Enable tap-to-focus functionality",
                isChecked = settingsManager.getTapToFocus()
            ))
            items.add(SettingsListItem.SectionDivider)

            // Plugin Settings grouped by category
            val pluginsByCategory = pluginRegistry.getPluginsByCategory()

            // Category display names
            val categoryNames = mapOf(
                PluginCategory.OVERLAYS to "Overlay Plugins",
                PluginCategory.ANALYSIS to "Analysis Plugins",
                PluginCategory.CONTROLS to "Control Plugins",
                PluginCategory.AI to "AI-Powered Features",
                PluginCategory.CAPTURE to "Capture Features"
            )

            // Iterate through categories in order
            val orderedCategories = listOf(
                PluginCategory.OVERLAYS,
                PluginCategory.ANALYSIS,
                PluginCategory.CONTROLS,
                PluginCategory.AI,
                PluginCategory.CAPTURE
            )

            for (category in orderedCategories) {
                val plugins = pluginsByCategory[category] ?: continue
                if (plugins.isEmpty()) continue

                // Add category header
                items.add(SettingsListItem.CategoryHeader(categoryNames[category] ?: category.name))

                // Add plugin items
                for (pluginInfo in plugins) {
                    items.add(
                        SettingsListItem.PluginItem.fromPluginInfo(
                            pluginInfo,
                            settingsManager.isPluginEnabled(pluginInfo.name)
                        )
                    )
                }

                // Add section divider after each category
                items.add(SettingsListItem.SectionDivider)
            }

            // Advanced Settings Section
            items.add(SettingsListItem.CategoryHeader("Advanced Settings"))
            items.add(SettingsListItem.SwitchItem(
                key = "debug_logging",
                title = "Debug Logging",
                description = "Enable verbose logging for troubleshooting",
                isChecked = settingsManager.debugLogging.value
            ))
            items.add(SettingsListItem.SwitchItem(
                key = "performance_monitoring",
                title = "Performance Monitoring",
                description = "Track plugin performance metrics",
                isChecked = settingsManager.getPerformanceMonitoring()
            ))
            items.add(SettingsListItem.SwitchItem(
                key = "raw_capture",
                title = "RAW Capture",
                description = "Enable RAW photo format (if supported)",
                isChecked = settingsManager.getRawCapture()
            ))
            items.add(SettingsListItem.SectionDivider)

            // About Section
            try {
                val packageInfo = packageManager.getPackageInfo(packageName, 0)
                val versionName = packageInfo.versionName ?: "Unknown"
                val versionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    packageInfo.longVersionCode.toString()
                } else {
                    @Suppress("DEPRECATION")
                    packageInfo.versionCode.toString()
                }

                items.add(SettingsListItem.CategoryHeader("About CustomCamera"))
                items.add(SettingsListItem.InfoItem(
                    key = "app_version",
                    title = "Version",
                    description = "Current app version",
                    value = versionName
                ))
                items.add(SettingsListItem.InfoItem(
                    key = "app_build",
                    title = "Build Code",
                    description = "Internal build number",
                    value = versionCode
                ))
                items.add(SettingsListItem.ButtonItem(
                    key = "check_updates",
                    title = "Check for Updates",
                    description = "Check GitHub for latest version"
                ))
            } catch (e: Exception) {
                Log.e(TAG, "Error getting package info", e)
            }

            // Submit to adapter
            settingsAdapter.submitList(items)

            Log.i(TAG, "Built settings list with ${items.size} items (${pluginsByCategory.values.sumOf { it.size }} plugins)")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to build settings list", e)
            Toast.makeText(this, "Settings error: ${e.message}", Toast.LENGTH_SHORT).show()
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

    // ========================================================================
    // Setting Handlers
    // ========================================================================

    private fun handleSwitchToggle(key: String, value: Boolean) {
        when (key) {
            "grid_overlay" -> settingsManager.setGridOverlay(value)
            "tap_to_focus" -> settingsManager.setTapToFocus(value)
            "video_stabilization" -> settingsManager.setVideoStabilization(value)
            "debug_logging" -> settingsManager.setDebugLogging(value)
            "performance_monitoring" -> settingsManager.setPerformanceMonitoring(value)
            "raw_capture" -> settingsManager.setRawCapture(value)
            "camera_info_overlay" -> settingsManager.setCameraInfoOverlay(value)
            "histogram_overlay" -> settingsManager.setHistogramOverlay(value)
            "exposure_lock" -> settingsManager.setPluginSetting("ExposureControl", "exposureLocked", value.toString())
            "manual_controls_enabled" -> settingsManager.setPluginSetting("ProControls", "manualModeEnabled", value.toString())
            else -> Log.w(TAG, "Unknown switch key: $key")
        }
        Toast.makeText(this, "Setting updated", Toast.LENGTH_SHORT).show()
        Log.i(TAG, "Switch setting changed: $key = $value")
    }

    private fun handleDropdownChange(key: String, value: String) {
        when (key) {
            "photo_resolution" -> settingsManager.setPhotoResolution(value)
            "video_quality" -> settingsManager.setVideoQuality(value)
            "auto_focus_mode" -> settingsManager.setAutoFocusMode(value)
            "grid_type" -> settingsManager.setPluginSetting("GridOverlay", "gridType", value)
            else -> Log.w(TAG, "Unknown dropdown key: $key")
        }
        Toast.makeText(this, "Setting updated", Toast.LENGTH_SHORT).show()
        Log.i(TAG, "Dropdown setting changed: $key = $value")
    }

    private fun handleSliderChange(key: String, value: Int) {
        when (key) {
            "photo_quality" -> settingsManager.setPhotoQuality(value)
            "default_exposure" -> settingsManager.setPluginSetting("ExposureControl", "exposureIndex", value.toString())
            "processing_interval" -> settingsManager.setPluginSetting("CameraInfo", "processingInterval", value.toString())
            else -> Log.w(TAG, "Unknown slider key: $key")
        }
        Toast.makeText(this, "Setting updated", Toast.LENGTH_SHORT).show()
        Log.i(TAG, "Slider setting changed: $key = $value")
    }

    private fun handleButtonClick(key: String) {
        when (key) {
            "check_updates" -> openGitHubReleases()
            else -> {
                Log.w(TAG, "Unknown button key: $key")
                Toast.makeText(this, "Feature not yet implemented", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openGitHubReleases() {
        try {
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                data = android.net.Uri.parse("https://github.com/tribixbite/CustomCamera/releases")
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open GitHub releases", e)
            Toast.makeText(this, "Could not open browser", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private const val TAG = "SimpleSettingsActivity"
    }
}