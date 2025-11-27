package com.customcamera.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.customcamera.app.databinding.ActivitySettingsBinding
import com.customcamera.app.engine.CameraEngine
import com.customcamera.app.engine.DebugLogger
import com.customcamera.app.engine.SettingsManager
import com.customcamera.app.plugins.*
import com.customcamera.app.settings.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume

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

    // Activity result launchers for file import/export
    private val pluginConfigExporterLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri?.let {
            lifecycleScope.launch {
                writePluginConfiguration(it)
            }
        }
    }

    private val pluginConfigImporterLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            lifecycleScope.launch {
                importPluginConfiguration(it)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "SettingsActivity onCreate")

        try {
            binding = ActivitySettingsBinding.inflate(layoutInflater)
            setContentView(binding.root)
            Log.i(TAG, "Layout inflated successfully")

            setupToolbar()
            initializeSettings()
            setupSettingsUI()

            Log.i(TAG, "SettingsActivity setup complete")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create SettingsActivity", e)
            Toast.makeText(this, "Settings error: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
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
        settingsManager = SettingsManager.getInstance(this)
        debugLogger = DebugLogger()

        Log.i(TAG, "Settings initialized")
    }

    private fun setupSettingsUI() {
        try {
            // Create settings sections
            createSettingsSections()
            Log.i(TAG, "Settings sections created: ${settingsSections.size}")
            settingsSections.forEachIndexed { index, section ->
                Log.i(TAG, "  Section $index: ${section.title}")
            }

            // Setup RecyclerView
            settingsAdapter = SettingsAdapter(settingsSections) { setting, value ->
                handleSettingChange(setting, value)
            }

            binding.settingsRecyclerView.apply {
                layoutManager = LinearLayoutManager(this@SettingsActivity)
                adapter = settingsAdapter
            }
            Log.i(TAG, "RecyclerView setup complete")

            // Setup action buttons
            binding.exportSettingsButton.setOnClickListener { exportSettings() }
            binding.resetSettingsButton.setOnClickListener { resetSettings() }
            binding.debugLogButton.setOnClickListener { showDebugLog() }
            Log.i(TAG, "Action buttons setup complete")

            Log.i(TAG, "Settings UI setup complete")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup settings UI", e)
            Toast.makeText(this, "Settings UI error: ${e.message}", Toast.LENGTH_LONG).show()
        }
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

        // Plugin Browser & Management Section
        settingsSections.add(
            SettingsSection(
                title = "Plugin Browser & Import",
                icon = R.drawable.ic_extension,
                settings = listOf(
                    SettingsItem.Button(
                        key = "browse_plugins",
                        title = "Browse Available Plugins",
                        description = "View and install plugins from the plugin store",
                        buttonText = "Browse"
                    ),
                    SettingsItem.Button(
                        key = "import_plugin",
                        title = "Import Plugin",
                        description = "Import plugin from file (.apk or .jar)",
                        buttonText = "Import"
                    ),
                    SettingsItem.Button(
                        key = "export_plugins",
                        title = "Export Plugin Configuration",
                        description = "Export current plugin settings and list",
                        buttonText = "Export"
                    ),
                    SettingsItem.Button(
                        key = "manage_plugins",
                        title = "Manage Installed Plugins",
                        description = "View, update, or remove installed plugins",
                        buttonText = "Manage"
                    )
                )
            )
        )
        Log.i(TAG, "Added Plugin Browser & Import section. Total sections: ${settingsSections.size}")

        // Plugin Control Section
        settingsSections.add(
            SettingsSection(
                title = "Plugin Control",
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
        Log.i(TAG, "Added Plugin Control section. Total sections: ${settingsSections.size}")

        // Pixel Camera App Settings Section
        settingsSections.add(
            SettingsSection(
                title = "Pixel Camera Style",
                icon = R.drawable.ic_camera,
                settings = listOf(
                    SettingsItem.Switch(
                        key = "pixel_ui_style",
                        title = "Pixel UI Style",
                        description = "Use Google Pixel camera interface design",
                        isChecked = settingsManager.getPluginSetting("PixelCamera", "enablePixelUI", "false").toBoolean()
                    ),
                    SettingsItem.Switch(
                        key = "pixel_computational_photography",
                        title = "Computational Photography",
                        description = "Enable Pixel-style HDR+ and Night Sight features",
                        isChecked = settingsManager.getPluginSetting("PixelCamera", "enableComputationalPhoto", "false").toBoolean()
                    ),
                    SettingsItem.Switch(
                        key = "pixel_portrait_mode",
                        title = "Pixel Portrait Mode",
                        description = "Advanced depth-based background blur like Pixel cameras",
                        isChecked = settingsManager.getPluginSetting("PixelCamera", "enablePortraitMode", "false").toBoolean()
                    ),
                    SettingsItem.Switch(
                        key = "pixel_night_sight",
                        title = "Night Sight",
                        description = "Pixel-style low-light photography enhancement",
                        isChecked = settingsManager.getPluginSetting("PixelCamera", "enableNightSight", "false").toBoolean()
                    ),
                    SettingsItem.Switch(
                        key = "pixel_motion_photos",
                        title = "Motion Photos",
                        description = "Capture short videos with still photos (Live Photos)",
                        isChecked = settingsManager.getPluginSetting("PixelCamera", "enableMotionPhotos", "false").toBoolean()
                    ),
                    SettingsItem.Switch(
                        key = "pixel_top_shot",
                        title = "Top Shot",
                        description = "AI-powered best shot selection from multiple frames",
                        isChecked = settingsManager.getPluginSetting("PixelCamera", "enableTopShot", "false").toBoolean()
                    ),
                    SettingsItem.Dropdown(
                        key = "pixel_photo_format",
                        title = "Photo Format",
                        description = "Default photo storage format",
                        options = listOf(
                            "JPEG" to "jpeg",
                            "HEIF" to "heif",
                            "RAW + JPEG" to "raw_jpeg"
                        ),
                        currentValue = settingsManager.getPluginSetting("PixelCamera", "photoFormat", "jpeg")
                    )
                )
            )
        )

        // Samsung Camera App Settings Section
        settingsSections.add(
            SettingsSection(
                title = "Samsung Camera Style",
                icon = R.drawable.ic_camera,
                settings = listOf(
                    SettingsItem.Switch(
                        key = "samsung_ui_style",
                        title = "Samsung One UI Style",
                        description = "Use Samsung Galaxy camera interface design",
                        isChecked = settingsManager.getPluginSetting("SamsungCamera", "enableSamsungUI", "false").toBoolean()
                    ),
                    SettingsItem.Switch(
                        key = "samsung_single_take",
                        title = "Single Take",
                        description = "Capture multiple photos and videos with AI selection",
                        isChecked = settingsManager.getPluginSetting("SamsungCamera", "enableSingleTake", "false").toBoolean()
                    ),
                    SettingsItem.Switch(
                        key = "samsung_scene_optimizer",
                        title = "Scene Optimizer",
                        description = "AI-powered scene detection and optimization",
                        isChecked = settingsManager.getPluginSetting("SamsungCamera", "enableSceneOptimizer", "false").toBoolean()
                    ),
                    SettingsItem.Switch(
                        key = "samsung_super_resolution",
                        title = "Super Resolution",
                        description = "AI upscaling for enhanced image quality",
                        isChecked = settingsManager.getPluginSetting("SamsungCamera", "enableSuperResolution", "false").toBoolean()
                    ),
                    SettingsItem.Switch(
                        key = "samsung_pro_mode",
                        title = "Pro Mode",
                        description = "Professional manual controls with histogram",
                        isChecked = settingsManager.getPluginSetting("SamsungCamera", "enableProMode", "false").toBoolean()
                    ),
                    SettingsItem.Switch(
                        key = "samsung_director_view",
                        title = "Director's View",
                        description = "Multi-camera recording with dual preview",
                        isChecked = settingsManager.getPluginSetting("SamsungCamera", "enableDirectorView", "false").toBoolean()
                    ),
                    SettingsItem.Switch(
                        key = "samsung_food_mode",
                        title = "Food Mode",
                        description = "Specialized food photography with color enhancement",
                        isChecked = settingsManager.getPluginSetting("SamsungCamera", "enableFoodMode", "false").toBoolean()
                    ),
                    SettingsItem.Dropdown(
                        key = "samsung_shooting_methods",
                        title = "Shooting Methods",
                        description = "Alternative capture methods",
                        options = listOf(
                            "Tap Shutter" to "tap",
                            "Palm Gesture" to "palm",
                            "Voice Commands" to "voice",
                            "Volume Keys" to "volume",
                            "Floating Shutter" to "floating"
                        ),
                        currentValue = settingsManager.getPluginSetting("SamsungCamera", "shootingMethod", "tap")
                    ),
                    SettingsItem.Slider(
                        key = "samsung_beauty_level",
                        title = "Beauty Level",
                        description = "Face beautification intensity (0-10)",
                        min = 0,
                        max = 10,
                        currentValue = settingsManager.getPluginSetting("SamsungCamera", "beautyLevel", "0").toInt()
                    )
                )
            )
        )

        // About Section with Version Info
        try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            val versionName = packageInfo.versionName ?: "Unknown"
            val versionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                packageInfo.longVersionCode.toString()
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toString()
            }
            val installTime = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                .format(java.util.Date(packageInfo.lastUpdateTime))

            settingsSections.add(
                SettingsSection(
                    title = "About CustomCamera",
                    icon = R.drawable.ic_info,
                    settings = listOf(
                        SettingsItem.Info(
                            key = "app_version",
                            title = "Version",
                            description = "Current app version",
                            value = versionName
                        ),
                        SettingsItem.Info(
                            key = "app_build",
                            title = "Build Code",
                            description = "Internal build number",
                            value = versionCode
                        ),
                        SettingsItem.Info(
                            key = "app_updated",
                            title = "Last Updated",
                            description = "Installation/update date",
                            value = installTime
                        ),
                        SettingsItem.Info(
                            key = "app_package",
                            title = "Package Name",
                            description = "Android package identifier",
                            value = packageName
                        ),
                        SettingsItem.Button(
                            key = "check_updates",
                            title = "Check for Updates",
                            description = "Check GitHub for latest version",
                            buttonText = "Check Now"
                        )
                    )
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting package info", e)
            // Add minimal about section on error
            settingsSections.add(
                SettingsSection(
                    title = "About CustomCamera",
                    icon = R.drawable.ic_info,
                    settings = listOf(
                        SettingsItem.Info(
                            key = "app_error",
                            title = "Error",
                            description = "Could not load version info",
                            value = "Check logs"
                        )
                    )
                )
            )
        }
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

            // Plugin Browser & Management
            "browse_plugins" -> {
                launchPluginBrowser()
            }
            "import_plugin" -> {
                launchPluginImporter()
            }
            "export_plugins" -> {
                exportPluginConfiguration()
            }
            "manage_plugins" -> {
                launchPluginManager()
            }

            // Plugin Control
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

            // Pixel Camera Settings
            "pixel_ui_style" -> {
                settingsManager.setPluginSetting("PixelCamera", "enablePixelUI", value.toString())
            }
            "pixel_computational_photography" -> {
                settingsManager.setPluginSetting("PixelCamera", "enableComputationalPhoto", value.toString())
            }
            "pixel_portrait_mode" -> {
                settingsManager.setPluginSetting("PixelCamera", "enablePortraitMode", value.toString())
            }
            "pixel_night_sight" -> {
                settingsManager.setPluginSetting("PixelCamera", "enableNightSight", value.toString())
            }
            "pixel_motion_photos" -> {
                settingsManager.setPluginSetting("PixelCamera", "enableMotionPhotos", value.toString())
            }
            "pixel_top_shot" -> {
                settingsManager.setPluginSetting("PixelCamera", "enableTopShot", value.toString())
            }
            "pixel_photo_format" -> {
                settingsManager.setPluginSetting("PixelCamera", "photoFormat", value as String)
            }

            // Samsung Camera Settings
            "samsung_ui_style" -> {
                settingsManager.setPluginSetting("SamsungCamera", "enableSamsungUI", value.toString())
            }
            "samsung_single_take" -> {
                settingsManager.setPluginSetting("SamsungCamera", "enableSingleTake", value.toString())
            }
            "samsung_scene_optimizer" -> {
                settingsManager.setPluginSetting("SamsungCamera", "enableSceneOptimizer", value.toString())
            }
            "samsung_super_resolution" -> {
                settingsManager.setPluginSetting("SamsungCamera", "enableSuperResolution", value.toString())
            }
            "samsung_pro_mode" -> {
                settingsManager.setPluginSetting("SamsungCamera", "enableProMode", value.toString())
            }
            "samsung_director_view" -> {
                settingsManager.setPluginSetting("SamsungCamera", "enableDirectorView", value.toString())
            }
            "samsung_food_mode" -> {
                settingsManager.setPluginSetting("SamsungCamera", "enableFoodMode", value.toString())
            }
            "samsung_shooting_methods" -> {
                settingsManager.setPluginSetting("SamsungCamera", "shootingMethod", value as String)
            }
            "samsung_beauty_level" -> {
                settingsManager.setPluginSetting("SamsungCamera", "beautyLevel", value.toString())
            }

            // About Section
            "check_updates" -> {
                checkForUpdates()
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

    private fun launchPluginBrowser() {
        lifecycleScope.launch {
            try {
                // Get all built-in plugins from PluginRegistry
                val pluginRegistry = com.customcamera.app.engine.plugins.PluginRegistry(this@SettingsActivity)
                val allPlugins = pluginRegistry.getAllPlugins()

                // Sort by category, then by display name
                val sortedPlugins = allPlugins.sortedWith(
                    compareBy<com.customcamera.app.engine.plugins.PluginRegistry.PluginInfo> { it.category.ordinal }
                        .thenBy { it.displayName }
                )

                // Format plugin list with category grouping
                val pluginsByCategory = sortedPlugins.groupBy { it.category }
                val pluginItems = mutableListOf<String>()
                val pluginData = mutableListOf<com.customcamera.app.engine.plugins.PluginRegistry.PluginInfo>()

                pluginsByCategory.forEach { (category, plugins) ->
                    // Add category header
                    pluginItems.add("━━━ ${category.name} ━━━")
                    pluginData.add(plugins.first()) // Dummy entry for header

                    // Add plugins in this category
                    plugins.forEach { plugin ->
                        val status = if (settingsManager.isPluginEnabled(plugin.name)) "✓" else "○"
                        pluginItems.add("$status ${plugin.displayName}\n   ${plugin.description}")
                        pluginData.add(plugin)
                    }
                }

                val builder = androidx.appcompat.app.AlertDialog.Builder(this@SettingsActivity)
                builder.setTitle("Built-in Plugins (${allPlugins.size} total)")
                builder.setItems(pluginItems.toTypedArray()) { _, which ->
                    val selectedPlugin = pluginData[which]

                    // Check if clicked on category header
                    if (pluginItems[which].startsWith("━━━")) {
                        return@setItems
                    }

                    // Show plugin details
                    val isEnabled = settingsManager.isPluginEnabled(selectedPlugin.name)
                    val statusText = if (isEnabled) "Enabled" else "Disabled"
                    val toggleText = if (isEnabled) "Disable" else "Enable"

                    val detailsBuilder = androidx.appcompat.app.AlertDialog.Builder(this@SettingsActivity)
                    detailsBuilder.setTitle(selectedPlugin.displayName)
                    detailsBuilder.setMessage(
                        "${selectedPlugin.description}\n\n" +
                        "Category: ${selectedPlugin.category.name}\n" +
                        "Status: $statusText\n" +
                        "User Toggleable: ${if (selectedPlugin.userToggleable) "Yes" else "No (System)"}"
                    )

                    if (selectedPlugin.userToggleable) {
                        detailsBuilder.setPositiveButton(toggleText) { _, _ ->
                            settingsManager.setPluginEnabled(selectedPlugin.name, !isEnabled)
                            android.widget.Toast.makeText(
                                this@SettingsActivity,
                                "${selectedPlugin.displayName} ${if (isEnabled) "disabled" else "enabled"}",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()

                            // Refresh UI
                            createSettingsSections()
                            settingsAdapter.updateSections(settingsSections)

                            // Refresh browser to show new status
                            launchPluginBrowser()
                        }
                    }

                    detailsBuilder.setNegativeButton("Close", null)
                    detailsBuilder.show()

                    debugLogger.logInfo("Plugin browser: viewed ${selectedPlugin.displayName}",
                        mapOf("enabled" to isEnabled), "Settings")
                }
                builder.setNegativeButton("Close", null)
                builder.show()

                Log.i(TAG, "Plugin browser opened: ${allPlugins.size} built-in plugins")
                debugLogger.logInfo("Plugin browser opened",
                    mapOf("totalPlugins" to allPlugins.size), "Settings")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open plugin browser", e)
                android.widget.Toast.makeText(this@SettingsActivity, "Plugin browser error: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun launchPluginImporter() {
        try {
            // Launch file picker for JSON configuration import
            pluginConfigImporterLauncher.launch(arrayOf("application/json", "text/plain"))

            android.widget.Toast.makeText(this, "Select plugin configuration file (.json)", android.widget.Toast.LENGTH_SHORT).show()
            debugLogger.logInfo("Plugin configuration import initiated", emptyMap(), "Settings")
            Log.i(TAG, "Plugin configuration import initiated")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch plugin importer", e)
            android.widget.Toast.makeText(this, "Import error: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    private suspend fun importPluginConfiguration(uri: Uri) {
        withContext(Dispatchers.IO) {
            try {
                // Read JSON file
                val jsonString = contentResolver.openInputStream(uri)?.use { inputStream ->
                    inputStream.bufferedReader().use { it.readText() }
                } ?: throw Exception("Failed to read file")

                // Parse JSON
                val config = org.json.JSONObject(jsonString)

                // Validate version
                val configVersion = config.optString("version", "unknown")
                if (configVersion != "1.0") {
                    throw Exception("Unsupported configuration version: $configVersion")
                }

                // Extract metadata
                val exportDate = config.optLong("exportDate", 0)
                val appVersion = config.optString("appVersion", "unknown")
                val exportDateFormatted = if (exportDate > 0) {
                    java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                        .format(java.util.Date(exportDate))
                } else "Unknown"

                // Show confirmation dialog on main thread
                val shouldImport = withContext(Dispatchers.Main) {
                    showImportConfirmationDialog(appVersion, exportDateFormatted)
                }

                if (!shouldImport) {
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(this@SettingsActivity, "Import cancelled", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    return@withContext
                }

                // Apply plugin states
                val pluginStates = config.optJSONObject("pluginStates")
                var pluginsApplied = 0
                pluginStates?.let { states ->
                    states.keys().forEach { pluginName ->
                        val isEnabled = states.getBoolean(pluginName)
                        settingsManager.setPluginEnabled(pluginName, isEnabled)
                        pluginsApplied++
                    }
                }

                // Apply camera settings
                config.optJSONObject("cameraSettings")?.let { settings ->
                    settings.optInt("photoQuality", -1).let {
                        if (it > 0) settingsManager.setPhotoQuality(it)
                    }
                    settings.optBoolean("gridOverlay", false).let {
                        settingsManager.setGridOverlay(it)
                    }
                }

                // Apply video settings
                config.optJSONObject("videoSettings")?.let { settings ->
                    settings.optString("videoQuality", "").let {
                        if (it.isNotEmpty()) settingsManager.setVideoQuality(it)
                    }
                    settingsManager.setVideoStabilization(settings.optBoolean("videoStabilization", true))
                }

                Log.i(TAG, "Plugin configuration imported: $pluginsApplied plugins configured")

                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(
                        this@SettingsActivity,
                        "Configuration imported\n$pluginsApplied plugins configured",
                        android.widget.Toast.LENGTH_LONG
                    ).show()

                    // Refresh UI
                    createSettingsSections()
                    settingsAdapter.updateSections(settingsSections)

                    debugLogger.logInfo(
                        "Plugin configuration imported",
                        mapOf("pluginsConfigured" to pluginsApplied),
                        "Settings"
                    )
                }
            } catch (e: org.json.JSONException) {
                Log.e(TAG, "Invalid JSON configuration", e)
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(this@SettingsActivity, "Invalid configuration file: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to import plugin configuration", e)
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(this@SettingsActivity, "Import failed: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private suspend fun showImportConfirmationDialog(appVersion: String, exportDate: String): Boolean =
        suspendCancellableCoroutine { continuation ->
            val builder = androidx.appcompat.app.AlertDialog.Builder(this)
            builder.setTitle("Import Configuration?")
            builder.setMessage(
                "This will replace your current plugin settings.\n\n" +
                "Configuration Details:\n" +
                "• App Version: $appVersion\n" +
                "• Export Date: $exportDate\n\n" +
                "Do you want to continue?"
            )
            builder.setPositiveButton("Import") { _, _ ->
                continuation.resume(true)
            }
            builder.setNegativeButton("Cancel") { _, _ ->
                continuation.resume(false)
            }
            builder.setOnCancelListener {
                continuation.resume(false)
            }
            builder.show()
        }

    private fun exportPluginConfiguration() {
        try {
            // Generate filename with timestamp
            val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date())
            val fileName = "customcamera_plugins_$timestamp.json"

            // Launch SAF file creator
            pluginConfigExporterLauncher.launch(fileName)

            debugLogger.logInfo("Plugin configuration export initiated", emptyMap(), "Settings")
            Log.i(TAG, "Plugin configuration export initiated: $fileName")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initiate export", e)
            android.widget.Toast.makeText(this, "Export error: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    private suspend fun writePluginConfiguration(uri: Uri) {
        withContext(Dispatchers.IO) {
            try {
                // List of all plugins in the app
                val allPlugins = listOf(
                    "AutoFocus", "GridOverlay", "CameraInfo", "ProControls", "ExposureControl",
                    "Barcode", "Histogram", "ExposureAnalysis", "MotionDetection", "QRScanner",
                    "SharpnessAnalysis", "SmartScene", "SmartAdjustments", "ObjectDetection",
                    "Crop", "RAWCapture", "AdvancedVideoRecording", "HDR", "NightMode",
                    "DualCameraPiP", "ManualFocus", "DiagnosticOverlay", "ScanningOverlay",
                    "ManualControlsSimple"
                )

                // Build JSON configuration
                val config = org.json.JSONObject().apply {
                    // Metadata
                    put("version", "1.0")
                    put("appVersion", "2.3.2")
                    put("appBuild", 40)
                    put("exportDate", System.currentTimeMillis())
                    put("exportDateFormatted", java.text.SimpleDateFormat(
                        "yyyy-MM-dd HH:mm:ss",
                        java.util.Locale.getDefault()
                    ).format(java.util.Date()))

                    // Plugin states
                    val pluginStates = org.json.JSONObject()
                    allPlugins.forEach { pluginName ->
                        pluginStates.put(pluginName, settingsManager.isPluginEnabled(pluginName))
                    }
                    put("pluginStates", pluginStates)

                    // Camera settings
                    put("cameraSettings", org.json.JSONObject().apply {
                        put("defaultCameraIndex", settingsManager.defaultCameraIndex.value)
                        put("photoQuality", settingsManager.photoQuality.value)
                        put("gridOverlay", settingsManager.gridOverlay.value)
                        put("flashMode", settingsManager.flashMode.value)
                        put("photoResolution", settingsManager.photoResolution.value)
                    })

                    // Video settings
                    put("videoSettings", org.json.JSONObject().apply {
                        put("videoQuality", settingsManager.videoQuality.value)
                        put("videoStabilization", settingsManager.videoStabilization.value)
                    })

                    // Focus settings
                    put("focusSettings", org.json.JSONObject().apply {
                        put("autoFocusMode", settingsManager.autoFocusMode.value)
                        put("tapToFocus", settingsManager.tapToFocus.value)
                    })

                    // Advanced settings
                    put("advancedSettings", org.json.JSONObject().apply {
                        put("rawCapture", settingsManager.rawCapture.value)
                        put("histogramOverlay", settingsManager.histogramOverlay.value)
                        put("cameraInfoOverlay", settingsManager.cameraInfoOverlay.value)
                        put("debugLogging", settingsManager.debugLogging.value)
                        put("performanceMonitoring", settingsManager.performanceMonitoring.value)
                        put("levelIndicator", settingsManager.levelIndicator.value)
                    })
                }

                // Write to file
                contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(config.toString(4).toByteArray()) // 4-space indent for readability
                }

                val fileSize = config.toString().length / 1024.0 // KB
                Log.i(TAG, "Plugin configuration exported successfully (${String.format("%.2f", fileSize)} KB)")

                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(
                        this@SettingsActivity,
                        "Configuration exported successfully\n${String.format("%.2f", fileSize)} KB",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                    debugLogger.logInfo(
                        "Plugin configuration exported",
                        mapOf("fileSize" to fileSize, "pluginCount" to allPlugins.size),
                        "Settings"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to export plugin configuration", e)
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(this@SettingsActivity, "Export failed: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun launchPluginManager() {
        lifecycleScope.launch {
            try {
                // Create plugin management interface
                val installedPlugins = listOf(
                    "AutoFocus" to "Built-in autofocus controls",
                    "GridOverlay" to "Composition grid overlay",
                    "CameraInfo" to "Real-time camera information",
                    "ProControls" to "Manual camera controls",
                    "ExposureControl" to "Advanced exposure management",
                    "BarcodeScanning" to "ML Kit barcode detection"
                )

                val pluginList = installedPlugins.map { plugin ->
                    val isEnabled = settingsManager.isPluginEnabled(plugin.first)
                    val status = if (isEnabled) "✅ ENABLED" else "❌ DISABLED"
                    "${plugin.first}\n${plugin.second}\n$status"
                }.toTypedArray()

                val builder = androidx.appcompat.app.AlertDialog.Builder(this@SettingsActivity)
                builder.setTitle("Manage Installed Plugins")
                builder.setItems(pluginList) { _, which ->
                    val selectedPlugin = installedPlugins[which]
                    manageIndividualPlugin(selectedPlugin.first, selectedPlugin.second)
                }
                builder.setNegativeButton("Close", null)
                builder.show()

                debugLogger.logInfo("Plugin manager opened", mapOf("installedCount" to installedPlugins.size), "Settings")
                Log.i(TAG, "Plugin manager launched")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to launch plugin manager", e)
                android.widget.Toast.makeText(this@SettingsActivity, "Manager error: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun manageIndividualPlugin(pluginName: String, description: String) {
        val isEnabled = settingsManager.isPluginEnabled(pluginName)
        val builder = androidx.appcompat.app.AlertDialog.Builder(this)

        builder.setTitle("Manage: $pluginName")
        builder.setMessage("$description\n\nStatus: ${if (isEnabled) "Enabled" else "Disabled"}")

        builder.setPositiveButton(if (isEnabled) "Disable" else "Enable") { _, _ ->
            settingsManager.setPluginEnabled(pluginName, !isEnabled)
            android.widget.Toast.makeText(this, "$pluginName ${if (!isEnabled) "enabled" else "disabled"}", android.widget.Toast.LENGTH_SHORT).show()

            // Refresh the settings UI
            createSettingsSections()
            settingsAdapter.updateSections(settingsSections)
        }

        builder.setNeutralButton("Plugin Info") { _, _ ->
            showPluginInfo(pluginName)
        }

        builder.setNegativeButton("Cancel", null)
        builder.show()
    }

    private fun showPluginInfo(pluginName: String) {
        val pluginInfo = when (pluginName) {
            "AutoFocus" -> "Version: 1.0\nDeveloper: CustomCamera Team\nFeatures: Touch-to-focus, continuous AF, face tracking"
            "GridOverlay" -> "Version: 1.0\nDeveloper: CustomCamera Team\nFeatures: Rule of thirds, golden ratio, custom grids"
            "CameraInfo" -> "Version: 1.0\nDeveloper: CustomCamera Team\nFeatures: Real-time stats, exposure info, frame analysis"
            "ProControls" -> "Version: 1.0\nDeveloper: CustomCamera Team\nFeatures: Manual ISO, shutter speed, focus distance"
            "ExposureControl" -> "Version: 1.0\nDeveloper: CustomCamera Team\nFeatures: Exposure compensation, metering modes"
            "BarcodeScanning" -> "Version: 1.0\nDeveloper: CustomCamera Team\nFeatures: QR codes, barcodes, real-time detection"
            else -> "Version: Unknown\nDeveloper: Unknown\nNo additional information available"
        }

        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        builder.setTitle("Plugin Information")
        builder.setMessage("$pluginName\n\n$pluginInfo")
        builder.setPositiveButton("OK", null)
        builder.show()
    }

    private fun checkForUpdates() {
        lifecycleScope.launch {
            try {
                Log.i(TAG, "Checking for updates from GitHub...")
                Toast.makeText(this@SettingsActivity, "Checking for updates...", Toast.LENGTH_SHORT).show()

                // GitHub API endpoint for latest release
                val url = URL("https://api.github.com/repos/tribixbite/CustomCamera/releases/latest")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
                connection.connectTimeout = 10000
                connection.readTimeout = 10000

                withContext(Dispatchers.IO) {
                    val responseCode = connection.responseCode
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        val response = connection.inputStream.bufferedReader().use { it.readText() }
                        connection.disconnect()

                        // Parse JSON response
                        val jsonObject = org.json.JSONObject(response)
                        val latestVersion = jsonObject.getString("tag_name")
                        val releaseUrl = jsonObject.getString("html_url")
                        val releaseName = jsonObject.getString("name")
                        val publishedAt = jsonObject.getString("published_at")

                        // Get download URL for debug APK
                        val assets = jsonObject.getJSONArray("assets")
                        var downloadUrl: String? = null
                        for (i in 0 until assets.length()) {
                            val asset = assets.getJSONObject(i)
                            val name = asset.getString("name")
                            if (name.endsWith("debug.apk")) {
                                downloadUrl = asset.getString("browser_download_url")
                                break
                            }
                        }

                        // Get current version
                        val packageInfo = packageManager.getPackageInfo(packageName, 0)
                        val currentVersion = packageInfo.versionName

                        withContext(Dispatchers.Main) {
                            Log.i(TAG, "Current version: $currentVersion, Latest version: $latestVersion")

                            if (latestVersion != currentVersion) {
                                // Show update available dialog
                                showUpdateDialog(latestVersion, releaseName, publishedAt, downloadUrl, releaseUrl)
                            } else {
                                Toast.makeText(
                                    this@SettingsActivity,
                                    "You're running the latest version ($currentVersion)",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    } else {
                        connection.disconnect()
                        withContext(Dispatchers.Main) {
                            Log.e(TAG, "Failed to check for updates. Response code: $responseCode")
                            Toast.makeText(
                                this@SettingsActivity,
                                "Failed to check for updates (HTTP $responseCode)",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking for updates", e)
                Toast.makeText(
                    this@SettingsActivity,
                    "Error checking for updates: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun showUpdateDialog(version: String, name: String, publishedAt: String, downloadUrl: String?, releaseUrl: String) {
        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        builder.setTitle("Update Available")
        builder.setMessage(
            "Version: $version\n" +
            "Release: $name\n" +
            "Published: ${publishedAt.take(10)}\n\n" +
            "A new version is available!"
        )

        if (downloadUrl != null) {
            builder.setPositiveButton("Download & Install") { _, _ ->
                downloadAndInstallUpdate(downloadUrl, version)
            }
        }

        builder.setNeutralButton("View on GitHub") { _, _ ->
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(releaseUrl))
            startActivity(intent)
        }

        builder.setNegativeButton("Later", null)
        builder.show()
    }

    private fun downloadAndInstallUpdate(downloadUrl: String, version: String) {
        lifecycleScope.launch {
            try {
                Log.i(TAG, "Downloading update from: $downloadUrl")
                Toast.makeText(this@SettingsActivity, "Downloading update...", Toast.LENGTH_SHORT).show()

                withContext(Dispatchers.IO) {
                    val url = URL(downloadUrl)
                    val connection = url.openConnection() as HttpURLConnection
                    connection.requestMethod = "GET"
                    connection.connectTimeout = 30000
                    connection.readTimeout = 30000

                    val totalBytes = connection.contentLength
                    val outputFile = File(getExternalFilesDir(null), "CustomCamera-$version.apk")

                    connection.inputStream.use { input ->
                        outputFile.outputStream().use { output ->
                            val buffer = ByteArray(8192)
                            var bytesRead: Int
                            var totalBytesRead = 0L

                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                output.write(buffer, 0, bytesRead)
                                totalBytesRead += bytesRead

                                // Update progress on main thread
                                if (totalBytes > 0) {
                                    val progress = (totalBytesRead * 100 / totalBytes).toInt()
                                    withContext(Dispatchers.Main) {
                                        if (progress % 10 == 0) { // Update every 10%
                                            Log.i(TAG, "Download progress: $progress%")
                                        }
                                    }
                                }
                            }
                        }
                    }
                    connection.disconnect()

                    withContext(Dispatchers.Main) {
                        Log.i(TAG, "Download complete: ${outputFile.absolutePath}")
                        Toast.makeText(
                            this@SettingsActivity,
                            "Download complete. Installing...",
                            Toast.LENGTH_SHORT
                        ).show()

                        // Install the APK
                        val intent = Intent(Intent.ACTION_VIEW)
                        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            FileProvider.getUriForFile(
                                this@SettingsActivity,
                                "${packageName}.fileprovider",
                                outputFile
                            )
                        } else {
                            Uri.fromFile(outputFile)
                        }
                        intent.setDataAndType(uri, "application/vnd.android.package-archive")
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                        startActivity(intent)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error downloading update", e)
                Toast.makeText(
                    this@SettingsActivity,
                    "Download failed: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    companion object {
        private const val TAG = "SettingsActivity"
    }
}