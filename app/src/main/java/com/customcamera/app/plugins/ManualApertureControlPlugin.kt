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
 * Professional aperture control plugin for devices with variable aperture support
 * Provides precise f-stop control with depth of field calculations
 */
class ManualApertureControlPlugin : ControlPlugin() {
    override val name: String = "ManualApertureControl"

    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    private var isEnabled = false

    // Aperture control state
    private var currentAperture: Float = 2.8f
    private var apertureRange: Range<Float> = Range(1.4f, 8.0f)
    private var supportedApertures: FloatArray = floatArrayOf(1.4f, 2.0f, 2.8f, 4.0f, 5.6f, 8.0f)
    private var hasVariableAperture = false

    // Camera properties for DoF calculations
    private var sensorSize = 5.4f // Diagonal in mm (typical smartphone sensor)
    private var focalLength = 26f // Equivalent focal length in mm

    // Performance tracking
    private var lastApertureChangeTime = 0L
    private var apertureChangeCount = 0
    private var performanceMetrics = mutableMapOf<String, Any>()

    // Aperture presets for different photography scenarios
    private val aperturePresets = mapOf(
        "portrait" to 2.8f,
        "landscape" to 8.0f,
        "macro" to 4.0f,
        "lowlight" to 1.4f,
        "street" to 5.6f,
        "group" to 4.0f,
        "bokeh_max" to 1.4f,
        "sharp_max" to 8.0f
    )

    override fun initialize(cameraManager: CameraManager?, cameraId: String?) {
        super.initialize(cameraManager, cameraId)

        coroutineScope.launch {
            detectApertureCapabilities()
            loadSettings()

            Log.i(TAG, "ManualApertureControlPlugin initialized")
            Log.i(TAG, "Variable aperture supported: $hasVariableAperture")
            Log.i(TAG, "Aperture range: ${apertureRange.lower}f to ${apertureRange.upper}f")
        }
    }

    /**
     * Detect camera's aperture capabilities
     */
    private suspend fun detectApertureCapabilities() = withContext(Dispatchers.IO) {
        try {
            cameraManager?.let { manager ->
                cameraId?.let { id ->
                    val characteristics = manager.getCameraCharacteristics(id)

                    // Check for variable aperture support
                    val availableApertures = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)
                    availableApertures?.let { apertures ->
                        hasVariableAperture = apertures.size > 1
                        supportedApertures = apertures

                        if (hasVariableAperture) {
                            apertureRange = Range(apertures.minOrNull() ?: 1.4f, apertures.maxOrNull() ?: 8.0f)
                            currentAperture = apertures[apertures.size / 2] // Start with middle aperture
                        }

                        Log.i(TAG, "Supported apertures: ${apertures.contentToString()}")
                    } ?: run {
                        // Simulate typical smartphone apertures for compatibility
                        Log.w(TAG, "No aperture info available, using simulated values")
                        hasVariableAperture = false
                        supportedApertures = floatArrayOf(1.8f, 2.4f) // Typical dual-aperture phone
                    }

                    // Get sensor size for DoF calculations
                    val sensorSizeInfo = characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
                    sensorSizeInfo?.let { size ->
                        sensorSize = sqrt(size.width.pow(2) + size.height.pow(2))
                    }

                    // Get focal length
                    val focalLengthInfo = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                    focalLengthInfo?.let { lengths ->
                        focalLength = lengths[0] // Use primary focal length
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting aperture capabilities", e)
            // Use default values for compatibility
            hasVariableAperture = false
            supportedApertures = floatArrayOf(1.8f, 2.4f)
        }
    }

    /**
     * Set aperture value (f-stop)
     */
    fun setAperture(fStop: Float): Boolean {
        if (!hasVariableAperture) {
            Log.w(TAG, "Variable aperture not supported on this device")
            return false
        }

        val clampedAperture = fStop.coerceIn(apertureRange.lower, apertureRange.upper)

        // Find closest supported aperture
        val closestAperture = supportedApertures.minByOrNull { kotlin.math.abs(it - clampedAperture) }
            ?: clampedAperture

        return try {
            currentAperture = closestAperture
            lastApertureChangeTime = System.currentTimeMillis()
            apertureChangeCount++

            // Apply to capture request if session is active
            captureSession?.let { session ->
                val requestBuilder = session.device.createCaptureRequest(
                    if (isRecording) android.hardware.camera2.CameraDevice.TEMPLATE_RECORD
                    else android.hardware.camera2.CameraDevice.TEMPLATE_PREVIEW
                )

                requestBuilder.set(CaptureRequest.LENS_APERTURE, currentAperture)
                session.setRepeatingRequest(requestBuilder.build(), null, null)
            }

            saveSettings()
            updatePerformanceMetrics()

            Log.i(TAG, "Aperture set to f/$currentAperture")
            true

        } catch (e: Exception) {
            Log.e(TAG, "Error setting aperture to f/$fStop", e)
            false
        }
    }

    /**
     * Get current aperture value
     */
    fun getCurrentAperture(): Float = currentAperture

    /**
     * Get aperture range
     */
    fun getApertureRange(): Range<Float> = apertureRange

    /**
     * Get supported aperture values
     */
    fun getSupportedApertures(): FloatArray = supportedApertures

    /**
     * Check if device has variable aperture
     */
    fun hasVariableApertureSupport(): Boolean = hasVariableAperture

    /**
     * Apply aperture preset
     */
    fun applyPreset(presetName: String): Boolean {
        val aperture = aperturePresets[presetName]
        return if (aperture != null) {
            setAperture(aperture)
        } else {
            Log.w(TAG, "Unknown aperture preset: $presetName")
            false
        }
    }

    /**
     * Get available presets
     */
    fun getAvailablePresets(): Map<String, Float> = aperturePresets

    /**
     * Calculate depth of field for current settings
     */
    fun calculateDepthOfField(focusDistance: Float): DepthOfFieldInfo {
        val hyperfocalDistance = calculateHyperfocalDistance()

        val nearDistance = (hyperfocalDistance * focusDistance) / (hyperfocalDistance + focusDistance)
        val farDistance = if (focusDistance >= hyperfocalDistance) {
            Float.POSITIVE_INFINITY
        } else {
            (hyperfocalDistance * focusDistance) / (hyperfocalDistance - focusDistance)
        }

        val totalDoF = if (farDistance == Float.POSITIVE_INFINITY) {
            Float.POSITIVE_INFINITY
        } else {
            farDistance - nearDistance
        }

        return DepthOfFieldInfo(
            nearDistance = nearDistance,
            farDistance = farDistance,
            totalDepth = totalDoF,
            hyperfocalDistance = hyperfocalDistance,
            aperture = currentAperture,
            focusDistance = focusDistance
        )
    }

    /**
     * Calculate hyperfocal distance for current aperture
     */
    fun calculateHyperfocalDistance(): Float {
        val circleOfConfusion = sensorSize / 1500f // Typical CoC for smartphone sensors
        return (focalLength.pow(2)) / (currentAperture * circleOfConfusion * 1000f) // Convert to meters
    }

    /**
     * Get aperture-specific recommendations
     */
    fun getApertureRecommendations(scenario: String): ApertureRecommendation? {
        return when (scenario.lowercase()) {
            "portrait" -> ApertureRecommendation(
                aperture = 2.8f,
                reason = "Shallow depth of field for subject isolation",
                pros = listOf("Beautiful bokeh", "Subject separation"),
                cons = listOf("Narrow focus plane", "May miss focus")
            )
            "landscape" -> ApertureRecommendation(
                aperture = 8.0f,
                reason = "Maximum sharpness across the frame",
                pros = listOf("Everything in focus", "Maximum detail"),
                cons = listOf("Requires more light", "Less subject isolation")
            )
            "macro" -> ApertureRecommendation(
                aperture = 5.6f,
                reason = "Balance between DoF and sharpness",
                pros = listOf("Adequate depth", "Good sharpness"),
                cons = listOf("Still shallow DoF at close distances")
            )
            "lowlight" -> ApertureRecommendation(
                aperture = apertureRange.lower,
                reason = "Maximum light gathering",
                pros = listOf("Brightest image", "Fastest shutter speeds"),
                cons = listOf("Very shallow DoF", "Potential lens aberrations")
            )
            else -> null
        }
    }

    /**
     * Format aperture value for display
     */
    fun formatAperture(fStop: Float): String {
        return when {
            fStop == fStop.toInt().toFloat() -> "f/${fStop.toInt()}"
            else -> "f/%.1f".format(fStop)
        }
    }

    /**
     * Update performance metrics
     */
    private fun updatePerformanceMetrics() {
        val currentTime = System.currentTimeMillis()
        performanceMetrics["current_aperture"] = currentAperture
        performanceMetrics["aperture_changes"] = apertureChangeCount
        performanceMetrics["last_change_time"] = lastApertureChangeTime
        performanceMetrics["has_variable_aperture"] = hasVariableAperture
        performanceMetrics["supported_apertures_count"] = supportedApertures.size
        performanceMetrics["hyperfocal_distance"] = calculateHyperfocalDistance()

        if (lastApertureChangeTime > 0) {
            performanceMetrics["time_since_last_change"] = currentTime - lastApertureChangeTime
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
            "aperture" to currentAperture,
            "enabled" to isEnabled
        )

        // Save to SettingsManager when available
        coroutineScope.launch {
            try {
                // settingsManager?.savePluginSettings(name, settings)
                Log.d(TAG, "Aperture settings saved: $settings")
            } catch (e: Exception) {
                Log.e(TAG, "Error saving aperture settings", e)
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
            currentAperture = 2.8f
            isEnabled = false

            Log.d(TAG, "Aperture settings loaded")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading aperture settings", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        saveSettings()
        Log.i(TAG, "ManualApertureControlPlugin destroyed")
    }

    /**
     * Data class for depth of field information
     */
    data class DepthOfFieldInfo(
        val nearDistance: Float,
        val farDistance: Float,
        val totalDepth: Float,
        val hyperfocalDistance: Float,
        val aperture: Float,
        val focusDistance: Float
    )

    /**
     * Data class for aperture recommendations
     */
    data class ApertureRecommendation(
        val aperture: Float,
        val reason: String,
        val pros: List<String>,
        val cons: List<String>
    )

    companion object {
        private const val TAG = "ManualApertureControl"
    }
}