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
import com.customcamera.app.pip.ConcurrentCameraCapability
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.*
import android.widget.Toast

/**
 * DualCameraPiPPlugin provides picture-in-picture functionality for dual camera recording.
 * Allows users to record from both front and back cameras simultaneously with
 * advanced positioning, sizing, and control features.
 */
class DualCameraPiPPlugin : UIPlugin() {

    override val name: String = "DualCameraPiP"
    override val displayName: String = "Dual Camera PiP"
    override val description: String = "Picture-in-picture with dual cameras"
    override val iconResId: Int = com.customcamera.app.R.drawable.ic_pip
    override val category: com.customcamera.app.engine.plugins.PluginCategory = com.customcamera.app.engine.plugins.PluginCategory.CAPTURE
    override val userToggleable: Boolean = true
    override val version: String = "1.0.0"
    override val priority: Int = 30 // High priority for UI management

    private var cameraContext: CameraContext? = null
    private var mainPreviewView: PreviewView? = null
    private var pipOverlayView: PiPOverlayView? = null
    private var dualCameraCoordinator: DualCameraCoordinator? = null

    // PiP state management
    private val _isPiPEnabled = MutableStateFlow(false)
    private val _isDualCameraSupported = MutableStateFlow(false)
    private val _pipPosition = MutableStateFlow(PiPPosition.TOP_RIGHT)
    private val _pipSize = MutableStateFlow(PiPSize.MEDIUM)
    private val _mainCamera = MutableStateFlow(0) // Main camera index
    private val _pipCamera = MutableStateFlow(1) // PiP camera index

    // Public state flows
    val isPiPEnabled: StateFlow<Boolean> = _isPiPEnabled.asStateFlow()
    val isDualCameraSupported: StateFlow<Boolean> = _isDualCameraSupported.asStateFlow()
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

        // **CRITICAL**: Share the same camera provider as CameraEngine
        val provider = context.cameraEngine?.getProvider()
        if (provider != null) {
            dualCameraCoordinator?.setProvider(provider)
            Log.i(TAG, "✅ Shared camera provider set for DualCameraCoordinator")

            // Check concurrent camera support
            val capability = ConcurrentCameraCapability(context.context)
            val concurrentInfo = capability.checkSupport(provider)

            if (!concurrentInfo.isSupported) {
                Log.w(TAG, "⚠️ Concurrent cameras not supported: ${concurrentInfo.errorMessage}")
                _isDualCameraSupported.value = false
            } else {
                _isDualCameraSupported.value = true
                Log.i(TAG, "✅ Concurrent cameras supported: ${concurrentInfo.availableCombinations.size} combinations")

                // Use recommended combination if available
                concurrentInfo.recommendedCombination?.let { (main, pip) ->
                    _mainCamera.value = main
                    _pipCamera.value = pip
                    Log.i(TAG, "Using recommended camera combination: main=$main, pip=$pip")
                }
            }
        } else {
            Log.e(TAG, "❌ Camera provider not available from CameraEngine")
            _isDualCameraSupported.value = false
        }

        // Load camera indices from settings (may override recommended)
        val savedMain = context.settingsManager.defaultCameraIndex.value
        val savedPip = context.settingsManager.pipCameraIndex.value
        if (savedMain >= 0) _mainCamera.value = savedMain
        if (savedPip >= 0) _pipCamera.value = savedPip
        Log.i(TAG, "Loaded camera indices - Main: ${_mainCamera.value}, PiP: ${_pipCamera.value}")

        // Auto-select appropriate PiP camera if default doesn't exist
        validateAndFixCameraIndices()

        context.debugLogger.logPlugin(
            name,
            "initialized",
            mapOf(
                "pipEnabled" to _isPiPEnabled.value,
                "dualCameraSupported" to _isDualCameraSupported.value,
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

        // Apply the camera switch if PiP is enabled
        if (_isPiPEnabled.value) {
            val pipPreview = pipOverlayView?.getPreviewView()
            if (pipPreview != null && mainPreviewView != null) {
                // Switch to new concurrent configuration
                cameraContext?.cameraEngine?.switchToConcurrentMode(
                    mainCameraIndex = _mainCamera.value,
                    pipCameraIndex = _pipCamera.value,
                    mainPreviewView = mainPreviewView!!,
                    pipPreviewView = pipPreview,
                    onSuccess = {
                        Log.i(TAG, "✅ Camera swap successful")
                    },
                    onFailure = { exception ->
                        Log.e(TAG, "❌ Camera swap failed", exception)
                        // Revert the swap
                        _mainCamera.value = currentMain
                        _pipCamera.value = currentPiP
                    }
                )
            }
        }

        saveSettings()

        Log.i(TAG, "Cameras swapped: main=${_mainCamera.value}, pip=${_pipCamera.value}")

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
            val pipPreview = pipOverlayView?.getPreviewView()
            if (pipPreview != null && mainPreviewView != null) {
                // Switch to new concurrent configuration
                cameraContext?.cameraEngine?.switchToConcurrentMode(
                    mainCameraIndex = mainCameraIndex,
                    pipCameraIndex = pipCameraIndex,
                    mainPreviewView = mainPreviewView!!,
                    pipPreviewView = pipPreview,
                    onSuccess = {
                        Log.i(TAG, "✅ Camera configuration updated")
                    },
                    onFailure = { exception ->
                        Log.e(TAG, "❌ Failed to update camera configuration", exception)
                    }
                )
            }
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
     * Enable PiP mode by creating overlay and switching to concurrent camera mode
     */
    private fun enablePiPMode() {
        Log.i(TAG, "=== ENABLING PiP MODE ===")

        // Check concurrent camera support
        if (!_isDualCameraSupported.value) {
            Log.e(TAG, "❌ Cannot enable PiP: Concurrent cameras not supported on this device")
            Toast.makeText(
                cameraContext?.context,
                "Dual camera not supported on this device",
                Toast.LENGTH_LONG
            ).show()
            _isPiPEnabled.value = false
            return
        }

        if (mainPreviewView == null) {
            Log.e(TAG, "❌ Cannot enable PiP: main preview view not set")
            return
        }

        Log.i(TAG, "✅ Main preview view available")
        Log.i(TAG, "Creating PiP overlay...")
        createPiPOverlay()

        if (pipOverlayView == null) {
            Log.e(TAG, "❌ PiP overlay creation failed - overlay is null")
            return
        }

        Log.i(TAG, "✅ PiP overlay created successfully")

        val pipPreview = pipOverlayView?.getPreviewView()
        if (pipPreview == null) {
            Log.e(TAG, "❌ PiP PreviewView is null!")
            removePiPOverlay()
            return
        }

        Log.i(TAG, "✅ PiP PreviewView obtained: ${pipPreview.javaClass.simpleName}")

        // Ensure mainPreviewView is available
        if (mainPreviewView == null) {
            Log.e(TAG, "❌ Main PreviewView is null!")
            removePiPOverlay()
            return
        }

        // Request CameraEngine to switch to concurrent mode
        cameraContext?.cameraEngine?.switchToConcurrentMode(
            mainCameraIndex = _mainCamera.value,
            pipCameraIndex = _pipCamera.value,
            mainPreviewView = mainPreviewView!!,
            pipPreviewView = pipPreview,
            onSuccess = {
                Log.i(TAG, "✅ PiP mode enabled successfully")
                _isPiPEnabled.value = true

                cameraContext?.debugLogger?.logPlugin(
                    name,
                    "pip_enabled",
                    mapOf(
                        "mainCamera" to _mainCamera.value,
                        "pipCamera" to _pipCamera.value,
                        "overlayCreated" to true
                    )
                )
            },
            onFailure = { exception ->
                Log.e(TAG, "❌ Failed to enable PiP mode", exception)
                removePiPOverlay()
                _isPiPEnabled.value = false
                Toast.makeText(
                    cameraContext?.context,
                    "Failed to enable dual camera: ${exception.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        )

        Log.i(TAG, "=== PiP MODE ENABLE COMPLETE ===")
    }

    /**
     * Disable PiP mode by removing overlay and switching back to single camera
     */
    private fun disablePiPMode() {
        Log.i(TAG, "Disabling PiP mode...")
        removePiPOverlay()
        cameraContext?.cameraEngine?.switchToSingleMode()

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
        val previewView = mainPreviewView
        if (previewView == null) {
            Log.e(TAG, "Cannot create PiP overlay: mainPreviewView is null")
            return
        }

        val parent = previewView.parent as? ViewGroup
        if (parent == null) {
            Log.e(TAG, "Cannot create PiP overlay: parent ViewGroup is null")
            return
        }

        // Create PiP overlay if it doesn't exist
        if (pipOverlayView == null) {
            try {
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

                Log.i(TAG, "✅ PiP overlay created and added to view hierarchy (parent: ${parent.javaClass.simpleName})")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to create PiP overlay", e)
                pipOverlayView = null
            }
        } else {
            Log.i(TAG, "PiP overlay already exists, reusing")
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

    /**
     * Validate camera indices and fix them if invalid
     */
    private fun validateAndFixCameraIndices() {
        val cameraEngine = cameraContext?.cameraEngine
        if (cameraEngine == null) {
            Log.w(TAG, "CameraEngine not available for camera validation")
            return
        }

        val availableCameras = cameraEngine.availableCameras.value
        val cameraCount = availableCameras.size

        Log.i(TAG, "Validating camera indices. Available cameras: $cameraCount")

        // If we don't have at least 2 cameras, PiP won't work
        if (cameraCount < 2) {
            Log.w(TAG, "Not enough cameras for PiP (need 2, found $cameraCount)")
            _isPiPEnabled.value = false
            return
        }

        // Validate main camera index
        if (_mainCamera.value >= cameraCount || _mainCamera.value < 0) {
            Log.w(TAG, "Invalid main camera index ${_mainCamera.value}, resetting to 0")
            _mainCamera.value = 0
        }

        // Validate PiP camera index
        if (_pipCamera.value >= cameraCount || _pipCamera.value < 0 || _pipCamera.value == _mainCamera.value) {
            // Find the first available camera that's different from main
            val newPipCamera = (0 until cameraCount).firstOrNull { it != _mainCamera.value } ?: 1
            Log.w(TAG, "Invalid PiP camera index ${_pipCamera.value}, setting to $newPipCamera")
            _pipCamera.value = newPipCamera
        }

        Log.i(TAG, "Camera validation complete: main=${_mainCamera.value}, pip=${_pipCamera.value}")

        // Save the corrected values
        cameraContext?.let { saveSettings() }
    }

    /**
     * Get current PiP overlay position and size as normalized coordinates (0-1)
     * Used for compositing dual camera photos
     */
    fun getPiPOverlayRect(): RectF {
        val size = _pipSize.value
        val position = _pipPosition.value

        val width = size.widthPercent
        val height = size.heightPercent

        // Calculate normalized position based on PiPPosition enum
        val (left, top) = when (position) {
            PiPPosition.TOP_LEFT -> Pair(0.02f, 0.02f)
            PiPPosition.TOP_RIGHT -> Pair(0.98f - width, 0.02f)
            PiPPosition.BOTTOM_LEFT -> Pair(0.02f, 0.98f - height)
            PiPPosition.BOTTOM_RIGHT -> Pair(0.98f - width, 0.98f - height)
            PiPPosition.CENTER_LEFT -> Pair(0.02f, 0.5f - height / 2)
            PiPPosition.CENTER_RIGHT -> Pair(0.98f - width, 0.5f - height / 2)
            PiPPosition.TOP_CENTER -> Pair(0.5f - width / 2, 0.02f)
            PiPPosition.BOTTOM_CENTER -> Pair(0.5f - width / 2, 0.98f - height)
        }

        return RectF(left, top, left + width, top + height)
    }

    companion object : com.customcamera.app.engine.plugins.PluginProvider {
        private const val TAG = "DualCameraPiPPlugin"

        // PluginProvider implementation
        override val id: String = "dual_camera_pip"

        override val displayNameRes: Int = com.customcamera.app.R.string.plugin_dual_camera_pip_display_name

        override val descriptionRes: Int = com.customcamera.app.R.string.plugin_dual_camera_pip_description

        override val iconResId: Int = com.customcamera.app.R.drawable.ic_pip

        override val category: com.customcamera.app.engine.plugins.PluginCategory =
            com.customcamera.app.engine.plugins.PluginCategory.CAPTURE

        override val userToggleable: Boolean = true

        override fun isSupported(context: android.content.Context): Boolean {
            // TODO: Add device capability checking if needed
            return true
        }

        override fun create(dependencies: com.customcamera.app.engine.plugins.PluginDependencies): com.customcamera.app.engine.plugins.CameraPlugin {
            return DualCameraPiPPlugin()
        }
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