package com.customcamera.app.core

import java.nio.ByteBuffer

/**
 * Luminance histogram computed straight from a YUV_420_888 Y plane.
 *
 * Works on a [ByteBuffer] plus strides so it can consume CameraX ImageProxy
 * planes without copying; sub-sampling keeps it well under 1 ms per frame.
 */
class Histogram(val bins: IntArray) {

    init {
        require(bins.isNotEmpty()) { "histogram needs at least one bin" }
    }

    val total: Long get() = bins.fold(0L) { acc, v -> acc + v }

    /** Bin counts normalised to 0..1 against the tallest bin. */
    fun normalized(): FloatArray {
        val max = bins.maxOrNull()?.takeIf { it > 0 } ?: return FloatArray(bins.size)
        return FloatArray(bins.size) { bins[it].toFloat() / max }
    }

    /** Fraction of samples in the top bin (blown highlights) — drives the clipping warning. */
    fun highlightClipping(): Float = if (total == 0L) 0f else bins.last().toFloat() / total

    /** Fraction of samples in the bottom bin (crushed shadows). */
    fun shadowClipping(): Float = if (total == 0L) 0f else bins.first().toFloat() / total

    /** Mean luminance in 0..255. */
    fun meanLuma(): Float {
        val t = total
        if (t == 0L) return 0f
        val binWidth = 256f / bins.size
        var sum = 0.0
        bins.forEachIndexed { i, c -> sum += c * (i + 0.5) * binWidth }
        return (sum / t).toFloat()
    }

    companion object {
        const val DEFAULT_BINS = 64

        /**
         * @param yPlane luminance plane; position is not modified.
         * @param width image width in pixels.
         * @param height image height in pixels.
         * @param rowStride bytes between rows.
         * @param pixelStride bytes between adjacent pixels in a row.
         * @param step sample every [step]th pixel in both axes.
         */
        fun fromLuma(
            yPlane: ByteBuffer,
            width: Int,
            height: Int,
            rowStride: Int,
            pixelStride: Int = 1,
            binCount: Int = DEFAULT_BINS,
            step: Int = 4,
        ): Histogram {
            require(width > 0 && height > 0) { "invalid size ${width}x$height" }
            require(binCount in 1..256 && 256 % binCount == 0) { "binCount must divide 256: $binCount" }
            require(step >= 1) { "step must be ≥ 1" }
            val bins = IntArray(binCount)
            val shift = Integer.numberOfTrailingZeros(256 / binCount)
            val limit = yPlane.limit()
            var y = 0
            while (y < height) {
                val rowOffset = y * rowStride
                var x = 0
                while (x < width) {
                    val index = rowOffset + x * pixelStride
                    if (index >= limit) break
                    val luma = yPlane.get(index).toInt() and 0xFF
                    bins[luma ushr shift]++
                    x += step
                }
                y += step
            }
            return Histogram(bins)
        }
    }
}
