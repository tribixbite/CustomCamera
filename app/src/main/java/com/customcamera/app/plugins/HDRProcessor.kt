package com.customcamera.app.plugins

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import kotlin.math.exp
import kotlin.math.pow

/**
 * HDRProcessor handles merging bracketed exposures into HDR images.
 * Uses simplified Mertens exposure fusion without frame alignment.
 *
 * Note: This implementation assumes the user holds the camera steady.
 * For v2, add OpenCV dependency for proper frame alignment.
 */
class HDRProcessor {

    /**
     * Merge bracketed exposures using weighted fusion
     *
     * @param frames List of bracketed frames at different exposures
     * @return Merged HDR bitmap or null on failure
     */
    fun mergeExposures(frames: List<HDRCaptureController.BracketedFrame>): Bitmap? {
        if (frames.isEmpty()) {
            Log.e(TAG, "No frames to merge")
            return null
        }

        if (frames.size == 1) {
            Log.w(TAG, "Only one frame, returning it directly")
            return frames[0].bitmap
        }

        Log.i(TAG, "Merging ${frames.size} bracketed exposures")

        return try {
            val startTime = System.currentTimeMillis()

            // Get dimensions from first frame
            val width = frames[0].bitmap.width
            val height = frames[0].bitmap.height

            // Verify all frames have same dimensions
            if (!frames.all { it.bitmap.width == width && it.bitmap.height == height }) {
                Log.e(TAG, "Frame dimensions mismatch")
                return null
            }

            // Calculate quality weights for each frame
            val weights = frames.map { frame ->
                calculateQualityWeights(frame.bitmap, frame.evValue)
            }

            // Merge pixels using weighted average
            val mergedBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

            for (y in 0 until height) {
                for (x in 0 until width) {
                    var totalR = 0f
                    var totalG = 0f
                    var totalB = 0f
                    var totalWeight = 0f

                    // Accumulate weighted pixel values from all frames
                    frames.forEachIndexed { index, frame ->
                        val pixel = frame.bitmap.getPixel(x, y)
                        val weight = weights[index][y][x]

                        totalR += Color.red(pixel) * weight
                        totalG += Color.green(pixel) * weight
                        totalB += Color.blue(pixel) * weight
                        totalWeight += weight
                    }

                    // Normalize by total weight
                    if (totalWeight > 0f) {
                        val r = (totalR / totalWeight).toInt().coerceIn(0, 255)
                        val g = (totalG / totalWeight).toInt().coerceIn(0, 255)
                        val b = (totalB / totalWeight).toInt().coerceIn(0, 255)
                        mergedBitmap.setPixel(x, y, Color.rgb(r, g, b))
                    } else {
                        // Fallback to middle frame if weights are zero
                        val middleIndex = frames.size / 2
                        mergedBitmap.setPixel(x, y, frames[middleIndex].bitmap.getPixel(x, y))
                    }
                }
            }

            val processingTime = System.currentTimeMillis() - startTime
            Log.i(TAG, "Merged ${frames.size} frames in ${processingTime}ms")

            mergedBitmap

        } catch (e: Exception) {
            Log.e(TAG, "Error merging exposures", e)
            null
        }
    }

    /**
     * Calculate quality weights for a frame using Mertens criteria:
     * - Contrast (Laplacian filter)
     * - Saturation (color intensity)
     * - Well-exposedness (distance from mid-gray)
     *
     * @param bitmap Frame to analyze
     * @param evValue Exposure value for this frame
     * @return 2D array of quality weights (0.0 to 1.0) for each pixel
     */
    private fun calculateQualityWeights(bitmap: Bitmap, evValue: Int): Array<FloatArray> {
        val width = bitmap.width
        val height = bitmap.height
        val weights = Array(height) { FloatArray(width) }

        Log.d(TAG, "Calculating quality weights for ${width}x${height} frame at EV $evValue")

        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = bitmap.getPixel(x, y)
                val r = Color.red(pixel) / 255f
                val g = Color.green(pixel) / 255f
                val b = Color.blue(pixel) / 255f

                // 1. Contrast weight (simplified Laplacian)
                val contrastWeight = calculateContrastWeight(bitmap, x, y)

                // 2. Saturation weight
                val max = maxOf(r, g, b)
                val min = minOf(r, g, b)
                val saturationWeight = (max - min).pow(1.0f)

                // 3. Well-exposedness weight (Gaussian around mid-gray 0.5)
                val luminance = 0.299f * r + 0.587f * g + 0.114f * b
                val wellExposedWeight = exp(-((luminance - 0.5f).pow(2) / (2 * 0.2f.pow(2))))

                // Combine weights (equal importance)
                weights[y][x] = (contrastWeight * saturationWeight * wellExposedWeight).toFloat()
            }
        }

        // Normalize weights to prevent values from getting too small
        val maxWeight = weights.flatMap { it.toList() }.maxOrNull() ?: 1f
        if (maxWeight > 0f) {
            for (y in 0 until height) {
                for (x in 0 until width) {
                    weights[y][x] = (weights[y][x] / maxWeight).coerceIn(0.01f, 1f)
                }
            }
        }

        return weights
    }

    /**
     * Calculate contrast weight using simplified Laplacian filter
     */
    private fun calculateContrastWeight(bitmap: Bitmap, x: Int, y: Int): Float {
        val width = bitmap.width
        val height = bitmap.height

        // Get center pixel luminance
        val centerPixel = bitmap.getPixel(x, y)
        val centerLum = calculateLuminance(centerPixel)

        // Calculate gradient with neighbors
        var gradientSum = 0f
        var count = 0

        // Check 4-connected neighbors
        val neighbors = listOf(
            Pair(x - 1, y),     // Left
            Pair(x + 1, y),     // Right
            Pair(x, y - 1),     // Top
            Pair(x, y + 1)      // Bottom
        )

        for ((nx, ny) in neighbors) {
            if (nx in 0 until width && ny in 0 until height) {
                val neighborPixel = bitmap.getPixel(nx, ny)
                val neighborLum = calculateLuminance(neighborPixel)
                gradientSum += kotlin.math.abs(centerLum - neighborLum)
                count++
            }
        }

        return if (count > 0) {
            (gradientSum / count).coerceIn(0f, 1f)
        } else {
            0.1f // Fallback for edge pixels
        }
    }

    /**
     * Calculate luminance from RGB pixel
     */
    private fun calculateLuminance(pixel: Int): Float {
        val r = Color.red(pixel) / 255f
        val g = Color.green(pixel) / 255f
        val b = Color.blue(pixel) / 255f
        return 0.299f * r + 0.587f * g + 0.114f * b
    }

    /**
     * Apply tone mapping to compress HDR to displayable range
     * Uses simple gamma correction (simplified version of tone mapping)
     *
     * Note: Mertens fusion already produces LDR output, so this is optional.
     * In v2 with full HDR pipeline, use Reinhard or filmic tone mapping.
     *
     * @param image Input HDR image
     * @param gamma Gamma value (default 2.2 for display)
     * @return Tone-mapped bitmap
     */
    fun applyToneMapping(image: Bitmap, gamma: Float = 2.2f): Bitmap {
        Log.d(TAG, "Applying gamma correction with gamma=$gamma")

        val width = image.width
        val height = image.height
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        val invGamma = 1f / gamma

        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = image.getPixel(x, y)

                // Apply gamma correction to each channel
                val r = (Color.red(pixel) / 255f).pow(invGamma)
                val g = (Color.green(pixel) / 255f).pow(invGamma)
                val b = (Color.blue(pixel) / 255f).pow(invGamma)

                val newR = (r * 255).toInt().coerceIn(0, 255)
                val newG = (g * 255).toInt().coerceIn(0, 255)
                val newB = (b * 255).toInt().coerceIn(0, 255)

                result.setPixel(x, y, Color.rgb(newR, newG, newB))
            }
        }

        Log.i(TAG, "Tone mapping complete")
        return result
    }

    /**
     * Get processing statistics
     */
    fun getStats(): Map<String, Any> {
        return mapOf(
            "algorithm" to "Mertens Exposure Fusion (simplified)",
            "alignment" to "None (v1 - requires steady hands)",
            "toneMapping" to "Gamma correction",
            "openCVRequired" to false
        )
    }

    companion object {
        private const val TAG = "HDRProcessor"
    }
}
