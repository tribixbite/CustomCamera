package com.customcamera.app.plugins

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.TotalCaptureResult
import android.util.Log
import android.util.Range
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import java.io.File
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Exposure bracketing and HDR control plugin
 * Provides automatic exposure bracketing for HDR photography
 */
class ExposureBracketingPlugin : com.customcamera.app.engine.plugins.ControlPlugin() {
    override val name: String = "ExposureBracketing"

    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    private var isEnabled = false

    // Bracketing settings
    private var bracketingMode = BracketingMode.THREE_SHOT
    private var exposureSteps: Float = 1.0f // EV steps between shots
    private var maxExposureCompensation: Float = 2.0f
    private var exposureCompensationRange: Range<Int> = Range(-24, 24) // In 1/12 EV steps
    private var exposureCompensationStep: Float = 0.083f // 1/12 EV

    // HDR settings
    private var hdrMode = HDRMode.AUTO
    private var hdrStrength: Float = 1.0f
    private var tonemappingMode = TonemappingMode.NATURAL
    private var ghostingReduction = true

    // Capture state
    private var isBracketingInProgress = false
    private var currentBracketSequence = mutableListOf<BracketFrame>()
    private var bracketingCallback: BracketingCallback? = null

    // Performance tracking
    private var lastBracketingTime = 0L
    private var bracketingCount = 0
    private var performanceMetrics = mutableMapOf<String, Any>()

    // Bracketing presets
    private val bracketingPresets = mapOf(
        "standard" to BracketingPreset(BracketingMode.THREE_SHOT, 1.0f, "Standard 3-shot HDR"),
        "wide_range" to BracketingPreset(BracketingMode.FIVE_SHOT, 1.5f, "Wide range 5-shot HDR"),
        "subtle" to BracketingPreset(BracketingMode.THREE_SHOT, 0.7f, "Subtle 3-shot HDR"),
        "extreme" to BracketingPreset(BracketingMode.SEVEN_SHOT, 2.0f, "Extreme 7-shot HDR"),
        "fast" to BracketingPreset(BracketingMode.TWO_SHOT, 1.0f, "Fast 2-shot HDR")
    )

    override fun initialize(cameraManager: CameraManager?, cameraId: String?) {
        super.initialize(cameraManager, cameraId)

        coroutineScope.launch {
            detectExposureCapabilities()
            loadSettings()

            Log.i(TAG, "ExposureBracketingPlugin initialized")
            Log.i(TAG, "Exposure compensation range: ${exposureCompensationRange.lower/12f}EV to ${exposureCompensationRange.upper/12f}EV")
        }
    }

    /**
     * Detect camera's exposure capabilities
     */
    private suspend fun detectExposureCapabilities() = withContext(Dispatchers.IO) {
        try {
            cameraManager?.let { manager ->
                cameraId?.let { id ->
                    val characteristics = manager.getCameraCharacteristics(id)

                    // Get exposure compensation range
                    val expCompRange = characteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)
                    expCompRange?.let { range ->
                        exposureCompensationRange = range
                        maxExposureCompensation = range.upper / 12f
                    }

                    // Get exposure compensation step
                    val expCompStep = characteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP)
                    expCompStep?.let { step ->
                        exposureCompensationStep = step.toFloat()
                    }

                    // Check available scene modes for HDR
                    val sceneModes = characteristics.get(CameraCharacteristics.CONTROL_AVAILABLE_SCENE_MODES)
                    sceneModes?.let { modes ->
                        val hasHDRScene = modes.contains(CameraMetadata.CONTROL_SCENE_MODE_HDR)
                        Log.i(TAG, "HDR scene mode supported: $hasHDRScene")
                    }

                    Log.i(TAG, "Exposure compensation: ±${maxExposureCompensation}EV, step: ${exposureCompensationStep}EV")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting exposure capabilities", e)
            // Use default values for compatibility
            exposureCompensationRange = Range(-12, 12)
            maxExposureCompensation = 1.0f
            exposureCompensationStep = 0.083f
        }
    }

    /**
     * Start exposure bracketing sequence
     */
    suspend fun startBracketing(
        outputDir: File,
        mode: BracketingMode = bracketingMode,
        steps: Float = exposureSteps,
        callback: BracketingCallback? = null
    ): Boolean {
        if (isBracketingInProgress) {
            Log.w(TAG, "Bracketing already in progress")
            return false
        }

        return withContext(Dispatchers.IO) {
            try {
                isBracketingInProgress = true
                currentBracketSequence.clear()
                bracketingCallback = callback
                lastBracketingTime = System.currentTimeMillis()

                val exposureValues = generateExposureSequence(mode, steps)
                Log.i(TAG, "Starting ${mode.shots}-shot bracketing with EV: ${exposureValues.joinToString()}")

                callback?.onBracketingStarted(mode, exposureValues)

                for ((index, ev) in exposureValues.withIndex()) {
                    val filename = "bracket_${System.currentTimeMillis()}_${index}_${ev.toString().replace(".", "_")}.jpg"
                    val outputFile = File(outputDir, filename)

                    val success = captureWithExposureCompensation(ev, outputFile)
                    val bracketFrame = BracketFrame(
                        index = index,
                        exposureValue = ev,
                        filename = filename,
                        file = outputFile,
                        success = success,
                        timestamp = System.currentTimeMillis()
                    )

                    currentBracketSequence.add(bracketFrame)
                    callback?.onFrameCaptured(bracketFrame)

                    if (!success) {
                        Log.e(TAG, "Failed to capture bracket frame at ${ev}EV")
                    }

                    // Small delay between shots for stability
                    if (index < exposureValues.size - 1) {
                        delay(200)
                    }
                }

                bracketingCount++
                updatePerformanceMetrics()
                callback?.onBracketingCompleted(currentBracketSequence.toList())

                Log.i(TAG, "Bracketing sequence completed: ${currentBracketSequence.size} frames")
                saveSettings()

                true
            } catch (e: Exception) {
                Log.e(TAG, "Error during bracketing sequence", e)
                callback?.onBracketingFailed(e)
                false
            } finally {
                isBracketingInProgress = false
            }
        }
    }

    /**
     * Generate exposure values for bracketing sequence
     */
    private fun generateExposureSequence(mode: BracketingMode, steps: Float): List<Float> {
        val clampedSteps = steps.coerceIn(0.3f, maxExposureCompensation)

        return when (mode) {
            BracketingMode.TWO_SHOT -> listOf(-clampedSteps, clampedSteps)
            BracketingMode.THREE_SHOT -> listOf(-clampedSteps, 0f, clampedSteps)
            BracketingMode.FIVE_SHOT -> listOf(-clampedSteps * 2, -clampedSteps, 0f, clampedSteps, clampedSteps * 2)
            BracketingMode.SEVEN_SHOT -> listOf(
                -clampedSteps * 3, -clampedSteps * 2, -clampedSteps, 0f,
                clampedSteps, clampedSteps * 2, clampedSteps * 3
            )
        }.map { it.coerceIn(-maxExposureCompensation, maxExposureCompensation) }
    }

    /**
     * Capture single frame with exposure compensation
     */
    private suspend fun captureWithExposureCompensation(ev: Float, outputFile: File): Boolean = withContext(Dispatchers.IO) {
        try {
            // Apply exposure compensation
            captureSession?.let { session ->
                val requestBuilder = session.device.createCaptureRequest(android.hardware.camera2.CameraDevice.TEMPLATE_STILL_CAPTURE)

                // Convert EV to camera2 units (1/12 EV steps)
                val exposureCompensation = (ev / exposureCompensationStep).roundToInt()
                    .coerceIn(exposureCompensationRange.lower, exposureCompensationRange.upper)

                requestBuilder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, exposureCompensation)
                requestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON)

                // Apply the exposure setting temporarily
                session.capture(requestBuilder.build(), object : CameraCaptureSession.CaptureCallback() {
                    override fun onCaptureCompleted(
                        session: CameraCaptureSession,
                        request: CaptureRequest,
                        result: TotalCaptureResult
                    ) {
                        Log.d(TAG, "Exposure compensation applied: ${ev}EV")
                    }
                }, null)

                // Small delay for AE to settle
                delay(300)
            }

            // Capture using ImageCapture (simulated for now)
            // In real implementation, this would use the actual ImageCapture instance
            Log.i(TAG, "Capturing frame with ${ev}EV compensation to ${outputFile.name}")

            // Simulate successful capture
            true

        } catch (e: Exception) {
            Log.e(TAG, "Error capturing with exposure compensation ${ev}EV", e)
            false
        }
    }

    /**
     * Process bracketed images into HDR
     */
    suspend fun processHDR(
        bracketedImages: List<BracketFrame>,
        outputFile: File,
        hdrSettings: HDRSettings = HDRSettings()
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Processing ${bracketedImages.size} images into HDR")

            // HDR processing would happen here
            // For now, this is a placeholder for the actual HDR algorithm
            val validFrames = bracketedImages.filter { it.success && it.file.exists() }

            if (validFrames.size < 2) {
                Log.e(TAG, "Insufficient valid frames for HDR processing")
                return@withContext false
            }

            // Simulate HDR processing
            delay(2000) // Simulate processing time

            Log.i(TAG, "HDR processing completed: ${outputFile.name}")
            true

        } catch (e: Exception) {
            Log.e(TAG, "Error processing HDR", e)
            false
        }
    }

    /**
     * Apply bracketing preset
     */
    fun applyPreset(presetName: String): Boolean {
        val preset = bracketingPresets[presetName]
        return if (preset != null) {
            bracketingMode = preset.mode
            exposureSteps = preset.steps
            saveSettings()
            Log.i(TAG, "Applied bracketing preset: $presetName")
            true
        } else {
            Log.w(TAG, "Unknown bracketing preset: $presetName")
            false
        }
    }

    /**
     * Set HDR mode
     */
    fun setHDRMode(mode: HDRMode): Boolean {
        return try {
            hdrMode = mode

            captureSession?.let { session ->
                val requestBuilder = session.device.createCaptureRequest(
                    if (isRecording) android.hardware.camera2.CameraDevice.TEMPLATE_RECORD
                    else android.hardware.camera2.CameraDevice.TEMPLATE_PREVIEW
                )

                when (mode) {
                    HDRMode.OFF -> {
                        // Disable HDR processing
                    }
                    HDRMode.AUTO -> {
                        requestBuilder.set(CaptureRequest.CONTROL_SCENE_MODE, CameraMetadata.CONTROL_SCENE_MODE_HDR)
                    }
                    HDRMode.ALWAYS_ON -> {
                        requestBuilder.set(CaptureRequest.CONTROL_SCENE_MODE, CameraMetadata.CONTROL_SCENE_MODE_HDR)
                        requestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_USE_SCENE_MODE)
                    }
                }

                session.setRepeatingRequest(requestBuilder.build(), null, null)
            }

            saveSettings()
            Log.i(TAG, "HDR mode set to: $mode")
            true

        } catch (e: Exception) {
            Log.e(TAG, "Error setting HDR mode to $mode", e)
            false
        }
    }

    /**
     * Get current bracketing settings
     */
    fun getCurrentSettings(): BracketingSettings {
        return BracketingSettings(
            mode = bracketingMode,
            exposureSteps = exposureSteps,
            hdrMode = hdrMode,
            hdrStrength = hdrStrength,
            tonemappingMode = tonemappingMode,
            ghostingReduction = ghostingReduction,
            isInProgress = isBracketingInProgress
        )
    }

    /**
     * Get available presets
     */
    fun getAvailablePresets(): Map<String, BracketingPreset> = bracketingPresets

    /**
     * Get bracketing recommendations for scenario
     */
    fun getBracketingRecommendation(scenario: String): BracketingRecommendation? {
        return when (scenario.lowercase()) {
            "landscape" -> BracketingRecommendation(
                mode = BracketingMode.THREE_SHOT,
                steps = 1.0f,
                reason = "Standard HDR for landscape detail",
                tips = listOf("Use tripod for best results", "Capture RAW if available", "Avoid moving subjects")
            )
            "architecture" -> BracketingRecommendation(
                mode = BracketingMode.FIVE_SHOT,
                steps = 1.5f,
                reason = "Wide range for building details",
                tips = listOf("Include shadow and highlight detail", "Use manual focus", "Check for people movement")
            )
            "interior" -> BracketingRecommendation(
                mode = BracketingMode.FIVE_SHOT,
                steps = 2.0f,
                reason = "Extreme range for window/indoor balance",
                tips = listOf("Essential for window/interior scenes", "Use timer to avoid shake", "Consider flash for extreme shadows")
            )
            "sunset" -> BracketingRecommendation(
                mode = BracketingMode.THREE_SHOT,
                steps = 1.5f,
                reason = "Capture sky and foreground detail",
                tips = listOf("Time for golden hour", "Focus on horizon line", "Watch for changing light")
            )
            else -> null
        }
    }

    /**
     * Update performance metrics
     */
    private fun updatePerformanceMetrics() {
        val currentTime = System.currentTimeMillis()
        performanceMetrics["bracketing_mode"] = bracketingMode.name
        performanceMetrics["exposure_steps"] = exposureSteps
        performanceMetrics["hdr_mode"] = hdrMode.name
        performanceMetrics["bracketing_count"] = bracketingCount
        performanceMetrics["last_bracketing_time"] = lastBracketingTime
        performanceMetrics["is_in_progress"] = isBracketingInProgress
        performanceMetrics["current_sequence_size"] = currentBracketSequence.size

        if (lastBracketingTime > 0) {
            performanceMetrics["time_since_last_bracketing"] = currentTime - lastBracketingTime
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
            "bracketing_mode" to bracketingMode.name,
            "exposure_steps" to exposureSteps,
            "hdr_mode" to hdrMode.name,
            "hdr_strength" to hdrStrength,
            "tonemapping_mode" to tonemappingMode.name,
            "ghosting_reduction" to ghostingReduction,
            "enabled" to isEnabled
        )

        // Save to SettingsManager when available
        coroutineScope.launch {
            try {
                // settingsManager?.savePluginSettings(name, settings)
                Log.d(TAG, "Bracketing settings saved: $settings")
            } catch (e: Exception) {
                Log.e(TAG, "Error saving bracketing settings", e)
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
            bracketingMode = BracketingMode.THREE_SHOT
            exposureSteps = 1.0f
            hdrMode = HDRMode.AUTO
            hdrStrength = 1.0f
            tonemappingMode = TonemappingMode.NATURAL
            ghostingReduction = true
            isEnabled = false

            Log.d(TAG, "Bracketing settings loaded")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading bracketing settings", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        saveSettings()
        Log.i(TAG, "ExposureBracketingPlugin destroyed")
    }

    /**
     * Bracketing modes
     */
    enum class BracketingMode(val shots: Int) {
        TWO_SHOT(2),
        THREE_SHOT(3),
        FIVE_SHOT(5),
        SEVEN_SHOT(7)
    }

    /**
     * HDR modes
     */
    enum class HDRMode {
        OFF,
        AUTO,
        ALWAYS_ON
    }

    /**
     * Tonemapping modes
     */
    enum class TonemappingMode {
        NATURAL,
        VIVID,
        DRAMATIC,
        SOFT
    }

    /**
     * Data class for bracket frame
     */
    data class BracketFrame(
        val index: Int,
        val exposureValue: Float,
        val filename: String,
        val file: File,
        val success: Boolean,
        val timestamp: Long
    )

    /**
     * Data class for bracketing preset
     */
    data class BracketingPreset(
        val mode: BracketingMode,
        val steps: Float,
        val description: String
    )

    /**
     * Data class for current bracketing settings
     */
    data class BracketingSettings(
        val mode: BracketingMode,
        val exposureSteps: Float,
        val hdrMode: HDRMode,
        val hdrStrength: Float,
        val tonemappingMode: TonemappingMode,
        val ghostingReduction: Boolean,
        val isInProgress: Boolean
    )

    /**
     * Data class for HDR settings
     */
    data class HDRSettings(
        val strength: Float = 1.0f,
        val tonemapping: TonemappingMode = TonemappingMode.NATURAL,
        val ghostingReduction: Boolean = true,
        val colorSaturation: Float = 1.0f,
        val localContrast: Float = 1.0f
    )

    /**
     * Data class for bracketing recommendation
     */
    data class BracketingRecommendation(
        val mode: BracketingMode,
        val steps: Float,
        val reason: String,
        val tips: List<String>
    )

    /**
     * Interface for bracketing callbacks
     */
    interface BracketingCallback {
        fun onBracketingStarted(mode: BracketingMode, exposureValues: List<Float>)
        fun onFrameCaptured(frame: BracketFrame)
        fun onBracketingCompleted(frames: List<BracketFrame>)
        fun onBracketingFailed(error: Exception)
    }

    companion object {
        private const val TAG = "ExposureBracketing"
    }
}