package com.customcamera.app.pip

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.camera.core.Camera
import androidx.camera.view.PreviewView
import com.customcamera.app.plugins.PiPPlugin

/**
 * PiPOverlayView provides the UI implementation for picture-in-picture
 * camera overlay with draggable positioning and size controls.
 */
class PiPOverlayView(context: Context) : FrameLayout(context) {

    private var mainPreviewView: PreviewView? = null
    private var pipPreviewView: PreviewView? = null

    // PiP configuration
    private var pipPosition: PiPPlugin.PiPPosition = PiPPlugin.PiPPosition.TOP_RIGHT
    private var pipSize: PiPPlugin.PiPSize = PiPPlugin.PiPSize.SMALL
    private var isPiPVisible: Boolean = false

    // Touch handling for dragging
    private var isDragging: Boolean = false
    private var lastTouchX: Float = 0f
    private var lastTouchY: Float = 0f

    // Animation
    private var animationDuration: Long = 300L

    init {
        setupPiPContainer()
    }

    /**
     * Set main preview (typically rear camera)
     */
    fun setMainPreview(preview: PreviewView) {
        Log.i(TAG, "Setting main preview")

        mainPreviewView?.let { removeView(it) }
        mainPreviewView = preview

        val layoutParams = LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.MATCH_PARENT
        )
        addView(preview, 0, layoutParams) // Add as background
    }

    /**
     * Set PiP preview (typically front camera)
     */
    fun setPiPPreview(preview: PreviewView) {
        Log.i(TAG, "Setting PiP preview")

        pipPreviewView?.let { removeView(it) }
        pipPreviewView = preview

        val layoutParams = createPiPLayoutParams()
        addView(preview, layoutParams) // Add as overlay

        setupPiPTouchHandling(preview)
        isPiPVisible = true

        Log.i(TAG, "PiP preview set at position: $pipPosition, size: $pipSize")
    }

    /**
     * Animate PiP position change
     */
    fun animatePiPPosition() {
        val pipView = pipPreviewView ?: return

        Log.d(TAG, "Animating PiP position change")

        val newLayoutParams = createPiPLayoutParams()

        // Animate to new position
        val animX = ObjectAnimator.ofFloat(pipView, "translationX", newLayoutParams.leftMargin.toFloat())
        val animY = ObjectAnimator.ofFloat(pipView, "translationY", newLayoutParams.topMargin.toFloat())

        AnimatorSet().apply {
            playTogether(animX, animY)
            duration = animationDuration
            start()
        }

        // Update layout params after animation
        pipView.layoutParams = newLayoutParams
    }

    /**
     * Toggle PiP size
     */
    fun togglePiPSize() {
        pipSize = when (pipSize) {
            PiPPlugin.PiPSize.SMALL -> PiPPlugin.PiPSize.MEDIUM
            PiPPlugin.PiPSize.MEDIUM -> PiPPlugin.PiPSize.LARGE
            PiPPlugin.PiPSize.LARGE -> PiPPlugin.PiPSize.SMALL
        }

        updatePiPSize()
        Log.i(TAG, "PiP size toggled to: $pipSize")
    }

    /**
     * Swap main and PiP cameras with animation
     */
    fun swapCameras() {
        val mainView = mainPreviewView
        val pipView = pipPreviewView

        if (mainView == null || pipView == null) {
            Log.w(TAG, "Cannot swap cameras - views not set")
            return
        }

        Log.i(TAG, "Swapping cameras with animation")

        // Animate swap
        val mainScaleDown = ObjectAnimator.ofFloat(mainView, "scaleX", 1f, 0.8f)
        val mainScaleUp = ObjectAnimator.ofFloat(mainView, "scaleX", 0.8f, 1f)

        val pipScaleDown = ObjectAnimator.ofFloat(pipView, "scaleX", 1f, 0.8f)
        val pipScaleUp = ObjectAnimator.ofFloat(pipView, "scaleX", 0.8f, 1f)

        AnimatorSet().apply {
            play(mainScaleDown).with(pipScaleDown)
            play(mainScaleUp).after(mainScaleDown)
            play(pipScaleUp).after(pipScaleDown)
            duration = animationDuration / 2
            start()
        }

        // Swap references
        mainPreviewView = pipView
        pipPreviewView = mainView
    }

    /**
     * Animate swap for external use
     */
    fun animateSwap() {
        val pipView = pipPreviewView ?: return

        // Quick scale animation to indicate swap
        val scaleDown = ObjectAnimator.ofFloat(pipView, "scaleX", 1f, 0.8f)
        val scaleUp = ObjectAnimator.ofFloat(pipView, "scaleX", 0.8f, 1f)

        AnimatorSet().apply {
            play(scaleUp).after(scaleDown)
            duration = animationDuration / 2
            start()
        }

        Log.d(TAG, "Swap animation played")
    }

    /**
     * Set PiP position
     */
    fun setPiPPosition(position: PiPPlugin.PiPPosition) {
        if (pipPosition != position) {
            pipPosition = position
            updatePiPPosition()
            Log.i(TAG, "PiP position set to: $position")
        }
    }

    /**
     * Set PiP size
     */
    fun setPiPSize(size: PiPPlugin.PiPSize) {
        if (pipSize != size) {
            pipSize = size
            updatePiPSize()
            Log.i(TAG, "PiP size set to: $size")
        }
    }

    /**
     * Show PiP overlay
     */
    fun showPiP() {
        if (!isPiPVisible) {
            visibility = VISIBLE
            isPiPVisible = true

            // Fade in animation
            alpha = 0f
            animate()
                .alpha(1f)
                .setDuration(animationDuration)
                .start()

            Log.i(TAG, "PiP overlay shown")
        }
    }

    /**
     * Hide PiP overlay
     */
    fun hidePiP() {
        if (isPiPVisible) {
            // Fade out animation
            animate()
                .alpha(0f)
                .setDuration(animationDuration)
                .withEndAction {
                    visibility = GONE
                    isPiPVisible = false
                }
                .start()

            Log.i(TAG, "PiP overlay hidden")
        }
    }

    /**
     * Update for camera changes
     */
    fun updateForCamera(camera: Camera) {
        Log.d(TAG, "Updating PiP for camera change")
        // Update any camera-specific PiP settings
    }

    private fun setupPiPContainer() {
        layoutParams = LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.MATCH_PARENT
        )
    }

    private fun createPiPLayoutParams(): LayoutParams {
        val density = resources.displayMetrics.density
        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels

        // Calculate size based on pip size setting
        val (width, height) = when (pipSize) {
            PiPPlugin.PiPSize.SMALL -> Pair((120 * density).toInt(), (160 * density).toInt())
            PiPPlugin.PiPSize.MEDIUM -> Pair((180 * density).toInt(), (240 * density).toInt())
            PiPPlugin.PiPSize.LARGE -> Pair((240 * density).toInt(), (320 * density).toInt())
        }

        // Calculate position based on pip position setting
        val margin = (16 * density).toInt()
        val (leftMargin, topMargin) = when (pipPosition) {
            PiPPlugin.PiPPosition.TOP_LEFT -> Pair(margin, margin)
            PiPPlugin.PiPPosition.TOP_RIGHT -> Pair(screenWidth - width - margin, margin)
            PiPPlugin.PiPPosition.BOTTOM_LEFT -> Pair(margin, screenHeight - height - margin)
            PiPPlugin.PiPPosition.BOTTOM_RIGHT -> Pair(screenWidth - width - margin, screenHeight - height - margin)
        }

        return LayoutParams(width, height).apply {
            this.leftMargin = leftMargin
            this.topMargin = topMargin
        }
    }

    private fun updatePiPPosition() {
        val pipView = pipPreviewView ?: return
        val newLayoutParams = createPiPLayoutParams()

        pipView.layoutParams = newLayoutParams
        animatePiPPosition()
    }

    private fun updatePiPSize() {
        val pipView = pipPreviewView ?: return
        val newLayoutParams = createPiPLayoutParams()

        // Animate size change
        val scaleX = ObjectAnimator.ofFloat(pipView, "scaleX", 1f, 0.8f, 1f)
        val scaleY = ObjectAnimator.ofFloat(pipView, "scaleY", 1f, 0.8f, 1f)

        AnimatorSet().apply {
            playTogether(scaleX, scaleY)
            duration = animationDuration
            start()
        }

        pipView.layoutParams = newLayoutParams
    }

    private fun setupPiPTouchHandling(pipView: PreviewView) {
        pipView.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isDragging = true
                    lastTouchX = event.rawX
                    lastTouchY = event.rawY
                    Log.d(TAG, "PiP touch started")
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isDragging) {
                        val deltaX = event.rawX - lastTouchX
                        val deltaY = event.rawY - lastTouchY

                        view.translationX += deltaX
                        view.translationY += deltaY

                        lastTouchX = event.rawX
                        lastTouchY = event.rawY
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (isDragging) {
                        isDragging = false
                        snapToNearestCorner(view)
                        Log.d(TAG, "PiP touch ended")
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun snapToNearestCorner(view: View) {
        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels
        val margin = (16 * resources.displayMetrics.density).toInt()

        val centerX = view.x + view.width / 2
        val centerY = view.y + view.height / 2

        // Determine nearest corner
        val newPosition = when {
            centerX < screenWidth / 2 && centerY < screenHeight / 2 -> PiPPlugin.PiPPosition.TOP_LEFT
            centerX >= screenWidth / 2 && centerY < screenHeight / 2 -> PiPPlugin.PiPPosition.TOP_RIGHT
            centerX < screenWidth / 2 && centerY >= screenHeight / 2 -> PiPPlugin.PiPPosition.BOTTOM_LEFT
            else -> PiPPlugin.PiPPosition.BOTTOM_RIGHT
        }

        // Update position and animate to corner
        pipPosition = newPosition
        animatePiPPosition()

        Log.d(TAG, "PiP snapped to: $newPosition")
    }

    companion object {
        private const val TAG = "PiPOverlayView"
    }
}