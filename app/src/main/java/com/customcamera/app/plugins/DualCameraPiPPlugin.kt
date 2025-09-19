package com.customcamera.app.plugins

import android.graphics.*
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.camera.core.*
import androidx.camera.core.Camera
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import com.customcamera.app.engine.CameraContext
import com.customcamera.app.engine.plugins.UIPlugin
import com.customcamera.app.pip.PiPOverlayView
import com.customcamera.app.pip.DualCameraCoordinator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * DualCameraPiPPlugin provides picture-in-picture functionality for dual camera recording.
 * Allows users to record from both front and back cameras simultaneously with
 * advanced positioning, sizing, and control features.
 */
class DualCameraPiPPlugin : UIPlugin() {

    override val name: String = "DualCameraPiP"
    override val version: String = "1.0.0"
    override val priority: Int = 30 // High priority for UI management

    private var cameraContext: CameraContext? = null
    private var mainPreviewView: PreviewView? = null
    private var pipOverlayView: PiPOverlayView? = null
    private var dualCameraCoordinator: DualCameraCoordinator? = null

    // PiP state management
    private val _isPiPEnabled = MutableStateFlow(false)
    private val _pipPosition = MutableStateFlow(PiPPosition.TOP_RIGHT)
    private val _pipSize = MutableStateFlow(PiPSize.MEDIUM)
    private val _mainCamera = MutableStateFlow(0) // Main camera index
    private val _pipCamera = MutableStateFlow(1) // PiP camera index

    // Public state flows
    val isPiPEnabled: StateFlow<Boolean> = _isPiPEnabled.asStateFlow()
    val pipPosition: StateFlow<PiPPosition> = _pipPosition.asStateFlow()
    val pipSize: StateFlow<PiPSize> = _pipSize.asStateFlow()
    val mainCamera: StateFlow<Int> = _mainCamera.asStateFlow()
    val pipCamera: StateFlow<Int> = _pipCamera.asStateFlow()

    // Configuration
    private var isDraggable: Boolean = true
    private var autoSwapOnSwitch: Boolean = true
    private var pipOpacity: Float = 1.0f
    private var snapToCorners: Boolean = true

    override suspend fun initialize(context: CameraContext) {
        this.cameraContext = context
        Log.i(TAG, "DualCameraPiPPlugin initialized")

        // Create dual camera coordinator
        dualCameraCoordinator = DualCameraCoordinator(
            context = context.context,
            lifecycleOwner = context.context as LifecycleOwner
        )

        loadSettings(context)

        context.debugLogger.logPlugin(
            name,
            "initialized",
            mapOf(
                "pipEnabled" to _isPiPEnabled.value,
                "position" to _pipPosition.value.name,
                "size" to _pipSize.value.name,
                "mainCamera" to _mainCamera.value,
                "pipCamera" to _pipCamera.value
            )
        )
    }

    override suspend fun onCameraReady(camera: Camera) {
        Log.i(TAG, "Camera ready for dual PiP setup")

        cameraContext?.debugLogger?.logPlugin(
            name,
            "camera_ready",
            mapOf("cameraId" to camera.cameraInfo.toString())
        )
    }

    override fun createUIView(context: CameraContext): View? {
        // Return null as we manage UI through the setupMainPreview method
        return null
    }

    /**
     * Set up the main preview view that will host the PiP overlay
     */
    fun setupMainPreview(previewView: PreviewView) {
        this.mainPreviewView = previewView

        if (_isPiPEnabled.value) {
            createPiPOverlay()
        }

        Log.i(TAG, "Main preview view configured for PiP")
    }

    /**
     * Enable or disable Picture-in-Picture mode
     */
    fun setPiPEnabled(enabled: Boolean) {
        if (_isPiPEnabled.value != enabled) {
            _isPiPEnabled.value = enabled

            if (enabled) {
                enablePiPMode()
            } else {
                disablePiPMode()
            }

            saveSettings()

            Log.i(TAG, "PiP mode ${if (enabled) "enabled" else "disabled"}")

            cameraContext?.debugLogger?.logPlugin(
                name,
                "pip_toggled",
                mapOf("enabled" to enabled)
            )
        }
    }

    /**
     * Toggle Picture-in-Picture mode on/off
     */
    fun togglePiP(): Boolean {
        val newState = !_isPiPEnabled.value
        setPiPEnabled(newState)
        return newState
    }

    /**
     * Set PiP overlay position
     */
    fun setPiPPosition(position: PiPPosition) {
        if (_pipPosition.value != position) {
            _pipPosition.value = position
            updatePiPLayout()
            saveSettings()

            Log.i(TAG, "PiP position changed to: ${position.name}")
        }
    }

    /**
     * Set PiP overlay size
     */
    fun setPiPSize(size: PiPSize) {
        if (_pipSize.value != size) {
            _pipSize.value = size
            updatePiPLayout()
            saveSettings()

            Log.i(TAG, "PiP size changed to: ${size.name}")
        }
    }

    /**
     * Swap main and PiP cameras
     */
    fun swapCameras() {
        val currentMain = _mainCamera.value
        val currentPiP = _pipCamera.value

        _mainCamera.value = currentPiP
        _pipCamera.value = currentMain

        // Apply the camera switch
        if (_isPiPEnabled.value) {
            applyCameraConfiguration()
        }

        saveSettings()

        Log.i(TAG, "Cameras swapped: main=$currentPiP, pip=$currentMain")

        cameraContext?.debugLogger?.logPlugin(
            name,
            "cameras_swapped",
            mapOf(
                "newMain" to _mainCamera.value,
                "newPiP" to _pipCamera.value
            )
        )
    }

    /**
     * Set specific cameras for main and PiP
     */
    fun setCameras(mainCameraIndex: Int, pipCameraIndex: Int) {
        _mainCamera.value = mainCameraIndex
        _pipCamera.value = pipCameraIndex

        if (_isPiPEnabled.value) {
            applyCameraConfiguration()
        }

        saveSettings()

        Log.i(TAG, "Cameras set: main=$mainCameraIndex, pip=$pipCameraIndex")
    }

    /**
     * Configure PiP behavior settings
     */
    fun configurePiP(
        draggable: Boolean = this.isDraggable,
        autoSwap: Boolean = this.autoSwapOnSwitch,
        opacity: Float = this.pipOpacity,
        snapToCorners: Boolean = this.snapToCorners
    ) {
        this.isDraggable = draggable
        this.autoSwapOnSwitch = autoSwap
        this.pipOpacity = opacity.coerceIn(0.1f, 1.0f)
        this.snapToCorners = snapToCorners

        // Apply to existing overlay if present
        pipOverlayView?.apply {
            setDraggable(this@DualCameraPiPPlugin.isDraggable)
            setOpacity(this@DualCameraPiPPlugin.pipOpacity)
            setSnapToCorners(this@DualCameraPiPPlugin.snapToCorners)
        }

        saveSettings()

        Log.i(TAG, "PiP configuration updated: draggable=$draggable, opacity=$opacity")
    }

    /**
     * Get current PiP status and statistics
     */
    fun getPiPStatus(): Map<String, Any> {
        return mapOf(
            "enabled" to _isPiPEnabled.value,
            "position" to _pipPosition.value.name,
            "size" to _pipSize.value.name,
            "mainCamera" to _mainCamera.value,
            "pipCamera" to _pipCamera.value,
            "draggable" to isDraggable,
            "autoSwap" to autoSwapOnSwitch,
            "opacity" to pipOpacity,
            "snapToCorners" to snapToCorners,
            "overlayCreated" to (pipOverlayView != null),
            "coordinatorActive" to (dualCameraCoordinator?.isActive() == true)
        )
    }

    override fun cleanup() {
        Log.i(TAG, "Cleaning up DualCameraPiPPlugin")

        disablePiPMode()
        dualCameraCoordinator?.cleanup()

        pipOverlayView = null
        mainPreviewView = null
        dualCameraCoordinator = null
        cameraContext = null
    }

    /**
     * Enable PiP mode by creating overlay and setting up dual cameras
     */
    private fun enablePiPMode() {
        if (mainPreviewView == null) {
            Log.w(TAG, "Cannot enable PiP: main preview view not set")
            return
        }

        createPiPOverlay()
        applyCameraConfiguration()

        cameraContext?.debugLogger?.logPlugin(
            name,
            "pip_enabled",
            mapOf(
                "mainCamera" to _mainCamera.value,
                "pipCamera" to _pipCamera.value
            )
        )
    }

    /**
     * Disable PiP mode by removing overlay and reverting to single camera
     */
    private fun disablePiPMode() {
        removePiPOverlay()
        dualCameraCoordinator?.stopPiPCamera()

        cameraContext?.debugLogger?.logPlugin(
            name,
            "pip_disabled",
            emptyMap()
        )
    }

    /**
     * Create the PiP overlay view
     */
    private fun createPiPOverlay() {
        val previewView = mainPreviewView ?: return
        val parent = previewView.parent as? ViewGroup ?: return

        // Create PiP overlay if it doesn't exist
        if (pipOverlayView == null) {
            pipOverlayView = PiPOverlayView(cameraContext!!.context).apply {
                setPosition(_pipPosition.value)
                setSize(_pipSize.value)
                setDraggable(isDraggable)
                setOpacity(pipOpacity)
                setSnapToCorners(snapToCorners)

                // Set up drag and position change callbacks
                setOnPositionChangedListener { newPosition ->
                    _pipPosition.value = newPosition
                    saveSettings()
                }

                setOnSwapRequestListener {
                    swapCameras()
                }
            }

            // Add to parent view
            parent.addView(pipOverlayView)

            Log.i(TAG, "PiP overlay created and added to view hierarchy")
        }
    }

    /**
     * Remove the PiP overlay view
     */
    private fun removePiPOverlay() {
        pipOverlayView?.let { overlay ->
            val parent = overlay.parent as? ViewGroup
            parent?.removeView(overlay)
            pipOverlayView = null

            Log.i(TAG, "PiP overlay removed from view hierarchy")
        }
    }

    /**
     * Update PiP overlay layout based on current position and size
     */
    private fun updatePiPLayout() {
        pipOverlayView?.apply {
            setPosition(_pipPosition.value)
            setSize(_pipSize.value)
        }
    }

    /**
     * Apply current camera configuration to coordinator
     */
    private fun applyCameraConfiguration() {
        dualCameraCoordinator?.let { coordinator ->
            coordinator.setupDualCamera(
                mainCameraIndex = _mainCamera.value,
                pipCameraIndex = _pipCamera.value,
                pipPreviewView = pipOverlayView?.getPreviewView()
            )
        }
    }

    /**
     * Load settings from preferences
     */
    private fun loadSettings(context: CameraContext) {
        val settings = context.settingsManager

        try {
            _isPiPEnabled.value = settings.getPluginSetting(name, "enabled", "false").toBoolean()
            _pipPosition.value = PiPPosition.valueOf(
                settings.getPluginSetting(name, "position", PiPPosition.TOP_RIGHT.name)
            )
            _pipSize.value = PiPSize.valueOf(
                settings.getPluginSetting(name, "size", PiPSize.MEDIUM.name)
            )
            _mainCamera.value = settings.getPluginSetting(name, "mainCamera", "0").toInt()
            _pipCamera.value = settings.getPluginSetting(name, "pipCamera", "1").toInt()

            isDraggable = settings.getPluginSetting(name, "draggable", "true").toBoolean()
            autoSwapOnSwitch = settings.getPluginSetting(name, "autoSwap", "true").toBoolean()
            pipOpacity = settings.getPluginSetting(name, "opacity", "1.0").toFloat()
            snapToCorners = settings.getPluginSetting(name, "snapToCorners", "true").toBoolean()

            Log.i(TAG, "Settings loaded: PiP=${_isPiPEnabled.value}, position=${_pipPosition.value}")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load settings, using defaults", e)
        }
    }

    /**
     * Save current settings to preferences
     */
    private fun saveSettings() {
        val settings = cameraContext?.settingsManager ?: return

        settings.setPluginSetting(name, "enabled", _isPiPEnabled.value.toString())
        settings.setPluginSetting(name, "position", _pipPosition.value.name)
        settings.setPluginSetting(name, "size", _pipSize.value.name)
        settings.setPluginSetting(name, "mainCamera", _mainCamera.value.toString())
        settings.setPluginSetting(name, "pipCamera", _pipCamera.value.toString())
        settings.setPluginSetting(name, "draggable", isDraggable.toString())
        settings.setPluginSetting(name, "autoSwap", autoSwapOnSwitch.toString())
        settings.setPluginSetting(name, "opacity", pipOpacity.toString())
        settings.setPluginSetting(name, "snapToCorners", snapToCorners.toString())
    }

    companion object {
        private const val TAG = "DualCameraPiPPlugin"
    }
}

/**
 * PiP overlay position options
 */
enum class PiPPosition {
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT,
    CENTER_LEFT,
    CENTER_RIGHT,
    TOP_CENTER,
    BOTTOM_CENTER
}

/**
 * PiP overlay size options
 */
enum class PiPSize(val widthPercent: Float, val heightPercent: Float) {
    SMALL(0.15f, 0.20f),
    MEDIUM(0.25f, 0.30f),
    LARGE(0.35f, 0.40f),
    EXTRA_LARGE(0.45f, 0.50f)
}