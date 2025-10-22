package com.customcamera.app.video

import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.Log
import android.view.*
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.customcamera.app.plugins.AdvancedVideoRecordingPlugin
import com.customcamera.app.plugins.VideoQuality
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlin.math.min

/**
 * VideoControlsOverlay provides a professional video recording interface
 * with manual controls, quality selection, and recording status indicators.
 */
class VideoControlsOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private var videoPlugin: AdvancedVideoRecordingPlugin? = null

    // UI Components
    private lateinit var recordButton: Button
    private lateinit var pauseButton: ImageButton
    private lateinit var stopButton: ImageButton
    private lateinit var qualitySpinner: Spinner
    private lateinit var recordingIndicator: View
    private lateinit var durationText: TextView
    private lateinit var manualControlsPanel: LinearLayout
    private lateinit var isoToggle: ToggleButton
    private lateinit var shutterToggle: ToggleButton
    private lateinit var focusToggle: ToggleButton
    private lateinit var stabilizationToggle: ToggleButton
    private lateinit var audioToggle: ToggleButton
    private lateinit var stabilizationModeSpinner: Spinner
    private lateinit var stabilizationStrengthSeekBar: SeekBar
    private lateinit var stabilizationStrengthLabel: TextView

    // Animation and visual state
    private var recordingAnimation: ObjectAnimator? = null
    private var isControlsVisible = true

    init {
        setupLayout()
        setupControls()
        Log.i(TAG, "VideoControlsOverlay initialized")
    }

    /**
     * Set the video plugin for handling recording operations
     */
    fun setVideoPlugin(plugin: AdvancedVideoRecordingPlugin) {
        this.videoPlugin = plugin
        setupPluginObservers()
        updateControlsFromPlugin()
    }

    /**
     * Toggle visibility of manual controls
     */
    fun toggleManualControls() {
        isControlsVisible = !isControlsVisible

        val targetAlpha = if (isControlsVisible) 1.0f else 0.3f
        manualControlsPanel.animate()
            .alpha(targetAlpha)
            .setDuration(300)
            .start()

        Log.d(TAG, "Manual controls ${if (isControlsVisible) "shown" else "hidden"}")
    }

    /**
     * Show/hide the entire overlay
     */
    fun setOverlayVisible(visible: Boolean) {
        visibility = if (visible) View.VISIBLE else View.GONE
    }

    private fun setupLayout() {
        // Create main container
        val mainContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM
            }
            setPadding(16, 16, 16, 32)
        }

        // Create recording controls container
        val recordingContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(0, 0, 0, 16)
        }

        // Recording status and duration
        val statusContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        recordingIndicator = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(24, 24).apply {
                setMargins(0, 0, 16, 0)
            }
            setBackgroundColor(Color.RED)
            visibility = View.GONE
        }

        durationText = TextView(context).apply {
            text = "00:00"
            textSize = 24f  // Increased from 18f for better visibility
            setTextColor(Color.WHITE)  // Will change to red when recording
            typeface = Typeface.MONOSPACE
        }

        statusContainer.addView(recordingIndicator)
        statusContainer.addView(durationText)

        // Quality selector - Moved to separate line for better visibility
        val qualityContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(0, 8, 0, 8)
        }

        val qualityLabel = TextView(context).apply {
            text = "Quality:"
            textSize = 14f
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 8, 0)
            }
        }

        qualitySpinner = Spinner(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        qualityContainer.addView(qualityLabel)
        qualityContainer.addView(qualitySpinner)

        recordingContainer.addView(statusContainer)

        // Control buttons container
        val buttonsContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(0, 0, 0, 16)
        }

        // Create record button as Button (not ImageButton) to show "REC" text
        recordButton = Button(context).apply {
            text = "REC"
            layoutParams = LinearLayout.LayoutParams(100, 80).apply {
                setMargins(8, 0, 8, 0)
            }
            setBackgroundColor(Color.RED)
            setTextColor(Color.WHITE)
            setOnClickListener { startRecording() }
            contentDescription = "Record"
        }

        // Create pause and stop as ImageButtons with icons
        pauseButton = createControlButton("⏸") { pauseRecording() }
        stopButton = createControlButton("⏹") { stopRecording() }

        // Set proper icons on ImageButtons to fix "white squares" issue
        pauseButton.setImageResource(android.R.drawable.ic_media_pause)
        stopButton.setImageResource(android.R.drawable.ic_delete)  // Using delete icon as stop placeholder

        // Initially hide pause and stop buttons
        pauseButton.visibility = View.GONE
        stopButton.visibility = View.GONE

        buttonsContainer.addView(recordButton)
        buttonsContainer.addView(pauseButton)
        buttonsContainer.addView(stopButton)

        // Manual controls panel
        manualControlsPanel = createManualControlsPanel()

        // Add all containers to main container
        mainContainer.addView(recordingContainer)
        mainContainer.addView(qualityContainer)  // Quality selector on its own line with label
        mainContainer.addView(buttonsContainer)
        mainContainer.addView(manualControlsPanel)

        addView(mainContainer)
    }

    private fun createControlButton(text: String, onClick: () -> Unit): ImageButton {
        return ImageButton(context).apply {
            layoutParams = LinearLayout.LayoutParams(80, 80).apply {
                setMargins(8, 0, 8, 0)
            }
            background = ContextCompat.getDrawable(context, android.R.drawable.btn_default)
            setOnClickListener { onClick() }
            contentDescription = text
        }
    }

    private fun createManualControlsPanel(): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            // Reduced padding from 16 to 8 to save vertical space
            setPadding(8, 8, 8, 8)
            setBackgroundColor(Color.argb(180, 0, 0, 0))

            // Title (reduced size and margins for compactness)
            val title = TextView(context).apply {
                text = "Manual Controls"
                textSize = 14f  // Reduced from 16f
                setTextColor(Color.WHITE)
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 0, 8)  // Reduced from 16
                }
            }
            addView(title)

            // Controls grid
            val controlsGrid = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            // First row
            val row1 = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                gravity = Gravity.CENTER
            }

            isoToggle = createToggleButton("Manual ISO") { enabled ->
                videoPlugin?.setManualISOEnabled(enabled)
            }
            shutterToggle = createToggleButton("Manual Shutter") { enabled ->
                videoPlugin?.setManualShutterEnabled(enabled)
            }

            row1.addView(isoToggle)
            row1.addView(shutterToggle)

            // Second row
            val row2 = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                gravity = Gravity.CENTER
            }

            focusToggle = createToggleButton("Manual Focus") { enabled ->
                videoPlugin?.setManualFocusEnabled(enabled)
            }
            stabilizationToggle = createToggleButton("Stabilization") { enabled ->
                videoPlugin?.setStabilizationEnabled(enabled)
            }

            row2.addView(focusToggle)
            row2.addView(stabilizationToggle)

            // Third row
            val row3 = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                gravity = Gravity.CENTER
            }

            audioToggle = createToggleButton("Audio") { enabled ->
                videoPlugin?.setAudioRecordingEnabled(enabled)
            }

            row3.addView(audioToggle)

            controlsGrid.addView(row1)
            controlsGrid.addView(row2)
            controlsGrid.addView(row3)

            // Stabilization Mode Selector (reduced margins)
            val stabilizationModeContainer = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 8, 0, 4)  // Reduced from 16, 8
                }
            }

            val stabilizationModeLabel = TextView(context).apply {
                text = "Stabilization Mode"
                textSize = 12f  // Reduced from 14f
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 0, 4)  // Reduced from 8
                }
            }

            stabilizationModeSpinner = Spinner(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            stabilizationModeContainer.addView(stabilizationModeLabel)
            stabilizationModeContainer.addView(stabilizationModeSpinner)

            // Stabilization Strength Control (reduced margins)
            val stabilizationStrengthContainer = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 4, 0, 0)  // Reduced from 8
                }
            }

            stabilizationStrengthLabel = TextView(context).apply {
                text = "Stabilization Strength: 70%"
                textSize = 12f  // Reduced from 14f
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 0, 4)  // Reduced from 8
                }
            }

            stabilizationStrengthSeekBar = SeekBar(context).apply {
                max = 100
                progress = 70
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            stabilizationStrengthContainer.addView(stabilizationStrengthLabel)
            stabilizationStrengthContainer.addView(stabilizationStrengthSeekBar)

            addView(controlsGrid)
            addView(stabilizationModeContainer)
            addView(stabilizationStrengthContainer)
        }
    }

    private fun createToggleButton(text: String, onToggle: (Boolean) -> Unit): ToggleButton {
        return ToggleButton(context).apply {
            this.text = text
            textOff = text
            textOn = text
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                setMargins(4, 4, 4, 4)
            }
            setOnCheckedChangeListener { _, isChecked ->
                onToggle(isChecked)
            }
        }
    }

    private fun setupControls() {
        // Set up quality spinner
        setupQualitySpinner()

        // Set up stabilization controls
        setupStabilizationControls()

        // Set up long press on controls panel to toggle visibility
        manualControlsPanel.setOnLongClickListener {
            toggleManualControls()
            true
        }
    }

    private fun setupQualitySpinner() {
        val qualities = VideoQuality.values().map { it.displayName }
        val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, qualities)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        qualitySpinner.adapter = adapter

        qualitySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedQuality = VideoQuality.values()[position]
                videoPlugin?.setVideoQuality(selectedQuality)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupStabilizationControls() {
        // Set up stabilization mode spinner
        val modes = VideoStabilizationManager.StabilizationMode.values().map { it.name }
        val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, modes)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        stabilizationModeSpinner.adapter = adapter

        stabilizationModeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedMode = VideoStabilizationManager.StabilizationMode.values()[position]
                videoPlugin?.setStabilizationMode(selectedMode)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Set up stabilization strength SeekBar
        stabilizationStrengthSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val strength = progress / 100.0f
                    videoPlugin?.setStabilizationStrength(strength)
                    stabilizationStrengthLabel.text = "Stabilization Strength: ${progress}%"
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun setupPluginObservers() {
        val plugin = videoPlugin ?: return

        // Get LifecycleOwner from Activity context
        // The context passed to VideoControlsOverlay is from CameraActivityEngine which is an Activity
        val activity = context as? android.app.Activity
        val lifecycleOwner = activity as? LifecycleOwner

        if (lifecycleOwner == null) {
            Log.e(TAG, "❌ Failed to get LifecycleOwner from context - observers not set up!")
            return
        }

        Log.i(TAG, "✅ Setting up video plugin observers with LifecycleOwner")

        lifecycleOwner.lifecycleScope.launch {
            // Observe recording state
            plugin.isRecording.collect { isRecording ->
                Log.d(TAG, "📹 Recording state changed: $isRecording")
                updateRecordingState(isRecording)
            }
        }

        lifecycleOwner.lifecycleScope.launch {
            // Observe recording duration
            plugin.recordingDuration.collect { duration ->
                updateDuration(duration)
            }
        }

        lifecycleOwner.lifecycleScope.launch {
            // Observe pause state
            plugin.isPaused.collect { isPaused ->
                updatePauseState(isPaused)
            }
        }
    }

    private fun updateControlsFromPlugin() {
        val plugin = videoPlugin ?: return

        // Update toggle states
        isoToggle.isChecked = plugin.manualISOEnabled.value
        shutterToggle.isChecked = plugin.manualShutterEnabled.value
        focusToggle.isChecked = plugin.manualFocusEnabled.value
        stabilizationToggle.isChecked = plugin.stabilizationEnabled.value
        audioToggle.isChecked = plugin.audioRecordingEnabled.value

        // Update quality spinner
        val currentQuality = plugin.currentQuality.value
        val qualityIndex = VideoQuality.values().indexOf(currentQuality)
        if (qualityIndex >= 0) {
            qualitySpinner.setSelection(qualityIndex)
        }

        // Update stabilization mode spinner
        val currentMode = plugin.stabilizationMode.value
        val modeIndex = VideoStabilizationManager.StabilizationMode.values().indexOf(currentMode)
        if (modeIndex >= 0) {
            stabilizationModeSpinner.setSelection(modeIndex)
        }

        // Update stabilization strength SeekBar
        val currentStrength = plugin.stabilizationStrength.value
        val strengthPercent = (currentStrength * 100).toInt()
        stabilizationStrengthSeekBar.progress = strengthPercent
        stabilizationStrengthLabel.text = "Stabilization Strength: ${strengthPercent}%"
    }

    private fun updateRecordingState(isRecording: Boolean) {
        if (isRecording) {
            recordButton.visibility = View.GONE
            pauseButton.visibility = View.VISIBLE
            stopButton.visibility = View.VISIBLE
            recordingIndicator.visibility = View.VISIBLE

            // Change timer color to red when recording
            durationText.setTextColor(Color.RED)

            // Start recording indicator animation
            recordingAnimation = ObjectAnimator.ofFloat(recordingIndicator, "alpha", 1.0f, 0.3f).apply {
                duration = 800
                repeatCount = ObjectAnimator.INFINITE
                repeatMode = ObjectAnimator.REVERSE
                start()
            }

            // Lock manual controls during recording
            setManualControlsEnabled(false)
            Log.d(TAG, "🔒 Manual controls locked during recording")
        } else {
            recordButton.visibility = View.VISIBLE
            pauseButton.visibility = View.GONE
            stopButton.visibility = View.GONE
            recordingIndicator.visibility = View.GONE

            // Change timer color back to white when not recording
            durationText.setTextColor(Color.WHITE)

            // Stop recording indicator animation
            recordingAnimation?.cancel()
            recordingAnimation = null

            // Unlock manual controls when not recording
            setManualControlsEnabled(true)
            Log.d(TAG, "🔓 Manual controls unlocked")
        }
    }

    private fun updateDuration(durationMs: Long) {
        val seconds = durationMs / 1000
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        durationText.text = String.format("%02d:%02d", minutes, remainingSeconds)
    }

    private fun updatePauseState(isPaused: Boolean) {
        if (isPaused) {
            recordingAnimation?.pause()
        } else {
            recordingAnimation?.resume()
        }
    }

    /**
     * Enable or disable manual controls panel
     */
    private fun setManualControlsEnabled(enabled: Boolean) {
        // Disable all toggle buttons
        isoToggle.isEnabled = enabled
        shutterToggle.isEnabled = enabled
        focusToggle.isEnabled = enabled
        stabilizationToggle.isEnabled = enabled
        audioToggle.isEnabled = enabled

        // Disable quality and stabilization mode spinners
        qualitySpinner.isEnabled = enabled
        stabilizationModeSpinner.isEnabled = enabled
        stabilizationStrengthSeekBar.isEnabled = enabled

        // Visual feedback: dim controls when disabled
        val alpha = if (enabled) 1.0f else 0.5f
        manualControlsPanel.alpha = alpha
    }

    private fun startRecording() {
        val plugin = videoPlugin ?: return
        val lifecycleOwner = context as? LifecycleOwner ?: return

        lifecycleOwner.lifecycleScope.launch {
            val result = plugin.startRecording()
            if (result.isFailure) {
                Log.e(TAG, "Failed to start recording: ${result.exceptionOrNull()?.message}")
                Toast.makeText(context, "Failed to start recording", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun pauseRecording() {
        val plugin = videoPlugin ?: return
        plugin.pauseRecording()
    }

    private fun stopRecording() {
        val plugin = videoPlugin ?: return
        plugin.stopRecording()
    }

    companion object {
        private const val TAG = "VideoControlsOverlay"
    }
}