package com.customcamera.app.manual

import android.content.Context
import android.graphics.*
import android.util.Log
import android.view.View
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.customcamera.app.engine.DebugLogger
import kotlinx.coroutines.launch
import kotlin.math.*

/**
 * Visual Aids Overlay Manager
 *
 * Provides professional visual aids for manual photography:
 * - Real-time histogram display
 * - Zebra pattern for overexposure warning
 * - Focus peaking for manual focus assistance
 * - Live exposure meter with EV readings
 * - Professional viewfinder information overlay
 *
 * Designed to replicate professional camera viewfinder aids on mobile devices.
 */
class VisualAidsOverlayManager(
    private val context: Context,
    private val debugLogger: DebugLogger
) {

    companion object {
        private const val TAG = "VisualAidsOverlay"

        // Histogram configuration
        private const val HISTOGRAM_WIDTH = 200
        private const val HISTOGRAM_HEIGHT = 100
        private const val HISTOGRAM_MARGIN = 16

        // Zebra pattern configuration
        private const val ZEBRA_THRESHOLD = 240 // RGB value threshold for overexposure
        private const val ZEBRA_STRIPE_WIDTH = 4

        // Focus peaking configuration
        private const val FOCUS_EDGE_THRESHOLD = 50
        private const val FOCUS_PEAKING_COLOR = Color.RED

        // Exposure meter configuration
        private const val EXPOSURE_METER_WIDTH = 200
        private const val EXPOSURE_METER_HEIGHT = 20

        // Professional overlay colors
        private const val OVERLAY_TEXT_COLOR = Color.WHITE
        private const val OVERLAY_BACKGROUND_COLOR = 0x80000000.toInt()
        private const val GRID_LINE_COLOR = 0x40FFFFFF.toInt()
    }

    // Visual aids state
    private var histogramData: IntArray? = null
    private var exposureLevel: Float = 0.5f
    private var isShowingHistogram = false
    private var isShowingZebraPattern = false
    private var isShowingFocusPeaking = false
    private var isShowingExposureMeter = false
    private var isShowingProfessionalOverlay = false

    // Manual controls state for overlay
    private var currentControlsState: ManualControlsState? = null

    // Paint objects for drawing
    private val histogramPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        alpha = 200
    }

    private val zebraPaint = Paint().apply {
        color = Color.MAGENTA
        style = Paint.Style.FILL
        alpha = 150
    }

    private val focusPeakingPaint = Paint().apply {
        color = FOCUS_PEAKING_COLOR
        style = Paint.Style.FILL
        alpha = 180
    }

    private val textPaint = Paint().apply {
        color = OVERLAY_TEXT_COLOR
        textSize = 32f
        isAntiAlias = true
        typeface = Typeface.MONOSPACE
    }

    private val backgroundPaint = Paint().apply {
        color = OVERLAY_BACKGROUND_COLOR
        style = Paint.Style.FILL
    }

    private val meterPaint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.FILL
    }

    private val gridPaint = Paint().apply {
        color = GRID_LINE_COLOR
        style = Paint.Style.STROKE
        strokeWidth = 2f
        pathEffect = DashPathEffect(floatArrayOf(10f, 5f), 0f)
    }

    /**
     * Update histogram data
     */
    fun updateHistogram(histogram: IntArray) {
        histogramData = histogram
        debugLogger.logDebug("Histogram updated with ${histogram.size} bins")
    }

    /**
     * Update exposure level (0.0 to 1.0)
     */
    fun updateExposureLevel(level: Float) {
        exposureLevel = level.coerceIn(0f, 1f)
        debugLogger.logDebug("Exposure level updated to $level")
    }

    /**
     * Update manual controls state for overlay display
     */
    fun updateManualControlsState(state: ManualControlsState) {
        currentControlsState = state
        isShowingHistogram = state.showHistogram
        isShowingZebraPattern = state.showZebraPattern
        isShowingFocusPeaking = state.showFocusPeaking
        isShowingExposureMeter = state.showExposureMeter
        isShowingProfessionalOverlay = state.showProfessionalOverlay
    }

    /**
     * Draw all enabled visual aids on the provided canvas
     */
    fun drawOverlay(canvas: Canvas, viewWidth: Int, viewHeight: Int) {
        try {
            if (isShowingHistogram) {
                drawHistogram(canvas, viewWidth, viewHeight)
            }

            if (isShowingExposureMeter) {
                drawExposureMeter(canvas, viewWidth, viewHeight)
            }

            if (isShowingProfessionalOverlay) {
                drawProfessionalInfoOverlay(canvas, viewWidth, viewHeight)
            }

            // Note: Zebra pattern and focus peaking require image processing
            // and are typically applied during image preview processing
        } catch (e: Exception) {
            Log.e(TAG, "Error drawing visual aids overlay", e)
        }
    }

    /**
     * Process image for zebra pattern detection
     */
    fun processZebraPattern(bitmap: Bitmap): Bitmap? {
        if (!isShowingZebraPattern) return null

        try {
            val resultBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(resultBitmap)
            val paint = Paint(zebraPaint)

            val width = bitmap.width
            val height = bitmap.height
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

            // Create zebra pattern overlay
            val zebraOverlay = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val zebraPixels = IntArray(width * height)

            for (y in 0 until height) {
                for (x in 0 until width) {
                    val pixel = pixels[y * width + x]
                    val r = (pixel shr 16) and 0xFF
                    val g = (pixel shr 8) and 0xFF
                    val b = pixel and 0xFF

                    // Check if pixel is overexposed
                    if (r > ZEBRA_THRESHOLD || g > ZEBRA_THRESHOLD || b > ZEBRA_THRESHOLD) {
                        // Apply zebra pattern
                        val stripePosition = (x + y) / ZEBRA_STRIPE_WIDTH
                        if (stripePosition % 2 == 0) {
                            zebraPixels[y * width + x] = zebraPaint.color
                        }
                    }
                }
            }

            zebraOverlay.setPixels(zebraPixels, 0, width, 0, 0, width, height)
            canvas.drawBitmap(zebraOverlay, 0f, 0f, paint)
            zebraOverlay.recycle()

            return resultBitmap
        } catch (e: Exception) {
            Log.e(TAG, "Error processing zebra pattern", e)
            return null
        }
    }

    /**
     * Process image for focus peaking
     */
    fun processFocusPeaking(bitmap: Bitmap): Bitmap? {
        if (!isShowingFocusPeaking) return null

        try {
            val resultBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(resultBitmap)

            val width = bitmap.width
            val height = bitmap.height
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

            // Edge detection for focus peaking
            val edgeBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val edgePixels = IntArray(width * height)

            for (y in 1 until height - 1) {
                for (x in 1 until width - 1) {
                    val centerPixel = pixels[y * width + x]
                    val centerGray = getGrayscaleValue(centerPixel)

                    // Sobel edge detection
                    var sobelX = 0
                    var sobelY = 0

                    // Sobel X kernel
                    sobelX += -1 * getGrayscaleValue(pixels[(y - 1) * width + (x - 1)])
                    sobelX += 0 * getGrayscaleValue(pixels[(y - 1) * width + x])
                    sobelX += 1 * getGrayscaleValue(pixels[(y - 1) * width + (x + 1)])
                    sobelX += -2 * getGrayscaleValue(pixels[y * width + (x - 1)])
                    sobelX += 0 * getGrayscaleValue(pixels[y * width + x])
                    sobelX += 2 * getGrayscaleValue(pixels[y * width + (x + 1)])
                    sobelX += -1 * getGrayscaleValue(pixels[(y + 1) * width + (x - 1)])
                    sobelX += 0 * getGrayscaleValue(pixels[(y + 1) * width + x])
                    sobelX += 1 * getGrayscaleValue(pixels[(y + 1) * width + (x + 1)])

                    // Sobel Y kernel
                    sobelY += -1 * getGrayscaleValue(pixels[(y - 1) * width + (x - 1)])
                    sobelY += -2 * getGrayscaleValue(pixels[(y - 1) * width + x])
                    sobelY += -1 * getGrayscaleValue(pixels[(y - 1) * width + (x + 1)])
                    sobelY += 0 * getGrayscaleValue(pixels[y * width + (x - 1)])
                    sobelY += 0 * getGrayscaleValue(pixels[y * width + x])
                    sobelY += 0 * getGrayscaleValue(pixels[y * width + (x + 1)])
                    sobelY += 1 * getGrayscaleValue(pixels[(y + 1) * width + (x - 1)])
                    sobelY += 2 * getGrayscaleValue(pixels[(y + 1) * width + x])
                    sobelY += 1 * getGrayscaleValue(pixels[(y + 1) * width + (x + 1)])

                    val magnitude = sqrt((sobelX * sobelX + sobelY * sobelY).toDouble()).toInt()

                    if (magnitude > FOCUS_EDGE_THRESHOLD) {
                        edgePixels[y * width + x] = FOCUS_PEAKING_COLOR
                    }
                }
            }

            edgeBitmap.setPixels(edgePixels, 0, width, 0, 0, width, height)
            canvas.drawBitmap(edgeBitmap, 0f, 0f, focusPeakingPaint)
            edgeBitmap.recycle()

            return resultBitmap
        } catch (e: Exception) {
            Log.e(TAG, "Error processing focus peaking", e)
            return null
        }
    }

    // Private drawing methods

    private fun drawHistogram(canvas: Canvas, viewWidth: Int, viewHeight: Int) {
        histogramData?.let { histogram ->
            val left = viewWidth - HISTOGRAM_WIDTH - HISTOGRAM_MARGIN
            val top = HISTOGRAM_MARGIN
            val right = left + HISTOGRAM_WIDTH
            val bottom = top + HISTOGRAM_HEIGHT

            // Draw background
            canvas.drawRect(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat(), backgroundPaint)

            // Find max value for scaling
            val maxValue = histogram.maxOrNull() ?: 1

            // Draw histogram bars
            val barWidth = HISTOGRAM_WIDTH.toFloat() / histogram.size
            for (i in histogram.indices) {
                val barHeight = (histogram[i].toFloat() / maxValue) * HISTOGRAM_HEIGHT
                val barLeft = left + i * barWidth
                val barTop = bottom - barHeight
                val barRight = barLeft + barWidth

                canvas.drawRect(barLeft, barTop, barRight, bottom.toFloat(), histogramPaint)
            }

            // Draw border
            canvas.drawRect(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat(),
                Paint().apply {
                    color = Color.WHITE
                    style = Paint.Style.STROKE
                    strokeWidth = 2f
                })
        }
    }

    private fun drawExposureMeter(canvas: Canvas, viewWidth: Int, viewHeight: Int) {
        val left = (viewWidth - EXPOSURE_METER_WIDTH) / 2
        val top = viewHeight - EXPOSURE_METER_HEIGHT - 100
        val right = left + EXPOSURE_METER_WIDTH
        val bottom = top + EXPOSURE_METER_HEIGHT

        // Draw background
        canvas.drawRect(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat(), backgroundPaint)

        // Draw exposure level
        val exposureWidth = (exposureLevel * EXPOSURE_METER_WIDTH).toInt()
        val meterColor = when {
            exposureLevel < 0.3f -> Color.BLUE
            exposureLevel > 0.7f -> Color.RED
            else -> Color.GREEN
        }

        meterPaint.color = meterColor
        canvas.drawRect(left.toFloat(), top.toFloat(),
            (left + exposureWidth).toFloat(), bottom.toFloat(), meterPaint)

        // Draw center mark
        val centerX = left + EXPOSURE_METER_WIDTH / 2
        canvas.drawLine(centerX.toFloat(), (top - 5).toFloat(),
            centerX.toFloat(), (bottom + 5).toFloat(),
            Paint().apply {
                color = Color.WHITE
                strokeWidth = 2f
            })

        // Draw EV text
        val evValue = (exposureLevel - 0.5f) * 6f // -3 to +3 EV range
        val evText = String.format("%.1f EV", evValue)
        canvas.drawText(evText, left.toFloat(), (top - 10).toFloat(), textPaint)
    }

    private fun drawProfessionalInfoOverlay(canvas: Canvas, viewWidth: Int, viewHeight: Int) {
        currentControlsState?.let { state ->
            val margin = 16f
            var yPosition = 50f

            // Background for info panel
            val panelWidth = 300f
            val panelHeight = 200f
            canvas.drawRect(margin, yPosition - 30f, margin + panelWidth, yPosition + panelHeight, backgroundPaint)

            // Manual mode indicator
            val modeText = if (state.isManualModeEnabled) "MANUAL" else "AUTO"
            canvas.drawText(modeText, margin + 10f, yPosition, textPaint)
            yPosition += 35f

            if (state.isManualModeEnabled) {
                // ISO
                val isoText = state.manualIso?.let { "ISO: $it" } ?: "ISO: AUTO"
                canvas.drawText(isoText, margin + 10f, yPosition, textPaint)
                yPosition += 30f

                // Shutter speed
                val shutterText = state.manualShutterSpeed?.let {
                    "SHUTTER: ${formatShutterSpeed(it)}"
                } ?: "SHUTTER: AUTO"
                canvas.drawText(shutterText, margin + 10f, yPosition, textPaint)
                yPosition += 30f

                // Focus distance
                val focusText = state.manualFocusDistance?.let {
                    "FOCUS: ${String.format("%.2f", it)}"
                } ?: "FOCUS: AUTO"
                canvas.drawText(focusText, margin + 10f, yPosition, textPaint)
                yPosition += 30f

                // White balance
                val wbText = state.manualWhiteBalance?.let {
                    "WB: ${it}K"
                } ?: "WB: AUTO"
                canvas.drawText(wbText, margin + 10f, yPosition, textPaint)
                yPosition += 30f

                // Exposure compensation
                if (state.exposureCompensation != 0) {
                    val evSteps = state.exposureCompensation / 3.0
                    val evText = "EV: ${String.format("%+.1f", evSteps)}"
                    canvas.drawText(evText, margin + 10f, yPosition, textPaint)
                }
            }
        }
    }

    private fun formatShutterSpeed(shutterSpeedNs: Long): String {
        val seconds = shutterSpeedNs / 1000000000.0
        return when {
            seconds >= 1.0 -> "${seconds.roundToInt()}s"
            seconds >= 0.1 -> "${(seconds * 10).roundToInt() / 10.0}s"
            else -> "1/${(1.0 / seconds).roundToInt()}s"
        }
    }

    private fun getGrayscaleValue(pixel: Int): Int {
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        return ((0.299 * r + 0.587 * g + 0.114 * b).toInt()).coerceIn(0, 255)
    }
}