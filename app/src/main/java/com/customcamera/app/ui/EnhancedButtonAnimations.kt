package com.customcamera.app.ui

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.BounceInterpolator
import android.view.animation.OvershootInterpolator
import androidx.core.content.ContextCompat
import com.customcamera.app.R
import kotlin.math.sin

/**
 * Enhanced Button Animation System
 *
 * Provides sophisticated button animations with:
 * - Ripple effects with customizable colors
 * - Elastic bounce animations
 * - Glow effects for special states
 * - Haptic feedback integration
 * - State-aware animations (enabled/disabled/active)
 * - Performance optimized animations
 */
class EnhancedButtonAnimations(private val context: Context) {

    companion object {
        const val ANIMATION_DURATION_SHORT = 150L
        const val ANIMATION_DURATION_MEDIUM = 300L
        const val ANIMATION_DURATION_LONG = 500L

        const val SCALE_PRESSED = 0.92f
        const val SCALE_BOUNCE = 1.08f
        const val SCALE_NORMAL = 1.0f

        const val ALPHA_DISABLED = 0.5f
        const val ALPHA_PRESSED = 0.8f
        const val ALPHA_NORMAL = 1.0f
    }

    /**
     * Standard button press animation with enhanced feedback
     */
    fun animateStandardPress(button: View, onComplete: (() -> Unit)? = null) {
        // Haptic feedback
        button.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)

        val scaleDown = createScaleAnimator(button, SCALE_NORMAL, SCALE_PRESSED, ANIMATION_DURATION_SHORT)
        val scaleUp = createScaleAnimator(button, SCALE_PRESSED, SCALE_BOUNCE, ANIMATION_DURATION_MEDIUM)
        val scaleNormal = createScaleAnimator(button, SCALE_BOUNCE, SCALE_NORMAL, ANIMATION_DURATION_SHORT)

        val alphaDown = createAlphaAnimator(button, ALPHA_NORMAL, ALPHA_PRESSED, ANIMATION_DURATION_SHORT)
        val alphaUp = createAlphaAnimator(button, ALPHA_PRESSED, ALPHA_NORMAL, ANIMATION_DURATION_MEDIUM)

        scaleUp.startDelay = ANIMATION_DURATION_SHORT
        scaleNormal.startDelay = ANIMATION_DURATION_SHORT + ANIMATION_DURATION_MEDIUM
        alphaUp.startDelay = ANIMATION_DURATION_SHORT

        val animatorSet = AnimatorSet()
        animatorSet.playTogether(scaleDown, scaleUp, scaleNormal, alphaDown, alphaUp)
        animatorSet.interpolator = AccelerateDecelerateInterpolator()

        if (onComplete != null) {
            animatorSet.addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    onComplete()
                }
            })
        }

        animatorSet.start()
    }

    /**
     * Capture button animation with special effect
     */
    fun animateCapturePress(button: View, onComplete: (() -> Unit)? = null) {
        // Enhanced haptic feedback for capture
        button.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)

        val scaleDown = createScaleAnimator(button, SCALE_NORMAL, 0.85f, 100L)
        val scaleUp = createScaleAnimator(button, 0.85f, 1.15f, 200L)
        val scaleNormal = createScaleAnimator(button, 1.15f, SCALE_NORMAL, 150L)

        val rotation = ObjectAnimator.ofFloat(button, "rotation", 0f, 8f, -8f, 0f)
        rotation.duration = 450L

        // Add glow effect
        addGlowEffect(button, Color.WHITE, 300L)

        scaleUp.startDelay = 100L
        scaleNormal.startDelay = 300L

        val animatorSet = AnimatorSet()
        animatorSet.playTogether(scaleDown, scaleUp, scaleNormal, rotation)
        animatorSet.interpolator = BounceInterpolator()

        if (onComplete != null) {
            animatorSet.addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    onComplete()
                }
            })
        }

        animatorSet.start()
    }

    /**
     * Long press animation with sustained feedback
     */
    fun animateLongPress(button: View, onComplete: (() -> Unit)? = null) {
        // Strong haptic feedback for long press
        button.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)

        val pulse = ObjectAnimator.ofFloat(button, "scaleX", SCALE_NORMAL, 1.12f, SCALE_NORMAL)
        pulse.duration = ANIMATION_DURATION_MEDIUM
        pulse.repeatCount = 1

        val pulseY = ObjectAnimator.ofFloat(button, "scaleY", SCALE_NORMAL, 1.12f, SCALE_NORMAL)
        pulseY.duration = ANIMATION_DURATION_MEDIUM
        pulseY.repeatCount = 1

        val rotate = ObjectAnimator.ofFloat(button, "rotation", 0f, 15f, 0f)
        rotate.duration = ANIMATION_DURATION_MEDIUM

        // Add sustained glow
        addGlowEffect(button, Color.YELLOW, ANIMATION_DURATION_LONG)

        val animatorSet = AnimatorSet()
        animatorSet.playTogether(pulse, pulseY, rotate)
        animatorSet.interpolator = OvershootInterpolator()

        if (onComplete != null) {
            animatorSet.addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    onComplete()
                }
            })
        }

        animatorSet.start()
    }

    /**
     * Toggle state animation (for switches, toggles)
     */
    fun animateToggle(button: View, isActive: Boolean, onComplete: (() -> Unit)? = null) {
        button.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)

        val targetAlpha = if (isActive) ALPHA_NORMAL else 0.6f
        val targetScale = if (isActive) 1.05f else 0.95f
        val glowColor = if (isActive) Color.GREEN else Color.GRAY

        val scaleX = ObjectAnimator.ofFloat(button, "scaleX", button.scaleX, targetScale)
        val scaleY = ObjectAnimator.ofFloat(button, "scaleY", button.scaleY, targetScale)
        val alpha = ObjectAnimator.ofFloat(button, "alpha", button.alpha, targetAlpha)

        scaleX.duration = ANIMATION_DURATION_MEDIUM
        scaleY.duration = ANIMATION_DURATION_MEDIUM
        alpha.duration = ANIMATION_DURATION_MEDIUM

        if (isActive) {
            addGlowEffect(button, glowColor, ANIMATION_DURATION_MEDIUM)
        }

        val animatorSet = AnimatorSet()
        animatorSet.playTogether(scaleX, scaleY, alpha)
        animatorSet.interpolator = AccelerateDecelerateInterpolator()

        if (onComplete != null) {
            animatorSet.addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    onComplete()
                }
            })
        }

        animatorSet.start()
    }

    /**
     * Error state animation
     */
    fun animateError(button: View, onComplete: (() -> Unit)? = null) {
        button.performHapticFeedback(HapticFeedbackConstants.REJECT)

        val shake = ObjectAnimator.ofFloat(button, "translationX", 0f, -25f, 25f, -15f, 15f, -5f, 5f, 0f)
        shake.duration = ANIMATION_DURATION_LONG

        addGlowEffect(button, Color.RED, ANIMATION_DURATION_MEDIUM)

        if (onComplete != null) {
            shake.addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    onComplete()
                }
            })
        }

        shake.start()
    }

    /**
     * Success state animation
     */
    fun animateSuccess(button: View, onComplete: (() -> Unit)? = null) {
        button.performHapticFeedback(HapticFeedbackConstants.CONFIRM)

        val bounce = ObjectAnimator.ofFloat(button, "scaleX", SCALE_NORMAL, 1.2f, 0.9f, 1.1f, SCALE_NORMAL)
        bounce.duration = ANIMATION_DURATION_LONG

        val bounceY = ObjectAnimator.ofFloat(button, "scaleY", SCALE_NORMAL, 1.2f, 0.9f, 1.1f, SCALE_NORMAL)
        bounceY.duration = ANIMATION_DURATION_LONG

        addGlowEffect(button, Color.GREEN, ANIMATION_DURATION_MEDIUM)

        val animatorSet = AnimatorSet()
        animatorSet.playTogether(bounce, bounceY)
        animatorSet.interpolator = BounceInterpolator()

        if (onComplete != null) {
            animatorSet.addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    onComplete()
                }
            })
        }

        animatorSet.start()
    }

    /**
     * Loading animation for buttons
     */
    fun animateLoading(button: View, isLoading: Boolean) {
        if (isLoading) {
            val rotation = ObjectAnimator.ofFloat(button, "rotation", 0f, 360f)
            rotation.duration = 1000L
            rotation.repeatCount = ValueAnimator.INFINITE
            rotation.interpolator = AccelerateDecelerateInterpolator()
            button.tag = rotation
            rotation.start()

            button.alpha = 0.7f
        } else {
            val rotation = button.tag as? ObjectAnimator
            rotation?.cancel()
            button.tag = null
            button.rotation = 0f
            button.alpha = ALPHA_NORMAL
        }
    }

    /**
     * Breathing animation for special states
     */
    fun animateBreathing(button: View, isBreathing: Boolean) {
        if (isBreathing) {
            val breathe = ObjectAnimator.ofFloat(button, "alpha", ALPHA_NORMAL, 0.6f, ALPHA_NORMAL)
            breathe.duration = 2000L
            breathe.repeatCount = ValueAnimator.INFINITE
            breathe.interpolator = AccelerateDecelerateInterpolator()
            button.tag = breathe
            breathe.start()
        } else {
            val breathe = button.tag as? ObjectAnimator
            breathe?.cancel()
            button.tag = null
            button.alpha = ALPHA_NORMAL
        }
    }

    // Helper methods

    private fun createScaleAnimator(view: View, from: Float, to: Float, duration: Long): AnimatorSet {
        val scaleX = ObjectAnimator.ofFloat(view, "scaleX", from, to)
        val scaleY = ObjectAnimator.ofFloat(view, "scaleY", from, to)
        scaleX.duration = duration
        scaleY.duration = duration

        val animatorSet = AnimatorSet()
        animatorSet.playTogether(scaleX, scaleY)
        return animatorSet
    }

    private fun createAlphaAnimator(view: View, from: Float, to: Float, duration: Long): ObjectAnimator {
        val alpha = ObjectAnimator.ofFloat(view, "alpha", from, to)
        alpha.duration = duration
        return alpha
    }

    private fun addGlowEffect(view: View, color: Int, duration: Long) {
        val originalBackground = view.background

        val glowDrawable = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.TRANSPARENT)
            setStroke(8, color)
        }

        view.background = glowDrawable

        val glowAlpha = ObjectAnimator.ofInt(glowDrawable, "alpha", 0, 255, 0)
        glowAlpha.duration = duration
        glowAlpha.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                view.background = originalBackground
            }
        })
        glowAlpha.start()
    }

    /**
     * Create a ripple effect on button press
     */
    fun createRippleEffect(view: View, x: Float, y: Float, color: Int = Color.WHITE) {
        val rippleView = RippleView(context, x, y, color)

        if (view.parent is android.view.ViewGroup) {
            val parent = view.parent as android.view.ViewGroup
            parent.addView(rippleView)

            val animator = ObjectAnimator.ofFloat(rippleView, "progress", 0f, 1f)
            animator.duration = ANIMATION_DURATION_MEDIUM
            animator.addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    parent.removeView(rippleView)
                }
            })
            animator.start()
        }
    }

    /**
     * Pulse animation for notifications or alerts
     */
    fun animatePulse(button: View, color: Int = Color.RED, count: Int = 3) {
        addGlowEffect(button, color, ANIMATION_DURATION_MEDIUM * count)

        val scale = ObjectAnimator.ofFloat(button, "scaleX", SCALE_NORMAL, 1.1f, SCALE_NORMAL)
        scale.duration = ANIMATION_DURATION_MEDIUM
        scale.repeatCount = count - 1

        val scaleY = ObjectAnimator.ofFloat(button, "scaleY", SCALE_NORMAL, 1.1f, SCALE_NORMAL)
        scaleY.duration = ANIMATION_DURATION_MEDIUM
        scaleY.repeatCount = count - 1

        val animatorSet = AnimatorSet()
        animatorSet.playTogether(scale, scaleY)
        animatorSet.start()
    }
}

/**
 * Custom view for ripple effects
 */
private class RippleView(
    context: Context,
    private val centerX: Float,
    private val centerY: Float,
    private val color: Int
) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = this@RippleView.color
        style = Paint.Style.FILL
    }

    var progress: Float = 0f
        set(value) {
            field = value
            invalidate()
        }

    private val maxRadius = 200f

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val radius = maxRadius * progress
        val alpha = ((1f - progress) * 255).toInt()

        paint.alpha = alpha
        canvas.drawCircle(centerX, centerY, radius, paint)
    }
}