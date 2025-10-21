package com.customcamera.app.plugins

import android.content.Context
import android.util.Log
import androidx.camera.core.Camera
import com.customcamera.app.R
import com.customcamera.app.engine.CameraContext
import com.customcamera.app.engine.plugins.CameraPlugin
import com.customcamera.app.engine.plugins.PluginCategory
import com.customcamera.app.engine.plugins.PluginDependencies
import com.customcamera.app.engine.plugins.PluginProvider

/**
 * DiagnosticOverlayPlugin provides real-time camera and sensor diagnostics
 *
 * Shows:
 * - Camera state (CLOSED, OPENING, OPEN, etc.)
 * - Sensor availability
 * - Permissions status
 * - Camera lifecycle events
 * - Error messages with timestamps
 *
 * Note: This plugin controls the DiagnosticOverlay view that's already in the layout.
 * It doesn't create a new view, just toggles visibility of the existing one.
 */
class DiagnosticOverlayPlugin : CameraPlugin() {

    override val name: String = "DiagnosticOverlay"
    override val displayName: String = "Diagnostic Overlay"
    override val description: String = "Real-time camera and sensor diagnostics"
    override val iconResId: Int = R.drawable.ic_extension
    override val category: PluginCategory = PluginCategory.DEBUG
    override val userToggleable: Boolean = true
    override val version: String = "1.0.0"
    override val priority: Int = 10 // Low priority - debug feature

    private var cameraContext: CameraContext? = null
    private var isOverlayVisible: Boolean = false
    // Reference to overlay will be set via callback from activity
    var overlayToggleCallback: ((Boolean) -> Unit)? = null

    override suspend fun initialize(context: CameraContext) {
        this.cameraContext = context

        // Load saved state
        isOverlayVisible = context.settingsManager.getPluginSetting(
            name,
            "overlayVisible",
            "false"
        ).toBoolean()

        Log.i(TAG, "DiagnosticOverlayPlugin initialized, visible=$isOverlayVisible")

        context.debugLogger.logPlugin(
            name,
            "initialized",
            mapOf("visible" to isOverlayVisible)
        )
    }

    override suspend fun onCameraReady(camera: Camera) {
        Log.i(TAG, "Camera ready - diagnostic overlay available")

        cameraContext?.debugLogger?.logPlugin(
            name,
            "camera_ready",
            mapOf("enabled" to isEnabled, "visible" to isOverlayVisible)
        )
    }

    override suspend fun onCameraReleased(camera: Camera) {
        Log.i(TAG, "Camera released - diagnostic overlay persists")
    }

    override fun onPluginEnabled() {
        showOverlay()
        Log.i(TAG, "DiagnosticOverlay enabled, showing overlay")
    }

    override fun onPluginDisabled() {
        hideOverlay()
        Log.i(TAG, "DiagnosticOverlay disabled, hiding overlay")
    }

    /**
     * Show the diagnostic overlay
     */
    private fun showOverlay() {
        if (!isOverlayVisible) {
            isOverlayVisible = true
            overlayToggleCallback?.invoke(true)
            saveVisibilityState()

            cameraContext?.debugLogger?.logPlugin(
                name,
                "overlay_shown",
                emptyMap()
            )
        }
    }

    /**
     * Hide the diagnostic overlay
     */
    private fun hideOverlay() {
        if (isOverlayVisible) {
            isOverlayVisible = false
            overlayToggleCallback?.invoke(false)
            saveVisibilityState()

            cameraContext?.debugLogger?.logPlugin(
                name,
                "overlay_hidden",
                emptyMap()
            )
        }
    }

    /**
     * Toggle overlay visibility
     */
    fun toggleOverlay() {
        if (isOverlayVisible) {
            hideOverlay()
        } else {
            showOverlay()
        }
    }

    /**
     * Check if overlay is currently visible
     */
    fun isVisible(): Boolean = isOverlayVisible

    /**
     * Save visibility state to settings
     */
    private fun saveVisibilityState() {
        cameraContext?.settingsManager?.setPluginSetting(
            name,
            "overlayVisible",
            isOverlayVisible.toString()
        )
    }

    override fun cleanup() {
        Log.i(TAG, "Cleaning up DiagnosticOverlayPlugin")
        overlayToggleCallback = null
        cameraContext = null
    }

    companion object : PluginProvider {
        private const val TAG = "DiagnosticOverlayPlugin"

        // PluginProvider implementation
        override val id: String = "diagnostic_overlay"

        override val displayNameRes: Int = R.string.plugin_diagnostic_overlay_display_name

        override val descriptionRes: Int = R.string.plugin_diagnostic_overlay_description

        override val iconResId: Int = R.drawable.ic_extension

        override val category: PluginCategory = PluginCategory.DEBUG

        override val userToggleable: Boolean = true

        override val showInDropdown: Boolean = true

        override fun isSupported(context: Context): Boolean {
            // Diagnostic overlay is supported on all devices
            return true
        }

        override fun create(dependencies: PluginDependencies): CameraPlugin {
            return DiagnosticOverlayPlugin()
        }
    }
}
