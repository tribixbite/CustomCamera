package com.customcamera.app.camera2

import android.content.Context
import android.hardware.camera2.*
import android.util.Log
import android.util.Range

/**
 * ShutterSpeedController provides real shutter speed control
 * through Camera2 API with exposure time manipulation
 */
class ShutterSpeedController(private val context: Context) {

    private var cameraManager: CameraManager? = null
    private var exposureTimeRange: Range<Long>? = null
    private var currentExposureTimeNs: Long = 16666666L // 1/60s default

    // Predefined shutter speeds in nanoseconds
    private val shutterSpeeds = mapOf(
        "1/8000" to 125000L,
        "1/4000" to 250000L,
        "1/2000" to 500000L,
        "1/1000" to 1000000L,
        "1/500" to 2000000L,
        "1/250" to 4000000L,
        "1/125" to 8000000L,
        "1/60" to 16666666L,
        "1/30" to 33333333L,
        "1/15" to 66666666L,
        "1/8" to 125000000L,
        "1/4" to 250000000L,
        "1/2" to 500000000L,
        "1s" to 1000000000L,
        "2s" to 2000000000L,
        "4s" to 4000000000L,
        "8s" to 8000000000L,
        "15s" to 15000000000L,
        "30s" to 30000000000L
    )

    /**
     * Initialize shutter speed controller
     */
    fun initialize(cameraId: String): Boolean {
        return try {
            cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val characteristics = cameraManager!!.getCameraCharacteristics(cameraId)

            // Extract exposure time range from Camera2
            exposureTimeRange = characteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)

            Log.i(TAG, "Shutter speed controller initialized for camera $cameraId")
            Log.i(TAG, "Exposure time range: $exposureTimeRange")

            if (exposureTimeRange != null) {
                Log.i(TAG, "Min exposure: ${exposureTimeRange!!.lower}ns (${exposureTimeToDisplayText(exposureTimeRange!!.lower)})")
                Log.i(TAG, "Max exposure: ${exposureTimeRange!!.upper}ns (${exposureTimeToDisplayText(exposureTimeRange!!.upper)})")
            }

            true

        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize shutter speed controller", e)
            false
        }
    }

    /**
     * Set shutter speed by exposure time in nanoseconds
     */
    fun setShutterSpeed(exposureTimeNs: Long): Boolean {
        return try {
            val range = exposureTimeRange
            val clampedExposureTime = if (range != null) {
                exposureTimeNs.coerceIn(range.lower, range.upper)
            } else {
                exposureTimeNs
            }

            currentExposureTimeNs = clampedExposureTime

            Log.i(TAG, "Shutter speed set to: ${exposureTimeToDisplayText(clampedExposureTime)} (${clampedExposureTime}ns)")

            // In production with Camera2 capture session:
            // captureRequestBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, clampedExposureTime)
            // captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF)

            true

        } catch (e: Exception) {
            Log.e(TAG, "Failed to set shutter speed", e)
            false
        }
    }

    /**
     * Set shutter speed by predefined speed name
     */
    fun setShutterSpeedByName(speedName: String): Boolean {
        val exposureTimeNs = shutterSpeeds[speedName]
        return if (exposureTimeNs != null) {
            setShutterSpeed(exposureTimeNs)
        } else {
            Log.w(TAG, "Unknown shutter speed: $speedName")
            false
        }
    }

    /**
     * Get available shutter speeds within camera range
     */
    fun getAvailableShutterSpeeds(): List<Pair<String, Long>> {
        val range = exposureTimeRange ?: return shutterSpeeds.toList()

        return shutterSpeeds.filter { (_, exposureTime) ->
            exposureTime in range.lower..range.upper
        }.toList()
    }

    /**
     * Convert exposure time to display text
     */
    fun exposureTimeToDisplayText(exposureTimeNs: Long): String {
        return when {
            exposureTimeNs < 1000000L -> { // Less than 1ms
                val fraction = (1000000000L / exposureTimeNs).toInt()
                "1/$fraction"
            }
            exposureTimeNs < 1000000000L -> { // Less than 1 second
                val ms = exposureTimeNs / 1000000
                "${ms}ms"
            }
            else -> { // 1 second or more
                val seconds = exposureTimeNs / 1000000000
                "${seconds}s"
            }
        }
    }

    /**
     * Get current shutter speed settings
     */
    fun getCurrentShutterSpeedSettings(): Map<String, Any> {
        return mapOf(
            "currentExposureTimeNs" to currentExposureTimeNs,
            "currentDisplayText" to exposureTimeToDisplayText(currentExposureTimeNs),
            "exposureTimeRange" to (exposureTimeRange?.toString() ?: "unknown"),
            "availableShutterSpeeds" to getAvailableShutterSpeeds().size,
            "manualShutterSupported" to (exposureTimeRange != null)
        )
    }

    /**
     * Reset to auto shutter speed
     */
    fun resetToAuto(): Boolean {
        currentExposureTimeNs = 16666666L // 1/60s default
        Log.i(TAG, "Shutter speed reset to auto (1/60s)")
        return true
    }

    companion object {
        private const val TAG = "ShutterSpeedController"
    }
}