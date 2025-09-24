package com.customcamera.app.manual

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.Log
import android.util.Range
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.customcamera.app.R
import com.customcamera.app.engine.DebugLogger
import kotlinx.coroutines.launch
import kotlin.math.*

/**
 * Professional Manual Controls UI
 *
 * Provides a comprehensive manual controls interface including:
 * - ISO control slider with presets
 * - Shutter speed wheel with common values
 * - Manual focus distance slider
 * - White balance controls
 * - Exposure compensation wheel
 * - Professional visual aids toggles
 *
 * Designed to replicate professional camera manual controls interface.
 */
class ManualControlsUI @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    companion object {
        private const val TAG = "ManualControlsUI"

        // Common ISO values
        private val ISO_PRESETS = listOf(100, 200, 400, 800, 1600, 3200, 6400)

        // Common shutter speed values (in nanoseconds)
        private val SHUTTER_PRESETS = listOf(
            1000000000L,    // 1s
            500000000L,     // 1/2s
            250000000L,     // 1/4s
            125000000L,     // 1/8s
            62500000L,      // 1/16s
            33333333L,      // 1/30s
            16666667L,      // 1/60s
            8333333L,       // 1/120s
            4166667L,       // 1/240s
            2083333L,       // 1/480s
            1041667L        // 1/960s
        )

        // White balance presets
        private val WHITE_BALANCE_PRESETS = mapOf(
            "Auto" to 0,
            "Tungsten" to 3200,
            "Fluorescent" to 4000,
            "Daylight" to 5500,
            "Flash" to 5500,
            "Cloudy" to 6000,
            "Shade" to 7000
        )
    }

    // UI Components
    private lateinit var manualModeSwitch: Switch
    private lateinit var controlsContainer: LinearLayout

    // ISO Controls
    private lateinit var isoContainer: LinearLayout
    private lateinit var isoLabel: TextView
    private lateinit var isoSeekBar: SeekBar
    private lateinit var isoValue: TextView
    private lateinit var isoAutoButton: Button

    // Shutter Speed Controls
    private lateinit var shutterContainer: LinearLayout
    private lateinit var shutterLabel: TextView
    private lateinit var shutterSeekBar: SeekBar
    private lateinit var shutterValue: TextView
    private lateinit var shutterAutoButton: Button

    // Focus Controls
    private lateinit var focusContainer: LinearLayout
    private lateinit var focusLabel: TextView
    private lateinit var focusSeekBar: SeekBar
    private lateinit var focusValue: TextView
    private lateinit var focusAutoButton: Button

    // White Balance Controls
    private lateinit var wbContainer: LinearLayout
    private lateinit var wbLabel: TextView
    private lateinit var wbSpinner: Spinner
    private lateinit var wbCustomSeekBar: SeekBar
    private lateinit var wbCustomValue: TextView

    // Exposure Compensation
    private lateinit var exposureContainer: LinearLayout
    private lateinit var exposureLabel: TextView
    private lateinit var exposureSeekBar: SeekBar
    private lateinit var exposureValue: TextView

    // Visual Aids Controls
    private lateinit var visualAidsContainer: LinearLayout
    private lateinit var histogramSwitch: Switch
    private lateinit var zebraSwitch: Switch
    private lateinit var focusPeakingSwitch: Switch
    private lateinit var exposureMeterSwitch: Switch
    private lateinit var overlaySwitch: Switch

    // Manual Controls Manager
    private var manualControlsManager: ManualControlsManager? = null
    private var debugLogger: DebugLogger? = null
    private var lifecycleOwner: LifecycleOwner? = null

    // Current ranges from camera
    private var isoRange = Range(100, 6400)
    private var shutterRange = Range(1000000000L, 33333333L)
    private var focusRange = Range(0f, Float.POSITIVE_INFINITY)

    init {
        initializeUI()
    }

    /**
     * Initialize the manual controls manager
     */
    fun initialize(
        manualControlsManager: ManualControlsManager,
        debugLogger: DebugLogger,
        lifecycleOwner: LifecycleOwner
    ) {
        this.manualControlsManager = manualControlsManager
        this.debugLogger = debugLogger
        this.lifecycleOwner = lifecycleOwner

        setupControlsCallbacks()
        updateUIFromState(manualControlsManager.getCurrentState())

        Log.i(TAG, "Manual controls UI initialized")
    }

    /**
     * Show or hide the manual controls panel
     */
    fun setVisible(visible: Boolean) {
        visibility = if (visible) View.VISIBLE else View.GONE

        debugLogger?.logInfo(
            "Manual controls UI visibility changed",
            mapOf("visible" to visible.toString()),
            "ManualControlsUI"
        )
    }

    /**
     * Update UI from current manual controls state
     */
    fun updateFromState(state: ManualControlsState) {
        lifecycleOwner?.lifecycleScope?.launch {
            updateUIFromState(state)
        }
    }

    // Private UI initialization methods

    private fun initializeUI() {
        // Set background and padding
        setBackgroundColor(0xE0000000.toInt())
        setPadding(16, 16, 16, 16)

        createManualModeToggle()
        createControlsContainer()
        createISOControls()
        createShutterControls()
        createFocusControls()
        createWhiteBalanceControls()
        createExposureControls()
        createVisualAidsControls()

        layoutComponents()
    }

    private fun createManualModeToggle() {
        manualModeSwitch = Switch(context).apply {
            text = "Manual Mode"
            textSize = 16f
            setTextColor(Color.WHITE)
            id = View.generateViewId()
            setOnCheckedChangeListener { _, isChecked ->
                manualControlsManager?.setManualModeEnabled(isChecked)
                controlsContainer.visibility = if (isChecked) View.VISIBLE else View.GONE
            }
        }
        addView(manualModeSwitch)
    }

    private fun createControlsContainer() {
        controlsContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            id = View.generateViewId()
            visibility = View.GONE // Initially hidden until manual mode is enabled
        }
        addView(controlsContainer)
    }

    private fun createISOControls() {
        isoContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 8, 0, 8)
        }

        isoLabel = TextView(context).apply {
            text = "ISO Sensitivity"
            textSize = 14f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
        }

        val isoControlsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        isoSeekBar = SeekBar(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        val iso = isoRange.lower + progress * (isoRange.upper - isoRange.lower) / 100
                        manualControlsManager?.setManualIso(iso)
                        updateIsoValueDisplay(iso)
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }

        isoValue = TextView(context).apply {
            text = "AUTO"
            textSize = 12f
            setTextColor(Color.YELLOW)
            setPadding(8, 0, 8, 0)
            minWidth = 80
            gravity = Gravity.CENTER
        }

        isoAutoButton = Button(context).apply {
            text = "AUTO"
            textSize = 10f
            setPadding(8, 4, 8, 4)
            setOnClickListener {
                manualControlsManager?.setManualIso(null)
                updateIsoValueDisplay(null)
            }
        }

        isoControlsRow.addView(isoSeekBar)
        isoControlsRow.addView(isoValue)
        isoControlsRow.addView(isoAutoButton)

        isoContainer.addView(isoLabel)
        isoContainer.addView(isoControlsRow)
        controlsContainer.addView(isoContainer)
    }

    private fun createShutterControls() {
        shutterContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 8, 0, 8)
        }

        shutterLabel = TextView(context).apply {
            text = "Shutter Speed"
            textSize = 14f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
        }

        val shutterControlsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        shutterSeekBar = SeekBar(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            max = SHUTTER_PRESETS.size - 1
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        val shutterSpeed = SHUTTER_PRESETS[progress]
                        manualControlsManager?.setManualShutterSpeed(shutterSpeed)
                        updateShutterValueDisplay(shutterSpeed)
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }

        shutterValue = TextView(context).apply {
            text = "AUTO"
            textSize = 12f
            setTextColor(Color.YELLOW)
            setPadding(8, 0, 8, 0)
            minWidth = 100
            gravity = Gravity.CENTER
        }

        shutterAutoButton = Button(context).apply {
            text = "AUTO"
            textSize = 10f
            setPadding(8, 4, 8, 4)
            setOnClickListener {
                manualControlsManager?.setManualShutterSpeed(null)
                updateShutterValueDisplay(null)
            }
        }

        shutterControlsRow.addView(shutterSeekBar)
        shutterControlsRow.addView(shutterValue)
        shutterControlsRow.addView(shutterAutoButton)

        shutterContainer.addView(shutterLabel)
        shutterContainer.addView(shutterControlsRow)
        controlsContainer.addView(shutterContainer)
    }

    private fun createFocusControls() {
        focusContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 8, 0, 8)
        }

        focusLabel = TextView(context).apply {
            text = "Manual Focus"
            textSize = 14f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
        }

        val focusControlsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        focusSeekBar = SeekBar(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        val focusDistance = progress / 100f * focusRange.upper
                        manualControlsManager?.setManualFocusDistance(focusDistance)
                        updateFocusValueDisplay(focusDistance)
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }

        focusValue = TextView(context).apply {
            text = "AUTO"
            textSize = 12f
            setTextColor(Color.YELLOW)
            setPadding(8, 0, 8, 0)
            minWidth = 80
            gravity = Gravity.CENTER
        }

        focusAutoButton = Button(context).apply {
            text = "AUTO"
            textSize = 10f
            setPadding(8, 4, 8, 4)
            setOnClickListener {
                manualControlsManager?.setManualFocusDistance(null)
                updateFocusValueDisplay(null)
            }
        }

        focusControlsRow.addView(focusSeekBar)
        focusControlsRow.addView(focusValue)
        focusControlsRow.addView(focusAutoButton)

        focusContainer.addView(focusLabel)
        focusContainer.addView(focusControlsRow)
        controlsContainer.addView(focusContainer)
    }

    private fun createWhiteBalanceControls() {
        wbContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 8, 0, 8)
        }

        wbLabel = TextView(context).apply {
            text = "White Balance"
            textSize = 14f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
        }

        // Spinner for presets
        wbSpinner = Spinner(context).apply {
            val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, WHITE_BALANCE_PRESETS.keys.toList())
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            this.adapter = adapter
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    val selectedPreset = WHITE_BALANCE_PRESETS.keys.toList()[position]
                    val colorTemp = WHITE_BALANCE_PRESETS[selectedPreset]
                    manualControlsManager?.setManualWhiteBalance(colorTemp)

                    // Show/hide custom controls based on selection
                    val isCustom = selectedPreset != "Auto"
                    wbCustomSeekBar.visibility = if (isCustom) View.VISIBLE else View.GONE
                    wbCustomValue.visibility = if (isCustom) View.VISIBLE else View.GONE
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        }

        // Custom temperature controls (initially hidden)
        val customTempRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        wbCustomSeekBar = SeekBar(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            max = 100
            visibility = View.GONE
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        val temp = 2000 + progress * 80 // 2000K to 10000K range
                        manualControlsManager?.setManualWhiteBalance(temp)
                        updateWbCustomValueDisplay(temp)
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }

        wbCustomValue = TextView(context).apply {
            text = "5500K"
            textSize = 12f
            setTextColor(Color.YELLOW)
            setPadding(8, 0, 8, 0)
            minWidth = 80
            gravity = Gravity.CENTER
            visibility = View.GONE
        }

        customTempRow.addView(wbCustomSeekBar)
        customTempRow.addView(wbCustomValue)

        wbContainer.addView(wbLabel)
        wbContainer.addView(wbSpinner)
        wbContainer.addView(customTempRow)
        controlsContainer.addView(wbContainer)
    }

    private fun createExposureControls() {
        exposureContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 8, 0, 8)
        }

        exposureLabel = TextView(context).apply {
            text = "Exposure Compensation"
            textSize = 14f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
        }

        val exposureControlsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        exposureSeekBar = SeekBar(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            max = 18 // -9 to +9 in 1/3 EV steps
            progress = 9 // Center position (0 EV)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        val evSteps = progress - 9
                        manualControlsManager?.setExposureCompensation(evSteps)
                        updateExposureValueDisplay(evSteps)
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }

        exposureValue = TextView(context).apply {
            text = "0.0 EV"
            textSize = 12f
            setTextColor(Color.YELLOW)
            setPadding(8, 0, 8, 0)
            minWidth = 80
            gravity = Gravity.CENTER
        }

        exposureControlsRow.addView(exposureSeekBar)
        exposureControlsRow.addView(exposureValue)

        exposureContainer.addView(exposureLabel)
        exposureContainer.addView(exposureControlsRow)
        controlsContainer.addView(exposureContainer)
    }

    private fun createVisualAidsControls() {
        visualAidsContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 16, 0, 8)
        }

        val visualAidsTitle = TextView(context).apply {
            text = "Visual Aids"
            textSize = 14f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, 8)
        }

        histogramSwitch = Switch(context).apply {
            text = "Histogram"
            textSize = 12f
            setTextColor(Color.WHITE)
            setOnCheckedChangeListener { _, isChecked ->
                updateVisualAids()
            }
        }

        zebraSwitch = Switch(context).apply {
            text = "Zebra Pattern"
            textSize = 12f
            setTextColor(Color.WHITE)
            setOnCheckedChangeListener { _, isChecked ->
                updateVisualAids()
            }
        }

        focusPeakingSwitch = Switch(context).apply {
            text = "Focus Peaking"
            textSize = 12f
            setTextColor(Color.WHITE)
            setOnCheckedChangeListener { _, isChecked ->
                updateVisualAids()
            }
        }

        exposureMeterSwitch = Switch(context).apply {
            text = "Exposure Meter"
            textSize = 12f
            setTextColor(Color.WHITE)
            setOnCheckedChangeListener { _, isChecked ->
                updateVisualAids()
            }
        }

        overlaySwitch = Switch(context).apply {
            text = "Pro Info Overlay"
            textSize = 12f
            setTextColor(Color.WHITE)
            setOnCheckedChangeListener { _, isChecked ->
                updateVisualAids()
            }
        }

        visualAidsContainer.addView(visualAidsTitle)
        visualAidsContainer.addView(histogramSwitch)
        visualAidsContainer.addView(zebraSwitch)
        visualAidsContainer.addView(focusPeakingSwitch)
        visualAidsContainer.addView(exposureMeterSwitch)
        visualAidsContainer.addView(overlaySwitch)
        controlsContainer.addView(visualAidsContainer)
    }

    private fun layoutComponents() {
        val constraintSet = ConstraintSet()
        constraintSet.clone(this)

        // Manual mode switch at top
        constraintSet.connect(manualModeSwitch.id, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
        constraintSet.connect(manualModeSwitch.id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
        constraintSet.connect(manualModeSwitch.id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)

        // Controls container below switch
        constraintSet.connect(controlsContainer.id, ConstraintSet.TOP, manualModeSwitch.id, ConstraintSet.BOTTOM, 16)
        constraintSet.connect(controlsContainer.id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
        constraintSet.connect(controlsContainer.id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
        constraintSet.connect(controlsContainer.id, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)

        constraintSet.applyTo(this)
    }

    // Private update methods

    private fun setupControlsCallbacks() {
        manualControlsManager?.setOnControlsChangedCallback { state ->
            lifecycleOwner?.lifecycleScope?.launch {
                updateUIFromState(state)
            }
        }
    }

    private fun updateUIFromState(state: ManualControlsState) {
        manualModeSwitch.isChecked = state.isManualModeEnabled
        controlsContainer.visibility = if (state.isManualModeEnabled) View.VISIBLE else View.GONE

        // Update ranges
        isoRange = state.isoRange
        shutterRange = state.shutterSpeedRange
        focusRange = state.focusDistanceRange

        // Update control values
        updateIsoValueDisplay(state.manualIso)
        updateShutterValueDisplay(state.manualShutterSpeed)
        updateFocusValueDisplay(state.manualFocusDistance)
        updateExposureValueDisplay(state.exposureCompensation)

        // Update visual aids switches
        histogramSwitch.isChecked = state.showHistogram
        zebraSwitch.isChecked = state.showZebraPattern
        focusPeakingSwitch.isChecked = state.showFocusPeaking
        exposureMeterSwitch.isChecked = state.showExposureMeter
        overlaySwitch.isChecked = state.showProfessionalOverlay
    }

    private fun updateIsoValueDisplay(iso: Int?) {
        isoValue.text = iso?.toString() ?: "AUTO"
        iso?.let {
            val progress = ((it - isoRange.lower) * 100 / (isoRange.upper - isoRange.lower)).coerceIn(0, 100)
            isoSeekBar.progress = progress
        }
    }

    private fun updateShutterValueDisplay(shutterSpeed: Long?) {
        if (shutterSpeed == null) {
            shutterValue.text = "AUTO"
        } else {
            shutterValue.text = manualControlsManager?.formatShutterSpeed(shutterSpeed) ?: "1/60s"
            val index = SHUTTER_PRESETS.indexOf(shutterSpeed)
            if (index >= 0) {
                shutterSeekBar.progress = index
            }
        }
    }

    private fun updateFocusValueDisplay(focusDistance: Float?) {
        if (focusDistance == null) {
            focusValue.text = "AUTO"
        } else {
            focusValue.text = String.format("%.2f", focusDistance)
            val progress = ((focusDistance / focusRange.upper) * 100).toInt().coerceIn(0, 100)
            focusSeekBar.progress = progress
        }
    }

    private fun updateExposureValueDisplay(evSteps: Int) {
        val evValue = evSteps / 3.0
        exposureValue.text = String.format("%+.1f EV", evValue)
        exposureSeekBar.progress = evSteps + 9
    }

    private fun updateWbCustomValueDisplay(colorTemp: Int) {
        wbCustomValue.text = "${colorTemp}K"
    }

    private fun updateVisualAids() {
        manualControlsManager?.setVisualAidsEnabled(
            histogram = histogramSwitch.isChecked,
            zebraPattern = zebraSwitch.isChecked,
            focusPeaking = focusPeakingSwitch.isChecked,
            exposureMeter = exposureMeterSwitch.isChecked,
            professionalOverlay = overlaySwitch.isChecked
        )
    }
}