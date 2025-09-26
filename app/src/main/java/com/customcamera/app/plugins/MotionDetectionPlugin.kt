package com.customcamera.app.plugins

import android.graphics.*
import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.ImageProxy
import com.customcamera.app.engine.CameraContext
import com.customcamera.app.engine.plugins.ProcessingPlugin
import com.customcamera.app.engine.plugins.ProcessingResult
import com.customcamera.app.engine.plugins.ProcessingMetadata
import java.nio.ByteBuffer
import kotlin.math.*

/**
 * MotionDetectionPlugin provides AI-powered motion detection for smart capture timing.
 * This plugin can detect movement, stillness, and optimal moments for photography.
 */
class MotionDetectionPlugin : ProcessingPlugin() {

    override val name: String = "MotionDetection"
    override val version: String = "1.0.0"
    override val priority: Int = 20 // Medium priority for motion detection

    private var cameraContext: CameraContext? = null

    // Motion detection configuration
    private var isMotionDetectionEnabled: Boolean = true
    private var motionSensitivity: Float = 0.3f
    private var stillnessThreshold: Float = 0.1f
    private var processingInterval: Long = 100L // Process every 100ms
    private var lastProcessingTime: Long = 0L

    // Motion analysis state
    private var previousFrame: ByteArray? = null
    private var currentMotionLevel: Float = 0.0f
    private var motionHistory: MutableList<MotionFrame> = mutableListOf()
    private var motionRegions: List<Rect> = emptyList()
    private var isSubjectStill: Boolean = false
    private var stillnessDuration: Long = 0L
    private var lastMotionTime: Long = 0L

    // Smart capture features
    private var smartCaptureEnabled: Boolean = true
    private var captureOnStillness: Boolean = false
    private var captureOnPeakAction: Boolean = false
    private var motionTriggerEnabled: Boolean = false
    private var motionTriggerThreshold: Float = 0.7f

    // Advanced motion analysis
    private var motionDirection: MotionDirection = MotionDirection.NONE
    private var motionSpeed: Float = 0.0f
    private var motionPrediction: MotionPrediction? = null

    override suspend fun initialize(context: CameraContext) {
        this.cameraContext = context
        Log.i(TAG, "MotionDetectionPlugin initialized")

        loadSettings(context)

        context.debugLogger.logPlugin(
            name,
            "initialized",
            mapOf(
                "motionDetectionEnabled" to isMotionDetectionEnabled,
                "motionSensitivity" to motionSensitivity,
                "stillnessThreshold" to stillnessThreshold,
                "smartCaptureEnabled" to smartCaptureEnabled,
                "processingInterval" to processingInterval
            )
        )
    }

    override suspend fun onCameraReady(camera: Camera) {
        Log.i(TAG, "Camera ready for motion detection")

        // Reset motion detection state
        resetMotionState()

        cameraContext?.debugLogger?.logPlugin(
            name,
            "camera_ready",
            mapOf("motionStateReset" to true)
        )
    }

    override suspend fun onCameraReleased(camera: Camera) {
        Log.i(TAG, "Camera released, stopping motion detection")
        resetMotionState()
    }

    override suspend fun processFrame(image: ImageProxy): ProcessingResult {
        if (!isMotionDetectionEnabled) {
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

            // Perform comprehensive motion analysis
            val motionAnalysis = analyzeMotion(image, currentTime)

            // Update motion state and history
            updateMotionState(motionAnalysis, currentTime)

            // Check for smart capture opportunities
            val captureRecommendations = analyzeCaptureOpportunities(motionAnalysis)

            val processingTime = System.currentTimeMillis() - startTime

            val metadata = ProcessingMetadata(
                timestamp = currentTime,
                processingTimeMs = processingTime,
                frameNumber = motionHistory.size.toLong(),
                imageSize = android.util.Size(image.width, image.height),
                additionalData = mapOf(
                    "motionLevel" to currentMotionLevel,
                    "isSubjectStill" to isSubjectStill,
                    "stillnessDuration" to stillnessDuration,
                    "motionDirection" to motionDirection.name,
                    "motionSpeed" to motionSpeed,
                    "motionRegions" to motionRegions.size
                )
            )

            ProcessingResult.Success(
                data = mapOf(
                    "motionAnalysis" to motionAnalysis,
                    "currentMotionLevel" to currentMotionLevel,
                    "isSubjectStill" to isSubjectStill,
                    "stillnessDuration" to stillnessDuration,
                    "motionDirection" to motionDirection,
                    "motionSpeed" to motionSpeed,
                    "motionRegions" to motionRegions,
                    "captureRecommendations" to captureRecommendations,
                    "motionPrediction" to (motionPrediction ?: "none")
                ),
                metadata = metadata
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error processing frame for motion detection", e)
            ProcessingResult.Failure("Motion detection error: ${e.message}", e)
        }
    }

    /**
     * Analyze motion between current frame and previous frame
     */
    private suspend fun analyzeMotion(image: ImageProxy, timestamp: Long): MotionAnalysis {
        val currentFrame = imageProxyToByteArray(image)
        val width = image.width
        val height = image.height

        val motionAnalysis = if (previousFrame != null) {
            // Calculate frame difference and motion metrics
            val frameDifference = calculateFrameDifference(previousFrame!!, currentFrame, width, height)
            val motionLevel = calculateMotionLevel(frameDifference)
            val motionRegions = detectMotionRegions(frameDifference, width, height)
            val motionDirection = calculateMotionDirection(previousFrame!!, currentFrame, width, height)
            val motionSpeed = calculateMotionSpeed(motionLevel, processingInterval)

            MotionAnalysis(
                timestamp = timestamp,
                motionLevel = motionLevel,
                motionRegions = motionRegions,
                motionDirection = motionDirection,
                motionSpeed = motionSpeed,
                frameDifference = frameDifference,
                motionQuality = assessMotionQuality(motionLevel, motionRegions),
                isSignificantMotion = motionLevel > motionSensitivity
            )
        } else {
            // First frame, no motion analysis possible
            MotionAnalysis(
                timestamp = timestamp,
                motionLevel = 0.0f,
                motionRegions = emptyList(),
                motionDirection = MotionDirection.NONE,
                motionSpeed = 0.0f,
                frameDifference = null,
                motionQuality = MotionQuality.UNKNOWN,
                isSignificantMotion = false
            )
        }

        // Store current frame as previous for next analysis
        previousFrame = currentFrame.copyOf()

        return motionAnalysis
    }

    /**
     * Calculate frame difference between two frames
     */
    private fun calculateFrameDifference(
        previousFrame: ByteArray,
        currentFrame: ByteArray,
        width: Int,
        height: Int
    ): IntArray {
        val minSize = minOf(previousFrame.size, currentFrame.size)
        val difference = IntArray(minSize)

        for (i in 0 until minSize) {
            val prev = previousFrame[i].toInt() and 0xFF
            val curr = currentFrame[i].toInt() and 0xFF
            difference[i] = abs(curr - prev)
        }

        return difference
    }

    /**
     * Calculate overall motion level from frame difference
     */
    private fun calculateMotionLevel(frameDifference: IntArray?): Float {
        if (frameDifference == null) return 0.0f

        val threshold = 25 // Motion detection threshold
        val motionPixels = frameDifference.count { it > threshold }
        val totalPixels = frameDifference.size

        return if (totalPixels > 0) {
            motionPixels.toFloat() / totalPixels
        } else 0.0f
    }

    /**
     * Detect regions with significant motion
     */
    private fun detectMotionRegions(
        frameDifference: IntArray?,
        width: Int,
        height: Int
    ): List<Rect> {
        if (frameDifference == null) return emptyList()

        val regions = mutableListOf<Rect>()
        val regionSize = 32 // 32x32 pixel regions
        val motionThreshold = 30

        // Divide image into regions and check for motion
        for (y in 0 until height - regionSize step regionSize) {
            for (x in 0 until width - regionSize step regionSize) {
                var motionInRegion = 0

                for (ry in 0 until regionSize) {
                    for (rx in 0 until regionSize) {
                        val pixelY = y + ry
                        val pixelX = x + rx

                        if (pixelY < height && pixelX < width) {
                            val index = pixelY * width + pixelX
                            if (index < frameDifference.size && frameDifference[index] > motionThreshold) {
                                motionInRegion++
                            }
                        }
                    }
                }

                // If region has significant motion, add to list
                val regionPixels = regionSize * regionSize
                val motionRatio = motionInRegion.toFloat() / regionPixels

                if (motionRatio > 0.2f) { // 20% of pixels show motion
                    regions.add(Rect(x, y, x + regionSize, y + regionSize))
                }
            }
        }

        return regions
    }

    /**
     * Calculate motion direction using optical flow-like analysis
     */
    private fun calculateMotionDirection(
        previousFrame: ByteArray,
        currentFrame: ByteArray,
        width: Int,
        height: Int
    ): MotionDirection {
        var horizontalMotion = 0.0
        var verticalMotion = 0.0
        var motionCount = 0

        val sampleStep = 16 // Sample every 16th pixel for performance
        val threshold = 25

        for (y in 1 until height - 1 step sampleStep) {
            for (x in 1 until width - 1 step sampleStep) {
                val index = y * width + x

                if (index < minOf(previousFrame.size, currentFrame.size) - width - 1) {
                    val prevPixel = previousFrame[index].toInt() and 0xFF
                    val currPixel = currentFrame[index].toInt() and 0xFF

                    if (abs(currPixel - prevPixel) > threshold) {
                        // Calculate gradients for motion direction
                        val gradX = (currentFrame[index + 1].toInt() and 0xFF) -
                                   (currentFrame[index - 1].toInt() and 0xFF)
                        val gradY = (currentFrame[index + width].toInt() and 0xFF) -
                                   (currentFrame[index - width].toInt() and 0xFF)

                        horizontalMotion += gradX
                        verticalMotion += gradY
                        motionCount++
                    }
                }
            }
        }

        return if (motionCount > 0) {
            val avgHorizontal = horizontalMotion / motionCount
            val avgVertical = verticalMotion / motionCount

            when {
                abs(avgHorizontal) < 10 && abs(avgVertical) < 10 -> MotionDirection.NONE
                abs(avgHorizontal) > abs(avgVertical) * 2 -> {
                    if (avgHorizontal > 0) MotionDirection.RIGHT else MotionDirection.LEFT
                }
                abs(avgVertical) > abs(avgHorizontal) * 2 -> {
                    if (avgVertical > 0) MotionDirection.DOWN else MotionDirection.UP
                }
                avgHorizontal > 0 && avgVertical > 0 -> MotionDirection.DOWN_RIGHT
                avgHorizontal > 0 && avgVertical < 0 -> MotionDirection.UP_RIGHT
                avgHorizontal < 0 && avgVertical > 0 -> MotionDirection.DOWN_LEFT
                avgHorizontal < 0 && avgVertical < 0 -> MotionDirection.UP_LEFT
                else -> MotionDirection.MIXED
            }
        } else {
            MotionDirection.NONE
        }
    }

    /**
     * Calculate motion speed based on motion level and time interval
     */
    private fun calculateMotionSpeed(motionLevel: Float, intervalMs: Long): Float {
        // Convert motion level to speed (pixels per second)
        val intervalSec = intervalMs / 1000.0f
        return if (intervalSec > 0) motionLevel / intervalSec else 0.0f
    }

    /**
     * Assess the quality of detected motion
     */
    private fun assessMotionQuality(motionLevel: Float, motionRegions: List<Rect>): MotionQuality {
        return when {
            motionLevel < 0.05f -> MotionQuality.STILL
            motionLevel < 0.2f && motionRegions.size <= 2 -> MotionQuality.SMOOTH
            motionLevel < 0.5f && motionRegions.size <= 5 -> MotionQuality.MODERATE
            motionLevel < 0.8f -> MotionQuality.ACTIVE
            else -> MotionQuality.CHAOTIC
        }
    }

    /**
     * Update motion state based on current analysis
     */
    private fun updateMotionState(motionAnalysis: MotionAnalysis, currentTime: Long) {
        currentMotionLevel = motionAnalysis.motionLevel
        motionRegions = motionAnalysis.motionRegions
        motionDirection = motionAnalysis.motionDirection
        motionSpeed = motionAnalysis.motionSpeed

        // Update stillness detection
        if (motionAnalysis.motionLevel <= stillnessThreshold) {
            if (!isSubjectStill) {
                isSubjectStill = true
                stillnessDuration = 0L
                Log.d(TAG, "Subject became still")
            } else {
                stillnessDuration = currentTime - (motionHistory.lastOrNull()?.timestamp ?: currentTime)
            }
        } else {
            if (isSubjectStill) {
                Log.d(TAG, "Subject started moving after ${stillnessDuration}ms of stillness")
            }
            isSubjectStill = false
            stillnessDuration = 0L
            lastMotionTime = currentTime
        }

        // Add to motion history
        val motionFrame = MotionFrame(
            timestamp = currentTime,
            motionLevel = motionAnalysis.motionLevel,
            motionDirection = motionAnalysis.motionDirection,
            motionSpeed = motionAnalysis.motionSpeed,
            motionQuality = motionAnalysis.motionQuality,
            isSignificantMotion = motionAnalysis.isSignificantMotion
        )

        motionHistory.add(motionFrame)
        if (motionHistory.size > 100) {
            motionHistory.removeAt(0) // Keep last 100 frames
        }

        // Generate motion prediction
        motionPrediction = generateMotionPrediction()

        // Log significant motion events
        if (motionAnalysis.isSignificantMotion) {
            cameraContext?.debugLogger?.logPlugin(
                name,
                "significant_motion_detected",
                mapOf(
                    "motionLevel" to motionAnalysis.motionLevel,
                    "motionDirection" to motionAnalysis.motionDirection.name,
                    "motionSpeed" to motionAnalysis.motionSpeed,
                    "motionRegions" to motionRegions.size
                )
            )
        }
    }

    /**
     * Analyze capture opportunities based on motion analysis
     */
    private fun analyzeCaptureOpportunities(motionAnalysis: MotionAnalysis): CaptureRecommendations {
        val recommendations = mutableListOf<String>()
        var captureScore = 0.5f // Base score

        // Stillness-based capture
        if (captureOnStillness && isSubjectStill && stillnessDuration > 500) {
            recommendations.add("Subject is still - good for sharp photos")
            captureScore += 0.3f
        }

        // Peak action capture
        if (captureOnPeakAction && motionAnalysis.motionLevel > 0.6f &&
            motionAnalysis.motionQuality == MotionQuality.ACTIVE) {
            recommendations.add("Peak action detected - capture dynamic moment")
            captureScore += 0.4f
        }

        // Motion trigger
        if (motionTriggerEnabled && motionAnalysis.motionLevel > motionTriggerThreshold) {
            recommendations.add("Motion trigger activated")
            captureScore += 0.5f
        }

        // Predictive capture based on motion patterns
        motionPrediction?.let { prediction ->
            if (prediction.optimalCaptureTime <= 1000) { // Within 1 second
                recommendations.add("Optimal capture moment predicted in ${prediction.optimalCaptureTime}ms")
                captureScore += 0.3f
            }
        }

        // Composition-based recommendations
        if (motionRegions.isNotEmpty()) {
            val centralMotion = motionRegions.any { rect ->
                val centerX = rect.centerX()
                val centerY = rect.centerY()
                // Check if motion is in central area (rough estimation)
                centerX > 0.3f && centerX < 0.7f && centerY > 0.3f && centerY < 0.7f
            }

            if (centralMotion) {
                recommendations.add("Central subject motion - good composition")
                captureScore += 0.2f
            }
        }

        return CaptureRecommendations(
            shouldCapture = captureScore > 0.7f,
            captureScore = captureScore.coerceIn(0.0f, 1.0f),
            recommendations = recommendations,
            bestCaptureTime = motionPrediction?.optimalCaptureTime ?: 0L,
            captureReason = recommendations.firstOrNull() ?: "Standard capture"
        )
    }

    /**
     * Generate motion prediction for smart capture timing
     */
    private fun generateMotionPrediction(): MotionPrediction? {
        if (motionHistory.size < 10) return null

        val recentFrames = motionHistory.takeLast(10)
        val avgMotionLevel = recentFrames.map { it.motionLevel }.average().toFloat()
        val motionTrend = calculateMotionTrend(recentFrames)

        val optimalCaptureTime = when {
            motionTrend == MotionTrend.DECREASING && avgMotionLevel < 0.3f -> {
                // Motion is decreasing, subject likely to be still soon
                500L
            }
            motionTrend == MotionTrend.INCREASING && avgMotionLevel > 0.6f -> {
                // Motion is increasing, capture at peak
                200L
            }
            motionTrend == MotionTrend.STABLE && isSubjectStill -> {
                // Stable and still, immediate capture
                0L
            }
            else -> {
                // Default prediction
                1000L
            }
        }

        return MotionPrediction(
            avgMotionLevel = avgMotionLevel,
            motionTrend = motionTrend,
            optimalCaptureTime = optimalCaptureTime,
            confidence = calculatePredictionConfidence(recentFrames)
        )
    }

    /**
     * Calculate motion trend from recent frames
     */
    private fun calculateMotionTrend(recentFrames: List<MotionFrame>): MotionTrend {
        if (recentFrames.size < 5) return MotionTrend.UNKNOWN

        val firstHalf = recentFrames.take(recentFrames.size / 2).map { it.motionLevel }.average()
        val secondHalf = recentFrames.drop(recentFrames.size / 2).map { it.motionLevel }.average()

        val difference = secondHalf - firstHalf

        return when {
            difference > 0.1 -> MotionTrend.INCREASING
            difference < -0.1 -> MotionTrend.DECREASING
            abs(difference) <= 0.05 -> MotionTrend.STABLE
            else -> MotionTrend.FLUCTUATING
        }
    }

    /**
     * Calculate confidence in motion prediction
     */
    private fun calculatePredictionConfidence(recentFrames: List<MotionFrame>): Float {
        val motionLevels = recentFrames.map { it.motionLevel }
        val variance = motionLevels.map { (it - motionLevels.average()).pow(2) }.average()
        val stability = 1f - variance.toFloat().coerceAtMost(1f)

        return (stability * 0.7f + (recentFrames.size / 20f) * 0.3f).coerceIn(0f, 1f)
    }

    /**
     * Reset motion detection state
     */
    private fun resetMotionState() {
        previousFrame = null
        currentMotionLevel = 0.0f
        motionHistory.clear()
        motionRegions = emptyList()
        isSubjectStill = false
        stillnessDuration = 0L
        lastMotionTime = 0L
        motionDirection = MotionDirection.NONE
        motionSpeed = 0.0f
        motionPrediction = null
    }

    // Utility functions
    private fun imageProxyToByteArray(image: ImageProxy): ByteArray {
        val buffer: ByteBuffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return bytes
    }

    // Configuration methods
    fun setMotionDetectionEnabled(enabled: Boolean) {
        if (isMotionDetectionEnabled != enabled) {
            isMotionDetectionEnabled = enabled
            if (!enabled) {
                resetMotionState()
            }
            saveSettings()
            Log.i(TAG, "Motion detection ${if (enabled) "enabled" else "disabled"}")
        }
    }

    fun setMotionSensitivity(sensitivity: Float) {
        val clampedSensitivity = sensitivity.coerceIn(0.1f, 1.0f)
        if (motionSensitivity != clampedSensitivity) {
            motionSensitivity = clampedSensitivity
            saveSettings()
            Log.i(TAG, "Motion sensitivity set to: $motionSensitivity")
        }
    }

    fun setStillnessThreshold(threshold: Float) {
        val clampedThreshold = threshold.coerceIn(0.05f, 0.5f)
        if (stillnessThreshold != clampedThreshold) {
            stillnessThreshold = clampedThreshold
            saveSettings()
            Log.i(TAG, "Stillness threshold set to: $stillnessThreshold")
        }
    }

    fun setSmartCaptureEnabled(enabled: Boolean) {
        smartCaptureEnabled = enabled
        saveSettings()
        Log.i(TAG, "Smart capture ${if (enabled) "enabled" else "disabled"}")
    }

    fun setCaptureOnStillness(enabled: Boolean) {
        captureOnStillness = enabled
        saveSettings()
        Log.i(TAG, "Capture on stillness ${if (enabled) "enabled" else "disabled"}")
    }

    fun setCaptureOnPeakAction(enabled: Boolean) {
        captureOnPeakAction = enabled
        saveSettings()
        Log.i(TAG, "Capture on peak action ${if (enabled) "enabled" else "disabled"}")
    }

    fun setMotionTriggerEnabled(enabled: Boolean) {
        motionTriggerEnabled = enabled
        saveSettings()
        Log.i(TAG, "Motion trigger ${if (enabled) "enabled" else "disabled"}")
    }

    fun setMotionTriggerThreshold(threshold: Float) {
        val clampedThreshold = threshold.coerceIn(0.3f, 1.0f)
        if (motionTriggerThreshold != clampedThreshold) {
            motionTriggerThreshold = clampedThreshold
            saveSettings()
            Log.i(TAG, "Motion trigger threshold set to: $motionTriggerThreshold")
        }
    }

    fun setProcessingInterval(intervalMs: Long) {
        if (intervalMs > 0 && processingInterval != intervalMs) {
            processingInterval = intervalMs
            saveSettings()
            Log.i(TAG, "Motion detection processing interval set to: ${intervalMs}ms")
        }
    }

    // Getters
    fun getCurrentMotionLevel(): Float = currentMotionLevel
    fun isSubjectCurrentlyStill(): Boolean = isSubjectStill
    fun getStillnessDuration(): Long = stillnessDuration
    fun getMotionDirection(): MotionDirection = motionDirection
    fun getMotionSpeed(): Float = motionSpeed
    fun getMotionRegions(): List<Rect> = motionRegions
    fun getMotionHistory(): List<MotionFrame> = motionHistory.toList()
    fun getMotionPrediction(): MotionPrediction? = motionPrediction

    fun getMotionStats(): Map<String, Any> {
        val recentMotionLevels = motionHistory.takeLast(20).map { it.motionLevel }
        return mapOf(
            "currentMotionLevel" to currentMotionLevel,
            "isSubjectStill" to isSubjectStill,
            "stillnessDuration" to stillnessDuration,
            "motionDirection" to motionDirection.name,
            "motionSpeed" to motionSpeed,
            "motionRegionsCount" to motionRegions.size,
            "motionHistorySize" to motionHistory.size,
            "avgRecentMotion" to if (recentMotionLevels.isNotEmpty()) recentMotionLevels.average() else 0.0,
            "maxRecentMotion" to (recentMotionLevels.maxOrNull() ?: 0.0f),
            "motionDetectionEnabled" to isMotionDetectionEnabled,
            "smartCaptureEnabled" to smartCaptureEnabled
        )
    }

    override fun cleanup() {
        Log.i(TAG, "Cleaning up MotionDetectionPlugin")

        resetMotionState()
        cameraContext = null
    }

    private fun loadSettings(context: CameraContext) {
        val settings = context.settingsManager

        try {
            isMotionDetectionEnabled = settings.getPluginSetting(name, "motionDetectionEnabled", "true").toBoolean()
            motionSensitivity = settings.getPluginSetting(name, "motionSensitivity", "0.3").toFloat()
            stillnessThreshold = settings.getPluginSetting(name, "stillnessThreshold", "0.1").toFloat()
            processingInterval = settings.getPluginSetting(name, "processingInterval", "100").toLong()
            smartCaptureEnabled = settings.getPluginSetting(name, "smartCaptureEnabled", "true").toBoolean()
            captureOnStillness = settings.getPluginSetting(name, "captureOnStillness", "false").toBoolean()
            captureOnPeakAction = settings.getPluginSetting(name, "captureOnPeakAction", "false").toBoolean()
            motionTriggerEnabled = settings.getPluginSetting(name, "motionTriggerEnabled", "false").toBoolean()
            motionTriggerThreshold = settings.getPluginSetting(name, "motionTriggerThreshold", "0.7").toFloat()

            Log.i(TAG, "Loaded settings: detection=$isMotionDetectionEnabled, sensitivity=$motionSensitivity, threshold=$stillnessThreshold")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load settings, using defaults", e)
        }
    }

    private fun saveSettings() {
        val settings = cameraContext?.settingsManager ?: return

        settings.setPluginSetting(name, "motionDetectionEnabled", isMotionDetectionEnabled.toString())
        settings.setPluginSetting(name, "motionSensitivity", motionSensitivity.toString())
        settings.setPluginSetting(name, "stillnessThreshold", stillnessThreshold.toString())
        settings.setPluginSetting(name, "processingInterval", processingInterval.toString())
        settings.setPluginSetting(name, "smartCaptureEnabled", smartCaptureEnabled.toString())
        settings.setPluginSetting(name, "captureOnStillness", captureOnStillness.toString())
        settings.setPluginSetting(name, "captureOnPeakAction", captureOnPeakAction.toString())
        settings.setPluginSetting(name, "motionTriggerEnabled", motionTriggerEnabled.toString())
        settings.setPluginSetting(name, "motionTriggerThreshold", motionTriggerThreshold.toString())
    }

    companion object {
        private const val TAG = "MotionDetectionPlugin"
    }
}

/**
 * Enums and data classes for motion detection
 */
enum class MotionDirection {
    NONE, UP, DOWN, LEFT, RIGHT,
    UP_LEFT, UP_RIGHT, DOWN_LEFT, DOWN_RIGHT,
    MIXED
}

enum class MotionQuality {
    UNKNOWN, STILL, SMOOTH, MODERATE, ACTIVE, CHAOTIC
}

enum class MotionTrend {
    UNKNOWN, STABLE, INCREASING, DECREASING, FLUCTUATING
}

data class MotionAnalysis(
    val timestamp: Long,
    val motionLevel: Float,
    val motionRegions: List<Rect>,
    val motionDirection: MotionDirection,
    val motionSpeed: Float,
    val frameDifference: IntArray?,
    val motionQuality: MotionQuality,
    val isSignificantMotion: Boolean
)

data class MotionFrame(
    val timestamp: Long,
    val motionLevel: Float,
    val motionDirection: MotionDirection,
    val motionSpeed: Float,
    val motionQuality: MotionQuality,
    val isSignificantMotion: Boolean
)

data class MotionPrediction(
    val avgMotionLevel: Float,
    val motionTrend: MotionTrend,
    val optimalCaptureTime: Long,
    val confidence: Float
)

data class CaptureRecommendations(
    val shouldCapture: Boolean,
    val captureScore: Float,
    val recommendations: List<String>,
    val bestCaptureTime: Long,
    val captureReason: String
)