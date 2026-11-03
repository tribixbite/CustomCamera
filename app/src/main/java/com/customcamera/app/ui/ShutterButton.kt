package com.customcamera.app.ui

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import com.customcamera.app.R
import kotlin.math.min

/**
 * Custom-drawn shutter: white disc for photo, red disc for video, red rounded
 * square while recording, and a spinning arc while a long capture (night
 * mode) is processing. Morphs between states with a short animation.
 */
class ShutterButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    enum class Style { PHOTO, VIDEO, RECORDING }

    var style: Style = Style.PHOTO
        set(value) {
            if (field == value) return
            field = value
            animateMorph()
            updateDescription()
        }

    /** Shows a spinner and blocks the press animation while a capture is processing. */
    var busy: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            if (value) spinner.start() else spinner.cancel()
            invalidate()
        }

    private val density = resources.displayMetrics.density
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f * density
        color = Color.WHITE
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f * density
        strokeCap = Paint.Cap.ROUND
        color = ContextCompat.getColor(context, R.color.accent)
    }
    private val red = ContextCompat.getColor(context, R.color.record_red)
    private val rect = RectF()

    /** 0 = circle, 1 = rounded square (recording). */
    private var morph = 0f
    private var pressScale = 1f
    private var spinAngle = 0f

    private val spinner = ValueAnimator.ofFloat(0f, 360f).apply {
        duration = 900
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { spinAngle = it.animatedValue as Float; invalidate() }
    }

    init {
        isClickable = true
        isFocusable = true
        updateDescription()
    }

    private fun updateDescription() {
        contentDescription = context.getString(
            when (style) {
                Style.PHOTO -> R.string.cd_take_photo
                Style.VIDEO -> R.string.cd_start_recording
                Style.RECORDING -> R.string.cd_stop_recording
            }
        )
    }

    private fun animateMorph() {
        val target = if (style == Style.RECORDING) 1f else 0f
        ValueAnimator.ofFloat(morph, target).apply {
            duration = 220
            addUpdateListener { morph = it.animatedValue as Float; invalidate() }
        }.start()
    }

    /** Called when the finger lifts (or the gesture is cancelled); ends a long-press burst. */
    var onRelease: (() -> Unit)? = null

    // Click/long-click handling (and performClick) stays in View.onTouchEvent; this only
    // animates and reports the release that ends a hold.
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> animatePress(0.9f)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                animatePress(1f)
                onRelease?.invoke()
            }
        }
        // super.onTouchEvent() dispatches performClick() on ACTION_UP.
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean = super.performClick()

    private fun animatePress(target: Float) {
        ValueAnimator.ofFloat(pressScale, target).apply {
            duration = 110
            addUpdateListener { pressScale = it.animatedValue as Float; invalidate() }
        }.start()
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val outer = min(width, height) / 2f - ringPaint.strokeWidth
        canvas.drawCircle(cx, cy, outer, ringPaint)

        val inner = (outer - 6f * density) * pressScale
        fillPaint.color = if (style == Style.PHOTO) Color.WHITE else red
        // Recording shrinks the disc into a rounded square.
        val half = inner * (1f - 0.45f * morph)
        val corner = half * (1f - 0.7f * morph)
        rect.set(cx - half, cy - half, cx + half, cy + half)
        if (busy) fillPaint.alpha = 110 else fillPaint.alpha = 255
        canvas.drawRoundRect(rect, corner, corner, fillPaint)

        if (busy) {
            rect.set(cx - outer, cy - outer, cx + outer, cy + outer)
            canvas.drawArc(rect, spinAngle, 90f, false, arcPaint)
        }
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = android.widget.Button::class.java.name
    }

    override fun onDetachedFromWindow() {
        spinner.cancel()
        super.onDetachedFromWindow()
    }
}
