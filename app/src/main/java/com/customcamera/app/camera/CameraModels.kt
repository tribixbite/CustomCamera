package com.customcamera.app.camera

import android.graphics.Matrix
import android.graphics.RectF
import android.net.Uri
import com.customcamera.app.core.AspectOption
import com.customcamera.app.core.CaptureMode
import com.customcamera.app.core.FlashOption
import com.customcamera.app.core.LensMath
import com.customcamera.app.core.PeakingMask
import com.customcamera.app.core.PipCorner
import com.customcamera.app.core.ScanContent

/** A camera exposed by CameraX, described for the lens picker. */
data class LensDescriptor(
    val cameraId: String,
    val isFront: Boolean,
    val equivalentFocalMm: Int?,
    val category: LensMath.LensCategory,
    val isLogicalMultiCamera: Boolean,
    val hasFlash: Boolean,
)

/** Hardware limits for Pro mode, read from Camera2 characteristics. */
data class ManualCapabilities(
    val manualSensor: Boolean,
    val isoRange: IntRange?,
    val exposureRangeNs: LongRange?,
    /** Closest focus distance in diopters; 0 means fixed focus. */
    val minFocusDiopters: Float,
    val awbModes: List<Int>,
) {
    val manualFocus: Boolean get() = minFocusDiopters > 0f

    companion object {
        val NONE = ManualCapabilities(false, null, null, 0f, emptyList())
    }
}

/** User-chosen Pro-mode values; null fields stay on auto. */
data class ManualSettings(
    val iso: Int? = null,
    val exposureNs: Long? = null,
    /** CaptureRequest.CONTROL_AWB_MODE_* value. */
    val awbMode: Int = AWB_AUTO,
    val focusDiopters: Float? = null,
) {
    val isAllAuto: Boolean get() = iso == null && exposureNs == null && awbMode == AWB_AUTO && focusDiopters == null

    companion object {
        /** CaptureRequest.CONTROL_AWB_MODE_AUTO */
        const val AWB_AUTO = 1
    }
}

/** Live auto-exposure readings from the preview stream. */
data class LiveExposure(val iso: Int, val exposureNs: Long, val focusDiopters: Float)

enum class RecordingStatus { IDLE, RECORDING, PAUSED }

/** Snapshot of everything the viewfinder UI renders. */
data class CameraState(
    val ready: Boolean = false,
    val mode: CaptureMode = CaptureMode.PHOTO,
    val availableModes: List<CaptureMode> = CaptureMode.available(emptySet()),
    val lenses: List<LensDescriptor> = emptyList(),
    val currentCameraId: String? = null,
    val isFront: Boolean = false,
    val hasFlashUnit: Boolean = false,
    val flash: FlashOption = FlashOption.OFF,
    val aspect: AspectOption = AspectOption.RATIO_4_3,
    val hdrAvailable: Boolean = false,
    val hdrEnabled: Boolean = false,
    val zoomRatio: Float = 1f,
    val minZoom: Float = 1f,
    val maxZoom: Float = 1f,
    val evIndex: Int = 0,
    val evMin: Int = 0,
    val evMax: Int = 0,
    val evStep: Float = 0f,
    val manualCapabilities: ManualCapabilities = ManualCapabilities.NONE,
    val manual: ManualSettings = ManualSettings(),
    val recording: RecordingStatus = RecordingStatus.IDLE,
    val recordingNanos: Long = 0L,
    val focusLocked: Boolean = false,
    /** Highest torch strength level of the bound camera; 0 when strength is not adjustable. */
    val torchStrengthMax: Int = 0,
    /** Current torch strength level (1..[torchStrengthMax]); meaningful only when adjustable. */
    val torchStrength: Int = 0,
    /** DUAL mode layout. */
    val pipCorner: PipCorner = PipCorner.DEFAULT,
    val dualFrontMain: Boolean = false,
    /** The binding includes the focus-peaking analyzer (false e.g. with RAW or after a fallback). */
    val peakingAvailable: Boolean = false,
    /** VIDEO binding includes a still-capture stream, so photos can be taken while recording. */
    val snapshotAvailable: Boolean = false,
    /** The bound still capture writes RAW (DNG) + JPEG. */
    val rawActive: Boolean = false,
    /** The bound still capture writes Ultra HDR (JPEG_R) photos. */
    val ultraHdrActive: Boolean = false,
) {
    val evSupported: Boolean get() = evMax > evMin
    val torchStrengthAdjustable: Boolean get() = torchStrengthMax > 1
}

/** One-shot notifications from the controller to the UI. */
sealed class CameraEvent {
    /** Sensor exposure started; the UI plays the shutter animation/sound now. */
    object CaptureStarted : CameraEvent()
    data class PhotoSaved(val uri: Uri?) : CameraEvent()
    data class VideoSaved(val uri: Uri) : CameraEvent()
    data class Error(val message: String, val cause: Throwable? = null) : CameraEvent()
    data class FocusResult(val success: Boolean) : CameraEvent()

    /** Barcodes found in the latest frame, with boxes in PreviewView coordinates. */
    data class BarcodesDetected(val results: List<DetectedCode>) : CameraEvent()
    data class HistogramUpdated(val histogram: com.customcamera.app.core.Histogram) : CameraEvent()

    /**
     * In-focus edges of the latest analysis frame. [mask] is upright; [toPreview]
     * maps mask cell coordinates onto PreviewView coordinates.
     */
    data class FocusPeakingUpdated(val mask: PeakingMask, val toPreview: Matrix) : CameraEvent()

    /** Fallback happened: the requested camera failed and another was bound. */
    data class CameraFallback(val failedCameraId: String?, val boundCameraId: String) : CameraEvent()
}

data class DetectedCode(val content: ScanContent, val box: RectF?)
