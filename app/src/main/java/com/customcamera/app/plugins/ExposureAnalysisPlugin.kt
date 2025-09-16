package com.customcamera.app.plugins

import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.ImageProxy
import com.customcamera.app.engine.CameraContext
import com.customcamera.app.engine.plugins.ProcessingPlugin
import com.customcamera.app.engine.plugins.ProcessingResult
import com.customcamera.app.engine.plugins.ProcessingMetadata

/**
 * ExposureAnalysisPlugin provides real-time exposure monitoring
 * with exposure warnings and optimal exposure recommendations.
 */
class ExposureAnalysisPlugin : ProcessingPlugin() {

    override val name: String = "ExposureAnalysis"
    override val version: String = "1.0.0"
    override val priority: Int = 65 // Medium priority for analysis

    private var cameraContext: CameraContext? = null

    // Analysis configuration
    private var isAnalysisEnabled: Boolean = true
    private var processingInterval: Long = 300L // Process every 300ms
    private var lastProcessingTime: Long = 0L

    // Analysis results
    private var currentExposureAnalysis: ExposureAnalysis? = null
    private var exposureHistory: MutableList<ExposureAnalysis> = mutableListOf()

    data class ExposureAnalysis(
        val averageBrightness: Float,
        val dynamicRange: Float,
        val shadowDetails: Float,
        val highlightDetails: Float,
        val contrastRatio: Float,
        val exposureRecommendation: ExposureRecommendation,
        val analysisTimestamp: Long = System.currentTimeMillis()
    )

    data class ExposureRecommendation(
        val recommendation: String,
        val suggestedEVAdjustment: Float,
        val confidenceLevel: Float,
        val reasoning: String
    )

    override suspend fun initialize(context: CameraContext) {
        this.cameraContext = context
        Log.i(TAG, "ExposureAnalysisPlugin initialized")

        loadSettings(context)

        context.debugLogger.logPlugin(
            name,
            "initialized",
            mapOf(
                "analysisEnabled" to isAnalysisEnabled,
                "processingInterval" to processingInterval
            )
        )
    }

    override suspend fun onCameraReady(camera: Camera) {
        Log.i(TAG, "Camera ready for exposure analysis")

        cameraContext?.debugLogger?.logPlugin(
            name,
            "camera_ready",
            mapOf("analysisEnabled" to isAnalysisEnabled)
        )
    }

    override suspend fun onCameraReleased(camera: Camera) {
        Log.i(TAG, "Camera released, stopping exposure analysis")
        clearAnalysis()
    }

    override suspend fun processFrame(image: ImageProxy): ProcessingResult {
        val currentTime = System.currentTimeMillis()

        if (!isAnalysisEnabled || currentTime - lastProcessingTime < processingInterval) {
            return ProcessingResult.Skip
        }

        lastProcessingTime = currentTime

        return try {
            // Perform real-time exposure analysis
            val analysis = analyzeExposure(image)
            currentExposureAnalysis = analysis

            // Add to history
            exposureHistory.add(analysis)
            if (exposureHistory.size > 20) {
                exposureHistory.removeAt(0) // Keep last 20 analyses
            }

            // Log significant exposure changes
            if (analysis.exposureRecommendation.confidenceLevel > 0.7f) {
                Log.i(TAG, "Exposure recommendation: ${analysis.exposureRecommendation.recommendation}")
            }

            val metadata = ProcessingMetadata(
                timestamp = currentTime,
                processingTimeMs = System.currentTimeMillis() - currentTime,
                frameNumber = 0L,
                imageSize = android.util.Size(image.width, image.height),
                additionalData = mapOf(
                    "averageBrightness" to analysis.averageBrightness,
                    "dynamicRange" to analysis.dynamicRange,
                    "recommendation" to analysis.exposureRecommendation.recommendation
                )
            )

            ProcessingResult.Success(
                data = mapOf(
                    "exposureAnalysis" to analysis,
                    "recommendation" to analysis.exposureRecommendation
                ),
                metadata = metadata
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error in exposure analysis", e)
            ProcessingResult.Failure("Exposure analysis error: ${e.message}", e)
        }
    }

    /**
     * Analyze exposure from image frame
     */
    private fun analyzeExposure(image: ImageProxy): ExposureAnalysis {
        try {
            // Get luminance data from Y plane
            val yPlane = image.planes[0]
            val yBuffer = yPlane.buffer
            val ySize = yBuffer.remaining()

            var totalBrightness = 0L
            var darkPixels = 0
            var brightPixels = 0
            var midtonePixels = 0

            val brightnessBins = IntArray(256)

            // Analyze pixel distribution
            for (i in 0 until ySize) {
                val luminance = yBuffer.get(i).toInt() and 0xFF
                totalBrightness += luminance
                brightnessBins[luminance]++

                when {
                    luminance < 64 -> darkPixels++
                    luminance > 192 -> brightPixels++
                    else -> midtonePixels++
                }
            }

            val averageBrightness = totalBrightness.toFloat() / ySize

            // Calculate dynamic range
            val nonZeroBins = brightnessBins.count { it > 0 }
            val dynamicRange = (nonZeroBins.toFloat() / 256) * 100f

            // Calculate shadow and highlight details
            val shadowDetails = (darkPixels.toFloat() / ySize) * 100f
            val highlightDetails = (brightPixels.toFloat() / ySize) * 100f

            // Calculate contrast ratio
            val darkestBin = brightnessBins.indexOfFirst { it > 0 }
            val brightestBin = brightnessBins.indexOfLast { it > 0 }
            val contrastRatio = if (darkestBin >= 0 && brightestBin >= 0) {
                (brightestBin - darkestBin).toFloat() / 256f * 100f
            } else {
                0f
            }

            // Generate exposure recommendation
            val recommendation = generateExposureRecommendation(
                averageBrightness, shadowDetails, highlightDetails, dynamicRange
            )

            return ExposureAnalysis(
                averageBrightness = averageBrightness,
                dynamicRange = dynamicRange,
                shadowDetails = shadowDetails,
                highlightDetails = highlightDetails,
                contrastRatio = contrastRatio,
                exposureRecommendation = recommendation
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error analyzing exposure", e)
            return ExposureAnalysis(
                averageBrightness = 128f,
                dynamicRange = 0f,
                shadowDetails = 0f,
                highlightDetails = 0f,
                contrastRatio = 0f,
                exposureRecommendation = ExposureRecommendation("Error", 0f, 0f, "Analysis failed")
            )
        }
    }

    /**
     * Generate optimal exposure recommendations
     */
    private fun generateExposureRecommendation(
        brightness: Float,
        shadows: Float,
        highlights: Float,
        dynamicRange: Float
    ): ExposureRecommendation {

        val recommendation: String
        val suggestedEV: Float
        val confidence: Float
        val reasoning: String

        when {
            brightness < 80f && shadows > 30f -> {
                recommendation = "Increase exposure"
                suggestedEV = +1.0f
                confidence = 0.8f
                reasoning = "Image too dark, many shadow areas"
            }
            brightness > 180f && highlights > 20f -> {
                recommendation = "Decrease exposure"
                suggestedEV = -1.0f
                confidence = 0.8f
                reasoning = "Image too bright, highlight clipping"
            }
            dynamicRange < 40f -> {
                recommendation = "Increase contrast"
                suggestedEV = 0f
                confidence = 0.6f
                reasoning = "Low dynamic range, flat image"
            }
            brightness in 100f..160f && highlights < 15f && shadows < 25f -> {
                recommendation = "Optimal exposure"
                suggestedEV = 0f
                confidence = 0.9f
                reasoning = "Well-balanced exposure"
            }
            else -> {
                recommendation = "Fine-tune exposure"
                suggestedEV = (128f - brightness) / 128f
                confidence = 0.5f
                reasoning = "Minor adjustments may improve image"
            }
        }

        return ExposureRecommendation(
            recommendation = recommendation,
            suggestedEVAdjustment = suggestedEV,
            confidenceLevel = confidence,
            reasoning = reasoning
        )
    }

    /**
     * Get current exposure analysis
     */
    fun getCurrentAnalysis(): ExposureAnalysis? = currentExposureAnalysis

    /**
     * Get exposure history
     */
    fun getExposureHistory(): List<ExposureAnalysis> = exposureHistory.toList()

    /**
     * Clear exposure history
     */
    fun clearExposureHistory() {
        exposureHistory.clear()
        Log.i(TAG, "Exposure history cleared")
    }

    /**
     * Enable or disable analysis
     */
    fun setAnalysisEnabled(enabled: Boolean) {
        if (isAnalysisEnabled != enabled) {
            isAnalysisEnabled = enabled
            saveSettings()
            Log.i(TAG, "Exposure analysis ${if (enabled) "enabled" else "disabled"}")
        }
    }

    override fun cleanup() {
        Log.i(TAG, "Cleaning up ExposureAnalysisPlugin")

        clearAnalysis()
        exposureHistory.clear()
        cameraContext = null
    }

    private fun clearAnalysis() {
        currentExposureAnalysis = null
    }


    private fun loadSettings(context: CameraContext) {
        val settings = context.settingsManager

        try {
            isAnalysisEnabled = settings.getPluginSetting(name, "analysisEnabled", "true").toBoolean()
            processingInterval = settings.getPluginSetting(name, "processingInterval", "300").toLong()

            Log.i(TAG, "Loaded settings: enabled=$isAnalysisEnabled, interval=${processingInterval}ms")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load settings, using defaults", e)
        }
    }

    private fun saveSettings() {
        val settings = cameraContext?.settingsManager ?: return

        settings.setPluginSetting(name, "analysisEnabled", isAnalysisEnabled.toString())
        settings.setPluginSetting(name, "processingInterval", processingInterval.toString())
    }

    companion object {
        private const val TAG = "ExposureAnalysisPlugin"
    }
}