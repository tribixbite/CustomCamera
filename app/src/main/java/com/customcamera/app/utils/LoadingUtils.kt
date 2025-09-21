package com.customcamera.app.utils

import android.animation.ObjectAnimator
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.customcamera.app.R

/**
 * Utility class for creating and managing loading states
 * with modern progress indicators and smooth transitions.
 */
object LoadingUtils {

    /**
     * Data class for loading state configuration
     */
    data class LoadingConfig(
        val message: String = "Loading...",
        val showProgress: Boolean = true,
        val backgroundColor: Int = R.color.loading_overlay,
        val textColor: Int = android.R.color.white,
        val cancelable: Boolean = false
    )

    /**
     * Create a loading overlay for a given view
     */
    fun createLoadingOverlay(
        context: Context,
        parent: ViewGroup,
        config: LoadingConfig = LoadingConfig()
    ): View {
        val overlay = FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(ContextCompat.getColor(context, config.backgroundColor))
            alpha = 0f
            visibility = View.GONE
        }

        // Loading content container
        val contentContainer = FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.CENTER
            )
        }

        // Progress indicator
        if (config.showProgress) {
            val progressIndicator = ImageView(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    48.dpToPx(context),
                    48.dpToPx(context),
                    android.view.Gravity.CENTER_HORIZONTAL
                ).apply {
                    bottomMargin = 16.dpToPx(context)
                }
                setImageResource(R.drawable.ic_loading_spinner)
                setColorFilter(ContextCompat.getColor(context, config.textColor))
            }
            contentContainer.addView(progressIndicator)
        }

        // Loading message
        val messageText = TextView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.CENTER_HORIZONTAL or android.view.Gravity.BOTTOM
            )
            text = config.message
            setTextColor(ContextCompat.getColor(context, config.textColor))
            textSize = 16f
            textAlignment = View.TEXT_ALIGNMENT_CENTER
        }
        contentContainer.addView(messageText)

        overlay.addView(contentContainer)
        parent.addView(overlay)

        return overlay
    }

    /**
     * Show loading overlay with animation
     */
    fun showLoading(
        overlay: View,
        config: LoadingConfig = LoadingConfig()
    ): ObjectAnimator? {
        overlay.visibility = View.VISIBLE

        // Start spinner animation if present
        val spinner = overlay.findViewByType(ImageView::class.java)
        val spinnerAnimation = spinner?.let { AnimationUtils.startLoadingSpinner(it) }

        // Fade in overlay
        AnimationUtils.fadeIn(overlay, 200L)

        return spinnerAnimation
    }

    /**
     * Hide loading overlay with animation
     */
    fun hideLoading(
        overlay: View,
        spinnerAnimation: ObjectAnimator?,
        onComplete: (() -> Unit)? = null
    ) {
        AnimationUtils.fadeOut(overlay, 200L) {
            spinnerAnimation?.cancel()
            overlay.findViewByType(ImageView::class.java)?.rotation = 0f
            onComplete?.invoke()
        }
    }

    /**
     * Update loading message
     */
    fun updateLoadingMessage(overlay: View, message: String) {
        overlay.findViewByType(TextView::class.java)?.text = message
    }

    /**
     * Show loading with automatic timeout
     */
    fun showLoadingWithTimeout(
        overlay: View,
        timeoutMs: Long = 10000L,
        onTimeout: (() -> Unit)? = null
    ): LoadingState {
        val spinnerAnimation = showLoading(overlay)

        val timeoutRunnable = Runnable {
            hideLoading(overlay, spinnerAnimation) {
                onTimeout?.invoke()
            }
        }

        overlay.postDelayed(timeoutRunnable, timeoutMs)

        return LoadingState(overlay, spinnerAnimation, timeoutRunnable)
    }

    /**
     * Progress states for step-by-step operations
     */
    data class ProgressState(
        val currentStep: Int,
        val totalSteps: Int,
        val stepMessage: String
    )

    /**
     * Show step-by-step progress
     */
    fun showStepProgress(
        overlay: View,
        progress: ProgressState
    ) {
        overlay.findViewByType(TextView::class.java)?.text =
            "${progress.stepMessage}\n(${progress.currentStep}/${progress.totalSteps})"
    }

    /**
     * Loading state holder for managing active loading operations
     */
    data class LoadingState(
        val overlay: View,
        val spinnerAnimation: ObjectAnimator?,
        val timeoutRunnable: Runnable?
    ) {
        fun cancel() {
            overlay.removeCallbacks(timeoutRunnable)
            hideLoading(overlay, spinnerAnimation)
        }

        fun updateMessage(message: String) {
            updateLoadingMessage(overlay, message)
        }

        fun updateProgress(progress: ProgressState) {
            showStepProgress(overlay, progress)
        }
    }

    /**
     * Extension functions for convenience
     */
    private fun Int.dpToPx(context: Context): Int {
        return (this * context.resources.displayMetrics.density).toInt()
    }

    private fun <T : View> View.findViewByType(clazz: Class<T>): T? {
        if (clazz.isInstance(this)) return this as T
        if (this is ViewGroup) {
            for (i in 0 until childCount) {
                val found = getChildAt(i).findViewByType(clazz)
                if (found != null) return found
            }
        }
        return null
    }
}