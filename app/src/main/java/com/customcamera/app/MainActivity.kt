package com.customcamera.app

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.customcamera.app.databinding.ActivityMainBinding
import com.customcamera.app.engine.DebugLogger
import com.customcamera.app.engine.SettingsManager
import kotlinx.coroutines.launch

/**
 * Enhanced MainActivity providing professional app launch experience
 * with quick access to camera, settings, and app information.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var settingsManager: SettingsManager
    private lateinit var debugLogger: DebugLogger

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "MainActivity onCreate - CustomCamera starting")

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize managers
        settingsManager = SettingsManager(this)
        debugLogger = DebugLogger()

        setupUI()
        setupClickListeners()
        displayAppInfo()

        Log.i(TAG, "MainActivity setup complete")
    }

    private fun setupUI() {
        // Set up fullscreen for modern app experience
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        )

        // Add app version to UI
        try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            val versionText = "v${packageInfo.versionName} (${packageInfo.longVersionCode})"
            binding.versionText.text = versionText
            binding.versionText.visibility = View.VISIBLE
        } catch (e: PackageManager.NameNotFoundException) {
            Log.w(TAG, "Could not get version info", e)
            binding.versionText.visibility = View.GONE
        }
    }

    private fun setupClickListeners() {
        // Primary camera button - go directly to camera selection
        binding.openCameraButton.setOnClickListener {
            Log.i(TAG, "Open Camera button clicked")
            launchCameraSelection()
        }

        // Quick camera button - skip selection, use default camera
        binding.quickCameraButton.setOnClickListener {
            Log.i(TAG, "Quick Camera button clicked")
            launchCameraDirectly()
        }

        // Settings button
        binding.settingsButton.setOnClickListener {
            Log.i(TAG, "Settings button clicked")
            launchSettings()
        }

        // About button
        binding.aboutButton.setOnClickListener {
            Log.i(TAG, "About button clicked")
            showAboutInfo()
        }

        // Exit button
        binding.exitButton.setOnClickListener {
            Log.i(TAG, "Exit button clicked")
            finish()
        }
    }

    private fun launchCameraSelection() {
        try {
            val intent = Intent(this, CameraSelectionActivity::class.java)
            startActivity(intent)
            debugLogger.logInfo("Launched camera selection", mapOf("source" to "main_activity"))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch camera selection", e)
            Toast.makeText(this, "Camera selection not available", Toast.LENGTH_SHORT).show()
        }
    }

    private fun launchCameraDirectly() {
        try {
            // Use default camera from settings
            val defaultCameraIndex = settingsManager.defaultCameraIndex.value

            val intent = Intent(this, CameraActivityEngine::class.java)
            intent.putExtra(CameraSelectionActivity.EXTRA_CAMERA_INDEX, defaultCameraIndex)
            startActivity(intent)

            debugLogger.logInfo(
                "Launched camera directly",
                mapOf(
                    "cameraIndex" to defaultCameraIndex,
                    "source" to "quick_launch"
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch camera directly", e)
            Toast.makeText(this, "Quick camera launch failed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun launchSettings() {
        try {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
            debugLogger.logInfo("Launched settings", mapOf("source" to "main_activity"))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch settings", e)
            Toast.makeText(this, "Settings not available", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showAboutInfo() {
        lifecycleScope.launch {
            try {
                val packageInfo = packageManager.getPackageInfo(packageName, 0)
                val settingsSummary = settingsManager.getSettingsSummary()
                val debugStats = debugLogger.getLogStats()

                val aboutInfo = """
                    🎯 CustomCamera - Professional Plugin-Based Camera App

                    📱 App Information:
                    Version: ${packageInfo.versionName} (${packageInfo.longVersionCode})
                    Package: ${packageInfo.packageName}
                    Target SDK: ${packageInfo.applicationInfo.targetSdkVersion}

                    🔧 Current Configuration:
                    Default Camera: ${settingsSummary.defaultCameraIndex}
                    Photo Quality: ${settingsSummary.photoQuality}%
                    Photo Resolution: ${settingsSummary.photoResolution}
                    Auto Focus Mode: ${settingsSummary.autoFocusMode}
                    Grid Overlay: ${if (settingsSummary.gridOverlay) "Enabled" else "Disabled"}
                    Debug Logging: ${if (settingsSummary.debugLogging) "Enabled" else "Disabled"}

                    📊 Plugin System:
                    • AutoFocus Plugin - Professional focus control
                    • Grid Overlay Plugin - Composition assistance
                    • Camera Info Plugin - Real-time analysis
                    • Pro Controls Plugin - Manual camera controls
                    • Exposure Control Plugin - Advanced exposure management

                    🐛 Debug Statistics:
                    Total Log Entries: ${debugStats.totalEntries}
                    Error Count: ${debugStats.levelCounts[com.customcamera.app.engine.LogLevel.ERROR] ?: 0}

                    🤖 Built with Claude Code - Professional AI Development
                    ❤️ Open Source MIT License
                """.trimIndent()

                Log.i(TAG, "About info:\n$aboutInfo")
                Toast.makeText(this@MainActivity, "About info logged - Check logcat for details", Toast.LENGTH_LONG).show()

                debugLogger.logInfo("About info displayed", mapOf("version" to packageInfo.versionName))

            } catch (e: Exception) {
                Log.e(TAG, "Failed to show about info", e)
                Toast.makeText(this@MainActivity, "About info error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun displayAppInfo() {
        lifecycleScope.launch {
            try {
                // Log startup information
                val settingsSummary = settingsManager.getSettingsSummary()

                debugLogger.logInfo(
                    "CustomCamera app started",
                    mapOf(
                        "defaultCamera" to settingsSummary.defaultCameraIndex,
                        "debugLogging" to settingsSummary.debugLogging,
                        "photoQuality" to settingsSummary.photoQuality
                    ),
                    "AppStartup"
                )

                Log.i(TAG, "App startup logged with current settings")

            } catch (e: Exception) {
                Log.w(TAG, "Failed to log startup info", e)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "MainActivity resumed")
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "MainActivity paused")
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}