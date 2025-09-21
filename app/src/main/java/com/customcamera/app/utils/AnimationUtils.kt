package com.customcamera.app.utils

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AnimationUtils
import android.view.animation.OvershootInterpolator
import androidx.interpolator.view.animation.FastOutSlowInInterpolator

/**
 * Utility class for creating smooth, modern animations
 * throughout the CustomCamera app.
 */
object AnimationUtils {

    private const val ANIMATION_DURATION_SHORT = 150L
    private const val ANIMATION_DURATION_MEDIUM = 300L
    private const val ANIMATION_DURATION_LONG = 500L

    /**
     * Animate button press with scale and alpha feedback
     */
    fun animateButtonPress(view: View, onComplete: (() -> Unit)? = null) {
        val scaleX = ObjectAnimator.ofFloat(view, "scaleX", 1.0f, 0.95f)
        val scaleY = ObjectAnimator.ofFloat(view, "scaleY", 1.0f, 0.95f)
        val alpha = ObjectAnimator.ofFloat(view, "alpha", 1.0f, 0.8f)

        val animatorSet = AnimatorSet().apply {
            playTogether(scaleX, scaleY, alpha)
            duration = ANIMATION_DURATION_SHORT
            interpolator = AccelerateDecelerateInterpolator()
        }

        animatorSet.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                animateButtonRelease(view, onComplete)
            }
        })

        animatorSet.start()
    }

    /**
     * Animate button release with slight overshoot
     */
    private fun animateButtonRelease(view: View, onComplete: (() -> Unit)? = null) {
        val scaleX = ObjectAnimator.ofFloat(view, "scaleX", 0.95f, 1.0f)
        val scaleY = ObjectAnimator.ofFloat(view, "scaleY", 0.95f, 1.0f)
        val alpha = ObjectAnimator.ofFloat(view, "alpha", 0.8f, 1.0f)

        val animatorSet = AnimatorSet().apply {
            playTogether(scaleX, scaleY, alpha)
            duration = 100L
            interpolator = OvershootInterpolator(1.2f)
        }

        animatorSet.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                onComplete?.invoke()
            }
        })

        animatorSet.start()
    }

    /**
     * Fade in animation for loading states
     */
    fun fadeIn(view: View, duration: Long = ANIMATION_DURATION_MEDIUM, onComplete: (() -> Unit)? = null) {
        view.alpha = 0f
        view.visibility = View.VISIBLE

        val fadeIn = ObjectAnimator.ofFloat(view, "alpha", 0f, 1f).apply {
            this.duration = duration
            interpolator = FastOutSlowInInterpolator()
        }

        fadeIn.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                onComplete?.invoke()
            }
        })

        fadeIn.start()
    }

    /**
     * Fade out animation for hiding views
     */
    fun fadeOut(view: View, duration: Long = ANIMATION_DURATION_MEDIUM, onComplete: (() -> Unit)? = null) {
        val fadeOut = ObjectAnimator.ofFloat(view, "alpha", 1f, 0f).apply {
            this.duration = duration
            interpolator = FastOutSlowInInterpolator()
        }

        fadeOut.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                view.visibility = View.GONE
                onComplete?.invoke()
            }
        })

        fadeOut.start()
    }

    /**
     * Slide in from right animation for new content
     */
    fun slideInFromRight(view: View, duration: Long = ANIMATION_DURATION_MEDIUM, onComplete: (() -> Unit)? = null) {
        view.translationX = view.width.toFloat()
        view.alpha = 0f
        view.visibility = View.VISIBLE

        val slideIn = ObjectAnimator.ofFloat(view, "translationX", view.width.toFloat(), 0f)
        val fadeIn = ObjectAnimator.ofFloat(view, "alpha", 0f, 1f)

        val animatorSet = AnimatorSet().apply {
            playTogether(slideIn, fadeIn)
            this.duration = duration
            interpolator = FastOutSlowInInterpolator()
        }

        animatorSet.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                onComplete?.invoke()
            }
        })

        animatorSet.start()
    }

    /**
     * Slide out to left animation for dismissing content
     */
    fun slideOutToLeft(view: View, duration: Long = ANIMATION_DURATION_MEDIUM, onComplete: (() -> Unit)? = null) {
        val slideOut = ObjectAnimator.ofFloat(view, "translationX", 0f, -view.width.toFloat())
        val fadeOut = ObjectAnimator.ofFloat(view, "alpha", 1f, 0f)

        val animatorSet = AnimatorSet().apply {
            playTogether(slideOut, fadeOut)
            this.duration = duration
            interpolator = AccelerateDecelerateInterpolator()
        }

        animatorSet.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                view.visibility = View.GONE
                view.translationX = 0f // Reset for reuse
                onComplete?.invoke()
            }
        })

        animatorSet.start()
    }

    /**
     * Pulse animation for notifications or important indicators
     */
    fun pulse(view: View, cycles: Int = 3, onComplete: (() -> Unit)? = null) {
        val scaleX = ObjectAnimator.ofFloat(view, "scaleX", 1.0f, 1.1f, 1.0f)
        val scaleY = ObjectAnimator.ofFloat(view, "scaleY", 1.0f, 1.1f, 1.0f)
        val alpha = ObjectAnimator.ofFloat(view, "alpha", 1.0f, 0.7f, 1.0f)

        val animatorSet = AnimatorSet().apply {
            playTogether(scaleX, scaleY, alpha)
            duration = ANIMATION_DURATION_LONG
            interpolator = AccelerateDecelerateInterpolator()
            setTarget(view)
        }

        var currentCycle = 0
        val listener = object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                currentCycle++
                if (currentCycle < cycles) {
                    animatorSet.start()
                } else {
                    onComplete?.invoke()
                }
            }
        }

        animatorSet.addListener(listener)
        animatorSet.start()
    }

    /**
     * Shake animation for error feedback
     */
    fun shake(view: View, intensity: Float = 10f, onComplete: (() -> Unit)? = null) {
        val shake = ObjectAnimator.ofFloat(
            view, "translationX",
            0f, intensity, -intensity, intensity, -intensity, intensity, 0f
        ).apply {
            duration = ANIMATION_DURATION_LONG
            interpolator = AccelerateDecelerateInterpolator()
        }

        shake.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                onComplete?.invoke()
            }
        })

        shake.start()
    }

    /**
     * Loading spinner rotation animation
     */
    fun startLoadingSpinner(view: View): ObjectAnimator {
        return ObjectAnimator.ofFloat(view, "rotation", 0f, 360f).apply {
            duration = 1000L
            interpolator = android.view.animation.LinearInterpolator()
            repeatCount = ObjectAnimator.INFINITE
            start()
        }
    }

    /**
     * Stop loading spinner with fade out
     */
    fun stopLoadingSpinner(view: View, spinner: ObjectAnimator?, onComplete: (() -> Unit)? = null) {
        spinner?.cancel()
        fadeOut(view, ANIMATION_DURATION_SHORT) {
            view.rotation = 0f
            onComplete?.invoke()
        }
    }

    /**
     * Cross-fade between two views
     */
    fun crossFade(viewOut: View, viewIn: View, duration: Long = ANIMATION_DURATION_MEDIUM, onComplete: (() -> Unit)? = null) {
        val fadeOut = ObjectAnimator.ofFloat(viewOut, "alpha", 1f, 0f)
        val fadeIn = ObjectAnimator.ofFloat(viewIn, "alpha", 0f, 1f)

        viewIn.alpha = 0f
        viewIn.visibility = View.VISIBLE

        val animatorSet = AnimatorSet().apply {
            playTogether(fadeOut, fadeIn)
            this.duration = duration
            interpolator = FastOutSlowInInterpolator()
        }

        animatorSet.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                viewOut.visibility = View.GONE
                onComplete?.invoke()
            }
        })

        animatorSet.start()
    }
}