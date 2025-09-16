package com.customcamera.app.focus

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.camera.core.Camera
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.MeteringPoint
import androidx.camera.view.PreviewView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * TapToFocusHandler provides sophisticated tap-to-focus functionality
 * with visual feedback and smooth animations.
 */
class TapToFocusHandler(
    private val context: Context,
    private val previewView: PreviewView
) {

    private var camera: Camera? = null
    private var focusIndicator: FocusIndicatorView? = null
    private var isEnabled: Boolean = true
    private var isFocusing: Boolean = false

    // Configuration
    private var focusTimeout: Long = 5000L // 5 seconds
    private var indicatorSize: Float = 120f // dp
    private var animationDuration: Long = 200L

    /**
     * Setup touch listener on the preview view
     */
    fun setupTouchListener(previewView: PreviewView, camera: Camera) {
        this.camera = camera

        previewView.setOnTouchListener { view, event ->
            if (event.action == MotionEvent.ACTION_DOWN && isEnabled && !isFocusing) {
                val x = event.x
                val y = event.y

                Log.d(TAG, "Touch detected at ($x, $y)")

                // Create focus point and trigger focus
                val meteringPoint = createFocusPoint(x, y)
                triggerAutoFocus(meteringPoint)
                showFocusIndicator(x, y)

                true
            } else {
                false
            }
        }

        Log.i(TAG, "Touch listener setup complete")
    }

    /**
     * Create metering point from touch coordinates
     */
    fun createFocusPoint(x: Float, y: Float): MeteringPoint {
        val factory = previewView.meteringPointFactory
        val meteringPoint = factory.createPoint(x, y)

        Log.d(TAG, "Created metering point at display coordinates ($x, $y)")
        return meteringPoint
    }

    /**
     * Trigger autofocus at the specified metering point
     */
    fun triggerAutoFocus(meteringPoint: MeteringPoint) {
        val currentCamera = camera ?: return

        if (isFocusing) {
            Log.w(TAG, "Focus already in progress, ignoring request")
            return
        }

        isFocusing = true

        try {
            // Create focus and metering action
            val action = FocusMeteringAction.Builder(meteringPoint)
                .addPoint(meteringPoint, FocusMeteringAction.FLAG_AF)
                .addPoint(meteringPoint, FocusMeteringAction.FLAG_AE)
                .setAutoCancelDuration(focusTimeout, TimeUnit.MILLISECONDS)
                .build()

            // Start focus and metering
            val listenableFuture = currentCamera.cameraControl.startFocusAndMetering(action)

            // Handle focus result
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    listenableFuture.get()
                    Log.i(TAG, "Focus completed successfully")
                    onFocusCompleted(true)
                } catch (e: Exception) {
                    Log.w(TAG, "Focus failed", e)
                    onFocusCompleted(false)
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to trigger autofocus", e)
            onFocusCompleted(false)
        }
    }

    /**
     * Show focus indicator at the specified coordinates
     */
    fun showFocusIndicator(x: Float, y: Float) {
        Log.d(TAG, "Showing focus indicator at ($x, $y)")

        // Remove existing indicator if present
        focusIndicator?.let { indicator ->
            (previewView.parent as? FrameLayout)?.removeView(indicator)
        }

        // Create new focus indicator
        focusIndicator = FocusIndicatorView(context).apply {
            setFocusPosition(x, y)
            setIndicatorSize(indicatorSize)
        }

        // Add to preview view parent
        (previewView.parent as? FrameLayout)?.let { parent ->
            val layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
            parent.addView(focusIndicator, layoutParams)

            // Animate indicator
            animateFocusIndicator()
        }
    }

    /**
     * Enable or disable tap-to-focus
     */
    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
        Log.i(TAG, "Tap-to-focus ${if (enabled) "enabled" else "disabled"}")

        if (!enabled) {
            hideFocusIndicator()
        }
    }

    /**
     * Configure focus timeout
     */
    fun setFocusTimeout(timeoutMs: Long) {
        focusTimeout = timeoutMs
        Log.d(TAG, "Focus timeout set to ${timeoutMs}ms")
    }

    /**
     * Configure indicator appearance
     */
    fun setIndicatorSize(sizeDp: Float) {
        indicatorSize = sizeDp
        Log.d(TAG, "Focus indicator size set to ${sizeDp}dp")
    }

    /**
     * Clean up resources
     */
    fun cleanup() {
        Log.i(TAG, "Cleaning up TapToFocusHandler")

        camera = null
        hideFocusIndicator()
        previewView.setOnTouchListener(null)
    }

    private fun onFocusCompleted(success: Boolean) {
        isFocusing = false

        Log.d(TAG, "Focus ${if (success) "completed successfully" else "failed"}")

        // Update indicator appearance based on result
        focusIndicator?.setFocusResult(success)

        // Hide indicator after delay
        CoroutineScope(Dispatchers.Main).launch {
            delay(1000) // Show result for 1 second
            hideFocusIndicator()
        }
    }

    private fun animateFocusIndicator() {
        val indicator = focusIndicator ?: return

        // Scale and fade animation
        val scaleX = ObjectAnimator.ofFloat(indicator, "scaleX", 1.5f, 1.0f)
        val scaleY = ObjectAnimator.ofFloat(indicator, "scaleY", 1.5f, 1.0f)
        val alpha = ObjectAnimator.ofFloat(indicator, "alpha", 0f, 1.0f)

        AnimatorSet().apply {
            playTogether(scaleX, scaleY, alpha)
            duration = animationDuration
            start()
        }

        Log.d(TAG, "Focus indicator animation started")
    }

    private fun hideFocusIndicator() {
        focusIndicator?.let { indicator ->
            (previewView.parent as? FrameLayout)?.removeView(indicator)
            focusIndicator = null
            Log.d(TAG, "Focus indicator hidden")
        }
    }

    companion object {
        private const val TAG = "TapToFocusHandler"
    }
}

/**
 * Custom view for displaying focus indicator with visual feedback
 */
class FocusIndicatorView(context: Context) : View(context) {

    private val paint = Paint().apply {
        color = Color.WHITE
        strokeWidth = 4f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private var focusX: Float = 0f
    private var focusY: Float = 0f
    private var indicatorSize: Float = 120f
    private var focusSuccess: Boolean? = null

    fun setFocusPosition(x: Float, y: Float) {
        focusX = x
        focusY = y

        // Position the view
        val size = (indicatorSize * resources.displayMetrics.density).toInt()
        layoutParams = FrameLayout.LayoutParams(size, size).apply {
            leftMargin = (x - size / 2).toInt()
            topMargin = (y - size / 2).toInt()
        }

        invalidate()
    }

    fun setIndicatorSize(sizeDp: Float) {
        indicatorSize = sizeDp
        invalidate()
    }

    fun setFocusResult(success: Boolean) {
        focusSuccess = success
        paint.color = if (success) Color.GREEN else Color.RED
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val centerX = width / 2f
        val centerY = height / 2f
        val radius = (indicatorSize * 0.4f * resources.displayMetrics.density)

        // Draw focus ring
        canvas.drawCircle(centerX, centerY, radius, paint)

        // Draw crosshairs
        val crossSize = radius * 0.6f
        canvas.drawLine(centerX - crossSize, centerY, centerX + crossSize, centerY, paint)
        canvas.drawLine(centerX, centerY - crossSize, centerX, centerY + crossSize, paint)

        // Draw corners if focus completed
        focusSuccess?.let { success ->
            val cornerSize = radius * 0.3f
            val cornerOffset = radius * 0.7f

            // Top-left corner
            canvas.drawLine(centerX - cornerOffset, centerY - cornerOffset - cornerSize,
                           centerX - cornerOffset, centerY - cornerOffset, paint)
            canvas.drawLine(centerX - cornerOffset, centerY - cornerOffset,
                           centerX - cornerOffset + cornerSize, centerY - cornerOffset, paint)

            // Top-right corner
            canvas.drawLine(centerX + cornerOffset, centerY - cornerOffset - cornerSize,
                           centerX + cornerOffset, centerY - cornerOffset, paint)
            canvas.drawLine(centerX + cornerOffset, centerY - cornerOffset,
                           centerX + cornerOffset - cornerSize, centerY - cornerOffset, paint)

            // Bottom-left corner
            canvas.drawLine(centerX - cornerOffset, centerY + cornerOffset + cornerSize,
                           centerX - cornerOffset, centerY + cornerOffset, paint)
            canvas.drawLine(centerX - cornerOffset, centerY + cornerOffset,
                           centerX - cornerOffset + cornerSize, centerY + cornerOffset, paint)

            // Bottom-right corner
            canvas.drawLine(centerX + cornerOffset, centerY + cornerOffset + cornerSize,
                           centerX + cornerOffset, centerY + cornerOffset, paint)
            canvas.drawLine(centerX + cornerOffset, centerY + cornerOffset,
                           centerX + cornerOffset - cornerSize, centerY + cornerOffset, paint)
        }
    }
}