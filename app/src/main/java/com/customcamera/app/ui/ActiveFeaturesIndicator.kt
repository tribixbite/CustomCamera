package com.customcamera.app.ui

import android.content.Context
import android.graphics.Color
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.FrameLayout

/**
 * Indicator overlay showing currently active camera features
 * (grid, barcode, HDR, night mode, etc.) for user awareness.
 */
class ActiveFeaturesIndicator(
    private val context: Context,
    private val parentView: ViewGroup
) {

    private var indicatorView: LinearLayout? = null
    private val activeFeatures = mutableMapOf<String, String>()

    /**
     * Initialize the indicator overlay
     */
    fun initialize() {
        createIndicatorView()
        Log.i(TAG, "Active features indicator initialized")
    }

    private fun createIndicatorView() {
        indicatorView = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(16, 12, 16, 12)
            setBackgroundColor(Color.parseColor("#AA000000"))
            visibility = View.GONE

            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                setMargins(0, 80, 0, 0)
            }
        }

        parentView.addView(indicatorView)
    }

    /**
     * Add or update a feature indicator
     */
    fun setFeature(key: String, label: String, active: Boolean) {
        if (active) {
            activeFeatures[key] = label
        } else {
            activeFeatures.remove(key)
        }
        updateDisplay()
    }

    /**
     * Convenience methods for common features
     */
    fun setGridActive(active: Boolean) {
        setFeature("grid", "📐 Grid", active)
    }

    fun setBarcodeActive(active: Boolean) {
        setFeature("barcode", "📱 Barcode", active)
    }

    fun setHDRActive(active: Boolean) {
        setFeature("hdr", "🌄 HDR", active)
    }

    fun setNightModeActive(active: Boolean) {
        setFeature("night", "🌙 Night", active)
    }

    fun setProModeActive(active: Boolean) {
        setFeature("pro", "⚙️ Pro", active)
    }

    fun setCropActive(active: Boolean) {
        setFeature("crop", "✂️ Crop", active)
    }

    fun setStabilizationActive(active: Boolean) {
        setFeature("stabilization", "📹 Stab", active)
    }

    fun setAIActive(active: Boolean) {
        setFeature("ai", "🤖 AI", active)
    }

    /**
     * Update the display based on active features
     */
    private fun updateDisplay() {
        indicatorView?.removeAllViews()

        if (activeFeatures.isEmpty()) {
            hide()
            return
        }

        // Add feature badges
        activeFeatures.values.forEachIndexed { index, label ->
            val badge = TextView(context).apply {
                text = label
                textSize = 12f
                setTextColor(Color.WHITE)
                setPadding(12, 6, 12, 6)
                setBackgroundColor(Color.parseColor("#60FFFFFF"))
            }

            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                if (index > 0) setMargins(8, 0, 0, 0)
            }

            indicatorView?.addView(badge, params)
        }

        show()
        Log.i(TAG, "Updated active features: ${activeFeatures.values.joinToString(", ")}")
    }

    /**
     * Show the indicator
     */
    private fun show() {
        if (indicatorView?.visibility == View.VISIBLE) return

        indicatorView?.let { view ->
            view.alpha = 0f
            view.visibility = View.VISIBLE
            view.animate()
                .alpha(1f)
                .setDuration(200)
                .start()
        }
    }

    /**
     * Hide the indicator
     */
    private fun hide() {
        indicatorView?.animate()
            ?.alpha(0f)
            ?.setDuration(200)
            ?.withEndAction {
                indicatorView?.visibility = View.GONE
            }
            ?.start()
    }

    /**
     * Clear all active features
     */
    fun clear() {
        activeFeatures.clear()
        updateDisplay()
    }

    companion object {
        private const val TAG = "ActiveFeaturesIndicator"
    }
}
