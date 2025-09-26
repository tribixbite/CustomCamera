package com.customcamera.app.plugins

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CameraCaptureSession
import android.util.Log
import android.util.Range
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Manual focus control plugin with hyperfocal distance calculator
 * Provides precise manual focus control with depth of field calculations
 */
class ManualFocusControlPlugin : ControlPlugin() {
    override val name: String = "ManualFocusControl"

    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    private var isEnabled = false

    // Focus control state
    private var currentFocusDistance: Float = 0.0f // 0 = infinity, higher = closer
    private var focusRange: Range<Float> = Range(0.0f, 10.0f) // Diopters
    private var minFocusDistance: Float = 0.1f // Minimum focus distance in meters
    private var hasManualFocus = false
    private var isAutoFocusEnabled = true

    // Hyperfocal distance calculation parameters
    private var focalLength = 26f // Equivalent focal length in mm
    private var aperture = 2.8f // Current aperture value
    private var sensorSize = 5.4f // Diagonal sensor size in mm
    private var circleOfConfusion = 0.018f // Circle of confusion in mm

    // Focus assist features
    private var focusAssistEnabled = false
    private var focusPeakingEnabled = false
    private var focusZoomEnabled = false

    // Performance tracking
    private var lastFocusChangeTime = 0L
    private var focusChangeCount = 0
    private var performanceMetrics = mutableMapOf<String, Any>()

    // Focus presets for different scenarios
    private val focusPresets = mapOf(
        "infinity" to FocusPreset(0.0f, "Infinity focus for landscapes", "∞"),
        "hyperfocal" to FocusPreset(-1.0f, "Hyperfocal distance for maximum DoF", "H"),
        "portrait" to FocusPreset(2.0f, "Portrait distance (2-3m)", "2m"),
        "macro" to FocusPreset(10.0f, "Close-up macro focus", "10cm"),
        "group" to FocusPreset(1.5f, "Group photo distance", "3m"),
        "street" to FocusPreset(0.5f, "Street photography", "5m"),
        "architecture" to FocusPreset(0.2f, "Building/architecture", "10m")
    )

    // Focus zones for different photography types
    private val focusZones = mapOf(
        "landscape" to FocusZone(0.0f, 0.3f, "Far distances for landscapes"),
        "general" to FocusZone(0.3f, 3.0f, "General photography range"),
        "portrait" to FocusZone(1.0f, 5.0f, "Portrait photography range"),
        "macro" to FocusZone(5.0f, 10.0f, "Close-up and macro range")
    )

    override fun initialize(cameraManager: CameraManager?, cameraId: String?) {
        super.initialize(cameraManager, cameraId)

        coroutineScope.launch {
            detectFocusCapabilities()
            loadSettings()

            Log.i(TAG, "ManualFocusControlPlugin initialized")
            Log.i(TAG, "Manual focus supported: $hasManualFocus")
            Log.i(TAG, "Focus range: ${focusRange.lower} to ${focusRange.upper} diopters")
            Log.i(TAG, "Min focus distance: ${minFocusDistance}m")
        }
    }

    /**
     * Detect camera's focus capabilities
     */
    private suspend fun detectFocusCapabilities() = withContext(Dispatchers.IO) {
        try {
            cameraManager?.let { manager ->
                cameraId?.let { id ->
                    val characteristics = manager.getCameraCharacteristics(id)

                    // Get minimum focus distance (0 = infinity, higher = closer)
                    val minFocusDist = characteristics.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE)
                    minFocusDist?.let { dist ->
                        focusRange = Range(0.0f, dist)
                        minFocusDistance = if (dist > 0) 1.0f / dist else Float.POSITIVE_INFINITY
                        hasManualFocus = dist > 0

                        Log.i(TAG, "Min focus distance: ${minFocusDistance}m (${dist} diopters)")
                    }

                    // Get available AF modes
                    val availableAFModes = characteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)
                    availableAFModes?.let { modes ->
                        hasManualFocus = hasManualFocus && modes.contains(CameraMetadata.CONTROL_AF_MODE_OFF)
                        Log.i(TAG, "Available AF modes: ${modes.contentToString()}")
                    }

                    // Get focal length for calculations
                    val focalLengths = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                    focalLengths?.let { lengths ->
                        focalLength = lengths[0] // Use primary focal length
                    }

                    // Get sensor size for DoF calculations
                    val sensorSizeInfo = characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
                    sensorSizeInfo?.let { size ->
                        sensorSize = sqrt(size.width.pow(2) + size.height.pow(2))
                        circleOfConfusion = sensorSize / 1500f // Typical CoC calculation
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting focus capabilities", e)
            // Use default values for compatibility
            hasManualFocus = false
            focusRange = Range(0.0f, 10.0f)
            minFocusDistance = 0.1f
        }
    }

    /**
     * Set manual focus distance in diopters
     */
    fun setFocusDistance(diopters: Float): Boolean {
        if (!hasManualFocus) {
            Log.w(TAG, "Manual focus not supported on this device")
            return false
        }

        val clampedDistance = diopters.coerceIn(focusRange.lower, focusRange.upper)

        return try {
            currentFocusDistance = clampedDistance
            lastFocusChangeTime = System.currentTimeMillis()
            focusChangeCount++

            // Apply to capture request if session is active
            captureSession?.let { session ->
                val requestBuilder = session.device.createCaptureRequest(
                    if (isRecording) android.hardware.camera2.CameraDevice.TEMPLATE_RECORD
                    else android.hardware.camera2.CameraDevice.TEMPLATE_PREVIEW
                )

                // Disable auto focus and set manual distance
                requestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_OFF)
                requestBuilder.set(CaptureRequest.LENS_FOCUS_DISTANCE, currentFocusDistance)

                session.setRepeatingRequest(requestBuilder.build(), null, null)
            }

            isAutoFocusEnabled = false
            saveSettings()
            updatePerformanceMetrics()

            Log.i(TAG, "Focus distance set to $currentFocusDistance diopters (${formatFocusDistance(currentFocusDistance)})")
            true

        } catch (e: Exception) {
            Log.e(TAG, "Error setting focus distance to $diopters", e)
            false
        }
    }

    /**
     * Set focus distance in meters
     */
    fun setFocusDistanceMeters(meters: Float): Boolean {
        val diopters = if (meters == Float.POSITIVE_INFINITY) 0.0f else 1.0f / meters
        return setFocusDistance(diopters)
    }

    /**
     * Enable or disable auto focus
     */
    fun setAutoFocusEnabled(enabled: Boolean): Boolean {
        return try {
            captureSession?.let { session ->
                val requestBuilder = session.device.createCaptureRequest(
                    if (isRecording) android.hardware.camera2.CameraDevice.TEMPLATE_RECORD
                    else android.hardware.camera2.CameraDevice.TEMPLATE_PREVIEW
                )

                if (enabled) {
                    requestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                } else {
                    requestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_OFF)
                    requestBuilder.set(CaptureRequest.LENS_FOCUS_DISTANCE, currentFocusDistance)
                }

                session.setRepeatingRequest(requestBuilder.build(), null, null)
            }

            isAutoFocusEnabled = enabled
            saveSettings()

            Log.i(TAG, "Auto focus ${if (enabled) "enabled" else "disabled"}")
            true

        } catch (e: Exception) {
            Log.e(TAG, "Error setting auto focus to $enabled", e)
            false
        }
    }

    /**
     * Calculate hyperfocal distance for current aperture
     */
    fun calculateHyperfocalDistance(apertureValue: Float = aperture): Float {
        aperture = apertureValue
        return (focalLength.pow(2)) / (aperture * circleOfConfusion * 1000f) // Convert to meters
    }

    /**
     * Set focus to hyperfocal distance
     */
    fun setHyperfocalFocus(apertureValue: Float = aperture): Boolean {
        val hyperfocalDistance = calculateHyperfocalDistance(apertureValue)
        Log.i(TAG, "Setting hyperfocal focus at ${hyperfocalDistance}m (f/$apertureValue)")
        return setFocusDistanceMeters(hyperfocalDistance)
    }

    /**
     * Calculate depth of field for current focus distance
     */
    fun calculateDepthOfField(apertureValue: Float = aperture): DepthOfFieldInfo {
        aperture = apertureValue
        val focusDistanceMeters = if (currentFocusDistance == 0.0f) {
            Float.POSITIVE_INFINITY
        } else {
            1.0f / currentFocusDistance
        }

        val hyperfocalDistance = calculateHyperfocalDistance()

        val nearDistance = if (focusDistanceMeters == Float.POSITIVE_INFINITY) {
            hyperfocalDistance
        } else {
            (hyperfocalDistance * focusDistanceMeters) / (hyperfocalDistance + focusDistanceMeters)
        }

        val farDistance = if (focusDistanceMeters >= hyperfocalDistance) {
            Float.POSITIVE_INFINITY
        } else {
            (hyperfocalDistance * focusDistanceMeters) / (hyperfocalDistance - focusDistanceMeters)
        }

        val totalDoF = if (farDistance == Float.POSITIVE_INFINITY) {
            Float.POSITIVE_INFINITY
        } else {
            farDistance - nearDistance
        }

        return DepthOfFieldInfo(
            focusDistance = focusDistanceMeters,
            nearDistance = nearDistance,
            farDistance = farDistance,
            totalDepth = totalDoF,
            hyperfocalDistance = hyperfocalDistance,
            aperture = aperture
        )
    }

    /**
     * Apply focus preset
     */
    fun applyPreset(presetName: String, apertureValue: Float = aperture): Boolean {
        val preset = focusPresets[presetName]
        return if (preset != null) {
            when (presetName) {
                "hyperfocal" -> setHyperfocalFocus(apertureValue)
                else -> setFocusDistance(preset.focusDistance)
            }
        } else {
            Log.w(TAG, "Unknown focus preset: $presetName")
            false
        }
    }

    /**
     * Get focus recommendation for photography scenario
     */
    fun getFocusRecommendation(scenario: String, subjectDistance: Float? = null): FocusRecommendation? {
        return when (scenario.lowercase()) {
            "landscape" -> FocusRecommendation(
                focusType = "hyperfocal",
                focusDistance = calculateHyperfocalDistance(),
                reason = "Maximize depth of field for entire scene",
                tips = listOf("Use small aperture (f/8-f/11)", "Focus at hyperfocal distance", "Everything from ${calculateHyperfocalDistance()/2}m to ∞ will be sharp")
            )
            "portrait" -> FocusRecommendation(
                focusType = "subject",
                focusDistance = subjectDistance ?: 2.0f,
                reason = "Focus on subject's eyes for sharp portrait",
                tips = listOf("Use wide aperture for shallow DoF", "Focus on nearest eye", "Consider focus tracking for moving subjects")
            )
            "macro" -> FocusRecommendation(
                focusType = "precise",
                focusDistance = 0.1f,
                reason = "Extreme close-up requires precise focus",
                tips = listOf("Use manual focus for precision", "Consider focus stacking", "Very shallow DoF at close distances")
            )
            "street" -> FocusRecommendation(
                focusType = "zone",
                focusDistance = 5.0f,
                reason = "Zone focusing for quick street photography",
                tips = listOf("Set focus to 3-5m", "Use f/8 for good DoF", "Prefocus for quick shooting")
            )
            else -> null
        }
    }

    /**
     * Enable focus assist features
     */
    fun setFocusAssistEnabled(enabled: Boolean) {
        focusAssistEnabled = enabled
        Log.i(TAG, "Focus assist ${if (enabled) "enabled" else "disabled"}")
    }

    /**
     * Enable focus peaking
     */
    fun setFocusPeakingEnabled(enabled: Boolean) {
        focusPeakingEnabled = enabled
        Log.i(TAG, "Focus peaking ${if (enabled) "enabled" else "disabled"}")
    }

    /**
     * Get current focus settings
     */
    fun getCurrentSettings(): FocusSettings {
        return FocusSettings(
            focusDistance = currentFocusDistance,
            focusDistanceMeters = if (currentFocusDistance == 0.0f) Float.POSITIVE_INFINITY else 1.0f / currentFocusDistance,
            isAutoFocusEnabled = isAutoFocusEnabled,
            hasManualFocus = hasManualFocus,
            focusAssistEnabled = focusAssistEnabled,
            focusPeakingEnabled = focusPeakingEnabled
        )
    }

    /**
     * Get available presets
     */
    fun getAvailablePresets(): Map<String, FocusPreset> = focusPresets

    /**
     * Get focus zones
     */
    fun getFocusZones(): Map<String, FocusZone> = focusZones

    /**
     * Format focus distance for display
     */
    fun formatFocusDistance(diopters: Float): String {
        return when {
            diopters == 0.0f -> "∞"
            diopters < 0.1f -> "%.1fm".format(1.0f / diopters)
            diopters < 1.0f -> "%.2fm".format(1.0f / diopters)
            else -> "%.0fcm".format((1.0f / diopters) * 100)
        }
    }

    /**
     * Update performance metrics
     */
    private fun updatePerformanceMetrics() {
        val currentTime = System.currentTimeMillis()
        val dof = calculateDepthOfField()

        performanceMetrics["current_focus_distance"] = currentFocusDistance
        performanceMetrics["focus_distance_meters"] = if (currentFocusDistance == 0.0f) "infinity" else 1.0f / currentFocusDistance
        performanceMetrics["focus_changes"] = focusChangeCount
        performanceMetrics["last_change_time"] = lastFocusChangeTime
        performanceMetrics["has_manual_focus"] = hasManualFocus
        performanceMetrics["auto_focus_enabled"] = isAutoFocusEnabled
        performanceMetrics["hyperfocal_distance"] = dof.hyperfocalDistance
        performanceMetrics["depth_of_field"] = if (dof.totalDepth == Float.POSITIVE_INFINITY) "infinite" else dof.totalDepth

        if (lastFocusChangeTime > 0) {
            performanceMetrics["time_since_last_change"] = currentTime - lastFocusChangeTime
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
            "focus_distance" to currentFocusDistance,
            "auto_focus_enabled" to isAutoFocusEnabled,
            "focus_assist_enabled" to focusAssistEnabled,
            "focus_peaking_enabled" to focusPeakingEnabled,
            "enabled" to isEnabled
        )

        // Save to SettingsManager when available
        coroutineScope.launch {
            try {
                // settingsManager?.savePluginSettings(name, settings)
                Log.d(TAG, "Focus settings saved: $settings")
            } catch (e: Exception) {
                Log.e(TAG, "Error saving focus settings", e)
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
            currentFocusDistance = 0.0f
            isAutoFocusEnabled = true
            focusAssistEnabled = false
            focusPeakingEnabled = false
            isEnabled = false

            Log.d(TAG, "Focus settings loaded")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading focus settings", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        saveSettings()
        Log.i(TAG, "ManualFocusControlPlugin destroyed")
    }

    /**
     * Data class for depth of field information
     */
    data class DepthOfFieldInfo(
        val focusDistance: Float,
        val nearDistance: Float,
        val farDistance: Float,
        val totalDepth: Float,
        val hyperfocalDistance: Float,
        val aperture: Float
    )

    /**
     * Data class for focus preset
     */
    data class FocusPreset(
        val focusDistance: Float, // -1 means calculate hyperfocal
        val description: String,
        val displayName: String
    )

    /**
     * Data class for focus zone
     */
    data class FocusZone(
        val minDiopters: Float,
        val maxDiopters: Float,
        val description: String
    )

    /**
     * Data class for focus recommendation
     */
    data class FocusRecommendation(
        val focusType: String,
        val focusDistance: Float,
        val reason: String,
        val tips: List<String>
    )

    /**
     * Data class for current focus settings
     */
    data class FocusSettings(
        val focusDistance: Float,
        val focusDistanceMeters: Float,
        val isAutoFocusEnabled: Boolean,
        val hasManualFocus: Boolean,
        val focusAssistEnabled: Boolean,
        val focusPeakingEnabled: Boolean
    )

    companion object {
        private const val TAG = "ManualFocusControl"
    }
}