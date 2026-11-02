package com.customcamera.app.gallery

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.withSave
import com.customcamera.app.core.CropAspect
import com.customcamera.app.core.CropHandle
import com.customcamera.app.core.CropMath
import com.customcamera.app.core.CropRect
import com.customcamera.app.core.PhotoEdit
import kotlin.math.min

/**
 * Shows the photo being edited, fitted inside the view's padding with the current
 * rotation/mirror applied, and an interactive crop rectangle on top of it:
 * corner and edge handles with 48dp touch targets, drag-inside-to-move, a dimmed
 * outside area and rule-of-thirds guides while a drag is in progress.
 *
 * The edit is held as a [PhotoEdit] whose crop is normalized to the displayed
 * image, so it is independent of view size and preview resolution. All geometry
 * lives in [CropMath]; this class only maps between view pixels and that model.
 */
class CropOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    /** Called after any change to [edit] made by touch or by the public operations. */
    var onEditChanged: ((PhotoEdit) -> Unit)? = null

    /** The current edit. Setting it (e.g. to restore state) does not fire [onEditChanged]. */
    var edit: PhotoEdit = PhotoEdit()
        set(value) {
            field = value
            updateLayoutRects()
            invalidate()
        }

    private var bitmap: Bitmap? = null

    private val density = resources.displayMetrics.density
    private val touchRadius = TOUCH_TARGET_DP * density / 2f
    private val minCropSize = MIN_CROP_DP * density
    private val handleLength = HANDLE_LENGTH_DP * density
    private val handleThickness = HANDLE_THICKNESS_DP * density

    /** Displayed image bounds in view pixels (fitted into the padded area). */
    private var imageBounds = CropRect(0f, 0f, 0f, 0f)
    private val imageMatrix = Matrix()

    private var activeHandle: CropHandle? = null
    private var dragStart = CropRect(0f, 0f, 0f, 0f)
    private var downX = 0f
    private var downY = 0f

    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val scrimPaint = Paint().apply { color = SCRIM_COLOR }
    private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
        strokeWidth = FRAME_DP * density
    }
    private val guidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = GUIDE_COLOR
        strokeWidth = GUIDE_DP * density
    }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }
    private val tmpRect = RectF()

    /** Sets the (EXIF-oriented) preview bitmap. Resolution may differ from the saved image. */
    fun setImage(image: Bitmap?) {
        bitmap = image
        updateLayoutRects()
        invalidate()
    }

    val hasImage: Boolean get() = bitmap != null

    /** Applies a crop aspect preset, refitting the crop as large as possible around its centre. */
    fun setAspect(aspect: CropAspect) {
        val frame = displayFrame() ?: run {
            edit = edit.copy(aspect = aspect)
            return
        }
        val current = CropMath.denormalize(edit.crop, frame)
        val ratio = aspect.ratio(frame.width, frame.height)
        val fitted = if (ratio == null) current else CropMath.fit(frame, ratio, current.centerX, current.centerY)
        commit(edit.copy(aspect = aspect, crop = CropMath.normalize(fitted, frame)))
    }

    /** Turns the image (and the crop with it) 90° clockwise; ratio presets swap orientation. */
    fun rotateClockwise() = commit(
        PhotoEdit(
            transform = edit.transform.rotatedClockwise(),
            crop = edit.crop.rotatedClockwise(),
            aspect = edit.aspect.rotated(),
        ),
    )

    /** Mirrors the image (and the crop with it) left-right. */
    fun flipHorizontally() = commit(
        edit.copy(transform = edit.transform.flippedHorizontally(), crop = edit.crop.flippedHorizontally()),
    )

    /** Back to the unedited image with a free crop. */
    fun reset() = commit(PhotoEdit())

    private fun commit(next: PhotoEdit) {
        edit = next
        onEditChanged?.invoke(next)
    }

    /** The displayed image in its own pixels (preview resolution), or null before an image is set. */
    private fun displayFrame(): CropRect? {
        val b = bitmap ?: return null
        val (dw, dh) = edit.transform.displaySize(b.width, b.height)
        return CropRect(0f, 0f, dw.toFloat(), dh.toFloat())
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateLayoutRects()
    }

    /** Fits the transformed image into the padded area and rebuilds the draw matrix. */
    private fun updateLayoutRects() {
        val b = bitmap
        val frame = displayFrame()
        val availW = (width - paddingLeft - paddingRight).toFloat()
        val availH = (height - paddingTop - paddingBottom).toFloat()
        if (b == null || frame == null || availW <= 0f || availH <= 0f) {
            imageBounds = CropRect(0f, 0f, 0f, 0f)
            return
        }
        val scale = min(availW / frame.width, availH / frame.height)
        val left = paddingLeft + (availW - frame.width * scale) / 2f
        val top = paddingTop + (availH - frame.height * scale) / 2f
        imageBounds = CropRect(left, top, left + frame.width * scale, top + frame.height * scale)

        // Bitmap → display orientation (rotate, move back to the origin, mirror) → view.
        imageMatrix.reset()
        imageMatrix.postRotate(edit.transform.rotation.toFloat())
        tmpRect.set(0f, 0f, b.width.toFloat(), b.height.toFloat())
        imageMatrix.mapRect(tmpRect)
        imageMatrix.postTranslate(-tmpRect.left, -tmpRect.top)
        if (edit.transform.flipped) {
            imageMatrix.postScale(-1f, 1f)
            imageMatrix.postTranslate(frame.width, 0f)
        }
        imageMatrix.postScale(scale, scale)
        imageMatrix.postTranslate(left, top)
        updateGestureExclusion()
    }

    private fun cropInView(): CropRect = CropMath.denormalize(edit.crop, imageBounds)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val b = bitmap ?: return
        if (imageBounds.width <= 0f) return
        canvas.drawBitmap(b, imageMatrix, bitmapPaint)

        val crop = cropInView()
        tmpRect.set(crop.left, crop.top, crop.right, crop.bottom)

        // Dim everything outside the crop.
        canvas.withSave {
            clipOutRect(tmpRect)
            drawRect(imageBounds.left, imageBounds.top, imageBounds.right, imageBounds.bottom, scrimPaint)
        }

        if (activeHandle != null) drawThirds(canvas, crop)
        canvas.drawRect(tmpRect, framePaint)
        drawHandles(canvas, crop)
    }

    private fun drawThirds(canvas: Canvas, crop: CropRect) {
        for (i in 1..2) {
            val x = crop.left + crop.width * i / 3f
            val y = crop.top + crop.height * i / 3f
            canvas.drawLine(x, crop.top, x, crop.bottom, guidePaint)
            canvas.drawLine(crop.left, y, crop.right, y, guidePaint)
        }
    }

    /** L-shaped corner brackets and short bars at edge midpoints, drawn just outside the frame. */
    private fun drawHandles(canvas: Canvas, crop: CropRect) {
        val t = handleThickness
        val len = min(handleLength, min(crop.width, crop.height) / 2f)
        // Corners: (x, y, horizontal direction, vertical direction into the crop).
        for ((x, y, sx, sy) in listOf(
            Corner(crop.left, crop.top, 1f, 1f),
            Corner(crop.right, crop.top, -1f, 1f),
            Corner(crop.left, crop.bottom, 1f, -1f),
            Corner(crop.right, crop.bottom, -1f, -1f),
        )) {
            drawBar(canvas, x - sx * t, y - sy * t, x + sx * len, y)
            drawBar(canvas, x - sx * t, y - sy * t, x, y + sy * len)
        }
        val half = len / 2f
        drawBar(canvas, crop.centerX - half, crop.top - t, crop.centerX + half, crop.top)
        drawBar(canvas, crop.centerX - half, crop.bottom, crop.centerX + half, crop.bottom + t)
        drawBar(canvas, crop.left - t, crop.centerY - half, crop.left, crop.centerY + half)
        drawBar(canvas, crop.right, crop.centerY - half, crop.right + t, crop.centerY + half)
    }

    private data class Corner(val x: Float, val y: Float, val sx: Float, val sy: Float)

    /** Filled rectangle between two corners given in any order. */
    private fun drawBar(canvas: Canvas, x0: Float, y0: Float, x1: Float, y1: Float) {
        canvas.drawRect(min(x0, x1), min(y0, y1), maxOf(x0, x1), maxOf(y0, y1), handlePaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled || bitmap == null || imageBounds.width <= 0f) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val handle = CropMath.hitTest(cropInView(), event.x, event.y, touchRadius) ?: return false
                activeHandle = handle
                dragStart = cropInView()
                downX = event.x
                downY = event.y
                // Keep the gesture even if a parent would like to scroll.
                parent?.requestDisallowInterceptTouchEvent(true)
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val handle = activeHandle ?: return false
                val frame = displayFrame() ?: return false
                val moved = CropMath.drag(
                    start = dragStart,
                    handle = handle,
                    dx = event.x - downX,
                    dy = event.y - downY,
                    bounds = imageBounds,
                    minSize = minCropSize,
                    ratio = edit.aspect.ratio(frame.width, frame.height),
                )
                edit = edit.copy(crop = CropMath.normalize(moved, imageBounds))
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (activeHandle == null) return false
                activeHandle = null
                parent?.requestDisallowInterceptTouchEvent(false)
                // Report once per gesture rather than on every move event.
                onEditChanged?.invoke(edit)
                invalidate()
                if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
                return true
            }
        }
        return activeHandle != null
    }

    override fun performClick(): Boolean = super.performClick()

    /**
     * Keeps the left/right handles usable under gesture navigation: without this a drag
     * starting near the screen edge becomes a system Back gesture (API 29+). Refreshed
     * from [updateLayoutRects], which runs on every edit, so the zones follow the handles.
     */
    private fun updateGestureExclusion() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        if (imageBounds.width <= 0f) {
            systemGestureExclusionRects = emptyList()
            return
        }
        val crop = cropInView()
        val r = touchRadius.toInt()
        systemGestureExclusionRects = listOf(crop.left, crop.right).flatMap { x ->
            listOf(crop.top, crop.centerY, crop.bottom).map { y ->
                Rect(x.toInt() - r, y.toInt() - r, x.toInt() + r, y.toInt() + r)
            }
        }
    }

    private companion object {
        const val TOUCH_TARGET_DP = 48f
        const val MIN_CROP_DP = 64f
        const val HANDLE_LENGTH_DP = 22f
        const val HANDLE_THICKNESS_DP = 3f
        const val FRAME_DP = 1.5f
        const val GUIDE_DP = 1f
        const val SCRIM_COLOR = 0xB3000000.toInt()
        const val GUIDE_COLOR = 0x99FFFFFF.toInt()
    }
}
