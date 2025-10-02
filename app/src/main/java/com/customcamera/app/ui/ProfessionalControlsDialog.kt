package com.customcamera.app.ui

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.customcamera.app.R
import com.customcamera.app.plugins.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Professional camera controls dialog
 * Provides comprehensive manual camera control interface
 */
class ProfessionalControlsDialog(
    private val context: Context,
    private val isoPlugin: AdvancedISOControlPlugin?,
    private val shutterPlugin: ProfessionalShutterControlPlugin?,
    private val aperturePlugin: ManualApertureControlPlugin?,
    private val whiteBalancePlugin: AdvancedWhiteBalancePlugin?,
    private val focusPlugin: ManualFocusControlPlugin?,
    private val bracketingPlugin: ExposureBracketingPlugin?
) : Dialog(context) {

    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    private var controlsContainer: LinearLayout? = null
    private var currentTab = ControlTab.ISO

    // UI Controls
    private var isoSeekBar: SeekBar? = null
    private var isoValueText: TextView? = null
    private var shutterSeekBar: SeekBar? = null
    private var shutterValueText: TextView? = null
    private var apertureSeekBar: SeekBar? = null
    private var apertureValueText: TextView? = null
    private var focusSeekBar: SeekBar? = null
    private var focusValueText: TextView? = null
    private var colorTempSeekBar: SeekBar? = null
    private var colorTempValueText: TextView? = null

    enum class ControlTab {
        ISO, SHUTTER, APERTURE, FOCUS, WHITE_BALANCE, BRACKETING
    }

    init {
        setupDialog()
        createUI()
        loadCurrentValues()
    }

    private fun setupDialog() {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        setCancelable(true)
    }

    private fun createUI() {
        val rootView = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
            setBackgroundColor(ContextCompat.getColor(context, R.color.surface))
        }

        // Title
        val titleText = TextView(context).apply {
            text = "Professional Controls"
            textSize = 20f
            setTextColor(ContextCompat.getColor(context, R.color.on_surface))
            setPadding(0, 0, 0, 24)
        }
        rootView.addView(titleText)

        // Tab buttons
        val tabContainer = createTabButtons()
        rootView.addView(tabContainer)

        // Controls container
        controlsContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 24, 0, 0)
        }
        rootView.addView(controlsContainer)

        // Action buttons
        val actionContainer = createActionButtons()
        rootView.addView(actionContainer)

        setContentView(rootView)
        showTab(ControlTab.ISO)
    }

    private fun createTabButtons(): LinearLayout {
        val tabContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val tabs = listOf(
            ControlTab.ISO to "ISO",
            ControlTab.SHUTTER to "Shutter",
            ControlTab.APERTURE to "Aperture",
            ControlTab.FOCUS to "Focus",
            ControlTab.WHITE_BALANCE to "WB",
            ControlTab.BRACKETING to "HDR"
        )

        tabs.forEach { (tab, label) ->
            val button = Button(context).apply {
                text = label
                textSize = 12f
                setPadding(16, 8, 16, 8)
                setOnClickListener { showTab(tab) }
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                ).apply {
                    setMargins(4, 0, 4, 0)
                }
            }
            tabContainer.addView(button)
        }

        return tabContainer
    }

    private fun showTab(tab: ControlTab) {
        currentTab = tab
        controlsContainer?.removeAllViews()

        when (tab) {
            ControlTab.ISO -> createISOControls()
            ControlTab.SHUTTER -> createShutterControls()
            ControlTab.APERTURE -> createApertureControls()
            ControlTab.FOCUS -> createFocusControls()
            ControlTab.WHITE_BALANCE -> createWhiteBalanceControls()
            ControlTab.BRACKETING -> createBracketingControls()
        }
    }

    private fun createISOControls() {
        isoPlugin?.let { plugin ->
            val container = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
            }

            // ISO Slider
            val isoControl = createSliderControl(
                "ISO Sensitivity",
                plugin.getCurrentISO(),
                plugin.getISORange().lower.toFloat(),
                plugin.getISORange().upper.toFloat()
            ) { value ->
                plugin.setISO(value.toInt())
                updateISODisplay(value.toInt())
            }
            isoSeekBar = isoControl.second
            isoValueText = isoControl.third
            container.addView(isoControl.first)

            // ISO Presets
            val presetsContainer = createPresetsContainer(
                plugin.getAvailablePresets().keys.toList()
            ) { preset ->
                plugin.applyPreset(preset)
                updateISODisplay(plugin.getCurrentISO())
                isoSeekBar?.progress = plugin.getCurrentISO() - plugin.getISORange().lower
            }
            container.addView(presetsContainer)

            // Noise level indicator
            val noiseText = TextView(context).apply {
                text = "Noise Level: ${String.format("%.1f", plugin.calculateNoiseLevel(plugin.getCurrentISO()))}"
                textSize = 12f
                setTextColor(ContextCompat.getColor(context, R.color.on_surface_variant))
                setPadding(0, 8, 0, 0)
            }
            container.addView(noiseText)

            controlsContainer?.addView(container)
        }
    }

    private fun createShutterControls() {
        shutterPlugin?.let { plugin ->
            val container = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
            }

            // Shutter Speed Slider (using index for predefined speeds)
            val shutterSpeeds = plugin.getCommonShutterSpeeds()
            val currentIndex = shutterSpeeds.indexOfFirst { it == plugin.getCurrentShutterSpeed() }
                .takeIf { it >= 0 } ?: 0

            val shutterControl = createSliderControl(
                "Shutter Speed",
                currentIndex.toFloat(),
                0f,
                (shutterSpeeds.size - 1).toFloat()
            ) { value ->
                val speed = shutterSpeeds[value.toInt()]
                plugin.setShutterSpeed(speed)
                updateShutterDisplay(speed)
            }
            shutterSeekBar = shutterControl.second
            shutterValueText = shutterControl.third
            container.addView(shutterControl.first)

            // Shutter Presets
            val presetsContainer = createPresetsContainer(
                plugin.getAvailablePresets().keys.toList()
            ) { preset ->
                plugin.applyPreset(preset)
                updateShutterDisplay(plugin.getCurrentShutterSpeed())
                val newIndex = shutterSpeeds.indexOfFirst { it == plugin.getCurrentShutterSpeed() }
                    .takeIf { it >= 0 } ?: 0
                shutterSeekBar?.progress = newIndex
            }
            container.addView(presetsContainer)

            // Motion blur indicator
            val motionText = TextView(context).apply {
                text = "Motion Blur Risk: ${String.format("%.1f%%", plugin.calculateMotionBlurRisk(plugin.getCurrentShutterSpeed()) * 100)}"
                textSize = 12f
                setTextColor(ContextCompat.getColor(context, R.color.on_surface_variant))
                setPadding(0, 8, 0, 0)
            }
            container.addView(motionText)

            controlsContainer?.addView(container)
        }
    }

    private fun createApertureControls() {
        aperturePlugin?.let { plugin ->
            if (!plugin.hasVariableApertureSupport()) {
                val unavailableText = TextView(context).apply {
                    text = "Variable aperture not supported on this device"
                    textSize = 14f
                    setTextColor(ContextCompat.getColor(context, R.color.on_surface_variant))
                    setPadding(0, 16, 0, 16)
                }
                controlsContainer?.addView(unavailableText)
                return
            }

            val container = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
            }

            // Aperture Slider
            val apertureControl = createSliderControl(
                "Aperture (f-stop)",
                plugin.getCurrentAperture(),
                plugin.getApertureRange().lower,
                plugin.getApertureRange().upper
            ) { value ->
                plugin.setAperture(value)
                updateApertureDisplay(value)
            }
            apertureSeekBar = apertureControl.second
            apertureValueText = apertureControl.third
            container.addView(apertureControl.first)

            // Aperture Presets
            val presetsContainer = createPresetsContainer(
                plugin.getAvailablePresets().keys.toList()
            ) { preset ->
                plugin.applyPreset(preset)
                updateApertureDisplay(plugin.getCurrentAperture())
                val range = plugin.getApertureRange()
                val progress = ((plugin.getCurrentAperture() - range.lower) / (range.upper - range.lower) * 100).toInt()
                apertureSeekBar?.progress = progress
            }
            container.addView(presetsContainer)

            // Depth of field info
            val dofInfo = plugin.calculateDepthOfField(2.0f) // Assume 2m focus distance
            val dofText = TextView(context).apply {
                text = "DoF: ${String.format("%.1fm", dofInfo.totalDepth)} | Hyperfocal: ${String.format("%.1fm", dofInfo.hyperfocalDistance)}"
                textSize = 12f
                setTextColor(ContextCompat.getColor(context, R.color.on_surface_variant))
                setPadding(0, 8, 0, 0)
            }
            container.addView(dofText)

            controlsContainer?.addView(container)
        }
    }

    private fun createFocusControls() {
        focusPlugin?.let { plugin ->
            val container = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
            }

            // Auto/Manual Toggle
            val autoFocusButton = Button(context).apply {
                text = if (plugin.getCurrentSettings().isAutoFocusEnabled) "Auto Focus: ON" else "Manual Focus: ON"
                setOnClickListener {
                    val newState = !plugin.getCurrentSettings().isAutoFocusEnabled
                    plugin.setAutoFocusEnabled(newState)
                    text = if (newState) "Auto Focus: ON" else "Manual Focus: ON"
                    focusSeekBar?.isEnabled = !newState
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 0, 16)
                }
            }
            container.addView(autoFocusButton)

            if (plugin.getCurrentSettings().hasManualFocus) {
                // Focus Distance Slider
                val focusControl = createSliderControl(
                    "Focus Distance",
                    plugin.getCurrentSettings().focusDistance,
                    0f,
                    10f
                ) { value ->
                    plugin.setFocusDistance(value)
                    updateFocusDisplay(value)
                }
                focusSeekBar = focusControl.second
                focusValueText = focusControl.third
                focusSeekBar?.isEnabled = !plugin.getCurrentSettings().isAutoFocusEnabled
                container.addView(focusControl.first)

                // Focus Presets
                val presetsContainer = createPresetsContainer(
                    plugin.getAvailablePresets().keys.toList()
                ) { preset ->
                    plugin.applyPreset(preset)
                    updateFocusDisplay(plugin.getCurrentSettings().focusDistance)
                    focusSeekBar?.progress = (plugin.getCurrentSettings().focusDistance * 10).toInt()
                }
                container.addView(presetsContainer)

                // Hyperfocal distance
                val hyperfocalText = TextView(context).apply {
                    text = "Hyperfocal: ${String.format("%.1fm", plugin.calculateHyperfocalDistance())}"
                    textSize = 12f
                    setTextColor(ContextCompat.getColor(context, R.color.on_surface_variant))
                    setPadding(0, 8, 0, 0)
                }
                container.addView(hyperfocalText)
            } else {
                val unavailableText = TextView(context).apply {
                    text = "Manual focus not supported on this device"
                    textSize = 14f
                    setTextColor(ContextCompat.getColor(context, R.color.on_surface_variant))
                    setPadding(0, 16, 0, 16)
                }
                container.addView(unavailableText)
            }

            controlsContainer?.addView(container)
        }
    }

    private fun createWhiteBalanceControls() {
        whiteBalancePlugin?.let { plugin ->
            val container = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
            }

            // Color Temperature Slider
            val colorTempControl = createSliderControl(
                "Color Temperature (K)",
                plugin.getCurrentSettings().colorTemperature.toFloat(),
                2000f,
                8000f
            ) { value ->
                plugin.setColorTemperature(value.toInt())
                updateColorTempDisplay(value.toInt())
            }
            colorTempSeekBar = colorTempControl.second
            colorTempValueText = colorTempControl.third
            container.addView(colorTempControl.first)

            // WB Presets
            val presetsContainer = createPresetsContainer(
                plugin.getAvailablePresets().keys.toList()
            ) { preset ->
                plugin.applyPreset(preset)
                val newTemp = plugin.getCurrentSettings().colorTemperature
                updateColorTempDisplay(newTemp)
                colorTempSeekBar?.progress = ((newTemp - 2000) / 60).toInt()
            }
            container.addView(presetsContainer)

            // Current description
            val descText = TextView(context).apply {
                text = plugin.getColorTemperatureDescription(plugin.getCurrentSettings().colorTemperature)
                textSize = 12f
                setTextColor(ContextCompat.getColor(context, R.color.on_surface_variant))
                setPadding(0, 8, 0, 0)
            }
            container.addView(descText)

            controlsContainer?.addView(container)
        }
    }

    private fun createBracketingControls() {
        bracketingPlugin?.let { plugin ->
            val container = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
            }

            // Bracketing Mode Selection
            val modeText = TextView(context).apply {
                text = "Bracketing Mode: ${plugin.getCurrentSettings().mode.name}"
                textSize = 14f
                setTextColor(ContextCompat.getColor(context, R.color.on_surface))
                setPadding(0, 0, 0, 8)
            }
            container.addView(modeText)

            // Mode buttons
            val modesContainer = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
            }

            ExposureBracketingPlugin.BracketingMode.values().forEach { mode ->
                val button = Button(context).apply {
                    text = "${mode.shots}"
                    textSize = 12f
                    setPadding(8, 4, 8, 4)
                    setOnClickListener {
                        // Apply mode logic would go here
                        modeText.text = "Bracketing Mode: ${mode.name}"
                    }
                    layoutParams = LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                    ).apply {
                        setMargins(2, 0, 2, 0)
                    }
                }
                modesContainer.addView(button)
            }
            container.addView(modesContainer)

            // Exposure Steps Slider
            val stepsControl = createSliderControl(
                "Exposure Steps (EV)",
                plugin.getCurrentSettings().exposureSteps,
                0.3f,
                2.0f
            ) { value ->
                // Apply exposure steps logic would go here
            }
            container.addView(stepsControl.first)

            // Bracketing Presets
            val presetsContainer = createPresetsContainer(
                plugin.getAvailablePresets().keys.toList()
            ) { preset ->
                plugin.applyPreset(preset)
                modeText.text = "Bracketing Mode: ${plugin.getCurrentSettings().mode.name}"
            }
            container.addView(presetsContainer)

            controlsContainer?.addView(container)
        }
    }

    private fun createSliderControl(
        label: String,
        currentValue: Float,
        minValue: Float,
        maxValue: Float,
        onValueChanged: (Float) -> Unit
    ): Triple<LinearLayout, SeekBar, TextView> {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 8, 0, 16)
        }

        val labelText = TextView(context).apply {
            text = label
            textSize = 14f
            setTextColor(ContextCompat.getColor(context, R.color.on_surface))
            setPadding(0, 0, 0, 8)
        }
        container.addView(labelText)

        val valueText = TextView(context).apply {
            text = String.format("%.1f", currentValue)
            textSize = 12f
            setTextColor(ContextCompat.getColor(context, R.color.primary))
            setPadding(0, 0, 0, 4)
        }
        container.addView(valueText)

        val seekBar = SeekBar(context).apply {
            max = 100
            progress = (((currentValue - minValue) / (maxValue - minValue)) * 100).toInt()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        val value = minValue + (progress / 100f) * (maxValue - minValue)
                        valueText.text = String.format("%.1f", value)
                        onValueChanged(value)
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        container.addView(seekBar)

        return Triple(container, seekBar, valueText)
    }

    private fun createPresetsContainer(presets: List<String>, onPresetSelected: (String) -> Unit): LinearLayout {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 8, 0, 0)
        }

        presets.take(4).forEach { preset ->
            val button = Button(context).apply {
                text = preset.capitalize()
                textSize = 10f
                setPadding(8, 4, 8, 4)
                setOnClickListener { onPresetSelected(preset) }
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                ).apply {
                    setMargins(2, 0, 2, 0)
                }
            }
            container.addView(button)
        }

        return container
    }

    private fun createActionButtons(): LinearLayout {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 24, 0, 0)
        }

        val resetButton = Button(context).apply {
            text = "Reset"
            setOnClickListener { resetCurrentTab() }
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                setMargins(0, 0, 8, 0)
            }
        }
        container.addView(resetButton)

        val closeButton = Button(context).apply {
            text = "Close"
            setOnClickListener { dismiss() }
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                setMargins(8, 0, 0, 0)
            }
        }
        container.addView(closeButton)

        return container
    }

    private fun loadCurrentValues() {
        coroutineScope.launch {
            // Load current values from plugins
            isoPlugin?.let { updateISODisplay(it.getCurrentISO()) }
            shutterPlugin?.let { updateShutterDisplay(it.getCurrentShutterSpeed()) }
            aperturePlugin?.let { updateApertureDisplay(it.getCurrentAperture()) }
            focusPlugin?.let { updateFocusDisplay(it.getCurrentSettings().focusDistance) }
            whiteBalancePlugin?.let { updateColorTempDisplay(it.getCurrentSettings().colorTemperature) }
        }
    }

    private fun updateISODisplay(iso: Int) {
        isoValueText?.text = iso.toString()
    }

    private fun updateShutterDisplay(shutterSpeedNs: Long) {
        shutterValueText?.text = shutterPlugin?.formatShutterSpeed(shutterSpeedNs) ?: "1/60s"
    }

    private fun updateApertureDisplay(aperture: Float) {
        apertureValueText?.text = aperturePlugin?.formatAperture(aperture) ?: "f/2.8"
    }

    private fun updateFocusDisplay(focusDistance: Float) {
        focusValueText?.text = focusPlugin?.formatFocusDistance(focusDistance) ?: "∞"
    }

    private fun updateColorTempDisplay(colorTemp: Int) {
        colorTempValueText?.text = whiteBalancePlugin?.formatColorTemperature(colorTemp) ?: "5500K"
    }

    private fun resetCurrentTab() {
        when (currentTab) {
            ControlTab.ISO -> {
                isoPlugin?.setISO(800)
                updateISODisplay(800)
                isoSeekBar?.progress = 50
            }
            ControlTab.SHUTTER -> {
                shutterPlugin?.setShutterSpeed(16666667L) // 1/60s
                updateShutterDisplay(16666667L)
                shutterSeekBar?.progress = 50
            }
            ControlTab.APERTURE -> {
                aperturePlugin?.setAperture(2.8f)
                updateApertureDisplay(2.8f)
                apertureSeekBar?.progress = 50
            }
            ControlTab.FOCUS -> {
                focusPlugin?.setAutoFocusEnabled(true)
                focusSeekBar?.progress = 0
            }
            ControlTab.WHITE_BALANCE -> {
                whiteBalancePlugin?.setColorTemperature(5500)
                updateColorTempDisplay(5500)
                colorTempSeekBar?.progress = 58 // (5500-2000)/60
            }
            ControlTab.BRACKETING -> {
                bracketingPlugin?.applyPreset("standard")
            }
        }
    }
}