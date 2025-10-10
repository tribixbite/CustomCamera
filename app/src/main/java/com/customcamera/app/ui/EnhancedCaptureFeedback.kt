package com.customcamera.app.ui

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.content.ContextCompat

/**
 * Enhanced capture feedback providing visual, haptic, and audio
 * feedback for photo/video capture actions.
 */
class EnhancedCaptureFeedback(
    private val context: Context,
    private val parentView: ViewGroup
) {

    private var flashOverlay: View? = null
    private var vibrator: Vibrator? = null

    /**
     * Initialize capture feedback system
     */
    fun initialize() {
        createFlashOverlay()
        initializeVibrator()
        Log.i(TAG, "Enhanced capture feedback initialized")
    }

    private fun createFlashOverlay() {
        flashOverlay = View(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.WHITE)
            visibility = View.GONE
            alpha = 0f
        }
        parentView.addView(flashOverlay)
    }

    private fun initializeVibrator() {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    /**
     * Trigger capture feedback for photo
     */
    fun onPhotoCapture(triggerView: View? = null) {
        // Haptic feedback
        vibrateCapture()

        // Visual flash
        flashScreen()

        // Button animation
        triggerView?.let { animateCaptureButton(it) }

        Log.i(TAG, "Photo capture feedback triggered")
    }

    /**
     * Trigger recording start feedback
     */
    fun onRecordingStart(triggerView: View? = null) {
        // Stronger haptic for recording
        vibrateRecordStart()

        // Pulse animation
        triggerView?.let { pulseButton(it) }

        Log.i(TAG, "Recording start feedback triggered")
    }

    /**
     * Trigger recording stop feedback
     */
    fun onRecordingStop(triggerView: View? = null) {
        // Two short vibrations
        vibrateRecordStop()

        // Stop pulse animation
        triggerView?.let {
            it.clearAnimation()
            it.animate()
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(150)
                .start()
        }

        Log.i(TAG, "Recording stop feedback triggered")
    }

    /**
     * Flash the screen white briefly
     */
    private fun flashScreen() {
        flashOverlay?.let { overlay ->
            overlay.alpha = 0.8f
            overlay.visibility = View.VISIBLE

            overlay.animate()
                .alpha(0f)
                .setDuration(150)
                .withEndAction {
                    overlay.visibility = View.GONE
                }
                .start()
        }
    }

    /**
     * Animate capture button press
     */
    private fun animateCaptureButton(button: View) {
        val scaleDown = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(button, "scaleX", 1f, 0.8f),
                ObjectAnimator.ofFloat(button, "scaleY", 1f, 0.8f)
            )
            duration = 100
        }

        val scaleUp = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(button, "scaleX", 0.8f, 1f),
                ObjectAnimator.ofFloat(button, "scaleY", 0.8f, 1f)
            )
            duration = 100
            startDelay = 100
        }

        AnimatorSet().apply {
            playSequentially(scaleDown, scaleUp)
            start()
        }
    }

    /**
     * Pulse button continuously (for recording)
     */
    private fun pulseButton(button: View) {
        val pulse = ObjectAnimator.ofFloat(button, "alpha", 1f, 0.5f, 1f).apply {
            duration = 1000
            repeatCount = ObjectAnimator.INFINITE
        }
        pulse.start()
    }

    /**
     * Vibrate for photo capture
     */
    private fun vibrateCapture() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(
                VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE)
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(50)
        }
    }

    /**
     * Vibrate for recording start
     */
    private fun vibrateRecordStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(
                VibrationEffect.createWaveform(
                    longArrayOf(0, 100, 50, 100),
                    intArrayOf(0, 255, 0, 200),
                    -1
                )
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(100)
        }
    }

    /**
     * Vibrate for recording stop
     */
    private fun vibrateRecordStop() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(
                VibrationEffect.createWaveform(
                    longArrayOf(0, 50, 30, 50),
                    intArrayOf(0, 150, 0, 150),
                    -1
                )
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(longArrayOf(0, 50, 30, 50), -1)
        }
    }

    /**
     * Cleanup resources
     */
    fun cleanup() {
        flashOverlay?.clearAnimation()
        vibrator?.cancel()
    }

    companion object {
        private const val TAG = "EnhancedCaptureFeedback"
    }
}
