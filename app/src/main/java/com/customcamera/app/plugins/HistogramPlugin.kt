package com.customcamera.app.plugins

import android.graphics.Color
import android.util.Log
import android.view.View
import androidx.camera.core.Camera
import androidx.camera.core.ImageProxy
import com.customcamera.app.engine.CameraContext
import com.customcamera.app.engine.plugins.ProcessingPlugin
import com.customcamera.app.engine.plugins.ProcessingResult
import com.customcamera.app.engine.plugins.ProcessingMetadata
import com.customcamera.app.analysis.HistogramView
import java.nio.ByteBuffer

/**
 * HistogramPlugin provides real-time histogram analysis
 * with RGB and luminance histogram display.
 */
class HistogramPlugin : ProcessingPlugin() {

    override val name: String = "Histogram"
    override val version: String = "1.0.0"
    override val priority: Int = 70 // Lower priority for analysis

    private var cameraContext: CameraContext? = null
    private var histogramView: HistogramView? = null

    // Histogram configuration
    private var isHistogramEnabled: Boolean = false
    private var showRGBHistogram: Boolean = true
    private var showLuminanceHistogram: Boolean = true
    private var processingInterval: Long = 200L // Process every 200ms
    private var lastProcessingTime: Long = 0L

    // Histogram data
    private var currentHistogram: Histogram? = null
    private var exposureWarnings: ExposureWarnings? = null

    data class Histogram(
        val red: IntArray,
        val green: IntArray,
        val blue: IntArray,
        val luminance: IntArray,
        val totalPixels: Int,
        val averageBrightness: Float,
        val timestamp: Long = System.currentTimeMillis()
    ) {
        companion object {
            const val HISTOGRAM_BINS = 256
        }
    }

    data class ExposureWarnings(
        val overExposed: Boolean,
        val underExposed: Boolean,
        val overExposedPercentage: Float,
        val underExposedPercentage: Float,
        val dynamicRange: Float,
        val optimalExposure: Boolean
    )

    override suspend fun initialize(context: CameraContext) {
        this.cameraContext = context
        Log.i(TAG, "HistogramPlugin initialized")

        loadSettings(context)

        context.debugLogger.logPlugin(
            name,
            "initialized",
            mapOf(
                "histogramEnabled" to isHistogramEnabled,
                "showRGB" to showRGBHistogram,
                "showLuminance" to showLuminanceHistogram,
                "processingInterval" to processingInterval
            )
        )
    }

    override suspend fun onCameraReady(camera: Camera) {
        Log.i(TAG, "Camera ready for histogram analysis")

        if (isHistogramEnabled) {
            createHistogramView()
        }

        cameraContext?.debugLogger?.logPlugin(
            name,
            "camera_ready",
            mapOf("histogramViewCreated" to (histogramView != null))
        )
    }

    override suspend fun onCameraReleased(camera: Camera) {
        Log.i(TAG, "Camera released, stopping histogram analysis")
        clearHistogram()
    }

    override suspend fun processFrame(image: ImageProxy): ProcessingResult {
        val currentTime = System.currentTimeMillis()

        // Throttle processing to avoid performance impact
        if (currentTime - lastProcessingTime < processingInterval) {
            return ProcessingResult.Skip
        }

        lastProcessingTime = currentTime

        return try {
            // Calculate histogram from image
            val histogram = calculateHistogram(image)
            currentHistogram = histogram

            // Analyze exposure
            val warnings = analyzeExposure(histogram)
            exposureWarnings = warnings

            // Update histogram display
            updateHistogramDisplay(histogram)

            // Log exposure warnings if any
            if (warnings.overExposed || warnings.underExposed) {
                Log.w(TAG, "Exposure warning: over=${warnings.overExposedPercentage}%, under=${warnings.underExposedPercentage}%")
            }

            val metadata = ProcessingMetadata(
                timestamp = currentTime,
                processingTimeMs = System.currentTimeMillis() - currentTime,
                frameNumber = 0L,
                imageSize = android.util.Size(image.width, image.height),
                additionalData = mapOf(
                    "averageBrightness" to histogram.averageBrightness,
                    "dynamicRange" to warnings.dynamicRange,
                    "overExposed" to warnings.overExposed,
                    "underExposed" to warnings.underExposed
                )
            )

            ProcessingResult.Success(
                data = mapOf(
                    "histogram" to histogram,
                    "exposureWarnings" to warnings
                ),
                metadata = metadata
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error processing frame for histogram", e)
            ProcessingResult.Failure("Histogram processing error: ${e.message}", e)
        }
    }

    /**
     * Calculate histogram from image
     */
    private fun calculateHistogram(image: ImageProxy): Histogram {
        val redHistogram = IntArray(Histogram.HISTOGRAM_BINS)
        val greenHistogram = IntArray(Histogram.HISTOGRAM_BINS)
        val blueHistogram = IntArray(Histogram.HISTOGRAM_BINS)
        val luminanceHistogram = IntArray(Histogram.HISTOGRAM_BINS)

        var totalBrightness = 0L
        var totalPixels = 0

        try {
            // Get Y plane (luminance)
            val yPlane = image.planes[0]
            val yBuffer = yPlane.buffer
            val ySize = yBuffer.remaining()

            // Process luminance data
            for (i in 0 until ySize) {
                val luminance = yBuffer.get(i).toInt() and 0xFF
                luminanceHistogram[luminance]++
                totalBrightness += luminance
                totalPixels++

                // Simulate RGB from luminance for demo
                // In production, you'd extract actual RGB from YUV data
                redHistogram[luminance]++
                greenHistogram[luminance]++
                blueHistogram[luminance]++
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error calculating histogram", e)
            // Return empty histogram on error
        }

        val averageBrightness = if (totalPixels > 0) {
            totalBrightness.toFloat() / totalPixels
        } else {
            0f
        }

        return Histogram(
            red = redHistogram,
            green = greenHistogram,
            blue = blueHistogram,
            luminance = luminanceHistogram,
            totalPixels = totalPixels,
            averageBrightness = averageBrightness
        )
    }

    /**
     * Analyze exposure from histogram
     */
    private fun analyzeExposure(histogram: Histogram): ExposureWarnings {
        val luminance = histogram.luminance
        val totalPixels = histogram.totalPixels

        // Calculate over/under exposure percentages
        val underExposedPixels = luminance.sliceArray(0..15).sum() // Very dark pixels
        val overExposedPixels = luminance.sliceArray(240..255).sum() // Very bright pixels

        val underExposedPercentage = (underExposedPixels.toFloat() / totalPixels) * 100f
        val overExposedPercentage = (overExposedPixels.toFloat() / totalPixels) * 100f

        // Calculate dynamic range (simplified)
        val nonZeroBins = luminance.count { it > 0 }
        val dynamicRange = (nonZeroBins.toFloat() / Histogram.HISTOGRAM_BINS) * 100f

        val overExposed = overExposedPercentage > 5f // More than 5% overexposed
        val underExposed = underExposedPercentage > 10f // More than 10% underexposed
        val optimalExposure = !overExposed && !underExposed && histogram.averageBrightness in 80f..180f

        return ExposureWarnings(
            overExposed = overExposed,
            underExposed = underExposed,
            overExposedPercentage = overExposedPercentage,
            underExposedPercentage = underExposedPercentage,
            dynamicRange = dynamicRange,
            optimalExposure = optimalExposure
        )
    }

    /**
     * Enable or disable histogram display
     */
    fun setHistogramEnabled(enabled: Boolean) {
        if (isHistogramEnabled != enabled) {
            isHistogramEnabled = enabled

            if (enabled) {
                createHistogramView()
            } else {
                hideHistogramView()
            }

            saveSettings()
            Log.i(TAG, "Histogram ${if (enabled) "enabled" else "disabled"}")
        }
    }

    /**
     * Set RGB histogram display
     */
    fun setRGBHistogramEnabled(enabled: Boolean) {
        if (showRGBHistogram != enabled) {
            showRGBHistogram = enabled
            histogramView?.setShowRGB(enabled)
            saveSettings()
            Log.i(TAG, "RGB histogram ${if (enabled) "enabled" else "disabled"}")
        }
    }

    /**
     * Set luminance histogram display
     */
    fun setLuminanceHistogramEnabled(enabled: Boolean) {
        if (showLuminanceHistogram != enabled) {
            showLuminanceHistogram = enabled
            histogramView?.setShowLuminance(enabled)
            saveSettings()
            Log.i(TAG, "Luminance histogram ${if (enabled) "enabled" else "disabled"}")
        }
    }

    /**
     * Get current histogram data
     */
    fun getCurrentHistogram(): Histogram? = currentHistogram

    /**
     * Get current exposure warnings
     */
    fun getCurrentExposureWarnings(): ExposureWarnings? = exposureWarnings

    override fun cleanup() {
        Log.i(TAG, "Cleaning up HistogramPlugin")

        hideHistogramView()
        histogramView = null
        currentHistogram = null
        exposureWarnings = null
        cameraContext = null
    }

    private fun createHistogramView() {
        if (cameraContext != null && histogramView == null) {
            histogramView = HistogramView(cameraContext!!.context).apply {
                setShowRGB(showRGBHistogram)
                setShowLuminance(showLuminanceHistogram)
                visibility = View.VISIBLE
            }
            Log.d(TAG, "Histogram view created")
        }
    }

    private fun hideHistogramView() {
        histogramView?.visibility = View.GONE
    }

    private fun updateHistogramDisplay(histogram: Histogram) {
        histogramView?.updateHistogram(histogram)
    }

    private fun clearHistogram() {
        currentHistogram = null
        exposureWarnings = null
        histogramView?.clearHistogram()
    }

    private fun loadSettings(context: CameraContext) {
        val settings = context.settingsManager

        try {
            isHistogramEnabled = settings.getPluginSetting(name, "histogramEnabled", "false").toBoolean()
            showRGBHistogram = settings.getPluginSetting(name, "showRGB", "true").toBoolean()
            showLuminanceHistogram = settings.getPluginSetting(name, "showLuminance", "true").toBoolean()
            processingInterval = settings.getPluginSetting(name, "processingInterval", "200").toLong()

            Log.i(TAG, "Loaded settings: enabled=$isHistogramEnabled, RGB=$showRGBHistogram, luminance=$showLuminanceHistogram")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load settings, using defaults", e)
        }
    }

    private fun saveSettings() {
        val settings = cameraContext?.settingsManager ?: return

        settings.setPluginSetting(name, "histogramEnabled", isHistogramEnabled.toString())
        settings.setPluginSetting(name, "showRGB", showRGBHistogram.toString())
        settings.setPluginSetting(name, "showLuminance", showLuminanceHistogram.toString())
        settings.setPluginSetting(name, "processingInterval", processingInterval.toString())
    }

    companion object {
        private const val TAG = "HistogramPlugin"
    }
}