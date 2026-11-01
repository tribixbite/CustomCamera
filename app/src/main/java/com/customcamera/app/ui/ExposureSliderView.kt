package com.customcamera.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.ContextCompat
import com.customcamera.app.R
import kotlin.math.roundToInt

/**
 * Vertical exposure-compensation slider shown next to the focus point.
 * Drag up to brighten. Values are CameraX exposure-compensation indices.
 */
class ExposureSliderView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    /** Called with the new index while dragging. */
    var onValueChanged: ((Int) -> Unit)? = null

    /** Called on touch down/up so the host can postpone auto-hide while dragging. */
    var onInteraction: ((dragging: Boolean) -> Unit)? = null

    private var min = 0
    private var max = 0
    var value: Int = 0
        private set

    private val density = resources.displayMetrics.density
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(150, 255, 255, 255)
        strokeWidth = 2f * density
    }
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ContextCompat.getColor(context, R.color.accent) }
    private val rayPaint = Paint(thumbPaint).apply { strokeWidth = 2f * density; strokeCap = Paint.Cap.ROUND }

    init {
        isFocusable = true
        contentDescription = context.getString(R.string.cd_exposure)
    }

    fun setRange(minIndex: Int, maxIndex: Int, current: Int) {
        min = minIndex
        max = maxIndex
        value = current.coerceIn(min, maxOf(min, max))
        invalidate()
    }

    private val trackPad get() = 14f * density

    private fun valueToY(v: Int): Float {
        if (max <= min) return height / 2f
        val f = (v - min).toFloat() / (max - min)
        return height - trackPad - f * (height - 2 * trackPad)
    }

    private fun yToValue(y: Float): Int {
        if (max <= min) return min
        val f = ((height - trackPad - y) / (height - 2 * trackPad)).coerceIn(0f, 1f)
        return (min + f * (max - min)).roundToInt()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                onInteraction?.invoke(true)
                update(yToValue(event.y))
            }
            MotionEvent.ACTION_MOVE -> update(yToValue(event.y))
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> onInteraction?.invoke(false)
        }
        return true
    }

    private fun update(newValue: Int) {
        if (newValue == value) return
        value = newValue
        performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
        onValueChanged?.invoke(newValue)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val thumbY = valueToY(value)
        val r = 9f * density
        canvas.drawLine(cx, trackPad, cx, thumbY - r - 4 * density, trackPaint)
        canvas.drawLine(cx, thumbY + r + 4 * density, cx, height - trackPad, trackPaint)
        // Sun glyph as the thumb.
        canvas.drawCircle(cx, thumbY, r * 0.55f, thumbPaint)
        for (i in 0 until 8) {
            val a = Math.toRadians(i * 45.0)
            val c = Math.cos(a).toFloat()
            val s = Math.sin(a).toFloat()
            canvas.drawLine(cx + c * r * 0.8f, thumbY + s * r * 0.8f, cx + c * r * 1.15f, thumbY + s * r * 1.15f, rayPaint)
        }
    }

    // Accessibility: expose as an adjustable range so TalkBack users can change exposure.
    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = android.widget.SeekBar::class.java.name
        val type = AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_INT
        info.rangeInfo = if (android.os.Build.VERSION.SDK_INT >= 36) {
            AccessibilityNodeInfo.RangeInfo(type, min.toFloat(), max.toFloat(), value.toFloat())
        } else {
            @Suppress("DEPRECATION")
            AccessibilityNodeInfo.RangeInfo.obtain(type, min.toFloat(), max.toFloat(), value.toFloat())
        }
        if (value < max) info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD)
        if (value > min) info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)
    }

    override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean = when (action) {
        AccessibilityNodeInfo.ACTION_SCROLL_FORWARD -> { update((value + 1).coerceAtMost(max)); true }
        AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD -> { update((value - 1).coerceAtLeast(min)); true }
        else -> super.performAccessibilityAction(action, arguments)
    }
}
