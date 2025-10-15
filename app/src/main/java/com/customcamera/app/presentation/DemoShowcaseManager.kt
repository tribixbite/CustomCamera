package com.customcamera.app.presentation

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.customcamera.app.R

/**
 * Demo Showcase Manager - Conference presentation helper
 *
 * Provides:
 * - Feature highlights with spotlights
 * - Gesture tutorials
 * - Feature annotations
 * - Demo flow automation
 * - Performance metrics overlay
 */
class DemoShowcaseManager(private val context: Context) {

    private var showcaseOverlay: ShowcaseOverlayView? = null
    private var isShowcaseMode = false
    private var currentStep = 0

    private val showcaseSteps = listOf(
        ShowcaseStep(
            title = "Dual Camera PiP",
            description = "Picture-in-Picture with concurrent cameras",
            targetView = "pipButton",
            gesture = "Tap to enable dual camera mode"
        ),
        ShowcaseStep(
            title = "Gesture Controls",
            description = "Multi-tap gestures for quick access",
            targetView = "previewView",
            gesture = "2x tap: Grid | 3x: Barcode | 4x: Crop"
        ),
        ShowcaseStep(
            title = "AI Features",
            description = "Smart scene detection & auto-adjustments",
            targetView = "previewView",
            gesture = "Long-press preview to see AI status"
        ),
        ShowcaseStep(
            title = "Professional Controls",
            description = "Manual ISO, shutter, focus, exposure",
            targetView = "manualControlsToggleButton",
            gesture = "Tap for pro mode controls"
        ),
        ShowcaseStep(
            title = "Night Mode",
            description = "Long exposure with multi-frame stacking",
            targetView = "nightModeButton",
            gesture = "Auto-activates in low light"
        )
    )

    /**
     * Start conference demo showcase
     */
    fun startDemoShowcase(rootView: ViewGroup) {
        if (isShowcaseMode) return

        isShowcaseMode = true
        currentStep = 0

        showcaseOverlay = ShowcaseOverlayView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        rootView.addView(showcaseOverlay)
        showNextStep(rootView)

        Log.i(TAG, "Demo showcase started")
    }

    /**
     * Show next showcase step
     */
    fun showNextStep(rootView: ViewGroup) {
        if (currentStep >= showcaseSteps.size) {
            endShowcase(rootView)
            return
        }

        val step = showcaseSteps[currentStep]

        // Find target view by ID
        val targetView = findViewByName(rootView, step.targetView)

        showcaseOverlay?.showStep(step, targetView)
        currentStep++
    }

    /**
     * End showcase mode
     */
    fun endShowcase(rootView: ViewGroup) {
        showcaseOverlay?.let { overlay ->
            overlay.fadeOut {
                rootView.removeView(overlay)
                showcaseOverlay = null
            }
        }

        isShowcaseMode = false
        currentStep = 0

        Log.i(TAG, "Demo showcase ended")
    }

    /**
     * Find view by resource name
     */
    private fun findViewByName(rootView: ViewGroup, viewName: String): View? {
        val id = context.resources.getIdentifier(viewName, "id", context.packageName)
        return if (id != 0) rootView.findViewById(id) else null
    }

    /**
     * Check if in showcase mode
     */
    fun isInShowcaseMode() = isShowcaseMode

    companion object {
        private const val TAG = "DemoShowcaseManager"
    }
}

/**
 * Showcase step data
 */
data class ShowcaseStep(
    val title: String,
    val description: String,
    val targetView: String,
    val gesture: String
)

/**
 * Showcase overlay view with spotlight and annotations
 */
class ShowcaseOverlayView(context: Context) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val spotlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var currentStep: ShowcaseStep? = null
    private var targetRect: RectF? = null
    private var spotlightRadius = 0f
    private var alpha = 0

    init {
        // Dark overlay
        paint.color = Color.BLACK
        paint.alpha = 200

        // White text with shadow
        textPaint.color = Color.WHITE
        textPaint.textSize = 48f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textPaint.setShadowLayer(8f, 0f, 4f, Color.BLACK)

        // Spotlight (clear circle)
        spotlightPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (currentStep == null) return

        val layerId = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)

        // Draw dark overlay
        canvas.drawColor(Color.argb(200, 0, 0, 0))

        // Draw spotlight around target
        targetRect?.let { rect ->
            val centerX = rect.centerX()
            val centerY = rect.centerY()
            canvas.drawCircle(centerX, centerY, spotlightRadius, spotlightPaint)
        }

        canvas.restoreToCount(layerId)

        // Draw text annotations
        drawAnnotations(canvas)
    }

    /**
     * Draw showcase annotations
     */
    private fun drawAnnotations(canvas: Canvas) {
        val step = currentStep ?: return

        val textY = height * 0.75f
        val textX = width / 2f

        // Title
        textPaint.textSize = 64f
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText(step.title, textX, textY, textPaint)

        // Description
        textPaint.textSize = 40f
        canvas.drawText(step.description, textX, textY + 70f, textPaint)

        // Gesture hint
        textPaint.textSize = 32f
        textPaint.alpha = 180
        canvas.drawText(step.gesture, textX, textY + 130f, textPaint)
        textPaint.alpha = 255

        // "Tap anywhere to continue" hint
        textPaint.textSize = 28f
        textPaint.alpha = 150
        canvas.drawText("Tap anywhere to continue", textX, height - 100f, textPaint)
        textPaint.alpha = 255
    }

    /**
     * Show showcase step with animation
     */
    fun showStep(step: ShowcaseStep, targetView: View?) {
        currentStep = step

        // Calculate target rectangle
        targetView?.let { view ->
            val location = IntArray(2)
            view.getLocationOnScreen(location)

            targetRect = RectF(
                location[0].toFloat(),
                location[1].toFloat(),
                location[0] + view.width.toFloat(),
                location[1] + view.height.toFloat()
            )

            spotlightRadius = maxOf(view.width, view.height) * 0.8f
        }

        // Fade in animation
        val animator = ValueAnimator.ofInt(0, 255)
        animator.duration = 500
        animator.interpolator = AccelerateDecelerateInterpolator()
        animator.addUpdateListener { animation ->
            alpha = animation.animatedValue as Int
            invalidate()
        }
        animator.start()
    }

    /**
     * Fade out animation
     */
    fun fadeOut(onComplete: () -> Unit) {
        val animator = ValueAnimator.ofInt(255, 0)
        animator.duration = 300
        animator.addUpdateListener { animation ->
            alpha = animation.animatedValue as Int
            invalidate()
        }
        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                onComplete()
            }
        })
        animator.start()
    }
}
