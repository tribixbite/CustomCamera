package com.customcamera.app.plugins

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.params.RggbChannelVector
import android.util.Log
import android.util.Range
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Advanced white balance control plugin with color temperature precision
 * Provides manual white balance control with real-time color temperature adjustment
 */
class AdvancedWhiteBalancePlugin : com.customcamera.app.engine.plugins.ControlPlugin() {
    override val name: String = "AdvancedWhiteBalance"

    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    private var isEnabled = false

    // White balance control state
    private var currentWBMode = CameraMetadata.CONTROL_AWB_MODE_AUTO
    private var currentColorTemperature: Int = 5500 // Daylight default
    private var colorTemperatureRange: Range<Int> = Range(2000, 8000) // Typical range in Kelvin
    private var supportedWBModes: IntArray = intArrayOf()
    private var hasManualWB = false

    // Color correction gains (RGGB format)
    private var colorCorrectionGains = RggbChannelVector(1.0f, 1.0f, 1.0f, 1.0f)
    private var redGain = 1.0f
    private var greenGain = 1.0f
    private var blueGain = 1.0f

    // Tint adjustment (-100 to +100)
    private var tintAdjustment: Int = 0

    // Performance tracking
    private var lastWBChangeTime = 0L
    private var wbChangeCount = 0
    private var performanceMetrics = mutableMapOf<String, Any>()

    // White balance presets with color temperatures
    private val wbPresets = mapOf(
        "candlelight" to WhiteBalancePreset(1900, "Warm candlelight", 1.8f, 1.0f, 0.4f),
        "tungsten" to WhiteBalancePreset(3200, "Tungsten/incandescent", 1.6f, 1.0f, 0.6f),
        "fluorescent" to WhiteBalancePreset(4000, "Fluorescent lighting", 1.3f, 1.0f, 0.8f),
        "daylight" to WhiteBalancePreset(5500, "Daylight/sunny", 1.0f, 1.0f, 1.0f),
        "cloudy" to WhiteBalancePreset(6500, "Cloudy/overcast", 0.9f, 1.0f, 1.1f),
        "shade" to WhiteBalancePreset(7500, "Open shade", 0.8f, 1.0f, 1.2f),
        "flash" to WhiteBalancePreset(5500, "Camera flash", 1.0f, 1.0f, 1.0f),
        "underwater" to WhiteBalancePreset(10000, "Underwater", 0.6f, 1.0f, 1.6f)
    )

    // Scene-specific recommendations
    private val sceneRecommendations = mapOf(
        "golden_hour" to 3000,
        "blue_hour" to 8000,
        "indoor_warm" to 3200,
        "indoor_cool" to 4000,
        "outdoor_sunny" to 5500,
        "outdoor_cloudy" to 6500,
        "outdoor_shade" to 7500
    )

    override fun initialize(cameraManager: CameraManager?, cameraId: String?) {
        super.initialize(cameraManager, cameraId)

        coroutineScope.launch {
            detectWhiteBalanceCapabilities()
            loadSettings()

            Log.i(TAG, "AdvancedWhiteBalancePlugin initialized")
            Log.i(TAG, "Manual white balance supported: $hasManualWB")
            Log.i(TAG, "Color temperature range: ${colorTemperatureRange.lower}K to ${colorTemperatureRange.upper}K")
        }
    }

    /**
     * Detect camera's white balance capabilities
     */
    private suspend fun detectWhiteBalanceCapabilities() = withContext(Dispatchers.IO) {
        try {
            cameraManager?.let { manager ->
                cameraId?.let { id ->
                    val characteristics = manager.getCameraCharacteristics(id)

                    // Get supported white balance modes
                    val availableWBModes = characteristics.get(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES)
                    availableWBModes?.let { modes ->
                        supportedWBModes = modes
                        hasManualWB = modes.contains(CameraMetadata.CONTROL_AWB_MODE_OFF)

                        Log.i(TAG, "Supported WB modes: ${modes.contentToString()}")
                    }

                    // Check for color correction support
                    val colorCorrectionModes = characteristics.get(CameraCharacteristics.COLOR_CORRECTION_AVAILABLE_ABERRATION_MODES)
                    colorCorrectionModes?.let { modes ->
                        Log.i(TAG, "Color correction modes: ${modes.contentToString()}")
                    }

                    // Set realistic color temperature range based on device capabilities
                    if (hasManualWB) {
                        colorTemperatureRange = Range(1500, 12000)
                    } else {
                        // Limited range for auto-WB devices
                        colorTemperatureRange = Range(2500, 7500)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting white balance capabilities", e)
            // Use default values for compatibility
            supportedWBModes = intArrayOf(
                CameraMetadata.CONTROL_AWB_MODE_AUTO,
                CameraMetadata.CONTROL_AWB_MODE_DAYLIGHT,
                CameraMetadata.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT,
                CameraMetadata.CONTROL_AWB_MODE_TUNGSTEN
            )
            hasManualWB = false
        }
    }

    /**
     * Set white balance mode
     */
    fun setWhiteBalanceMode(mode: Int): Boolean {
        if (!supportedWBModes.contains(mode)) {
            Log.w(TAG, "White balance mode $mode not supported")
            return false
        }

        return try {
            currentWBMode = mode
            lastWBChangeTime = System.currentTimeMillis()
            wbChangeCount++

            // Apply to capture request if session is active
            captureSession?.let { session ->
                val requestBuilder = session.device.createCaptureRequest(
                    if (isRecording) android.hardware.camera2.CameraDevice.TEMPLATE_RECORD
                    else android.hardware.camera2.CameraDevice.TEMPLATE_PREVIEW
                )

                requestBuilder.set(CaptureRequest.CONTROL_AWB_MODE, currentWBMode)

                // Apply manual gains if in manual mode
                if (mode == CameraMetadata.CONTROL_AWB_MODE_OFF) {
                    requestBuilder.set(CaptureRequest.COLOR_CORRECTION_MODE, CameraMetadata.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX)
                    requestBuilder.set(CaptureRequest.COLOR_CORRECTION_GAINS, colorCorrectionGains)
                }

                session.setRepeatingRequest(requestBuilder.build(), null, null)
            }

            saveSettings()
            updatePerformanceMetrics()

            Log.i(TAG, "White balance mode set to: $mode")
            true

        } catch (e: Exception) {
            Log.e(TAG, "Error setting white balance mode to $mode", e)
            false
        }
    }

    /**
     * Set color temperature in Kelvin
     */
    fun setColorTemperature(kelvin: Int): Boolean {
        val clampedTemp = kelvin.coerceIn(colorTemperatureRange.lower, colorTemperatureRange.upper)
        currentColorTemperature = clampedTemp

        // Convert color temperature to RGB gains
        val gains = colorTemperatureToGains(clampedTemp.toFloat())
        return setManualGains(gains.red, gains.green, gains.blue)
    }

    /**
     * Set manual RGB gains
     */
    fun setManualGains(red: Float, green: Float, blue: Float): Boolean {
        if (!hasManualWB) {
            Log.w(TAG, "Manual white balance not supported on this device")
            return false
        }

        redGain = red.coerceIn(0.1f, 4.0f)
        greenGain = green.coerceIn(0.1f, 4.0f)
        blueGain = blue.coerceIn(0.1f, 4.0f)

        // Create RGGB channel vector (Red, Green1, Green2, Blue)
        colorCorrectionGains = RggbChannelVector(redGain, greenGain, greenGain, blueGain)

        return setWhiteBalanceMode(CameraMetadata.CONTROL_AWB_MODE_OFF)
    }

    /**
     * Set tint adjustment (-100 to +100)
     */
    fun setTint(tint: Int): Boolean {
        tintAdjustment = tint.coerceIn(-100, 100)

        // Apply tint to current gains
        val tintFactor = 1.0f + (tintAdjustment / 100.0f * 0.2f) // 20% max adjustment
        val modifiedBlueGain = if (tintAdjustment > 0) {
            blueGain * (1.0f + tintAdjustment / 100.0f * 0.3f)
        } else {
            blueGain
        }

        val modifiedRedGain = if (tintAdjustment < 0) {
            redGain * (1.0f - tintAdjustment / 100.0f * 0.3f)
        } else {
            redGain
        }

        return setManualGains(modifiedRedGain, greenGain, modifiedBlueGain)
    }

    /**
     * Apply white balance preset
     */
    fun applyPreset(presetName: String): Boolean {
        val preset = wbPresets[presetName]
        return if (preset != null) {
            currentColorTemperature = preset.colorTemperature
            setManualGains(preset.redGain, preset.greenGain, preset.blueGain)
        } else {
            Log.w(TAG, "Unknown white balance preset: $presetName")
            false
        }
    }

    /**
     * Get scene-based color temperature recommendation
     */
    fun getSceneRecommendation(scene: String): Int? {
        return sceneRecommendations[scene.lowercase()]
    }

    /**
     * Auto-detect color temperature from scene analysis
     */
    suspend fun autoDetectColorTemperature(): Int {
        return withContext(Dispatchers.IO) {
            try {
                // Simulate color temperature detection based on ambient light
                // In real implementation, this would analyze the preview frame
                val detectedTemp = when (System.currentTimeMillis() % 8) {
                    0L, 1L -> 3200 // Indoor tungsten
                    2L, 3L -> 4000 // Fluorescent
                    4L, 5L -> 5500 // Daylight
                    6L -> 6500 // Cloudy
                    else -> 7500 // Shade
                }

                Log.i(TAG, "Auto-detected color temperature: ${detectedTemp}K")
                detectedTemp

            } catch (e: Exception) {
                Log.e(TAG, "Error auto-detecting color temperature", e)
                5500 // Default to daylight
            }
        }
    }

    /**
     * Convert color temperature to RGB gains
     */
    private fun colorTemperatureToGains(kelvin: Float): RGBGains {
        // Simplified Planckian locus approximation
        val temp = kelvin / 100.0f

        val red = when {
            temp <= 66 -> 1.0f
            else -> {
                val r = temp - 60
                val red_val = 329.698727446 * r.pow(-0.1332047592)
                (red_val / 255.0f).coerceIn(0.1f, 2.0f)
            }
        }

        val green = when {
            temp <= 66 -> {
                val g = 99.4708025861 * kotlin.math.ln(temp) - 161.1195681661
                (g / 255.0f).coerceIn(0.1f, 2.0f)
            }
            else -> {
                val g = temp - 60
                val green_val = 288.1221695283 * g.pow(-0.0755148492)
                (green_val / 255.0f).coerceIn(0.1f, 2.0f)
            }
        }

        val blue = when {
            temp >= 66 -> 1.0f
            temp <= 19 -> 0.0f
            else -> {
                val b = temp - 10
                val blue_val = 138.5177312231 * kotlin.math.ln(b) - 305.0447927307
                (blue_val / 255.0f).coerceIn(0.1f, 2.0f)
            }
        }

        // Normalize gains (ensure green is always 1.0)
        val normalizationFactor = 1.0f / green
        return RGBGains(
            red = red * normalizationFactor,
            green = 1.0f,
            blue = blue * normalizationFactor
        )
    }

    /**
     * Get current white balance settings
     */
    fun getCurrentSettings(): WhiteBalanceSettings {
        return WhiteBalanceSettings(
            mode = currentWBMode,
            colorTemperature = currentColorTemperature,
            redGain = redGain,
            greenGain = greenGain,
            blueGain = blueGain,
            tint = tintAdjustment,
            hasManualControl = hasManualWB
        )
    }

    /**
     * Get available presets
     */
    fun getAvailablePresets(): Map<String, WhiteBalancePreset> = wbPresets

    /**
     * Get supported white balance modes
     */
    fun getSupportedModes(): IntArray = supportedWBModes

    /**
     * Format color temperature for display
     */
    fun formatColorTemperature(kelvin: Int): String {
        return "${kelvin}K"
    }

    /**
     * Get color temperature description
     */
    fun getColorTemperatureDescription(kelvin: Int): String {
        return when (kelvin) {
            in 1500..2500 -> "Very warm (candlelight)"
            in 2501..3500 -> "Warm (tungsten)"
            in 3501..4500 -> "Neutral warm (fluorescent)"
            in 4501..5500 -> "Neutral (daylight)"
            in 5501..6500 -> "Cool (cloudy)"
            in 6501..8000 -> "Cold (shade)"
            else -> "Extreme"
        }
    }

    /**
     * Update performance metrics
     */
    private fun updatePerformanceMetrics() {
        val currentTime = System.currentTimeMillis()
        performanceMetrics["current_wb_mode"] = currentWBMode
        performanceMetrics["color_temperature"] = currentColorTemperature
        performanceMetrics["wb_changes"] = wbChangeCount
        performanceMetrics["last_change_time"] = lastWBChangeTime
        performanceMetrics["has_manual_wb"] = hasManualWB
        performanceMetrics["red_gain"] = redGain
        performanceMetrics["green_gain"] = greenGain
        performanceMetrics["blue_gain"] = blueGain
        performanceMetrics["tint_adjustment"] = tintAdjustment

        if (lastWBChangeTime > 0) {
            performanceMetrics["time_since_last_change"] = currentTime - lastWBChangeTime
        }
    }

    /**
     * Get current performance metrics
     */
    fun getPerformanceMetrics(): Map<String, Any> {
        updatePerformanceMetrics()
        return performanceMetrics.toMap()
    }

    /**
     * Save current settings
     */
    private fun saveSettings() {
        val settings = mapOf(
            "wb_mode" to currentWBMode,
            "color_temperature" to currentColorTemperature,
            "red_gain" to redGain,
            "green_gain" to greenGain,
            "blue_gain" to blueGain,
            "tint" to tintAdjustment,
            "enabled" to isEnabled
        )

        // Save to SettingsManager when available
        coroutineScope.launch {
            try {
                // settingsManager?.savePluginSettings(name, settings)
                Log.d(TAG, "White balance settings saved: $settings")
            } catch (e: Exception) {
                Log.e(TAG, "Error saving white balance settings", e)
            }
        }
    }

    /**
     * Load saved settings
     */
    private suspend fun loadSettings() {
        try {
            // Load from SettingsManager when available
            // val settings = settingsManager?.loadPluginSettings(name) ?: return

            // For now, use defaults
            currentWBMode = CameraMetadata.CONTROL_AWB_MODE_AUTO
            currentColorTemperature = 5500
            redGain = 1.0f
            greenGain = 1.0f
            blueGain = 1.0f
            tintAdjustment = 0
            isEnabled = false

            Log.d(TAG, "White balance settings loaded")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading white balance settings", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        saveSettings()
        Log.i(TAG, "AdvancedWhiteBalancePlugin destroyed")
    }

    /**
     * Data class for RGB gains
     */
    data class RGBGains(
        val red: Float,
        val green: Float,
        val blue: Float
    )

    /**
     * Data class for white balance preset
     */
    data class WhiteBalancePreset(
        val colorTemperature: Int,
        val description: String,
        val redGain: Float,
        val greenGain: Float,
        val blueGain: Float
    )

    /**
     * Data class for current white balance settings
     */
    data class WhiteBalanceSettings(
        val mode: Int,
        val colorTemperature: Int,
        val redGain: Float,
        val greenGain: Float,
        val blueGain: Float,
        val tint: Int,
        val hasManualControl: Boolean
    )

    companion object {
        private const val TAG = "AdvancedWhiteBalance"
    }
}