package com.customcamera.app.analysis

import android.content.Context
import android.graphics.*
import android.util.Log
import android.view.View
import com.customcamera.app.plugins.HistogramPlugin

/**
 * HistogramView displays real-time RGB and luminance histograms
 * with over/under exposure warnings and dynamic range analysis.
 */
class HistogramView(context: Context) : View(context) {

    private val redPaint = Paint().apply {
        color = Color.RED
        alpha = 180
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val greenPaint = Paint().apply {
        color = Color.GREEN
        alpha = 180
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val bluePaint = Paint().apply {
        color = Color.BLUE
        alpha = 180
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val luminancePaint = Paint().apply {
        color = Color.WHITE
        alpha = 200
        style = Paint.Style.STROKE
        strokeWidth = 2f
        isAntiAlias = true
    }

    private val backgroundPaint = Paint().apply {
        color = Color.BLACK
        alpha = 160
        style = Paint.Style.FILL
    }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 24f
        isAntiAlias = true
    }

    // Histogram data
    private var currentHistogram: HistogramPlugin.Histogram? = null
    private var showRGBHistogram: Boolean = true
    private var showLuminanceHistogram: Boolean = true

    // Display settings
    private val histogramHeight = 120f
    private val histogramMargin = 20f

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val histogram = currentHistogram ?: return

        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()

        if (viewWidth <= 0 || viewHeight <= 0) return

        // Draw background
        canvas.drawRect(0f, 0f, viewWidth, viewHeight, backgroundPaint)

        // Calculate histogram display area
        val histogramWidth = viewWidth - 2 * histogramMargin
        val histogramTop = histogramMargin
        val histogramBottom = histogramTop + histogramHeight

        // Draw RGB histograms
        if (showRGBHistogram) {
            drawRGBHistogram(canvas, histogram, histogramMargin, histogramTop, histogramWidth, histogramHeight)
        }

        // Draw luminance histogram
        if (showLuminanceHistogram) {
            val luminanceTop = if (showRGBHistogram) histogramBottom + 20f else histogramTop
            drawLuminanceHistogram(canvas, histogram, histogramMargin, luminanceTop, histogramWidth, histogramHeight)
        }

        // Draw exposure warnings
        drawExposureWarnings(canvas, histogram, viewWidth, viewHeight)
    }

    /**
     * Update histogram data
     */
    fun updateHistogram(histogram: HistogramPlugin.Histogram) {
        currentHistogram = histogram
        invalidate()
        Log.d(TAG, "Histogram updated - avg brightness: ${histogram.averageBrightness}")
    }

    /**
     * Clear histogram display
     */
    fun clearHistogram() {
        currentHistogram = null
        invalidate()
        Log.d(TAG, "Histogram cleared")
    }

    /**
     * Set RGB histogram visibility
     */
    fun setShowRGB(show: Boolean) {
        if (showRGBHistogram != show) {
            showRGBHistogram = show
            invalidate()
            Log.d(TAG, "RGB histogram ${if (show) "shown" else "hidden"}")
        }
    }

    /**
     * Set luminance histogram visibility
     */
    fun setShowLuminance(show: Boolean) {
        if (showLuminanceHistogram != show) {
            showLuminanceHistogram = show
            invalidate()
            Log.d(TAG, "Luminance histogram ${if (show) "shown" else "hidden"}")
        }
    }

    private fun drawRGBHistogram(
        canvas: Canvas,
        histogram: HistogramPlugin.Histogram,
        left: Float,
        top: Float,
        width: Float,
        height: Float
    ) {
        val binWidth = width / HistogramPlugin.Histogram.HISTOGRAM_BINS

        // Find max value for scaling
        val maxValue = maxOf(
            histogram.red.maxOrNull() ?: 0,
            histogram.green.maxOrNull() ?: 0,
            histogram.blue.maxOrNull() ?: 0
        )

        if (maxValue == 0) return

        // Draw RGB histograms
        for (i in 0 until HistogramPlugin.Histogram.HISTOGRAM_BINS) {
            val x = left + i * binWidth

            // Scale values to fit height
            val redHeight = (histogram.red[i].toFloat() / maxValue) * height
            val greenHeight = (histogram.green[i].toFloat() / maxValue) * height
            val blueHeight = (histogram.blue[i].toFloat() / maxValue) * height

            // Draw bars
            canvas.drawRect(x, top + height - redHeight, x + binWidth, top + height, redPaint)
            canvas.drawRect(x, top + height - greenHeight, x + binWidth, top + height, greenPaint)
            canvas.drawRect(x, top + height - blueHeight, x + binWidth, top + height, bluePaint)
        }
    }

    private fun drawLuminanceHistogram(
        canvas: Canvas,
        histogram: HistogramPlugin.Histogram,
        left: Float,
        top: Float,
        width: Float,
        height: Float
    ) {
        val binWidth = width / HistogramPlugin.Histogram.HISTOGRAM_BINS
        val maxValue = histogram.luminance.maxOrNull() ?: 0

        if (maxValue == 0) return

        val path = Path()
        var isFirstPoint = true

        for (i in 0 until HistogramPlugin.Histogram.HISTOGRAM_BINS) {
            val x = left + i * binWidth
            val normalizedValue = histogram.luminance[i].toFloat() / maxValue
            val y = top + height - (normalizedValue * height)

            if (isFirstPoint) {
                path.moveTo(x, y)
                isFirstPoint = false
            } else {
                path.lineTo(x, y)
            }
        }

        canvas.drawPath(path, luminancePaint)
    }

    private fun drawExposureWarnings(
        canvas: Canvas,
        histogram: HistogramPlugin.Histogram,
        viewWidth: Float,
        viewHeight: Float
    ) {
        val warnings = analyzeExposureFromHistogram(histogram)

        val warningY = viewHeight - 80f
        var warningText = ""

        if (warnings.overExposed) {
            warningText += "⚠️ OVEREXPOSED (${String.format("%.1f", warnings.overExposedPercentage)}%) "
        }

        if (warnings.underExposed) {
            warningText += "⚠️ UNDEREXPOSED (${String.format("%.1f", warnings.underExposedPercentage)}%) "
        }

        if (warnings.optimalExposure) {
            warningText = "✅ OPTIMAL EXPOSURE"
        }

        if (warningText.isNotEmpty()) {
            // Draw warning background
            val textBounds = Rect()
            textPaint.getTextBounds(warningText, 0, warningText.length, textBounds)

            canvas.drawRect(
                10f,
                warningY - textBounds.height() - 10f,
                textBounds.width() + 20f,
                warningY + 10f,
                backgroundPaint
            )

            // Draw warning text
            canvas.drawText(warningText, 15f, warningY, textPaint)
        }

        // Draw average brightness
        val brightnessText = "Avg: ${String.format("%.0f", histogram.averageBrightness)}"
        canvas.drawText(brightnessText, viewWidth - 100f, warningY, textPaint)
    }

    private fun analyzeExposureFromHistogram(histogram: HistogramPlugin.Histogram): HistogramPlugin.ExposureWarnings {
        val luminance = histogram.luminance
        val totalPixels = histogram.totalPixels

        val underExposedPixels = luminance.sliceArray(0..15).sum()
        val overExposedPixels = luminance.sliceArray(240..255).sum()

        val underExposedPercentage = (underExposedPixels.toFloat() / totalPixels) * 100f
        val overExposedPercentage = (overExposedPixels.toFloat() / totalPixels) * 100f

        val nonZeroBins = luminance.count { it > 0 }
        val dynamicRange = (nonZeroBins.toFloat() / HistogramPlugin.Histogram.HISTOGRAM_BINS) * 100f

        val overExposed = overExposedPercentage > 5f
        val underExposed = underExposedPercentage > 10f
        val optimalExposure = !overExposed && !underExposed && histogram.averageBrightness in 80f..180f

        return HistogramPlugin.ExposureWarnings(
            overExposed = overExposed,
            underExposed = underExposed,
            overExposedPercentage = overExposedPercentage,
            underExposedPercentage = underExposedPercentage,
            dynamicRange = dynamicRange,
            optimalExposure = optimalExposure
        )
    }

    companion object {
        private const val TAG = "HistogramView"
    }
}