package com.customcamera.app.gallery

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Matrix
import android.graphics.RectF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.min

/**
 * ImageView with pinch-to-zoom, panning and double-tap zoom, designed to live
 * inside a ViewPager2: while zoomed it keeps horizontal drags for itself, and
 * releases them to the pager at the image edge.
 */
class ZoomableImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : AppCompatImageView(context, attrs) {

    /** Single tap (used by the viewer to toggle its chrome). */
    var onSingleTap: (() -> Unit)? = null

    private val baseMatrix = Matrix()
    private val userMatrix = Matrix()
    private val drawMatrix = Matrix()
    private val values = FloatArray(9)
    private val rect = RectF()
    private var animator: ValueAnimator? = null

    val currentScale: Float
        get() {
            userMatrix.getValues(values)
            return values[Matrix.MSCALE_X]
        }

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val target = (currentScale * detector.scaleFactor).coerceIn(MIN_SCALE * 0.9f, MAX_SCALE)
            val factor = target / currentScale
            userMatrix.postScale(factor, factor, detector.focusX, detector.focusY)
            applyMatrix()
            return true
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            if (currentScale < MIN_SCALE) animateTo(MIN_SCALE, width / 2f, height / 2f)
        }
    })

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            onSingleTap?.invoke()
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            animateTo(if (currentScale > 1.05f) MIN_SCALE else DOUBLE_TAP_SCALE, e.x, e.y)
            return true
        }

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
            if (currentScale <= 1.01f) return false
            userMatrix.postTranslate(-dx, -dy)
            applyMatrix()
            return true
        }
    })

    init {
        scaleType = ScaleType.MATRIX
    }

    override fun setImageBitmap(bm: android.graphics.Bitmap?) {
        super.setImageBitmap(bm)
        resetZoom()
    }

    fun resetZoom() {
        animator?.cancel()
        userMatrix.reset()
        computeBaseMatrix()
        applyMatrix()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        computeBaseMatrix()
        applyMatrix()
    }

    /** Fit-centre the drawable. */
    private fun computeBaseMatrix() {
        val d = drawable ?: return
        if (width == 0 || height == 0 || d.intrinsicWidth <= 0 || d.intrinsicHeight <= 0) return
        val scale = min(width.toFloat() / d.intrinsicWidth, height.toFloat() / d.intrinsicHeight)
        baseMatrix.reset()
        baseMatrix.postScale(scale, scale)
        baseMatrix.postTranslate((width - d.intrinsicWidth * scale) / 2f, (height - d.intrinsicHeight * scale) / 2f)
    }

    /** Clamps translation so the image never drifts off-screen, then draws. */
    private fun applyMatrix() {
        val d = drawable ?: return
        drawMatrix.set(baseMatrix)
        drawMatrix.postConcat(userMatrix)
        rect.set(0f, 0f, d.intrinsicWidth.toFloat(), d.intrinsicHeight.toFloat())
        drawMatrix.mapRect(rect)
        var dx = 0f
        var dy = 0f
        if (rect.width() <= width) dx = (width - rect.width()) / 2f - rect.left
        else if (rect.left > 0) dx = -rect.left
        else if (rect.right < width) dx = width - rect.right
        if (rect.height() <= height) dy = (height - rect.height()) / 2f - rect.top
        else if (rect.top > 0) dy = -rect.top
        else if (rect.bottom < height) dy = height - rect.bottom
        if (dx != 0f || dy != 0f) {
            userMatrix.postTranslate(dx, dy)
            drawMatrix.postTranslate(dx, dy)
        }
        imageMatrix = drawMatrix
    }

    private fun animateTo(target: Float, focusX: Float, focusY: Float) {
        animator?.cancel()
        val start = currentScale
        animator = ValueAnimator.ofFloat(start, target).apply {
            duration = 220
            addUpdateListener {
                val next = it.animatedValue as Float
                val factor = next / currentScale
                userMatrix.postScale(factor, factor, focusX, focusY)
                applyMatrix()
            }
            start()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        // Keep horizontal drags while zoomed unless the image is already at its edge.
        val zoomed = currentScale > 1.01f
        val atEdge = drawable?.let {
            rect.set(0f, 0f, it.intrinsicWidth.toFloat(), it.intrinsicHeight.toFloat())
            drawMatrix.mapRect(rect)
            rect.left >= -1f || rect.right <= width + 1f
        } ?: true
        parent?.requestDisallowInterceptTouchEvent(zoomed && (!atEdge || event.pointerCount > 1))
        if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }

    companion object {
        private const val MIN_SCALE = 1f
        private const val MAX_SCALE = 5f
        private const val DOUBLE_TAP_SCALE = 2.5f
    }
}
