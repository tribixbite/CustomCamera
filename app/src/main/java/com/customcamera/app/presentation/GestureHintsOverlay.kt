package com.customcamera.app.presentation

import android.animation.ValueAnimator
import android.content.Context
import android.content.SharedPreferences
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import kotlin.math.sin

/**
 * Gesture Hints Overlay - Subtle hints showing available gestures
 *
 * Shows:
 * - Tap pattern hints (2x, 3x, 4x taps)
 * - Pinch-to-zoom indicator
 * - Long-press hints
 * - Feature availability
 */
class GestureHintsOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var showHints = false
    private var alpha = 0
    private var pulseAnimation: ValueAnimator? = null
    private var pulseAlpha = 0f

    private val prefs: SharedPreferences = context.getSharedPreferences("gesture_hints", Context.MODE_PRIVATE)
    private var hasSeenHints = prefs.getBoolean("has_seen_hints", false)

    private val hints = listOf(
        GestureHint("2×", "Grid", 0.25f, 0.35f, Color.CYAN),
        GestureHint("3×", "Barcode", 0.5f, 0.35f, Color.YELLOW),
        GestureHint("4×", "Crop", 0.75f, 0.35f, Color.GREEN),
        GestureHint("🤏", "Zoom", 0.5f, 0.65f, Color.WHITE),
        GestureHint("👆", "Long: AI", 0.5f, 0.8f, Color.MAGENTA)
    )

    init {
        paint.style = Paint.Style.FILL

        textPaint.color = Color.WHITE
        textPaint.textSize = 36f
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textPaint.setShadowLayer(8f, 0f, 4f, Color.BLACK)

        iconPaint.textSize = 48f
        iconPaint.textAlign = Paint.Align.CENTER
        iconPaint.setShadowLayer(8f, 0f, 4f, Color.BLACK)

        // Auto-show on first run
        if (!hasSeenHints) {
            postDelayed({
                showHints()
            }, 2000)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (!showHints || alpha == 0) return

        val width = width.toFloat()
        val height = height.toFloat()

        // Draw semi-transparent background
        paint.color = Color.argb((alpha * 0.7f).toInt(), 0, 0, 0)
        canvas.drawRect(0f, 0f, width, height, paint)

        // Draw gesture hints
        hints.forEach { hint ->
            drawHint(canvas, hint, width, height)
        }

        // Draw dismiss hint at bottom
        textPaint.textSize = 28f
        textPaint.alpha = (alpha * 0.8f).toInt()
        canvas.drawText(
            "Tap anywhere to dismiss",
            width / 2f,
            height - 60f,
            textPaint
        )
        textPaint.alpha = 255
    }

    /**
     * Draw individual gesture hint
     */
    private fun drawHint(canvas: Canvas, hint: GestureHint, width: Float, height: Float) {
        val x = width * hint.x
        val y = height * hint.y

        // Pulsing circle background
        val circleAlpha = ((alpha * 0.6f) + (pulseAlpha * 0.4f)).toInt()
        paint.color = Color.argb(circleAlpha, 255, 255, 255)
        val baseRadius = 60f
        val pulseRadius = baseRadius + (pulseAlpha * 10f)
        canvas.drawCircle(x, y, pulseRadius, paint)

        // Gesture icon/text
        iconPaint.color = hint.color
        iconPaint.alpha = alpha
        canvas.drawText(hint.gesture, x, y + 15f, iconPaint)

        // Label
        textPaint.textSize = 32f
        textPaint.alpha = alpha
        canvas.drawText(hint.label, x, y + 90f, textPaint)
        textPaint.alpha = 255
    }

    /**
     * Show gesture hints
     */
    fun showHints() {
        if (showHints) return

        showHints = true
        visibility = VISIBLE

        // Fade in
        val fadeIn = ValueAnimator.ofInt(0, 255)
        fadeIn.duration = 500
        fadeIn.interpolator = AccelerateDecelerateInterpolator()
        fadeIn.addUpdateListener { animation ->
            alpha = animation.animatedValue as Int
            invalidate()
        }
        fadeIn.start()

        // Start pulse animation
        startPulseAnimation()

        // Mark as seen
        prefs.edit().putBoolean("has_seen_hints", true).apply()
        hasSeenHints = true
    }

    /**
     * Hide gesture hints
     */
    fun hideHints() {
        if (!showHints) return

        pulseAnimation?.cancel()
        pulseAnimation = null

        val fadeOut = ValueAnimator.ofInt(255, 0)
        fadeOut.duration = 300
        fadeOut.addUpdateListener { animation ->
            alpha = animation.animatedValue as Int
            invalidate()
        }
        fadeOut.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                showHints = false
                visibility = GONE
            }
        })
        fadeOut.start()
    }

    /**
     * Start pulse animation for hints
     */
    private fun startPulseAnimation() {
        pulseAnimation?.cancel()

        pulseAnimation = ValueAnimator.ofFloat(0f, 1f)
        pulseAnimation?.apply {
            duration = 1500
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animation ->
                val value = animation.animatedValue as Float
                pulseAlpha = sin(value * Math.PI).toFloat() * 255f
                invalidate()
            }
            start()
        }
    }

    /**
     * Toggle hints visibility
     */
    fun toggle() {
        if (showHints) {
            hideHints()
        } else {
            showHints()
        }
    }

    /**
     * Reset "has seen" state (for demo purposes)
     */
    fun resetSeenState() {
        prefs.edit().putBoolean("has_seen_hints", false).apply()
        hasSeenHints = false
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        pulseAnimation?.cancel()
    }
}

/**
 * Gesture hint data
 */
data class GestureHint(
    val gesture: String,
    val label: String,
    val x: Float,  // Position as percentage of width (0-1)
    val y: Float,  // Position as percentage of height (0-1)
    val color: Int
)
