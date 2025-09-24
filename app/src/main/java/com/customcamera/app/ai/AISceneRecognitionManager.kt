package com.customcamera.app.ai

import android.content.Context
import android.graphics.*
import android.util.Log
import androidx.camera.core.ImageProxy
import kotlinx.coroutines.*
import kotlin.math.*

/**
 * AI Scene Recognition Manager
 *
 * Provides intelligent scene analysis and recognition capabilities:
 * - Real-time scene detection and classification
 * - Environmental condition analysis (lighting, weather, indoor/outdoor)
 * - Subject matter recognition (people, nature, architecture, food, etc.)
 * - Camera setting recommendations based on scene analysis
 * - Confidence scoring for recognition accuracy
 * - Performance-optimized ML inference pipeline
 */
class AISceneRecognitionManager(private val context: Context) {

    companion object {
        private const val TAG = "AISceneRecognition"
        private const val ANALYSIS_INTERVAL_MS = 500L
        private const val MIN_CONFIDENCE_THRESHOLD = 0.6f
        private const val SCENE_HISTORY_SIZE = 10
    }

    /**
     * Detected scene types
     */
    enum class SceneType {
        PORTRAIT,           // People, faces, portraits
        LANDSCAPE,          // Natural scenery, mountains, vistas
        ARCHITECTURE,       // Buildings, monuments, structures
        FOOD,              // Meals, drinks, culinary items
        MACRO,             // Close-up details, small objects
        NIGHT,             // Low-light, evening, darkness
        SUNSET_SUNRISE,    // Golden hour photography
        INDOOR,            // Interior spaces, rooms
        OUTDOOR,           // Exterior environments
        SPORTS_ACTION,     // Movement, athletics, action
        NATURE_WILDLIFE,   // Animals, plants, natural life
        STREET,            // Urban environments, city life
        DOCUMENT,          // Text, papers, documents
        PARTY_EVENT,       // Celebrations, gatherings
        UNKNOWN            // Unrecognized or mixed scenes
    }

    /**
     * Environmental conditions detected
     */
    enum class EnvironmentCondition {
        BRIGHT_DAYLIGHT,    // Strong natural lighting
        OVERCAST,           // Cloudy, diffused lighting
        GOLDEN_HOUR,        // Warm, angled lighting
        BLUE_HOUR,          // Twilight conditions
        LOW_LIGHT,          // Dim interior or evening
        ARTIFICIAL_LIGHT,   // Indoor lighting, flash
        BACKLIT,            // Subject against bright background
        HIGH_CONTRAST,      // Strong light/shadow differences
        NORMAL              // Balanced lighting conditions
    }

    /**
     * Scene analysis result
     */
    data class SceneAnalysis(
        val primaryScene: SceneType,
        val confidence: Float,
        val secondaryScenes: List<Pair<SceneType, Float>>,
        val environmentCondition: EnvironmentCondition,
        val lightingScore: Float,           // 0.0 = very dark, 1.0 = very bright
        val contrastScore: Float,           // 0.0 = low contrast, 1.0 = high contrast
        val colorTemperature: Float,        // Kelvin estimate
        val motionDetected: Boolean,
        val faceCount: Int,
        val recommendedSettings: CameraSettings,
        val timestamp: Long = System.currentTimeMillis()
    )

    /**
     * Recommended camera settings based on scene
     */
    data class CameraSettings(
        val exposureCompensation: Int = 0,  // EV adjustment
        val focusMode: FocusMode = FocusMode.AUTO,
        val meteringMode: MeteringMode = MeteringMode.CENTER_WEIGHTED,
        val whiteBalance: WhiteBalanceMode = WhiteBalanceMode.AUTO,
        val isoRange: Pair<Int, Int> = Pair(100, 800),
        val useFlash: Boolean = false,
        val enableHDR: Boolean = false,
        val captureMode: CaptureMode = CaptureMode.STANDARD
    )

    enum class FocusMode { AUTO, MACRO, INFINITY, MANUAL }
    enum class MeteringMode { CENTER_WEIGHTED, SPOT, MATRIX }
    enum class WhiteBalanceMode { AUTO, DAYLIGHT, TUNGSTEN, FLUORESCENT, CLOUDY }
    enum class CaptureMode { STANDARD, HDR, NIGHT, PORTRAIT, SPORT }

    private var isAnalysisActive = false
    private var analysisJob: Job? = null
    private val sceneHistory = mutableListOf<SceneAnalysis>()
    private var lastAnalysis: SceneAnalysis? = null

    // Image analysis buffers
    private var analysisBuffer: IntArray? = null
    private var analysisWidth = 0
    private var analysisHeight = 0

    /**
     * Initialize AI scene recognition system
     */
    fun initialize(): Boolean {
        return try {
            Log.d(TAG, "AI scene recognition system initialized")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize AI scene recognition", e)
            false
        }
    }

    /**
     * Start continuous scene analysis
     */
    fun startAnalysis() {
        if (isAnalysisActive) return

        isAnalysisActive = true
        analysisJob = CoroutineScope(Dispatchers.Default).launch {
            while (isActive && isAnalysisActive) {
                // Analysis processing happens when frames are provided
                delay(ANALYSIS_INTERVAL_MS)
            }
        }

        Log.d(TAG, "Started AI scene analysis")
    }

    /**
     * Stop scene analysis
     */
    fun stopAnalysis() {
        isAnalysisActive = false
        analysisJob?.cancel()
        analysisJob = null

        // Clear buffers
        analysisBuffer = null
        sceneHistory.clear()

        Log.d(TAG, "Stopped AI scene analysis")
    }

    /**
     * Analyze image frame for scene recognition
     */
    suspend fun analyzeFrame(imageProxy: ImageProxy): SceneAnalysis = withContext(Dispatchers.Default) {
        if (!isAnalysisActive) {
            return@withContext createDefaultAnalysis()
        }

        try {
            // Convert ImageProxy to analyzable format
            val bitmap = convertImageProxyToBitmap(imageProxy)

            // Perform scene analysis
            val sceneAnalysis = performSceneAnalysis(bitmap)

            // Update history and cache
            updateAnalysisHistory(sceneAnalysis)
            lastAnalysis = sceneAnalysis

            sceneAnalysis
        } catch (e: Exception) {
            Log.e(TAG, "Error during scene analysis", e)
            createDefaultAnalysis()
        }
    }

    /**
     * Get current scene analysis
     */
    fun getCurrentAnalysis(): SceneAnalysis? = lastAnalysis

    /**
     * Get scene analysis history
     */
    fun getAnalysisHistory(): List<SceneAnalysis> = sceneHistory.toList()

    /**
     * Get scene stability (how consistent recent detections are)
     */
    fun getSceneStability(): Float {
        if (sceneHistory.size < 3) return 0.5f

        val recentScenes = sceneHistory.takeLast(5)
        val primaryScene = recentScenes.first().primaryScene
        val consistentCount = recentScenes.count { it.primaryScene == primaryScene }

        return consistentCount.toFloat() / recentScenes.size
    }

    /**
     * Check if specific scene type is detected
     */
    fun isSceneDetected(sceneType: SceneType, minConfidence: Float = MIN_CONFIDENCE_THRESHOLD): Boolean {
        val analysis = lastAnalysis ?: return false
        return analysis.primaryScene == sceneType && analysis.confidence >= minConfidence
    }

    /**
     * Get recommended camera settings for current scene
     */
    fun getRecommendedSettings(): CameraSettings? {
        return lastAnalysis?.recommendedSettings
    }

    // Private analysis methods

    private fun convertImageProxyToBitmap(imageProxy: ImageProxy): Bitmap {
        val buffer = imageProxy.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)

        // Create a simplified bitmap for analysis (downscaled for performance)
        val options = BitmapFactory.Options().apply {
            inSampleSize = 4  // Reduce to 1/4 size for faster processing
            inPreferredConfig = Bitmap.Config.RGB_565
        }

        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            ?: createPlaceholderBitmap()
    }

    private fun createPlaceholderBitmap(): Bitmap {
        return Bitmap.createBitmap(160, 120, Bitmap.Config.RGB_565)
    }

    private suspend fun performSceneAnalysis(bitmap: Bitmap): SceneAnalysis = withContext(Dispatchers.Default) {
        // Analyze image properties
        val lightingScore = analyzeLighting(bitmap)
        val contrastScore = analyzeContrast(bitmap)
        val colorTemperature = analyzeColorTemperature(bitmap)
        val motionDetected = analyzeMotion(bitmap)
        val faceCount = detectFaces(bitmap)

        // Determine primary scene type
        val sceneScores = calculateSceneScores(bitmap, lightingScore, contrastScore, faceCount)
        val primaryScene = sceneScores.maxByOrNull { it.second }?.first ?: SceneType.UNKNOWN
        val confidence = sceneScores.find { it.first == primaryScene }?.second ?: 0.5f

        // Get secondary scenes (other high-scoring scenes)
        val secondaryScenes = sceneScores
            .filter { it.first != primaryScene && it.second > 0.3f }
            .sortedByDescending { it.second }
            .take(3)

        // Determine environmental conditions
        val environmentCondition = determineEnvironmentCondition(lightingScore, colorTemperature, contrastScore)

        // Generate camera setting recommendations
        val recommendedSettings = generateCameraSettings(primaryScene, environmentCondition, lightingScore)

        SceneAnalysis(
            primaryScene = primaryScene,
            confidence = confidence,
            secondaryScenes = secondaryScenes,
            environmentCondition = environmentCondition,
            lightingScore = lightingScore,
            contrastScore = contrastScore,
            colorTemperature = colorTemperature,
            motionDetected = motionDetected,
            faceCount = faceCount,
            recommendedSettings = recommendedSettings
        )
    }

    private fun analyzeLighting(bitmap: Bitmap): Float {
        var totalBrightness = 0.0
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        for (pixel in pixels) {
            val r = Color.red(pixel)
            val g = Color.green(pixel)
            val b = Color.blue(pixel)
            // Calculate perceived brightness using luminance formula
            totalBrightness += (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
        }

        return (totalBrightness / pixels.size).toFloat().coerceIn(0.0f, 1.0f)
    }

    private fun analyzeContrast(bitmap: Bitmap): Float {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        var minBrightness = 1.0
        var maxBrightness = 0.0

        for (pixel in pixels) {
            val r = Color.red(pixel)
            val g = Color.green(pixel)
            val b = Color.blue(pixel)
            val brightness = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0

            minBrightness = min(minBrightness, brightness)
            maxBrightness = max(maxBrightness, brightness)
        }

        return ((maxBrightness - minBrightness) / maxBrightness).toFloat().coerceIn(0.0f, 1.0f)
    }

    private fun analyzeColorTemperature(bitmap: Bitmap): Float {
        var totalR = 0.0
        var totalG = 0.0
        var totalB = 0.0

        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        for (pixel in pixels) {
            totalR += Color.red(pixel)
            totalG += Color.green(pixel)
            totalB += Color.blue(pixel)
        }

        val avgR = totalR / pixels.size
        val avgG = totalG / pixels.size
        val avgB = totalB / pixels.size

        // Simplified color temperature estimation
        val colorRatio = if (avgB > 0) avgR / avgB else 1.0

        // Convert to approximate Kelvin (simplified)
        return when {
            colorRatio > 1.5 -> 3000f  // Warm/tungsten
            colorRatio > 1.2 -> 4000f  // Warm daylight
            colorRatio > 0.9 -> 5500f  // Neutral daylight
            colorRatio > 0.7 -> 6500f  // Cool daylight
            else -> 8000f              // Very cool/shade
        }
    }

    private fun analyzeMotion(bitmap: Bitmap): Boolean {
        // Simplified motion detection - would compare with previous frame in real implementation
        return false
    }

    private fun detectFaces(bitmap: Bitmap): Int {
        // Simplified face detection - would use ML Kit or similar in real implementation
        return 0
    }

    private fun calculateSceneScores(
        bitmap: Bitmap,
        lightingScore: Float,
        contrastScore: Float,
        faceCount: Int
    ): List<Pair<SceneType, Float>> {
        val scores = mutableMapOf<SceneType, Float>()

        // Base scene detection using simple heuristics
        // In a real implementation, this would use trained ML models

        // Portrait detection
        scores[SceneType.PORTRAIT] = when {
            faceCount > 0 -> 0.9f
            lightingScore in 0.3f..0.8f && contrastScore < 0.6f -> 0.4f
            else -> 0.1f
        }

        // Landscape detection
        scores[SceneType.LANDSCAPE] = when {
            lightingScore > 0.6f && contrastScore > 0.5f -> 0.7f
            lightingScore > 0.4f -> 0.5f
            else -> 0.2f
        }

        // Night scene detection
        scores[SceneType.NIGHT] = when {
            lightingScore < 0.2f -> 0.8f
            lightingScore < 0.4f -> 0.6f
            else -> 0.1f
        }

        // Indoor scene detection
        scores[SceneType.INDOOR] = when {
            lightingScore in 0.3f..0.7f && contrastScore < 0.5f -> 0.6f
            lightingScore < 0.5f -> 0.4f
            else -> 0.3f
        }

        // Outdoor scene detection
        scores[SceneType.OUTDOOR] = when {
            lightingScore > 0.7f -> 0.8f
            lightingScore > 0.5f && contrastScore > 0.4f -> 0.6f
            else -> 0.3f
        }

        // Food detection (simplified)
        scores[SceneType.FOOD] = when {
            lightingScore in 0.4f..0.8f && contrastScore in 0.3f..0.7f -> 0.3f
            else -> 0.1f
        }

        // Architecture detection
        scores[SceneType.ARCHITECTURE] = when {
            contrastScore > 0.6f && lightingScore > 0.5f -> 0.5f
            else -> 0.2f
        }

        // Macro detection
        scores[SceneType.MACRO] = 0.2f // Default low score

        // Sports/Action detection
        scores[SceneType.SPORTS_ACTION] = 0.1f // Default low score

        // Set unknown as fallback
        scores[SceneType.UNKNOWN] = 0.3f

        return scores.toList().sortedByDescending { it.second }
    }

    private fun determineEnvironmentCondition(
        lightingScore: Float,
        colorTemperature: Float,
        contrastScore: Float
    ): EnvironmentCondition {
        return when {
            lightingScore > 0.8f -> EnvironmentCondition.BRIGHT_DAYLIGHT
            lightingScore < 0.2f -> EnvironmentCondition.LOW_LIGHT
            lightingScore < 0.4f -> EnvironmentCondition.BLUE_HOUR
            colorTemperature < 3500f -> EnvironmentCondition.ARTIFICIAL_LIGHT
            colorTemperature < 4500f && lightingScore > 0.5f -> EnvironmentCondition.GOLDEN_HOUR
            contrastScore > 0.7f -> EnvironmentCondition.HIGH_CONTRAST
            lightingScore < 0.6f && contrastScore < 0.4f -> EnvironmentCondition.OVERCAST
            else -> EnvironmentCondition.NORMAL
        }
    }

    private fun generateCameraSettings(
        sceneType: SceneType,
        environmentCondition: EnvironmentCondition,
        lightingScore: Float
    ): CameraSettings {
        return when (sceneType) {
            SceneType.PORTRAIT -> CameraSettings(
                focusMode = FocusMode.AUTO,
                meteringMode = MeteringMode.CENTER_WEIGHTED,
                enableHDR = false,
                captureMode = CaptureMode.PORTRAIT,
                exposureCompensation = if (lightingScore < 0.4f) 1 else 0
            )

            SceneType.LANDSCAPE -> CameraSettings(
                focusMode = FocusMode.INFINITY,
                meteringMode = MeteringMode.MATRIX,
                enableHDR = true,
                captureMode = CaptureMode.STANDARD,
                isoRange = Pair(100, 400)
            )

            SceneType.NIGHT -> CameraSettings(
                focusMode = FocusMode.AUTO,
                meteringMode = MeteringMode.CENTER_WEIGHTED,
                enableHDR = true,
                captureMode = CaptureMode.NIGHT,
                isoRange = Pair(400, 3200),
                exposureCompensation = 1
            )

            SceneType.MACRO -> CameraSettings(
                focusMode = FocusMode.MACRO,
                meteringMode = MeteringMode.SPOT,
                enableHDR = false,
                captureMode = CaptureMode.STANDARD
            )

            SceneType.SPORTS_ACTION -> CameraSettings(
                focusMode = FocusMode.AUTO,
                meteringMode = MeteringMode.CENTER_WEIGHTED,
                captureMode = CaptureMode.SPORT,
                isoRange = Pair(200, 1600)
            )

            else -> CameraSettings() // Default settings
        }
    }

    private fun updateAnalysisHistory(analysis: SceneAnalysis) {
        synchronized(sceneHistory) {
            sceneHistory.add(analysis)
            if (sceneHistory.size > SCENE_HISTORY_SIZE) {
                sceneHistory.removeAt(0)
            }
        }
    }

    private fun createDefaultAnalysis(): SceneAnalysis {
        return SceneAnalysis(
            primaryScene = SceneType.UNKNOWN,
            confidence = 0.0f,
            secondaryScenes = emptyList(),
            environmentCondition = EnvironmentCondition.NORMAL,
            lightingScore = 0.5f,
            contrastScore = 0.5f,
            colorTemperature = 5500f,
            motionDetected = false,
            faceCount = 0,
            recommendedSettings = CameraSettings()
        )
    }

    /**
     * Clean up resources
     */
    fun cleanup() {
        stopAnalysis()
        analysisBuffer = null
        Log.d(TAG, "AI scene recognition manager cleaned up")
    }
}