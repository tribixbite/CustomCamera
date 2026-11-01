package com.customcamera.app.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import com.customcamera.app.R
import com.customcamera.app.core.GridGeometry
import com.customcamera.app.core.GridOption
import com.customcamera.app.core.Histogram
import com.customcamera.app.core.OrientationMath
import kotlin.math.abs

/** Composition grid drawn over the preview. */
class GridOverlayView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    var option: GridOption = GridOption.OFF
        set(value) {
            field = value
            visibility = if (value == GridOption.OFF) GONE else VISIBLE
            invalidate()
        }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(140, 255, 255, 255)
        strokeWidth = resources.displayMetrics.density
    }

    init {
        isClickable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val lines = GridGeometry.lines(option, w, h)
        lines.vertical.forEach { canvas.drawLine(it * w, 0f, it * w, h, paint) }
        lines.horizontal.forEach { canvas.drawLine(0f, it * h, w, it * h, paint) }
    }
}

/**
 * Horizon level: a line through the centre that tilts with the device and
 * turns the accent colour when within half a degree of level. Registers its
 * accelerometer listener only while attached and [active].
 */
class LevelView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    View(context, attrs), SensorEventListener {

    var active: Boolean = false
        set(value) {
            field = value
            updateRegistration()
            visibility = if (value) VISIBLE else GONE
        }

    /** Snapped device orientation (0/90/180/270) from the activity's OrientationEventListener. */
    var deviceOrientation: Int = 0

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private var registered = false
    private var tilt = 0f
    private var flat = true
    private val filtered = FloatArray(3)

    private val density = resources.displayMetrics.density
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 2f * density
        strokeCap = Paint.Cap.ROUND
    }
    private val levelColor = ContextCompat.getColor(context, R.color.accent)

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateRegistration()
    }

    override fun onDetachedFromWindow() {
        unregister()
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        updateRegistration()
    }

    private fun updateRegistration() {
        val shouldListen = active && isAttachedToWindow && windowVisibility == VISIBLE && accelerometer != null
        if (shouldListen && !registered) {
            registered = sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)
        } else if (!shouldListen) {
            unregister()
        }
    }

    private fun unregister() {
        if (registered) sensorManager.unregisterListener(this)
        registered = false
    }

    override fun onSensorChanged(event: SensorEvent) {
        // Low-pass filter to steady the line.
        for (i in 0..2) filtered[i] = filtered[i] * 0.8f + event.values[i] * 0.2f
        flat = OrientationMath.isFlat(filtered[0], filtered[1], filtered[2])
        tilt = OrientationMath.tiltFromLevel(OrientationMath.rollDegrees(filtered[0], filtered[1]), deviceOrientation)
        invalidate()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onDraw(canvas: Canvas) {
        if (flat) return
        val cx = width / 2f
        val cy = height / 2f
        val level = abs(tilt) < 0.5f
        linePaint.color = if (level) levelColor else Color.argb(200, 255, 255, 255)
        val half = 56f * density
        val gap = 10f * density
        // Fixed reference ticks on both sides.
        canvas.drawLine(cx - half - 2 * gap - 16 * density, cy, cx - half - 2 * gap, cy, linePaint)
        canvas.drawLine(cx + half + 2 * gap, cy, cx + half + 2 * gap + 16 * density, cy, linePaint)
        canvas.save()
        canvas.rotate(-tilt, cx, cy)
        canvas.drawLine(cx - half, cy, cx + half, cy, linePaint)
        canvas.restore()
    }
}

/**
 * Tap-to-focus ring. Animates in at the touch point, tints by focus result,
 * and shows a lock label for AE/AF lock.
 */
class FocusIndicatorView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        color = Color.WHITE
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.accent)
        textSize = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, 12f, resources.displayMetrics)
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val accent = ContextCompat.getColor(context, R.color.accent)
    private var cx = 0f
    private var cy = 0f
    private var radius = 0f
    private var shown = false
    private var locked = false
    private var animator: ValueAnimator? = null
    private val hideRunnable = Runnable { shown = false; invalidate() }

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    /** Shows the ring at (x, y) in this view's coordinates. */
    fun show(x: Float, y: Float, lock: Boolean) {
        cx = x
        cy = y
        locked = lock
        shown = true
        ringPaint.color = if (lock) accent else Color.WHITE
        animator?.cancel()
        animator = ValueAnimator.ofFloat(48f * density, 34f * density).apply {
            duration = 260
            interpolator = DecelerateInterpolator()
            addUpdateListener { radius = it.animatedValue as Float; invalidate() }
            start()
        }
        removeCallbacks(hideRunnable)
        if (!lock) postDelayed(hideRunnable, HIDE_DELAY_MS)
    }

    fun onFocusResult(success: Boolean) {
        if (!shown) return
        ringPaint.color = if (locked || success) accent else Color.argb(220, 255, 255, 255)
        invalidate()
    }

    fun clear() {
        removeCallbacks(hideRunnable)
        shown = false
        locked = false
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (!shown) return
        canvas.drawCircle(cx, cy, radius, ringPaint)
        if (locked) canvas.drawText(context.getString(R.string.ae_af_lock), cx, cy - radius - 10f * density, textPaint)
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        removeCallbacks(hideRunnable)
        super.onDetachedFromWindow()
    }

    companion object {
        const val HIDE_DELAY_MS = 2_500L
    }
}

/** Live luminance histogram with clipping warnings. */
class HistogramView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private val background = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(110, 0, 0, 0) }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(200, 255, 255, 255) }
    private val clip = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ContextCompat.getColor(context, R.color.record_red) }
    private val path = Path()
    private val bounds = RectF()
    private var values: FloatArray = FloatArray(0)
    private var highlightsClipped = false
    private var shadowsClipped = false

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun update(histogram: Histogram) {
        values = histogram.normalized()
        highlightsClipped = histogram.highlightClipping() > CLIP_THRESHOLD
        shadowsClipped = histogram.shadowClipping() > CLIP_THRESHOLD
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val r = 8f * density
        bounds.set(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(bounds, r, r, background)
        if (values.isEmpty()) return
        val pad = 6f * density
        val w = width - 2 * pad
        val h = height - 2 * pad
        path.reset()
        path.moveTo(pad, pad + h)
        values.forEachIndexed { i, v ->
            path.lineTo(pad + w * i / (values.size - 1), pad + h - v * h)
        }
        path.lineTo(pad + w, pad + h)
        path.close()
        canvas.drawPath(path, fill)
        val bar = 3f * density
        if (shadowsClipped) canvas.drawRect(0f, 0f, bar, height.toFloat(), clip)
        if (highlightsClipped) canvas.drawRect(width - bar, 0f, width.toFloat(), height.toFloat(), clip)
    }

    companion object {
        /** More than 2% of samples in an end bin counts as clipping. */
        private const val CLIP_THRESHOLD = 0.02f
    }
}

/** Draws brackets around detected barcodes, or a centred reticle when none are visible. */
class BarcodeOverlayView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * density
        strokeCap = Paint.Cap.ROUND
        color = ContextCompat.getColor(context, R.color.accent)
    }
    private val reticlePaint = Paint(paint).apply { color = Color.argb(200, 255, 255, 255) }
    private var boxes: List<RectF> = emptyList()
    private val clearRunnable = Runnable { boxes = emptyList(); invalidate() }

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun show(rects: List<RectF>) {
        boxes = rects
        invalidate()
        removeCallbacks(clearRunnable)
        postDelayed(clearRunnable, 600)
    }

    override fun onDraw(canvas: Canvas) {
        if (boxes.isEmpty()) {
            val size = minOf(width, height) * 0.6f
            val l = (width - size) / 2
            val t = (height - size) / 2
            drawBrackets(canvas, RectF(l, t, l + size, t + size), reticlePaint)
        } else {
            boxes.forEach { drawBrackets(canvas, RectF(it).apply { inset(-8 * density, -8 * density) }, paint) }
        }
    }

    private fun drawBrackets(canvas: Canvas, r: RectF, p: Paint) {
        val len = minOf(r.width(), r.height()) * 0.22f
        canvas.drawLine(r.left, r.top, r.left + len, r.top, p)
        canvas.drawLine(r.left, r.top, r.left, r.top + len, p)
        canvas.drawLine(r.right, r.top, r.right - len, r.top, p)
        canvas.drawLine(r.right, r.top, r.right, r.top + len, p)
        canvas.drawLine(r.left, r.bottom, r.left + len, r.bottom, p)
        canvas.drawLine(r.left, r.bottom, r.left, r.bottom - len, p)
        canvas.drawLine(r.right, r.bottom, r.right - len, r.bottom, p)
        canvas.drawLine(r.right, r.bottom, r.right, r.bottom - len, p)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(clearRunnable)
        super.onDetachedFromWindow()
    }
}
