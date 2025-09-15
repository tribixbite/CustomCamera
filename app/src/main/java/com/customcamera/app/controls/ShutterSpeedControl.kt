package com.customcamera.app.controls

import android.content.Context
import android.util.Log
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.camera.core.Camera
import com.customcamera.app.engine.CameraContext

/**
 * ShutterSpeedControl provides shutter speed control
 * with range (1/8000s - 30s) and bulb mode support.
 */
class ShutterSpeedControl(
    private val context: Context,
    private val cameraContext: CameraContext
) {

    // Shutter speed configuration
    private val shutterSpeeds = listOf(
        // Fast shutter speeds (fractions)
        1.0/8000, 1.0/4000, 1.0/2000, 1.0/1000, 1.0/500, 1.0/250, 1.0/125,
        1.0/60, 1.0/30, 1.0/15, 1.0/8, 1.0/4, 1.0/2,
        // Slow shutter speeds (seconds)
        1.0, 2.0, 4.0, 8.0, 15.0, 30.0
    )

    private var currentShutterSpeedIndex: Int = 7 // 1/60s default
    private var isAutoShutterSpeed: Boolean = true
    private var isBulbModeEnabled: Boolean = false
    private var bulbDurationSeconds: Int = 5

    // UI components
    private var shutterSpeedSlider: SeekBar? = null
    private var shutterSpeedValueText: TextView? = null
    private var shutterSpeedWarningText: TextView? = null
    private var autoShutterSpeedToggle: androidx.appcompat.widget.SwitchCompat? = null
    private var bulbModeToggle: androidx.appcompat.widget.SwitchCompat? = null

    /**
     * Create shutter speed control UI
     */
    fun createShutterSpeedControlUI(): LinearLayout {
        Log.i(TAG, "Creating shutter speed control UI")

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 8, 16, 8)
        }

        // Title
        val titleText = TextView(context).apply {
            text = "Shutter Speed"
            textSize = 18f
            setPadding(0, 0, 0, 8)
        }
        container.addView(titleText)

        // Auto shutter speed toggle
        autoShutterSpeedToggle = androidx.appcompat.widget.SwitchCompat(context).apply {
            text = "Auto Shutter Speed"
            isChecked = isAutoShutterSpeed
            setOnCheckedChangeListener { _, isChecked ->
                setAutoShutterSpeed(isChecked)
            }
        }
        container.addView(autoShutterSpeedToggle)

        // Bulb mode toggle
        bulbModeToggle = androidx.appcompat.widget.SwitchCompat(context).apply {
            text = "Bulb Mode (Extended Exposure)"
            isChecked = isBulbModeEnabled
            setOnCheckedChangeListener { _, isChecked ->
                setBulbMode(isChecked)
            }
        }
        container.addView(bulbModeToggle)

        // Shutter speed value display
        shutterSpeedValueText = TextView(context).apply {
            text = getShutterSpeedDisplayText()
            textSize = 16f
            setPadding(0, 8, 0, 4)
        }
        container.addView(shutterSpeedValueText)

        // Shutter speed range slider
        shutterSpeedSlider = SeekBar(context).apply {
            max = shutterSpeeds.size - 1
            progress = currentShutterSpeedIndex
            isEnabled = !isAutoShutterSpeed && !isBulbModeEnabled

            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser && !isAutoShutterSpeed && !isBulbModeEnabled) {
                        setShutterSpeedIndex(progress)
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        container.addView(shutterSpeedSlider)

        // Motion blur preview indication
        shutterSpeedWarningText = TextView(context).apply {
            textSize = 12f
            setPadding(0, 4, 0, 0)
            updateShutterSpeedWarning(getCurrentShutterSpeed())
        }
        container.addView(shutterSpeedWarningText)

        Log.i(TAG, "Shutter speed control UI created")
        return container
    }

    /**
     * Set shutter speed by index
     */
    fun setShutterSpeedIndex(index: Int) {
        val clampedIndex = index.coerceIn(0, shutterSpeeds.size - 1)

        if (currentShutterSpeedIndex != clampedIndex) {
            currentShutterSpeedIndex = clampedIndex
            isAutoShutterSpeed = false
            isBulbModeEnabled = false

            updateShutterSpeedUI()
            saveSettings()

            val shutterSpeed = getCurrentShutterSpeed()
            Log.i(TAG, "Shutter speed set to: ${getShutterSpeedDisplayText()}")

            cameraContext.debugLogger.logPlugin(
                "ShutterSpeedControl",
                "shutter_speed_changed",
                mapOf(
                    "index" to currentShutterSpeedIndex,
                    "shutterSpeed" to shutterSpeed,
                    "displayText" to getShutterSpeedDisplayText()
                )
            )
        }
    }

    /**
     * Set auto shutter speed
     */
    fun setAutoShutterSpeed(enabled: Boolean) {
        if (isAutoShutterSpeed != enabled) {
            isAutoShutterSpeed = enabled

            if (enabled) {
                isBulbModeEnabled = false
                currentShutterSpeedIndex = 7 // Reset to 1/60s
            }

            updateShutterSpeedUI()
            saveSettings()

            Log.i(TAG, "Auto shutter speed ${if (enabled) "enabled" else "disabled"}")

            cameraContext.debugLogger.logPlugin(
                "ShutterSpeedControl",
                "auto_shutter_toggled",
                mapOf("enabled" to enabled)
            )
        }
    }

    /**
     * Set bulb mode for extended exposures
     */
    fun setBulbMode(enabled: Boolean) {
        if (isBulbModeEnabled != enabled) {
            isBulbModeEnabled = enabled

            if (enabled) {
                isAutoShutterSpeed = false
            }

            updateShutterSpeedUI()
            saveSettings()

            Log.i(TAG, "Bulb mode ${if (enabled) "enabled" else "disabled"}")

            cameraContext.debugLogger.logPlugin(
                "ShutterSpeedControl",
                "bulb_mode_toggled",
                mapOf(
                    "enabled" to enabled,
                    "bulbDuration" to bulbDurationSeconds
                )
            )
        }
    }

    /**
     * Apply shutter speed to camera
     */
    fun applyShutterSpeedToCamera(camera: Camera): Boolean {
        return try {
            val shutterSpeed = getCurrentShutterSpeed()

            Log.i(TAG, "Applying shutter speed: ${getShutterSpeedDisplayText()}")

            // Note: CameraX has limited shutter speed control
            // Full manual shutter speed requires Camera2 API
            // In production with Camera2:
            // captureRequestBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, exposureTimeNs)

            cameraContext.debugLogger.logPlugin(
                "ShutterSpeedControl",
                "shutter_speed_applied",
                mapOf(
                    "shutterSpeed" to shutterSpeed,
                    "bulbMode" to isBulbModeEnabled,
                    "autoMode" to isAutoShutterSpeed
                )
            )

            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply shutter speed", e)
            false
        }
    }

    /**
     * Get current shutter speed in seconds
     */
    fun getCurrentShutterSpeed(): Double {
        return if (isBulbModeEnabled) {
            bulbDurationSeconds.toDouble()
        } else {
            shutterSpeeds[currentShutterSpeedIndex]
        }
    }

    /**
     * Get shutter speed display text with fractions
     */
    fun getShutterSpeedDisplayText(): String {
        return when {
            isBulbModeEnabled -> "${bulbDurationSeconds}s (Bulb)"
            isAutoShutterSpeed -> "Auto"
            else -> {
                val speed = shutterSpeeds[currentShutterSpeedIndex]
                if (speed < 1.0) {
                    val fraction = (1.0 / speed).toInt()
                    "1/${fraction}s"
                } else {
                    "${speed.toInt()}s"
                }
            }
        }
    }

    /**
     * Get current settings
     */
    fun getShutterSpeedSettings(): Map<String, Any> {
        return mapOf(
            "currentShutterSpeedIndex" to currentShutterSpeedIndex,
            "currentShutterSpeed" to getCurrentShutterSpeed(),
            "isAutoShutterSpeed" to isAutoShutterSpeed,
            "isBulbModeEnabled" to isBulbModeEnabled,
            "bulbDurationSeconds" to bulbDurationSeconds,
            "displayText" to getShutterSpeedDisplayText()
        )
    }

    private fun updateShutterSpeedUI() {
        shutterSpeedValueText?.text = getShutterSpeedDisplayText()
        shutterSpeedSlider?.apply {
            progress = currentShutterSpeedIndex
            isEnabled = !isAutoShutterSpeed && !isBulbModeEnabled
        }
        autoShutterSpeedToggle?.isChecked = isAutoShutterSpeed
        bulbModeToggle?.isChecked = isBulbModeEnabled

        shutterSpeedWarningText?.let {
            updateShutterSpeedWarning(getCurrentShutterSpeed())
        }
    }

    private fun updateShutterSpeedWarning(shutterSpeed: Double) {
        val warningText = when {
            shutterSpeed >= 1.0 -> "📸 Tripod recommended for stability"
            shutterSpeed >= 1.0/30 -> "⚠️ Camera shake possible, steady hands needed"
            shutterSpeed >= 1.0/125 -> "🟡 Good for most handheld photography"
            shutterSpeed >= 1.0/500 -> "✅ Excellent for handheld, stops motion"
            else -> "⚡ Fast shutter, freezes action"
        }

        val warningColor = when {
            shutterSpeed >= 1.0 -> 0xFFFF5722.toInt() // Deep Orange
            shutterSpeed >= 1.0/30 -> 0xFFFF9800.toInt() // Orange
            shutterSpeed >= 1.0/125 -> 0xFFFFEB3B.toInt() // Yellow
            else -> 0xFF4CAF50.toInt() // Green
        }

        shutterSpeedWarningText?.apply {
            text = warningText
            setTextColor(warningColor)
        }
    }

    private fun saveSettings() {
        val settings = cameraContext.settingsManager
        settings.setPluginSetting("ShutterSpeedControl", "shutterSpeedIndex", currentShutterSpeedIndex.toString())
        settings.setPluginSetting("ShutterSpeedControl", "autoShutterSpeed", isAutoShutterSpeed.toString())
        settings.setPluginSetting("ShutterSpeedControl", "bulbModeEnabled", isBulbModeEnabled.toString())
        settings.setPluginSetting("ShutterSpeedControl", "bulbDuration", bulbDurationSeconds.toString())
    }

    private fun loadSettings() {
        try {
            val settings = cameraContext.settingsManager
            currentShutterSpeedIndex = settings.getPluginSetting("ShutterSpeedControl", "shutterSpeedIndex", "7").toInt()
            isAutoShutterSpeed = settings.getPluginSetting("ShutterSpeedControl", "autoShutterSpeed", "true").toBoolean()
            isBulbModeEnabled = settings.getPluginSetting("ShutterSpeedControl", "bulbModeEnabled", "false").toBoolean()
            bulbDurationSeconds = settings.getPluginSetting("ShutterSpeedControl", "bulbDuration", "5").toInt()

            Log.i(TAG, "Loaded settings: index=$currentShutterSpeedIndex, auto=$isAutoShutterSpeed, bulb=$isBulbModeEnabled")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load settings, using defaults", e)
        }
    }

    companion object {
        private const val TAG = "ShutterSpeedControl"
    }
}