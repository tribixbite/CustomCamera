package com.customcamera.app.manual

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.hardware.camera2.*
import android.hardware.camera2.params.MeteringRectangle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Range
import android.util.Size
import android.view.Surface
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraInfo
import androidx.camera.core.ImageCapture
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.customcamera.app.engine.DebugLogger
import kotlinx.coroutines.launch
import java.util.*
import kotlin.math.*

/**
 * Professional Manual Controls Manager
 *
 * Provides comprehensive manual camera controls for professional photography:
 * - Manual ISO (sensitivity) control
 * - Manual shutter speed control
 * - Manual focus distance control
 * - White balance control
 * - Exposure compensation
 * - Professional visual aids (histogram, zebra, focus peaking)
 *
 * Designed to replicate DSLR/mirrorless camera manual controls on mobile devices.
 */
class ManualControlsManager(
    private val context: Context,
    private val debugLogger: DebugLogger
) {

    companion object {
        private const val TAG = "ManualControlsManager"

        // Control ranges - will be updated from camera capabilities
        private val DEFAULT_ISO_RANGE = Range(100, 6400)
        private val DEFAULT_SHUTTER_RANGE = Range(1000000000L, 33333333L) // 1s to 1/30s in nanoseconds
        private val DEFAULT_FOCUS_RANGE = Range(0.0f, Float.POSITIVE_INFINITY)

        // Exposure compensation typically ranges from -3 to +3 EV in 1/3 stops
        private val EXPOSURE_COMPENSATION_RANGE = Range(-9, 9) // In 1/3 EV steps

        // White balance presets (color temperature in Kelvin)
        private val WHITE_BALANCE_PRESETS = mapOf(
            "Auto" to 0,
            "Daylight" to 5500,
            "Cloudy" to 6000,
            "Shade" to 7000,
            "Tungsten" to 3200,
            "Fluorescent" to 4000,
            "Flash" to 5500
        )
    }

    // Current manual control states
    private var isManualModeEnabled = false
    private var manualIso: Int? = null
    private var manualShutterSpeed: Long? = null // In nanoseconds
    private var manualFocusDistance: Float? = null
    private var manualWhiteBalance: Int? = null
    private var exposureCompensation: Int = 0 // In 1/3 EV steps

    // Camera capabilities
    private var isoRange: Range<Int> = DEFAULT_ISO_RANGE
    private var shutterSpeedRange: Range<Long> = DEFAULT_SHUTTER_RANGE
    private var focusDistanceRange: Range<Float> = DEFAULT_FOCUS_RANGE

    // Visual aids
    private var showHistogram = false
    private var showZebraPattern = false
    private var showFocusPeaking = false
    private var showExposureMeter = false
    private var showProfessionalOverlay = false

    // Camera2 API integration
    @OptIn(ExperimentalCamera2Interop::class)
    private var cameraInfo: CameraInfo? = null
    private var captureRequestBuilder: CaptureRequest.Builder? = null

    // Threading
    private val mainHandler = Handler(Looper.getMainLooper())

    // Callbacks
    private var onControlsChangedCallback: ((ManualControlsState) -> Unit)? = null
    private var onHistogramUpdateCallback: ((IntArray) -> Unit)? = null
    private var onExposureMeterUpdateCallback: ((Float) -> Unit)? = null

    /**
     * Initialize manual controls with camera information
     */
    @OptIn(ExperimentalCamera2Interop::class)
    fun initialize(cameraInfo: CameraInfo, lifecycleOwner: LifecycleOwner) {
        this.cameraInfo = cameraInfo

        lifecycleOwner.lifecycleScope.launch {
            try {
                updateCameraCapabilities()
                debugLogger.logInfo(
                    "Manual controls initialized - ISO: ${isoRange.lower}-${isoRange.upper}, Shutter: ${shutterSpeedRange.lower/1000000}ms-${shutterSpeedRange.upper/1000000}ms, Focus: ${focusDistanceRange.lower}-${focusDistanceRange.upper}",
                    mapOf(
                        "isoRange" to "${isoRange.lower}-${isoRange.upper}",
                        "shutterRange" to "${shutterSpeedRange.lower/1000000}ms-${shutterSpeedRange.upper/1000000}ms",
                        "focusRange" to "${focusDistanceRange.lower}-${focusDistanceRange.upper}"
                    )
                )
                Log.i(TAG, "Manual controls initialized successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize manual controls", e)
                debugLogger.logError("Manual controls initialization failed", e)
            }
        }
    }

    /**
     * Enable or disable manual mode
     */
    fun setManualModeEnabled(enabled: Boolean) {
        isManualModeEnabled = enabled

        if (!enabled) {
            // Reset all manual controls to auto
            manualIso = null
            manualShutterSpeed = null
            manualFocusDistance = null
            manualWhiteBalance = null
            exposureCompensation = 0
        }

        notifyControlsChanged()
        debugLogger.logInfo(
            "Manual mode ${if (enabled) "enabled" else "disabled"}"
        )
        Log.i(TAG, "Manual mode: $enabled")
    }

    /**
     * Set manual ISO sensitivity
     */
    fun setManualIso(iso: Int?) {
        if (!isManualModeEnabled) return

        manualIso = iso?.coerceIn(isoRange.lower, isoRange.upper)
        notifyControlsChanged()

        Log.i(TAG, "Manual ISO set to: $manualIso")
        debugLogger.logInfo(
            "Manual ISO changed to ${manualIso?.toString() ?: "auto"}",
            mapOf("iso" to (manualIso?.toString() ?: "auto"))
        )
    }

    /**
     * Set manual shutter speed in nanoseconds
     */
    fun setManualShutterSpeed(shutterSpeedNs: Long?) {
        if (!isManualModeEnabled) return

        manualShutterSpeed = shutterSpeedNs?.coerceIn(shutterSpeedRange.lower, shutterSpeedRange.upper)
        notifyControlsChanged()

        val shutterSpeedMs = manualShutterSpeed?.let { it / 1000000.0 }
        Log.i(TAG, "Manual shutter speed set to: ${shutterSpeedMs}ms")
        debugLogger.logInfo(
            "Manual shutter speed changed to ${shutterSpeedMs?.toString() ?: "auto"}ms",
            mapOf("shutterSpeedMs" to (shutterSpeedMs?.toString() ?: "auto"))
        )
    }

    /**
     * Set manual focus distance (0.0 = infinity, higher values = closer)
     */
    fun setManualFocusDistance(distance: Float?) {
        if (!isManualModeEnabled) return

        manualFocusDistance = distance?.coerceIn(focusDistanceRange.lower, focusDistanceRange.upper)
        notifyControlsChanged()

        Log.i(TAG, "Manual focus distance set to: $manualFocusDistance")
        debugLogger.logInfo(
            "Manual focus distance changed to ${manualFocusDistance?.toString() ?: "auto"}",
            mapOf("focusDistance" to (manualFocusDistance?.toString() ?: "auto"))
        )
    }

    /**
     * Set white balance color temperature
     */
    fun setManualWhiteBalance(colorTemperature: Int?) {
        if (!isManualModeEnabled) return

        manualWhiteBalance = colorTemperature
        notifyControlsChanged()

        Log.i(TAG, "Manual white balance set to: ${manualWhiteBalance}K")
        debugLogger.logInfo(
            "Manual white balance changed to ${manualWhiteBalance?.toString() ?: "auto"}K",
            mapOf("whiteBalance" to (manualWhiteBalance?.toString() ?: "auto"))
        )
    }

    /**
     * Set exposure compensation in 1/3 EV steps
     */
    fun setExposureCompensation(evSteps: Int) {
        exposureCompensation = evSteps.coerceIn(EXPOSURE_COMPENSATION_RANGE.lower, EXPOSURE_COMPENSATION_RANGE.upper)
        notifyControlsChanged()

        val evValue = exposureCompensation / 3.0
        Log.i(TAG, "Exposure compensation set to: ${evValue}EV")
        debugLogger.logInfo(
            "Exposure compensation changed to ${evValue}EV",
            mapOf("evSteps" to exposureCompensation.toString(), "evValue" to evValue.toString())
        )
    }

    /**
     * Configure visual aids
     */
    fun setVisualAidsEnabled(
        histogram: Boolean = showHistogram,
        zebraPattern: Boolean = showZebraPattern,
        focusPeaking: Boolean = showFocusPeaking,
        exposureMeter: Boolean = showExposureMeter,
        professionalOverlay: Boolean = showProfessionalOverlay
    ) {
        showHistogram = histogram
        showZebraPattern = zebraPattern
        showFocusPeaking = focusPeaking
        showExposureMeter = exposureMeter
        showProfessionalOverlay = professionalOverlay

        notifyControlsChanged()
        debugLogger.logInfo(
            "Visual aids configured: histogram=$histogram, zebra=$zebraPattern, focusPeaking=$focusPeaking, exposureMeter=$exposureMeter, overlay=$professionalOverlay",
            mapOf(
                "histogram" to histogram.toString(),
                "zebra" to zebraPattern.toString(),
                "focusPeaking" to focusPeaking.toString(),
                "exposureMeter" to exposureMeter.toString(),
                "overlay" to professionalOverlay.toString()
            )
        )
    }

    /**
     * Apply manual controls to camera capture request
     */
    @SuppressLint("UnsafeOptInUsageError")
    fun applyCaptureRequest(builder: CaptureRequest.Builder) {
        if (!isManualModeEnabled) {
            // Apply auto modes
            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
            builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            builder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)
            return
        }

        try {
            // Manual ISO
            manualIso?.let { iso ->
                builder.set(CaptureRequest.SENSOR_SENSITIVITY, iso)
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
            }

            // Manual shutter speed
            manualShutterSpeed?.let { shutter ->
                builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, shutter)
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
            }

            // Manual focus
            manualFocusDistance?.let { distance ->
                builder.set(CaptureRequest.LENS_FOCUS_DISTANCE, distance)
                builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
            }

            // Manual white balance
            manualWhiteBalance?.let { temp ->
                if (temp > 0) {
                    builder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_OFF)
                    // Note: Setting custom color temperature requires Camera2 extensions
                    // For now, we use the closest preset
                    val closestPreset = getClosestWhiteBalancePreset(temp)
                    builder.set(CaptureRequest.CONTROL_AWB_MODE, closestPreset)
                }
            }

            // Exposure compensation
            if (exposureCompensation != 0) {
                builder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, exposureCompensation)
            }

            Log.d(TAG, "Applied manual controls to capture request")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply manual controls", e)
            debugLogger.logError("Failed to apply manual controls", e)
        }
    }

    /**
     * Get current manual controls state
     */
    fun getCurrentState(): ManualControlsState {
        return ManualControlsState(
            isManualModeEnabled = isManualModeEnabled,
            manualIso = manualIso,
            manualShutterSpeed = manualShutterSpeed,
            manualFocusDistance = manualFocusDistance,
            manualWhiteBalance = manualWhiteBalance,
            exposureCompensation = exposureCompensation,
            isoRange = isoRange,
            shutterSpeedRange = shutterSpeedRange,
            focusDistanceRange = focusDistanceRange,
            showHistogram = showHistogram,
            showZebraPattern = showZebraPattern,
            showFocusPeaking = showFocusPeaking,
            showExposureMeter = showExposureMeter,
            showProfessionalOverlay = showProfessionalOverlay
        )
    }

    /**
     * Get available white balance presets
     */
    fun getWhiteBalancePresets(): Map<String, Int> = WHITE_BALANCE_PRESETS

    /**
     * Convert shutter speed from nanoseconds to readable string
     */
    fun formatShutterSpeed(shutterSpeedNs: Long): String {
        val seconds = shutterSpeedNs / 1000000000.0
        return when {
            seconds >= 1.0 -> "${seconds.roundToInt()}s"
            seconds >= 0.1 -> "${(seconds * 10).roundToInt() / 10.0}s"
            else -> "1/${(1.0 / seconds).roundToInt()}s"
        }
    }

    /**
     * Convert ISO to descriptive string
     */
    fun formatIso(iso: Int): String {
        return when {
            iso <= 200 -> "ISO $iso (Low)"
            iso <= 800 -> "ISO $iso (Normal)"
            iso <= 3200 -> "ISO $iso (High)"
            else -> "ISO $iso (Very High)"
        }
    }

    /**
     * Set callback for controls changes
     */
    fun setOnControlsChangedCallback(callback: (ManualControlsState) -> Unit) {
        onControlsChangedCallback = callback
    }

    /**
     * Set callback for histogram updates
     */
    fun setOnHistogramUpdateCallback(callback: (IntArray) -> Unit) {
        onHistogramUpdateCallback = callback
    }

    /**
     * Set callback for exposure meter updates
     */
    fun setOnExposureMeterUpdateCallback(callback: (Float) -> Unit) {
        onExposureMeterUpdateCallback = callback
    }

    /**
     * Process image for histogram and exposure analysis
     */
    fun processImageForAnalysis(image: ByteArray, width: Int, height: Int) {
        if (!showHistogram && !showExposureMeter && !showZebraPattern) return

        try {
            // Convert image to bitmap for analysis
            val bitmap = BitmapFactory.decodeByteArray(image, 0, image.size)
            bitmap?.let { bmp ->
                if (showHistogram) {
                    val histogram = calculateHistogram(bmp)
                    onHistogramUpdateCallback?.invoke(histogram)
                }

                if (showExposureMeter) {
                    val exposureLevel = calculateExposureLevel(bmp)
                    onExposureMeterUpdateCallback?.invoke(exposureLevel)
                }

                bmp.recycle()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to process image for analysis", e)
        }
    }

    // Private helper methods

    @OptIn(ExperimentalCamera2Interop::class)
    private fun updateCameraCapabilities() {
        cameraInfo?.let { info ->
            try {
                val camera2Info = Camera2CameraInfo.from(info)
                val characteristics = camera2Info.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
                characteristics?.let { range ->
                    isoRange = range
                }

                val exposureRange = camera2Info.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
                exposureRange?.let { range ->
                    shutterSpeedRange = range
                }

                val focusRange = camera2Info.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE)
                focusRange?.let { minDistance ->
                    focusDistanceRange = Range(0.0f, minDistance)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to get some camera characteristics", e)
            }
        }
    }

    private fun getClosestWhiteBalancePreset(colorTemp: Int): Int {
        return when {
            colorTemp < 3600 -> CaptureRequest.CONTROL_AWB_MODE_INCANDESCENT
            colorTemp < 4500 -> CaptureRequest.CONTROL_AWB_MODE_FLUORESCENT
            colorTemp < 5800 -> CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT
            colorTemp < 6500 -> CaptureRequest.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT
            else -> CaptureRequest.CONTROL_AWB_MODE_SHADE
        }
    }

    private fun calculateHistogram(bitmap: Bitmap): IntArray {
        val histogram = IntArray(256) // For grayscale histogram
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        for (pixel in pixels) {
            // Convert to grayscale
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            val gray = ((0.299 * r + 0.587 * g + 0.114 * b).toInt()).coerceIn(0, 255)
            histogram[gray]++
        }

        return histogram
    }

    private fun calculateExposureLevel(bitmap: Bitmap): Float {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        var totalBrightness = 0.0
        for (pixel in pixels) {
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            val brightness = 0.299 * r + 0.587 * g + 0.114 * b
            totalBrightness += brightness
        }

        return (totalBrightness / pixels.size / 255.0).toFloat()
    }

    private fun notifyControlsChanged() {
        onControlsChangedCallback?.invoke(getCurrentState())
    }
}

/**
 * Data class representing the current state of manual controls
 */
data class ManualControlsState(
    val isManualModeEnabled: Boolean,
    val manualIso: Int?,
    val manualShutterSpeed: Long?,
    val manualFocusDistance: Float?,
    val manualWhiteBalance: Int?,
    val exposureCompensation: Int,
    val isoRange: Range<Int>,
    val shutterSpeedRange: Range<Long>,
    val focusDistanceRange: Range<Float>,
    val showHistogram: Boolean,
    val showZebraPattern: Boolean,
    val showFocusPeaking: Boolean,
    val showExposureMeter: Boolean,
    val showProfessionalOverlay: Boolean
)