// Implementation additions for SettingsActivity.kt
// Add these after line 31 (after settingsSections declaration)

// ============================================================================
// ACTIVITY RESULT LAUNCHERS (Add as class properties)
// ============================================================================

// Export plugin configuration to JSON file
private val pluginConfigExporterLauncher = registerForActivityResult(
    ActivityResultContracts.CreateDocument("application/json")
) { uri: Uri? ->
    uri?.let {
        lifecycleScope.launch {
            writePluginConfiguration(it)
        }
    }
}

// Import plugin configuration from JSON file
private val pluginConfigImporterLauncher = registerForActivityResult(
    ActivityResultContracts.OpenDocument()
) { uri: Uri? ->
    uri?.let {
        lifecycleScope.launch {
            importPluginConfiguration(it)
        }
    }
}

// ============================================================================
// REPLACE exportPluginConfiguration() function (line 904)
// ============================================================================

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
        Toast.makeText(this, "Export error: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

// ============================================================================
// ADD NEW writePluginConfiguration() function
// ============================================================================

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
                })

                // Video settings
                put("videoSettings", org.json.JSONObject().apply {
                    put("videoQuality", settingsManager.videoQuality.value)
                    put("videoFps", settingsManager.videoFps.value)
                    put("videoStabilization", settingsManager.videoStabilization.value)
                })

                // Focus settings
                put("focusSettings", org.json.JSONObject().apply {
                    put("manualFocusEnabled", settingsManager.manualFocusEnabled.value)
                    put("focusPeakingEnabled", settingsManager.focusPeakingEnabled.value)
                })

                // Exposure settings
                put("exposureSettings", org.json.JSONObject().apply {
                    put("exposureCompensation", settingsManager.exposureCompensation.value)
                    put("autoExposureEnabled", settingsManager.autoExposureEnabled.value)
                })

                // HDR and advanced settings
                put("advancedSettings", org.json.JSONObject().apply {
                    put("hdrEnabled", settingsManager.hdrEnabled.value)
                    put("nightModeEnabled", settingsManager.nightModeEnabled.value)
                    put("rawCaptureEnabled", settingsManager.rawCaptureEnabled.value)
                })
            }

            // Write to file
            contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(config.toString(4).toByteArray()) // 4-space indent for readability
            }

            val fileSize = config.toString().length / 1024.0 // KB
            Log.i(TAG, "Plugin configuration exported successfully (${String.format("%.2f", fileSize)} KB)")

            withContext(Dispatchers.Main) {
                Toast.makeText(
                    this@SettingsActivity,
                    "Configuration exported successfully\n${String.format("%.2f", fileSize)} KB",
                    Toast.LENGTH_LONG
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
                Toast.makeText(this@SettingsActivity, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}

// ============================================================================
// REPLACE launchPluginImporter() function (line 873)
// ============================================================================

private fun launchPluginImporter() {
    try {
        // Launch file picker for JSON configuration import
        pluginConfigImporterLauncher.launch(arrayOf("application/json", "text/plain"))

        Toast.makeText(this, "Select plugin configuration file (.json)", Toast.LENGTH_SHORT).show()
        debugLogger.logInfo("Plugin configuration import initiated", emptyMap(), "Settings")
        Log.i(TAG, "Plugin configuration import initiated")
    } catch (e: Exception) {
        Log.e(TAG, "Failed to launch plugin importer", e)
        Toast.makeText(this, "Import error: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

// ============================================================================
// ADD NEW importPluginConfiguration() function
// ============================================================================

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
                    Toast.makeText(this@SettingsActivity, "Import cancelled", Toast.LENGTH_SHORT).show()
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
            }

            // Apply video settings
            config.optJSONObject("videoSettings")?.let { settings ->
                settings.optInt("videoQuality", -1).let {
                    if (it > 0) settingsManager.setVideoQuality(it)
                }
            }

            // Apply focus settings
            config.optJSONObject("focusSettings")?.let { settings ->
                settingsManager.setManualFocusEnabled(settings.optBoolean("manualFocusEnabled", false))
            }

            // Apply advanced settings
            config.optJSONObject("advancedSettings")?.let { settings ->
                settingsManager.setHDREnabled(settings.optBoolean("hdrEnabled", false))
                settingsManager.setNightModeEnabled(settings.optBoolean("nightModeEnabled", false))
            }

            Log.i(TAG, "Plugin configuration imported: $pluginsApplied plugins configured")

            withContext(Dispatchers.Main) {
                Toast.makeText(
                    this@SettingsActivity,
                    "Configuration imported\n$pluginsApplied plugins configured",
                    Toast.LENGTH_LONG
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
                Toast.makeText(this@SettingsActivity, "Invalid configuration file: ${e.message}", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import plugin configuration", e)
            withContext(Dispatchers.Main) {
                Toast.makeText(this@SettingsActivity, "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}

// ============================================================================
// ADD HELPER FUNCTION for import confirmation
// ============================================================================

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

// ============================================================================
// ADD IMPORTS at top of file (if not already present)
// ============================================================================

import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
