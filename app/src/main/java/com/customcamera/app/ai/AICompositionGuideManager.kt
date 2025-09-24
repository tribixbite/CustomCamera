package com.customcamera.app.ai

import android.content.Context
import android.graphics.*
import android.util.Log
import androidx.camera.core.ImageProxy
import kotlinx.coroutines.*
import kotlin.math.*

/**
 * AI Composition Guide Manager
 *
 * Provides intelligent composition guidance and photography assistance:
 * - Rule of thirds analysis and guidance
 * - Leading lines detection and suggestions
 * - Symmetry and balance analysis
 * - Depth of field recommendations
 * - Framing and cropping suggestions
 * - Subject positioning guidance
 * - Real-time composition scoring
 * - Professional photography tips
 */
class AICompositionGuideManager(private val context: Context) {

    companion object {
        private const val TAG = "AICompositionGuide"
        private const val ANALYSIS_INTERVAL_MS = 400L
        private const val MIN_GUIDANCE_CONFIDENCE = 0.4f
        private const val GOLDEN_RATIO = 1.618f
    }

    /**
     * Composition rules and guidelines
     */
    enum class CompositionRule {
        RULE_OF_THIRDS,          // Classic 1/3 positioning
        GOLDEN_RATIO,            // Golden ratio spiral and rectangles
        LEADING_LINES,           // Lines that guide the eye
        SYMMETRY,                // Horizontal, vertical, radial symmetry
        FRAMING,                 // Natural frames within the scene
        PATTERNS,                // Repeating elements and textures
        DEPTH_OF_FIELD,          // Foreground/background separation
        FILL_FRAME,              // Subject fills the entire frame
        NEGATIVE_SPACE,          // Empty space around subject
        COLOR_HARMONY,           // Complementary color relationships
        CENTER_COMPOSITION,      // Centered subject placement
        DIAGONAL_COMPOSITION     // Dynamic diagonal arrangements
    }

    /**
     * Guidance suggestion types
     */
    enum class GuidanceType {
        MOVE_SUBJECT,            // Reposition the main subject
        ADJUST_ANGLE,            // Change camera angle or tilt
        CHANGE_DISTANCE,         // Move closer or further away
        WAIT_FOR_MOMENT,         // Wait for better timing
        IMPROVE_LIGHTING,        // Better lighting position
        SIMPLIFY_BACKGROUND,     // Reduce background clutter
        ADD_FOREGROUND,          // Include foreground elements
        USE_SYMMETRY,            // Take advantage of symmetry
        FOLLOW_LINES,            // Align with leading lines
        FILL_FRAME_MORE,         // Get closer to subject
        CREATE_DEPTH,            // Add layers to the composition
        BALANCE_ELEMENTS         // Balance visual weight
    }

    /**
     * Composition analysis result
     */
    data class CompositionAnalysis(
        val overallScore: Float,                                    // 0.0 to 1.0
        val appliedRules: List<Pair<CompositionRule, Float>>,      // Rules and their scores
        val suggestions: List<CompositionSuggestion>,
        val subjectPosition: PointF?,                              // Detected main subject
        val interestPoints: List<PointF>,                          // Points of visual interest
        val leadingLines: List<LineSegment>,                       // Detected leading lines
        val symmetryAxis: PointF?,                                 // Axis of symmetry if found
        val dominantColors: List<Pair<Int, Float>>,               // Color and prevalence
        val visualWeight: CompositionWeight,                       // Distribution of visual elements
        val timestamp: Long = System.currentTimeMillis()
    )

    /**
     * Specific composition suggestion
     */
    data class CompositionSuggestion(
        val type: GuidanceType,
        val confidence: Float,
        val description: String,
        val targetPosition: PointF?,                               // Where to position subject/camera
        val visualIndicator: GuideOverlay?                         // Visual guide to show
    )

    /**
     * Line segment for leading lines
     */
    data class LineSegment(
        val start: PointF,
        val end: PointF,
        val strength: Float,                                       // How prominent the line is
        val angle: Float                                           // Angle in degrees
    )

    /**
     * Visual weight distribution
     */
    data class CompositionWeight(
        val topLeft: Float,
        val topRight: Float,
        val bottomLeft: Float,
        val bottomRight: Float,
        val center: Float
    )

    /**
     * Visual guide overlay information
     */
    data class GuideOverlay(
        val type: OverlayType,
        val positions: List<PointF>,
        val lines: List<LineSegment>,
        val color: Int = Color.YELLOW,
        val alpha: Float = 0.7f
    )

    enum class OverlayType {
        RULE_OF_THIRDS_GRID,
        GOLDEN_RATIO_SPIRAL,
        LEADING_LINE_GUIDES,
        SYMMETRY_AXIS,
        SUBJECT_POSITION_TARGET,
        BALANCE_INDICATORS
    }

    private var isAnalysisActive = false
    private var analysisJob: Job? = null
    private var lastAnalysis: CompositionAnalysis? = null
    private var currentOverlays = mutableListOf<GuideOverlay>()

    // Analysis settings
    private var enabledRules = setOf(
        CompositionRule.RULE_OF_THIRDS,
        CompositionRule.LEADING_LINES,
        CompositionRule.SYMMETRY,
        CompositionRule.NEGATIVE_SPACE
    )

    /**
     * Initialize composition guide system
     */
    fun initialize(): Boolean {
        return try {
            Log.d(TAG, "AI composition guide system initialized")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize composition guide", e)
            false
        }
    }

    /**
     * Start composition analysis
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

        Log.d(TAG, "Started composition analysis")
    }

    /**
     * Stop composition analysis
     */
    fun stopAnalysis() {
        isAnalysisActive = false
        analysisJob?.cancel()
        analysisJob = null
        currentOverlays.clear()
        lastAnalysis = null

        Log.d(TAG, "Stopped composition analysis")
    }

    /**
     * Analyze frame composition
     */
    suspend fun analyzeComposition(imageProxy: ImageProxy): CompositionAnalysis = withContext(Dispatchers.Default) {
        if (!isAnalysisActive) {
            return@withContext createDefaultAnalysis()
        }

        try {
            // Convert ImageProxy to analyzable format
            val bitmap = convertImageProxyToBitmap(imageProxy)

            // Perform comprehensive composition analysis
            val analysis = performCompositionAnalysis(bitmap, imageProxy.width, imageProxy.height)

            // Update overlays based on analysis
            updateGuideOverlays(analysis)

            lastAnalysis = analysis
            analysis

        } catch (e: Exception) {
            Log.e(TAG, "Error during composition analysis", e)
            createDefaultAnalysis()
        }
    }

    /**
     * Get current composition analysis
     */
    fun getCurrentAnalysis(): CompositionAnalysis? = lastAnalysis

    /**
     * Get current guide overlays for UI display
     */
    fun getCurrentOverlays(): List<GuideOverlay> = currentOverlays.toList()

    /**
     * Get composition score for current frame
     */
    fun getCurrentCompositionScore(): Float = lastAnalysis?.overallScore ?: 0.5f

    /**
     * Get active composition suggestions
     */
    fun getActiveSuggestions(): List<CompositionSuggestion> {
        return lastAnalysis?.suggestions?.filter { it.confidence >= MIN_GUIDANCE_CONFIDENCE } ?: emptyList()
    }

    /**
     * Update enabled composition rules
     */
    fun updateEnabledRules(rules: Set<CompositionRule>) {
        enabledRules = rules
        Log.d(TAG, "Updated enabled rules: ${rules.size} rules active")
    }

    /**
     * Check if composition guidance should be shown
     */
    fun shouldShowGuidance(): Boolean {
        val suggestions = getActiveSuggestions()
        return suggestions.isNotEmpty() && getCurrentCompositionScore() < 0.7f
    }

    // Private analysis methods

    private fun convertImageProxyToBitmap(imageProxy: ImageProxy): Bitmap {
        val buffer = imageProxy.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)

        // Create downscaled bitmap for composition analysis
        val options = BitmapFactory.Options().apply {
            inSampleSize = 3  // Reduce to 1/3 size for faster processing
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            ?: createPlaceholderBitmap()
    }

    private fun createPlaceholderBitmap(): Bitmap {
        return Bitmap.createBitmap(300, 200, Bitmap.Config.ARGB_8888)
    }

    private suspend fun performCompositionAnalysis(
        bitmap: Bitmap,
        originalWidth: Int,
        originalHeight: Int
    ): CompositionAnalysis = withContext(Dispatchers.Default) {

        // Detect key composition elements
        val subjectPosition = detectMainSubject(bitmap, originalWidth, originalHeight)
        val interestPoints = detectInterestPoints(bitmap, originalWidth, originalHeight)
        val leadingLines = detectLeadingLines(bitmap, originalWidth, originalHeight)
        val symmetryAxis = detectSymmetryAxis(bitmap, originalWidth, originalHeight)
        val dominantColors = analyzeDominantColors(bitmap)
        val visualWeight = calculateVisualWeight(bitmap)

        // Analyze each enabled composition rule
        val ruleScores = mutableListOf<Pair<CompositionRule, Float>>()
        val suggestions = mutableListOf<CompositionSuggestion>()

        if (CompositionRule.RULE_OF_THIRDS in enabledRules) {
            val score = analyzeRuleOfThirds(subjectPosition, interestPoints, originalWidth, originalHeight)
            ruleScores.add(CompositionRule.RULE_OF_THIRDS to score)

            if (score < 0.6f) {
                suggestions.add(createRuleOfThirdsSuggestion(subjectPosition, originalWidth, originalHeight))
            }
        }

        if (CompositionRule.GOLDEN_RATIO in enabledRules) {
            val score = analyzeGoldenRatio(subjectPosition, interestPoints, originalWidth, originalHeight)
            ruleScores.add(CompositionRule.GOLDEN_RATIO to score)
        }

        if (CompositionRule.LEADING_LINES in enabledRules) {
            val score = analyzeLeadingLines(leadingLines, subjectPosition, originalWidth, originalHeight)
            ruleScores.add(CompositionRule.LEADING_LINES to score)

            if (score > 0.4f && subjectPosition != null) {
                suggestions.add(createLeadingLinesSuggestion(leadingLines, subjectPosition))
            }
        }

        if (CompositionRule.SYMMETRY in enabledRules) {
            val score = analyzeSymmetry(symmetryAxis, visualWeight)
            ruleScores.add(CompositionRule.SYMMETRY to score)

            if (score > 0.5f) {
                suggestions.add(createSymmetrySuggestion(symmetryAxis))
            }
        }

        if (CompositionRule.NEGATIVE_SPACE in enabledRules) {
            val score = analyzeNegativeSpace(bitmap, subjectPosition)
            ruleScores.add(CompositionRule.NEGATIVE_SPACE to score)
        }

        if (CompositionRule.COLOR_HARMONY in enabledRules) {
            val score = analyzeColorHarmony(dominantColors)
            ruleScores.add(CompositionRule.COLOR_HARMONY to score)
        }

        // Calculate overall composition score
        val overallScore = calculateOverallScore(ruleScores)

        // Add general suggestions based on overall score
        if (overallScore < 0.5f) {
            suggestions.addAll(createGeneralImprovementSuggestions(subjectPosition, visualWeight))
        }

        CompositionAnalysis(
            overallScore = overallScore,
            appliedRules = ruleScores,
            suggestions = suggestions.filter { it.confidence >= MIN_GUIDANCE_CONFIDENCE },
            subjectPosition = subjectPosition,
            interestPoints = interestPoints,
            leadingLines = leadingLines,
            symmetryAxis = symmetryAxis,
            dominantColors = dominantColors,
            visualWeight = visualWeight
        )
    }

    private fun detectMainSubject(bitmap: Bitmap, originalWidth: Int, originalHeight: Int): PointF? {
        // Simplified subject detection using center-weighted approach
        // Real implementation would use object detection or saliency mapping

        val centerX = originalWidth / 2f
        val centerY = originalHeight / 2f

        // For now, assume subject is roughly in center but could be improved
        return PointF(centerX, centerY)
    }

    private fun detectInterestPoints(bitmap: Bitmap, originalWidth: Int, originalHeight: Int): List<PointF> {
        // Detect points of visual interest using corner detection or edge analysis
        val interestPoints = mutableListOf<PointF>()

        // Simple implementation: add rule of thirds intersection points as potential interest areas
        val thirdWidth = originalWidth / 3f
        val thirdHeight = originalHeight / 3f

        interestPoints.add(PointF(thirdWidth, thirdHeight))
        interestPoints.add(PointF(thirdWidth * 2, thirdHeight))
        interestPoints.add(PointF(thirdWidth, thirdHeight * 2))
        interestPoints.add(PointF(thirdWidth * 2, thirdHeight * 2))

        return interestPoints
    }

    private fun detectLeadingLines(bitmap: Bitmap, originalWidth: Int, originalHeight: Int): List<LineSegment> {
        // Simplified line detection using edge detection
        // Real implementation would use Hough transform or similar

        val lines = mutableListOf<LineSegment>()

        // Mock some potential leading lines
        lines.add(LineSegment(
            start = PointF(0f, originalHeight * 0.8f),
            end = PointF(originalWidth * 0.6f, originalHeight * 0.3f),
            strength = 0.6f,
            angle = -35f
        ))

        return lines
    }

    private fun detectSymmetryAxis(bitmap: Bitmap, originalWidth: Int, originalHeight: Int): PointF? {
        // Detect potential axis of symmetry
        // Real implementation would analyze pixel patterns

        // Check for vertical symmetry (common in architecture/portraits)
        val centerX = originalWidth / 2f
        val centerY = originalHeight / 2f

        return PointF(centerX, centerY) // Placeholder
    }

    private fun analyzeDominantColors(bitmap: Bitmap): List<Pair<Int, Float>> {
        // Analyze dominant colors in the image
        val colorCounts = mutableMapOf<Int, Int>()
        val totalPixels = bitmap.width * bitmap.height

        // Sample pixels to analyze colors (simplified)
        for (y in 0 until bitmap.height step 10) {
            for (x in 0 until bitmap.width step 10) {
                val pixel = bitmap.getPixel(x, y)
                val simplifiedColor = simplifyColor(pixel)
                colorCounts[simplifiedColor] = colorCounts.getOrDefault(simplifiedColor, 0) + 1
            }
        }

        return colorCounts.entries
            .sortedByDescending { it.value }
            .take(5)
            .map { it.key to (it.value.toFloat() / totalPixels) }
    }

    private fun calculateVisualWeight(bitmap: Bitmap): CompositionWeight {
        // Calculate visual weight in different regions
        val width = bitmap.width
        val height = bitmap.height

        val topLeft = calculateRegionWeight(bitmap, 0, 0, width/2, height/2)
        val topRight = calculateRegionWeight(bitmap, width/2, 0, width, height/2)
        val bottomLeft = calculateRegionWeight(bitmap, 0, height/2, width/2, height)
        val bottomRight = calculateRegionWeight(bitmap, width/2, height/2, width, height)
        val center = calculateRegionWeight(bitmap, width/4, height/4, 3*width/4, 3*height/4)

        return CompositionWeight(topLeft, topRight, bottomLeft, bottomRight, center)
    }

    private fun calculateRegionWeight(bitmap: Bitmap, x1: Int, y1: Int, x2: Int, y2: Int): Float {
        var totalWeight = 0f
        var pixelCount = 0

        for (y in y1 until y2) {
            for (x in x1 until x2) {
                if (x < bitmap.width && y < bitmap.height) {
                    val pixel = bitmap.getPixel(x, y)
                    val brightness = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3f / 255f
                    totalWeight += brightness
                    pixelCount++
                }
            }
        }

        return if (pixelCount > 0) totalWeight / pixelCount else 0f
    }

    private fun analyzeRuleOfThirds(subjectPosition: PointF?, interestPoints: List<PointF>, width: Int, height: Int): Float {
        if (subjectPosition == null) return 0.5f

        // Calculate distance to nearest third intersection
        val thirdWidth = width / 3f
        val thirdHeight = height / 3f

        val thirdPoints = listOf(
            PointF(thirdWidth, thirdHeight),
            PointF(thirdWidth * 2, thirdHeight),
            PointF(thirdWidth, thirdHeight * 2),
            PointF(thirdWidth * 2, thirdHeight * 2)
        )

        val minDistance = thirdPoints.minOfOrNull { point ->
            val dx = subjectPosition.x - point.x
            val dy = subjectPosition.y - point.y
            sqrt(dx * dx + dy * dy)
        } ?: Float.MAX_VALUE

        // Convert distance to score (closer is better)
        val maxDistance = sqrt((width * width + height * height).toFloat()) / 4f
        return 1f - (minDistance / maxDistance).coerceAtMost(1f)
    }

    private fun analyzeGoldenRatio(subjectPosition: PointF?, interestPoints: List<PointF>, width: Int, height: Int): Float {
        if (subjectPosition == null) return 0.5f

        // Golden ratio points
        val goldenX1 = width / GOLDEN_RATIO
        val goldenX2 = width - goldenX1
        val goldenY1 = height / GOLDEN_RATIO
        val goldenY2 = height - goldenY1

        val goldenPoints = listOf(
            PointF(goldenX1, goldenY1),
            PointF(goldenX2, goldenY1),
            PointF(goldenX1, goldenY2),
            PointF(goldenX2, goldenY2)
        )

        val minDistance = goldenPoints.minOfOrNull { point ->
            val dx = subjectPosition.x - point.x
            val dy = subjectPosition.y - point.y
            sqrt(dx * dx + dy * dy)
        } ?: Float.MAX_VALUE

        val maxDistance = sqrt((width * width + height * height).toFloat()) / 4f
        return 1f - (minDistance / maxDistance).coerceAtMost(1f)
    }

    private fun analyzeLeadingLines(lines: List<LineSegment>, subjectPosition: PointF?, width: Int, height: Int): Float {
        if (lines.isEmpty() || subjectPosition == null) return 0.0f

        // Check if leading lines point toward subject
        var totalScore = 0f

        for (line in lines) {
            val lineAngle = atan2(line.end.y - line.start.y, line.end.x - line.start.x)
            val subjectAngle = atan2(subjectPosition.y - line.start.y, subjectPosition.x - line.start.x)

            val angleDifference = abs(lineAngle - subjectAngle)
            val score = (1f - (angleDifference / PI).toFloat()) * line.strength
            totalScore += score
        }

        return (totalScore / lines.size).coerceAtMost(1f)
    }

    private fun analyzeSymmetry(symmetryAxis: PointF?, visualWeight: CompositionWeight): Float {
        if (symmetryAxis == null) return 0.3f

        // Check balance of visual weight
        val horizontalBalance = abs(visualWeight.topLeft + visualWeight.bottomLeft - visualWeight.topRight - visualWeight.bottomRight)
        val verticalBalance = abs(visualWeight.topLeft + visualWeight.topRight - visualWeight.bottomLeft - visualWeight.bottomRight)

        return (1f - horizontalBalance).coerceAtMost(1f) * 0.5f + (1f - verticalBalance).coerceAtMost(1f) * 0.5f
    }

    private fun analyzeNegativeSpace(bitmap: Bitmap, subjectPosition: PointF?): Float {
        if (subjectPosition == null) return 0.5f

        // Simplified negative space analysis
        // Real implementation would analyze empty areas around subject
        return 0.6f // Placeholder score
    }

    private fun analyzeColorHarmony(dominantColors: List<Pair<Int, Float>>): Float {
        if (dominantColors.size < 2) return 0.5f

        // Simplified color harmony analysis
        // Real implementation would check complementary, analogous, or triadic color schemes
        return 0.7f // Placeholder score
    }

    private fun calculateOverallScore(ruleScores: List<Pair<CompositionRule, Float>>): Float {
        if (ruleScores.isEmpty()) return 0.5f

        // Weighted average of rule scores
        val weights = mapOf(
            CompositionRule.RULE_OF_THIRDS to 0.25f,
            CompositionRule.LEADING_LINES to 0.2f,
            CompositionRule.SYMMETRY to 0.15f,
            CompositionRule.NEGATIVE_SPACE to 0.15f,
            CompositionRule.COLOR_HARMONY to 0.1f,
            CompositionRule.GOLDEN_RATIO to 0.15f
        )

        var weightedSum = 0f
        var totalWeight = 0f

        for ((rule, score) in ruleScores) {
            val weight = weights[rule] ?: 0.1f
            weightedSum += score * weight
            totalWeight += weight
        }

        return if (totalWeight > 0) weightedSum / totalWeight else 0.5f
    }

    // Suggestion creation methods

    private fun createRuleOfThirdsSuggestion(subjectPosition: PointF?, width: Int, height: Int): CompositionSuggestion {
        val targetPosition = PointF(width / 3f, height / 3f) // Default to top-left third intersection

        return CompositionSuggestion(
            type = GuidanceType.MOVE_SUBJECT,
            confidence = 0.8f,
            description = "Position your subject along the rule of thirds lines for better composition",
            targetPosition = targetPosition,
            visualIndicator = GuideOverlay(
                type = OverlayType.RULE_OF_THIRDS_GRID,
                positions = listOf(targetPosition),
                lines = emptyList()
            )
        )
    }

    private fun createLeadingLinesSuggestion(lines: List<LineSegment>, subjectPosition: PointF): CompositionSuggestion {
        return CompositionSuggestion(
            type = GuidanceType.FOLLOW_LINES,
            confidence = 0.7f,
            description = "Use the leading lines to guide the viewer's eye to your subject",
            targetPosition = subjectPosition,
            visualIndicator = GuideOverlay(
                type = OverlayType.LEADING_LINE_GUIDES,
                positions = emptyList(),
                lines = lines
            )
        )
    }

    private fun createSymmetrySuggestion(symmetryAxis: PointF?): CompositionSuggestion {
        return CompositionSuggestion(
            type = GuidanceType.USE_SYMMETRY,
            confidence = 0.6f,
            description = "Take advantage of the symmetry in your scene",
            targetPosition = symmetryAxis,
            visualIndicator = symmetryAxis?.let { axis ->
                GuideOverlay(
                    type = OverlayType.SYMMETRY_AXIS,
                    positions = listOf(axis),
                    lines = emptyList()
                )
            }
        )
    }

    private fun createGeneralImprovementSuggestions(subjectPosition: PointF?, visualWeight: CompositionWeight): List<CompositionSuggestion> {
        val suggestions = mutableListOf<CompositionSuggestion>()

        // Check for common issues
        if (subjectPosition != null) {
            suggestions.add(
                CompositionSuggestion(
                    type = GuidanceType.SIMPLIFY_BACKGROUND,
                    confidence = 0.5f,
                    description = "Try to simplify the background to make your subject stand out",
                    targetPosition = null,
                    visualIndicator = null
                )
            )
        }

        return suggestions
    }

    private fun updateGuideOverlays(analysis: CompositionAnalysis) {
        currentOverlays.clear()

        // Add overlays based on active suggestions
        for (suggestion in analysis.suggestions) {
            suggestion.visualIndicator?.let { overlay ->
                currentOverlays.add(overlay)
            }
        }
    }

    private fun simplifyColor(pixel: Int): Int {
        // Simplify color to reduce palette for analysis
        val r = (Color.red(pixel) / 32) * 32
        val g = (Color.green(pixel) / 32) * 32
        val b = (Color.blue(pixel) / 32) * 32
        return Color.rgb(r, g, b)
    }

    private fun createDefaultAnalysis(): CompositionAnalysis {
        return CompositionAnalysis(
            overallScore = 0.5f,
            appliedRules = emptyList(),
            suggestions = emptyList(),
            subjectPosition = null,
            interestPoints = emptyList(),
            leadingLines = emptyList(),
            symmetryAxis = null,
            dominantColors = emptyList(),
            visualWeight = CompositionWeight(0.25f, 0.25f, 0.25f, 0.25f, 0.0f)
        )
    }

    /**
     * Clean up resources
     */
    fun cleanup() {
        stopAnalysis()
        currentOverlays.clear()
        Log.d(TAG, "AI composition guide manager cleaned up")
    }
}