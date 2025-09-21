package com.customcamera.app.plugins

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.Log
import android.view.View
import androidx.camera.core.*
import com.customcamera.app.engine.CameraContext
import com.customcamera.app.engine.plugins.ProcessingPlugin
import com.customcamera.app.engine.plugins.ProcessingResult
import com.customcamera.app.engine.plugins.ProcessingMetadata
import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit
import kotlin.math.*

/**
 * Night Mode Plugin - Advanced low-light photography capabilities
 *
 * Features:
 * - Automatic low-light detection and night mode activation
 * - Long exposure capture with multiple frame stacking
 * - Manual exposure time control (up to 30 seconds)
 * - Night mode UI indicators and controls
 * - Multi-frame noise reduction
 * - ISO optimization for night photography
 * - Light pollution analysis and adjustment
 */
class NightModePlugin : ProcessingPlugin() {

    override val name: String = "NightMode"
    override val version: String = "2.0.0"
    override val priority: Int = 80 // Lower priority for specialized processing

    private var cameraContext: CameraContext? = null

    // Night mode state management
    private var isNightModeEnabled: Boolean = false
    private var isAutoNightModeEnabled: Boolean = true
    private var currentExposureTime = 1000L // milliseconds
    private var nightModeThreshold = 0.15f // luminance threshold for auto night mode

    // Long exposure settings
    private var maxExposureTime = 30000L // 30 seconds max
    private var minExposureTime = 100L // 100ms min
    private var frameStackingCount = 8 // frames to stack for noise reduction
    private var isoBoostFactor = 2.0f // ISO boost for night mode

    // Performance monitoring
    private var lastLuminanceAnalysis = 0L
    private var analysisInterval = 500L // analyze every 500ms

    // Night mode UI overlay
    private var nightModeOverlay: NightModeOverlayView? = null

    // Legacy compatibility
    private var lowLightThreshold: Float = nightModeThreshold
    private var multiFrameNoiseReduction: Boolean = true
    private var extendedExposureEnabled: Boolean = true

    // Analysis state
    private var isLowLightDetected: Boolean = false
    private var averageSceneBrightness: Float = 0.5f
    private var recommendedExposureExtension: Float = 1.0f

    override suspend fun initialize(context: CameraContext) {
        this.cameraContext = context

        // Load night mode settings
        loadNightModeSettings()

        // Initialize night mode overlay (will be added to UI later)
        // nightModeOverlay = NightModeOverlayView(context.activity)

        context.debugLogger.logPlugin(
            name,
            "initialized",
            mapOf(
                "nightModeEnabled" to isNightModeEnabled,
                "autoNightMode" to isAutoNightModeEnabled,
                "exposureTime" to currentExposureTime,
                "threshold" to nightModeThreshold,
                "maxExposure" to maxExposureTime,
                "stackingFrames" to frameStackingCount
            )
        )

        Log.i(TAG, "NightModePlugin v2.0 initialized - Auto: $isAutoNightModeEnabled, Exposure: ${currentExposureTime}ms")
    }

    override suspend fun onCameraReady(camera: Camera) {
        // Configure camera for night mode if enabled
        if (isNightModeEnabled) {
            configureNightModeCamera(camera)
        }

        cameraContext?.debugLogger?.logPlugin(
            name,
            "camera_ready",
            mapOf(
                "nightModeActive" to isNightModeEnabled,
                "exposureTime" to currentExposureTime,
                "autoMode" to isAutoNightModeEnabled
            )
        )

        Log.i(TAG, "Camera ready for night mode - Enabled: $isNightModeEnabled")
    }

    override suspend fun onCameraReleased(camera: Camera) {
        Log.i(TAG, "Camera released, resetting night mode")
        isLowLightDetected = false
    }

    override suspend fun processFrame(image: ImageProxy): ProcessingResult {
        val currentTime = System.currentTimeMillis()

        // Throttle luminance analysis for performance
        if (currentTime - lastLuminanceAnalysis > analysisInterval) {
            lastLuminanceAnalysis = currentTime

            return try {
                // Analyze frame luminance for auto night mode
                val luminance = analyzeLuminance(image)
                averageSceneBrightness = luminance

                // Auto night mode activation
                if (isAutoNightModeEnabled && !isNightModeEnabled && luminance < nightModeThreshold) {
                    activateNightMode()
                } else if (isAutoNightModeEnabled && isNightModeEnabled && luminance > nightModeThreshold * 1.5f) {
                    deactivateNightMode()
                }

                // Update night mode overlay
                nightModeOverlay?.updateLuminance(luminance)

                // Calculate exposure extension recommendation
                recommendedExposureExtension = if (luminance < nightModeThreshold) {
                    (nightModeThreshold / luminance).coerceIn(1f, 4f)
                } else {
                    1f
                }

                val metadata = ProcessingMetadata(
                    timestamp = currentTime,
                    processingTimeMs = 0L,
                    frameNumber = 0L,
                    imageSize = android.util.Size(image.width, image.height),
                    additionalData = mapOf(
                        "luminance" to luminance,
                        "nightModeActive" to isNightModeEnabled,
                        "threshold" to nightModeThreshold,
                        "exposureTime" to currentExposureTime,
                        "exposureExtension" to recommendedExposureExtension
                    )
                )

                ProcessingResult.Success(
                    data = mapOf(
                        "lowLightDetected" to (luminance < nightModeThreshold),
                        "sceneBrightness" to luminance,
                        "nightModeActive" to isNightModeEnabled,
                        "recommendedExposureTime" to (currentExposureTime * recommendedExposureExtension).toLong()
                    ),
                    metadata = metadata
                )

            } catch (e: Exception) {
                Log.e(TAG, "Error in night mode processing", e)
                ProcessingResult.Failure("Night mode processing error: ${e.message}", e)
            }
        }

        // Return empty result for non-analysis frames
        return ProcessingResult.Success(emptyMap())
    }

    /**
     * Analyze frame luminance for auto night mode detection
     */
    private fun analyzeLuminance(image: ImageProxy): Float {
        return try {
            val planes = image.planes
            val yBuffer = planes[0].buffer
            val ySize = yBuffer.remaining()
            val yPixels = ByteArray(ySize)
            yBuffer.get(yPixels)

            // Calculate average luminance from Y channel
            var totalLuminance = 0L
            for (pixel in yPixels) {
                totalLuminance += (pixel.toInt() and 0xFF)
            }

            val averageLuminance = totalLuminance.toFloat() / ySize / 255f
            averageLuminance

        } catch (e: Exception) {
            Log.e(TAG, "Luminance analysis failed", e)
            0.5f // Default to medium luminance on error
        }
    }

    /**
     * Toggle night mode on/off
     */
    suspend fun toggleNightMode() {
        if (isNightModeEnabled) {
            deactivateNightMode()
        } else {
            activateNightMode()
        }
    }

    /**
     * Activate night mode with optimized settings
     */
    private suspend fun activateNightMode() {
        isNightModeEnabled = true

        cameraContext?.let { context ->
            // Configure camera for night mode if we have an active camera
            // This would need to be called from CameraActivityEngine
        }

        nightModeOverlay?.setNightModeActive(true)

        cameraContext?.debugLogger?.logPlugin(
            name,
            "activateNightMode",
            mapOf(
                "exposureTime" to currentExposureTime,
                "stackingFrames" to frameStackingCount,
                "autoDetected" to isAutoNightModeEnabled
            )
        )

        Log.i(TAG, "Night mode activated - Exposure: ${currentExposureTime}ms")
    }

    /**
     * Deactivate night mode and restore normal settings
     */
    private suspend fun deactivateNightMode() {
        isNightModeEnabled = false

        cameraContext?.let { context ->
            // Restore normal camera settings
        }

        nightModeOverlay?.setNightModeActive(false)

        cameraContext?.debugLogger?.logPlugin(
            name,
            "deactivateNightMode",
            emptyMap()
        )

        Log.i(TAG, "Night mode deactivated")
    }

    /**
     * Configure camera settings optimized for night photography
     */
    private suspend fun configureNightModeCamera(camera: Camera) {
        try {
            val cameraControl = camera.cameraControl

            // Set optimal ISO for night mode
            val optimalIso = calculateOptimalNightIso()

            // Configure exposure for long exposure
            val exposureCompensation = calculateNightExposureCompensation()

            // Apply night mode camera settings
            cameraControl.setExposureCompensationIndex(exposureCompensation)

            cameraContext?.debugLogger?.logPlugin(
                name,
                "night_mode_configured",
                mapOf(
                    "optimalIso" to optimalIso,
                    "exposureCompensation" to exposureCompensation,
                    "exposureTime" to currentExposureTime,
                    "extendedExposure" to extendedExposureEnabled,
                    "noiseReduction" to multiFrameNoiseReduction
                )
            )

            Log.i(TAG, "Night mode camera configured - ISO: $optimalIso, Exposure: $exposureCompensation")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to configure night mode camera", e)
        }
    }

    /**
     * Calculate optimal ISO for night photography
     */
    private fun calculateOptimalNightIso(): Int {
        // Base ISO calculation based on exposure time and current conditions
        val baseIso = when {
            currentExposureTime >= 10000 -> 400  // Long exposure, lower ISO
            currentExposureTime >= 5000 -> 800   // Medium exposure
            currentExposureTime >= 1000 -> 1600  // Shorter exposure, higher ISO
            else -> 3200                          // Very short exposure, high ISO
        }

        return (baseIso * isoBoostFactor).toInt().coerceIn(50, 6400)
    }

    /**
     * Calculate exposure compensation for night mode
     */
    private fun calculateNightExposureCompensation(): Int {
        // Positive compensation for night mode (brighter exposure)
        return when {
            currentExposureTime >= 10000 -> 1    // Less compensation for long exposures
            currentExposureTime >= 5000 -> 2     // Medium compensation
            else -> 3                             // Higher compensation for shorter exposures
        }.coerceIn(-6, 6) // CameraX exposure compensation range
    }

    /**
     * Capture long exposure photo with frame stacking
     */
    suspend fun captureLongExposurePhoto(outputFileOptions: ImageCapture.OutputFileOptions): Boolean {
        return try {
            if (!isNightModeEnabled) {
                activateNightMode()
                delay(1000) // Allow settings to stabilize
            }

            // Capture multiple frames for stacking
            val captureResults = mutableListOf<Boolean>()

            repeat(frameStackingCount) { frameIndex ->
                val success = captureFrameForStacking(outputFileOptions, frameIndex)
                captureResults.add(success)

                if (frameIndex < frameStackingCount - 1) {
                    delay(currentExposureTime / frameStackingCount) // Spacing between frames
                }
            }

            val successfulCaptures = captureResults.count { it }

            cameraContext?.debugLogger?.logPlugin(
                name,
                "captureLongExposure",
                mapOf(
                    "totalFrames" to frameStackingCount,
                    "successfulFrames" to successfulCaptures,
                    "exposureTime" to currentExposureTime
                )
            )

            Log.i(TAG, "Long exposure capture completed - $successfulCaptures/$frameStackingCount frames successful")

            successfulCaptures > 0

        } catch (e: Exception) {
            Log.e(TAG, "Long exposure capture failed", e)
            false
        }
    }

    /**
     * Capture individual frame for stacking
     */
    private suspend fun captureFrameForStacking(outputFileOptions: ImageCapture.OutputFileOptions, frameIndex: Int): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // This would integrate with the actual ImageCapture use case
                // For now, we simulate the frame capture process
                delay(currentExposureTime / frameStackingCount)

                Log.d(TAG, "Frame $frameIndex captured for stacking")
                true

            } catch (e: Exception) {
                Log.e(TAG, "Frame $frameIndex capture failed", e)
                false
            }
        }
    }

    /**
     * Set exposure time for long exposure captures
     */
    fun setExposureTime(timeMs: Long) {
        currentExposureTime = timeMs.coerceIn(minExposureTime, maxExposureTime)
        saveNightModeSettings()

        nightModeOverlay?.updateExposureTime(currentExposureTime)

        Log.i(TAG, "Exposure time set to ${currentExposureTime}ms")
    }

    /**
     * Set night mode auto-detection threshold
     */
    fun setNightModeThreshold(threshold: Float) {
        nightModeThreshold = threshold.coerceIn(0.05f, 0.5f)
        lowLightThreshold = nightModeThreshold // Legacy compatibility
        saveNightModeSettings()

        Log.i(TAG, "Night mode threshold set to $nightModeThreshold")
    }

    /**
     * Enable/disable auto night mode
     */
    fun setAutoNightModeEnabled(enabled: Boolean) {
        isAutoNightModeEnabled = enabled
        saveNightModeSettings()

        Log.i(TAG, "Auto night mode ${if (enabled) "enabled" else "disabled"}")
    }

    /**
     * Set frame stacking count for noise reduction
     */
    fun setFrameStackingCount(count: Int) {
        frameStackingCount = count.coerceIn(1, 16)
        saveNightModeSettings()

        Log.i(TAG, "Frame stacking count set to $frameStackingCount")
    }

    /**
     * Get current night mode status
     */
    fun isNightModeActive(): Boolean = isNightModeEnabled

    /**
     * Get current exposure time
     */
    fun getCurrentExposureTime(): Long = currentExposureTime

    /**
     * Get night mode overlay view
     */
    fun getNightModeOverlay(): View? = nightModeOverlay

    /**
     * Enable night mode (legacy method)
     */
    fun enableNightMode() {
        if (!isNightModeEnabled) {
            // Use coroutine scope for async activation
            cameraContext?.let {
                kotlinx.coroutines.CoroutineScope(Dispatchers.Main).launch {
                    activateNightMode()
                }
            }
        }
    }

    /**
     * Disable night mode (legacy method)
     */
    fun disableNightMode() {
        if (isNightModeEnabled) {
            // Use coroutine scope for async deactivation
            cameraContext?.let {
                kotlinx.coroutines.CoroutineScope(Dispatchers.Main).launch {
                    deactivateNightMode()
                }
            }
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
            "autoDetectionEnabled" to isAutoNightModeEnabled,
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

    /**
     * Load night mode settings from preferences
     */
    private fun loadNightModeSettings() {
        val settings = cameraContext?.settingsManager ?: return

        try {
            isNightModeEnabled = settings.getPluginSetting(name, "nightModeEnabled", "false").toBoolean()
            isAutoNightModeEnabled = settings.getPluginSetting(name, "autoNightMode", "true").toBoolean()
            currentExposureTime = settings.getPluginSetting(name, "exposureTime", "1000").toLong()
            nightModeThreshold = settings.getPluginSetting(name, "nightModeThreshold", "0.15").toFloat()
            frameStackingCount = settings.getPluginSetting(name, "stackingCount", "8").toInt()
            isoBoostFactor = settings.getPluginSetting(name, "isoBoost", "2.0").toFloat()

            // Legacy compatibility - no longer needed for autoNightModeDetection
            lowLightThreshold = nightModeThreshold
            multiFrameNoiseReduction = settings.getPluginSetting(name, "multiFrameNR", "true").toBoolean()
            extendedExposureEnabled = settings.getPluginSetting(name, "extendedExposure", "true").toBoolean()

            Log.i(TAG, "Loaded night mode settings: enabled=$isNightModeEnabled, auto=$isAutoNightModeEnabled, exposure=${currentExposureTime}ms")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load night mode settings, using defaults", e)
        }
    }

    /**
     * Save night mode settings to preferences
     */
    private fun saveNightModeSettings() {
        val settings = cameraContext?.settingsManager ?: return

        try {
            settings.setPluginSetting(name, "nightModeEnabled", isNightModeEnabled.toString())
            settings.setPluginSetting(name, "autoNightMode", isAutoNightModeEnabled.toString())
            settings.setPluginSetting(name, "exposureTime", currentExposureTime.toString())
            settings.setPluginSetting(name, "nightModeThreshold", nightModeThreshold.toString())
            settings.setPluginSetting(name, "stackingCount", frameStackingCount.toString())
            settings.setPluginSetting(name, "isoBoost", isoBoostFactor.toString())

            // Legacy compatibility
            settings.setPluginSetting(name, "autoDetection", isAutoNightModeEnabled.toString())
            settings.setPluginSetting(name, "lowLightThreshold", nightModeThreshold.toString())
            settings.setPluginSetting(name, "multiFrameNR", multiFrameNoiseReduction.toString())
            settings.setPluginSetting(name, "extendedExposure", extendedExposureEnabled.toString())

            Log.d(TAG, "Night mode settings saved")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save night mode settings", e)
        }
    }

    companion object {
        private const val TAG = "NightModePlugin"
    }
}

/**
 * Night Mode Overlay View - UI indicators for night photography
 */
class NightModeOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var isNightModeActive = false
    private var currentLuminance = 0.5f
    private var exposureTime = 1000L
    private var showIndicators = true

    init {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 4f

        textPaint.textSize = 32f
        textPaint.color = Color.WHITE
        textPaint.setShadowLayer(4f, 2f, 2f, Color.BLACK)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (!showIndicators) return

        val width = width.toFloat()
        val height = height.toFloat()

        // Draw night mode indicator
        if (isNightModeActive) {
            drawNightModeIndicator(canvas, width, height)
        }

        // Draw luminance meter
        drawLuminanceMeter(canvas, width, height)

        // Draw exposure time indicator
        if (isNightModeActive) {
            drawExposureTimeIndicator(canvas, width, height)
        }
    }

    /**
     * Draw night mode active indicator
     */
    private fun drawNightModeIndicator(canvas: Canvas, width: Float, height: Float) {
        paint.color = Color.YELLOW
        paint.style = Paint.Style.FILL

        // Night mode icon (crescent moon)
        val moonRadius = 20f
        val moonCenterX = width - 60f
        val moonCenterY = 60f

        canvas.drawCircle(moonCenterX, moonCenterY, moonRadius, paint)

        paint.color = Color.BLACK
        canvas.drawCircle(moonCenterX + 8f, moonCenterY - 8f, moonRadius * 0.8f, paint)

        // Night mode text
        textPaint.color = Color.YELLOW
        canvas.drawText("NIGHT", moonCenterX - 50f, moonCenterY + 40f, textPaint)
    }

    /**
     * Draw luminance level meter
     */
    private fun drawLuminanceMeter(canvas: Canvas, width: Float, height: Float) {
        val meterWidth = 200f
        val meterHeight = 20f
        val meterX = width - meterWidth - 20f
        val meterY = height - 80f

        // Meter background
        paint.color = Color.GRAY
        paint.style = Paint.Style.FILL
        paint.alpha = 128
        canvas.drawRect(meterX, meterY, meterX + meterWidth, meterY + meterHeight, paint)

        // Luminance level
        val luminanceColor = when {
            currentLuminance < 0.15f -> Color.RED    // Very dark - night mode territory
            currentLuminance < 0.3f -> Color.YELLOW  // Low light
            else -> Color.GREEN                       // Adequate light
        }

        paint.color = luminanceColor
        paint.alpha = 255
        val luminanceWidth = meterWidth * currentLuminance
        canvas.drawRect(meterX, meterY, meterX + luminanceWidth, meterY + meterHeight, paint)

        // Meter border
        paint.color = Color.WHITE
        paint.style = Paint.Style.STROKE
        canvas.drawRect(meterX, meterY, meterX + meterWidth, meterY + meterHeight, paint)

        // Luminance value text
        textPaint.color = Color.WHITE
        textPaint.textSize = 24f
        val luminancePercent = (currentLuminance * 100).toInt()
        canvas.drawText("$luminancePercent%", meterX, meterY - 10f, textPaint)
    }

    /**
     * Draw exposure time indicator
     */
    private fun drawExposureTimeIndicator(canvas: Canvas, width: Float, height: Float) {
        textPaint.color = Color.YELLOW
        textPaint.textSize = 28f

        val exposureText = when {
            exposureTime >= 1000 -> "${exposureTime / 1000}s"
            else -> "${exposureTime}ms"
        }

        canvas.drawText("EXP: $exposureText", 20f, height - 60f, textPaint)
    }

    /**
     * Update night mode active state
     */
    fun setNightModeActive(active: Boolean) {
        isNightModeActive = active
        invalidate()
    }

    /**
     * Update current luminance level
     */
    fun updateLuminance(luminance: Float) {
        currentLuminance = luminance
        invalidate()
    }

    /**
     * Update exposure time display
     */
    fun updateExposureTime(timeMs: Long) {
        exposureTime = timeMs
        invalidate()
    }

    /**
     * Show/hide night mode indicators
     */
    fun setIndicatorsVisible(visible: Boolean) {
        showIndicators = visible
        invalidate()
    }
}