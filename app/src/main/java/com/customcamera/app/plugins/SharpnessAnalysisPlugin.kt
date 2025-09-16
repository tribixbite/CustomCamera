package com.customcamera.app.plugins

import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.ImageProxy
import com.customcamera.app.engine.CameraContext
import com.customcamera.app.engine.plugins.ProcessingPlugin
import com.customcamera.app.engine.plugins.ProcessingResult
import com.customcamera.app.engine.plugins.ProcessingMetadata
import kotlin.math.sqrt

/**
 * SharpnessAnalysisPlugin provides real-time sharpness measurement
 * with focus confirmation indicators and optimal aperture suggestions.
 */
class SharpnessAnalysisPlugin : ProcessingPlugin() {

    override val name: String = "SharpnessAnalysis"
    override val version: String = "1.0.0"
    override val priority: Int = 75 // Lower priority for analysis

    private var cameraContext: CameraContext? = null

    // Analysis configuration
    private var isSharpnessAnalysisEnabled: Boolean = true
    private var processingInterval: Long = 250L // Process every 250ms
    private var lastProcessingTime: Long = 0L
    private var focusConfirmationThreshold: Float = 0.7f

    // Analysis results
    private var currentSharpnessScore: Float = 0f
    private var focusConfirmed: Boolean = false
    private var sharpnessHistory: MutableList<SharpnessAnalysis> = mutableListOf()

    data class SharpnessAnalysis(
        val sharpnessScore: Float,
        val focusConfirmed: Boolean,
        val optimalAperture: Float?,
        val depthOfFieldEstimate: Float,
        val analysisTimestamp: Long = System.currentTimeMillis()
    )

    override suspend fun initialize(context: CameraContext) {
        this.cameraContext = context
        Log.i(TAG, "SharpnessAnalysisPlugin initialized")

        loadSettings(context)

        context.debugLogger.logPlugin(
            name,
            "initialized",
            mapOf(
                "sharpnessAnalysisEnabled" to isSharpnessAnalysisEnabled,
                "processingInterval" to processingInterval,
                "focusThreshold" to focusConfirmationThreshold
            )
        )
    }

    override suspend fun onCameraReady(camera: Camera) {
        Log.i(TAG, "Camera ready for sharpness analysis")

        cameraContext?.debugLogger?.logPlugin(
            name,
            "camera_ready",
            mapOf("sharpnessAnalysisEnabled" to isSharpnessAnalysisEnabled)
        )
    }

    override suspend fun onCameraReleased(camera: Camera) {
        Log.i(TAG, "Camera released, stopping sharpness analysis")
        clearSharpnessData()
    }

    override suspend fun processFrame(image: ImageProxy): ProcessingResult {
        val currentTime = System.currentTimeMillis()

        if (!isSharpnessAnalysisEnabled || currentTime - lastProcessingTime < processingInterval) {
            return ProcessingResult.Skip
        }

        lastProcessingTime = currentTime

        return try {
            // Perform real-time sharpness measurement
            val analysis = analyzeSharpness(image)
            currentSharpnessScore = analysis.sharpnessScore
            focusConfirmed = analysis.focusConfirmed

            // Add to history
            sharpnessHistory.add(analysis)
            if (sharpnessHistory.size > 10) {
                sharpnessHistory.removeAt(0) // Keep last 10 analyses
            }

            // Log significant focus changes
            if (analysis.focusConfirmed && !focusConfirmed) {
                Log.i(TAG, "Focus confirmed - sharpness: ${String.format("%.2f", analysis.sharpnessScore)}")
            }

            val metadata = ProcessingMetadata(
                timestamp = currentTime,
                processingTimeMs = System.currentTimeMillis() - currentTime,
                frameNumber = 0L,
                imageSize = android.util.Size(image.width, image.height),
                additionalData = mapOf(
                    "sharpnessScore" to analysis.sharpnessScore,
                    "focusConfirmed" to analysis.focusConfirmed,
                    "optimalAperture" to (analysis.optimalAperture ?: "auto")
                )
            )

            ProcessingResult.Success(
                data = mapOf(
                    "sharpnessAnalysis" to analysis,
                    "focusConfirmed" to analysis.focusConfirmed
                ),
                metadata = metadata
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error in sharpness analysis", e)
            ProcessingResult.Failure("Sharpness analysis error: ${e.message}", e)
        }
    }

    /**
     * Analyze sharpness from image frame
     */
    private fun analyzeSharpness(image: ImageProxy): SharpnessAnalysis {
        try {
            // Real-time sharpness measurement using Laplacian variance
            val sharpnessScore = calculateLaplacianVariance(image)

            // Focus confirmation based on sharpness threshold
            val focusConfirmed = sharpnessScore >= focusConfirmationThreshold

            // Estimate optimal aperture based on sharpness
            val optimalAperture = suggestOptimalAperture(sharpnessScore)

            // Estimate depth of field (simplified calculation)
            val depthOfFieldEstimate = estimateDepthOfField(sharpnessScore)

            return SharpnessAnalysis(
                sharpnessScore = sharpnessScore,
                focusConfirmed = focusConfirmed,
                optimalAperture = optimalAperture,
                depthOfFieldEstimate = depthOfFieldEstimate
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error analyzing sharpness", e)
            return SharpnessAnalysis(
                sharpnessScore = 0f,
                focusConfirmed = false,
                optimalAperture = null,
                depthOfFieldEstimate = 0f
            )
        }
    }

    /**
     * Calculate Laplacian variance for sharpness measurement
     */
    private fun calculateLaplacianVariance(image: ImageProxy): Float {
        try {
            // Get luminance data from Y plane
            val yPlane = image.planes[0]
            val yBuffer = yPlane.buffer
            val width = image.width
            val height = image.height
            val rowStride = yPlane.rowStride

            var laplacianSum = 0.0
            var pixelCount = 0

            // Sample center region for sharpness (avoid edges)
            val startX = width / 4
            val endX = 3 * width / 4
            val startY = height / 4
            val endY = 3 * height / 4

            for (y in startY until endY - 1) {
                for (x in startX until endX - 1) {
                    val centerPixel = getPixelValue(yBuffer, x, y, rowStride)

                    // Apply Laplacian kernel (simplified 3x3)
                    val topPixel = getPixelValue(yBuffer, x, y - 1, rowStride)
                    val bottomPixel = getPixelValue(yBuffer, x, y + 1, rowStride)
                    val leftPixel = getPixelValue(yBuffer, x - 1, y, rowStride)
                    val rightPixel = getPixelValue(yBuffer, x + 1, y, rowStride)

                    val laplacian = (-4 * centerPixel + topPixel + bottomPixel + leftPixel + rightPixel).toDouble()
                    laplacianSum += laplacian * laplacian
                    pixelCount++
                }
            }

            val variance = if (pixelCount > 0) laplacianSum / pixelCount else 0.0
            return (variance / 10000.0).toFloat().coerceIn(0f, 1f) // Normalize to 0-1

        } catch (e: Exception) {
            Log.e(TAG, "Error calculating Laplacian variance", e)
            return 0f
        }
    }

    private fun getPixelValue(buffer: java.nio.ByteBuffer, x: Int, y: Int, rowStride: Int): Int {
        return try {
            val index = y * rowStride + x
            if (index < buffer.capacity()) {
                buffer.get(index).toInt() and 0xFF
            } else {
                128 // Default value if out of bounds
            }
        } catch (e: Exception) {
            128
        }
    }

    /**
     * Suggest optimal aperture based on sharpness
     */
    private fun suggestOptimalAperture(sharpnessScore: Float): Float? {
        return when {
            sharpnessScore < 0.3f -> 5.6f // Increase aperture for better sharpness
            sharpnessScore < 0.5f -> 4.0f // Moderate aperture
            sharpnessScore < 0.7f -> 2.8f // Good sharpness
            else -> null // Current aperture is optimal
        }
    }

    /**
     * Estimate depth of field based on sharpness analysis
     */
    private fun estimateDepthOfField(sharpnessScore: Float): Float {
        // Simplified depth of field estimation
        // Higher sharpness typically indicates shallow DOF or good focus
        return when {
            sharpnessScore > 0.8f -> 0.2f // Very shallow DOF
            sharpnessScore > 0.6f -> 0.5f // Moderate DOF
            sharpnessScore > 0.4f -> 1.0f // Good DOF
            else -> 2.0f // Deep DOF or out of focus
        }
    }

    /**
     * Get focus confirmation indicators
     */
    fun getFocusConfirmationStatus(): Map<String, Any> {
        return mapOf(
            "focusConfirmed" to focusConfirmed,
            "sharpnessScore" to currentSharpnessScore,
            "focusQuality" to getFocusQualityDescription(),
            "recommendedAction" to getRecommendedAction()
        )
    }

    /**
     * Get focus quality description
     */
    fun getFocusQualityDescription(): String {
        return when {
            currentSharpnessScore > 0.8f -> "Excellent focus"
            currentSharpnessScore > 0.6f -> "Good focus"
            currentSharpnessScore > 0.4f -> "Acceptable focus"
            currentSharpnessScore > 0.2f -> "Poor focus"
            else -> "Out of focus"
        }
    }

    /**
     * Get recommended action based on sharpness
     */
    fun getRecommendedAction(): String {
        return when {
            currentSharpnessScore < 0.3f -> "Refocus or check stabilization"
            currentSharpnessScore < 0.5f -> "Fine-tune focus"
            currentSharpnessScore < 0.7f -> "Focus is acceptable"
            else -> "Focus is optimal"
        }
    }

    /**
     * Enable or disable sharpness analysis
     */
    fun setSharpnessAnalysisEnabled(enabled: Boolean) {
        if (isSharpnessAnalysisEnabled != enabled) {
            isSharpnessAnalysisEnabled = enabled
            saveSettings()
            Log.i(TAG, "Sharpness analysis ${if (enabled) "enabled" else "disabled"}")
        }
    }

    /**
     * Set focus confirmation threshold
     */
    fun setFocusConfirmationThreshold(threshold: Float) {
        val clampedThreshold = threshold.coerceIn(0f, 1f)
        if (focusConfirmationThreshold != clampedThreshold) {
            focusConfirmationThreshold = clampedThreshold
            saveSettings()
            Log.i(TAG, "Focus confirmation threshold set to: $clampedThreshold")
        }
    }

    /**
     * Get current sharpness analysis
     */
    fun getCurrentSharpnessAnalysis(): SharpnessAnalysis? {
        return sharpnessHistory.lastOrNull()
    }

    /**
     * Get sharpness history
     */
    fun getSharpnessHistory(): List<SharpnessAnalysis> = sharpnessHistory.toList()

    override fun cleanup() {
        Log.i(TAG, "Cleaning up SharpnessAnalysisPlugin")

        clearSharpnessData()
        sharpnessHistory.clear()
        cameraContext = null
    }

    private fun clearSharpnessData() {
        currentSharpnessScore = 0f
        focusConfirmed = false
    }

    private fun loadSettings(context: CameraContext) {
        val settings = context.settingsManager

        try {
            isSharpnessAnalysisEnabled = settings.getPluginSetting(name, "sharpnessAnalysisEnabled", "true").toBoolean()
            processingInterval = settings.getPluginSetting(name, "processingInterval", "250").toLong()
            focusConfirmationThreshold = settings.getPluginSetting(name, "focusThreshold", "0.7").toFloat()

            Log.i(TAG, "Loaded settings: enabled=$isSharpnessAnalysisEnabled, interval=${processingInterval}ms, threshold=$focusConfirmationThreshold")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load settings, using defaults", e)
        }
    }

    private fun saveSettings() {
        val settings = cameraContext?.settingsManager ?: return

        settings.setPluginSetting(name, "sharpnessAnalysisEnabled", isSharpnessAnalysisEnabled.toString())
        settings.setPluginSetting(name, "processingInterval", processingInterval.toString())
        settings.setPluginSetting(name, "focusThreshold", focusConfirmationThreshold.toString())
    }

    companion object {
        private const val TAG = "SharpnessAnalysisPlugin"
    }
}