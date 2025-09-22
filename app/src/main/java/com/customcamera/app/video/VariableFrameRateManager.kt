package com.customcamera.app.video

import android.content.Context
import android.util.Log
import android.util.Range
import androidx.camera.video.Quality
import androidx.camera.video.VideoSpec
import kotlinx.coroutines.*
import kotlin.math.*

/**
 * Variable Frame Rate Recording Manager
 *
 * Provides advanced frame rate control for professional video recording:
 * - Time-lapse recording with customizable intervals
 * - Slow motion recording with high frame rates
 * - Variable frame rate adaptation based on scene analysis
 * - Smooth frame rate transitions
 * - Battery-optimized frame rate selection
 * - Professional cinema frame rates (24p, 25p, 30p, 60p, 120p)
 */
class VariableFrameRateManager(private val context: Context) {

    companion object {
        private const val TAG = "VariableFrameRateManager"

        // Standard frame rates
        const val FRAME_RATE_24 = 24
        const val FRAME_RATE_25 = 25
        const val FRAME_RATE_30 = 30
        const val FRAME_RATE_60 = 60
        const val FRAME_RATE_120 = 120
        const val FRAME_RATE_240 = 240

        // Time-lapse intervals (seconds between frames)
        const val TIMELAPSE_INTERVAL_FAST = 0.5f      // 2x speed
        const val TIMELAPSE_INTERVAL_MEDIUM = 2.0f    // 8x speed
        const val TIMELAPSE_INTERVAL_SLOW = 5.0f      // 20x speed
        const val TIMELAPSE_INTERVAL_ULTRA = 30.0f    // 120x speed
    }

    /**
     * Recording modes with different frame rate strategies
     */
    enum class RecordingMode {
        STANDARD,           // Normal frame rate (30fps)
        CINEMATIC,          // Cinema frame rate (24fps)
        SMOOTH,             // High frame rate (60fps)
        SLOW_MOTION_2X,     // 60fps for 2x slow motion
        SLOW_MOTION_4X,     // 120fps for 4x slow motion
        SLOW_MOTION_8X,     // 240fps for 8x slow motion
        TIME_LAPSE_FAST,    // 30fps with 0.5s intervals
        TIME_LAPSE_MEDIUM,  // 30fps with 2s intervals
        TIME_LAPSE_SLOW,    // 30fps with 5s intervals
        TIME_LAPSE_ULTRA,   // 30fps with 30s intervals
        ADAPTIVE,           // Automatic frame rate based on scene
        BATTERY_SAVER       // Lower frame rate for battery conservation
    }

    /**
     * Frame rate configuration
     */
    data class FrameRateConfig(
        val targetFrameRate: Int,
        val captureFrameRate: Int = targetFrameRate,
        val playbackFrameRate: Int = 30,
        val intervalSeconds: Float = 0.0f,
        val mode: RecordingMode = RecordingMode.STANDARD,
        val adaptiveAdjustment: Boolean = false
    )

    /**
     * Scene analysis for adaptive frame rate
     */
    data class SceneAnalysis(
        val motionLevel: Float,        // 0.0 = static, 1.0 = high motion
        val lightingLevel: Float,      // 0.0 = dark, 1.0 = bright
        val complexityLevel: Float,    // 0.0 = simple, 1.0 = complex
        val batteryLevel: Float        // 0.0 = low, 1.0 = full
    )

    private var currentConfig = FrameRateConfig(targetFrameRate = 30)
    private var isRecording = false
    private var sceneAnalysisJob: Job? = null

    /**
     * Set recording mode and configure frame rate
     */
    fun setRecordingMode(mode: RecordingMode): FrameRateConfig {
        currentConfig = when (mode) {
            RecordingMode.STANDARD -> FrameRateConfig(
                targetFrameRate = FRAME_RATE_30,
                mode = mode
            )

            RecordingMode.CINEMATIC -> FrameRateConfig(
                targetFrameRate = FRAME_RATE_24,
                mode = mode
            )

            RecordingMode.SMOOTH -> FrameRateConfig(
                targetFrameRate = FRAME_RATE_60,
                mode = mode
            )

            RecordingMode.SLOW_MOTION_2X -> FrameRateConfig(
                targetFrameRate = FRAME_RATE_30,
                captureFrameRate = FRAME_RATE_60,
                playbackFrameRate = FRAME_RATE_30,
                mode = mode
            )

            RecordingMode.SLOW_MOTION_4X -> FrameRateConfig(
                targetFrameRate = FRAME_RATE_30,
                captureFrameRate = FRAME_RATE_120,
                playbackFrameRate = FRAME_RATE_30,
                mode = mode
            )

            RecordingMode.SLOW_MOTION_8X -> FrameRateConfig(
                targetFrameRate = FRAME_RATE_30,
                captureFrameRate = FRAME_RATE_240,
                playbackFrameRate = FRAME_RATE_30,
                mode = mode
            )

            RecordingMode.TIME_LAPSE_FAST -> FrameRateConfig(
                targetFrameRate = FRAME_RATE_30,
                intervalSeconds = TIMELAPSE_INTERVAL_FAST,
                mode = mode
            )

            RecordingMode.TIME_LAPSE_MEDIUM -> FrameRateConfig(
                targetFrameRate = FRAME_RATE_30,
                intervalSeconds = TIMELAPSE_INTERVAL_MEDIUM,
                mode = mode
            )

            RecordingMode.TIME_LAPSE_SLOW -> FrameRateConfig(
                targetFrameRate = FRAME_RATE_30,
                intervalSeconds = TIMELAPSE_INTERVAL_SLOW,
                mode = mode
            )

            RecordingMode.TIME_LAPSE_ULTRA -> FrameRateConfig(
                targetFrameRate = FRAME_RATE_30,
                intervalSeconds = TIMELAPSE_INTERVAL_ULTRA,
                mode = mode
            )

            RecordingMode.ADAPTIVE -> FrameRateConfig(
                targetFrameRate = FRAME_RATE_30,
                adaptiveAdjustment = true,
                mode = mode
            )

            RecordingMode.BATTERY_SAVER -> FrameRateConfig(
                targetFrameRate = FRAME_RATE_24,
                mode = mode
            )
        }

        Log.d(TAG, "Set recording mode: $mode with config: $currentConfig")
        return currentConfig
    }

    /**
     * Get current frame rate configuration
     */
    fun getCurrentConfig(): FrameRateConfig = currentConfig

    /**
     * Calculate speed multiplier for time-lapse/slow motion
     */
    fun getSpeedMultiplier(): Float {
        return when (currentConfig.mode) {
            RecordingMode.SLOW_MOTION_2X -> 0.5f
            RecordingMode.SLOW_MOTION_4X -> 0.25f
            RecordingMode.SLOW_MOTION_8X -> 0.125f
            RecordingMode.TIME_LAPSE_FAST -> 2.0f
            RecordingMode.TIME_LAPSE_MEDIUM -> 8.0f
            RecordingMode.TIME_LAPSE_SLOW -> 20.0f
            RecordingMode.TIME_LAPSE_ULTRA -> 120.0f
            else -> 1.0f
        }
    }

    /**
     * Start adaptive frame rate monitoring
     */
    fun startAdaptiveFrameRate() {
        if (currentConfig.adaptiveAdjustment) {
            sceneAnalysisJob = CoroutineScope(Dispatchers.Default).launch {
                while (isActive && currentConfig.adaptiveAdjustment) {
                    val sceneAnalysis = analyzeScene()
                    val adaptedConfig = adaptFrameRate(sceneAnalysis)

                    if (adaptedConfig != currentConfig) {
                        currentConfig = adaptedConfig
                        Log.d(TAG, "Adapted frame rate to: ${currentConfig.targetFrameRate}fps")
                    }

                    delay(2000) // Analyze every 2 seconds
                }
            }
        }
    }

    /**
     * Stop adaptive frame rate monitoring
     */
    fun stopAdaptiveFrameRate() {
        sceneAnalysisJob?.cancel()
        sceneAnalysisJob = null
    }

    /**
     * Check if current mode is time-lapse
     */
    fun isTimeLapseMode(): Boolean {
        return when (currentConfig.mode) {
            RecordingMode.TIME_LAPSE_FAST,
            RecordingMode.TIME_LAPSE_MEDIUM,
            RecordingMode.TIME_LAPSE_SLOW,
            RecordingMode.TIME_LAPSE_ULTRA -> true
            else -> false
        }
    }

    /**
     * Check if current mode is slow motion
     */
    fun isSlowMotionMode(): Boolean {
        return when (currentConfig.mode) {
            RecordingMode.SLOW_MOTION_2X,
            RecordingMode.SLOW_MOTION_4X,
            RecordingMode.SLOW_MOTION_8X -> true
            else -> false
        }
    }

    /**
     * Get estimated recording duration for time-lapse
     */
    fun getEstimatedDuration(realTimeSeconds: Long): Long {
        return if (isTimeLapseMode()) {
            (realTimeSeconds / getSpeedMultiplier()).toLong()
        } else {
            realTimeSeconds
        }
    }

    /**
     * Get estimated file size based on frame rate and quality
     */
    fun getEstimatedFileSize(
        durationSeconds: Long,
        quality: Quality = Quality.HD
    ): Long {
        val baseBitrate = when (quality) {
            Quality.LOWEST -> 1_000_000L    // 1 Mbps
            Quality.SD -> 3_000_000L        // 3 Mbps
            Quality.HD -> 8_000_000L        // 8 Mbps
            Quality.FHD -> 15_000_000L      // 15 Mbps
            Quality.UHD -> 45_000_000L      // 45 Mbps
            else -> 8_000_000L
        }

        // Adjust bitrate based on frame rate
        val frameRateMultiplier = currentConfig.captureFrameRate / 30.0f
        val adjustedBitrate = (baseBitrate * frameRateMultiplier).toLong()

        // Calculate file size in bytes
        return (adjustedBitrate * durationSeconds) / 8
    }

    /**
     * Get supported frame rates for current device
     */
    fun getSupportedFrameRates(): List<Int> {
        // In a real implementation, this would query CameraX capabilities
        return listOf(24, 25, 30, 60, 120, 240).filter { frameRate ->
            isFrameRateSupported(frameRate)
        }
    }

    /**
     * Check if a specific frame rate is supported
     */
    fun isFrameRateSupported(frameRate: Int): Boolean {
        // Simplified check - in real implementation, use CameraX capabilities
        return when (frameRate) {
            24, 25, 30 -> true
            60 -> true // Most modern devices support 60fps
            120 -> true // Many devices support 120fps
            240 -> false // Limited device support
            else -> false
        }
    }

    /**
     * Create VideoSpec with current frame rate configuration
     */
    fun createVideoSpec(quality: Quality = Quality.HD): VideoSpec {
        val frameRate = Range.create(
            currentConfig.captureFrameRate,
            currentConfig.captureFrameRate
        )

        return VideoSpec.builder()
            .setFrameRate(frameRate)
            .build()
    }

    /**
     * Get frame rate recommendations for different scenarios
     */
    fun getRecommendations(): Map<String, RecordingMode> {
        return mapOf(
            "General Recording" to RecordingMode.STANDARD,
            "Cinematic Look" to RecordingMode.CINEMATIC,
            "Sports/Action" to RecordingMode.SMOOTH,
            "Slow Motion Effect" to RecordingMode.SLOW_MOTION_2X,
            "Cloud Time-lapse" to RecordingMode.TIME_LAPSE_MEDIUM,
            "Sunset Time-lapse" to RecordingMode.TIME_LAPSE_SLOW,
            "Battery Conservation" to RecordingMode.BATTERY_SAVER,
            "Auto Optimization" to RecordingMode.ADAPTIVE
        )
    }

    // Private helper methods

    private suspend fun analyzeScene(): SceneAnalysis = withContext(Dispatchers.Default) {
        // Simplified scene analysis - in real implementation, analyze camera frames
        val motionLevel = detectMotionLevel()
        val lightingLevel = detectLightingLevel()
        val complexityLevel = detectSceneComplexity()
        val batteryLevel = getBatteryLevel()

        SceneAnalysis(
            motionLevel = motionLevel,
            lightingLevel = lightingLevel,
            complexityLevel = complexityLevel,
            batteryLevel = batteryLevel
        )
    }

    private fun adaptFrameRate(sceneAnalysis: SceneAnalysis): FrameRateConfig {
        val recommendedFrameRate = when {
            // High motion scenes benefit from higher frame rates
            sceneAnalysis.motionLevel > 0.7f -> FRAME_RATE_60

            // Low light scenes may need lower frame rates for better exposure
            sceneAnalysis.lightingLevel < 0.3f -> FRAME_RATE_24

            // Battery saving mode
            sceneAnalysis.batteryLevel < 0.2f -> FRAME_RATE_24

            // Complex scenes with moderate motion
            sceneAnalysis.complexityLevel > 0.6f && sceneAnalysis.motionLevel > 0.4f -> FRAME_RATE_30

            // Default to standard frame rate
            else -> FRAME_RATE_30
        }

        return currentConfig.copy(targetFrameRate = recommendedFrameRate)
    }

    private fun detectMotionLevel(): Float {
        // Simplified motion detection - in real implementation,
        // analyze consecutive frames for motion vectors
        return 0.5f
    }

    private fun detectLightingLevel(): Float {
        // Simplified lighting detection - in real implementation,
        // analyze frame brightness and ISO values
        return 0.7f
    }

    private fun detectSceneComplexity(): Float {
        // Simplified complexity detection - in real implementation,
        // analyze edge density and texture patterns
        return 0.4f
    }

    private fun getBatteryLevel(): Float {
        return try {
            val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager
            val batteryLevel = batteryManager.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
            batteryLevel / 100.0f
        } catch (e: Exception) {
            Log.w(TAG, "Unable to get battery level", e)
            1.0f // Assume full battery if unable to read
        }
    }

    /**
     * Set recording state
     */
    fun setRecording(recording: Boolean) {
        isRecording = recording
        if (recording) {
            startAdaptiveFrameRate()
        } else {
            stopAdaptiveFrameRate()
        }
    }

    /**
     * Clean up resources
     */
    fun cleanup() {
        stopAdaptiveFrameRate()
        Log.d(TAG, "Variable frame rate manager cleaned up")
    }
}