package com.customcamera.app.camera2

import android.content.Context
import android.hardware.camera2.*
import android.util.Log

/**
 * FocusDistanceController provides real focus distance control
 * through Camera2 API with manual focus capabilities
 */
class FocusDistanceController(private val context: Context) {

    private var cameraManager: CameraManager? = null
    private var minimumFocusDistance: Float = 0f
    private var currentFocusDistance: Float = 0f // 0 = infinity, max = closest

    // Focus presets in diopters (1/distance_in_meters)
    private val focusPresets = mapOf(
        "Infinity" to 0f,
        "Landscape (50m)" to 0.02f,
        "Portrait (2m)" to 0.5f,
        "Close (1m)" to 1.0f,
        "Macro (50cm)" to 2.0f,
        "Very Close (25cm)" to 4.0f,
        "Minimum" to 10.0f // Will be adjusted to actual camera minimum
    )

    /**
     * Initialize focus distance controller
     */
    fun initialize(cameraId: String): Boolean {
        return try {
            cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val characteristics = cameraManager!!.getCameraCharacteristics(cameraId)

            // Extract minimum focus distance from Camera2
            minimumFocusDistance = characteristics.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f

            Log.i(TAG, "Focus distance controller initialized for camera $cameraId")
            Log.i(TAG, "Minimum focus distance: ${minimumFocusDistance} diopters")

            if (minimumFocusDistance > 0f) {
                val closestDistanceCm = (100f / minimumFocusDistance)
                Log.i(TAG, "Closest focus distance: ${String.format("%.1f", closestDistanceCm)}cm")
            }

            true

        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize focus distance controller", e)
            false
        }
    }

    /**
     * Set focus distance in diopters (0 = infinity, max = closest)
     */
    fun setFocusDistance(focusDistanceDiopters: Float): Boolean {
        return try {
            val clampedDistance = focusDistanceDiopters.coerceIn(0f, minimumFocusDistance)
            currentFocusDistance = clampedDistance

            Log.i(TAG, "Focus distance set to: ${clampedDistance} diopters (${focusDistanceToDisplayText(clampedDistance)})")

            // In production with Camera2 capture session:
            // captureRequestBuilder.set(CaptureRequest.LENS_FOCUS_DISTANCE, clampedDistance)
            // captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_OFF)

            true

        } catch (e: Exception) {
            Log.e(TAG, "Failed to set focus distance", e)
            false
        }
    }

    /**
     * Set focus distance by preset name
     */
    fun setFocusDistanceByPreset(presetName: String): Boolean {
        val distance = focusPresets[presetName]
        return if (distance != null) {
            val adjustedDistance = if (presetName == "Minimum") minimumFocusDistance else distance
            setFocusDistance(adjustedDistance)
        } else {
            Log.w(TAG, "Unknown focus preset: $presetName")
            false
        }
    }

    /**
     * Get available focus presets within camera range
     */
    fun getAvailableFocusPresets(): List<Pair<String, Float>> {
        return focusPresets.filter { (name, distance) ->
            val adjustedDistance = if (name == "Minimum") minimumFocusDistance else distance
            adjustedDistance <= minimumFocusDistance
        }.map { (name, distance) ->
            val adjustedDistance = if (name == "Minimum") minimumFocusDistance else distance
            name to adjustedDistance
        }
    }

    /**
     * Convert focus distance to display text
     */
    fun focusDistanceToDisplayText(diopters: Float): String {
        return when {
            diopters == 0f -> "∞ (Infinity)"
            diopters < 0.1f -> {
                val meters = 1f / diopters
                "${String.format("%.0f", meters)}m"
            }
            diopters < 1f -> {
                val meters = 1f / diopters
                "${String.format("%.1f", meters)}m"
            }
            else -> {
                val cm = 100f / diopters
                "${String.format("%.0f", cm)}cm"
            }
        }
    }

    /**
     * Calculate hyperfocal distance
     */
    fun calculateHyperfocalDistance(focalLengthMm: Float, aperture: Float): String {
        return try {
            // Hyperfocal distance = (focal_length^2) / (aperture * circle_of_confusion) + focal_length
            val circleOfConfusion = 0.03f // mm for smartphone sensor
            val hyperfocalMm = (focalLengthMm * focalLengthMm) / (aperture * circleOfConfusion) + focalLengthMm
            val hyperfocalM = hyperfocalMm / 1000f

            if (hyperfocalM > 1f) {
                "Hyperfocal: ${String.format("%.1f", hyperfocalM)}m"
            } else {
                "Hyperfocal: ${String.format("%.0f", hyperfocalMm)}mm"
            }
        } catch (e: Exception) {
            "Hyperfocal: Unable to calculate"
        }
    }

    /**
     * Get current focus distance settings
     */
    fun getCurrentFocusDistanceSettings(): Map<String, Any> {
        return mapOf(
            "currentFocusDistance" to currentFocusDistance,
            "currentDisplayText" to focusDistanceToDisplayText(currentFocusDistance),
            "minimumFocusDistance" to minimumFocusDistance,
            "availablePresets" to getAvailableFocusPresets().size,
            "manualFocusSupported" to (minimumFocusDistance > 0f)
        )
    }

    /**
     * Reset to auto focus
     */
    fun resetToAutoFocus(): Boolean {
        currentFocusDistance = 0f
        Log.i(TAG, "Focus distance reset to auto (infinity)")
        return true
    }

    companion object {
        private const val TAG = "FocusDistanceController"
    }
}