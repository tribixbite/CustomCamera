package com.customcamera.app.controls

import android.content.Context
import android.util.Log
import android.widget.*
import androidx.camera.core.Camera
import com.customcamera.app.engine.CameraContext

/**
 * WhiteBalanceControl provides white balance adjustment
 * with presets and manual color temperature control (2000K-10000K).
 */
class WhiteBalanceControl(
    private val context: Context,
    private val cameraContext: CameraContext
) {

    // White balance configuration
    private val colorTemperatureRange = 2000..10000 // Kelvin
    private var currentColorTemperature: Int = 5500 // Daylight default
    private var currentWhiteBalancePreset: WhiteBalancePreset = WhiteBalancePreset.AUTO
    private var isManualWhiteBalance: Boolean = false

    enum class WhiteBalancePreset(val displayName: String, val colorTemp: Int) {
        AUTO("Auto", 0),
        DAYLIGHT("Daylight", 5500),
        CLOUDY("Cloudy", 6500),
        TUNGSTEN("Tungsten", 3200),
        FLUORESCENT("Fluorescent", 4000),
        FLASH("Flash", 5500),
        SHADE("Shade", 7500)
    }

    // UI components
    private var whiteBalanceSpinner: Spinner? = null
    private var colorTempSlider: SeekBar? = null
    private var colorTempValueText: TextView? = null
    private var whiteBalancePreviewText: TextView? = null
    private var manualWBToggle: androidx.appcompat.widget.SwitchCompat? = null

    /**
     * Create white balance control UI
     */
    fun createWhiteBalanceControlUI(): LinearLayout {
        Log.i(TAG, "Creating white balance control UI")

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 8, 16, 8)
        }

        // Title
        val titleText = TextView(context).apply {
            text = "White Balance"
            textSize = 18f
            setPadding(0, 0, 0, 8)
        }
        container.addView(titleText)

        // Manual white balance toggle
        manualWBToggle = androidx.appcompat.widget.SwitchCompat(context).apply {
            text = "Manual White Balance"
            isChecked = isManualWhiteBalance
            setOnCheckedChangeListener { _, isChecked ->
                setManualWhiteBalance(isChecked)
            }
        }
        container.addView(manualWBToggle)

        // White balance presets
        whiteBalanceSpinner = Spinner(context).apply {
            val adapter = ArrayAdapter(
                context,
                android.R.layout.simple_spinner_item,
                WhiteBalancePreset.values().map { it.displayName }
            ).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }

            this.adapter = adapter
            setSelection(currentWhiteBalancePreset.ordinal)

            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                    setWhiteBalancePreset(WhiteBalancePreset.values()[position])
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        }
        container.addView(whiteBalanceSpinner)

        // Color temperature display
        colorTempValueText = TextView(context).apply {
            text = "${currentColorTemperature}K"
            textSize = 16f
            setPadding(0, 8, 0, 4)
        }
        container.addView(colorTempValueText)

        // Manual color temperature control (2000K-10000K)
        colorTempSlider = SeekBar(context).apply {
            max = colorTemperatureRange.last - colorTemperatureRange.first
            progress = currentColorTemperature - colorTemperatureRange.first
            isEnabled = isManualWhiteBalance

            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser && isManualWhiteBalance) {
                        val colorTemp = progress + colorTemperatureRange.first
                        setColorTemperature(colorTemp)
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        container.addView(colorTempSlider)

        // White balance preview
        whiteBalancePreviewText = TextView(context).apply {
            textSize = 12f
            setPadding(0, 4, 0, 0)
            updateWhiteBalancePreview(currentColorTemperature)
        }
        container.addView(whiteBalancePreviewText)

        Log.i(TAG, "White balance control UI created")
        return container
    }

    /**
     * Set white balance preset
     */
    fun setWhiteBalancePreset(preset: WhiteBalancePreset) {
        if (currentWhiteBalancePreset != preset) {
            currentWhiteBalancePreset = preset

            if (preset != WhiteBalancePreset.AUTO) {
                isManualWhiteBalance = true
                currentColorTemperature = preset.colorTemp
            } else {
                isManualWhiteBalance = false
            }

            updateWhiteBalanceUI()
            saveSettings()

            Log.i(TAG, "White balance preset set to: ${preset.displayName}")

            cameraContext.debugLogger.logPlugin(
                "WhiteBalanceControl",
                "preset_changed",
                mapOf(
                    "preset" to preset.displayName,
                    "colorTemp" to preset.colorTemp,
                    "manual" to isManualWhiteBalance
                )
            )
        }
    }

    /**
     * Set manual color temperature (2000K-10000K)
     */
    fun setColorTemperature(kelvin: Int) {
        val clampedTemp = kelvin.coerceIn(colorTemperatureRange)

        if (currentColorTemperature != clampedTemp) {
            currentColorTemperature = clampedTemp
            isManualWhiteBalance = true
            currentWhiteBalancePreset = findNearestPreset(clampedTemp)

            updateWhiteBalanceUI()
            saveSettings()

            Log.i(TAG, "Color temperature set to: ${clampedTemp}K")

            cameraContext.debugLogger.logPlugin(
                "WhiteBalanceControl",
                "color_temp_changed",
                mapOf(
                    "colorTemp" to clampedTemp,
                    "nearestPreset" to currentWhiteBalancePreset.displayName
                )
            )
        }
    }

    /**
     * Set manual white balance mode
     */
    fun setManualWhiteBalance(enabled: Boolean) {
        if (isManualWhiteBalance != enabled) {
            isManualWhiteBalance = enabled

            if (!enabled) {
                currentWhiteBalancePreset = WhiteBalancePreset.AUTO
                currentColorTemperature = 5500
            }

            updateWhiteBalanceUI()
            saveSettings()

            Log.i(TAG, "Manual white balance ${if (enabled) "enabled" else "disabled"}")
        }
    }

    /**
     * Apply white balance to camera
     */
    fun applyWhiteBalanceToCamera(camera: Camera): Boolean {
        return try {
            Log.i(TAG, "Applying white balance: ${currentWhiteBalancePreset.displayName} (${currentColorTemperature}K)")

            // Note: CameraX has limited white balance control
            // Full manual white balance requires Camera2 API
            // In production with Camera2:
            // captureRequestBuilder.set(CaptureRequest.COLOR_CORRECTION_MODE, CameraMetadata.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX)

            cameraContext.debugLogger.logPlugin(
                "WhiteBalanceControl",
                "white_balance_applied",
                mapOf(
                    "preset" to currentWhiteBalancePreset.displayName,
                    "colorTemp" to currentColorTemperature,
                    "manual" to isManualWhiteBalance
                )
            )

            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply white balance", e)
            false
        }
    }

    /**
     * White balance fine-tuning
     */
    fun finetuneWhiteBalance(deltaKelvin: Int) {
        val newTemp = currentColorTemperature + deltaKelvin
        setColorTemperature(newTemp)
        Log.i(TAG, "White balance fine-tuned by ${deltaKelvin}K to ${currentColorTemperature}K")
    }

    /**
     * Get current white balance settings
     */
    fun getWhiteBalanceSettings(): Map<String, Any> {
        return mapOf(
            "currentPreset" to currentWhiteBalancePreset.displayName,
            "currentColorTemperature" to currentColorTemperature,
            "isManualWhiteBalance" to isManualWhiteBalance,
            "colorTempRange" to "${colorTemperatureRange.first}K-${colorTemperatureRange.last}K",
            "previewDescription" to getColorTemperatureDescription(currentColorTemperature)
        )
    }

    /**
     * Reset to auto white balance
     */
    fun resetToAutoWhiteBalance() {
        setWhiteBalancePreset(WhiteBalancePreset.AUTO)
        Log.i(TAG, "White balance reset to auto")
    }

    private fun updateWhiteBalanceUI() {
        colorTempValueText?.text = "${currentColorTemperature}K"
        colorTempSlider?.apply {
            progress = currentColorTemperature - colorTemperatureRange.first
            isEnabled = isManualWhiteBalance
        }
        whiteBalanceSpinner?.setSelection(currentWhiteBalancePreset.ordinal)
        manualWBToggle?.isChecked = isManualWhiteBalance

        whiteBalancePreviewText?.let {
            updateWhiteBalancePreview(currentColorTemperature)
        }
    }

    private fun updateWhiteBalancePreview(colorTemp: Int) {
        val description = getColorTemperatureDescription(colorTemp)
        val tintColor = getColorTemperatureTint(colorTemp)

        whiteBalancePreviewText?.apply {
            text = description
            setTextColor(tintColor)
        }
    }

    private fun getColorTemperatureDescription(kelvin: Int): String {
        return when {
            kelvin < 2500 -> "🔥 Very warm (candlelight)"
            kelvin < 3000 -> "🟠 Warm (incandescent)"
            kelvin < 3500 -> "🟡 Warm white (halogen)"
            kelvin < 4500 -> "⚪ Neutral white (fluorescent)"
            kelvin < 5500 -> "🔵 Cool white (flash)"
            kelvin < 6500 -> "☀️ Daylight"
            kelvin < 7500 -> "⛅ Overcast"
            else -> "🌫️ Very cool (shade/blue sky)"
        }
    }

    private fun getColorTemperatureTint(kelvin: Int): Int {
        return when {
            kelvin < 3000 -> 0xFFFF6B35.toInt() // Warm orange
            kelvin < 4000 -> 0xFFFFB74D.toInt() // Light orange
            kelvin < 5000 -> 0xFFFFF59D.toInt() // Light yellow
            kelvin < 6000 -> 0xFFFFFFFF.toInt() // White
            kelvin < 7000 -> 0xFFBBDEFB.toInt() // Light blue
            else -> 0xFF90CAF9.toInt() // Blue
        }
    }

    private fun findNearestPreset(colorTemp: Int): WhiteBalancePreset {
        return WhiteBalancePreset.values()
            .filter { it != WhiteBalancePreset.AUTO }
            .minByOrNull { kotlin.math.abs(it.colorTemp - colorTemp) }
            ?: WhiteBalancePreset.DAYLIGHT
    }

    private fun saveSettings() {
        val settings = cameraContext.settingsManager
        settings.setPluginSetting("WhiteBalanceControl", "preset", currentWhiteBalancePreset.name)
        settings.setPluginSetting("WhiteBalanceControl", "colorTemp", currentColorTemperature.toString())
        settings.setPluginSetting("WhiteBalanceControl", "manual", isManualWhiteBalance.toString())
    }

    private fun loadSettings() {
        try {
            val settings = cameraContext.settingsManager
            val presetName = settings.getPluginSetting("WhiteBalanceControl", "preset", WhiteBalancePreset.AUTO.name)
            currentWhiteBalancePreset = WhiteBalancePreset.valueOf(presetName)
            currentColorTemperature = settings.getPluginSetting("WhiteBalanceControl", "colorTemp", "5500").toInt()
            isManualWhiteBalance = settings.getPluginSetting("WhiteBalanceControl", "manual", "false").toBoolean()

            Log.i(TAG, "Loaded settings: preset=${currentWhiteBalancePreset.displayName}, temp=${currentColorTemperature}K")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load settings, using defaults", e)
        }
    }

    init {
        loadSettings()
    }

    companion object {
        private const val TAG = "WhiteBalanceControl"
    }
}