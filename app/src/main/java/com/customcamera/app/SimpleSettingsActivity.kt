package com.customcamera.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
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
import com.customcamera.app.debug.CameraAPIMonitor
import com.customcamera.app.debug.CameraResetManager
import com.customcamera.app.engine.CameraContext
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
    private lateinit var cameraManager: CameraManager
    private var cameraAPIMonitor: CameraAPIMonitor? = null
    private var cameraResetManager: CameraResetManager? = null
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
            settingsManager = SettingsManager.getInstance(this)
            pluginRegistry = PluginRegistry(this)
            debugLogger = DebugLogger()

            // Initialize Camera2 manager for detailed characteristics
            cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager

            // Get global API monitor instance
            cameraAPIMonitor = com.customcamera.app.debug.GlobalAPIMonitor.getInstance()

            // Initialize camera reset manager (requires CameraContext)
            lifecycleScope.launch {
                try {
                    val cameraProvider = ProcessCameraProvider.getInstance(this@SimpleSettingsActivity).get()
                    val cameraContext = CameraContext(
                        context = this@SimpleSettingsActivity,
                        cameraProvider = cameraProvider,
                        debugLogger = debugLogger,
                        settingsManager = settingsManager,
                        cameraEngine = null,  // No active camera engine in settings
                        apiMonitor = cameraAPIMonitor
                    )
                    cameraResetManager = CameraResetManager(cameraContext)
                    Log.i(TAG, "Camera reset manager initialized")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to initialize camera reset manager", e)
                }
            }

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
            onCameraSelected = { cameraIndex, isPipCamera ->
                if (isPipCamera) {
                    // PiP camera selection
                    settingsManager.setPipCameraIndex(cameraIndex)
                    Toast.makeText(
                        this,
                        "PiP camera set to: ${availableCameras.getOrNull(cameraIndex)?.second ?: "Camera $cameraIndex"}",
                        Toast.LENGTH_SHORT
                    ).show()
                    Log.i(TAG, "PiP camera changed to index: $cameraIndex")
                    // Rebuild list to update selection state
                    buildAndSubmitSettingsList()
                } else {
                    // Main camera selection
                    settingsManager.setDefaultCameraIndex(cameraIndex)
                    Toast.makeText(
                        this,
                        "Main camera set to: ${availableCameras.getOrNull(cameraIndex)?.second ?: "Camera $cameraIndex"}",
                        Toast.LENGTH_SHORT
                    ).show()
                    Log.i(TAG, "Main camera changed to index: $cameraIndex")
                    // Rebuild list to update selection state
                    buildAndSubmitSettingsList()
                }
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

            // Main Camera Selection Section
            if (availableCameras.isNotEmpty()) {
                items.add(SettingsListItem.CategoryHeader("Main Camera Selection"))

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

            // Dual Camera PiP Settings Section
            if (availableCameras.size >= 2) {
                Log.i(TAG, "Adding PiP camera section (${availableCameras.size} cameras available)")
                items.add(SettingsListItem.CategoryHeader("Dual Camera (PiP) Settings"))

                val selectedPipCameraIndex = settingsManager.pipCameraIndex.value
                Log.i(TAG, "Selected PiP camera index: $selectedPipCameraIndex")
                availableCameras.forEach { (index, name) ->
                    Log.i(TAG, "Adding PiP camera item: index=$index, name=$name")
                    items.add(
                        SettingsListItem.CameraItem(
                            cameraIndex = index,
                            cameraName = "$name (PiP)",
                            isSelected = index == selectedPipCameraIndex,
                            isPipCamera = true  // Mark as PiP camera item
                        )
                    )
                }

                // Info item after cameras so they're visible immediately
                items.add(SettingsListItem.InfoItem(
                    key = "pip_camera_info",
                    title = "PiP Camera Selection",
                    description = "Select the camera to use for Picture-in-Picture mode",
                    value = "Front camera recommended for PiP when main is back camera"
                ))

                Log.i(TAG, "Total items after PiP section: ${items.size}")

                items.add(SettingsListItem.SectionDivider)
            } else {
                Log.w(TAG, "PiP section skipped - only ${availableCameras.size} cameras available")
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
            items.add(SettingsListItem.DropdownItem(
                key = "flash_mode",
                title = "Flash Mode",
                description = "Default flash behavior",
                options = listOf(
                    "Auto" to "auto",
                    "On" to "on",
                    "Off" to "off",
                    "Torch" to "torch"
                ),
                currentValue = settingsManager.flashMode.value
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
            items.add(SettingsListItem.SwitchItem(
                key = "level_indicator",
                title = "Level Indicator",
                description = "Show horizon level indicator for straight shots",
                isChecked = settingsManager.getLevelIndicator()
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
            items.add(SettingsListItem.ButtonItem(
                key = "view_api_log",
                title = "View API Call Log",
                description = "View camera API calls and performance metrics"
            ))
            items.add(SettingsListItem.ButtonItem(
                key = "reset_camera_system",
                title = "Reset Camera System",
                description = "Reinitialize camera provider (fixes stuck cameras)"
            ))
            items.add(SettingsListItem.ButtonItem(
                key = "flush_camera_queue",
                title = "Flush Camera Queue",
                description = "Clear camera operation queue (fixes delayed operations)"
            ))
            items.add(SettingsListItem.SectionDivider)

            // Plugin Browser & Management Section
            items.add(SettingsListItem.CategoryHeader("Plugin Browser & Management"))
            items.add(SettingsListItem.ButtonItem(
                key = "browse_plugins",
                title = "Browse Available Plugins",
                description = "View and install plugins from the plugin store"
            ))
            items.add(SettingsListItem.ButtonItem(
                key = "import_plugin",
                title = "Import Plugin",
                description = "Import plugin from file (.apk or .jar)"
            ))
            items.add(SettingsListItem.ButtonItem(
                key = "export_plugin_config",
                title = "Export Plugin Configuration",
                description = "Export current plugin settings and list"
            ))
            items.add(SettingsListItem.ButtonItem(
                key = "manage_plugins",
                title = "Manage Installed Plugins",
                description = "View, update, or remove installed plugins"
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
                items.add(SettingsListItem.InfoItem(
                    key = "build_date",
                    title = "Build Date",
                    description = "When this version was compiled",
                    value = BuildConfig.BUILD_DATE
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
            "level_indicator" -> settingsManager.setLevelIndicator(value)
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
            "flash_mode" -> settingsManager.setFlashMode(value)
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
            // Debug system buttons
            "view_api_log" -> viewAPICallLog()
            "reset_camera_system" -> resetCameraSystem()
            "flush_camera_queue" -> flushCameraQueue()
            // Plugin management buttons
            "browse_plugins" -> launchPluginBrowser()
            "import_plugin" -> launchPluginImporter()
            "export_plugin_config" -> exportPluginConfiguration()
            "manage_plugins" -> launchPluginManager()
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
                val cameraIds = cameraManager.cameraIdList

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

                    // Camera2 characteristics
                    val camera2Info = StringBuilder()
                    if (index < cameraIds.size) {
                        try {
                            val characteristics = cameraManager.getCameraCharacteristics(cameraIds[index])

                            // Sensor physical size
                            val sensorSize = characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
                            if (sensorSize != null) {
                                camera2Info.append("\n  Sensor Size: ${String.format("%.2f", sensorSize.width)}mm × ${String.format("%.2f", sensorSize.height)}mm")
                            }

                            // Sensor resolution
                            val activeArraySize = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
                            if (activeArraySize != null) {
                                camera2Info.append("\n  Sensor Resolution: ${activeArraySize.width()} × ${activeArraySize.height()}")
                            }

                            // Focal length
                            val focalLengths = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                            if (focalLengths != null && focalLengths.isNotEmpty()) {
                                camera2Info.append("\n  Focal Length: ${focalLengths.joinToString(", ")}mm")
                            }

                            // ISO range
                            val isoRange = characteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
                            if (isoRange != null) {
                                camera2Info.append("\n  ISO Range: ${isoRange.lower} - ${isoRange.upper}")
                            }

                            // Exposure time range
                            val exposureTimeRange = characteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
                            if (exposureTimeRange != null) {
                                val minMs = exposureTimeRange.lower / 1_000_000.0
                                val maxMs = exposureTimeRange.upper / 1_000_000.0
                                camera2Info.append("\n  Exposure Time: ${String.format("%.3f", minMs)}ms - ${String.format("%.1f", maxMs)}ms")
                            }

                            // Hardware level
                            val hardwareLevel = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
                            val levelName = when (hardwareLevel) {
                                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "Legacy"
                                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "Limited"
                                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "Full"
                                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "Level 3"
                                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL -> "External"
                                else -> "Unknown"
                            }
                            camera2Info.append("\n  Hardware Level: $levelName")

                            // Max digital zoom
                            val maxDigitalZoom = characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM)
                            if (maxDigitalZoom != null) {
                                camera2Info.append("\n  Max Digital Zoom: ${String.format("%.1f", maxDigitalZoom)}x")
                            }

                            // Focus modes
                            val afModes = characteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)
                            if (afModes != null) {
                                camera2Info.append("\n  Focus Modes: ${afModes.size} available")
                            }
                        } catch (e: CameraAccessException) {
                            Log.e(TAG, "Error getting Camera2 characteristics for camera $index", e)
                        }
                    }

                    """
                    Camera $index ($facing):
                      Flash Unit: ${if (hasFlash) "Yes" else "No"}
                      Sensor Rotation: ${rotation}°
                      Zoom Range: $minZoom - $maxZoom
                      Exposure Compensation: $exposureRange$camera2Info
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

    private fun launchPluginBrowser() {
        lifecycleScope.launch {
            try {
                // Mock plugin store with available plugins
                val availablePlugins = listOf(
                    "Pro Focus Plugin v2.1" to "Advanced autofocus with AI tracking",
                    "HDR+ Plugin v1.5" to "Multi-frame HDR processing",
                    "Night Vision Plugin v1.3" to "Enhanced low-light photography",
                    "Portrait Mode Plugin v2.0" to "AI-powered background blur",
                    "Timelapse Pro Plugin v1.7" to "Advanced timelapse features",
                    "ML Enhance Plugin v1.2" to "Machine learning image enhancement"
                )

                val pluginNames = availablePlugins.map { "${it.first}\n${it.second}" }.toTypedArray()

                AlertDialog.Builder(this@SimpleSettingsActivity)
                    .setTitle("Available Plugins")
                    .setItems(pluginNames) { _, which ->
                        val selectedPlugin = availablePlugins[which]
                        Toast.makeText(
                            this@SimpleSettingsActivity,
                            "Selected: ${selectedPlugin.first}",
                            Toast.LENGTH_SHORT
                        ).show()
                        debugLogger.logInfo("Plugin browser: selected ${selectedPlugin.first}", emptyMap(), "Settings")
                    }
                    .setNegativeButton("Close", null)
                    .show()

                Log.i(TAG, "Plugin browser opened")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open plugin browser", e)
                Toast.makeText(this@SimpleSettingsActivity, "Plugin browser error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun launchPluginImporter() {
        lifecycleScope.launch {
            try {
                // Create file picker for plugin import
                val intent = android.content.Intent(android.content.Intent.ACTION_GET_CONTENT).apply {
                    type = "*/*"
                    addCategory(android.content.Intent.CATEGORY_OPENABLE)
                    putExtra(android.content.Intent.EXTRA_MIME_TYPES, arrayOf(
                        "application/vnd.android.package-archive",
                        "application/java-archive"
                    ))
                }

                try {
                    startActivity(android.content.Intent.createChooser(intent, "Select Plugin File"))
                    Toast.makeText(this@SimpleSettingsActivity, "Select .apk or .jar plugin file", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    // Fallback: Show manual instruction
                    AlertDialog.Builder(this@SimpleSettingsActivity)
                        .setTitle("Import Plugin")
                        .setMessage("To import a plugin:\n\n1. Place plugin file (.apk or .jar) in Downloads folder\n2. Plugins will be scanned automatically\n3. Enable in Plugin Settings section\n\nSupported formats:\n• .apk (Android Plugin)\n• .jar (Java Plugin)")
                        .setPositiveButton("OK", null)
                        .show()
                }

                debugLogger.logInfo("Plugin importer opened", emptyMap(), "Settings")
                Log.i(TAG, "Plugin importer launched")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to launch plugin importer", e)
                Toast.makeText(this@SimpleSettingsActivity, "Import error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun exportPluginConfiguration() {
        lifecycleScope.launch {
            try {
                val enabledPlugins = pluginRegistry.getSupportedProviders()
                    .filter { settingsManager.isPluginEnabled(it.id) }

                val exportData = """
                    === Plugin Configuration Export ===
                    Generated: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}

                    Total Plugins: ${pluginRegistry.getSupportedProviders().size}
                    Enabled Plugins: ${enabledPlugins.size}

                    === Enabled Plugins ===
                    ${enabledPlugins.joinToString("\n") { provider ->
                        "• ${provider.id} (${provider.category})"
                    }}

                    === All Plugins ===
                    ${pluginRegistry.getSupportedProviders().joinToString("\n") { provider ->
                        val status = if (settingsManager.isPluginEnabled(provider.id)) "✓" else "○"
                        "$status ${provider.id} - ${provider.category}"
                    }}
                """.trimIndent()

                Log.i(TAG, "Plugin configuration exported:\n$exportData")

                AlertDialog.Builder(this@SimpleSettingsActivity)
                    .setTitle("Plugin Configuration Exported")
                    .setMessage("Plugin configuration exported.\n\nTotal: ${pluginRegistry.getSupportedProviders().size} plugins\nEnabled: ${enabledPlugins.size} plugins")
                    .setPositiveButton("Copy to Clipboard") { _, _ ->
                        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Plugin Config", exportData)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(this@SimpleSettingsActivity, "Plugin configuration copied", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Close", null)
                    .show()

                debugLogger.logInfo("Plugin configuration exported", mapOf("enabled" to enabledPlugins.size), "Settings")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to export plugin configuration", e)
                Toast.makeText(this@SimpleSettingsActivity, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun launchPluginManager() {
        lifecycleScope.launch {
            try {
                val installedPlugins = pluginRegistry.getSupportedProviders()

                val pluginInfo = installedPlugins.map { provider ->
                    val enabled = settingsManager.isPluginEnabled(provider.id)
                    val status = if (enabled) "✓ Enabled" else "○ Disabled"
                    "${provider.id} - $status"
                }.toTypedArray()

                AlertDialog.Builder(this@SimpleSettingsActivity)
                    .setTitle("Installed Plugins (${installedPlugins.size})")
                    .setItems(pluginInfo) { _, which ->
                        val plugin = installedPlugins[which]
                        showPluginDetailsDialog(plugin)
                    }
                    .setNegativeButton("Close", null)
                    .show()

                Log.i(TAG, "Plugin manager opened")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open plugin manager", e)
                Toast.makeText(this@SimpleSettingsActivity, "Plugin manager error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showPluginDetailsDialog(provider: com.customcamera.app.engine.plugins.PluginProvider) {
        val isEnabled = settingsManager.isPluginEnabled(provider.id)

        val details = """
            Plugin ID: ${provider.id}
            Status: ${if (isEnabled) "✓ Enabled" else "○ Disabled"}
            Category: ${provider.category}
            User Toggleable: ${if (provider.userToggleable) "Yes" else "No"}

            To toggle this plugin, use the Plugin Settings section in the main settings list.
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle(provider.id)
            .setMessage(details)
            .setPositiveButton("OK", null)
            .show()
    }

    /**
     * View Camera API call log from CameraAPIMonitor
     */
    private fun viewAPICallLog() {
        lifecycleScope.launch {
            try {
                val logData = cameraAPIMonitor?.let { monitor ->
                    val report = monitor.generateDebugReport()
                    val stats = monitor.getAPICallStats()

                    if ((stats["totalCalls"] as? Int) ?: 0 > 0) {
                        report
                    } else {
                        "No API calls tracked yet.\n\nAPI calls are tracked when camera is active."
                    }
                } ?: "No active camera session detected.\n\nAPI monitor is available when camera engine is running."

                AlertDialog.Builder(this@SimpleSettingsActivity)
                    .setTitle("Camera API Call Log")
                    .setMessage(logData)
                    .setPositiveButton("Copy to Clipboard") { _, _ ->
                        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("API Call Log", logData)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(this@SimpleSettingsActivity, "API log copied", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Close", null)
                    .show()

                Log.i(TAG, "API Call Log:\n$logData")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to view API call log", e)
                Toast.makeText(this@SimpleSettingsActivity, "API log error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Reset camera system using CameraResetManager
     */
    private fun resetCameraSystem() {
        lifecycleScope.launch {
            try {
                if (cameraResetManager == null) {
                    Toast.makeText(this@SimpleSettingsActivity, "Camera reset manager not initialized", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                Toast.makeText(this@SimpleSettingsActivity, "Resetting camera system...", Toast.LENGTH_SHORT).show()
                Log.i(TAG, "Initiating camera system reset")

                val success = cameraResetManager!!.reinitializeCameraProvider()

                if (success) {
                    Toast.makeText(this@SimpleSettingsActivity, "✅ Camera system reset complete", Toast.LENGTH_LONG).show()
                    Log.i(TAG, "Camera system reset successful")
                } else {
                    Toast.makeText(this@SimpleSettingsActivity, "⚠️ Camera reset completed with warnings", Toast.LENGTH_LONG).show()
                    Log.w(TAG, "Camera reset completed with warnings")
                }

                debugLogger.logInfo("Camera system reset", mapOf("success" to success), "System")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to reset camera system", e)
                Toast.makeText(this@SimpleSettingsActivity, "Reset failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Flush camera queue using CameraResetManager
     */
    private fun flushCameraQueue() {
        lifecycleScope.launch {
            try {
                if (cameraResetManager == null) {
                    Toast.makeText(this@SimpleSettingsActivity, "Camera reset manager not initialized", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                Toast.makeText(this@SimpleSettingsActivity, "Flushing camera queue...", Toast.LENGTH_SHORT).show()
                Log.i(TAG, "Initiating camera queue flush")

                val success = cameraResetManager!!.flushCameraQueue()

                if (success) {
                    Toast.makeText(this@SimpleSettingsActivity, "✅ Camera queue flushed successfully", Toast.LENGTH_LONG).show()
                    Log.i(TAG, "Camera queue flush successful")
                } else {
                    Toast.makeText(this@SimpleSettingsActivity, "⚠️ Camera queue flush completed with warnings", Toast.LENGTH_LONG).show()
                    Log.w(TAG, "Camera queue flush completed with warnings")
                }

                debugLogger.logInfo("Camera queue flushed", mapOf("success" to success), "System")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to flush camera queue", e)
                Toast.makeText(this@SimpleSettingsActivity, "Flush failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    companion object {
        private const val TAG = "SimpleSettingsActivity"
    }
}