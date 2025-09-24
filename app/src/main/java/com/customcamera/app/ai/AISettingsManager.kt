package com.customcamera.app.ai

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

/**
 * AI Settings and Configuration Manager
 *
 * Manages all AI-powered camera feature settings and preferences:
 * - Scene recognition configuration
 * - Object detection settings
 * - Composition guide preferences
 * - Image processing parameters
 * - Face detection and beautification settings
 * - Background blur configuration
 * - Text recognition options
 * - Performance and optimization settings
 */
class AISettingsManager(private val context: Context) {

    companion object {
        private const val TAG = "AISettingsManager"
        private const val PREFS_NAME = "ai_camera_settings"

        // Scene Recognition Settings Keys
        private const val KEY_SCENE_RECOGNITION_ENABLED = "scene_recognition_enabled"
        private const val KEY_AUTO_SCENE_SETTINGS = "auto_scene_settings"
        private const val KEY_SCENE_CONFIDENCE_THRESHOLD = "scene_confidence_threshold"

        // Object Detection Settings Keys
        private const val KEY_OBJECT_DETECTION_ENABLED = "object_detection_enabled"
        private const val KEY_OBJECT_TRACKING_ENABLED = "object_tracking_enabled"
        private const val KEY_MAX_OBJECTS_TO_DETECT = "max_objects_to_detect"

        // Composition Guide Settings Keys
        private const val KEY_COMPOSITION_GUIDE_ENABLED = "composition_guide_enabled"
        private const val KEY_RULE_OF_THIRDS_ENABLED = "rule_of_thirds_enabled"
        private const val KEY_GOLDEN_RATIO_ENABLED = "golden_ratio_enabled"
        private const val KEY_LEADING_LINES_ENABLED = "leading_lines_enabled"
        private const val KEY_SYMMETRY_GUIDE_ENABLED = "symmetry_guide_enabled"

        // Image Processing Settings Keys
        private const val KEY_AI_PROCESSING_ENABLED = "ai_processing_enabled"
        private const val KEY_AUTO_HDR_ENABLED = "auto_hdr_enabled"
        private const val KEY_NOISE_REDUCTION_LEVEL = "noise_reduction_level"
        private const val KEY_SHARPENING_LEVEL = "sharpening_level"
        private const val KEY_COLOR_ENHANCEMENT_LEVEL = "color_enhancement_level"

        // Face Detection Settings Keys
        private const val KEY_FACE_DETECTION_ENABLED = "face_detection_enabled"
        private const val KEY_BEAUTIFICATION_ENABLED = "beautification_enabled"
        private const val KEY_SKIN_SMOOTHING_LEVEL = "skin_smoothing_level"
        private const val KEY_EYE_ENHANCEMENT_LEVEL = "eye_enhancement_level"
        private const val KEY_TEETH_WHITENING_LEVEL = "teeth_whitening_level"

        // Background Blur Settings Keys
        private const val KEY_BACKGROUND_BLUR_ENABLED = "background_blur_enabled"
        private const val KEY_BLUR_INTENSITY = "blur_intensity"
        private const val KEY_BLUR_STYLE = "blur_style"
        private const val KEY_EDGE_REFINEMENT_ENABLED = "edge_refinement_enabled"

        // Text Recognition Settings Keys
        private const val KEY_TEXT_RECOGNITION_ENABLED = "text_recognition_enabled"
        private const val KEY_OCR_LANGUAGES = "ocr_languages"
        private const val KEY_DOCUMENT_MODE_ENABLED = "document_mode_enabled"
        private const val KEY_BARCODE_DETECTION_ENABLED = "barcode_detection_enabled"

        // Performance Settings Keys
        private const val KEY_AI_PROCESSING_QUALITY = "ai_processing_quality"
        private const val KEY_REAL_TIME_PROCESSING = "real_time_processing"
        private const val KEY_BATTERY_OPTIMIZATION = "battery_optimization"
        private const val KEY_THERMAL_MANAGEMENT = "thermal_management"
    }

    private val sharedPrefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * AI feature configuration container
     */
    data class AIConfiguration(
        // Scene Recognition
        val sceneRecognitionEnabled: Boolean = true,
        val autoSceneSettings: Boolean = true,
        val sceneConfidenceThreshold: Float = 0.6f,

        // Object Detection
        val objectDetectionEnabled: Boolean = false,
        val objectTrackingEnabled: Boolean = true,
        val maxObjectsToDetect: Int = 10,

        // Composition Guide
        val compositionGuideEnabled: Boolean = true,
        val ruleOfThirdsEnabled: Boolean = true,
        val goldenRatioEnabled: Boolean = false,
        val leadingLinesEnabled: Boolean = true,
        val symmetryGuideEnabled: Boolean = true,

        // Image Processing
        val aiProcessingEnabled: Boolean = true,
        val autoHDREnabled: Boolean = true,
        val noiseReductionLevel: Float = 0.5f,
        val sharpeningLevel: Float = 0.3f,
        val colorEnhancementLevel: Float = 0.4f,

        // Face Detection
        val faceDetectionEnabled: Boolean = true,
        val beautificationEnabled: Boolean = false,
        val skinSmoothingLevel: Float = 0.5f,
        val eyeEnhancementLevel: Float = 0.3f,
        val teethWhiteningLevel: Float = 0.4f,

        // Background Blur
        val backgroundBlurEnabled: Boolean = false,
        val blurIntensity: Float = 0.7f,
        val blurStyle: String = "GAUSSIAN",
        val edgeRefinementEnabled: Boolean = true,

        // Text Recognition
        val textRecognitionEnabled: Boolean = false,
        val ocrLanguages: Set<String> = setOf("en"),
        val documentModeEnabled: Boolean = false,
        val barcodeDetectionEnabled: Boolean = true,

        // Performance
        val aiProcessingQuality: String = "BALANCED",
        val realTimeProcessing: Boolean = true,
        val batteryOptimization: Boolean = true,
        val thermalManagement: Boolean = true
    )

    /**
     * Performance preset configurations
     */
    enum class PerformancePreset {
        BATTERY_SAVER,      // Minimal AI features, optimized for battery
        BALANCED,           // Balanced AI features and performance
        PERFORMANCE,        // Full AI features, optimized for speed
        QUALITY             // Maximum AI features and quality
    }

    /**
     * Initialize AI settings with defaults
     */
    fun initialize() {
        Log.d(TAG, "AI settings manager initialized")
    }

    /**
     * Get current AI configuration
     */
    fun getCurrentConfiguration(): AIConfiguration {
        return AIConfiguration(
            // Scene Recognition
            sceneRecognitionEnabled = sharedPrefs.getBoolean(KEY_SCENE_RECOGNITION_ENABLED, true),
            autoSceneSettings = sharedPrefs.getBoolean(KEY_AUTO_SCENE_SETTINGS, true),
            sceneConfidenceThreshold = sharedPrefs.getFloat(KEY_SCENE_CONFIDENCE_THRESHOLD, 0.6f),

            // Object Detection
            objectDetectionEnabled = sharedPrefs.getBoolean(KEY_OBJECT_DETECTION_ENABLED, false),
            objectTrackingEnabled = sharedPrefs.getBoolean(KEY_OBJECT_TRACKING_ENABLED, true),
            maxObjectsToDetect = sharedPrefs.getInt(KEY_MAX_OBJECTS_TO_DETECT, 10),

            // Composition Guide
            compositionGuideEnabled = sharedPrefs.getBoolean(KEY_COMPOSITION_GUIDE_ENABLED, true),
            ruleOfThirdsEnabled = sharedPrefs.getBoolean(KEY_RULE_OF_THIRDS_ENABLED, true),
            goldenRatioEnabled = sharedPrefs.getBoolean(KEY_GOLDEN_RATIO_ENABLED, false),
            leadingLinesEnabled = sharedPrefs.getBoolean(KEY_LEADING_LINES_ENABLED, true),
            symmetryGuideEnabled = sharedPrefs.getBoolean(KEY_SYMMETRY_GUIDE_ENABLED, true),

            // Image Processing
            aiProcessingEnabled = sharedPrefs.getBoolean(KEY_AI_PROCESSING_ENABLED, true),
            autoHDREnabled = sharedPrefs.getBoolean(KEY_AUTO_HDR_ENABLED, true),
            noiseReductionLevel = sharedPrefs.getFloat(KEY_NOISE_REDUCTION_LEVEL, 0.5f),
            sharpeningLevel = sharedPrefs.getFloat(KEY_SHARPENING_LEVEL, 0.3f),
            colorEnhancementLevel = sharedPrefs.getFloat(KEY_COLOR_ENHANCEMENT_LEVEL, 0.4f),

            // Face Detection
            faceDetectionEnabled = sharedPrefs.getBoolean(KEY_FACE_DETECTION_ENABLED, true),
            beautificationEnabled = sharedPrefs.getBoolean(KEY_BEAUTIFICATION_ENABLED, false),
            skinSmoothingLevel = sharedPrefs.getFloat(KEY_SKIN_SMOOTHING_LEVEL, 0.5f),
            eyeEnhancementLevel = sharedPrefs.getFloat(KEY_EYE_ENHANCEMENT_LEVEL, 0.3f),
            teethWhiteningLevel = sharedPrefs.getFloat(KEY_TEETH_WHITENING_LEVEL, 0.4f),

            // Background Blur
            backgroundBlurEnabled = sharedPrefs.getBoolean(KEY_BACKGROUND_BLUR_ENABLED, false),
            blurIntensity = sharedPrefs.getFloat(KEY_BLUR_INTENSITY, 0.7f),
            blurStyle = sharedPrefs.getString(KEY_BLUR_STYLE, "GAUSSIAN") ?: "GAUSSIAN",
            edgeRefinementEnabled = sharedPrefs.getBoolean(KEY_EDGE_REFINEMENT_ENABLED, true),

            // Text Recognition
            textRecognitionEnabled = sharedPrefs.getBoolean(KEY_TEXT_RECOGNITION_ENABLED, false),
            ocrLanguages = sharedPrefs.getStringSet(KEY_OCR_LANGUAGES, setOf("en")) ?: setOf("en"),
            documentModeEnabled = sharedPrefs.getBoolean(KEY_DOCUMENT_MODE_ENABLED, false),
            barcodeDetectionEnabled = sharedPrefs.getBoolean(KEY_BARCODE_DETECTION_ENABLED, true),

            // Performance
            aiProcessingQuality = sharedPrefs.getString(KEY_AI_PROCESSING_QUALITY, "BALANCED") ?: "BALANCED",
            realTimeProcessing = sharedPrefs.getBoolean(KEY_REAL_TIME_PROCESSING, true),
            batteryOptimization = sharedPrefs.getBoolean(KEY_BATTERY_OPTIMIZATION, true),
            thermalManagement = sharedPrefs.getBoolean(KEY_THERMAL_MANAGEMENT, true)
        )
    }

    /**
     * Save AI configuration
     */
    fun saveConfiguration(config: AIConfiguration) {
        sharedPrefs.edit().apply {
            // Scene Recognition
            putBoolean(KEY_SCENE_RECOGNITION_ENABLED, config.sceneRecognitionEnabled)
            putBoolean(KEY_AUTO_SCENE_SETTINGS, config.autoSceneSettings)
            putFloat(KEY_SCENE_CONFIDENCE_THRESHOLD, config.sceneConfidenceThreshold)

            // Object Detection
            putBoolean(KEY_OBJECT_DETECTION_ENABLED, config.objectDetectionEnabled)
            putBoolean(KEY_OBJECT_TRACKING_ENABLED, config.objectTrackingEnabled)
            putInt(KEY_MAX_OBJECTS_TO_DETECT, config.maxObjectsToDetect)

            // Composition Guide
            putBoolean(KEY_COMPOSITION_GUIDE_ENABLED, config.compositionGuideEnabled)
            putBoolean(KEY_RULE_OF_THIRDS_ENABLED, config.ruleOfThirdsEnabled)
            putBoolean(KEY_GOLDEN_RATIO_ENABLED, config.goldenRatioEnabled)
            putBoolean(KEY_LEADING_LINES_ENABLED, config.leadingLinesEnabled)
            putBoolean(KEY_SYMMETRY_GUIDE_ENABLED, config.symmetryGuideEnabled)

            // Image Processing
            putBoolean(KEY_AI_PROCESSING_ENABLED, config.aiProcessingEnabled)
            putBoolean(KEY_AUTO_HDR_ENABLED, config.autoHDREnabled)
            putFloat(KEY_NOISE_REDUCTION_LEVEL, config.noiseReductionLevel)
            putFloat(KEY_SHARPENING_LEVEL, config.sharpeningLevel)
            putFloat(KEY_COLOR_ENHANCEMENT_LEVEL, config.colorEnhancementLevel)

            // Face Detection
            putBoolean(KEY_FACE_DETECTION_ENABLED, config.faceDetectionEnabled)
            putBoolean(KEY_BEAUTIFICATION_ENABLED, config.beautificationEnabled)
            putFloat(KEY_SKIN_SMOOTHING_LEVEL, config.skinSmoothingLevel)
            putFloat(KEY_EYE_ENHANCEMENT_LEVEL, config.eyeEnhancementLevel)
            putFloat(KEY_TEETH_WHITENING_LEVEL, config.teethWhiteningLevel)

            // Background Blur
            putBoolean(KEY_BACKGROUND_BLUR_ENABLED, config.backgroundBlurEnabled)
            putFloat(KEY_BLUR_INTENSITY, config.blurIntensity)
            putString(KEY_BLUR_STYLE, config.blurStyle)
            putBoolean(KEY_EDGE_REFINEMENT_ENABLED, config.edgeRefinementEnabled)

            // Text Recognition
            putBoolean(KEY_TEXT_RECOGNITION_ENABLED, config.textRecognitionEnabled)
            putStringSet(KEY_OCR_LANGUAGES, config.ocrLanguages)
            putBoolean(KEY_DOCUMENT_MODE_ENABLED, config.documentModeEnabled)
            putBoolean(KEY_BARCODE_DETECTION_ENABLED, config.barcodeDetectionEnabled)

            // Performance
            putString(KEY_AI_PROCESSING_QUALITY, config.aiProcessingQuality)
            putBoolean(KEY_REAL_TIME_PROCESSING, config.realTimeProcessing)
            putBoolean(KEY_BATTERY_OPTIMIZATION, config.batteryOptimization)
            putBoolean(KEY_THERMAL_MANAGEMENT, config.thermalManagement)

            apply()
        }

        Log.d(TAG, "AI configuration saved")
    }

    /**
     * Apply performance preset
     */
    fun applyPerformancePreset(preset: PerformancePreset) {
        val config = when (preset) {
            PerformancePreset.BATTERY_SAVER -> AIConfiguration(
                sceneRecognitionEnabled = false,
                objectDetectionEnabled = false,
                compositionGuideEnabled = false,
                aiProcessingEnabled = false,
                faceDetectionEnabled = false,
                backgroundBlurEnabled = false,
                textRecognitionEnabled = false,
                realTimeProcessing = false,
                batteryOptimization = true,
                thermalManagement = true,
                aiProcessingQuality = "FAST"
            )

            PerformancePreset.BALANCED -> AIConfiguration(
                sceneRecognitionEnabled = true,
                objectDetectionEnabled = false,
                compositionGuideEnabled = true,
                aiProcessingEnabled = true,
                faceDetectionEnabled = true,
                backgroundBlurEnabled = false,
                textRecognitionEnabled = false,
                noiseReductionLevel = 0.3f,
                sharpeningLevel = 0.2f,
                colorEnhancementLevel = 0.3f,
                realTimeProcessing = true,
                batteryOptimization = true,
                aiProcessingQuality = "BALANCED"
            )

            PerformancePreset.PERFORMANCE -> AIConfiguration(
                sceneRecognitionEnabled = true,
                objectDetectionEnabled = true,
                compositionGuideEnabled = true,
                aiProcessingEnabled = true,
                faceDetectionEnabled = true,
                backgroundBlurEnabled = true,
                textRecognitionEnabled = false,
                noiseReductionLevel = 0.5f,
                sharpeningLevel = 0.4f,
                colorEnhancementLevel = 0.5f,
                realTimeProcessing = true,
                batteryOptimization = false,
                aiProcessingQuality = "QUALITY"
            )

            PerformancePreset.QUALITY -> AIConfiguration(
                sceneRecognitionEnabled = true,
                objectDetectionEnabled = true,
                compositionGuideEnabled = true,
                aiProcessingEnabled = true,
                faceDetectionEnabled = true,
                backgroundBlurEnabled = true,
                textRecognitionEnabled = true,
                noiseReductionLevel = 0.7f,
                sharpeningLevel = 0.5f,
                colorEnhancementLevel = 0.6f,
                skinSmoothingLevel = 0.6f,
                eyeEnhancementLevel = 0.4f,
                realTimeProcessing = false,
                batteryOptimization = false,
                aiProcessingQuality = "MAXIMUM"
            )
        }

        saveConfiguration(config)
        Log.d(TAG, "Applied performance preset: $preset")
    }

    /**
     * Reset to default settings
     */
    fun resetToDefaults() {
        sharedPrefs.edit().clear().apply()
        Log.d(TAG, "AI settings reset to defaults")
    }

    /**
     * Get scene recognition configuration
     */
    fun getSceneRecognitionConfig(): SceneRecognitionConfig {
        val config = getCurrentConfiguration()
        return SceneRecognitionConfig(
            enabled = config.sceneRecognitionEnabled,
            autoApplySettings = config.autoSceneSettings,
            confidenceThreshold = config.sceneConfidenceThreshold
        )
    }

    /**
     * Get object detection configuration
     */
    fun getObjectDetectionConfig(): Unit {
        val config = getCurrentConfiguration()
        // Object detection config would be applied to AIObjectDetectionManager
        Log.d(TAG, "Object detection config: enabled=${config.objectDetectionEnabled}")
    }

    /**
     * Get composition guide configuration
     */
    fun getCompositionGuideConfig(): Set<AICompositionGuideManager.CompositionRule> {
        val config = getCurrentConfiguration()
        val enabledRules = mutableSetOf<AICompositionGuideManager.CompositionRule>()

        if (config.ruleOfThirdsEnabled) {
            enabledRules.add(AICompositionGuideManager.CompositionRule.RULE_OF_THIRDS)
        }
        if (config.goldenRatioEnabled) {
            enabledRules.add(AICompositionGuideManager.CompositionRule.GOLDEN_RATIO)
        }
        if (config.leadingLinesEnabled) {
            enabledRules.add(AICompositionGuideManager.CompositionRule.LEADING_LINES)
        }
        if (config.symmetryGuideEnabled) {
            enabledRules.add(AICompositionGuideManager.CompositionRule.SYMMETRY)
        }

        return enabledRules
    }

    /**
     * Get image processing configuration
     */
    fun getImageProcessingConfig(): AIImageProcessingManager.ProcessingConfig {
        val config = getCurrentConfiguration()
        val qualityPreset = when (config.aiProcessingQuality) {
            "FAST" -> AIImageProcessingManager.QualityPreset.FAST
            "BALANCED" -> AIImageProcessingManager.QualityPreset.BALANCED
            "QUALITY" -> AIImageProcessingManager.QualityPreset.QUALITY
            "MAXIMUM" -> AIImageProcessingManager.QualityPreset.MAXIMUM
            else -> AIImageProcessingManager.QualityPreset.BALANCED
        }

        return AIImageProcessingManager.ProcessingConfig(
            mode = AIImageProcessingManager.ProcessingMode.AUTO,
            noiseReduction = config.noiseReductionLevel,
            sharpening = config.sharpeningLevel,
            colorEnhancement = config.colorEnhancementLevel,
            enableHDR = config.autoHDREnabled,
            qualityPreset = qualityPreset
        )
    }

    /**
     * Get face detection configuration
     */
    fun getFaceDetectionConfig(): AIFaceDetectionManager.BeautificationConfig {
        val config = getCurrentConfiguration()
        return AIFaceDetectionManager.BeautificationConfig(
            skinSmoothing = if (config.beautificationEnabled) config.skinSmoothingLevel else 0f,
            eyeEnhancement = if (config.beautificationEnabled) config.eyeEnhancementLevel else 0f,
            teethWhitening = if (config.beautificationEnabled) config.teethWhiteningLevel else 0f,
            enableAutoBeautification = config.beautificationEnabled
        )
    }

    /**
     * Get background blur configuration
     */
    fun getBackgroundBlurConfig(): AIBackgroundBlurManager.BlurConfig {
        val config = getCurrentConfiguration()
        val blurStyle = try {
            AIBackgroundBlurManager.BlurStyle.valueOf(config.blurStyle)
        } catch (e: IllegalArgumentException) {
            AIBackgroundBlurManager.BlurStyle.GAUSSIAN
        }

        return AIBackgroundBlurManager.BlurConfig(
            mode = if (config.backgroundBlurEnabled) AIBackgroundBlurManager.BlurMode.PORTRAIT else AIBackgroundBlurManager.BlurMode.OFF,
            style = blurStyle,
            intensity = config.blurIntensity,
            edgeRefinement = config.edgeRefinementEnabled
        )
    }

    /**
     * Get text recognition configuration
     */
    fun getTextRecognitionConfig(): AITextRecognitionManager.RecognitionConfig {
        val config = getCurrentConfiguration()
        val recognitionMode = when (config.aiProcessingQuality) {
            "FAST" -> AITextRecognitionManager.RecognitionMode.FAST
            "BALANCED" -> AITextRecognitionManager.RecognitionMode.ACCURATE
            "QUALITY", "MAXIMUM" -> AITextRecognitionManager.RecognitionMode.PRECISE
            else -> AITextRecognitionManager.RecognitionMode.ACCURATE
        }

        return AITextRecognitionManager.RecognitionConfig(
            languages = config.ocrLanguages.toList(),
            recognitionMode = recognitionMode,
            enableRealTime = config.realTimeProcessing,
            enableBarcodes = config.barcodeDetectionEnabled,
            documentMode = config.documentModeEnabled
        )
    }

    /**
     * Check if any AI features are enabled
     */
    fun hasEnabledAIFeatures(): Boolean {
        val config = getCurrentConfiguration()
        return config.sceneRecognitionEnabled ||
               config.objectDetectionEnabled ||
               config.compositionGuideEnabled ||
               config.aiProcessingEnabled ||
               config.faceDetectionEnabled ||
               config.backgroundBlurEnabled ||
               config.textRecognitionEnabled
    }

    /**
     * Get feature summary for display
     */
    fun getFeatureSummary(): Map<String, Boolean> {
        val config = getCurrentConfiguration()
        return mapOf(
            "Scene Recognition" to config.sceneRecognitionEnabled,
            "Object Detection" to config.objectDetectionEnabled,
            "Composition Guide" to config.compositionGuideEnabled,
            "AI Processing" to config.aiProcessingEnabled,
            "Face Detection" to config.faceDetectionEnabled,
            "Background Blur" to config.backgroundBlurEnabled,
            "Text Recognition" to config.textRecognitionEnabled
        )
    }

    /**
     * Export settings to string (for backup)
     */
    fun exportSettings(): String {
        val config = getCurrentConfiguration()
        return "AI_SETTINGS_V1:${config.toString()}"
    }

    /**
     * Import settings from string (from backup)
     */
    fun importSettings(settingsString: String): Boolean {
        return try {
            if (settingsString.startsWith("AI_SETTINGS_V1:")) {
                // In a real implementation, would parse the settings string
                Log.d(TAG, "Settings import would be implemented here")
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import settings", e)
            false
        }
    }

    /**
     * AI Settings Summary for display
     */
    data class AISettingsSummary(
        val totalFeatures: Int,
        val enabledFeatures: Int,
        val performanceLevel: String,
        val batteryImpact: String, // LOW, MEDIUM, HIGH
        val processingQuality: String
    )

    /**
     * Get settings summary
     */
    fun getSettingsSummary(): AISettingsSummary {
        val config = getCurrentConfiguration()
        val featureMap = getFeatureSummary()
        val enabledCount = featureMap.values.count { it }

        val batteryImpact = when {
            !config.batteryOptimization && enabledCount > 4 -> "HIGH"
            enabledCount > 2 -> "MEDIUM"
            else -> "LOW"
        }

        return AISettingsSummary(
            totalFeatures = featureMap.size,
            enabledFeatures = enabledCount,
            performanceLevel = config.aiProcessingQuality,
            batteryImpact = batteryImpact,
            processingQuality = config.aiProcessingQuality
        )
    }

    /**
     * Clean up resources
     */
    fun cleanup() {
        Log.d(TAG, "AI settings manager cleaned up")
    }

    // Define a simplified SceneRecognitionConfig for compatibility
    data class SceneRecognitionConfig(
        val enabled: Boolean,
        val autoApplySettings: Boolean,
        val confidenceThreshold: Float
    )
}