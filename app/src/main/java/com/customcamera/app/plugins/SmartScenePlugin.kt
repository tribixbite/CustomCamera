package com.customcamera.app.plugins

import android.graphics.*
import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.ImageProxy
import androidx.annotation.ColorInt
import com.customcamera.app.engine.CameraContext
import com.customcamera.app.engine.plugins.ProcessingPlugin
import com.customcamera.app.engine.plugins.ProcessingResult
import com.customcamera.app.engine.plugins.ProcessingMetadata
import java.nio.ByteBuffer
import kotlin.math.*

/**
 * SmartScenePlugin provides AI-powered automatic scene detection and classification.
 * This plugin analyzes camera frames to identify scene types (portrait, landscape, macro, etc.)
 * and provides intelligent suggestions for optimal camera settings.
 */
class SmartScenePlugin : ProcessingPlugin() {

    override val name: String = "SmartScene"
    override val version: String = "1.0.0"
    override val priority: Int = 30 // High priority for scene analysis

    private var cameraContext: CameraContext? = null
    private var isSceneDetectionEnabled: Boolean = true
    private var processingInterval: Long = 200L // Process every 200ms
    private var lastProcessingTime: Long = 0L

    // Scene detection state
    private var currentScene: SceneType = SceneType.UNKNOWN
    private var sceneConfidence: Float = 0.0f
    private var sceneDetectionHistory: MutableList<SceneDetection> = mutableListOf()

    // Advanced analysis parameters
    private var brightnessThreshold = 128
    private var contrastThreshold = 50
    private var edgeDensityThreshold = 0.3f
    private var colorVariationThreshold = 40

    override suspend fun initialize(context: CameraContext) {
        this.cameraContext = context
        Log.i(TAG, "SmartScenePlugin initialized")

        loadSettings(context)

        context.debugLogger.logPlugin(
            name,
            "initialized",
            mapOf(
                "sceneDetectionEnabled" to isSceneDetectionEnabled,
                "processingInterval" to processingInterval,
                "brightnessThreshold" to brightnessThreshold,
                "contrastThreshold" to contrastThreshold
            )
        )
    }

    override suspend fun onCameraReady(camera: Camera) {
        Log.i(TAG, "Camera ready for scene detection")

        cameraContext?.debugLogger?.logPlugin(
            name,
            "camera_ready",
            mapOf("currentScene" to currentScene.name)
        )
    }

    override suspend fun onCameraReleased(camera: Camera) {
        Log.i(TAG, "Camera released, stopping scene detection")
        currentScene = SceneType.UNKNOWN
        sceneConfidence = 0.0f
    }

    override suspend fun processFrame(image: ImageProxy): ProcessingResult {
        if (!isSceneDetectionEnabled) {
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

            // Perform comprehensive scene analysis
            val sceneAnalysis = performSceneAnalysis(image)

            // Update current scene if confidence is high enough
            if (sceneAnalysis.confidence > 0.6f) {
                if (currentScene != sceneAnalysis.sceneType) {
                    val previousScene = currentScene
                    currentScene = sceneAnalysis.sceneType
                    sceneConfidence = sceneAnalysis.confidence

                    Log.i(TAG, "Scene changed from ${previousScene.name} to ${currentScene.name} (confidence: ${String.format("%.2f", sceneConfidence)})")

                    // Add to detection history
                    sceneDetectionHistory.add(sceneAnalysis)
                    if (sceneDetectionHistory.size > 20) {
                        sceneDetectionHistory.removeAt(0) // Keep last 20 detections
                    }

                    // Log scene change
                    cameraContext?.debugLogger?.logPlugin(
                        name,
                        "scene_detected",
                        mapOf(
                            "scene" to currentScene.name,
                            "confidence" to sceneConfidence,
                            "previousScene" to previousScene.name,
                            "analysisData" to sceneAnalysis.analysisData
                        )
                    )
                }
            }

            val processingTime = System.currentTimeMillis() - startTime

            val metadata = ProcessingMetadata(
                timestamp = currentTime,
                processingTimeMs = processingTime,
                frameNumber = 0L,
                imageSize = android.util.Size(image.width, image.height),
                additionalData = mapOf(
                    "currentScene" to currentScene.name,
                    "sceneConfidence" to sceneConfidence,
                    "sceneDetectionEnabled" to isSceneDetectionEnabled
                )
            )

            ProcessingResult.Success(
                data = mapOf(
                    "sceneType" to currentScene,
                    "confidence" to sceneConfidence,
                    "sceneAnalysis" to sceneAnalysis,
                    "recommendations" to getSceneRecommendations(currentScene)
                ),
                metadata = metadata
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error processing frame for scene detection", e)
            ProcessingResult.Failure("Scene detection error: ${e.message}", e)
        }
    }

    /**
     * Perform comprehensive scene analysis on the image
     */
    private suspend fun performSceneAnalysis(image: ImageProxy): SceneDetection {
        val imageData = imageProxyToByteArray(image)
        val width = image.width
        val height = image.height

        // Calculate various scene characteristics
        val brightness = calculateAverageBrightness(imageData, width, height)
        val contrast = calculateContrast(imageData, width, height)
        val edgeDensity = calculateEdgeDensity(imageData, width, height)
        val colorVariation = calculateColorVariation(imageData, width, height)
        val dominantColors = findDominantColors(imageData, width, height)
        val textureComplexity = calculateTextureComplexity(imageData, width, height)

        // Analyze composition and spatial characteristics
        val faceRegions = detectFaceRegions(imageData, width, height)
        val horizonLine = detectHorizonLine(imageData, width, height)
        val focusRegions = detectFocusRegions(imageData, width, height)
        val motionBlur = detectMotionBlur(imageData, width, height)

        // Scene classification based on comprehensive analysis
        val sceneType = classifyScene(
            brightness, contrast, edgeDensity, colorVariation,
            textureComplexity, faceRegions, horizonLine, focusRegions,
            motionBlur, dominantColors
        )

        val confidence = calculateSceneConfidence(
            sceneType, brightness, contrast, edgeDensity,
            colorVariation, textureComplexity
        )

        val analysisData = mapOf(
            "brightness" to brightness,
            "contrast" to contrast,
            "edgeDensity" to edgeDensity,
            "colorVariation" to colorVariation,
            "textureComplexity" to textureComplexity,
            "faceRegions" to faceRegions.size,
            "horizonDetected" to (horizonLine >= 0),
            "focusRegions" to focusRegions.size,
            "motionBlur" to motionBlur,
            "dominantColors" to dominantColors.size
        )

        return SceneDetection(
            sceneType = sceneType,
            confidence = confidence,
            timestamp = System.currentTimeMillis(),
            analysisData = analysisData
        )
    }

    /**
     * Classify scene based on comprehensive image analysis
     */
    private fun classifyScene(
        brightness: Float,
        contrast: Float,
        edgeDensity: Float,
        colorVariation: Float,
        textureComplexity: Float,
        faceRegions: List<Rect>,
        horizonLine: Int,
        focusRegions: List<Rect>,
        motionBlur: Float,
        dominantColors: List<Int>
    ): SceneType {

        // Portrait detection - faces with lower background complexity
        if (faceRegions.isNotEmpty() && textureComplexity < 0.6f) {
            return SceneType.PORTRAIT
        }

        // Landscape detection - horizon line and high edge density
        if (horizonLine >= 0 && edgeDensity > 0.4f && colorVariation > 30) {
            return SceneType.LANDSCAPE
        }

        // Macro detection - high contrast with focused center region
        if (contrast > 60 && focusRegions.isNotEmpty() && edgeDensity > 0.5f) {
            val centerFocus = focusRegions.any { rect ->
                val centerX = rect.centerX()
                val centerY = rect.centerY()
                // Check if focus region is near center
                abs(centerX - 0.5f) < 0.3f && abs(centerY - 0.5f) < 0.3f
            }
            if (centerFocus) return SceneType.MACRO
        }

        // Night/Low Light detection
        if (brightness < 50 && contrast < 30) {
            return SceneType.NIGHT
        }

        // Indoor detection - moderate brightness with artificial lighting
        if (brightness >= 80 && brightness <= 160 && colorVariation < 35 && textureComplexity < 0.5f) {
            return SceneType.INDOOR
        }

        // Sports/Action detection - motion blur indicators
        if (motionBlur > 0.7f && edgeDensity > 0.6f) {
            return SceneType.SPORTS_ACTION
        }

        // Food detection - warm colors and close-up characteristics
        val hasWarmTones = dominantColors.any { color ->
            val hsv = FloatArray(3)
            Color.colorToHSV(color, hsv)
            hsv[0] in 15f..45f || hsv[0] in 315f..360f // Red-orange-yellow range
        }
        if (hasWarmTones && textureComplexity > 0.4f && contrast > 40) {
            return SceneType.FOOD
        }

        // Architecture detection - high edge density with geometric patterns
        if (edgeDensity > 0.6f && contrast > 50 && colorVariation < 40) {
            return SceneType.ARCHITECTURE
        }

        // Sunset/Sunrise detection - warm dominant colors with moderate brightness
        if (brightness >= 60 && brightness <= 140 && hasWarmTones && horizonLine >= 0) {
            return SceneType.SUNSET_SUNRISE
        }

        // Document/Text detection - high contrast with low color variation
        if (contrast > 70 && colorVariation < 20 && edgeDensity > 0.7f) {
            return SceneType.DOCUMENT_TEXT
        }

        // Default to general outdoor or indoor based on brightness
        return if (brightness > 120) SceneType.OUTDOOR else SceneType.INDOOR
    }

    /**
     * Calculate confidence score for scene classification
     */
    private fun calculateSceneConfidence(
        sceneType: SceneType,
        brightness: Float,
        contrast: Float,
        edgeDensity: Float,
        colorVariation: Float,
        textureComplexity: Float
    ): Float {
        var confidence = 0.5f // Base confidence

        // Adjust confidence based on scene-specific characteristics
        when (sceneType) {
            SceneType.PORTRAIT -> {
                confidence += if (textureComplexity < 0.6f) 0.3f else -0.2f
                confidence += if (contrast > 30) 0.2f else -0.1f
            }
            SceneType.LANDSCAPE -> {
                confidence += if (edgeDensity > 0.4f) 0.3f else -0.2f
                confidence += if (colorVariation > 30) 0.2f else -0.1f
            }
            SceneType.MACRO -> {
                confidence += if (contrast > 60) 0.3f else -0.2f
                confidence += if (edgeDensity > 0.5f) 0.2f else -0.1f
            }
            SceneType.NIGHT -> {
                confidence += if (brightness < 50) 0.4f else -0.3f
            }
            SceneType.SPORTS_ACTION -> {
                confidence += if (edgeDensity > 0.6f) 0.3f else -0.2f
            }
            else -> {
                confidence += 0.1f // Slight boost for other scenes
            }
        }

        return confidence.coerceIn(0.0f, 1.0f)
    }

    /**
     * Get recommendations for optimal settings based on detected scene
     */
    private fun getSceneRecommendations(sceneType: SceneType): Map<String, Any> {
        return when (sceneType) {
            SceneType.PORTRAIT -> mapOf(
                "focusMode" to "SINGLE_POINT",
                "exposureCompensation" to "+0.3",
                "flashMode" to "AUTO",
                "iso" to "AUTO",
                "whiteBalance" to "AUTO",
                "suggestions" to listOf(
                    "Use portrait mode if available",
                    "Focus on the subject's eyes",
                    "Consider slight overexposure for skin tones"
                )
            )
            SceneType.LANDSCAPE -> mapOf(
                "focusMode" to "HYPERFOCAL",
                "exposureCompensation" to "0",
                "flashMode" to "OFF",
                "iso" to "LOW",
                "whiteBalance" to "DAYLIGHT",
                "suggestions" to listOf(
                    "Use smaller aperture for deeper depth of field",
                    "Consider using a tripod",
                    "Rule of thirds composition"
                )
            )
            SceneType.MACRO -> mapOf(
                "focusMode" to "MACRO",
                "exposureCompensation" to "+0.3",
                "flashMode" to "OFF",
                "iso" to "LOW",
                "whiteBalance" to "AUTO",
                "suggestions" to listOf(
                    "Get as close as possible to subject",
                    "Use manual focus for precision",
                    "Avoid camera shake"
                )
            )
            SceneType.NIGHT -> mapOf(
                "focusMode" to "INFINITY",
                "exposureCompensation" to "+0.5",
                "flashMode" to "OFF",
                "iso" to "HIGH",
                "whiteBalance" to "AUTO",
                "suggestions" to listOf(
                    "Use tripod for stability",
                    "Enable night mode if available",
                    "Long exposure for light trails"
                )
            )
            SceneType.SPORTS_ACTION -> mapOf(
                "focusMode" to "CONTINUOUS",
                "exposureCompensation" to "0",
                "flashMode" to "OFF",
                "iso" to "AUTO_HIGH",
                "whiteBalance" to "AUTO",
                "suggestions" to listOf(
                    "Use burst mode",
                    "Higher shutter speed",
                    "Continuous autofocus"
                )
            )
            SceneType.FOOD -> mapOf(
                "focusMode" to "SINGLE_POINT",
                "exposureCompensation" to "+0.3",
                "flashMode" to "OFF",
                "iso" to "LOW",
                "whiteBalance" to "WARM",
                "suggestions" to listOf(
                    "Natural lighting works best",
                    "45-degree angle often flattering",
                    "Focus on the most appealing part"
                )
            )
            else -> mapOf(
                "focusMode" to "AUTO",
                "exposureCompensation" to "0",
                "flashMode" to "AUTO",
                "iso" to "AUTO",
                "whiteBalance" to "AUTO",
                "suggestions" to listOf("Use automatic settings")
            )
        }
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
        val sampleStep = 4 // Sample every 4th pixel for performance

        for (i in imageData.indices step sampleStep) {
            totalBrightness += (imageData[i].toInt() and 0xFF)
        }

        return totalBrightness.toFloat() / (imageData.size / sampleStep)
    }

    private fun calculateContrast(imageData: ByteArray, width: Int, height: Int): Float {
        val histogram = IntArray(256)
        val sampleStep = 4

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

    private fun calculateEdgeDensity(imageData: ByteArray, width: Int, height: Int): Float {
        var edgeCount = 0
        val threshold = 30
        val sampleStep = 8 // Sample every 8th pixel for performance

        // Simple edge detection using gradient magnitude
        for (y in 1 until height - 1 step sampleStep) {
            for (x in 1 until width - 1 step sampleStep) {
                val index = y * width + x
                if (index < imageData.size - width - 1) {
                    val current = imageData[index].toInt() and 0xFF
                    val right = imageData[index + 1].toInt() and 0xFF
                    val down = imageData[index + width].toInt() and 0xFF

                    val gradientX = abs(right - current)
                    val gradientY = abs(down - current)
                    val magnitude = sqrt((gradientX * gradientX + gradientY * gradientY).toDouble())

                    if (magnitude > threshold) {
                        edgeCount++
                    }
                }
            }
        }

        val totalSamples = ((height - 2) / sampleStep) * ((width - 2) / sampleStep)
        return if (totalSamples > 0) edgeCount.toFloat() / totalSamples else 0f
    }

    private fun calculateColorVariation(imageData: ByteArray, width: Int, height: Int): Float {
        val colorCounts = mutableMapOf<Int, Int>()
        val sampleStep = 8

        // Sample colors and count unique values
        for (i in imageData.indices step sampleStep) {
            val colorValue = (imageData[i].toInt() and 0xFF) / 8 // Reduce to ~32 color levels
            colorCounts[colorValue] = (colorCounts[colorValue] ?: 0) + 1
        }

        // Calculate color variation based on unique color count and distribution
        val uniqueColors = colorCounts.size
        val maxPossibleColors = 32
        val uniformity = colorCounts.values.maxOrNull()?.toFloat() ?: 1f
        val totalSamples = colorCounts.values.sum().toFloat()

        return (uniqueColors.toFloat() / maxPossibleColors) * (1f - uniformity / totalSamples) * 100f
    }

    private fun calculateTextureComplexity(imageData: ByteArray, width: Int, height: Int): Float {
        var totalVariance = 0.0
        val windowSize = 3
        val sampleStep = 16

        // Calculate local variance in small windows
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
                        val mean = windowPixels.average()
                        val variance = windowPixels.sumOf { (it - mean).pow(2) } / windowPixels.size
                        totalVariance += variance
                    }
                }
            }
        }

        val windowCount = ((height - 2 * windowSize) / sampleStep) * ((width - 2 * windowSize) / sampleStep)
        return if (windowCount > 0) {
            (totalVariance / windowCount / 10000.0).coerceIn(0.0, 1.0).toFloat()
        } else 0f
    }

    private fun findDominantColors(imageData: ByteArray, width: Int, height: Int): List<Int> {
        val colorCounts = mutableMapOf<Int, Int>()
        val sampleStep = 16

        // Sample colors and count occurrences
        for (i in imageData.indices step sampleStep) {
            val grayValue = imageData[i].toInt() and 0xFF
            // Convert grayscale to approximate color buckets
            val colorBucket = when {
                grayValue < 64 -> Color.BLACK
                grayValue < 128 -> Color.GRAY
                grayValue < 192 -> Color.LTGRAY
                else -> Color.WHITE
            }

            colorCounts[colorBucket] = (colorCounts[colorBucket] ?: 0) + 1
        }

        // Return top 3 most dominant colors
        return colorCounts.entries
            .sortedByDescending { it.value }
            .take(3)
            .map { it.key }
    }

    private fun detectFaceRegions(imageData: ByteArray, width: Int, height: Int): List<Rect> {
        // Simplified face detection based on skin tone regions and oval patterns
        val faceRegions = mutableListOf<Rect>()

        // This is a placeholder for actual face detection
        // In production, you would use ML Kit Face Detection or similar

        // Simple heuristic: look for skin tone regions in upper portion of image
        val skinToneRegions = mutableListOf<Point>()
        val upperHalf = height / 2

        for (y in 0 until upperHalf step 16) {
            for (x in 0 until width step 16) {
                val index = y * width + x
                if (index < imageData.size) {
                    val pixel = imageData[index].toInt() and 0xFF
                    // Skin tone detection (simplified for grayscale)
                    if (pixel in 120..200) {
                        skinToneRegions.add(Point(x, y))
                    }
                }
            }
        }

        // Cluster skin tone regions into potential face areas
        if (skinToneRegions.size > 10) {
            val centerX = skinToneRegions.map { it.x }.average().toInt()
            val centerY = skinToneRegions.map { it.y }.average().toInt()
            val avgWidth = width / 6  // Estimate face width
            val avgHeight = height / 8 // Estimate face height

            faceRegions.add(Rect(
                centerX - avgWidth / 2,
                centerY - avgHeight / 2,
                centerX + avgWidth / 2,
                centerY + avgHeight / 2
            ))
        }

        return faceRegions
    }

    private fun detectHorizonLine(imageData: ByteArray, width: Int, height: Int): Int {
        val horizontalEdges = IntArray(height) { 0 }

        // Look for strong horizontal edges that could indicate horizon
        for (y in height / 4 until 3 * height / 4) { // Focus on middle section
            var edgeStrength = 0

            for (x in 0 until width - 1 step 4) {
                val index = y * width + x
                if (index < imageData.size - 1) {
                    val current = imageData[index].toInt() and 0xFF
                    val next = imageData[index + 1].toInt() and 0xFF
                    edgeStrength += abs(next - current)
                }
            }

            horizontalEdges[y] = edgeStrength
        }

        // Find the row with the strongest horizontal edge
        val maxEdgeY = horizontalEdges.indices.maxByOrNull { horizontalEdges[it] } ?: -1
        return if (horizontalEdges[maxEdgeY] > width * 10) maxEdgeY else -1
    }

    private fun detectFocusRegions(imageData: ByteArray, width: Int, height: Int): List<Rect> {
        val focusRegions = mutableListOf<Rect>()
        val regionSize = 64 // 64x64 pixel regions
        val sharpnessThreshold = 50.0

        // Divide image into regions and calculate sharpness
        for (y in 0 until height - regionSize step regionSize) {
            for (x in 0 until width - regionSize step regionSize) {
                val sharpness = calculateRegionSharpness(imageData, width, x, y, regionSize)

                if (sharpness > sharpnessThreshold) {
                    focusRegions.add(Rect(x, y, x + regionSize, y + regionSize))
                }
            }
        }

        return focusRegions
    }

    private fun calculateRegionSharpness(
        imageData: ByteArray,
        width: Int,
        regionX: Int,
        regionY: Int,
        regionSize: Int
    ): Double {
        var totalGradient = 0.0
        var pixelCount = 0

        for (y in regionY until minOf(regionY + regionSize, imageData.size / width - 1)) {
            for (x in regionX until minOf(regionX + regionSize, width - 1)) {
                val index = y * width + x
                if (index < imageData.size - width - 1) {
                    val current = imageData[index].toInt() and 0xFF
                    val right = imageData[index + 1].toInt() and 0xFF
                    val down = imageData[index + width].toInt() and 0xFF

                    val gradientX = abs(right - current)
                    val gradientY = abs(down - current)
                    totalGradient += sqrt((gradientX * gradientX + gradientY * gradientY).toDouble())
                    pixelCount++
                }
            }
        }

        return if (pixelCount > 0) totalGradient / pixelCount else 0.0
    }

    private fun detectMotionBlur(imageData: ByteArray, width: Int, height: Int): Float {
        var totalBlur = 0.0
        var sampleCount = 0
        val sampleStep = 32

        // Simple motion blur detection using local variance
        for (y in 1 until height - 1 step sampleStep) {
            for (x in 1 until width - 1 step sampleStep) {
                val index = y * width + x
                if (index < imageData.size - width - 1) {
                    val neighbors = listOf(
                        imageData[index - 1].toInt() and 0xFF,
                        imageData[index + 1].toInt() and 0xFF,
                        imageData[index - width].toInt() and 0xFF,
                        imageData[index + width].toInt() and 0xFF,
                        imageData[index].toInt() and 0xFF
                    )

                    val mean = neighbors.average()
                    val variance = neighbors.sumOf { (it - mean).pow(2) } / neighbors.size

                    // Lower variance often indicates blur
                    totalBlur += (100.0 - sqrt(variance)) / 100.0
                    sampleCount++
                }
            }
        }

        return if (sampleCount > 0) {
            (totalBlur / sampleCount).coerceIn(0.0, 1.0).toFloat()
        } else 0f
    }

    /**
     * Enable or disable scene detection
     */
    fun setSceneDetectionEnabled(enabled: Boolean) {
        if (isSceneDetectionEnabled != enabled) {
            isSceneDetectionEnabled = enabled
            if (!enabled) {
                currentScene = SceneType.UNKNOWN
                sceneConfidence = 0.0f
            }
            saveSettings()

            Log.i(TAG, "Scene detection ${if (enabled) "enabled" else "disabled"}")

            cameraContext?.debugLogger?.logPlugin(
                name,
                "scene_detection_toggled",
                mapOf("enabled" to enabled)
            )
        }
    }

    /**
     * Set processing interval for scene detection
     */
    fun setProcessingInterval(intervalMs: Long) {
        if (intervalMs > 0 && processingInterval != intervalMs) {
            processingInterval = intervalMs
            saveSettings()
            Log.i(TAG, "Scene detection processing interval set to: ${intervalMs}ms")
        }
    }

    /**
     * Get current scene information
     */
    fun getCurrentSceneInfo(): Map<String, Any> {
        return mapOf(
            "currentScene" to currentScene.name,
            "confidence" to sceneConfidence,
            "detectionEnabled" to isSceneDetectionEnabled,
            "processingInterval" to processingInterval,
            "historyCount" to sceneDetectionHistory.size,
            "recommendations" to getSceneRecommendations(currentScene)
        )
    }

    /**
     * Get scene detection history
     */
    fun getSceneHistory(): List<SceneDetection> {
        return sceneDetectionHistory.toList()
    }

    /**
     * Clear scene detection history
     */
    fun clearSceneHistory() {
        sceneDetectionHistory.clear()
        Log.i(TAG, "Scene detection history cleared")

        cameraContext?.debugLogger?.logPlugin(
            name,
            "history_cleared",
            emptyMap()
        )
    }

    /**
     * Force scene re-detection on next frame
     */
    fun forceSceneRedetection() {
        lastProcessingTime = 0L
        Log.i(TAG, "Forced scene re-detection on next frame")
    }

    override fun cleanup() {
        Log.i(TAG, "Cleaning up SmartScenePlugin")

        currentScene = SceneType.UNKNOWN
        sceneConfidence = 0.0f
        sceneDetectionHistory.clear()
        cameraContext = null
    }

    private fun loadSettings(context: CameraContext) {
        val settings = context.settingsManager

        try {
            isSceneDetectionEnabled = settings.getPluginSetting(name, "sceneDetectionEnabled", "true").toBoolean()
            processingInterval = settings.getPluginSetting(name, "processingInterval", "200").toLong()
            brightnessThreshold = settings.getPluginSetting(name, "brightnessThreshold", "128").toInt()
            contrastThreshold = settings.getPluginSetting(name, "contrastThreshold", "50").toInt()
            edgeDensityThreshold = settings.getPluginSetting(name, "edgeDensityThreshold", "0.3").toFloat()
            colorVariationThreshold = settings.getPluginSetting(name, "colorVariationThreshold", "40").toInt()

            Log.i(TAG, "Loaded settings: detection=$isSceneDetectionEnabled, interval=${processingInterval}ms")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load settings, using defaults", e)
        }
    }

    private fun saveSettings() {
        val settings = cameraContext?.settingsManager ?: return

        settings.setPluginSetting(name, "sceneDetectionEnabled", isSceneDetectionEnabled.toString())
        settings.setPluginSetting(name, "processingInterval", processingInterval.toString())
        settings.setPluginSetting(name, "brightnessThreshold", brightnessThreshold.toString())
        settings.setPluginSetting(name, "contrastThreshold", contrastThreshold.toString())
        settings.setPluginSetting(name, "edgeDensityThreshold", edgeDensityThreshold.toString())
        settings.setPluginSetting(name, "colorVariationThreshold", colorVariationThreshold.toString())
    }

    companion object {
        private const val TAG = "SmartScenePlugin"
    }
}

/**
 * Enumeration of scene types that can be automatically detected
 */
enum class SceneType {
    UNKNOWN,
    PORTRAIT,
    LANDSCAPE,
    MACRO,
    NIGHT,
    INDOOR,
    OUTDOOR,
    SPORTS_ACTION,
    FOOD,
    ARCHITECTURE,
    SUNSET_SUNRISE,
    DOCUMENT_TEXT
}

/**
 * Data class representing a scene detection result
 */
data class SceneDetection(
    val sceneType: SceneType,
    val confidence: Float,
    val timestamp: Long,
    val analysisData: Map<String, Any>
)