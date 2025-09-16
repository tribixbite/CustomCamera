package com.customcamera.app.camera2

import android.content.Context
import android.hardware.camera2.*
import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.ZoomState

/**
 * ZoomController provides pinch-to-zoom functionality
 * with Camera2 API integration for precise zoom control
 */
class ZoomController(
    private val context: Context
) {

    private var currentZoomRatio: Float = 1.0f
    private var minZoomRatio: Float = 1.0f
    private var maxZoomRatio: Float = 10.0f
    private var zoomStepSize: Float = 0.1f

    // Camera2 characteristics
    private var cameraManager: CameraManager? = null
    private var maxDigitalZoom: Float = 1.0f

    /**
     * Initialize zoom controller for specific camera
     */
    fun initialize(cameraId: String): Boolean {
        return try {
            cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val characteristics = cameraManager!!.getCameraCharacteristics(cameraId)

            // Extract zoom capabilities from Camera2
            maxDigitalZoom = characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1.0f
            maxZoomRatio = maxDigitalZoom.coerceAtMost(10.0f) // Limit to 10x for usability

            Log.i(TAG, "Zoom controller initialized for camera $cameraId")
            Log.i(TAG, "Max digital zoom: $maxDigitalZoom")
            Log.i(TAG, "Zoom range: ${minZoomRatio}x - ${maxZoomRatio}x")

            true

        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize zoom controller", e)
            false
        }
    }

    /**
     * Apply zoom ratio to camera
     */
    fun setZoomRatio(zoomRatio: Float, camera: Camera?): Boolean {
        return try {
            val clampedZoom = zoomRatio.coerceIn(minZoomRatio, maxZoomRatio)

            if (camera != null) {
                // Use CameraX zoom control
                camera.cameraControl.setZoomRatio(clampedZoom)
                currentZoomRatio = clampedZoom

                Log.d(TAG, "Zoom ratio set to: ${String.format("%.1f", clampedZoom)}x")
                true
            } else {
                Log.w(TAG, "Camera not available for zoom control")
                false
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to set zoom ratio", e)
            false
        }
    }

    /**
     * Process pinch gesture for zoom
     */
    fun processPinchGesture(scaleFactor: Float, camera: Camera?): Boolean {
        val newZoomRatio = (currentZoomRatio * scaleFactor).coerceIn(minZoomRatio, maxZoomRatio)

        return if (kotlin.math.abs(newZoomRatio - currentZoomRatio) > zoomStepSize) {
            setZoomRatio(newZoomRatio, camera)
        } else {
            false // No significant change
        }
    }

    /**
     * Get current zoom state
     */
    fun getCurrentZoomState(): ZoomState? {
        // This would typically come from the camera
        return null
    }

    /**
     * Get zoom capabilities
     */
    fun getZoomCapabilities(): Map<String, Any> {
        return mapOf(
            "currentZoomRatio" to currentZoomRatio,
            "minZoomRatio" to minZoomRatio,
            "maxZoomRatio" to maxZoomRatio,
            "maxDigitalZoom" to maxDigitalZoom,
            "zoomStepSize" to zoomStepSize,
            "zoomSupported" to (maxZoomRatio > minZoomRatio)
        )
    }

    /**
     * Reset zoom to 1x
     */
    fun resetZoom(camera: Camera?): Boolean {
        return setZoomRatio(1.0f, camera)
    }

    /**
     * Get zoom level percentage (0-100%)
     */
    fun getZoomPercentage(): Int {
        val zoomRange = maxZoomRatio - minZoomRatio
        val currentZoomFromMin = currentZoomRatio - minZoomRatio
        return ((currentZoomFromMin / zoomRange) * 100).toInt().coerceIn(0, 100)
    }

    /**
     * Get zoom display text
     */
    fun getZoomDisplayText(): String {
        return "${String.format("%.1f", currentZoomRatio)}x"
    }

    companion object {
        private const val TAG = "ZoomController"
    }
}