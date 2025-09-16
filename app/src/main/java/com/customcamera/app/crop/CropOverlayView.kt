package com.customcamera.app.crop

import android.content.Context
import android.graphics.*
import android.util.Log
import android.view.MotionEvent
import android.view.View
import androidx.camera.core.Camera
import com.customcamera.app.plugins.CropPlugin

/**
 * CropOverlayView provides interactive crop interface
 */
class CropOverlayView(context: Context) : View(context) {

    private val cropPaint = Paint().apply {
        color = Color.WHITE
        strokeWidth = 3f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private val overlayPaint = Paint().apply {
        color = Color.BLACK
        alpha = 128
        style = Paint.Style.FILL
    }

    private var cropArea: RectF = RectF(0.25f, 0.25f, 0.75f, 0.75f)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()

        if (viewWidth <= 0 || viewHeight <= 0) return

        val cropRect = RectF(
            cropArea.left * viewWidth,
            cropArea.top * viewHeight,
            cropArea.right * viewWidth,
            cropArea.bottom * viewHeight
        )

        // Draw overlay
        canvas.drawRect(0f, 0f, viewWidth, cropRect.top, overlayPaint)
        canvas.drawRect(0f, cropRect.bottom, viewWidth, viewHeight, overlayPaint)
        canvas.drawRect(0f, cropRect.top, cropRect.left, cropRect.bottom, overlayPaint)
        canvas.drawRect(cropRect.right, cropRect.top, viewWidth, cropRect.bottom, overlayPaint)

        // Draw crop rectangle
        canvas.drawRect(cropRect, cropPaint)
    }

    fun setCropArea(area: RectF) {
        cropArea = RectF(area)
        invalidate()
    }

    fun setAspectRatio(ratio: CropPlugin.AspectRatio) {
        Log.d("CropOverlayView", "Aspect ratio set to: ${ratio.displayName}")
        invalidate()
    }

    fun setAspectRatioLocked(locked: Boolean) {
        Log.d("CropOverlayView", "Aspect ratio locked: $locked")
    }

    fun getCropArea(): RectF = RectF(cropArea)

    fun updateForCamera(camera: Camera) {
        invalidate()
    }

    companion object {
        private const val TAG = "CropOverlayView"
    }
}