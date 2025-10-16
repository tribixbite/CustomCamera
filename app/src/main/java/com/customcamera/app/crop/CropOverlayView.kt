package com.customcamera.app.crop

import android.content.Context
import android.graphics.*
import android.util.Log
import android.view.MotionEvent
import android.view.View
import androidx.camera.core.Camera
import com.customcamera.app.plugins.CropPlugin
import kotlin.math.abs

/**
 * CropOverlayView provides interactive crop interface with touch dragging
 */
class CropOverlayView(context: Context) : View(context) {

    private val cropPaint = Paint().apply {
        color = Color.WHITE
        strokeWidth = 4f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private val overlayPaint = Paint().apply {
        color = Color.BLACK
        alpha = 128
        style = Paint.Style.FILL
    }

    private val handlePaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private var cropArea: RectF = RectF(0.25f, 0.25f, 0.75f, 0.75f)
    private var aspectRatio: CropPlugin.AspectRatio = CropPlugin.AspectRatio.FREE
    private var aspectRatioLocked: Boolean = false

    // Touch handling
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var activeHandle: Handle? = null
    private val handleRadius = 30f
    private val touchSlop = 50f

    enum class Handle {
        TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT,
        LEFT, TOP, RIGHT, BOTTOM, CENTER
    }

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

        // Draw dimmed overlay outside crop area
        canvas.drawRect(0f, 0f, viewWidth, cropRect.top, overlayPaint)
        canvas.drawRect(0f, cropRect.bottom, viewWidth, viewHeight, overlayPaint)
        canvas.drawRect(0f, cropRect.top, cropRect.left, cropRect.bottom, overlayPaint)
        canvas.drawRect(cropRect.right, cropRect.top, viewWidth, cropRect.bottom, overlayPaint)

        // Draw crop rectangle border
        canvas.drawRect(cropRect, cropPaint)

        // Draw rule of thirds grid lines
        val gridPaint = Paint(cropPaint).apply { alpha = 100 }
        val gridX1 = cropRect.left + cropRect.width() / 3f
        val gridX2 = cropRect.left + (cropRect.width() * 2f) / 3f
        val gridY1 = cropRect.top + cropRect.height() / 3f
        val gridY2 = cropRect.top + (cropRect.height() * 2f) / 3f

        canvas.drawLine(gridX1, cropRect.top, gridX1, cropRect.bottom, gridPaint)
        canvas.drawLine(gridX2, cropRect.top, gridX2, cropRect.bottom, gridPaint)
        canvas.drawLine(cropRect.left, gridY1, cropRect.right, gridY1, gridPaint)
        canvas.drawLine(cropRect.left, gridY2, cropRect.right, gridY2, gridPaint)

        // Draw corner handles
        drawHandle(canvas, cropRect.left, cropRect.top) // Top-left
        drawHandle(canvas, cropRect.right, cropRect.top) // Top-right
        drawHandle(canvas, cropRect.left, cropRect.bottom) // Bottom-left
        drawHandle(canvas, cropRect.right, cropRect.bottom) // Bottom-right

        // Draw edge handles
        drawHandle(canvas, cropRect.centerX(), cropRect.top) // Top
        drawHandle(canvas, cropRect.centerX(), cropRect.bottom) // Bottom
        drawHandle(canvas, cropRect.left, cropRect.centerY()) // Left
        drawHandle(canvas, cropRect.right, cropRect.centerY()) // Right
    }

    private fun drawHandle(canvas: Canvas, x: Float, y: Float) {
        canvas.drawCircle(x, y, handleRadius, handlePaint)
    }

    fun setCropArea(area: RectF) {
        cropArea = RectF(area)
        invalidate()
    }

    fun setAspectRatio(ratio: CropPlugin.AspectRatio) {
        aspectRatio = ratio
        Log.d(TAG, "Aspect ratio set to: ${ratio.displayName}")
        invalidate()
    }

    fun setAspectRatioLocked(locked: Boolean) {
        aspectRatioLocked = locked
        Log.d(TAG, "Aspect ratio locked: $locked")
    }

    fun getCropArea(): RectF = RectF(cropArea)

    fun updateForCamera(camera: Camera) {
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = x
                lastTouchY = y
                activeHandle = findTouchedHandle(x, y, viewWidth, viewHeight)
                return activeHandle != null
            }

            MotionEvent.ACTION_MOVE -> {
                if (activeHandle != null) {
                    val dx = (x - lastTouchX) / viewWidth
                    val dy = (y - lastTouchY) / viewHeight

                    updateCropArea(dx, dy)

                    lastTouchX = x
                    lastTouchY = y
                    invalidate()
                    return true
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                activeHandle = null
                return true
            }
        }

        return super.onTouchEvent(event)
    }

    private fun findTouchedHandle(x: Float, y: Float, viewWidth: Float, viewHeight: Float): Handle? {
        val cropRect = RectF(
            cropArea.left * viewWidth,
            cropArea.top * viewHeight,
            cropArea.right * viewWidth,
            cropArea.bottom * viewHeight
        )

        // Check corner handles first
        if (isNearPoint(x, y, cropRect.left, cropRect.top)) return Handle.TOP_LEFT
        if (isNearPoint(x, y, cropRect.right, cropRect.top)) return Handle.TOP_RIGHT
        if (isNearPoint(x, y, cropRect.left, cropRect.bottom)) return Handle.BOTTOM_LEFT
        if (isNearPoint(x, y, cropRect.right, cropRect.bottom)) return Handle.BOTTOM_RIGHT

        // Check edge handles
        if (isNearPoint(x, y, cropRect.centerX(), cropRect.top)) return Handle.TOP
        if (isNearPoint(x, y, cropRect.centerX(), cropRect.bottom)) return Handle.BOTTOM
        if (isNearPoint(x, y, cropRect.left, cropRect.centerY())) return Handle.LEFT
        if (isNearPoint(x, y, cropRect.right, cropRect.centerY())) return Handle.RIGHT

        // Check if inside crop area (for dragging entire crop)
        if (cropRect.contains(x, y)) return Handle.CENTER

        return null
    }

    private fun isNearPoint(x: Float, y: Float, px: Float, py: Float): Boolean {
        return abs(x - px) <= touchSlop && abs(y - py) <= touchSlop
    }

    private fun updateCropArea(dx: Float, dy: Float) {
        val newCropArea = RectF(cropArea)

        when (activeHandle) {
            Handle.TOP_LEFT -> {
                newCropArea.left += dx
                newCropArea.top += dy
            }
            Handle.TOP_RIGHT -> {
                newCropArea.right += dx
                newCropArea.top += dy
            }
            Handle.BOTTOM_LEFT -> {
                newCropArea.left += dx
                newCropArea.bottom += dy
            }
            Handle.BOTTOM_RIGHT -> {
                newCropArea.right += dx
                newCropArea.bottom += dy
            }
            Handle.LEFT -> newCropArea.left += dx
            Handle.TOP -> newCropArea.top += dy
            Handle.RIGHT -> newCropArea.right += dx
            Handle.BOTTOM -> newCropArea.bottom += dy
            Handle.CENTER -> {
                // Move entire crop area
                newCropArea.offset(dx, dy)
            }
            null -> return
        }

        // Constrain to bounds [0, 1]
        newCropArea.left = newCropArea.left.coerceIn(0f, newCropArea.right - 0.1f)
        newCropArea.top = newCropArea.top.coerceIn(0f, newCropArea.bottom - 0.1f)
        newCropArea.right = newCropArea.right.coerceIn(newCropArea.left + 0.1f, 1f)
        newCropArea.bottom = newCropArea.bottom.coerceIn(newCropArea.top + 0.1f, 1f)

        // Apply aspect ratio constraint if locked
        if (aspectRatioLocked && aspectRatio.ratio != null && activeHandle != Handle.CENTER) {
            applyAspectRatioConstraint(newCropArea, aspectRatio.ratio!!)
        }

        cropArea = newCropArea
        onCropAreaChanged?.invoke(cropArea)
    }

    private fun applyAspectRatioConstraint(area: RectF, targetRatio: Float) {
        val currentWidth = area.width()
        val currentHeight = area.height()
        val currentRatio = currentWidth / currentHeight

        if (abs(currentRatio - targetRatio) > 0.01f) {
            when (activeHandle) {
                Handle.TOP_LEFT, Handle.TOP_RIGHT, Handle.BOTTOM_LEFT, Handle.BOTTOM_RIGHT -> {
                    // Adjust height to match width and aspect ratio
                    val newHeight = currentWidth / targetRatio
                    when (activeHandle) {
                        Handle.TOP_LEFT, Handle.TOP_RIGHT -> {
                            area.top = area.bottom - newHeight
                        }
                        Handle.BOTTOM_LEFT, Handle.BOTTOM_RIGHT -> {
                            area.bottom = area.top + newHeight
                        }
                        else -> {}
                    }
                }
                Handle.LEFT, Handle.RIGHT -> {
                    // Adjust height to maintain aspect ratio
                    val newHeight = currentWidth / targetRatio
                    val centerY = area.centerY()
                    area.top = centerY - newHeight / 2f
                    area.bottom = centerY + newHeight / 2f
                }
                Handle.TOP, Handle.BOTTOM -> {
                    // Adjust width to maintain aspect ratio
                    val newWidth = currentHeight * targetRatio
                    val centerX = area.centerX()
                    area.left = centerX - newWidth / 2f
                    area.right = centerX + newWidth / 2f
                }
                else -> {}
            }
        }
    }

    // Callback for when crop area changes
    var onCropAreaChanged: ((RectF) -> Unit)? = null

    companion object {
        private const val TAG = "CropOverlayView"
    }
}