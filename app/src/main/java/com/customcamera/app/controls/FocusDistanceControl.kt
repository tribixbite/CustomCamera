package com.customcamera.app.controls

import android.content.Context
import android.util.Log
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.camera.core.Camera
import com.customcamera.app.engine.CameraContext
import kotlin.math.sqrt

/**
 * FocusDistanceControl provides manual focus distance control
 * with distance display and hyperfocal distance calculator.
 */
class FocusDistanceControl(
    private val context: Context,
    private val cameraContext: CameraContext
) {

    // Focus distance configuration
    private var focusDistance: Float = 0.0f // 0.0 = infinity, 1.0 = closest
    private var isManualFocus: Boolean = false
    private var focusPeakingEnabled: Boolean = false

    // Camera characteristics (estimated)
    private val minimumFocusDistance: Float = 0.1f // 10cm
    private val focalLength: Float = 4.0f // mm (typical smartphone)
    private val aperture: Float = 1.8f // f/1.8 (typical)

    // UI components
    private var focusDistanceSlider: SeekBar? = null
    private var focusDistanceText: TextView? = null
    private var hyperfocalText: TextView? = null
    private var focusPeakingToggle: androidx.appcompat.widget.SwitchCompat? = null
    private var manualFocusToggle: androidx.appcompat.widget.SwitchCompat? = null

    /**
     * Create focus distance control UI
     */
    fun createFocusDistanceControlUI(): LinearLayout {
        Log.i(TAG, "Creating focus distance control UI")

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 8, 16, 8)
        }

        // Title
        val titleText = TextView(context).apply {
            text = "Manual Focus Distance"
            textSize = 18f
            setPadding(0, 0, 0, 8)
        }
        container.addView(titleText)

        // Manual focus toggle
        manualFocusToggle = androidx.appcompat.widget.SwitchCompat(context).apply {
            text = "Manual Focus"
            isChecked = isManualFocus
            setOnCheckedChangeListener { _, isChecked ->
                setManualFocus(isChecked)
            }
        }
        container.addView(manualFocusToggle)

        // Focus peaking toggle
        focusPeakingToggle = androidx.appcompat.widget.SwitchCompat(context).apply {
            text = "Focus Peaking Indicator"
            isChecked = focusPeakingEnabled
            setOnCheckedChangeListener { _, isChecked ->
                setFocusPeaking(isChecked)
            }
        }
        container.addView(focusPeakingToggle)

        // Focus distance display (cm/m/infinity)
        focusDistanceText = TextView(context).apply {
            text = getFocusDistanceDisplay()
            textSize = 16f
            setPadding(0, 8, 0, 4)
        }
        container.addView(focusDistanceText)

        // Manual focus distance slider
        focusDistanceSlider = SeekBar(context).apply {
            max = 100
            progress = (focusDistance * 100).toInt()
            isEnabled = isManualFocus

            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser && isManualFocus) {
                        val distance = progress / 100f
                        setFocusDistance(distance)
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        container.addView(focusDistanceSlider)

        // Hyperfocal distance calculator
        hyperfocalText = TextView(context).apply {
            textSize = 12f
            setPadding(0, 4, 0, 0)
            text = calculateHyperfocalDistance()
        }
        container.addView(hyperfocalText)

        Log.i(TAG, "Focus distance control UI created")
        return container
    }

    /**
     * Set focus distance (0.0 = infinity, 1.0 = closest)
     */
    fun setFocusDistance(distance: Float) {
        val clampedDistance = distance.coerceIn(0f, 1f)

        if (focusDistance != clampedDistance) {
            focusDistance = clampedDistance
            isManualFocus = true

            updateFocusDistanceUI()
            saveSettings()

            Log.i(TAG, "Focus distance set to: $clampedDistance (${getFocusDistanceDisplay()})")

            cameraContext.debugLogger.logPlugin(
                "FocusDistanceControl",
                "focus_distance_changed",
                mapOf(
                    "distance" to clampedDistance,
                    "displayText" to getFocusDistanceDisplay()
                )
            )
        }
    }

    /**
     * Set manual focus mode
     */
    fun setManualFocus(enabled: Boolean) {
        if (isManualFocus != enabled) {
            isManualFocus = enabled

            if (!enabled) {
                focusDistance = 0.0f // Reset to infinity when disabling
            }

            updateFocusDistanceUI()
            saveSettings()

            Log.i(TAG, "Manual focus ${if (enabled) "enabled" else "disabled"}")

            cameraContext.debugLogger.logPlugin(
                "FocusDistanceControl",
                "manual_focus_toggled",
                mapOf("enabled" to enabled)
            )
        }
    }

    /**
     * Set focus peaking indicator
     */
    fun setFocusPeaking(enabled: Boolean) {
        if (focusPeakingEnabled != enabled) {
            focusPeakingEnabled = enabled
            saveSettings()

            Log.i(TAG, "Focus peaking ${if (enabled) "enabled" else "disabled"}")

            cameraContext.debugLogger.logPlugin(
                "FocusDistanceControl",
                "focus_peaking_toggled",
                mapOf("enabled" to enabled)
            )
        }
    }

    /**
     * Apply focus distance to camera
     */
    fun applyFocusDistanceToCamera(camera: Camera): Boolean {
        return try {
            if (isManualFocus) {
                Log.i(TAG, "Applying manual focus distance: $focusDistance")

                // Note: CameraX has limited manual focus control
                // Full manual focus requires Camera2 API
                // In production with Camera2:
                // captureRequestBuilder.set(CaptureRequest.LENS_FOCUS_DISTANCE, focusDistance * minimumFocusDistance)
            } else {
                Log.i(TAG, "Using auto focus")
                // Return to continuous autofocus
                camera.cameraControl.cancelFocusAndMetering()
            }

            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply focus distance", e)
            false
        }
    }

    /**
     * Focus distance display (cm/m/infinity)
     */
    fun getFocusDistanceDisplay(): String {
        return when {
            focusDistance == 0f -> "∞ (Infinity)"
            focusDistance < 0.1f -> {
                val meters = (1f / (focusDistance * 10f + 1f)) * 10f
                "${String.format("%.1f", meters)}m"
            }
            focusDistance < 0.5f -> {
                val cm = (1f / (focusDistance * 5f + 0.1f)) * 100f
                "${String.format("%.0f", cm)}cm"
            }
            else -> {
                val cm = minimumFocusDistance * 100f / focusDistance
                "${String.format("%.0f", cm)}cm"
            }
        }
    }

    /**
     * Hyperfocal distance calculator
     */
    fun calculateHyperfocalDistance(): String {
        try {
            // Hyperfocal distance = (focal_length^2) / (aperture * circle_of_confusion) + focal_length
            val circleOfConfusion = 0.03f // mm for smartphone sensor
            val hyperfocalMm = (focalLength * focalLength) / (aperture * circleOfConfusion) + focalLength
            val hyperfocalM = hyperfocalMm / 1000f

            return if (hyperfocalM > 1f) {
                "📏 Hyperfocal: ${String.format("%.1f", hyperfocalM)}m"
            } else {
                "📏 Hyperfocal: ${String.format("%.0f", hyperfocalMm)}mm"
            }
        } catch (e: Exception) {
            return "📏 Hyperfocal: Unable to calculate"
        }
    }

    /**
     * Get current focus settings
     */
    fun getFocusDistanceSettings(): Map<String, Any> {
        return mapOf(
            "focusDistance" to focusDistance,
            "isManualFocus" to isManualFocus,
            "focusPeakingEnabled" to focusPeakingEnabled,
            "focusDistanceDisplay" to getFocusDistanceDisplay(),
            "hyperfocalDistance" to calculateHyperfocalDistance(),
            "minimumFocusDistance" to minimumFocusDistance
        )
    }

    private fun updateFocusDistanceUI() {
        focusDistanceText?.text = getFocusDistanceDisplay()
        focusDistanceSlider?.apply {
            progress = (focusDistance * 100).toInt()
            isEnabled = isManualFocus
        }
        manualFocusToggle?.isChecked = isManualFocus
        focusPeakingToggle?.isChecked = focusPeakingEnabled
        hyperfocalText?.text = calculateHyperfocalDistance()
    }

    private fun saveSettings() {
        val settings = cameraContext.settingsManager
        settings.setPluginSetting("FocusDistanceControl", "focusDistance", focusDistance.toString())
        settings.setPluginSetting("FocusDistanceControl", "manualFocus", isManualFocus.toString())
        settings.setPluginSetting("FocusDistanceControl", "focusPeaking", focusPeakingEnabled.toString())
    }

    private fun loadSettings() {
        try {
            val settings = cameraContext.settingsManager
            focusDistance = settings.getPluginSetting("FocusDistanceControl", "focusDistance", "0.0").toFloat()
            isManualFocus = settings.getPluginSetting("FocusDistanceControl", "manualFocus", "false").toBoolean()
            focusPeakingEnabled = settings.getPluginSetting("FocusDistanceControl", "focusPeaking", "false").toBoolean()

            Log.i(TAG, "Loaded settings: distance=$focusDistance, manual=$isManualFocus, peaking=$focusPeakingEnabled")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load settings, using defaults", e)
        }
    }

    init {
        loadSettings()
    }

    companion object {
        private const val TAG = "FocusDistanceControl"
    }
}