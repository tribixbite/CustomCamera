package com.customcamera.app.camera2

import android.content.Context
import android.hardware.camera2.*
import android.util.Log
import android.util.Range

/**
 * ManualControlHelper provides Camera2 API integration
 * for real manual camera controls
 */
class ManualControlHelper(private val context: Context) {

    private var cameraManager: CameraManager? = null
    private var currentCameraCharacteristics: CameraCharacteristics? = null

    fun initializeForCamera(cameraId: String): Boolean {
        return try {
            cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            currentCameraCharacteristics = cameraManager!!.getCameraCharacteristics(cameraId)

            Log.i(TAG, "Manual control helper initialized for camera $cameraId")
            logCameraCapabilities(cameraId)
            true

        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize manual control helper", e)
            false
        }
    }

    fun getISORange(): Range<Int>? {
        return currentCameraCharacteristics?.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
    }

    fun getExposureTimeRange(): Range<Long>? {
        return currentCameraCharacteristics?.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
    }

    fun getMinimumFocusDistance(): Float? {
        return currentCameraCharacteristics?.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE)
    }

    fun isManualControlSupported(): Boolean {
        val characteristics = currentCameraCharacteristics ?: return false

        val isoRange = characteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
        val exposureRange = characteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)

        return isoRange != null && exposureRange != null
    }

    fun getManualControlCapabilities(): Map<String, Any> {
        val characteristics = currentCameraCharacteristics

        return if (characteristics != null) {
            mapOf(
                "isoRange" to (getISORange()?.toString() ?: "Not available"),
                "exposureTimeRange" to (getExposureTimeRange()?.toString() ?: "Not available"),
                "minimumFocusDistance" to (getMinimumFocusDistance()?.toString() ?: "Not available"),
                "manualControlSupported" to isManualControlSupported(),
                "availableAFModes" to (characteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)?.contentToString() ?: "Unknown"),
                "availableAEModes" to (characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES)?.contentToString() ?: "Unknown")
            )
        } else {
            mapOf("error" to "Camera characteristics not available")
        }
    }

    private fun logCameraCapabilities(cameraId: String) {
        try {
            val capabilities = getManualControlCapabilities()
            Log.i(TAG, "Camera $cameraId manual control capabilities:")
            capabilities.forEach { (key, value) ->
                Log.i(TAG, "  $key: $value")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error logging camera capabilities", e)
        }
    }

    companion object {
        private const val TAG = "ManualControlHelper"
    }
}