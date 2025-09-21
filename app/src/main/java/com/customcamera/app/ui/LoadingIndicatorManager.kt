package com.customcamera.app.ui

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.customcamera.app.R
import kotlinx.coroutines.*

/**
 * Enhanced loading indicator manager with smooth animations
 * and contextual messages for different camera operations.
 */
class LoadingIndicatorManager(private val context: Context) {

    private var loadingOverlay: View? = null
    private var isShowing = false
    private var animationJob: Job? = null

    enum class LoadingType {
        CAMERA_INIT,
        PHOTO_CAPTURE,
        VIDEO_START,
        VIDEO_STOP,
        PLUGIN_LOADING,
        SETTINGS_SAVE,
        FOCUS_PROCESSING,
        HDR_PROCESSING,
        NIGHT_MODE,
        BARCODE_SCAN
    }

    /**
     * Show loading indicator with enhanced animations
     */
    fun showLoading(parent: ViewGroup, type: LoadingType, autoDismiss: Long? = null) {
        if (isShowing) return

        hideLoading() // Ensure clean state

        val inflater = LayoutInflater.from(context)
        loadingOverlay = inflater.inflate(R.layout.loading_indicator, parent, false)

        loadingOverlay?.let { overlay ->
            val loadingText = overlay.findViewById<TextView>(R.id.loadingText)
            val loadingSubtext = overlay.findViewById<TextView>(R.id.loadingSubtext)
            val progressSpinner = overlay.findViewById<ProgressBar>(R.id.progressSpinner)

            loadingText.text = type.title
            loadingSubtext.text = type.subtitle

            // Initial state for animation
            overlay.alpha = 0f
            overlay.scaleX = 0.8f
            overlay.scaleY = 0.8f

            parent.addView(overlay, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = android.view.Gravity.CENTER
            })

            // Animate in
            animateIn(overlay)

            // Start progress spinner animation
            startSpinnerAnimation(progressSpinner)

            isShowing = true

            // Auto-dismiss if specified
            autoDismiss?.let { delay ->
                animationJob = CoroutineScope(Dispatchers.Main).launch {
                    delay(delay)
                    hideLoading()
                }
            }
        }
    }

    /**
     * Hide loading indicator with smooth animation
     */
    fun hideLoading() {
        animationJob?.cancel()

        loadingOverlay?.let { overlay ->
            if (isShowing) {
                animateOut(overlay) {
                    (overlay.parent as? ViewGroup)?.removeView(overlay)
                    loadingOverlay = null
                    isShowing = false
                }
            }
        }
    }

    /**
     * Update loading text while showing
     */
    fun updateLoading(type: LoadingType) {
        loadingOverlay?.let { overlay ->
            val loadingText = overlay.findViewById<TextView>(R.id.loadingText)
            val loadingSubtext = overlay.findViewById<TextView>(R.id.loadingSubtext)

            // Animate text change
            ObjectAnimator.ofFloat(loadingText, "alpha", 1f, 0f).apply {
                duration = 150
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        loadingText.text = type.title
                        loadingSubtext.text = type.subtitle
                        ObjectAnimator.ofFloat(loadingText, "alpha", 0f, 1f).apply {
                            duration = 150
                            start()
                        }
                    }
                })
                start()
            }
        }
    }

    private fun animateIn(view: View) {
        val fadeIn = ObjectAnimator.ofFloat(view, "alpha", 0f, 1f).apply {
            duration = 300
            interpolator = FastOutSlowInInterpolator()
        }

        val scaleInX = ObjectAnimator.ofFloat(view, "scaleX", 0.8f, 1f).apply {
            duration = 300
            interpolator = FastOutSlowInInterpolator()
        }

        val scaleInY = ObjectAnimator.ofFloat(view, "scaleY", 0.8f, 1f).apply {
            duration = 300
            interpolator = FastOutSlowInInterpolator()
        }

        fadeIn.start()
        scaleInX.start()
        scaleInY.start()
    }

    private fun animateOut(view: View, onComplete: () -> Unit) {
        val fadeOut = ObjectAnimator.ofFloat(view, "alpha", 1f, 0f).apply {
            duration = 200
            interpolator = FastOutSlowInInterpolator()
        }

        val scaleOutX = ObjectAnimator.ofFloat(view, "scaleX", 1f, 0.8f).apply {
            duration = 200
            interpolator = FastOutSlowInInterpolator()
        }

        val scaleOutY = ObjectAnimator.ofFloat(view, "scaleY", 1f, 0.8f).apply {
            duration = 200
            interpolator = FastOutSlowInInterpolator()
        }

        fadeOut.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                onComplete()
            }
        })

        fadeOut.start()
        scaleOutX.start()
        scaleOutY.start()
    }

    private fun startSpinnerAnimation(progressBar: ProgressBar) {
        val rotationAnimator = ObjectAnimator.ofFloat(progressBar, "rotation", 0f, 360f).apply {
            duration = 1000
            repeatCount = ValueAnimator.INFINITE
            interpolator = android.view.animation.LinearInterpolator()
        }
        rotationAnimator.start()
    }

    /**
     * Check if loading indicator is currently showing
     */
    fun isLoading(): Boolean = isShowing

    /**
     * Loading type definitions with contextual messages
     */
    private val LoadingType.title: String
        get() = when (this) {
            LoadingType.CAMERA_INIT -> "Initializing Camera"
            LoadingType.PHOTO_CAPTURE -> "Capturing Photo"
            LoadingType.VIDEO_START -> "Starting Recording"
            LoadingType.VIDEO_STOP -> "Stopping Recording"
            LoadingType.PLUGIN_LOADING -> "Loading Plugin"
            LoadingType.SETTINGS_SAVE -> "Saving Settings"
            LoadingType.FOCUS_PROCESSING -> "Auto Focus"
            LoadingType.HDR_PROCESSING -> "HDR Processing"
            LoadingType.NIGHT_MODE -> "Night Mode"
            LoadingType.BARCODE_SCAN -> "Scanning"
        }

    private val LoadingType.subtitle: String
        get() = when (this) {
            LoadingType.CAMERA_INIT -> "Setting up camera hardware"
            LoadingType.PHOTO_CAPTURE -> "Processing image"
            LoadingType.VIDEO_START -> "Preparing video capture"
            LoadingType.VIDEO_STOP -> "Saving video"
            LoadingType.PLUGIN_LOADING -> "Initializing features"
            LoadingType.SETTINGS_SAVE -> "Updating preferences"
            LoadingType.FOCUS_PROCESSING -> "Adjusting focus"
            LoadingType.HDR_PROCESSING -> "Combining exposures"
            LoadingType.NIGHT_MODE -> "Enhancing low-light image"
            LoadingType.BARCODE_SCAN -> "Detecting barcodes"
        }
}