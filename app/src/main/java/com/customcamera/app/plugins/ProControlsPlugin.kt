package com.customcamera.app.plugins

import android.content.Context
import android.util.Log
import android.util.Range
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.camera.core.Camera
import androidx.camera.core.CameraControl
import androidx.camera.core.ExposureState
import com.customcamera.app.R
import com.customcamera.app.engine.CameraContext
import com.customcamera.app.engine.plugins.ControlPlugin
import com.customcamera.app.engine.plugins.ControlResult
import com.customcamera.app.engine.plugins.UIEvent
import com.customcamera.app.engine.plugins.UIPlugin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * ProControlsPlugin provides professional manual camera controls including
 * exposure compensation, ISO, and other advanced camera settings.
 * Combines both ControlPlugin and UIPlugin functionality.
 */
class ProControlsPlugin : ControlPlugin() {

    override val name: String = "ProControls"
    override val version: String = "1.0.0"
    override val priority: Int = 20 // High priority for camera controls

    private var cameraContext: CameraContext? = null
    private var currentCamera: Camera? = null
    private var controlsView: View? = null

    // Control ranges - will be populated from camera capabilities
    private var exposureRange: Range<Int>? = null
    private var isoRange: Range<Int>? = null

    // Current control values
    private var currentExposure: Int = 0
    private var currentISO: Int = 100
    private var isAutoExposure: Boolean = true
    private var isAutoISO: Boolean = true

    // Control mode
    private var isManualModeEnabled: Boolean = false

    // UI references
    private var exposureSeekBar: SeekBar? = null
    private var exposureValueText: TextView? = null
    private var isoSeekBar: SeekBar? = null
    private var isoValueText: TextView? = null

    override suspend fun initialize(context: CameraContext) {
        this.cameraContext = context
        Log.i(TAG, "ProControlsPlugin initialized")

        // Load settings
        loadSettings(context)

        context.debugLogger.logPlugin(
            name,
            "initialized",
            mapOf(
                "manualModeEnabled" to isManualModeEnabled,
                "autoExposure" to isAutoExposure,
                "autoISO" to isAutoISO
            )
        )
    }

    override suspend fun onCameraReady(camera: Camera) {
        currentCamera = camera
        Log.i(TAG, "Camera ready, initializing pro controls")

        // Get camera capabilities
        extractCameraCapabilities(camera)

        // Apply current settings
        val result = applyControls(camera)
        cameraContext?.debugLogger?.logPlugin(
            name,
            "camera_ready",
            mapOf(
                "controlsApplied" to result.toString(),
                "exposureRange" to exposureRange.toString(),
                "isoRange" to isoRange.toString()
            )
        )

        // Update UI with current values
        updateControlsUI()
    }

    override suspend fun onCameraReleased(camera: Camera) {
        Log.i(TAG, "Camera released, preserving control settings")
        currentCamera = null
    }

    override suspend fun applyControls(camera: Camera): ControlResult {
        return try {
            val cameraControl = camera.cameraControl

            // Apply exposure compensation if in manual mode
            if (!isAutoExposure && exposureRange != null) {
                cameraControl.setExposureCompensationIndex(currentExposure)
                Log.d(TAG, "Applied exposure compensation: $currentExposure")
            }

            // Note: Direct ISO control is limited in CameraX
            // This would typically be done through Camera2 API for full control
            Log.i(TAG, "Applied pro controls: exposure=$currentExposure, iso=$currentISO")

            ControlResult.Success("Pro controls applied successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply pro controls", e)
            ControlResult.Failure("Failed to apply controls: ${e.message}", e)
        }
    }

    override suspend fun resetControls(camera: Camera): ControlResult {
        return try {
            currentExposure = 0
            currentISO = 100
            isAutoExposure = true
            isAutoISO = true
            isManualModeEnabled = false

            applyControls(camera)
            updateControlsUI()
            saveSettings()

            ControlResult.Success("Pro controls reset to auto")
        } catch (e: Exception) {
            ControlResult.Failure("Failed to reset controls: ${e.message}", e)
        }
    }

    override fun getCurrentSettings(): Map<String, Any> {
        return mapOf(
            "currentExposure" to currentExposure,
            "currentISO" to currentISO,
            "isAutoExposure" to isAutoExposure,
            "isAutoISO" to isAutoISO,
            "isManualModeEnabled" to isManualModeEnabled,
            "exposureRange" to (exposureRange?.toString() ?: "unknown"),
            "isoRange" to (isoRange?.toString() ?: "unknown")
        )
    }

    /**
     * Create the pro controls UI
     */
    fun createControlsUI(context: Context): View? {
        if (!isEnabled || !isManualModeEnabled) {
            return null
        }

        Log.i(TAG, "Creating pro controls UI")

        // Create a vertical layout for controls
        val controlsLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }

        // Add exposure control
        controlsLayout.addView(createExposureControl(context))

        // Add ISO control
        controlsLayout.addView(createISOControl(context))

        controlsView = controlsLayout
        return controlsLayout
    }

    /**
     * Enable or disable manual mode
     */
    fun setManualModeEnabled(enabled: Boolean) {
        if (isManualModeEnabled != enabled) {
            isManualModeEnabled = enabled

            if (!enabled) {
                // Reset to auto when disabling manual mode
                isAutoExposure = true
                isAutoISO = true
                currentCamera?.let { camera ->
                    CoroutineScope(Dispatchers.Main).launch {
                        applyControls(camera)
                        updateControlsUI()
                    }
                }
            }

            saveSettings()
            Log.i(TAG, "Manual mode ${if (enabled) "enabled" else "disabled"}")

            cameraContext?.debugLogger?.logPlugin(
                name,
                "manual_mode_changed",
                mapOf("enabled" to enabled)
            )
        }
    }

    /**
     * Set exposure compensation manually
     */
    fun setExposureCompensation(exposureIndex: Int) {
        val range = exposureRange
        if (range != null && exposureIndex in range) {
            currentExposure = exposureIndex
            isAutoExposure = false

            currentCamera?.let { camera ->
                CoroutineScope(Dispatchers.Main).launch {
                    applyControls(camera)
                }
            }

            updateExposureUI()
            saveSettings()

            Log.i(TAG, "Exposure compensation set to: $exposureIndex")
        }
    }

    /**
     * Set ISO manually (Note: Limited in CameraX)
     */
    fun setISO(iso: Int) {
        val range = isoRange
        if (range != null && iso in range) {
            currentISO = iso
            isAutoISO = false

            // Note: Direct ISO control is limited in CameraX
            // In a production app, you'd use Camera2 API for full manual control

            updateISOUI()
            saveSettings()

            Log.i(TAG, "ISO set to: $iso (Note: Limited control in CameraX)")
        }
    }

    /**
     * Toggle auto exposure
     */
    fun toggleAutoExposure() {
        isAutoExposure = !isAutoExposure

        if (isAutoExposure) {
            currentExposure = 0
        }

        currentCamera?.let { camera ->
            CoroutineScope(Dispatchers.Main).launch {
                applyControls(camera)
            }
        }

        updateExposureUI()
        saveSettings()

        Log.i(TAG, "Auto exposure ${if (isAutoExposure) "enabled" else "disabled"}")
    }

    /**
     * Get exposure compensation value in EV
     */
    fun getExposureEV(): Float {
        val camera = currentCamera ?: return 0f
        val exposureState = camera.cameraInfo.exposureState
        val step = exposureState.exposureCompensationStep
        return currentExposure * step.toFloat()
    }

    override fun cleanup() {
        Log.i(TAG, "Cleaning up ProControlsPlugin")

        controlsView = null
        exposureSeekBar = null
        exposureValueText = null
        isoSeekBar = null
        isoValueText = null
        currentCamera = null
        cameraContext = null
    }

    private fun extractCameraCapabilities(camera: Camera) {
        try {
            val exposureState = camera.cameraInfo.exposureState
            exposureRange = exposureState.exposureCompensationRange

            // For ISO, we'll use a reasonable range since CameraX doesn't expose it directly
            isoRange = Range.create(50, 6400)

            Log.i(TAG, "Camera capabilities extracted:")
            Log.i(TAG, "  Exposure range: $exposureRange")
            Log.i(TAG, "  Exposure step: ${exposureState.exposureCompensationStep}")
            Log.i(TAG, "  ISO range: $isoRange (estimated)")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract camera capabilities", e)
            // Fallback ranges
            exposureRange = Range.create(-6, 6)
            isoRange = Range.create(50, 6400)
        }
    }

    private fun createExposureControl(context: Context): View {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 8, 0, 8)
        }

        // Title
        val titleText = TextView(context).apply {
            text = "Exposure Compensation"
            textSize = 16f
            setPadding(0, 0, 0, 8)
        }
        container.addView(titleText)

        // Value display
        exposureValueText = TextView(context).apply {
            text = "${getExposureEV()}EV"
            textSize = 14f
            setPadding(0, 0, 0, 4)
        }
        container.addView(exposureValueText)

        // Seek bar
        val range = exposureRange ?: Range.create(-6, 6)
        exposureSeekBar = SeekBar(context).apply {
            max = range.upper - range.lower
            progress = currentExposure - range.lower

            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        val exposureValue = progress + range.lower
                        setExposureCompensation(exposureValue)
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        container.addView(exposureSeekBar)

        return container
    }

    private fun createISOControl(context: Context): View {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 8, 0, 8)
        }

        // Title
        val titleText = TextView(context).apply {
            text = "ISO Sensitivity"
            textSize = 16f
            setPadding(0, 0, 0, 8)
        }
        container.addView(titleText)

        // Value display
        isoValueText = TextView(context).apply {
            text = "ISO $currentISO"
            textSize = 14f
            setPadding(0, 0, 0, 4)
        }
        container.addView(isoValueText)

        // Seek bar
        val range = isoRange ?: Range.create(50, 6400)
        isoSeekBar = SeekBar(context).apply {
            max = range.upper - range.lower
            progress = currentISO - range.lower

            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        val isoValue = progress + range.lower
                        setISO(isoValue)
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        container.addView(isoSeekBar)

        return container
    }

    private fun updateControlsUI() {
        updateExposureUI()
        updateISOUI()
    }

    private fun updateExposureUI() {
        exposureValueText?.text = "${getExposureEV()}EV"

        val range = exposureRange
        if (range != null) {
            exposureSeekBar?.progress = currentExposure - range.lower
        }
    }

    private fun updateISOUI() {
        isoValueText?.text = "ISO $currentISO"

        val range = isoRange
        if (range != null) {
            isoSeekBar?.progress = currentISO - range.lower
        }
    }

    private fun loadSettings(context: CameraContext) {
        val settings = context.settingsManager

        try {
            isManualModeEnabled = settings.getPluginSetting(name, "manualModeEnabled", "false").toBoolean()
            currentExposure = settings.getPluginSetting(name, "currentExposure", "0").toInt()
            currentISO = settings.getPluginSetting(name, "currentISO", "100").toInt()
            isAutoExposure = settings.getPluginSetting(name, "autoExposure", "true").toBoolean()
            isAutoISO = settings.getPluginSetting(name, "autoISO", "true").toBoolean()

            Log.i(TAG, "Loaded settings: manual=$isManualModeEnabled, exposure=$currentExposure, iso=$currentISO")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load some settings, using defaults", e)
        }
    }

    private fun saveSettings() {
        val settings = cameraContext?.settingsManager ?: return

        settings.setPluginSetting(name, "manualModeEnabled", isManualModeEnabled.toString())
        settings.setPluginSetting(name, "currentExposure", currentExposure.toString())
        settings.setPluginSetting(name, "currentISO", currentISO.toString())
        settings.setPluginSetting(name, "autoExposure", isAutoExposure.toString())
        settings.setPluginSetting(name, "autoISO", isAutoISO.toString())
    }

    companion object {
        private const val TAG = "ProControlsPlugin"
    }
}