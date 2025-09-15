package com.customcamera.app.plugins

import android.content.Context
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import com.customcamera.app.engine.CameraContext
import com.customcamera.app.engine.plugins.UIPlugin
import com.customcamera.app.pip.DualCameraManager
import com.customcamera.app.pip.PiPOverlayView

/**
 * PiPPlugin provides picture-in-picture overlay system
 * for simultaneous dual camera operation.
 */
class PiPPlugin : UIPlugin() {

    override val name: String = "PiP"
    override val version: String = "1.0.0"
    override val priority: Int = 30 // Medium priority for UI

    private var cameraContext: CameraContext? = null
    private var dualCameraManager: DualCameraManager? = null
    private var pipOverlayView: PiPOverlayView? = null

    // PiP state
    private var isPiPEnabled: Boolean = false
    private var pipPosition: PiPPosition = PiPPosition.TOP_RIGHT
    private var pipSize: PiPSize = PiPSize.SMALL

    enum class PiPPosition {
        TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT
    }

    enum class PiPSize {
        SMALL, MEDIUM, LARGE
    }

    override suspend fun initialize(context: CameraContext) {
        this.cameraContext = context
        Log.i(TAG, "PiPPlugin initialized")

        // Initialize dual camera manager
        dualCameraManager = DualCameraManager(context)

        // Load settings
        loadSettings(context)

        context.debugLogger.logPlugin(
            name,
            "initialized",
            mapOf(
                "pipEnabled" to isPiPEnabled,
                "position" to pipPosition.name,
                "size" to pipSize.name
            )
        )
    }

    override suspend fun onCameraReady(camera: Camera) {
        Log.i(TAG, "Camera ready for PiP system")

        if (isPiPEnabled) {
            try {
                // Initialize dual camera setup
                dualCameraManager?.initializeDualCameras()
                Log.i(TAG, "Dual cameras initialized for PiP")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize dual cameras for PiP", e)
                isPiPEnabled = false
            }
        }

        cameraContext?.debugLogger?.logPlugin(
            name,
            "camera_ready",
            mapOf("pipEnabled" to isPiPEnabled)
        )
    }

    override suspend fun onCameraReleased(camera: Camera) {
        Log.i(TAG, "Camera released, stopping PiP")
        stopPiP()
    }

    override fun createUIView(context: CameraContext): View? {
        if (!isEnabled || !isPiPEnabled) {
            return null
        }

        Log.i(TAG, "Creating PiP overlay UI")

        // Create PiP overlay view
        pipOverlayView = PiPOverlayView(context.context).apply {
            setPiPPosition(pipPosition)
            setPiPSize(pipSize)
        }

        return pipOverlayView
    }

    override fun updateUI(camera: Camera) {
        pipOverlayView?.updateForCamera(camera)
        Log.d(TAG, "PiP UI updated for camera")
    }

    /**
     * Enable PiP mode
     */
    suspend fun enablePiP(): Boolean {
        if (isPiPEnabled) {
            return true
        }

        Log.i(TAG, "Enabling PiP mode")

        return try {
            val context = cameraContext ?: return false

            // Initialize dual camera manager
            dualCameraManager?.initializeDualCameras()

            isPiPEnabled = true
            saveSettings()

            cameraContext?.debugLogger?.logPlugin(
                name,
                "pip_enabled",
                mapOf("position" to pipPosition.name, "size" to pipSize.name)
            )

            Log.i(TAG, "PiP mode enabled successfully")
            true

        } catch (e: Exception) {
            Log.e(TAG, "Failed to enable PiP mode", e)
            isPiPEnabled = false
            false
        }
    }

    /**
     * Disable PiP mode
     */
    fun disablePiP() {
        if (!isPiPEnabled) {
            return
        }

        Log.i(TAG, "Disabling PiP mode")

        stopPiP()
        isPiPEnabled = false
        saveSettings()

        cameraContext?.debugLogger?.logPlugin(
            name,
            "pip_disabled",
            emptyMap()
        )
    }

    /**
     * Toggle PiP mode
     */
    suspend fun togglePiP(): Boolean {
        return if (isPiPEnabled) {
            disablePiP()
            false
        } else {
            enablePiP()
        }
    }

    /**
     * Set PiP position
     */
    fun setPiPPosition(position: PiPPosition) {
        if (pipPosition != position) {
            pipPosition = position
            pipOverlayView?.setPiPPosition(position)
            saveSettings()

            Log.i(TAG, "PiP position changed to: $position")

            cameraContext?.debugLogger?.logPlugin(
                name,
                "pip_position_changed",
                mapOf("newPosition" to position.name)
            )
        }
    }

    /**
     * Set PiP size
     */
    fun setPiPSize(size: PiPSize) {
        if (pipSize != size) {
            pipSize = size
            pipOverlayView?.setPiPSize(size)
            saveSettings()

            Log.i(TAG, "PiP size changed to: $size")

            cameraContext?.debugLogger?.logPlugin(
                name,
                "pip_size_changed",
                mapOf("newSize" to size.name)
            )
        }
    }

    /**
     * Swap main and PiP cameras
     */
    suspend fun swapCameras(): Boolean {
        if (!isPiPEnabled) {
            return false
        }

        return try {
            dualCameraManager?.swapCameras()
            pipOverlayView?.animateSwap()

            Log.i(TAG, "Cameras swapped in PiP mode")

            cameraContext?.debugLogger?.logPlugin(
                name,
                "cameras_swapped",
                emptyMap()
            )

            true

        } catch (e: Exception) {
            Log.e(TAG, "Failed to swap cameras", e)
            false
        }
    }

    /**
     * Check if PiP is currently enabled
     */
    fun isPiPActive(): Boolean = isPiPEnabled

    /**
     * Get current PiP configuration
     */
    fun getPiPConfig(): Map<String, Any> {
        return mapOf(
            "enabled" to isPiPEnabled,
            "position" to pipPosition.name,
            "size" to pipSize.name,
            "available" to (dualCameraManager?.isDualCameraAvailable() ?: false)
        )
    }

    override fun cleanup() {
        Log.i(TAG, "Cleaning up PiPPlugin")

        stopPiP()
        dualCameraManager?.cleanup()
        dualCameraManager = null
        pipOverlayView = null
        cameraContext = null
    }

    private fun stopPiP() {
        dualCameraManager?.stopDualCameras()
        pipOverlayView?.hidePiP()
    }

    private fun loadSettings(context: CameraContext) {
        val settings = context.settingsManager

        try {
            isPiPEnabled = settings.getPluginSetting(name, "pipEnabled", "false").toBoolean()

            val positionString = settings.getPluginSetting(name, "pipPosition", PiPPosition.TOP_RIGHT.name)
            pipPosition = PiPPosition.valueOf(positionString)

            val sizeString = settings.getPluginSetting(name, "pipSize", PiPSize.SMALL.name)
            pipSize = PiPSize.valueOf(sizeString)

            Log.i(TAG, "Loaded settings: enabled=$isPiPEnabled, position=$pipPosition, size=$pipSize")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load settings, using defaults", e)
            isPiPEnabled = false
            pipPosition = PiPPosition.TOP_RIGHT
            pipSize = PiPSize.SMALL
        }
    }

    private fun saveSettings() {
        val settings = cameraContext?.settingsManager ?: return

        settings.setPluginSetting(name, "pipEnabled", isPiPEnabled.toString())
        settings.setPluginSetting(name, "pipPosition", pipPosition.name)
        settings.setPluginSetting(name, "pipSize", pipSize.name)
    }

    companion object {
        private const val TAG = "PiPPlugin"
    }
}