package com.customcamera.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.customcamera.app.databinding.ActivitySimpleSettingsRecyclerviewBinding
import com.customcamera.app.engine.DebugLogger
import com.customcamera.app.engine.SettingsManager
import com.customcamera.app.engine.plugins.PluginCategory
import com.customcamera.app.engine.plugins.PluginRegistry
import com.customcamera.app.ui.settings.SettingsAdapter
import com.customcamera.app.ui.settings.SettingsListItem
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Settings activity with RecyclerView for smooth scrolling performance
 */
class SimpleSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySimpleSettingsRecyclerviewBinding
    private lateinit var settingsManager: SettingsManager
    private lateinit var pluginRegistry: PluginRegistry
    private lateinit var settingsAdapter: SettingsAdapter
    private lateinit var debugLogger: DebugLogger
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
            debugLogger = DebugLogger()

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

            // Grid & Overlays Section
            items.add(SettingsListItem.CategoryHeader("Grid & Overlays"))
            items.add(SettingsListItem.DropdownItem(
                key = "grid_type",
                title = "Grid Type",
                description = "Composition grid style",
                options = listOf(
                    "Rule of Thirds" to "RULE_OF_THIRDS",
                    "Golden Ratio" to "GOLDEN_RATIO",
                    "Center Cross" to "CENTER_CROSS",
                    "Diagonal Lines" to "DIAGONAL_LINES",
                    "Square Grid" to "SQUARE_GRID"
                ),
                currentValue = settingsManager.getPluginSetting("GridOverlay", "gridType", "RULE_OF_THIRDS")
            ))
            items.add(SettingsListItem.SwitchItem(
                key = "camera_info_overlay",
                title = "Camera Info Overlay",
                description = "Show camera information on screen",
                isChecked = settingsManager.getCameraInfoOverlay()
            ))
            items.add(SettingsListItem.SwitchItem(
                key = "histogram_overlay",
                title = "Histogram Overlay",
                description = "Show exposure histogram",
                isChecked = settingsManager.getHistogramOverlay()
            ))
            items.add(SettingsListItem.SectionDivider)

            // Manual Controls Section
            items.add(SettingsListItem.CategoryHeader("Manual Controls"))
            items.add(SettingsListItem.SwitchItem(
                key = "manual_controls_enabled",
                title = "Enable Manual Controls",
                description = "Show professional camera controls",
                isChecked = settingsManager.getPluginSetting("ProControls", "manualModeEnabled", "false").toBoolean()
            ))
            items.add(SettingsListItem.SliderItem(
                key = "default_exposure",
                title = "Default Exposure Compensation",
                description = "Initial exposure compensation (-6 to +6)",
                min = -6,
                max = 6,
                currentValue = settingsManager.getPluginSetting("ExposureControl", "exposureIndex", "0").toInt()
            ))
            items.add(SettingsListItem.SwitchItem(
                key = "exposure_lock",
                title = "Exposure Lock",
                description = "Lock exposure at startup",
                isChecked = settingsManager.getPluginSetting("ExposureControl", "exposureLocked", "false").toBoolean()
            ))
            items.add(SettingsListItem.SectionDivider)

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
            items.add(SettingsListItem.SliderItem(
                key = "processing_interval",
                title = "Processing Interval",
                description = "Frame processing interval (ms)",
                min = 100,
                max = 5000,
                currentValue = settingsManager.getPluginSetting("CameraInfo", "processingInterval", "1000").toInt()
            ))
            items.add(SettingsListItem.SwitchItem(
                key = "raw_capture",
                title = "RAW Capture",
                description = "Enable RAW photo format (if supported)",
                isChecked = settingsManager.getRawCapture()
            ))
            items.add(SettingsListItem.SectionDivider)

            // Debug & System Info Section
            items.add(SettingsListItem.CategoryHeader("Debug & System Info"))
            items.add(SettingsListItem.ButtonItem(
                key = "show_debug_log",
                title = "Show Debug Log",
                description = "View recent debug log entries and statistics"
            ))
            items.add(SettingsListItem.ButtonItem(
                key = "show_camera_info",
                title = "Camera System Details",
                description = "View detailed camera hardware information"
            ))
            items.add(SettingsListItem.ButtonItem(
                key = "export_settings",
                title = "Export Settings",
                description = "Export current configuration to log"
            ))
            items.add(SettingsListItem.ButtonItem(
                key = "reset_settings",
                title = "Reset to Defaults",
                description = "Reset all settings to default values"
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
            "show_debug_log" -> showDebugLog()
            "show_camera_info" -> showCameraSystemInfo()
            "export_settings" -> exportSettings()
            "reset_settings" -> resetSettings()
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

    private fun showDebugLog() {
        lifecycleScope.launch {
            try {
                val logStats = debugLogger.getLogStats()
                val recentEntries = debugLogger.getLogEntries(limit = 50)

                val debugInfo = """
                    === Debug Log Statistics ===
                    Total entries: ${logStats.totalEntries}
                    Level counts: ${logStats.levelCounts}
                    Category counts: ${logStats.categoryCounts}

                    Time range:
                    Oldest: ${logStats.oldestEntry?.let { formatTimestamp(it) } ?: "N/A"}
                    Newest: ${logStats.newestEntry?.let { formatTimestamp(it) } ?: "N/A"}

                    === Recent Entries (${recentEntries.size}) ===
                    ${recentEntries.joinToString("\n") {
                        "${formatTimestamp(it.timestamp)} [${it.level}] ${it.tag}: ${it.message}"
                    }}
                """.trimIndent()

                Log.i(TAG, "Debug log info:\n$debugInfo")

                AlertDialog.Builder(this@SimpleSettingsActivity)
                    .setTitle("Debug Log")
                    .setMessage("Total: ${logStats.totalEntries} entries\nShowing last ${recentEntries.size} entries\n\n" +
                        "Level counts:\n${logStats.levelCounts.entries.joinToString("\n") { "  ${it.key}: ${it.value}" }}\n\n" +
                        "Category counts:\n${logStats.categoryCounts.entries.filter { it.value > 0 }.joinToString("\n") { "  ${it.key}: ${it.value}" }}\n\n" +
                        "Tap 'Copy Full Log' to get detailed entries.")
                    .setPositiveButton("Copy Full Log") { _, _ ->
                        val fullLog = debugLogger.exportDebugLog(includeDetails = true)
                        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Debug Log", fullLog)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(this@SimpleSettingsActivity, "Full debug log copied to clipboard", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Close", null)
                    .show()

            } catch (e: Exception) {
                Log.e(TAG, "Failed to show debug log", e)
                Toast.makeText(this@SimpleSettingsActivity, "Debug log error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun formatTimestamp(timestamp: Long): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date(timestamp))
    }

    private fun showCameraSystemInfo() {
        lifecycleScope.launch {
            try {
                val cameraProvider = ProcessCameraProvider.getInstance(this@SimpleSettingsActivity).get()
                val cameras = cameraProvider.availableCameraInfos

                val cameraDetails = cameras.mapIndexed { index, cameraInfo ->
                    val facing = when (cameraInfo.lensFacing) {
                        CameraSelector.LENS_FACING_FRONT -> "Front"
                        CameraSelector.LENS_FACING_BACK -> "Back"
                        else -> "External"
                    }
                    val hasFlash = try { cameraInfo.hasFlashUnit() } catch (e: Exception) { false }
                    val rotation = try { cameraInfo.sensorRotationDegrees } catch (e: Exception) { 0 }
                    val minZoom = try { cameraInfo.zoomState.value?.minZoomRatio?.toString() ?: "N/A" } catch (e: Exception) { "N/A" }
                    val maxZoom = try { cameraInfo.zoomState.value?.maxZoomRatio?.toString() ?: "N/A" } catch (e: Exception) { "N/A" }

                    // Get exposure compensation range
                    val exposureRange = try {
                        val state = cameraInfo.exposureState
                        "${state.exposureCompensationRange.lower} to ${state.exposureCompensationRange.upper} (step: ${state.exposureCompensationStep})"
                    } catch (e: Exception) { "N/A" }

                    // Get torch support
                    val hasTorch = try { cameraInfo.hasFlashUnit() } catch (e: Exception) { false }

                    // Get focus modes (requires Camera2CameraInfo)
                    val focusModes = try {
                        androidx.camera.camera2.interop.Camera2CameraInfo.from(cameraInfo)
                        "Available"
                    } catch (e: Exception) { "Unknown" }

                    """
                    Camera $index ($facing):
                      Flash Unit: ${if (hasFlash) "Yes" else "No"}
                      Torch: ${if (hasTorch) "Yes" else "No"}
                      Sensor Rotation: ${rotation}°
                      Zoom Range: $minZoom - $maxZoom
                      Exposure Compensation: $exposureRange
                      Focus Modes: $focusModes
                    """.trimIndent()
                }.joinToString("\n\n")

                // Get enabled plugins count
                val enabledPluginsCount = pluginRegistry.getSupportedProviders().count {
                    settingsManager.isPluginEnabled(it.id)
                }

                val systemInfo = """
                    === Camera System Details ===
                    Total Cameras: ${cameras.size}
                    Default Camera: ${settingsManager.defaultCameraIndex.value}

                    $cameraDetails

                    === Device Info ===
                    Manufacturer: ${Build.MANUFACTURER}
                    Model: ${Build.MODEL}
                    Device: ${Build.DEVICE}
                    Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})
                    CameraX Version: 1.3.1

                    === App Configuration ===
                    Enabled Plugins: $enabledPluginsCount
                    Photo Quality: ${settingsManager.photoQuality.value}%
                    Video Quality: ${settingsManager.getVideoQuality()}
                    Debug Logging: ${if (settingsManager.debugLogging.value) "Enabled" else "Disabled"}
                    RAW Capture: ${if (settingsManager.getRawCapture()) "Enabled" else "Disabled"}
                """.trimIndent()

                Log.i(TAG, systemInfo)

                // Show dialog with scrollable view for long content
                AlertDialog.Builder(this@SimpleSettingsActivity)
                    .setTitle("Camera System Details")
                    .setMessage(systemInfo)
                    .setPositiveButton("Copy to Clipboard") { _, _ ->
                        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Camera Info", systemInfo)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(this@SimpleSettingsActivity, "Camera info copied to clipboard", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Close", null)
                    .show()

            } catch (e: Exception) {
                Log.e(TAG, "Failed to get camera info", e)
                Toast.makeText(this@SimpleSettingsActivity, "Camera info error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun exportSettings() {
        lifecycleScope.launch {
            try {
                val settings = settingsManager.exportSettings()
                val debugData = debugLogger.exportDebugLog(includeDetails = false)

                val exportData = """
                    === CustomCamera Settings Export ===
                    Generated: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}

                    === Settings (${settings.size} items) ===
                    ${settings.entries.sortedBy { it.key }.joinToString("\n") { "${it.key} = ${it.value}" }}

                    === Enabled Plugins (${pluginRegistry.getSupportedProviders().count { settingsManager.isPluginEnabled(it.id) }}) ===
                    ${pluginRegistry.getSupportedProviders().filter {
                        settingsManager.isPluginEnabled(it.id)
                    }.joinToString("\n") { "• ${it.id}" }}

                    === Recent Debug Log ===
                    $debugData
                """.trimIndent()

                Log.i(TAG, "Settings exported:\n$exportData")

                AlertDialog.Builder(this@SimpleSettingsActivity)
                    .setTitle("Settings Exported")
                    .setMessage("Settings, enabled plugins, and debug log exported.\n\nTap 'Copy to Clipboard' to copy all data.")
                    .setPositiveButton("Copy to Clipboard") { _, _ ->
                        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Settings Export", exportData)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(this@SimpleSettingsActivity, "Export copied to clipboard", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Close", null)
                    .show()

            } catch (e: Exception) {
                Log.e(TAG, "Failed to export settings", e)
                Toast.makeText(this@SimpleSettingsActivity, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun resetSettings() {
        // Show confirmation dialog
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Reset Settings")
            .setMessage("Reset all settings to default values?\n\nThis will:\n• Reset photo/video quality\n• Reset all plugin states\n• Clear manual control preferences\n\nThis cannot be undone.")
            .setPositiveButton("Reset") { _, _ ->
                lifecycleScope.launch {
                    try {
                        settingsManager.resetToDefaults()

                        // Rebuild settings list with defaults
                        buildAndSubmitSettingsList()

                        Toast.makeText(this@SimpleSettingsActivity, "Settings reset to defaults", Toast.LENGTH_LONG).show()
                        Log.i(TAG, "Settings reset to defaults")

                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to reset settings", e)
                        Toast.makeText(this@SimpleSettingsActivity, "Reset failed: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    companion object {
        private const val TAG = "SimpleSettingsActivity"
    }
}