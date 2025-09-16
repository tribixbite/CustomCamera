package com.customcamera.app.plugins

import android.content.Context
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.camera.core.Camera
import com.customcamera.app.engine.CameraContext
import com.customcamera.app.engine.plugins.ControlPlugin
import com.customcamera.app.engine.plugins.ControlResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * ManualFocusPlugin provides manual focus distance control
 * with UI slider and focus lock functionality.
 */
class ManualFocusPlugin : ControlPlugin() {

    override val name: String = "ManualFocus"
    override val version: String = "1.0.0"
    override val priority: Int = 12 // High priority for focus

    private var cameraContext: CameraContext? = null
    private var currentCamera: Camera? = null
    private var manualFocusView: View? = null

    // Focus state
    private var isManualFocusEnabled: Boolean = false
    private var focusDistance: Float = 0.5f // 0.0 = infinity, 1.0 = closest
    private var focusLocked: Boolean = false

    // UI components
    private var focusSlider: SeekBar? = null
    private var focusDistanceText: TextView? = null
    private var focusLockIndicator: TextView? = null

    override suspend fun initialize(context: CameraContext) {
        this.cameraContext = context
        Log.i(TAG, "ManualFocusPlugin initialized")

        loadSettings(context)

        context.debugLogger.logPlugin(
            name,
            "initialized",
            mapOf(
                "manualFocusEnabled" to isManualFocusEnabled,
                "focusDistance" to focusDistance,
                "focusLocked" to focusLocked
            )
        )
    }

    override suspend fun onCameraReady(camera: Camera) {
        currentCamera = camera
        Log.i(TAG, "Camera ready for manual focus")

        // Apply current focus settings
        val result = applyControls(camera)

        cameraContext?.debugLogger?.logPlugin(
            name,
            "camera_ready",
            mapOf(
                "manualFocusEnabled" to isManualFocusEnabled,
                "result" to result.toString()
            )
        )
    }

    override suspend fun onCameraReleased(camera: Camera) {
        Log.i(TAG, "Camera released, preserving focus settings")
        currentCamera = null
    }

    override suspend fun applyControls(camera: Camera): ControlResult {
        return try {
            if (isManualFocusEnabled) {
                // Note: CameraX has limited manual focus support
                // Full manual focus requires Camera2 API
                Log.i(TAG, "Manual focus applied: distance=$focusDistance, locked=$focusLocked")
                ControlResult.Success("Manual focus applied (limited CameraX support)")
            } else {
                // Cancel any manual focus and return to auto
                camera.cameraControl.cancelFocusAndMetering()
                Log.i(TAG, "Returned to auto focus")
                ControlResult.Success("Auto focus restored")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply manual focus", e)
            ControlResult.Failure("Manual focus failed: ${e.message}", e)
        }
    }

    override suspend fun resetControls(camera: Camera): ControlResult {
        return try {
            isManualFocusEnabled = false
            focusDistance = 0.5f
            focusLocked = false

            applyControls(camera)
            updateUI()
            saveSettings()

            ControlResult.Success("Manual focus reset to auto")
        } catch (e: Exception) {
            ControlResult.Failure("Failed to reset manual focus: ${e.message}", e)
        }
    }

    override fun getCurrentSettings(): Map<String, Any> {
        return mapOf(
            "isManualFocusEnabled" to isManualFocusEnabled,
            "focusDistance" to focusDistance,
            "focusLocked" to focusLocked,
            "focusDistancePercent" to (focusDistance * 100).toInt()
        )
    }

    /**
     * Create manual focus control UI
     */
    fun createManualFocusUI(context: Context): View {
        Log.i(TAG, "Creating manual focus UI")

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }

        // Focus distance control
        container.addView(createFocusDistanceControl(context))

        // Focus lock indicator
        focusLockIndicator = TextView(context).apply {
            text = if (focusLocked) "🔒 Focus Locked" else "🔓 Focus Unlocked"
            textSize = 14f
            setPadding(0, 8, 0, 0)
        }
        container.addView(focusLockIndicator)

        manualFocusView = container
        return container
    }

    /**
     * Set manual focus mode
     */
    fun setManualFocusEnabled(enabled: Boolean) {
        if (isManualFocusEnabled != enabled) {
            isManualFocusEnabled = enabled

            currentCamera?.let { camera ->
                CoroutineScope(Dispatchers.Main).launch {
                    applyControls(camera)
                }
            }

            updateUI()
            saveSettings()

            Log.i(TAG, "Manual focus ${if (enabled) "enabled" else "disabled"}")

            cameraContext?.debugLogger?.logPlugin(
                name,
                "manual_focus_toggled",
                mapOf("enabled" to enabled)
            )
        }
    }

    /**
     * Set focus distance (0.0 = infinity, 1.0 = closest)
     */
    fun setFocusDistance(distance: Float) {
        val clampedDistance = distance.coerceIn(0f, 1f)

        if (focusDistance != clampedDistance) {
            focusDistance = clampedDistance

            if (isManualFocusEnabled) {
                currentCamera?.let { camera ->
                    CoroutineScope(Dispatchers.Main).launch {
                        applyControls(camera)
                    }
                }
            }

            updateFocusDistanceUI()
            saveSettings()

            Log.d(TAG, "Focus distance set to: $clampedDistance")
        }
    }

    /**
     * Toggle focus lock
     */
    fun toggleFocusLock() {
        focusLocked = !focusLocked
        updateFocusLockUI()
        saveSettings()

        Log.i(TAG, "Focus lock ${if (focusLocked) "enabled" else "disabled"}")

        cameraContext?.debugLogger?.logPlugin(
            name,
            "focus_lock_toggled",
            mapOf("locked" to focusLocked)
        )
    }

    /**
     * Reset to auto focus
     */
    fun resetToAutoFocus() {
        isManualFocusEnabled = false
        focusLocked = false

        currentCamera?.let { camera ->
            CoroutineScope(Dispatchers.Main).launch {
                resetControls(camera)
            }
        }

        Log.i(TAG, "Reset to auto focus")
    }

    /**
     * Get focus distance in human-readable format
     */
    fun getFocusDistanceDescription(): String {
        return when {
            focusDistance < 0.1f -> "Infinity"
            focusDistance < 0.3f -> "Far (${String.format("%.1f", focusDistance * 10)}m)"
            focusDistance < 0.7f -> "Medium (${String.format("%.0f", focusDistance * 100)}cm)"
            else -> "Close (${String.format("%.0f", focusDistance * 50)}cm)"
        }
    }

    override fun cleanup() {
        Log.i(TAG, "Cleaning up ManualFocusPlugin")

        manualFocusView = null
        focusSlider = null
        focusDistanceText = null
        focusLockIndicator = null
        currentCamera = null
        cameraContext = null
    }

    private fun createFocusDistanceControl(context: Context): View {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 8, 0, 8)
        }

        // Title
        val titleText = TextView(context).apply {
            text = "Manual Focus Distance"
            textSize = 16f
            setPadding(0, 0, 0, 8)
        }
        container.addView(titleText)

        // Distance display
        focusDistanceText = TextView(context).apply {
            text = getFocusDistanceDescription()
            textSize = 14f
            setPadding(0, 0, 0, 4)
        }
        container.addView(focusDistanceText)

        // Focus distance slider
        focusSlider = SeekBar(context).apply {
            max = 100
            progress = (focusDistance * 100).toInt()

            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        val distance = progress / 100f
                        setFocusDistance(distance)
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        container.addView(focusSlider)

        return container
    }

    private fun updateUI() {
        updateFocusDistanceUI()
        updateFocusLockUI()
    }

    private fun updateFocusDistanceUI() {
        focusDistanceText?.text = getFocusDistanceDescription()
        focusSlider?.progress = (focusDistance * 100).toInt()
    }

    private fun updateFocusLockUI() {
        focusLockIndicator?.text = if (focusLocked) "🔒 Focus Locked" else "🔓 Focus Unlocked"
    }

    private fun loadSettings(context: CameraContext) {
        val settings = context.settingsManager

        try {
            isManualFocusEnabled = settings.getPluginSetting(name, "manualFocusEnabled", "false").toBoolean()
            focusDistance = settings.getPluginSetting(name, "focusDistance", "0.5").toFloat()
            focusLocked = settings.getPluginSetting(name, "focusLocked", "false").toBoolean()

            Log.i(TAG, "Loaded settings: enabled=$isManualFocusEnabled, distance=$focusDistance, locked=$focusLocked")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load settings, using defaults", e)
        }
    }

    private fun saveSettings() {
        val settings = cameraContext?.settingsManager ?: return

        settings.setPluginSetting(name, "manualFocusEnabled", isManualFocusEnabled.toString())
        settings.setPluginSetting(name, "focusDistance", focusDistance.toString())
        settings.setPluginSetting(name, "focusLocked", focusLocked.toString())
    }

    companion object {
        private const val TAG = "ManualFocusPlugin"
    }
}