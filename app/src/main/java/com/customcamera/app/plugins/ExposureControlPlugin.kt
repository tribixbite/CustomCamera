package com.customcamera.app.plugins

import android.util.Log
import android.util.Range
import androidx.camera.core.Camera
import androidx.camera.core.ExposureState
import com.customcamera.app.engine.CameraContext
import com.customcamera.app.engine.plugins.ControlPlugin
import com.customcamera.app.engine.plugins.ControlResult
import kotlinx.coroutines.delay

/**
 * ExposureControlPlugin provides advanced exposure control capabilities
 * including exposure compensation, exposure lock, and automatic exposure analysis.
 */
class ExposureControlPlugin : ControlPlugin() {

    override val name: String = "ExposureControl"
    override val version: String = "1.0.0"
    override val priority: Int = 15 // High priority for exposure controls

    private var cameraContext: CameraContext? = null
    private var currentCamera: Camera? = null

    // Exposure state tracking
    private var exposureCompensationRange: Range<Int>? = null
    private var exposureCompensationStep: android.util.Rational? = null
    private var currentExposureIndex: Int = 0
    private var isExposureLocked: Boolean = false
    private var autoExposureEnabled: Boolean = true

    // Exposure analysis
    private var lastExposureAnalysis: ExposureAnalysis? = null

    data class ExposureAnalysis(
        val currentEV: Float,
        val recommendedEV: Float,
        val isOptimal: Boolean,
        val underExposed: Boolean,
        val overExposed: Boolean,
        val analysisTimestamp: Long
    )

    override suspend fun initialize(context: CameraContext) {
        this.cameraContext = context
        Log.i(TAG, "ExposureControlPlugin initialized")

        // Load settings
        loadSettings(context)

        context.debugLogger.logPlugin(
            name,
            "initialized",
            mapOf(
                "autoExposure" to autoExposureEnabled,
                "exposureLocked" to isExposureLocked
            )
        )
    }

    override suspend fun onCameraReady(camera: Camera) {
        currentCamera = camera
        Log.i(TAG, "Camera ready, initializing exposure controls")

        // Get exposure capabilities
        extractExposureCapabilities(camera)

        // Apply current settings
        val result = applyControls(camera)

        cameraContext?.debugLogger?.logPlugin(
            name,
            "camera_ready",
            mapOf(
                "exposureRange" to exposureCompensationRange.toString(),
                "exposureStep" to exposureCompensationStep.toString(),
                "currentIndex" to currentExposureIndex,
                "result" to result.toString()
            )
        )

        Log.i(TAG, "Exposure controls ready - Range: $exposureCompensationRange, Step: $exposureCompensationStep")
    }

    override suspend fun onCameraReleased(camera: Camera) {
        Log.i(TAG, "Camera released, preserving exposure settings")
        currentCamera = null
        lastExposureAnalysis = null
    }

    override suspend fun applyControls(camera: Camera): ControlResult {
        return try {
            if (!autoExposureEnabled) {
                // Apply manual exposure compensation
                camera.cameraControl.setExposureCompensationIndex(currentExposureIndex)
                Log.d(TAG, "Applied manual exposure compensation: $currentExposureIndex (${getCurrentEV()}EV)")
            } else {
                // Reset to auto exposure
                camera.cameraControl.setExposureCompensationIndex(0)
                Log.d(TAG, "Applied auto exposure mode")
            }

            ControlResult.Success("Exposure controls applied successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply exposure controls", e)
            ControlResult.Failure("Failed to apply exposure: ${e.message}", e)
        }
    }

    override suspend fun resetControls(camera: Camera): ControlResult {
        return try {
            currentExposureIndex = 0
            autoExposureEnabled = true
            isExposureLocked = false

            applyControls(camera)
            saveSettings()

            Log.i(TAG, "Exposure controls reset to auto")
            ControlResult.Success("Exposure reset to automatic")

        } catch (e: Exception) {
            ControlResult.Failure("Failed to reset exposure: ${e.message}", e)
        }
    }

    override fun getCurrentSettings(): Map<String, Any> {
        return mapOf(
            "currentExposureIndex" to currentExposureIndex,
            "currentEV" to getCurrentEV(),
            "autoExposureEnabled" to autoExposureEnabled,
            "isExposureLocked" to isExposureLocked,
            "exposureRange" to (exposureCompensationRange?.toString() ?: "unknown"),
            "exposureStep" to (exposureCompensationStep?.toString() ?: "unknown"),
            "lastAnalysis" to (lastExposureAnalysis?.toString() ?: "none")
        )
    }

    /**
     * Set exposure compensation manually
     */
    suspend fun setExposureCompensation(index: Int): ControlResult {
        val range = exposureCompensationRange
        if (range == null) {
            return ControlResult.Failure("Exposure range not available")
        }

        if (index !in range) {
            return ControlResult.Failure("Exposure index $index out of range $range")
        }

        currentExposureIndex = index
        autoExposureEnabled = false

        val camera = currentCamera
        if (camera != null) {
            val result = applyControls(camera)
            if (result is ControlResult.Success) {
                saveSettings()
                Log.i(TAG, "Exposure compensation set to $index (${getCurrentEV()}EV)")

                cameraContext?.debugLogger?.logPlugin(
                    name,
                    "exposure_set_manual",
                    mapOf(
                        "index" to index,
                        "ev" to getCurrentEV()
                    )
                )
            }
            return result
        } else {
            saveSettings()
            return ControlResult.Success("Exposure compensation saved (camera not ready)")
        }
    }

    /**
     * Enable or disable auto exposure
     */
    suspend fun setAutoExposure(enabled: Boolean): ControlResult {
        if (autoExposureEnabled == enabled) {
            return ControlResult.Success("Auto exposure already ${if (enabled) "enabled" else "disabled"}")
        }

        autoExposureEnabled = enabled

        if (enabled) {
            currentExposureIndex = 0
        }

        val camera = currentCamera
        if (camera != null) {
            val result = applyControls(camera)
            if (result is ControlResult.Success) {
                saveSettings()
                Log.i(TAG, "Auto exposure ${if (enabled) "enabled" else "disabled"}")

                cameraContext?.debugLogger?.logPlugin(
                    name,
                    "auto_exposure_changed",
                    mapOf("enabled" to enabled)
                )
            }
            return result
        } else {
            saveSettings()
            return ControlResult.Success("Auto exposure setting saved")
        }
    }

    /**
     * Lock exposure at current level
     */
    suspend fun lockExposure(): ControlResult {
        val camera = currentCamera ?: return ControlResult.Failure("Camera not available")

        return try {
            // Lock exposure at current compensation level
            isExposureLocked = true
            autoExposureEnabled = false

            // Keep current exposure index
            camera.cameraControl.setExposureCompensationIndex(currentExposureIndex)

            saveSettings()
            Log.i(TAG, "Exposure locked at ${getCurrentEV()}EV")

            cameraContext?.debugLogger?.logPlugin(
                name,
                "exposure_locked",
                mapOf(
                    "index" to currentExposureIndex,
                    "ev" to getCurrentEV()
                )
            )

            ControlResult.Success("Exposure locked at ${getCurrentEV()}EV")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to lock exposure", e)
            ControlResult.Failure("Failed to lock exposure: ${e.message}", e)
        }
    }

    /**
     * Unlock exposure (return to auto)
     */
    suspend fun unlockExposure(): ControlResult {
        if (!isExposureLocked) {
            return ControlResult.Success("Exposure already unlocked")
        }

        isExposureLocked = false
        return setAutoExposure(true)
    }

    /**
     * Perform exposure bracketing (multiple shots at different exposures)
     */
    suspend fun performExposureBracketing(steps: IntArray): ControlResult {
        val camera = currentCamera ?: return ControlResult.Failure("Camera not available")
        val range = exposureCompensationRange ?: return ControlResult.Failure("Exposure range not available")

        return try {
            Log.i(TAG, "Starting exposure bracketing with steps: ${steps.joinToString()}")

            val originalIndex = currentExposureIndex
            val results = mutableListOf<String>()

            for (step in steps) {
                if (step in range) {
                    // Set exposure for this bracket
                    camera.cameraControl.setExposureCompensationIndex(step)

                    // Wait for exposure to settle
                    delay(500)

                    results.add("${step} (${step * (exposureCompensationStep?.toFloat() ?: 0f)}EV)")
                    Log.d(TAG, "Bracketing step: $step")
                }
            }

            // Restore original exposure
            camera.cameraControl.setExposureCompensationIndex(originalIndex)

            cameraContext?.debugLogger?.logPlugin(
                name,
                "exposure_bracketing",
                mapOf(
                    "steps" to steps.joinToString(),
                    "results" to results.size
                )
            )

            ControlResult.Success("Bracketing completed: ${results.joinToString()}")

        } catch (e: Exception) {
            Log.e(TAG, "Exposure bracketing failed", e)
            ControlResult.Failure("Bracketing failed: ${e.message}", e)
        }
    }

    /**
     * Analyze current exposure and provide recommendations
     */
    fun analyzeExposure(): ExposureAnalysis? {
        val camera = currentCamera ?: return null

        try {
            val currentEV = getCurrentEV()

            // Simple exposure analysis (in production, this would use histogram data)
            val underExposed = currentEV < -2.0f
            val overExposed = currentEV > 2.0f
            val isOptimal = !underExposed && !overExposed && Math.abs(currentEV) < 0.5f

            val recommendedEV = when {
                underExposed -> currentEV + 1.0f
                overExposed -> currentEV - 1.0f
                else -> currentEV
            }

            lastExposureAnalysis = ExposureAnalysis(
                currentEV = currentEV,
                recommendedEV = recommendedEV,
                isOptimal = isOptimal,
                underExposed = underExposed,
                overExposed = overExposed,
                analysisTimestamp = System.currentTimeMillis()
            )

            Log.d(TAG, "Exposure analysis: $lastExposureAnalysis")
            return lastExposureAnalysis

        } catch (e: Exception) {
            Log.e(TAG, "Failed to analyze exposure", e)
            return null
        }
    }

    /**
     * Get current exposure compensation in EV units
     */
    fun getCurrentEV(): Float {
        val step = exposureCompensationStep?.toFloat() ?: return 0f
        return currentExposureIndex * step
    }

    /**
     * Get available exposure range in EV units
     */
    fun getExposureRangeEV(): Pair<Float, Float>? {
        val range = exposureCompensationRange ?: return null
        val step = exposureCompensationStep?.toFloat() ?: return null

        return Pair(
            range.lower * step,
            range.upper * step
        )
    }

    override fun cleanup() {
        Log.i(TAG, "Cleaning up ExposureControlPlugin")

        cameraContext = null
        currentCamera = null
        lastExposureAnalysis = null
    }

    private fun extractExposureCapabilities(camera: Camera) {
        try {
            val exposureState: ExposureState = camera.cameraInfo.exposureState

            exposureCompensationRange = exposureState.exposureCompensationRange
            exposureCompensationStep = exposureState.exposureCompensationStep

            Log.i(TAG, "Exposure capabilities extracted:")
            Log.i(TAG, "  Range: $exposureCompensationRange")
            Log.i(TAG, "  Step: $exposureCompensationStep")

            if (exposureCompensationRange != null && exposureCompensationStep != null) {
                val evRange = getExposureRangeEV()
                Log.i(TAG, "  EV Range: ${evRange?.first}EV to ${evRange?.second}EV")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract exposure capabilities", e)
            // Fallback values
            exposureCompensationRange = Range.create(-6, 6)
            exposureCompensationStep = android.util.Rational(1, 3) // 1/3 EV steps
        }
    }

    private fun loadSettings(context: CameraContext) {
        val settings = context.settingsManager

        try {
            currentExposureIndex = settings.getPluginSetting(name, "exposureIndex", "0").toInt()
            autoExposureEnabled = settings.getPluginSetting(name, "autoExposure", "true").toBoolean()
            isExposureLocked = settings.getPluginSetting(name, "exposureLocked", "false").toBoolean()

            Log.i(TAG, "Loaded settings: index=$currentExposureIndex, auto=$autoExposureEnabled, locked=$isExposureLocked")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load settings, using defaults", e)
            currentExposureIndex = 0
            autoExposureEnabled = true
            isExposureLocked = false
        }
    }

    private fun saveSettings() {
        val settings = cameraContext?.settingsManager ?: return

        settings.setPluginSetting(name, "exposureIndex", currentExposureIndex.toString())
        settings.setPluginSetting(name, "autoExposure", autoExposureEnabled.toString())
        settings.setPluginSetting(name, "exposureLocked", isExposureLocked.toString())
    }

    companion object {
        private const val TAG = "ExposureControlPlugin"
    }
}