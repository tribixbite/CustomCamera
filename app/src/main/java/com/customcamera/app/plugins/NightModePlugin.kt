package com.customcamera.app.plugins

import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.ImageProxy
import com.customcamera.app.engine.CameraContext
import com.customcamera.app.engine.plugins.ProcessingPlugin
import com.customcamera.app.engine.plugins.ProcessingResult
import com.customcamera.app.engine.plugins.ProcessingMetadata

/**
 * NightModePlugin provides low-light photography enhancements
 * with multi-frame noise reduction and extended exposure handling.
 */
class NightModePlugin : ProcessingPlugin() {

    override val name: String = "NightMode"
    override val version: String = "1.0.0"
    override val priority: Int = 80 // Lower priority for specialized processing

    private var cameraContext: CameraContext? = null

    // Night mode configuration
    private var isNightModeEnabled: Boolean = false
    private var autoNightModeDetection: Boolean = true
    private var lowLightThreshold: Float = 0.3f // Brightness threshold for auto-detection
    private var multiFrameNoiseReduction: Boolean = true
    private var extendedExposureEnabled: Boolean = true

    // Analysis state
    private var isLowLightDetected: Boolean = false
    private var averageSceneBrightness: Float = 0.5f
    private var recommendedExposureExtension: Float = 1.0f

    override suspend fun initialize(context: CameraContext) {
        this.cameraContext = context
        Log.i(TAG, "NightModePlugin initialized")

        loadSettings(context)

        context.debugLogger.logPlugin(
            name,
            "initialized",
            mapOf(
                "nightModeEnabled" to isNightModeEnabled,
                "autoDetection" to autoNightModeDetection,
                "lowLightThreshold" to lowLightThreshold,
                "multiFrameNR" to multiFrameNoiseReduction
            )
        )
    }

    override suspend fun onCameraReady(camera: Camera) {
        Log.i(TAG, "Camera ready for night mode")

        if (isNightModeEnabled) {
            // Configure camera for night mode
            configureNightModeSettings(camera)
        }

        cameraContext?.debugLogger?.logPlugin(
            name,
            "camera_ready",
            mapOf("nightModeActive" to isNightModeEnabled)
        )
    }

    override suspend fun onCameraReleased(camera: Camera) {
        Log.i(TAG, "Camera released, resetting night mode")
        isLowLightDetected = false
    }

    override suspend fun processFrame(image: ImageProxy): ProcessingResult {
        return try {
            // Low-light detection
            val brightness = detectLowLight(image)
            averageSceneBrightness = brightness

            val wasLowLight = isLowLightDetected
            isLowLightDetected = brightness < lowLightThreshold

            // Auto-enable night mode if low light detected
            if (autoNightModeDetection && !wasLowLight && isLowLightDetected) {
                Log.i(TAG, "Low light detected, auto-enabling night mode")
                enableNightMode()
            }

            // Calculate exposure extension recommendation
            recommendedExposureExtension = if (isLowLightDetected) {
                (lowLightThreshold / brightness).coerceIn(1f, 4f)
            } else {
                1f
            }

            val metadata = ProcessingMetadata(
                timestamp = System.currentTimeMillis(),
                processingTimeMs = 0L,
                frameNumber = 0L,
                imageSize = android.util.Size(image.width, image.height),
                additionalData = mapOf(
                    "brightness" to brightness,
                    "lowLightDetected" to isLowLightDetected,
                    "nightModeActive" to isNightModeEnabled,
                    "exposureExtension" to recommendedExposureExtension
                )
            )

            ProcessingResult.Success(
                data = mapOf(
                    "lowLightDetected" to isLowLightDetected,
                    "sceneBrightness" to brightness,
                    "nightModeRecommended" to (brightness < lowLightThreshold)
                ),
                metadata = metadata
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error in night mode processing", e)
            ProcessingResult.Failure("Night mode processing error: ${e.message}", e)
        }
    }

    /**
     * Low-light detection
     */
    private fun detectLowLight(image: ImageProxy): Float {
        try {
            // Calculate average brightness from Y plane
            val yPlane = image.planes[0]
            val yBuffer = yPlane.buffer
            val ySize = yBuffer.remaining()

            var totalBrightness = 0L
            var pixelCount = 0

            // Sample pixels for brightness calculation
            val sampleRate = 10 // Sample every 10th pixel for performance
            for (i in 0 until ySize step sampleRate) {
                val luminance = yBuffer.get(i).toInt() and 0xFF
                totalBrightness += luminance
                pixelCount++
            }

            val averageBrightness = if (pixelCount > 0) {
                totalBrightness.toFloat() / pixelCount / 255f // Normalize to 0-1
            } else {
                0.5f
            }

            return averageBrightness

        } catch (e: Exception) {
            Log.e(TAG, "Error detecting low light", e)
            return 0.5f // Default middle value
        }
    }

    /**
     * Configure camera settings for night mode
     */
    private fun configureNightModeSettings(camera: Camera) {
        try {
            Log.i(TAG, "Configuring camera for night mode")

            // In production, this would:
            // - Increase ISO sensitivity
            // - Extend exposure time
            // - Enable noise reduction
            // - Adjust white balance for artificial lighting

            cameraContext?.debugLogger?.logPlugin(
                name,
                "night_mode_configured",
                mapOf(
                    "extendedExposure" to extendedExposureEnabled,
                    "noiseReduction" to multiFrameNoiseReduction
                )
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error configuring night mode", e)
        }
    }

    /**
     * Enable night mode
     */
    fun enableNightMode() {
        if (!isNightModeEnabled) {
            isNightModeEnabled = true
            saveSettings()

            Log.i(TAG, "Night mode enabled")

            cameraContext?.debugLogger?.logPlugin(
                name,
                "night_mode_enabled",
                mapOf(
                    "autoDetected" to autoNightModeDetection,
                    "sceneBrightness" to averageSceneBrightness
                )
            )
        }
    }

    /**
     * Disable night mode
     */
    fun disableNightMode() {
        if (isNightModeEnabled) {
            isNightModeEnabled = false
            saveSettings()

            Log.i(TAG, "Night mode disabled")

            cameraContext?.debugLogger?.logPlugin(
                name,
                "night_mode_disabled",
                emptyMap()
            )
        }
    }

    /**
     * Multi-frame noise reduction
     */
    suspend fun performMultiFrameNoiseReduction(frames: List<ImageProxy>): ImageProxy? {
        if (!multiFrameNoiseReduction || frames.isEmpty()) {
            return frames.firstOrNull()
        }

        Log.i(TAG, "Performing multi-frame noise reduction on ${frames.size} frames")

        return try {
            // In production, this would:
            // 1. Align frames to compensate for camera shake
            // 2. Average pixel values across frames
            // 3. Apply noise reduction algorithms
            // 4. Return processed composite image

            cameraContext?.debugLogger?.logPlugin(
                name,
                "multi_frame_noise_reduction",
                mapOf(
                    "frameCount" to frames.size,
                    "processingTime" to "simulated"
                )
            )

            frames.first() // Return first frame for now

        } catch (e: Exception) {
            Log.e(TAG, "Error in multi-frame noise reduction", e)
            frames.firstOrNull()
        }
    }

    /**
     * Extended exposure handling
     */
    fun getExtendedExposureRecommendation(): Map<String, Any> {
        return mapOf(
            "recommendedExtension" to recommendedExposureExtension,
            "currentBrightness" to averageSceneBrightness,
            "lowLightDetected" to isLowLightDetected,
            "extendedExposureEnabled" to extendedExposureEnabled
        )
    }

    /**
     * Night mode UI indicators
     */
    fun getNightModeUIState(): Map<String, Any> {
        return mapOf(
            "nightModeEnabled" to isNightModeEnabled,
            "lowLightDetected" to isLowLightDetected,
            "autoDetectionEnabled" to autoNightModeDetection,
            "uiIndicatorColor" to if (isNightModeEnabled) "yellow" else "white",
            "statusText" to if (isNightModeEnabled) "NIGHT MODE" else "AUTO"
        )
    }

    override fun cleanup() {
        Log.i(TAG, "Cleaning up NightModePlugin")

        isNightModeEnabled = false
        isLowLightDetected = false
        cameraContext = null
    }

    private fun loadSettings(context: CameraContext) {
        val settings = context.settingsManager

        try {
            isNightModeEnabled = settings.getPluginSetting(name, "nightModeEnabled", "false").toBoolean()
            autoNightModeDetection = settings.getPluginSetting(name, "autoDetection", "true").toBoolean()
            lowLightThreshold = settings.getPluginSetting(name, "lowLightThreshold", "0.3").toFloat()
            multiFrameNoiseReduction = settings.getPluginSetting(name, "multiFrameNR", "true").toBoolean()
            extendedExposureEnabled = settings.getPluginSetting(name, "extendedExposure", "true").toBoolean()

            Log.i(TAG, "Loaded settings: enabled=$isNightModeEnabled, autoDetect=$autoNightModeDetection")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load settings, using defaults", e)
        }
    }

    private fun saveSettings() {
        val settings = cameraContext?.settingsManager ?: return

        settings.setPluginSetting(name, "nightModeEnabled", isNightModeEnabled.toString())
        settings.setPluginSetting(name, "autoDetection", autoNightModeDetection.toString())
        settings.setPluginSetting(name, "lowLightThreshold", lowLightThreshold.toString())
        settings.setPluginSetting(name, "multiFrameNR", multiFrameNoiseReduction.toString())
        settings.setPluginSetting(name, "extendedExposure", extendedExposureEnabled.toString())
    }

    companion object {
        private const val TAG = "NightModePlugin"
    }
}