package com.customcamera.app

import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.customcamera.app.databinding.ActivitySettingsBinding
import com.customcamera.app.engine.CameraEngine
import com.customcamera.app.engine.DebugLogger
import com.customcamera.app.engine.SettingsManager
import com.customcamera.app.plugins.*
import com.customcamera.app.settings.*
import kotlinx.coroutines.launch

/**
 * Comprehensive settings activity that provides configuration for all camera features
 * and plugins. Demonstrates the full capabilities of the plugin system.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var settingsManager: SettingsManager
    private lateinit var debugLogger: DebugLogger
    private lateinit var settingsAdapter: SettingsAdapter

    // Mock engine and plugins for settings configuration
    private var mockCameraEngine: CameraEngine? = null
    private val settingsSections = mutableListOf<SettingsSection>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "SettingsActivity onCreate")

        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        initializeSettings()
        setupSettingsUI()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            title = "Camera Settings"
        }
    }

    private fun initializeSettings() {
        settingsManager = SettingsManager(this)
        debugLogger = DebugLogger()

        Log.i(TAG, "Settings initialized")
    }

    private fun setupSettingsUI() {
        // Create settings sections
        createSettingsSections()

        // Setup RecyclerView
        settingsAdapter = SettingsAdapter(settingsSections) { setting, value ->
            handleSettingChange(setting, value)
        }

        binding.settingsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@SettingsActivity)
            adapter = settingsAdapter
        }

        // Setup action buttons
        binding.exportSettingsButton.setOnClickListener { exportSettings() }
        binding.resetSettingsButton.setOnClickListener { resetSettings() }
        binding.debugLogButton.setOnClickListener { showDebugLog() }

        Log.i(TAG, "Settings UI setup complete")
    }

    private fun createSettingsSections() {
        settingsSections.clear()

        // Camera Settings Section
        settingsSections.add(
            SettingsSection(
                title = "Camera Settings",
                icon = R.drawable.ic_camera,
                settings = listOf(
                    SettingsItem.Dropdown(
                        key = "default_camera_index",
                        title = "Default Camera",
                        description = "Camera to use when app starts",
                        options = listOf("Back Camera" to "0", "Front Camera" to "1", "External Camera" to "2"),
                        currentValue = settingsManager.defaultCameraIndex.value.toString()
                    ),
                    SettingsItem.Slider(
                        key = "photo_quality",
                        title = "Photo Quality",
                        description = "JPEG compression quality (1-100%)",
                        min = 1,
                        max = 100,
                        currentValue = settingsManager.photoQuality.value
                    ),
                    SettingsItem.Dropdown(
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
                    ),
                    SettingsItem.Switch(
                        key = "grid_overlay",
                        title = "Grid Overlay",
                        description = "Show composition grid by default",
                        isChecked = settingsManager.gridOverlay.value
                    )
                )
            )
        )

        // Focus Settings Section
        settingsSections.add(
            SettingsSection(
                title = "Focus Settings",
                icon = R.drawable.ic_focus,
                settings = listOf(
                    SettingsItem.Dropdown(
                        key = "auto_focus_mode",
                        title = "Auto Focus Mode",
                        description = "Default focus behavior",
                        options = listOf(
                            "Continuous" to "continuous",
                            "Single Shot" to "single",
                            "Manual" to "manual"
                        ),
                        currentValue = settingsManager.getAutoFocusMode()
                    ),
                    SettingsItem.Switch(
                        key = "tap_to_focus",
                        title = "Tap to Focus",
                        description = "Enable tap-to-focus functionality",
                        isChecked = settingsManager.getTapToFocus()
                    )
                )
            )
        )

        // Manual Controls Section
        settingsSections.add(
            SettingsSection(
                title = "Manual Controls",
                icon = R.drawable.ic_settings,
                settings = listOf(
                    SettingsItem.Switch(
                        key = "manual_controls_enabled",
                        title = "Enable Manual Controls",
                        description = "Show professional camera controls",
                        isChecked = settingsManager.getPluginSetting("ProControls", "manualModeEnabled", "false").toBoolean()
                    ),
                    SettingsItem.Slider(
                        key = "default_exposure",
                        title = "Default Exposure Compensation",
                        description = "Initial exposure compensation (-6 to +6)",
                        min = -6,
                        max = 6,
                        currentValue = settingsManager.getPluginSetting("ExposureControl", "exposureIndex", "0").toInt()
                    ),
                    SettingsItem.Switch(
                        key = "exposure_lock",
                        title = "Exposure Lock",
                        description = "Lock exposure at startup",
                        isChecked = settingsManager.getPluginSetting("ExposureControl", "exposureLocked", "false").toBoolean()
                    )
                )
            )
        )

        // Grid and Overlay Settings
        settingsSections.add(
            SettingsSection(
                title = "Grid & Overlays",
                icon = R.drawable.ic_settings,
                settings = listOf(
                    SettingsItem.Dropdown(
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
                    ),
                    SettingsItem.Switch(
                        key = "camera_info_overlay",
                        title = "Camera Info Overlay",
                        description = "Show camera information on screen",
                        isChecked = settingsManager.getCameraInfoOverlay()
                    ),
                    SettingsItem.Switch(
                        key = "histogram_overlay",
                        title = "Histogram Overlay",
                        description = "Show exposure histogram",
                        isChecked = settingsManager.getHistogramOverlay()
                    )
                )
            )
        )

        // Video Settings Section
        settingsSections.add(
            SettingsSection(
                title = "Video Settings",
                icon = R.drawable.ic_camera,
                settings = listOf(
                    SettingsItem.Dropdown(
                        key = "video_quality",
                        title = "Video Quality",
                        description = "Resolution for video recording",
                        options = listOf(
                            "4K UHD (3840×2160)" to "4k",
                            "Full HD (1920×1080)" to "1080p",
                            "HD (1280×720)" to "720p"
                        ),
                        currentValue = settingsManager.getVideoQuality()
                    ),
                    SettingsItem.Switch(
                        key = "video_stabilization",
                        title = "Video Stabilization",
                        description = "Enable electronic image stabilization",
                        isChecked = settingsManager.getVideoStabilization()
                    )
                )
            )
        )

        // Debug and Advanced Section
        settingsSections.add(
            SettingsSection(
                title = "Debug & Advanced",
                icon = R.drawable.ic_settings,
                settings = listOf(
                    SettingsItem.Switch(
                        key = "debug_logging",
                        title = "Debug Logging",
                        description = "Enable verbose logging for troubleshooting",
                        isChecked = settingsManager.debugLogging.value
                    ),
                    SettingsItem.Switch(
                        key = "performance_monitoring",
                        title = "Performance Monitoring",
                        description = "Track plugin performance metrics",
                        isChecked = settingsManager.getPerformanceMonitoring()
                    ),
                    SettingsItem.Slider(
                        key = "processing_interval",
                        title = "Processing Interval",
                        description = "Frame processing interval (ms)",
                        min = 100,
                        max = 5000,
                        currentValue = settingsManager.getPluginSetting("CameraInfo", "processingInterval", "1000").toInt()
                    ),
                    SettingsItem.Switch(
                        key = "raw_capture",
                        title = "RAW Capture",
                        description = "Enable RAW photo format (if supported)",
                        isChecked = settingsManager.getRawCapture()
                    )
                )
            )
        )

        // Plugin Management Section
        settingsSections.add(
            SettingsSection(
                title = "Plugin Management",
                icon = R.drawable.ic_settings,
                settings = listOf(
                    SettingsItem.Switch(
                        key = "plugin_autofocus",
                        title = "AutoFocus Plugin",
                        description = "Enable advanced focus controls",
                        isChecked = settingsManager.isPluginEnabled("AutoFocus")
                    ),
                    SettingsItem.Switch(
                        key = "plugin_gridoverlay",
                        title = "Grid Overlay Plugin",
                        description = "Enable composition grid overlays",
                        isChecked = settingsManager.isPluginEnabled("GridOverlay")
                    ),
                    SettingsItem.Switch(
                        key = "plugin_camerainfo",
                        title = "Camera Info Plugin",
                        description = "Enable frame analysis and monitoring",
                        isChecked = settingsManager.isPluginEnabled("CameraInfo")
                    ),
                    SettingsItem.Switch(
                        key = "plugin_procontrols",
                        title = "Pro Controls Plugin",
                        description = "Enable manual camera controls",
                        isChecked = settingsManager.isPluginEnabled("ProControls")
                    ),
                    SettingsItem.Switch(
                        key = "plugin_exposurecontrol",
                        title = "Exposure Control Plugin",
                        description = "Enable advanced exposure management",
                        isChecked = settingsManager.isPluginEnabled("ExposureControl")
                    )
                )
            )
        )
    }

    private fun handleSettingChange(setting: SettingsItem, value: Any) {
        Log.i(TAG, "Setting changed: ${setting.key} = $value")

        when (setting.key) {
            // Camera Settings
            "default_camera_index" -> {
                settingsManager.setDefaultCameraIndex((value as String).toInt())
            }
            "photo_quality" -> {
                settingsManager.setPhotoQuality(value as Int)
            }
            "photo_resolution" -> {
                settingsManager.setPhotoResolution(value as String)
            }
            "grid_overlay" -> {
                settingsManager.setGridOverlay(value as Boolean)
            }

            // Focus Settings
            "auto_focus_mode" -> {
                settingsManager.setAutoFocusMode(value as String)
            }
            "tap_to_focus" -> {
                settingsManager.setTapToFocus(value as Boolean)
            }

            // Manual Controls
            "manual_controls_enabled" -> {
                settingsManager.setPluginSetting("ProControls", "manualModeEnabled", value.toString())
            }
            "default_exposure" -> {
                settingsManager.setPluginSetting("ExposureControl", "exposureIndex", value.toString())
            }
            "exposure_lock" -> {
                settingsManager.setPluginSetting("ExposureControl", "exposureLocked", value.toString())
            }

            // Grid and Overlays
            "grid_type" -> {
                settingsManager.setPluginSetting("GridOverlay", "gridType", value as String)
            }
            "camera_info_overlay" -> {
                settingsManager.setCameraInfoOverlay(value as Boolean)
            }
            "histogram_overlay" -> {
                settingsManager.setHistogramOverlay(value as Boolean)
            }

            // Video Settings
            "video_quality" -> {
                settingsManager.setVideoQuality(value as String)
            }
            "video_stabilization" -> {
                settingsManager.setVideoStabilization(value as Boolean)
            }

            // Debug and Advanced
            "debug_logging" -> {
                settingsManager.setDebugLogging(value as Boolean)
            }
            "performance_monitoring" -> {
                settingsManager.setPerformanceMonitoring(value as Boolean)
            }
            "processing_interval" -> {
                settingsManager.setPluginSetting("CameraInfo", "processingInterval", value.toString())
            }
            "raw_capture" -> {
                settingsManager.setRawCapture(value as Boolean)
            }

            // Plugin Management
            "plugin_autofocus" -> {
                settingsManager.setPluginEnabled("AutoFocus", value as Boolean)
            }
            "plugin_gridoverlay" -> {
                settingsManager.setPluginEnabled("GridOverlay", value as Boolean)
            }
            "plugin_camerainfo" -> {
                settingsManager.setPluginEnabled("CameraInfo", value as Boolean)
            }
            "plugin_procontrols" -> {
                settingsManager.setPluginEnabled("ProControls", value as Boolean)
            }
            "plugin_exposurecontrol" -> {
                settingsManager.setPluginEnabled("ExposureControl", value as Boolean)
            }

            else -> {
                Log.w(TAG, "Unknown setting key: ${setting.key}")
            }
        }

        // Log the change
        debugLogger.logInfo(
            "Setting changed: ${setting.title}",
            mapOf(
                "key" to setting.key,
                "value" to value.toString(),
                "type" to setting.javaClass.simpleName
            ),
            "Settings"
        )

        Toast.makeText(this, "${setting.title} updated", Toast.LENGTH_SHORT).show()
    }

    private fun exportSettings() {
        lifecycleScope.launch {
            try {
                val settingsData = settingsManager.exportSettings()
                val debugData = debugLogger.exportDebugLog(includeDetails = false)

                val exportData = """
                    === CustomCamera Settings Export ===
                    Generated: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}

                    === Settings (${settingsData.size} items) ===
                    ${settingsData.entries.joinToString("\n") { "${it.key} = ${it.value}" }}

                    === Recent Debug Log ===
                    $debugData
                """.trimIndent()

                // In a real app, you'd save this to external storage or share it
                Log.i(TAG, "Settings exported:\n$exportData")
                Toast.makeText(this@SettingsActivity, "Settings exported to log", Toast.LENGTH_LONG).show()

            } catch (e: Exception) {
                Log.e(TAG, "Failed to export settings", e)
                Toast.makeText(this@SettingsActivity, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun resetSettings() {
        lifecycleScope.launch {
            try {
                settingsManager.resetToDefaults()

                // Recreate settings sections with default values
                createSettingsSections()
                settingsAdapter.updateSections(settingsSections)

                Toast.makeText(this@SettingsActivity, "Settings reset to defaults", Toast.LENGTH_LONG).show()
                Log.i(TAG, "Settings reset to defaults")

            } catch (e: Exception) {
                Log.e(TAG, "Failed to reset settings", e)
                Toast.makeText(this@SettingsActivity, "Reset failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showDebugLog() {
        lifecycleScope.launch {
            try {
                val logStats = debugLogger.getLogStats()
                val recentEntries = debugLogger.getLogEntries(limit = 10)

                val debugInfo = """
                    === Debug Log Statistics ===
                    Total entries: ${logStats.totalEntries}
                    Level counts: ${logStats.levelCounts}
                    Category counts: ${logStats.categoryCounts}

                    === Recent Entries (${recentEntries.size}) ===
                    ${recentEntries.joinToString("\n") { "[${it.level}] ${it.tag}: ${it.message}" }}
                """.trimIndent()

                Log.i(TAG, "Debug log info:\n$debugInfo")
                Toast.makeText(this@SettingsActivity, "Debug info logged - Check logcat", Toast.LENGTH_LONG).show()

            } catch (e: Exception) {
                Log.e(TAG, "Failed to show debug log", e)
                Toast.makeText(this@SettingsActivity, "Debug log error: ${e.message}", Toast.LENGTH_LONG).show()
            }
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

    override fun onResume() {
        super.onResume()
        // Refresh settings in case they were changed elsewhere
        lifecycleScope.launch {
            createSettingsSections()
            settingsAdapter.updateSections(settingsSections)
        }
    }

    companion object {
        private const val TAG = "SettingsActivity"
    }
}