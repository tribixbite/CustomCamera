package com.customcamera.app.engine.plugins

import androidx.camera.core.Camera
import androidx.camera.core.ImageProxy
import com.customcamera.app.engine.CameraContext

/**
 * Base class for all camera plugins. Plugins extend the camera functionality
 * by providing specialized features like focus control, image processing,
 * UI overlays, and analysis capabilities.
 */
abstract class CameraPlugin {

    /**
     * Unique name identifying this plugin
     */
    abstract val name: String

    /**
     * Plugin version for compatibility tracking
     */
    open val version: String = "1.0.0"

    /**
     * Plugin priority for execution order (lower numbers = higher priority)
     */
    open val priority: Int = 100

    /**
     * Whether this plugin is currently enabled
     */
    var isEnabled: Boolean = true
        protected set

    /**
     * Initialize the plugin with camera context
     * Called when the plugin is registered with the plugin manager
     */
    abstract suspend fun initialize(context: CameraContext)

    /**
     * Called when a camera becomes ready for use
     * Plugins can set up camera-specific functionality here
     */
    abstract suspend fun onCameraReady(camera: Camera)

    /**
     * Called when camera is being switched or unbound
     * Plugins should clean up camera-specific resources
     */
    open suspend fun onCameraReleased(camera: Camera) {
        // Default implementation - no action needed
    }

    /**
     * Process a camera frame (for analysis plugins)
     * Only called if the plugin implements ProcessingPlugin
     */
    open suspend fun processFrame(image: ImageProxy): ProcessingResult? {
        return null
    }

    /**
     * Enable the plugin
     */
    fun enable() {
        isEnabled = true
        onPluginEnabled()
    }

    /**
     * Disable the plugin
     */
    fun disable() {
        isEnabled = false
        onPluginDisabled()
    }

    /**
     * Called when plugin is enabled
     */
    protected open fun onPluginEnabled() {
        // Override in subclasses if needed
    }

    /**
     * Called when plugin is disabled
     */
    protected open fun onPluginDisabled() {
        // Override in subclasses if needed
    }

    /**
     * Clean up plugin resources
     * Called when plugin is unregistered or engine is destroyed
     */
    abstract fun cleanup()

    override fun toString(): String {
        return "Plugin(name='$name', version='$version', enabled=$isEnabled)"
    }
}

/**
 * Specialized plugin for image/frame processing
 * Plugins that analyze camera frames should extend this class
 */
abstract class ProcessingPlugin : CameraPlugin() {

    /**
     * Process a camera frame and return analysis results
     * This method will be called for every camera frame when enabled
     */
    abstract override suspend fun processFrame(image: ImageProxy): ProcessingResult

    /**
     * Configure processing settings like target resolution, analysis interval, etc.
     */
    open fun configureProcessing(): ProcessingConfig {
        return ProcessingConfig()
    }
}

/**
 * Specialized plugin for UI overlays and controls
 * Plugins that add UI elements should extend this class
 */
abstract class UIPlugin : CameraPlugin() {

    /**
     * Create and return the UI view for this plugin
     * Called when the plugin needs to add UI elements to the camera interface
     */
    abstract fun createUIView(context: CameraContext): android.view.View?

    /**
     * Called when the UI view is being removed or destroyed
     * Plugins should release any references to the view here to prevent memory leaks
     *
     * IMPORTANT: This prevents IllegalStateException when createUIView is called
     * multiple times and prevents memory leaks from retained view references
     */
    open fun destroyUIView() {
        // Default implementation - override in subclasses to release view references
    }

    /**
     * Update UI based on camera state changes
     */
    open fun updateUI(camera: Camera) {
        // Override in subclasses if needed
    }

    /**
     * Handle UI interactions and events
     */
    open fun onUIEvent(event: UIEvent) {
        // Override in subclasses if needed
    }
}

/**
 * Specialized plugin for camera control operations
 * Plugins that control camera settings should extend this class
 */
abstract class ControlPlugin : CameraPlugin() {

    /**
     * Apply control settings to the camera
     */
    abstract suspend fun applyControls(camera: Camera): ControlResult

    /**
     * Reset controls to default values
     */
    open suspend fun resetControls(camera: Camera): ControlResult {
        return ControlResult.Success("Controls reset to defaults")
    }

    /**
     * Get current control values
     */
    abstract fun getCurrentSettings(): Map<String, Any>
}

/**
 * Result class for frame processing operations
 */
sealed class ProcessingResult {
    data class Success(
        val data: Map<String, Any> = emptyMap(),
        val metadata: ProcessingMetadata? = null
    ) : ProcessingResult()

    data class Failure(
        val error: String,
        val exception: Throwable? = null
    ) : ProcessingResult()

    object Skip : ProcessingResult()
}

/**
 * Result class for control operations
 */
sealed class ControlResult {
    data class Success(val message: String) : ControlResult()
    data class Failure(val error: String, val exception: Throwable? = null) : ControlResult()
}

/**
 * Configuration for processing plugins
 */
data class ProcessingConfig(
    val targetResolution: android.util.Size? = null,
    val processingInterval: Long = 16L, // ~60 FPS
    val enableMetadata: Boolean = true,
    val maxProcessingTime: Long = 33L // Max 33ms per frame
)

/**
 * Metadata for processing results
 */
data class ProcessingMetadata(
    val timestamp: Long = System.currentTimeMillis(),
    val processingTimeMs: Long = 0L,
    val frameNumber: Long = 0L,
    val imageSize: android.util.Size? = null,
    val additionalData: Map<String, Any> = emptyMap()
)

/**
 * UI events that can be handled by UI plugins
 */
sealed class UIEvent {
    object Show : UIEvent()
    object Hide : UIEvent()
    data class Touch(val x: Float, val y: Float) : UIEvent()
    data class Gesture(val type: String, val data: Map<String, Any>) : UIEvent()
    data class StateChange(val state: String, val data: Any?) : UIEvent()
}