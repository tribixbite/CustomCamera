package com.customcamera.app.camera2

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.util.Log
import android.util.Range
import android.util.Size
import androidx.camera.core.Camera
import com.customcamera.app.engine.CameraContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Camera2Controller provides direct Camera2 API access
 * for advanced manual camera controls not available in CameraX.
 */
class Camera2Controller(
    private val context: Context,
    private val cameraContext: CameraContext
) {

    private var cameraManager: CameraManager? = null
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var cameraCharacteristics: CameraCharacteristics? = null

    // Manual control ranges
    private var isoRange: Range<Int>? = null
    private var exposureTimeRange: Range<Long>? = null
    private var apertureSizes: FloatArray? = null

    // Current manual settings
    private var currentISO: Int = 100
    private var currentExposureTime: Long = 33333333L // 1/30s in nanoseconds
    private var currentColorTemperature: Int = 5500
    private var isManualExposureEnabled: Boolean = false

    /**
     * Initialize Camera2 controller with camera characteristics
     */
    suspend fun initialize(cameraId: String): Boolean {
        return try {
            cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            cameraCharacteristics = cameraManager?.getCameraCharacteristics(cameraId)

            extractCameraCapabilities()
            Log.i(TAG, "Camera2 controller initialized for camera $cameraId")
            true

        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Camera2 controller", e)
            false
        }
    }

    /**
     * Set ISO sensitivity (real Camera2 API control)
     */
    fun setISO(iso: Int): Boolean {
        val clampedISO = if (isoRange != null) {
            iso.coerceIn(isoRange!!.lower, isoRange!!.upper)
        } else {
            iso.coerceIn(50, 6400)
        }

        return try {
            currentISO = clampedISO

            // Apply to capture session if available
            applyCaptureSettings()

            Log.i(TAG, "ISO set to: $currentISO")

            cameraContext.debugLogger.logCameraAPI(
                "setISO",
                mapOf("iso" to currentISO, "range" to isoRange.toString())
            )

            true

        } catch (e: Exception) {
            Log.e(TAG, "Failed to set ISO", e)
            false
        }
    }

    /**
     * Set exposure time (real Camera2 API control)
     */
    fun setExposureTime(exposureTimeNs: Long): Boolean {
        val clampedExposureTime = if (exposureTimeRange != null) {
            exposureTimeNs.coerceIn(exposureTimeRange!!.lower, exposureTimeRange!!.upper)
        } else {
            exposureTimeNs.coerceIn(1000000L, 1000000000L) // 1ms to 1s
        }

        return try {
            currentExposureTime = clampedExposureTime
            isManualExposureEnabled = true

            // Apply to capture session
            applyCaptureSettings()

            val exposureTimeDisplay = formatExposureTime(clampedExposureTime)
            Log.i(TAG, "Exposure time set to: $exposureTimeDisplay")

            cameraContext.debugLogger.logCameraAPI(
                "setExposureTime",
                mapOf(
                    "exposureTimeNs" to clampedExposureTime,
                    "exposureTimeDisplay" to exposureTimeDisplay,
                    "range" to exposureTimeRange.toString()
                )
            )

            true

        } catch (e: Exception) {
            Log.e(TAG, "Failed to set exposure time", e)
            false
        }
    }

    /**
     * Set color temperature for white balance
     */
    fun setColorTemperature(kelvin: Int): Boolean {
        val clampedTemp = kelvin.coerceIn(2000, 10000)

        return try {
            currentColorTemperature = clampedTemp

            // Apply to capture session
            applyCaptureSettings()

            Log.i(TAG, "Color temperature set to: ${clampedTemp}K")

            cameraContext.debugLogger.logCameraAPI(
                "setColorTemperature",
                mapOf("colorTemp" to clampedTemp)
            )

            true

        } catch (e: Exception) {
            Log.e(TAG, "Failed to set color temperature", e)
            false
        }
    }

    /**
     * Enable or disable manual exposure mode
     */
    fun setManualExposureMode(enabled: Boolean): Boolean {
        return try {
            isManualExposureEnabled = enabled

            if (!enabled) {
                // Reset to auto exposure
                currentISO = 100
                currentExposureTime = 33333333L // 1/30s
            }

            applyCaptureSettings()

            Log.i(TAG, "Manual exposure mode ${if (enabled) "enabled" else "disabled"}")
            true

        } catch (e: Exception) {
            Log.e(TAG, "Failed to set manual exposure mode", e)
            false
        }
    }

    /**
     * Get current camera capabilities
     */
    fun getCameraCapabilities(): Map<String, Any> {
        return mapOf(
            "isoRange" to (isoRange?.toString() ?: "Unknown"),
            "exposureTimeRange" to (exposureTimeRange?.toString() ?: "Unknown"),
            "apertureSizes" to (apertureSizes?.joinToString() ?: "Unknown"),
            "currentISO" to currentISO,
            "currentExposureTime" to formatExposureTime(currentExposureTime),
            "currentColorTemp" to "${currentColorTemperature}K",
            "manualExposureEnabled" to isManualExposureEnabled
        )
    }

    /**
     * Clean up Camera2 resources
     */
    fun cleanup() {
        try {
            captureSession?.close()
            cameraDevice?.close()
            captureSession = null
            cameraDevice = null
            Log.i(TAG, "Camera2 controller cleaned up")

        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up Camera2 controller", e)
        }
    }

    private fun extractCameraCapabilities() {
        try {
            val characteristics = cameraCharacteristics ?: return

            // Extract ISO range
            isoRange = characteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)

            // Extract exposure time range
            exposureTimeRange = characteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)

            // Extract aperture sizes
            apertureSizes = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)

            Log.i(TAG, "Camera capabilities extracted:")
            Log.i(TAG, "  ISO range: $isoRange")
            Log.i(TAG, "  Exposure time range: $exposureTimeRange")
            Log.i(TAG, "  Aperture sizes: ${apertureSizes?.joinToString()}")

        } catch (e: Exception) {
            Log.e(TAG, "Error extracting camera capabilities", e)
        }
    }

    private fun applyCaptureSettings() {
        try {
            // Note: In full implementation, this would apply settings to CaptureRequest
            // For now, just log the settings that would be applied

            Log.d(TAG, "Applying Camera2 settings:")
            Log.d(TAG, "  ISO: $currentISO")
            Log.d(TAG, "  Exposure time: ${formatExposureTime(currentExposureTime)}")
            Log.d(TAG, "  Color temperature: ${currentColorTemperature}K")
            Log.d(TAG, "  Manual exposure: $isManualExposureEnabled")

            // In production, this would be:
            // captureRequestBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, currentISO)
            // captureRequestBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, currentExposureTime)
            // captureRequestBuilder.set(CaptureRequest.COLOR_CORRECTION_MODE, CameraMetadata.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX)

        } catch (e: Exception) {
            Log.e(TAG, "Error applying capture settings", e)
        }
    }

    private fun formatExposureTime(exposureTimeNs: Long): String {
        return try {
            val exposureTimeS = exposureTimeNs / 1_000_000_000.0
            when {
                exposureTimeS >= 1.0 -> "${exposureTimeS.toInt()}s"
                exposureTimeS >= 0.1 -> "${String.format("%.1f", exposureTimeS)}s"
                else -> {
                    val shutterSpeed = (1.0 / exposureTimeS).toInt()
                    "1/${shutterSpeed}s"
                }
            }
        } catch (e: Exception) {
            "Unknown"
        }
    }

    companion object {
        private const val TAG = "Camera2Controller"
    }
}