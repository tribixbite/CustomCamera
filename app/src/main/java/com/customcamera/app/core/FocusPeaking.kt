package com.customcamera.app.core

import java.nio.ByteBuffer

/**
 * Focus-peaking mask: which sample cells of a frame contain strong local
 * contrast (in-focus edges). Cell (x, y) covers image pixels
 * `[x * step, (x + 1) * step) × [y * step, (y + 1) * step)` of the source buffer,
 * in the buffer's own (sensor, unrotated) orientation.
 */
class PeakingMask(val width: Int, val height: Int, val step: Int, val cells: BooleanArray) {
    init {
        require(cells.size == width * height) { "mask size ${cells.size} != ${width}x$height" }
    }

    val count: Int get() = cells.count { it }

    operator fun get(x: Int, y: Int): Boolean = cells[y * width + x]

    /**
     * The mask rotated clockwise by [degrees] (0, 90, 180 or 270), i.e. into the
     * upright orientation an ImageProxy reports through rotationDegrees.
     */
    fun rotated(degrees: Int): PeakingMask {
        val d = ((degrees % 360) + 360) % 360
        require(d % 90 == 0) { "rotation must be a multiple of 90: $degrees" }
        if (d == 0) return this
        val w = if (d == 180) width else height
        val h = if (d == 180) height else width
        val out = BooleanArray(w * h)
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (!cells[y * width + x]) continue
                // Clockwise: 90 → (h-1-y, x); 180 → (w-1-x, h-1-y); 270 → (y, w-1-x), in source dimensions.
                val (nx, ny) = when (d) {
                    90 -> (height - 1 - y) to x
                    180 -> (width - 1 - x) to (height - 1 - y)
                    else -> y to (width - 1 - x)
                }
                out[ny * w + nx] = true
            }
        }
        return PeakingMask(w, h, step, out)
    }
}

/**
 * Edge detector for manual-focus assistance, computed straight from a
 * YUV_420_888 luminance plane so it can run on every analysis frame.
 *
 * Gradient magnitude is the sum of absolute central differences on a grid
 * sub-sampled by `step`. The threshold adapts to the scene: only the strongest
 * [topFraction] of cells are highlighted, and never cells below [minGradient],
 * so a defocused or flat frame shows (almost) nothing instead of noise.
 */
object FocusPeaking {
    /** Maximum gradient value: two differences of at most 255 each. */
    private const val MAX_GRADIENT = 510

    const val DEFAULT_STEP = 2
    const val DEFAULT_TOP_FRACTION = 0.06f
    const val DEFAULT_MIN_GRADIENT = 48

    /**
     * @param yPlane luminance plane; position is not modified.
     * @param width image width in pixels.
     * @param height image height in pixels.
     * @param rowStride bytes between rows.
     * @param pixelStride bytes between adjacent pixels in a row.
     * @param step sampling step in pixels (mask resolution = size / step).
     * @param topFraction at most this fraction of cells is marked.
     * @param minGradient cells weaker than this are never marked.
     */
    fun detect(
        yPlane: ByteBuffer,
        width: Int,
        height: Int,
        rowStride: Int,
        pixelStride: Int = 1,
        step: Int = DEFAULT_STEP,
        topFraction: Float = DEFAULT_TOP_FRACTION,
        minGradient: Int = DEFAULT_MIN_GRADIENT,
    ): PeakingMask {
        require(width > 0 && height > 0) { "invalid size ${width}x$height" }
        require(step >= 1) { "step must be ≥ 1" }
        require(topFraction in 0f..1f) { "topFraction must be in 0..1" }
        val w = width / step
        val h = height / step
        val limit = yPlane.limit()

        // Sub-sampled luma grid; out-of-buffer samples (truncated last row) read as 0.
        val luma = IntArray(w * h)
        for (y in 0 until h) {
            val rowOffset = y * step * rowStride
            for (x in 0 until w) {
                val index = rowOffset + x * step * pixelStride
                luma[y * w + x] = if (index < limit) yPlane.get(index).toInt() and 0xFF else 0
            }
        }

        // Gradient magnitudes plus their histogram (border cells stay 0).
        val gradient = IntArray(w * h)
        val histogram = IntArray(MAX_GRADIENT + 1)
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val i = y * w + x
                val g = kotlin.math.abs(luma[i + 1] - luma[i - 1]) + kotlin.math.abs(luma[i + w] - luma[i - w])
                gradient[i] = g
                histogram[g]++
            }
        }

        val threshold = maxOf(minGradient, thresholdForTopFraction(histogram, (w * h * topFraction).toInt()))
        val cells = BooleanArray(w * h) { gradient[it] >= threshold }
        return PeakingMask(w, h, step, cells)
    }

    /**
     * Smallest gradient value such that at most [budget] cells are at or above it
     * (so nothing is marked when even the strongest bin exceeds the budget).
     */
    internal fun thresholdForTopFraction(histogram: IntArray, budget: Int): Int {
        var above = 0
        for (g in histogram.indices.reversed()) {
            if (above + histogram[g] > budget) return g + 1
            above += histogram[g]
        }
        return 0
    }
}
