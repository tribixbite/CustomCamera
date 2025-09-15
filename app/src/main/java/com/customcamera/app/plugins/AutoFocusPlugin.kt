package com.customcamera.app.plugins

import android.util.Log
import android.view.MotionEvent
import android.view.View
import androidx.camera.core.*
import androidx.camera.view.PreviewView
import androidx.lifecycle.lifecycleScope
import com.customcamera.app.engine.CameraContext
import com.customcamera.app.engine.plugins.ControlPlugin
import com.customcamera.app.engine.plugins.ControlResult
import com.customcamera.app.engine.plugins.UIEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * AutoFocus plugin that provides both continuous autofocus and tap-to-focus functionality.
 * Demonstrates the plugin system with real camera control operations.
 */
class AutoFocusPlugin : ControlPlugin() {

    override val name: String = "AutoFocus"
    override val version: String = "1.0.0"
    override val priority: Int = 10 // High priority for focus operations

    private var cameraContext: CameraContext? = null
    private var currentCamera: Camera? = null
    private var previewView: PreviewView? = null

    // Focus settings
    private var autoFocusMode: AutoFocusMode = AutoFocusMode.CONTINUOUS
    private var tapToFocusEnabled: Boolean = true
    private var focusLockEnabled: Boolean = false

    // Focus state tracking
    private var isFocusing: Boolean = false
    private var lastFocusTime: Long = 0
    private var focusLockActive: Boolean = false

    enum class AutoFocusMode {
        CONTINUOUS, // Continuous autofocus
        MANUAL,     // Manual focus control only
        SINGLE      // Single shot autofocus on demand
    }

    override suspend fun initialize(context: CameraContext) {
        this.cameraContext = context
        Log.i(TAG, "AutoFocusPlugin initialized")

        // Load settings from settings manager
        loadSettings(context)

        // Log current focus configuration
        context.debugLogger.logPlugin(
            name,
            "initialized",
            mapOf(
                "autoFocusMode" to autoFocusMode.name,
                "tapToFocusEnabled" to tapToFocusEnabled,
                "focusLockEnabled" to focusLockEnabled
            )
        )
    }

    override suspend fun onCameraReady(camera: Camera) {
        currentCamera = camera
        Log.i(TAG, "Camera ready, configuring autofocus")

        // Apply initial autofocus settings
        val result = applyControls(camera)
        cameraContext?.debugLogger?.logPlugin(
            name,
            "camera_ready",
            mapOf(
                "focusMode" to autoFocusMode.name,
                "configResult" to result.toString()
            )
        )
    }

    override suspend fun onCameraReleased(camera: Camera) {
        Log.i(TAG, "Camera released, cleaning up focus controls")
        currentCamera = null
        previewView = null
        isFocusing = false
        focusLockActive = false
    }

    override suspend fun applyControls(camera: Camera): ControlResult {
        return try {
            when (autoFocusMode) {
                AutoFocusMode.CONTINUOUS -> {
                    // Enable continuous autofocus by creating a center focus point
                    val centerPoint = previewView?.let { preview ->
                        val factory = preview.meteringPointFactory
                        factory.createPoint(preview.width / 2f, preview.height / 2f)
                    }

                    if (centerPoint != null) {
                        val action = FocusMeteringAction.Builder(centerPoint)
                            .addPoint(centerPoint, FocusMeteringAction.FLAG_AF)
                            .build()
                        camera.cameraControl.startFocusAndMetering(action)
                    }
                }
                AutoFocusMode.MANUAL -> {
                    // Manual focus - disable continuous AF
                    camera.cameraControl.cancelFocusAndMetering()
                }
                AutoFocusMode.SINGLE -> {
                    // Single shot - will be triggered manually
                    camera.cameraControl.cancelFocusAndMetering()
                }
            }

            Log.i(TAG, "Applied autofocus controls: $autoFocusMode")
            ControlResult.Success("Autofocus configured: $autoFocusMode")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply autofocus controls", e)
            ControlResult.Failure("Failed to configure autofocus: ${e.message}", e)
        }
    }

    override suspend fun resetControls(camera: Camera): ControlResult {
        return try {
            // Reset to continuous autofocus
            autoFocusMode = AutoFocusMode.CONTINUOUS
            focusLockActive = false
            applyControls(camera)
        } catch (e: Exception) {
            ControlResult.Failure("Failed to reset focus controls: ${e.message}", e)
        }
    }

    override fun getCurrentSettings(): Map<String, Any> {
        return mapOf(
            "autoFocusMode" to autoFocusMode.name,
            "tapToFocusEnabled" to tapToFocusEnabled,
            "focusLockEnabled" to focusLockEnabled,
            "isFocusing" to isFocusing,
            "focusLockActive" to focusLockActive,
            "lastFocusTime" to lastFocusTime
        )
    }

    /**
     * Set the PreviewView for tap-to-focus functionality
     */
    fun setPreviewView(previewView: PreviewView) {
        this.previewView = previewView

        if (tapToFocusEnabled) {
            setupTapToFocus(previewView)
        }

        Log.i(TAG, "PreviewView set for tap-to-focus")
    }

    /**
     * Perform tap-to-focus at specific coordinates
     */
    suspend fun performTapToFocus(x: Float, y: Float): ControlResult {
        val camera = currentCamera ?: return ControlResult.Failure("No camera available")
        val preview = previewView ?: return ControlResult.Failure("No preview view set")

        if (!tapToFocusEnabled) {
            return ControlResult.Failure("Tap-to-focus is disabled")
        }

        return try {
            isFocusing = true
            lastFocusTime = System.currentTimeMillis()

            // Create metering point from touch coordinates
            val factory = preview.meteringPointFactory
            val point = factory.createPoint(x, y)

            // Create focus and metering action
            val action = FocusMeteringAction.Builder(point)
                .addPoint(point, FocusMeteringAction.FLAG_AF)
                .addPoint(point, FocusMeteringAction.FLAG_AE)
                .setAutoCancelDuration(5, TimeUnit.SECONDS)
                .build()

            // Start focus and metering
            val result = camera.cameraControl.startFocusAndMetering(action)

            // Log the operation
            cameraContext?.debugLogger?.logPlugin(
                name,
                "tap_to_focus",
                mapOf(
                    "x" to x,
                    "y" to y,
                    "timestamp" to lastFocusTime
                )
            )

            // Wait for result and update state
            try {
                result.get()
                isFocusing = false
                Log.i(TAG, "Tap-to-focus completed successfully")
                ControlResult.Success("Focus completed at ($x, $y)")
            } catch (e: Exception) {
                isFocusing = false
                Log.w(TAG, "Tap-to-focus failed", e)
                ControlResult.Failure("Focus failed: ${e.message}", e)
            }

        } catch (e: Exception) {
            isFocusing = false
            Log.e(TAG, "Error performing tap-to-focus", e)
            ControlResult.Failure("Tap-to-focus error: ${e.message}", e)
        }
    }

    /**
     * Lock focus at current position
     */
    suspend fun lockFocus(): ControlResult {
        val camera = currentCamera ?: return ControlResult.Failure("No camera available")

        return try {
            if (focusLockActive) {
                // Unlock focus
                camera.cameraControl.cancelFocusAndMetering()
                focusLockActive = false
                Log.i(TAG, "Focus unlocked")
                ControlResult.Success("Focus unlocked")
            } else {
                // Lock current focus
                val center = previewView?.let { preview ->
                    val factory = preview.meteringPointFactory
                    factory.createPoint(preview.width / 2f, preview.height / 2f)
                }

                if (center != null) {
                    val action = FocusMeteringAction.Builder(center)
                        .addPoint(center, FocusMeteringAction.FLAG_AF)
                        .disableAutoCancel() // Keep focus locked
                        .build()

                    camera.cameraControl.startFocusAndMetering(action)
                    focusLockActive = true
                    Log.i(TAG, "Focus locked")
                    ControlResult.Success("Focus locked")
                } else {
                    ControlResult.Failure("Could not determine focus point")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling focus lock", e)
            ControlResult.Failure("Focus lock error: ${e.message}", e)
        }
    }

    /**
     * Set autofocus mode
     */
    fun setAutoFocusMode(mode: AutoFocusMode) {
        autoFocusMode = mode
        cameraContext?.settingsManager?.setPluginSetting(name, "autoFocusMode", mode.name)

        // Apply new mode if camera is ready
        currentCamera?.let { camera ->
            cameraContext?.debugLogger?.let { logger ->
                logger.logPlugin(
                    name,
                    "mode_changed",
                    mapOf("newMode" to mode.name)
                )
            }

            // Apply in background
            // Note: In production, this would be done with proper coroutine scope
            try {
                kotlinx.coroutines.runBlocking {
                    applyControls(camera)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to apply new focus mode", e)
            }
        }

        Log.i(TAG, "Autofocus mode changed to: $mode")
    }

    /**
     * Enable or disable tap-to-focus
     */
    fun setTapToFocusEnabled(enabled: Boolean) {
        tapToFocusEnabled = enabled
        cameraContext?.settingsManager?.setPluginSetting(name, "tapToFocusEnabled", enabled.toString())

        previewView?.let { preview ->
            if (enabled) {
                setupTapToFocus(preview)
            } else {
                preview.setOnTouchListener(null)
            }
        }

        Log.i(TAG, "Tap-to-focus ${if (enabled) "enabled" else "disabled"}")
    }

    override fun cleanup() {
        Log.i(TAG, "Cleaning up AutoFocusPlugin")

        currentCamera = null
        previewView?.setOnTouchListener(null)
        previewView = null
        cameraContext = null

        isFocusing = false
        focusLockActive = false
    }

    private fun setupTapToFocus(previewView: PreviewView) {
        previewView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN && tapToFocusEnabled && !isFocusing) {
                // Launch focus operation in background
                cameraContext?.let { context ->
                    if (context.context is androidx.lifecycle.LifecycleOwner) {
                        (context.context as androidx.lifecycle.LifecycleOwner).lifecycleScope.launch {
                            performTapToFocus(event.x, event.y)
                        }
                    }
                }
                true
            } else {
                false
            }
        }
    }

    private fun loadSettings(context: CameraContext) {
        val settings = context.settingsManager

        try {
            // Load autofocus mode
            val modeString = settings.getPluginSetting(name, "autoFocusMode", AutoFocusMode.CONTINUOUS.name)
            autoFocusMode = AutoFocusMode.valueOf(modeString)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load autofocus mode setting, using default", e)
            autoFocusMode = AutoFocusMode.CONTINUOUS
        }

        // Load tap-to-focus setting
        try {
            val tapToFocusString = settings.getPluginSetting(name, "tapToFocusEnabled", "true")
            tapToFocusEnabled = tapToFocusString.toBoolean()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load tap-to-focus setting, using default", e)
            tapToFocusEnabled = true
        }

        Log.i(TAG, "Loaded settings: mode=$autoFocusMode, tapToFocus=$tapToFocusEnabled")
    }

    companion object {
        private const val TAG = "AutoFocusPlugin"
    }
}