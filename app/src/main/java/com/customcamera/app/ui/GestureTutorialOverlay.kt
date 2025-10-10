package com.customcamera.app.ui

import android.content.Context
import android.graphics.Color
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*

/**
 * First-run tutorial overlay explaining gesture controls
 * to help users discover camera features.
 */
class GestureTutorialOverlay(
    private val context: Context,
    private val parentView: ViewGroup
) {

    private var overlayView: LinearLayout? = null
    private val PREFS_NAME = "CustomCameraTutorial"
    private val PREF_TUTORIAL_SHOWN = "tutorial_shown"

    var onComplete: (() -> Unit)? = null

    /**
     * Initialize the tutorial overlay
     */
    fun initialize() {
        createOverlayView()
        Log.i(TAG, "Gesture tutorial overlay initialized")
    }

    private fun createOverlayView() {
        overlayView = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
            setBackgroundColor(Color.parseColor("#EE000000"))
            visibility = View.GONE

            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // Add title
        addTitle()

        // Add gesture explanations
        addGestureItem("Double Tap", "Toggle composition grid overlay")
        addGestureItem("Triple Tap", "Toggle barcode/QR scanning")
        addGestureItem("Quadruple Tap", "Toggle crop mode")
        addGestureItem("Five Taps", "Toggle smart scene detection")
        addGestureItem("Six Taps", "Toggle object detection")
        addGestureItem("Pinch", "Zoom in/out")
        addGestureItem("Long Press", "Show active features status")
        addGestureItem("Swipe Left", "Open quick settings (coming soon)")

        addDivider()

        // Add button tips
        addTitle("Button Controls")
        addGestureItem("Settings Button", "Long press for full settings")
        addGestureItem("Shutter Button", "Tap to capture photo")
        addGestureItem("Flash Button", "Cycle through flash modes")
        addGestureItem("Switch Button", "Change camera")

        // Add action buttons
        addActionButtons()

        parentView.addView(overlayView)
    }

    private fun addTitle() {
        val title = TextView(context).apply {
            text = "📱 Camera Gestures & Controls"
            textSize = 24f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, 32)
            gravity = Gravity.CENTER
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        overlayView?.addView(title)
    }

    private fun addGestureItem(gesture: String, description: String) {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 12, 0, 12)
        }

        val gestureText = TextView(context).apply {
            text = gesture
            textSize = 16f
            setTextColor(Color.parseColor("#4CAF50"))
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                180,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        container.addView(gestureText)

        val descText = TextView(context).apply {
            text = description
            textSize = 14f
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        }
        container.addView(descText)

        overlayView?.addView(container)
    }

    private fun addDivider() {
        val divider = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                2
            ).apply {
                setMargins(0, 24, 0, 24)
            }
            setBackgroundColor(Color.parseColor("#40FFFFFF"))
        }
        overlayView?.addView(divider)
    }

    private fun addActionButtons() {
        val buttonContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 32, 0, 0)
        }

        // Don't show again button
        val dontShowButton = Button(context).apply {
            text = "Don't Show Again"
            setPadding(32, 24, 32, 24)
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                setMargins(0, 0, 8, 0)
            }
            setOnClickListener {
                markTutorialComplete()
                hide()
            }
        }
        buttonContainer.addView(dontShowButton)

        // Got it button
        val gotItButton = Button(context).apply {
            text = "Got It!"
            setPadding(32, 24, 32, 24)
            setBackgroundColor(Color.parseColor("#4CAF50"))
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                setMargins(8, 0, 0, 0)
            }
            setOnClickListener {
                hide()
            }
        }
        buttonContainer.addView(gotItButton)

        overlayView?.addView(buttonContainer)
    }

    /**
     * Show tutorial if not shown before
     */
    fun showIfNeeded(): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val shown = prefs.getBoolean(PREF_TUTORIAL_SHOWN, false)

        if (!shown) {
            show()
            return true
        }
        return false
    }

    /**
     * Show the tutorial
     */
    fun show() {
        overlayView?.let { view ->
            view.alpha = 0f
            view.visibility = View.VISIBLE
            view.animate()
                .alpha(1f)
                .setDuration(300)
                .start()

            Log.i(TAG, "Tutorial shown")
        }
    }

    /**
     * Hide the tutorial
     */
    fun hide() {
        overlayView?.animate()
            ?.alpha(0f)
            ?.setDuration(200)
            ?.withEndAction {
                overlayView?.visibility = View.GONE
                onComplete?.invoke()
                Log.i(TAG, "Tutorial hidden")
            }
            ?.start()
    }

    /**
     * Mark tutorial as complete (don't show again)
     */
    private fun markTutorialComplete() {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(PREF_TUTORIAL_SHOWN, true).apply()
        Log.i(TAG, "Tutorial marked as complete")
    }

    /**
     * Reset tutorial (for testing or user request)
     */
    fun reset() {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(PREF_TUTORIAL_SHOWN, false).apply()
        Log.i(TAG, "Tutorial reset")
    }

    companion object {
        private const val TAG = "GestureTutorial"
    }
}
