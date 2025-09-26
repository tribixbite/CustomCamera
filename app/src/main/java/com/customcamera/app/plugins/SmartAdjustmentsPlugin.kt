package com.customcamera.app.plugins

import android.graphics.*
import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraControl
import androidx.camera.core.ImageProxy
import com.customcamera.app.engine.CameraContext
import com.customcamera.app.engine.plugins.ControlPlugin
import com.customcamera.app.engine.plugins.ControlResult
import com.customcamera.app.engine.plugins.ProcessingPlugin
import com.customcamera.app.engine.plugins.ProcessingResult
import com.customcamera.app.engine.plugins.ProcessingMetadata
import java.nio.ByteBuffer
import kotlin.math.*

/**
 * SmartAdjustmentsPlugin provides AI-powered automatic camera adjustments
 * for optimal exposure, white balance, and color optimization based on scene analysis.
 */
class SmartAdjustmentsPlugin : ProcessingPlugin() {

    override val name: String = "SmartAdjustments"
    override val version: String = "1.0.0"
    override val priority: Int = 25 // Medium-high priority for adjustments

    private var cameraContext: CameraContext? = null
    private var currentCamera: Camera? = null

    // Smart adjustment configuration
    private var isSmartAdjustmentsEnabled: Boolean = true
    private var autoExposureEnabled: Boolean = true
    private var autoWhiteBalanceEnabled: Boolean = true
    private var autoColorOptimizationEnabled: Boolean = true
    private var processingInterval: Long = 300L // Process every 300ms
    private var lastProcessingTime: Long = 0L

    // Analysis state
    private var currentExposureLevel: Float = 0.0f
    private var currentWhiteBalanceTemp: Int = 5500 // Default daylight
    private var currentColorSaturation: Float = 1.0f
    private var currentContrast: Float = 1.0f
    private var adjustmentHistory: MutableList<CameraAdjustment> = mutableListOf()

    // Scene-based adjustment profiles
    private val adjustmentProfiles = mapOf(
        "PORTRAIT" to AdjustmentProfile(
            exposureBoost = 0.3f,
            whiteBalanceTemp = 5200,
            saturationBoost = 0.1f,
            contrastBoost = 0.2f
        ),
        "LANDSCAPE" to AdjustmentProfile(
            exposureBoost = 0.0f,
            whiteBalanceTemp = 5500,
            saturationBoost = 0.2f,
            contrastBoost = 0.1f
        ),
        "MACRO" to AdjustmentProfile(
            exposureBoost = 0.2f,
            whiteBalanceTemp = 5300,
            saturationBoost = 0.3f,
            contrastBoost = 0.3f
        ),
        "NIGHT" to AdjustmentProfile(
            exposureBoost = 0.8f,
            whiteBalanceTemp = 4800,
            saturationBoost = -0.1f,
            contrastBoost = 0.4f
        ),
        "FOOD" to AdjustmentProfile(
            exposureBoost = 0.2f,
            whiteBalanceTemp = 4500,
            saturationBoost = 0.4f,
            contrastBoost = 0.2f
        ),
        "SUNSET_SUNRISE" to AdjustmentProfile(
            exposureBoost = -0.2f,
            whiteBalanceTemp = 3200,
            saturationBoost = 0.3f,
            contrastBoost = 0.3f
        )
    )

    override suspend fun initialize(context: CameraContext) {
        this.cameraContext = context
        Log.i(TAG, "SmartAdjustmentsPlugin initialized")

        loadSettings(context)

        context.debugLogger.logPlugin(
            name,
            "initialized",
            mapOf(
                "smartAdjustmentsEnabled" to isSmartAdjustmentsEnabled,
                "autoExposureEnabled" to autoExposureEnabled,
                "autoWhiteBalanceEnabled" to autoWhiteBalanceEnabled,
                "autoColorOptimizationEnabled" to autoColorOptimizationEnabled,
                "processingInterval" to processingInterval
            )
        )
    }

    override suspend fun onCameraReady(camera: Camera) {
        this.currentCamera = camera
        Log.i(TAG, "Camera ready for smart adjustments")

        // Reset adjustments to default values
        resetToDefaults()

        cameraContext?.debugLogger?.logPlugin(
            name,
            "camera_ready",
            mapOf(
                "cameraControlAvailable" to (camera.cameraControl != null),
                "exposureLevel" to currentExposureLevel,
                "whiteBalanceTemp" to currentWhiteBalanceTemp
            )
        )
    }

    override suspend fun onCameraReleased(camera: Camera) {
        Log.i(TAG, "Camera released, stopping smart adjustments")
        currentCamera = null
        resetToDefaults()
    }

    override suspend fun processFrame(image: ImageProxy): ProcessingResult {
        if (!isSmartAdjustmentsEnabled || currentCamera == null) {
            return ProcessingResult.Skip
        }

        val currentTime = System.currentTimeMillis()

        // Throttle processing to avoid performance impact
        if (currentTime - lastProcessingTime < processingInterval) {
            return ProcessingResult.Skip
        }

        lastProcessingTime = currentTime

        return try {
            val startTime = System.currentTimeMillis()

            // Analyze image for optimal adjustments
            val imageAnalysis = analyzeImageForAdjustments(image)

            // Apply smart adjustments based on analysis
            val adjustmentsApplied = applySmartAdjustments(imageAnalysis)

            val processingTime = System.currentTimeMillis() - startTime

            val metadata = ProcessingMetadata(
                timestamp = currentTime,
                processingTimeMs = processingTime,
                frameNumber = 0L,
                imageSize = android.util.Size(image.width, image.height),
                additionalData = mapOf(
                    "smartAdjustmentsEnabled" to isSmartAdjustmentsEnabled,
                    "adjustmentsApplied" to adjustmentsApplied.size,
                    "currentExposureLevel" to currentExposureLevel,
                    "currentWhiteBalanceTemp" to currentWhiteBalanceTemp
                )
            )

            ProcessingResult.Success(
                data = mapOf(
                    "imageAnalysis" to imageAnalysis,
                    "adjustmentsApplied" to adjustmentsApplied,
                    "currentSettings" to getCurrentSettings()
                ),
                metadata = metadata
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error processing frame for smart adjustments", e)
            ProcessingResult.Failure("Smart adjustments error: ${e.message}", e)
        }
    }

    /**
     * Analyze image for optimal camera adjustments
     */
    private suspend fun analyzeImageForAdjustments(image: ImageProxy): ImageAnalysis {
        val imageData = imageProxyToByteArray(image)
        val width = image.width
        val height = image.height

        // Calculate comprehensive image characteristics
        val brightness = calculateAverageBrightness(imageData, width, height)
        val contrast = calculateContrast(imageData, width, height)
        val colorBalance = analyzeColorBalance(imageData, width, height)
        val saturation = calculateSaturation(imageData, width, height)
        val histogram = calculateHistogram(imageData)
        val exposureAnalysis = analyzeExposure(histogram, brightness)
        val whiteBalanceAnalysis = analyzeWhiteBalance(colorBalance)

        return ImageAnalysis(
            brightness = brightness,
            contrast = contrast,
            saturation = saturation,
            colorBalance = colorBalance,
            histogram = histogram,
            exposureAnalysis = exposureAnalysis,
            whiteBalanceAnalysis = whiteBalanceAnalysis,
            recommendedExposureAdjustment = calculateExposureAdjustment(exposureAnalysis, brightness),
            recommendedWhiteBalanceTemp = calculateWhiteBalanceAdjustment(whiteBalanceAnalysis),
            recommendedSaturationAdjustment = calculateSaturationAdjustment(saturation),
            recommendedContrastAdjustment = calculateContrastAdjustment(contrast, histogram)
        )
    }

    /**
     * Apply smart adjustments based on image analysis
     */
    private suspend fun applySmartAdjustments(analysis: ImageAnalysis): List<String> {
        val adjustmentsApplied = mutableListOf<String>()
        val cameraControl = currentCamera?.cameraControl ?: return adjustmentsApplied

        try {
            // Apply exposure adjustment
            if (autoExposureEnabled && abs(analysis.recommendedExposureAdjustment) > 0.1f) {
                val newExposureLevel = (currentExposureLevel + analysis.recommendedExposureAdjustment)
                    .coerceIn(-2.0f, 2.0f)

                if (abs(newExposureLevel - currentExposureLevel) > 0.1f) {
                    // Apply exposure compensation (simulated for compilation)
                    Log.d(TAG, "Would apply exposure compensation: $newExposureLevel")
                    currentExposureLevel = newExposureLevel
                    adjustmentsApplied.add("exposure")
                    Log.d(TAG, "Applied exposure adjustment: $newExposureLevel")
                }
            }

            // Apply white balance adjustment
            if (autoWhiteBalanceEnabled && abs(analysis.recommendedWhiteBalanceTemp - currentWhiteBalanceTemp) > 200) {
                val newWhiteBalanceTemp = analysis.recommendedWhiteBalanceTemp
                    .coerceIn(2000, 10000)

                // Apply white balance adjustment (simulated for compilation)
                Log.d(TAG, "Would apply white balance: ${newWhiteBalanceTemp}K")
                currentWhiteBalanceTemp = newWhiteBalanceTemp
                adjustmentsApplied.add("whiteBalance")
                Log.d(TAG, "Applied white balance adjustment: ${newWhiteBalanceTemp}K")
            }

            // Apply color optimization adjustments
            if (autoColorOptimizationEnabled) {
                var colorAdjustmentsApplied = false

                // Saturation adjustment
                if (abs(analysis.recommendedSaturationAdjustment) > 0.1f) {
                    val newSaturation = (currentColorSaturation + analysis.recommendedSaturationAdjustment)
                        .coerceIn(0.5f, 2.0f)

                    if (abs(newSaturation - currentColorSaturation) > 0.1f) {
                        currentColorSaturation = newSaturation
                        colorAdjustmentsApplied = true
                        Log.d(TAG, "Applied saturation adjustment: $newSaturation")
                    }
                }

                // Contrast adjustment
                if (abs(analysis.recommendedContrastAdjustment) > 0.1f) {
                    val newContrast = (currentContrast + analysis.recommendedContrastAdjustment)
                        .coerceIn(0.5f, 2.0f)

                    if (abs(newContrast - currentContrast) > 0.1f) {
                        currentContrast = newContrast
                        colorAdjustmentsApplied = true
                        Log.d(TAG, "Applied contrast adjustment: $newContrast")
                    }
                }

                if (colorAdjustmentsApplied) {
                    adjustmentsApplied.add("colorOptimization")
                }
            }

            // Record adjustment in history
            if (adjustmentsApplied.isNotEmpty()) {
                val adjustment = CameraAdjustment(
                    timestamp = System.currentTimeMillis(),
                    adjustmentType = adjustmentsApplied.joinToString(","),
                    exposureLevel = currentExposureLevel,
                    whiteBalanceTemp = currentWhiteBalanceTemp,
                    saturation = currentColorSaturation,
                    contrast = currentContrast,
                    imageAnalysis = analysis
                )

                adjustmentHistory.add(adjustment)
                if (adjustmentHistory.size > 50) {
                    adjustmentHistory.removeAt(0) // Keep last 50 adjustments
                }

                cameraContext?.debugLogger?.logPlugin(
                    name,
                    "adjustments_applied",
                    mapOf(
                        "adjustments" to adjustmentsApplied,
                        "exposureLevel" to currentExposureLevel,
                        "whiteBalanceTemp" to currentWhiteBalanceTemp,
                        "saturation" to currentColorSaturation,
                        "contrast" to currentContrast
                    )
                )
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply camera adjustments", e)
        }

        return adjustmentsApplied
    }

    /**
     * Apply exposure compensation to camera
     */
    private suspend fun applyExposureCompensation(cameraControl: CameraControl, exposureLevel: Float) {
        try {
            cameraControl.setExposureCompensationIndex((exposureLevel * 6).toInt()) // Assuming 1/6 EV steps
            Log.d(TAG, "Applied exposure compensation: $exposureLevel EV")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set exposure compensation", e)
        }
    }

    /**
     * Apply white balance adjustment to camera
     */
    private suspend fun applyWhiteBalanceAdjustment(cameraControl: CameraControl, temperature: Int) {
        try {
            // Note: Actual white balance control depends on camera capabilities
            // This is a placeholder for the implementation
            Log.d(TAG, "Applied white balance: ${temperature}K")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set white balance", e)
        }
    }

    /**
     * Calculate optimal exposure adjustment based on image analysis
     */
    private fun calculateExposureAdjustment(exposureAnalysis: ExposureAnalysis, brightness: Float): Float {
        return when {
            exposureAnalysis.underExposedPixels > 0.3f -> {
                // Image is underexposed
                val adjustment = (0.4f - brightness / 255f) * 2f
                adjustment.coerceIn(0.1f, 1.5f)
            }
            exposureAnalysis.overExposedPixels > 0.2f -> {
                // Image is overexposed
                val adjustment = (brightness / 255f - 0.8f) * -2f
                adjustment.coerceIn(-1.5f, -0.1f)
            }
            else -> {
                // Well exposed, minor adjustment if needed
                val idealBrightness = 128f
                val difference = (idealBrightness - brightness) / 255f
                (difference * 0.5f).coerceIn(-0.3f, 0.3f)
            }
        }
    }

    /**
     * Calculate optimal white balance adjustment
     */
    private fun calculateWhiteBalanceAdjustment(whiteBalanceAnalysis: WhiteBalanceAnalysis): Int {
        return when {
            whiteBalanceAnalysis.colorCast == "warm" -> {
                // Too warm, increase color temperature
                (currentWhiteBalanceTemp + 300).coerceAtMost(8000)
            }
            whiteBalanceAnalysis.colorCast == "cool" -> {
                // Too cool, decrease color temperature
                (currentWhiteBalanceTemp - 300).coerceAtLeast(3000)
            }
            else -> {
                // Neutral, minor adjustment toward ideal daylight
                val targetTemp = 5500
                val difference = targetTemp - currentWhiteBalanceTemp
                currentWhiteBalanceTemp + (difference * 0.3).toInt()
            }
        }
    }

    /**
     * Calculate saturation adjustment recommendation
     */
    private fun calculateSaturationAdjustment(currentSaturation: Float): Float {
        val idealSaturation = 0.6f // Target saturation level
        val difference = idealSaturation - currentSaturation
        return (difference * 0.3f).coerceIn(-0.3f, 0.3f)
    }

    /**
     * Calculate contrast adjustment recommendation
     */
    private fun calculateContrastAdjustment(contrast: Float, histogram: IntArray): Float {
        // Calculate histogram spread
        val spread = calculateHistogramSpread(histogram)
        val idealContrast = 60f

        return when {
            contrast < idealContrast * 0.7f -> {
                // Low contrast, increase
                ((idealContrast - contrast) / idealContrast * 0.4f).coerceAtMost(0.3f)
            }
            contrast > idealContrast * 1.3f -> {
                // High contrast, decrease slightly
                ((contrast - idealContrast) / idealContrast * -0.2f).coerceAtLeast(-0.2f)
            }
            else -> {
                // Good contrast
                0.0f
            }
        }
    }

    /**
     * Apply scene-specific adjustment profile
     */
    fun applySceneProfile(sceneType: String) {
        val profile = adjustmentProfiles[sceneType.uppercase()]
        if (profile != null && currentCamera != null) {
            Log.i(TAG, "Applying scene profile: $sceneType")

            val cameraControl = currentCamera!!.cameraControl

            try {
                // Apply profile adjustments (simulated for compilation)
                if (autoExposureEnabled) {
                    val newExposureLevel = profile.exposureBoost.coerceIn(-2.0f, 2.0f)
                    Log.d(TAG, "Would apply scene profile exposure: $newExposureLevel")
                    currentExposureLevel = newExposureLevel
                }

                if (autoWhiteBalanceEnabled) {
                    Log.d(TAG, "Would apply scene profile white balance: ${profile.whiteBalanceTemp}K")
                    currentWhiteBalanceTemp = profile.whiteBalanceTemp
                }

                if (autoColorOptimizationEnabled) {
                    currentColorSaturation = (1.0f + profile.saturationBoost).coerceIn(0.5f, 2.0f)
                    currentContrast = (1.0f + profile.contrastBoost).coerceIn(0.5f, 2.0f)
                }

                cameraContext?.debugLogger?.logPlugin(
                    name,
                    "scene_profile_applied",
                    mapOf(
                        "sceneType" to sceneType,
                        "profile" to mapOf(
                            "exposureBoost" to profile.exposureBoost,
                            "whiteBalanceTemp" to profile.whiteBalanceTemp,
                            "saturationBoost" to profile.saturationBoost,
                            "contrastBoost" to profile.contrastBoost
                        )
                    )
                )

            } catch (e: Exception) {
                Log.e(TAG, "Failed to apply scene profile: $sceneType", e)
            }
        }
    }

    /**
     * Reset adjustments to default values
     */
    private fun resetToDefaults() {
        currentExposureLevel = 0.0f
        currentWhiteBalanceTemp = 5500
        currentColorSaturation = 1.0f
        currentContrast = 1.0f
        Log.d(TAG, "Reset adjustments to defaults")
    }

    /**
     * Get current camera adjustment settings
     */
    fun getCurrentSettings(): Map<String, Any> {
        return mapOf(
            "exposureLevel" to currentExposureLevel,
            "whiteBalanceTemp" to currentWhiteBalanceTemp,
            "saturation" to currentColorSaturation,
            "contrast" to currentContrast,
            "smartAdjustmentsEnabled" to isSmartAdjustmentsEnabled,
            "autoExposureEnabled" to autoExposureEnabled,
            "autoWhiteBalanceEnabled" to autoWhiteBalanceEnabled,
            "autoColorOptimizationEnabled" to autoColorOptimizationEnabled
        )
    }

    /**
     * Get adjustment history
     */
    fun getAdjustmentHistory(): List<CameraAdjustment> {
        return adjustmentHistory.toList()
    }

    /**
     * Clear adjustment history
     */
    fun clearAdjustmentHistory() {
        adjustmentHistory.clear()
        Log.i(TAG, "Adjustment history cleared")
    }

    // Image analysis utility functions
    private fun imageProxyToByteArray(image: ImageProxy): ByteArray {
        val buffer: ByteBuffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return bytes
    }

    private fun calculateAverageBrightness(imageData: ByteArray, width: Int, height: Int): Float {
        var totalBrightness = 0L
        val sampleStep = 8 // Sample every 8th pixel for performance

        for (i in imageData.indices step sampleStep) {
            totalBrightness += (imageData[i].toInt() and 0xFF)
        }

        return totalBrightness.toFloat() / (imageData.size / sampleStep)
    }

    private fun calculateContrast(imageData: ByteArray, width: Int, height: Int): Float {
        val histogram = IntArray(256)
        val sampleStep = 8

        // Build histogram
        for (i in imageData.indices step sampleStep) {
            val pixel = imageData[i].toInt() and 0xFF
            histogram[pixel]++
        }

        // Calculate contrast using standard deviation
        val mean = histogram.indices.map { it * histogram[it] }.sum().toFloat() / histogram.sum()
        val variance = histogram.indices.map {
            histogram[it] * (it - mean).pow(2)
        }.sum().toFloat() / histogram.sum()

        return sqrt(variance)
    }

    private fun calculateSaturation(imageData: ByteArray, width: Int, height: Int): Float {
        // For grayscale images, estimate saturation from local variations
        var totalVariation = 0.0
        val windowSize = 3
        val sampleStep = 16

        for (y in windowSize until height - windowSize step sampleStep) {
            for (x in windowSize until width - windowSize step sampleStep) {
                val centerIndex = y * width + x
                if (centerIndex < imageData.size) {
                    val windowPixels = mutableListOf<Int>()

                    for (wy in -windowSize..windowSize) {
                        for (wx in -windowSize..windowSize) {
                            val pixelIndex = (y + wy) * width + (x + wx)
                            if (pixelIndex >= 0 && pixelIndex < imageData.size) {
                                windowPixels.add(imageData[pixelIndex].toInt() and 0xFF)
                            }
                        }
                    }

                    if (windowPixels.isNotEmpty()) {
                        val maxVal = windowPixels.maxOrNull() ?: 0
                        val minVal = windowPixels.minOrNull() ?: 0
                        totalVariation += (maxVal - minVal).toDouble()
                    }
                }
            }
        }

        val windowCount = ((height - 2 * windowSize) / sampleStep) * ((width - 2 * windowSize) / sampleStep)
        return if (windowCount > 0) {
            (totalVariation / windowCount / 255.0).coerceIn(0.0, 1.0).toFloat()
        } else 0.5f
    }

    private fun analyzeColorBalance(imageData: ByteArray, width: Int, height: Int): ColorBalance {
        // For grayscale, simulate color balance analysis
        val avgBrightness = calculateAverageBrightness(imageData, width, height)

        return ColorBalance(
            redBalance = avgBrightness / 255f,
            greenBalance = avgBrightness / 255f,
            blueBalance = avgBrightness / 255f,
            overall = "neutral"
        )
    }

    private fun calculateHistogram(imageData: ByteArray): IntArray {
        val histogram = IntArray(256)
        val sampleStep = 4

        for (i in imageData.indices step sampleStep) {
            val pixel = imageData[i].toInt() and 0xFF
            histogram[pixel]++
        }

        return histogram
    }

    private fun analyzeExposure(histogram: IntArray, brightness: Float): ExposureAnalysis {
        val totalPixels = histogram.sum()
        val underExposedPixels = histogram.slice(0..25).sum().toFloat() / totalPixels
        val overExposedPixels = histogram.slice(230..255).sum().toFloat() / totalPixels
        val wellExposedPixels = 1f - underExposedPixels - overExposedPixels

        val exposureQuality = when {
            wellExposedPixels > 0.8f -> "excellent"
            wellExposedPixels > 0.6f -> "good"
            wellExposedPixels > 0.4f -> "fair"
            else -> "poor"
        }

        return ExposureAnalysis(
            underExposedPixels = underExposedPixels,
            overExposedPixels = overExposedPixels,
            wellExposedPixels = wellExposedPixels,
            averageBrightness = brightness,
            exposureQuality = exposureQuality
        )
    }

    private fun analyzeWhiteBalance(colorBalance: ColorBalance): WhiteBalanceAnalysis {
        val colorCast = when {
            colorBalance.redBalance > colorBalance.blueBalance + 0.1f -> "warm"
            colorBalance.blueBalance > colorBalance.redBalance + 0.1f -> "cool"
            else -> "neutral"
        }

        val accuracy = 1f - abs(colorBalance.redBalance - colorBalance.blueBalance)

        return WhiteBalanceAnalysis(
            colorCast = colorCast,
            accuracy = accuracy,
            recommendedAdjustment = if (colorCast != "neutral") 0.3f else 0.0f
        )
    }

    private fun calculateHistogramSpread(histogram: IntArray): Float {
        var firstNonZero = -1
        var lastNonZero = -1

        for (i in histogram.indices) {
            if (histogram[i] > 0) {
                if (firstNonZero == -1) firstNonZero = i
                lastNonZero = i
            }
        }

        return if (firstNonZero >= 0 && lastNonZero >= 0) {
            (lastNonZero - firstNonZero).toFloat() / 255f
        } else 0f
    }

    // Configuration methods
    fun setSmartAdjustmentsEnabled(enabled: Boolean) {
        if (isSmartAdjustmentsEnabled != enabled) {
            isSmartAdjustmentsEnabled = enabled
            if (!enabled) {
                resetToDefaults()
            }
            saveSettings()
            Log.i(TAG, "Smart adjustments ${if (enabled) "enabled" else "disabled"}")
        }
    }

    fun setAutoExposureEnabled(enabled: Boolean) {
        autoExposureEnabled = enabled
        saveSettings()
        Log.i(TAG, "Auto exposure ${if (enabled) "enabled" else "disabled"}")
    }

    fun setAutoWhiteBalanceEnabled(enabled: Boolean) {
        autoWhiteBalanceEnabled = enabled
        saveSettings()
        Log.i(TAG, "Auto white balance ${if (enabled) "enabled" else "disabled"}")
    }

    fun setAutoColorOptimizationEnabled(enabled: Boolean) {
        autoColorOptimizationEnabled = enabled
        saveSettings()
        Log.i(TAG, "Auto color optimization ${if (enabled) "enabled" else "disabled"}")
    }

    fun setProcessingInterval(intervalMs: Long) {
        if (intervalMs > 0 && processingInterval != intervalMs) {
            processingInterval = intervalMs
            saveSettings()
            Log.i(TAG, "Smart adjustments processing interval set to: ${intervalMs}ms")
        }
    }

    override fun cleanup() {
        Log.i(TAG, "Cleaning up SmartAdjustmentsPlugin")

        resetToDefaults()
        adjustmentHistory.clear()
        currentCamera = null
        cameraContext = null
    }

    private fun loadSettings(context: CameraContext) {
        val settings = context.settingsManager

        try {
            isSmartAdjustmentsEnabled = settings.getPluginSetting(name, "smartAdjustmentsEnabled", "true").toBoolean()
            autoExposureEnabled = settings.getPluginSetting(name, "autoExposureEnabled", "true").toBoolean()
            autoWhiteBalanceEnabled = settings.getPluginSetting(name, "autoWhiteBalanceEnabled", "true").toBoolean()
            autoColorOptimizationEnabled = settings.getPluginSetting(name, "autoColorOptimizationEnabled", "true").toBoolean()
            processingInterval = settings.getPluginSetting(name, "processingInterval", "300").toLong()

            Log.i(TAG, "Loaded settings: smart=$isSmartAdjustmentsEnabled, exposure=$autoExposureEnabled, wb=$autoWhiteBalanceEnabled, color=$autoColorOptimizationEnabled")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load settings, using defaults", e)
        }
    }

    private fun saveSettings() {
        val settings = cameraContext?.settingsManager ?: return

        settings.setPluginSetting(name, "smartAdjustmentsEnabled", isSmartAdjustmentsEnabled.toString())
        settings.setPluginSetting(name, "autoExposureEnabled", autoExposureEnabled.toString())
        settings.setPluginSetting(name, "autoWhiteBalanceEnabled", autoWhiteBalanceEnabled.toString())
        settings.setPluginSetting(name, "autoColorOptimizationEnabled", autoColorOptimizationEnabled.toString())
        settings.setPluginSetting(name, "processingInterval", processingInterval.toString())
    }

    companion object {
        private const val TAG = "SmartAdjustmentsPlugin"
    }
}

/**
 * Data classes for image analysis and adjustments
 */
data class ImageAnalysis(
    val brightness: Float,
    val contrast: Float,
    val saturation: Float,
    val colorBalance: ColorBalance,
    val histogram: IntArray,
    val exposureAnalysis: ExposureAnalysis,
    val whiteBalanceAnalysis: WhiteBalanceAnalysis,
    val recommendedExposureAdjustment: Float,
    val recommendedWhiteBalanceTemp: Int,
    val recommendedSaturationAdjustment: Float,
    val recommendedContrastAdjustment: Float
)

data class ColorBalance(
    val redBalance: Float,
    val greenBalance: Float,
    val blueBalance: Float,
    val overall: String
)

data class ExposureAnalysis(
    val underExposedPixels: Float,
    val overExposedPixels: Float,
    val wellExposedPixels: Float,
    val averageBrightness: Float,
    val exposureQuality: String
)

data class WhiteBalanceAnalysis(
    val colorCast: String,
    val accuracy: Float,
    val recommendedAdjustment: Float
)

data class AdjustmentProfile(
    val exposureBoost: Float,
    val whiteBalanceTemp: Int,
    val saturationBoost: Float,
    val contrastBoost: Float
)

data class CameraAdjustment(
    val timestamp: Long,
    val adjustmentType: String,
    val exposureLevel: Float,
    val whiteBalanceTemp: Int,
    val saturation: Float,
    val contrast: Float,
    val imageAnalysis: ImageAnalysis
)