package com.customcamera.app.camera2

import android.content.Context
import android.hardware.camera2.*
import android.util.Log

/**
 * ApertureController provides aperture control for devices
 * that support variable aperture through Camera2 API
 */
class ApertureController(private val context: Context) {

    private var cameraManager: CameraManager? = null
    private var availableApertures: FloatArray? = null
    private var currentAperture: Float = 1.8f

    /**
     * Initialize aperture controller
     */
    fun initialize(cameraId: String): Boolean {
        return try {
            cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val characteristics = cameraManager!!.getCameraCharacteristics(cameraId)

            // Extract available apertures from Camera2
            availableApertures = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)

            Log.i(TAG, "Aperture controller initialized for camera $cameraId")

            if (availableApertures != null && availableApertures!!.size > 1) {
                Log.i(TAG, "Variable aperture supported: ${availableApertures!!.contentToString()}")
                currentAperture = availableApertures!![0] // Use first available aperture
            } else {
                Log.i(TAG, "Fixed aperture camera - aperture control not available")
            }

            true

        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize aperture controller", e)
            false
        }
    }

    /**
     * Set aperture value
     */
    fun setAperture(aperture: Float): Boolean {
        return try {
            val apertures = availableApertures
            if (apertures != null && apertures.any { it == aperture }) {
                currentAperture = aperture

                Log.i(TAG, "Aperture set to: f/${aperture}")

                // In production with Camera2 capture session:
                // captureRequestBuilder.set(CaptureRequest.LENS_APERTURE, aperture)

                true
            } else {
                Log.w(TAG, "Aperture f/$aperture not supported by camera")
                false
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to set aperture", e)
            false
        }
    }

    /**
     * Get available apertures
     */
    fun getAvailableApertures(): FloatArray? = availableApertures

    /**
     * Check if variable aperture is supported
     */
    fun isVariableApertureSupported(): Boolean {
        return availableApertures != null && availableApertures!!.size > 1
    }

    /**
     * Get aperture display text
     */
    fun getApertureDisplayText(aperture: Float): String {
        return "f/${String.format("%.1f", aperture)}"
    }

    /**
     * Calculate depth of field
     */
    fun calculateDepthOfField(
        focalLengthMm: Float,
        aperture: Float,
        focusDistanceM: Float,
        sensorSizeMm: Float = 6.17f // Typical smartphone sensor diagonal
    ): String {
        return try {
            // Simplified depth of field calculation
            val circleOfConfusion = sensorSizeMm / 1000f // Convert to reasonable CoC
            val hyperfocalDistance = (focalLengthMm * focalLengthMm) / (aperture * circleOfConfusion)

            val nearLimit = (focusDistanceM * hyperfocalDistance) / (hyperfocalDistance + focusDistanceM)
            val farLimit = (focusDistanceM * hyperfocalDistance) / (hyperfocalDistance - focusDistanceM)

            val dofNear = kotlin.math.abs(focusDistanceM - nearLimit)
            val dofFar = if (farLimit > 0 && farLimit < 1000) kotlin.math.abs(farLimit - focusDistanceM) else Float.POSITIVE_INFINITY

            "DoF: ${String.format("%.1f", dofNear)}m - ${if (dofFar.isFinite()) String.format("%.1f", dofFar) + "m" else "∞"}"

        } catch (e: Exception) {
            "DoF: Unable to calculate"
        }
    }

    /**
     * Get current aperture settings
     */
    fun getCurrentApertureSettings(): Map<String, Any> {
        return mapOf(
            "currentAperture" to currentAperture,
            "currentDisplayText" to getApertureDisplayText(currentAperture),
            "availableApertures" to (availableApertures?.contentToString() ?: "none"),
            "variableApertureSupported" to isVariableApertureSupported(),
            "apertureCount" to (availableApertures?.size ?: 0)
        )
    }

    companion object {
        private const val TAG = "ApertureController"
    }
}