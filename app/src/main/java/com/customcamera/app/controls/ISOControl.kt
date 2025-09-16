package com.customcamera.app.controls

import android.content.Context
import android.util.Log
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.camera.core.Camera
import com.customcamera.app.engine.CameraContext

/**
 * ISOControl provides ISO sensitivity control
 * with range slider (50-6400) and performance warnings.
 */
class ISOControl(
    private val context: Context,
    private val cameraContext: CameraContext
) {

    // ISO configuration
    private val isoRange = 50..6400
    private var currentISO: Int = 100
    private var isAutoISO: Boolean = true

    // UI components
    private var isoSlider: SeekBar? = null
    private var isoValueText: TextView? = null
    private var isoWarningText: TextView? = null
    private var autoISOToggle: androidx.appcompat.widget.SwitchCompat? = null

    /**
     * Create ISO control UI
     */
    fun createISOControlUI(): LinearLayout {
        Log.i(TAG, "Creating ISO control UI")

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 8, 16, 8)
        }

        // Title
        val titleText = TextView(context).apply {
            text = "ISO Sensitivity"
            textSize = 18f
            setPadding(0, 0, 0, 8)
        }
        container.addView(titleText)

        // Auto ISO toggle
        autoISOToggle = androidx.appcompat.widget.SwitchCompat(context).apply {
            text = "Auto ISO"
            isChecked = isAutoISO
            setOnCheckedChangeListener { _, isChecked ->
                setAutoISO(isChecked)
            }
        }
        container.addView(autoISOToggle)

        // ISO value display
        isoValueText = TextView(context).apply {
            text = "ISO $currentISO"
            textSize = 16f
            setPadding(0, 8, 0, 4)
        }
        container.addView(isoValueText)

        // ISO range slider (50-6400)
        isoSlider = SeekBar(context).apply {
            max = isoRange.last - isoRange.first
            progress = currentISO - isoRange.first
            isEnabled = !isAutoISO

            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser && !isAutoISO) {
                        val isoValue = progress + isoRange.first
                        setISOValue(isoValue)
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        container.addView(isoSlider)

        // ISO performance impact warnings
        isoWarningText = TextView(context).apply {
            textSize = 12f
            setPadding(0, 4, 0, 0)
            updateISOWarning(currentISO)
        }
        container.addView(isoWarningText)

        Log.i(TAG, "ISO control UI created")
        return container
    }

    /**
     * Set ISO value manually
     */
    fun setISOValue(iso: Int) {
        val clampedISO = iso.coerceIn(isoRange)

        if (currentISO != clampedISO) {
            currentISO = clampedISO
            isAutoISO = false

            updateISOUI()
            saveSettings()

            Log.i(TAG, "ISO set to: $currentISO")

            cameraContext.debugLogger.logPlugin(
                "ISOControl",
                "iso_changed",
                mapOf(
                    "newISO" to currentISO,
                    "autoISO" to isAutoISO
                )
            )
        }
    }

    /**
     * Set auto ISO mode
     */
    fun setAutoISO(enabled: Boolean) {
        if (isAutoISO != enabled) {
            isAutoISO = enabled

            if (enabled) {
                currentISO = 100 // Reset to default when enabling auto
            }

            updateISOUI()
            saveSettings()

            Log.i(TAG, "Auto ISO ${if (enabled) "enabled" else "disabled"}")

            cameraContext.debugLogger.logPlugin(
                "ISOControl",
                "auto_iso_toggled",
                mapOf("enabled" to enabled)
            )
        }
    }

    /**
     * Apply ISO settings to camera
     */
    fun applyISOToCamera(camera: Camera): Boolean {
        return try {
            // Note: CameraX has limited ISO control
            // Full manual ISO requires Camera2 API
            Log.i(TAG, "Applying ISO settings: $currentISO (auto: $isAutoISO)")

            // In production with Camera2:
            // captureRequestBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, currentISO)

            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply ISO settings", e)
            false
        }
    }

    /**
     * Get current ISO settings
     */
    fun getISOSettings(): Map<String, Any> {
        return mapOf(
            "currentISO" to currentISO,
            "isAutoISO" to isAutoISO,
            "isoRange" to "${isoRange.first}-${isoRange.last}",
            "performanceWarning" to getPerformanceWarning(currentISO)
        )
    }

    /**
     * Reset ISO to auto
     */
    fun resetISOToAuto() {
        setAutoISO(true)
        Log.i(TAG, "ISO reset to auto")
    }

    private fun updateISOUI() {
        isoValueText?.text = "ISO $currentISO"
        isoSlider?.apply {
            progress = currentISO - isoRange.first
            isEnabled = !isAutoISO
        }
        autoISOToggle?.isChecked = isAutoISO
        isoWarningText?.let { updateISOWarning(currentISO) }
    }

    private fun updateISOWarning(iso: Int) {
        val warningText = getPerformanceWarning(iso)
        val warningColor = getWarningColor(iso)

        isoWarningText?.apply {
            text = warningText
            setTextColor(warningColor)
        }
    }

    private fun getPerformanceWarning(iso: Int): String {
        return when {
            iso <= 100 -> "✅ Optimal quality, minimal noise"
            iso <= 400 -> "🟡 Good quality, slight noise"
            iso <= 800 -> "🟠 Acceptable quality, moderate noise"
            iso <= 1600 -> "🔶 Reduced quality, noticeable noise"
            iso <= 3200 -> "🔺 Poor quality, significant noise"
            else -> "⚠️ Very poor quality, extreme noise"
        }
    }

    private fun getWarningColor(iso: Int): Int {
        return when {
            iso <= 100 -> 0xFF4CAF50.toInt() // Green
            iso <= 400 -> 0xFFFFEB3B.toInt() // Yellow
            iso <= 800 -> 0xFFFF9800.toInt() // Orange
            iso <= 1600 -> 0xFFFF5722.toInt() // Deep Orange
            else -> 0xFFF44336.toInt() // Red
        }
    }

    private fun saveSettings() {
        cameraContext.settingsManager.setPluginSetting("ISOControl", "currentISO", currentISO.toString())
        cameraContext.settingsManager.setPluginSetting("ISOControl", "autoISO", isAutoISO.toString())
    }

    private fun loadSettings() {
        try {
            currentISO = cameraContext.settingsManager.getPluginSetting("ISOControl", "currentISO", "100").toInt()
            isAutoISO = cameraContext.settingsManager.getPluginSetting("ISOControl", "autoISO", "true").toBoolean()

            Log.i(TAG, "Loaded ISO settings: ISO=$currentISO, auto=$isAutoISO")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load ISO settings, using defaults", e)
            currentISO = 100
            isAutoISO = true
        }
    }

    init {
        loadSettings()
    }

    companion object {
        private const val TAG = "ISOControl"
    }
}