package com.customcamera.app.core

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Zoom preset generation and formatting for the zoom chips under the viewfinder.
 */
object ZoomMath {

    /** Candidate presets; only those inside the camera's range are offered. */
    private val CANDIDATES = floatArrayOf(0.5f, 0.6f, 1f, 2f, 3f, 5f, 10f)

    /** Max number of chips so the row fits on narrow phones. */
    private const val MAX_PRESETS = 5

    /**
     * Zoom chips for a camera with the given zoom range.
     *
     * Always includes 1x. When the camera can zoom out below 1x (logical
     * multi-camera with an ultra-wide), the minimum ratio itself becomes the
     * wide chip so it maps exactly onto the ultra-wide lens (e.g. 0.6x on Samsung).
     */
    fun presets(minRatio: Float, maxRatio: Float): List<Float> {
        require(minRatio > 0f && maxRatio >= minRatio) { "invalid zoom range [$minRatio, $maxRatio]" }
        val result = mutableListOf<Float>()
        if (minRatio < 0.95f) result += roundTenth(minRatio)
        if (1f in minRatio..maxRatio) result += 1f
        CANDIDATES.filter { it > 1f && it <= maxRatio + 0.01f }.forEach { result += it }
        if (result.isEmpty()) result += roundTenth(minRatio)
        // Keep the widest and 1x, then the most useful telephoto steps.
        return result.distinct().take(MAX_PRESETS)
    }

    /** Index of the preset that best represents the current ratio (for highlighting). */
    fun activePresetIndex(presets: List<Float>, ratio: Float): Int {
        if (presets.isEmpty()) return -1
        // Highlight the largest preset not exceeding the current ratio (with tolerance).
        val idx = presets.indexOfLast { it <= ratio + 0.05f }
        return if (idx >= 0) idx else 0
    }

    /** "0.6x", "1x", "2.5x", "10x". Values ≥ 10 drop the decimal. */
    fun format(ratio: Float): String {
        val r = roundTenth(ratio)
        return when {
            r >= 10f -> "${r.roundToInt()}x"
            abs(r - r.roundToInt()) < 0.05f -> "${r.roundToInt()}x"
            else -> String.format(Locale.US, "%.1fx", r)
        }
    }

    /**
     * Applies a pinch scale factor to the current ratio, clamped to the range.
     */
    fun applyPinch(current: Float, scaleFactor: Float, minRatio: Float, maxRatio: Float): Float =
        (current * scaleFactor).coerceIn(minRatio, maxRatio)

    /**
     * Next ratio for one volume-key zoom step (≈ 10% per press, geometric so
     * steps feel uniform across the range).
     */
    fun step(current: Float, zoomIn: Boolean, minRatio: Float, maxRatio: Float): Float {
        val factor = if (zoomIn) 1.1f else 1f / 1.1f
        return (current * factor).coerceIn(minRatio, maxRatio)
    }

    private fun roundTenth(v: Float): Float = (v * 10).roundToInt() / 10f
}
