package com.customcamera.app.barcode

import android.content.Context
import android.graphics.*
import android.util.Log
import android.view.View

/**
 * BarcodeOverlayView draws bounding boxes and highlights
 * around detected barcodes on the camera preview.
 */
class BarcodeOverlayView(context: Context) : View(context) {

    private val paint = Paint().apply {
        color = Color.GREEN
        strokeWidth = 6f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 24f
        isAntiAlias = true
        style = Paint.Style.FILL
    }

    private val backgroundPaint = Paint().apply {
        color = Color.BLACK
        alpha = 180
        style = Paint.Style.FILL
    }

    private var detectedBarcodes: List<DetectedBarcode> = emptyList()
    private var isOverlayEnabled: Boolean = true

    /**
     * Update the barcodes to display
     */
    fun updateBarcodes(barcodes: List<DetectedBarcode>) {
        detectedBarcodes = barcodes
        invalidate() // Trigger redraw

        Log.d(TAG, "Updated overlay with ${barcodes.size} barcodes")
    }

    /**
     * Clear all barcode highlights
     */
    fun clearBarcodes() {
        if (detectedBarcodes.isNotEmpty()) {
            detectedBarcodes = emptyList()
            invalidate()
            Log.d(TAG, "Cleared barcode overlay")
        }
    }

    /**
     * Enable or disable the overlay
     */
    fun setOverlayEnabled(enabled: Boolean) {
        if (isOverlayEnabled != enabled) {
            isOverlayEnabled = enabled
            visibility = if (enabled) VISIBLE else GONE
            Log.i(TAG, "Barcode overlay ${if (enabled) "enabled" else "disabled"}")
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (!isOverlayEnabled || detectedBarcodes.isEmpty()) {
            return
        }

        // Draw highlights for each detected barcode
        detectedBarcodes.forEach { barcode ->
            drawBarcodeHighlight(canvas, barcode)
        }
    }

    private fun drawBarcodeHighlight(canvas: Canvas, barcode: DetectedBarcode) {
        val bounds = barcode.boundingBox

        // Draw bounding box
        canvas.drawRect(bounds, paint)

        // Draw corner indicators
        drawCornerIndicators(canvas, barcode.cornerPoints)

        // Draw barcode data text
        if (barcode.data.isNotEmpty()) {
            drawBarcodeText(canvas, barcode.data, bounds)
        }

        // Draw format indicator
        drawFormatIndicator(canvas, barcode.format, bounds)
    }

    private fun drawCornerIndicators(canvas: Canvas, corners: Array<Point>) {
        val cornerSize = 20f

        corners.forEach { corner ->
            // Draw small squares at corners
            canvas.drawRect(
                corner.x - cornerSize / 2,
                corner.y - cornerSize / 2,
                corner.x + cornerSize / 2,
                corner.y + cornerSize / 2,
                paint
            )
        }
    }

    private fun drawBarcodeText(canvas: Canvas, text: String, bounds: Rect) {
        val maxWidth = bounds.width() - 20f
        val truncatedText = if (text.length > 30) {
            text.substring(0, 27) + "..."
        } else {
            text
        }

        // Calculate text position
        val textX = bounds.left + 10f
        val textY = bounds.top - 10f

        // Draw background for text
        val textBounds = Rect()
        textPaint.getTextBounds(truncatedText, 0, truncatedText.length, textBounds)

        canvas.drawRect(
            textX - 5f,
            textY - textBounds.height() - 5f,
            textX + textBounds.width() + 5f,
            textY + 5f,
            backgroundPaint
        )

        // Draw text
        canvas.drawText(truncatedText, textX, textY, textPaint)
    }

    private fun drawFormatIndicator(canvas: Canvas, format: String, bounds: Rect) {
        val indicatorText = format.replace("_", " ")
        val textX = bounds.right - 80f
        val textY = bounds.bottom + 30f

        // Draw format indicator
        val textBounds = Rect()
        textPaint.getTextBounds(indicatorText, 0, indicatorText.length, textBounds)

        canvas.drawRect(
            textX - 5f,
            textY - textBounds.height() - 5f,
            textX + textBounds.width() + 5f,
            textY + 5f,
            backgroundPaint
        )

        canvas.drawText(indicatorText, textX, textY, textPaint)
    }

    /**
     * Set highlight color
     */
    fun setHighlightColor(color: Int) {
        paint.color = color
        invalidate()
    }

    /**
     * Set text size for barcode data
     */
    fun setTextSize(sizeSp: Float) {
        textPaint.textSize = sizeSp * resources.displayMetrics.scaledDensity
        invalidate()
    }

    companion object {
        private const val TAG = "BarcodeOverlayView"
    }
}