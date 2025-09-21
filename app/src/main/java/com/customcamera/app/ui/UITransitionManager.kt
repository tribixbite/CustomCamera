package com.customcamera.app.ui

import android.animation.*
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.view.animation.*
import androidx.core.animation.doOnEnd
import androidx.core.animation.doOnStart
import androidx.core.view.isVisible
import kotlinx.coroutines.*

/**
 * Advanced UI Transition Manager
 *
 * Provides sophisticated UI transitions for:
 * - Activity transitions
 * - View animations
 * - Camera mode switches
 * - Settings panel animations
 * - Plugin UI transitions
 * - Smooth state changes
 */
class UITransitionManager(private val context: Context) {

    companion object {
        private const val DEFAULT_DURATION = 300L
        private const val FAST_DURATION = 150L
        private const val SLOW_DURATION = 500L
        private const val ELASTIC_DURATION = 600L
    }

    /**
     * Transition types for different UI scenarios
     */
    enum class TransitionType {
        FADE,
        SLIDE_LEFT,
        SLIDE_RIGHT,
        SLIDE_UP,
        SLIDE_DOWN,
        SCALE_UP,
        SCALE_DOWN,
        ELASTIC_BOUNCE,
        ROTATION_3D,
        MORPHING,
        RIPPLE_REVEAL
    }

    /**
     * Easing curves for different animation feels
     */
    enum class EasingCurve {
        LINEAR,
        EASE_IN,
        EASE_OUT,
        EASE_IN_OUT,
        BOUNCE,
        ELASTIC,
        OVERSHOOT,
        ANTICIPATE
    }

    /**
     * Animate view entrance with specified transition
     */
    fun animateIn(
        view: View,
        transitionType: TransitionType = TransitionType.FADE,
        duration: Long = DEFAULT_DURATION,
        easing: EasingCurve = EasingCurve.EASE_OUT,
        delay: Long = 0L,
        onComplete: (() -> Unit)? = null
    ) {
        view.isVisible = true

        val animator = when (transitionType) {
            TransitionType.FADE -> createFadeInAnimator(view)
            TransitionType.SLIDE_LEFT -> createSlideInAnimator(view, -view.width.toFloat(), 0f)
            TransitionType.SLIDE_RIGHT -> createSlideInAnimator(view, view.width.toFloat(), 0f)
            TransitionType.SLIDE_UP -> createSlideInAnimator(view, 0f, -view.height.toFloat(), isVertical = true)
            TransitionType.SLIDE_DOWN -> createSlideInAnimator(view, 0f, view.height.toFloat(), isVertical = true)
            TransitionType.SCALE_UP -> createScaleInAnimator(view)
            TransitionType.SCALE_DOWN -> createScaleDownInAnimator(view)
            TransitionType.ELASTIC_BOUNCE -> createElasticBounceInAnimator(view)
            TransitionType.ROTATION_3D -> createRotation3DInAnimator(view)
            TransitionType.MORPHING -> createMorphingInAnimator(view)
            TransitionType.RIPPLE_REVEAL -> createRippleRevealAnimator(view)
        }

        configureAnimator(animator, duration, easing, delay, onComplete)
        animator.start()
    }

    /**
     * Animate view exit with specified transition
     */
    fun animateOut(
        view: View,
        transitionType: TransitionType = TransitionType.FADE,
        duration: Long = DEFAULT_DURATION,
        easing: EasingCurve = EasingCurve.EASE_IN,
        delay: Long = 0L,
        onComplete: (() -> Unit)? = null
    ) {
        val animator = when (transitionType) {
            TransitionType.FADE -> createFadeOutAnimator(view)
            TransitionType.SLIDE_LEFT -> createSlideOutAnimator(view, -view.width.toFloat(), 0f)
            TransitionType.SLIDE_RIGHT -> createSlideOutAnimator(view, view.width.toFloat(), 0f)
            TransitionType.SLIDE_UP -> createSlideOutAnimator(view, 0f, -view.height.toFloat(), isVertical = true)
            TransitionType.SLIDE_DOWN -> createSlideOutAnimator(view, 0f, view.height.toFloat(), isVertical = true)
            TransitionType.SCALE_UP -> createScaleOutAnimator(view)
            TransitionType.SCALE_DOWN -> createScaleDownOutAnimator(view)
            TransitionType.ELASTIC_BOUNCE -> createElasticBounceOutAnimator(view)
            TransitionType.ROTATION_3D -> createRotation3DOutAnimator(view)
            TransitionType.MORPHING -> createMorphingOutAnimator(view)
            TransitionType.RIPPLE_REVEAL -> createRippleHideAnimator(view)
        }

        configureAnimator(animator, duration, easing, delay) {
            view.isVisible = false
            onComplete?.invoke()
        }
        animator.start()
    }

    /**
     * Transition between two views with cross-fade
     */
    fun crossFadeViews(
        viewOut: View,
        viewIn: View,
        duration: Long = DEFAULT_DURATION,
        onComplete: (() -> Unit)? = null
    ) {
        val fadeOut = createFadeOutAnimator(viewOut)
        val fadeIn = createFadeInAnimator(viewIn)

        viewIn.isVisible = true

        val animatorSet = AnimatorSet()
        animatorSet.playTogether(fadeOut, fadeIn)
        animatorSet.duration = duration
        animatorSet.doOnEnd {
            viewOut.isVisible = false
            onComplete?.invoke()
        }
        animatorSet.start()
    }

    /**
     * Create smooth camera mode transition
     */
    fun animateCameraModeSwitch(
        container: ViewGroup,
        fromMode: View,
        toMode: View,
        direction: TransitionType = TransitionType.SLIDE_LEFT,
        onComplete: (() -> Unit)? = null
    ) {
        val slideOutDirection = when (direction) {
            TransitionType.SLIDE_LEFT -> TransitionType.SLIDE_LEFT
            TransitionType.SLIDE_RIGHT -> TransitionType.SLIDE_RIGHT
            else -> TransitionType.SLIDE_LEFT
        }

        val slideInDirection = when (direction) {
            TransitionType.SLIDE_LEFT -> TransitionType.SLIDE_RIGHT
            TransitionType.SLIDE_RIGHT -> TransitionType.SLIDE_LEFT
            else -> TransitionType.SLIDE_RIGHT
        }

        // Slide out current mode
        animateOut(fromMode, slideOutDirection, FAST_DURATION, EasingCurve.EASE_IN)

        // Slide in new mode with slight delay
        CoroutineScope(Dispatchers.Main).launch {
            delay(FAST_DURATION / 2)
            animateIn(toMode, slideInDirection, FAST_DURATION, EasingCurve.EASE_OUT, onComplete = onComplete)
        }
    }

    /**
     * Create floating action button reveal animation
     */
    fun animateFABReveal(
        fab: View,
        revealFromCenter: Boolean = true,
        onComplete: (() -> Unit)? = null
    ) {
        if (revealFromCenter) {
            animateIn(fab, TransitionType.SCALE_UP, DEFAULT_DURATION, EasingCurve.OVERSHOOT, onComplete = onComplete)
        } else {
            animateIn(fab, TransitionType.SLIDE_UP, DEFAULT_DURATION, EasingCurve.EASE_OUT, onComplete = onComplete)
        }
    }

    /**
     * Create settings panel slide animation
     */
    fun animateSettingsPanel(
        panel: View,
        show: Boolean,
        fromSide: TransitionType = TransitionType.SLIDE_RIGHT,
        onComplete: (() -> Unit)? = null
    ) {
        if (show) {
            animateIn(panel, fromSide, DEFAULT_DURATION, EasingCurve.EASE_OUT, onComplete = onComplete)
        } else {
            val exitDirection = when (fromSide) {
                TransitionType.SLIDE_LEFT -> TransitionType.SLIDE_LEFT
                TransitionType.SLIDE_RIGHT -> TransitionType.SLIDE_RIGHT
                TransitionType.SLIDE_UP -> TransitionType.SLIDE_UP
                TransitionType.SLIDE_DOWN -> TransitionType.SLIDE_DOWN
                else -> TransitionType.SLIDE_RIGHT
            }
            animateOut(panel, exitDirection, DEFAULT_DURATION, EasingCurve.EASE_IN, onComplete = onComplete)
        }
    }

    // Private animation creation methods

    private fun createFadeInAnimator(view: View): ObjectAnimator {
        view.alpha = 0f
        return ObjectAnimator.ofFloat(view, "alpha", 0f, 1f)
    }

    private fun createFadeOutAnimator(view: View): ObjectAnimator {
        return ObjectAnimator.ofFloat(view, "alpha", view.alpha, 0f)
    }

    private fun createSlideInAnimator(view: View, fromX: Float, fromY: Float, isVertical: Boolean = false): ObjectAnimator {
        return if (isVertical) {
            view.translationY = fromY
            ObjectAnimator.ofFloat(view, "translationY", fromY, 0f)
        } else {
            view.translationX = fromX
            ObjectAnimator.ofFloat(view, "translationX", fromX, 0f)
        }
    }

    private fun createSlideOutAnimator(view: View, toX: Float, toY: Float, isVertical: Boolean = false): ObjectAnimator {
        return if (isVertical) {
            ObjectAnimator.ofFloat(view, "translationY", view.translationY, toY)
        } else {
            ObjectAnimator.ofFloat(view, "translationX", view.translationX, toX)
        }
    }

    private fun createScaleInAnimator(view: View): AnimatorSet {
        view.scaleX = 0f
        view.scaleY = 0f

        val scaleX = ObjectAnimator.ofFloat(view, "scaleX", 0f, 1f)
        val scaleY = ObjectAnimator.ofFloat(view, "scaleY", 0f, 1f)
        val alpha = ObjectAnimator.ofFloat(view, "alpha", 0f, 1f)

        return AnimatorSet().apply {
            playTogether(scaleX, scaleY, alpha)
        }
    }

    private fun createScaleOutAnimator(view: View): AnimatorSet {
        val scaleX = ObjectAnimator.ofFloat(view, "scaleX", view.scaleX, 0f)
        val scaleY = ObjectAnimator.ofFloat(view, "scaleY", view.scaleY, 0f)
        val alpha = ObjectAnimator.ofFloat(view, "alpha", view.alpha, 0f)

        return AnimatorSet().apply {
            playTogether(scaleX, scaleY, alpha)
        }
    }

    private fun createScaleDownInAnimator(view: View): AnimatorSet {
        view.scaleX = 1.5f
        view.scaleY = 1.5f
        view.alpha = 0f

        val scaleX = ObjectAnimator.ofFloat(view, "scaleX", 1.5f, 1f)
        val scaleY = ObjectAnimator.ofFloat(view, "scaleY", 1.5f, 1f)
        val alpha = ObjectAnimator.ofFloat(view, "alpha", 0f, 1f)

        return AnimatorSet().apply {
            playTogether(scaleX, scaleY, alpha)
        }
    }

    private fun createScaleDownOutAnimator(view: View): AnimatorSet {
        val scaleX = ObjectAnimator.ofFloat(view, "scaleX", view.scaleX, 1.5f)
        val scaleY = ObjectAnimator.ofFloat(view, "scaleY", view.scaleY, 1.5f)
        val alpha = ObjectAnimator.ofFloat(view, "alpha", view.alpha, 0f)

        return AnimatorSet().apply {
            playTogether(scaleX, scaleY, alpha)
        }
    }

    private fun createElasticBounceInAnimator(view: View): AnimatorSet {
        view.scaleX = 0f
        view.scaleY = 0f
        view.alpha = 0f

        val scaleX = ObjectAnimator.ofFloat(view, "scaleX", 0f, 1.2f, 0.9f, 1.1f, 1f)
        val scaleY = ObjectAnimator.ofFloat(view, "scaleY", 0f, 1.2f, 0.9f, 1.1f, 1f)
        val alpha = ObjectAnimator.ofFloat(view, "alpha", 0f, 1f)

        return AnimatorSet().apply {
            playTogether(scaleX, scaleY, alpha)
            duration = ELASTIC_DURATION
        }
    }

    private fun createElasticBounceOutAnimator(view: View): AnimatorSet {
        val scaleX = ObjectAnimator.ofFloat(view, "scaleX", view.scaleX, 1.1f, 0.9f, 1.2f, 0f)
        val scaleY = ObjectAnimator.ofFloat(view, "scaleY", view.scaleY, 1.1f, 0.9f, 1.2f, 0f)
        val alpha = ObjectAnimator.ofFloat(view, "alpha", view.alpha, 0f)

        return AnimatorSet().apply {
            playTogether(scaleX, scaleY, alpha)
            duration = ELASTIC_DURATION
        }
    }

    private fun createRotation3DInAnimator(view: View): AnimatorSet {
        view.rotationY = 90f
        view.alpha = 0f

        val rotationY = ObjectAnimator.ofFloat(view, "rotationY", 90f, 0f)
        val alpha = ObjectAnimator.ofFloat(view, "alpha", 0f, 1f)

        return AnimatorSet().apply {
            playTogether(rotationY, alpha)
        }
    }

    private fun createRotation3DOutAnimator(view: View): AnimatorSet {
        val rotationY = ObjectAnimator.ofFloat(view, "rotationY", view.rotationY, -90f)
        val alpha = ObjectAnimator.ofFloat(view, "alpha", view.alpha, 0f)

        return AnimatorSet().apply {
            playTogether(rotationY, alpha)
        }
    }

    private fun createMorphingInAnimator(view: View): AnimatorSet {
        view.scaleX = 0.3f
        view.scaleY = 2f
        view.alpha = 0f

        val scaleX = ObjectAnimator.ofFloat(view, "scaleX", 0.3f, 1f)
        val scaleY = ObjectAnimator.ofFloat(view, "scaleY", 2f, 1f)
        val alpha = ObjectAnimator.ofFloat(view, "alpha", 0f, 1f)

        return AnimatorSet().apply {
            playTogether(scaleX, scaleY, alpha)
        }
    }

    private fun createMorphingOutAnimator(view: View): AnimatorSet {
        val scaleX = ObjectAnimator.ofFloat(view, "scaleX", view.scaleX, 0.3f)
        val scaleY = ObjectAnimator.ofFloat(view, "scaleY", view.scaleY, 2f)
        val alpha = ObjectAnimator.ofFloat(view, "alpha", view.alpha, 0f)

        return AnimatorSet().apply {
            playTogether(scaleX, scaleY, alpha)
        }
    }

    private fun createRippleRevealAnimator(view: View): ObjectAnimator {
        view.scaleX = 0f
        view.scaleY = 0f
        val animatorSet = AnimatorSet()
        val scaleX = ObjectAnimator.ofFloat(view, "scaleX", 0f, 1f)
        val scaleY = ObjectAnimator.ofFloat(view, "scaleY", 0f, 1f)
        animatorSet.playTogether(scaleX, scaleY)
        animatorSet.interpolator = OvershootInterpolator(0.5f)
        return scaleX // Return one animator for compatibility
    }

    private fun createRippleHideAnimator(view: View): ObjectAnimator {
        val animatorSet = AnimatorSet()
        val scaleX = ObjectAnimator.ofFloat(view, "scaleX", view.scaleX, 0f)
        val scaleY = ObjectAnimator.ofFloat(view, "scaleY", view.scaleY, 0f)
        animatorSet.playTogether(scaleX, scaleY)
        animatorSet.interpolator = AnticipateInterpolator(0.5f)
        return scaleX // Return one animator for compatibility
    }

    private fun configureAnimator(
        animator: Animator,
        duration: Long,
        easing: EasingCurve,
        delay: Long,
        onComplete: (() -> Unit)?
    ) {
        animator.duration = duration
        animator.startDelay = delay
        animator.interpolator = getInterpolator(easing)

        onComplete?.let { callback ->
            animator.doOnEnd { callback() }
        }
    }

    private fun getInterpolator(easing: EasingCurve): TimeInterpolator {
        return when (easing) {
            EasingCurve.LINEAR -> LinearInterpolator()
            EasingCurve.EASE_IN -> AccelerateInterpolator()
            EasingCurve.EASE_OUT -> DecelerateInterpolator()
            EasingCurve.EASE_IN_OUT -> AccelerateDecelerateInterpolator()
            EasingCurve.BOUNCE -> BounceInterpolator()
            EasingCurve.ELASTIC -> OvershootInterpolator(1.5f)
            EasingCurve.OVERSHOOT -> OvershootInterpolator()
            EasingCurve.ANTICIPATE -> AnticipateInterpolator()
        }
    }
}