package com.customcamera.app.focus

import android.content.Context
import android.graphics.*
import android.util.Log
import android.view.View
import androidx.camera.core.ImageProxy
import java.nio.ByteBuffer

/**
 * FocusPeakingOverlay provides focus peaking visualization
 * by highlighting sharp edges in the camera preview
 */
class FocusPeakingOverlay(context: Context) : View(context) {

    private val peakingPaint = Paint().apply {
        color = Color.RED
        alpha = 180
        style = Paint.Style.STROKE
        strokeWidth = 2f
        isAntiAlias = true
    }

    private val backgroundPaint = Paint().apply {
        color = Color.BLACK
        alpha = 100
        style = Paint.Style.FILL
    }

    // Focus peaking data
    private var peakingBitmap: Bitmap? = null
    private var isEnabled: Boolean = false
    private var peakingThreshold: Float = 0.3f
    private var peakingColor: Int = Color.RED

    /**
     * Process image frame for focus peaking
     */
    fun processFrameForPeaking(image: ImageProxy) {
        if (!isEnabled) return

        try {
            // Get Y plane (luminance) for edge detection
            val yPlane = image.planes[0]
            val yBuffer = yPlane.buffer
            val width = image.width
            val height = image.height
            val rowStride = yPlane.rowStride

            // Create bitmap for focus peaking overlay
            val bitmap = Bitmap.createBitmap(width / 4, height / 4, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            // Perform edge detection for focus peaking
            detectFocusEdges(yBuffer, width, height, rowStride, canvas)

            peakingBitmap = bitmap
            post { invalidate() } // Update UI on main thread

        } catch (e: Exception) {
            Log.e(TAG, "Error processing frame for focus peaking", e)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (!isEnabled) return

        val bitmap = peakingBitmap ?: return

        // Draw focus peaking overlay
        val destRect = Rect(0, 0, width, height)
        canvas.drawBitmap(bitmap, null, destRect, peakingPaint)
    }

    /**
     * Enable or disable focus peaking
     */
    fun setFocusPeakingEnabled(enabled: Boolean) {
        if (isEnabled != enabled) {
            isEnabled = enabled
            visibility = if (enabled) VISIBLE else GONE

            if (!enabled) {
                peakingBitmap?.recycle()
                peakingBitmap = null
            }

            Log.i(TAG, "Focus peaking ${if (enabled) "enabled" else "disabled"}")
            invalidate()
        }
    }

    /**
     * Set focus peaking color
     */
    fun setPeakingColor(color: Int) {
        if (peakingColor != color) {
            peakingColor = color
            peakingPaint.color = color
            Log.i(TAG, "Focus peaking color changed")
            invalidate()
        }
    }

    /**
     * Set focus peaking threshold
     */
    fun setPeakingThreshold(threshold: Float) {
        val clampedThreshold = threshold.coerceIn(0f, 1f)
        if (peakingThreshold != clampedThreshold) {
            peakingThreshold = clampedThreshold
            Log.i(TAG, "Focus peaking threshold set to: $clampedThreshold")
        }
    }

    /**
     * Get focus peaking status
     */
    fun getFocusPeakingStatus(): Map<String, Any> {
        return mapOf(
            "enabled" to isEnabled,
            "threshold" to peakingThreshold,
            "color" to String.format("#%06X", 0xFFFFFF and peakingColor),
            "hasPeakingData" to (peakingBitmap != null)
        )
    }

    private fun detectFocusEdges(
        yBuffer: ByteBuffer,
        width: Int,
        height: Int,
        rowStride: Int,
        canvas: Canvas
    ) {
        try {
            val downsampleFactor = 4
            val scaledWidth = width / downsampleFactor
            val scaledHeight = height / downsampleFactor

            // Simple edge detection using Sobel-like operator
            for (y in 1 until scaledHeight - 1) {
                for (x in 1 until scaledWidth - 1) {
                    val srcX = x * downsampleFactor
                    val srcY = y * downsampleFactor

                    // Get surrounding pixels
                    val topLeft = getPixelValue(yBuffer, srcX - 1, srcY - 1, rowStride)
                    val top = getPixelValue(yBuffer, srcX, srcY - 1, rowStride)
                    val topRight = getPixelValue(yBuffer, srcX + 1, srcY - 1, rowStride)
                    val left = getPixelValue(yBuffer, srcX - 1, srcY, rowStride)
                    val center = getPixelValue(yBuffer, srcX, srcY, rowStride)
                    val right = getPixelValue(yBuffer, srcX + 1, srcY, rowStride)
                    val bottomLeft = getPixelValue(yBuffer, srcX - 1, srcY + 1, rowStride)
                    val bottom = getPixelValue(yBuffer, srcX, srcY + 1, rowStride)
                    val bottomRight = getPixelValue(yBuffer, srcX + 1, srcY + 1, rowStride)

                    // Sobel edge detection
                    val sobelX = (-topLeft + topRight - 2 * left + 2 * right - bottomLeft + bottomRight).toFloat()
                    val sobelY = (-topLeft - 2 * top - topRight + bottomLeft + 2 * bottom + bottomRight).toFloat()

                    val magnitude = kotlin.math.sqrt(sobelX * sobelX + sobelY * sobelY) / 255f

                    // Highlight areas with high edge magnitude (in focus)
                    if (magnitude > peakingThreshold) {
                        canvas.drawPoint(x.toFloat(), y.toFloat(), peakingPaint)
                    }
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error in edge detection", e)
        }
    }

    private fun getPixelValue(buffer: ByteBuffer, x: Int, y: Int, rowStride: Int): Int {
        return try {
            val index = y * rowStride + x
            if (index < buffer.capacity()) {
                buffer.get(index).toInt() and 0xFF
            } else {
                128 // Default value if out of bounds
            }
        } catch (e: Exception) {
            128
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        peakingBitmap?.recycle()
        peakingBitmap = null
    }

    companion object {
        private const val TAG = "FocusPeakingOverlay"
    }
}