package com.customcamera.app.camera

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.RectF
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.location.Location
import android.os.SystemClock
import android.util.Log
import android.util.Rational
import android.util.Size
import android.view.Surface
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraState as XCameraState
import androidx.camera.core.DynamicRange
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ExperimentalZeroShutterLag
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.MirrorMode
import androidx.camera.core.Preview
import androidx.camera.core.UseCase
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.ViewPort
import androidx.camera.core.ZoomState
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.ExperimentalPersistentRecording
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.camera.view.TransformExperimental
import androidx.camera.view.transform.CoordinateTransform
import androidx.camera.view.transform.ImageProxyTransformFactory
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import com.customcamera.app.BuildConfig
import com.customcamera.app.core.AspectOption
import com.customcamera.app.core.CaptureMode
import com.customcamera.app.core.ExtensionIds
import com.customcamera.app.core.FlashOption
import com.customcamera.app.core.Histogram
import com.customcamera.app.core.LensMath
import com.customcamera.app.core.MediaNaming
import com.customcamera.app.core.PhotoQualityOption
import com.customcamera.app.core.ScanContent
import com.customcamera.app.core.VideoQualityOption
import com.customcamera.app.gallery.MediaStoreHelper
import com.customcamera.app.settings.AppSettings
import com.customcamera.app.util.await
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.util.Date
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Owns the CameraX session for the viewfinder.
 *
 * Responsibilities: provider/extension initialisation, choosing and binding a
 * camera (with automatic fallback to other cameras when one fails — the
 * original purpose of this project), per-mode use-case composition, manual
 * Camera2 controls, still capture, recording and frame analysis.
 *
 * All public methods must be called on the main thread.
 */
@OptIn(
    ExperimentalCamera2Interop::class,
    TransformExperimental::class,
    ExperimentalZeroShutterLag::class,
    ExperimentalGetImage::class,
    ExperimentalPersistentRecording::class,
)
class CameraController(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val previewView: PreviewView,
    private val settings: AppSettings,
) {
    private val _state = MutableStateFlow(CameraState())
    val state: StateFlow<CameraState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<CameraEvent>(extraBufferCapacity = 32)
    val events: SharedFlow<CameraEvent> = _events.asSharedFlow()

    private val mainExecutor = ContextCompat.getMainExecutor(context)
    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    private var provider: ProcessCameraProvider? = null
    private var extensions: ExtensionsManager? = null
    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var videoConfig: VideoConfig? = null
    private var analysis: ImageAnalysis? = null
    private var recording: Recording? = null
    private var barcodeScanner: BarcodeScanner? = null

    private var zoomObserver: Observer<ZoomState>? = null
    private var cameraStateObserver: Observer<XCameraState>? = null
    private var observedInfo: CameraInfo? = null

    /** Explicit camera from the lens picker; null = platform default for the facing. */
    private var requestedCameraId: String? = null
    private var targetRotation: Int = Surface.ROTATION_0
    private val liveExposure = AtomicReference<LiveExposure?>()

    /** Screen-flash implementation supplied by the activity (front cameras without a flash unit). */
    var screenFlash: ImageCapture.ScreenFlash? = null

    /** Whether the histogram analyzer should run in photo/pro modes. */
    var histogramEnabled: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            if (_state.value.ready && _state.value.mode.isPhotoLike) rebind()
        }

    /** Identifies a VideoCapture configuration so it is only rebuilt when it changes. */
    private data class VideoConfig(
        val quality: VideoQualityOption,
        val hdr: Boolean,
        val stabilization: Boolean,
        val mirror: Boolean,
    )

    /**
     * Initialises CameraX and binds the first camera.
     *
     * @return false if no camera could be bound at all.
     */
    suspend fun start(mode: CaptureMode, front: Boolean, cameraId: String?, flash: FlashOption, aspect: AspectOption, hdr: Boolean): Boolean {
        val p = ProcessCameraProvider.getInstance(context).await()
        provider = p
        loadExtensionsInBackground(p)
        requestedCameraId = cameraId?.takeIf { id -> p.availableCameraInfos.any { idOf(it) == id } }
        _state.update {
            it.copy(
                mode = mode,
                isFront = front,
                flash = flash,
                aspect = aspect,
                hdrEnabled = hdr,
                lenses = describeLenses(p.availableCameraInfos),
            )
        }
        return bind()
    }

    /**
     * Extension discovery can block for tens of seconds (on some Samsung builds
     * the framework extension proxy times out ~2 s per query), and both
     * ExtensionsManager initialisation and isExtensionAvailable() block their
     * calling thread. All of it therefore runs on a background thread, results
     * are cached per camera, and the main thread only ever reads the cache.
     */
    private fun loadExtensionsInBackground(p: ProcessCameraProvider) {
        val selectors: Map<String, CameraSelector> = buildMap {
            put(DEFAULT_BACK_KEY, CameraSelector.DEFAULT_BACK_CAMERA)
            put(DEFAULT_FRONT_KEY, CameraSelector.DEFAULT_FRONT_CAMERA)
            p.availableCameraInfos.forEach { info -> idOf(info).let { id -> put(id, selectorForId(id)) } }
        }
        extensionExecutor.execute {
            val manager = runCatching { ExtensionsManager.getInstanceAsync(context, p).get(EXTENSION_INIT_TIMEOUT_S, TimeUnit.SECONDS) }
                .onFailure { Log.w(TAG, "Extensions unavailable", it) }
                .getOrNull() ?: return@execute
            val cache = selectors.mapValues { (_, selector) ->
                listOf(ExtensionIds.NIGHT, ExtensionIds.BOKEH, ExtensionIds.HDR).filter { mode ->
                    runCatching { manager.isExtensionAvailable(selector, mode) }.getOrDefault(false)
                }.toSet()
            }
            if (BuildConfig.DEBUG) Log.d(TAG, "Extension availability: $cache")
            mainExecutor.execute {
                extensions = manager
                extensionCache = cache
                refreshExtensionAvailability()
            }
        }
    }

    /** Recomputes extension-backed modes for the bound camera without rebinding it. */
    private fun refreshExtensionAvailability() {
        val supported = supportedExtensions(boundExtensionKey ?: return)
        _state.update {
            it.copy(
                availableModes = CaptureMode.available(supported),
                hdrAvailable = ExtensionIds.HDR in supported && it.mode == CaptureMode.PHOTO,
            )
        }
        // HDR was requested before extensions were known: rebind once so it takes effect.
        val st = _state.value
        if (st.hdrEnabled && st.hdrAvailable && recording == null) rebind()
    }

    /** Cached extension modes for a camera key (Camera2 id or default-facing key); never blocks. */
    private fun supportedExtensions(key: String): Set<Int> = extensionCache[key].orEmpty()

    private fun selectorForId(cameraId: String): CameraSelector =
        CameraSelector.Builder().addCameraFilter { list -> list.filter { idOf(it) == cameraId } }.build()

    private val extensionExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var extensionCache: Map<String, Set<Int>> = emptyMap()

    /** Extension-cache key of the currently bound camera. */
    private var boundExtensionKey: String? = null

    // ---------------------------------------------------------------------
    // Binding
    // ---------------------------------------------------------------------

    private fun rebind() {
        if (provider != null) bind()
    }

    /**
     * Binds the requested camera, falling back to a reduced configuration and
     * then to every other camera (same facing first) if binding fails.
     */
    private fun bind(): Boolean {
        val p = provider ?: return false
        val st = _state.value
        val infos = p.availableCameraInfos
        if (infos.isEmpty()) {
            emit(CameraEvent.Error("No cameras available on this device"))
            return false
        }
        val sameFacing = infos.filter { isFront(it) == st.isFront }.map { idOf(it) }
        val otherFacing = infos.filter { isFront(it) != st.isFront }.map { idOf(it) }
        // null = the platform default (logical multi-camera on most phones).
        val candidates: List<String?> = buildList {
            if (requestedCameraId != null) add(requestedCameraId) else add(null)
            addAll(sameFacing)
            addAll(otherFacing)
        }.distinct()

        var lastError: Throwable? = null
        for ((index, id) in candidates.withIndex()) {
            for (safeMode in listOf(false, true)) {
                try {
                    val boundId = bindTo(p, id, st, safeMode)
                    if (index > 0) {
                        emit(CameraEvent.CameraFallback(requestedCameraId, boundId))
                    }
                    return true
                } catch (e: Exception) {
                    lastError = e
                    Log.w(TAG, "Bind failed for camera=${id ?: "default"} safe=$safeMode", e)
                }
            }
        }
        _state.update { it.copy(ready = false) }
        emit(CameraEvent.Error("Could not open any camera", lastError))
        return false
    }

    /**
     * Binds one camera. In [safeMode] optional features (analysis, Ultra HDR,
     * RAW, ZSL, extensions, HDR video) are dropped to maximise compatibility.
     *
     * @return the Camera2 id that was bound.
     */
    private fun bindTo(p: ProcessCameraProvider, cameraId: String?, st: CameraState, safeMode: Boolean): String {
        val facingFront = if (cameraId == null) st.isFront else p.availableCameraInfos.first { idOf(it) == cameraId }.let(::isFront)
        val baseSelector = if (cameraId == null) {
            CameraSelector.Builder()
                .requireLensFacing(if (facingFront) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK)
                .build()
        } else {
            selectorForId(cameraId)
        }
        val baseInfo = baseSelector.filter(p.availableCameraInfos).firstOrNull()
            ?: throw IllegalStateException("No camera matches ${cameraId ?: "default facing"}")

        val ext = extensions
        val extensionKey = cameraId ?: if (facingFront) DEFAULT_FRONT_KEY else DEFAULT_BACK_KEY
        val supportedExtensions = supportedExtensions(extensionKey)

        // Extension modes that disappeared on this camera fall back to PHOTO.
        var mode = st.mode
        if (mode.extensionMode != null && mode.extensionMode !in supportedExtensions) mode = CaptureMode.PHOTO
        val extensionMode: Int? = when {
            safeMode -> null
            mode.extensionMode != null -> mode.extensionMode
            mode == CaptureMode.PHOTO && st.hdrEnabled && ExtensionIds.HDR in supportedExtensions -> ExtensionIds.HDR
            else -> null
        }
        val selector = if (extensionMode != null && ext != null) ext.getExtensionEnabledCameraSelector(baseSelector, extensionMode) else baseSelector

        // A persistent recording keeps running across unbind/rebind of its VideoCapture.
        p.unbindAll()

        val useCases = buildUseCases(mode, baseInfo, extensionMode, baseSelector, facingFront, safeMode)
        val ratio = if (mode == CaptureMode.VIDEO) Rational(9, 16) else Rational(st.aspect.width, st.aspect.height)
        val viewPort = ViewPort.Builder(ratio, previewView.display?.rotation ?: Surface.ROTATION_0)
            .setScaleType(ViewPort.FILL_CENTER)
            .build()
        val group = UseCaseGroup.Builder().setViewPort(viewPort).apply { useCases.forEach { addUseCase(it) } }.build()
        val bound = p.bindToLifecycle(lifecycleOwner, selector, group)
        camera = bound
        boundExtensionKey = extensionKey

        val boundId = idOf(baseInfo)
        observeCamera(bound.cameraInfo)
        val exposure = bound.cameraInfo.exposureState
        _state.update {
            it.copy(
                ready = true,
                mode = mode,
                availableModes = CaptureMode.available(supportedExtensions),
                currentCameraId = boundId,
                isFront = facingFront,
                hasFlashUnit = bound.cameraInfo.hasFlashUnit(),
                hdrAvailable = ExtensionIds.HDR in supportedExtensions && mode == CaptureMode.PHOTO,
                evIndex = exposure.exposureCompensationIndex,
                evMin = if (exposure.isExposureCompensationSupported) exposure.exposureCompensationRange.lower else 0,
                evMax = if (exposure.isExposureCompensationSupported) exposure.exposureCompensationRange.upper else 0,
                evStep = exposure.exposureCompensationStep.toFloat(),
                manualCapabilities = if (extensionMode == null) manualCapabilities(baseInfo) else ManualCapabilities.NONE,
                manual = if (mode == CaptureMode.PRO) it.manual else ManualSettings(),
                focusLocked = false,
            )
        }
        applyFlash()
        applyManual()
        if (BuildConfig.DEBUG) Log.d(TAG, "Bound camera=$boundId mode=$mode ext=$extensionMode safe=$safeMode uses=${useCases.size}")
        return boundId
    }

    private fun buildUseCases(
        mode: CaptureMode,
        info: CameraInfo,
        extensionMode: Int?,
        baseSelector: CameraSelector,
        front: Boolean,
        safeMode: Boolean,
    ): List<UseCase> {
        val st = _state.value
        val aspectStrategy = when {
            mode == CaptureMode.VIDEO || st.aspect == AspectOption.RATIO_16_9 -> AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY
            else -> AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY
        }

        val previewBuilder = Preview.Builder()
            .setResolutionSelector(ResolutionSelector.Builder().setAspectRatioStrategy(aspectStrategy).build())
        if (extensionMode == null) {
            Camera2Interop.Extender(previewBuilder).setSessionCaptureCallback(liveExposureCallback)
        }
        val preview = previewBuilder.build().also { it.surfaceProvider = previewView.surfaceProvider }
        val useCases = mutableListOf<UseCase>(preview)

        imageCapture = null
        analysis = null
        when (mode) {
            CaptureMode.VIDEO -> useCases += obtainVideoCapture(info, front, safeMode)
            CaptureMode.SCAN -> useCases += buildBarcodeAnalysis()
            else -> {
                useCases += buildImageCapture(mode, info, extensionMode, front, safeMode)
                val analysisAllowed = extensionMode == null ||
                    runCatching { extensions?.isImageAnalysisSupported(baseSelector, extensionMode) == true }.getOrDefault(false)
                if (!safeMode && histogramEnabled && analysisAllowed) useCases += buildHistogramAnalysis(aspectStrategy)
            }
        }
        if (mode != CaptureMode.VIDEO && recording != null) {
            // Never drop an active persistent recording when switching mode.
            videoCapture?.let { useCases += it }
        }
        return useCases
    }

    private fun buildImageCapture(mode: CaptureMode, info: CameraInfo, extensionMode: Int?, front: Boolean, safeMode: Boolean): ImageCapture {
        val captureMode = when {
            safeMode -> ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
            else -> when (settings.photoQuality) {
                PhotoQualityOption.QUALITY -> ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
                PhotoQualityOption.LATENCY -> ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
                PhotoQualityOption.ZERO_SHUTTER_LAG ->
                    if (info.isZslSupported && extensionMode == null) ImageCapture.CAPTURE_MODE_ZERO_SHUTTER_LAG
                    else ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
            }
        }
        val formats = if (extensionMode == null && !safeMode) {
            runCatching { ImageCapture.getImageCaptureCapabilities(info).supportedOutputFormats }.getOrDefault(emptySet())
        } else emptySet()
        val outputFormat = when {
            mode == CaptureMode.PRO && settings.raw && ImageCapture.OUTPUT_FORMAT_RAW_JPEG in formats -> ImageCapture.OUTPUT_FORMAT_RAW_JPEG
            settings.ultraHdr && ImageCapture.OUTPUT_FORMAT_JPEG_ULTRA_HDR in formats -> ImageCapture.OUTPUT_FORMAT_JPEG_ULTRA_HDR
            else -> ImageCapture.OUTPUT_FORMAT_JPEG
        }
        val strategy = if (_state.value.aspect == AspectOption.RATIO_16_9) {
            AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY
        } else AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY
        return ImageCapture.Builder()
            .setCaptureMode(captureMode)
            .setJpegQuality(settings.jpegQuality)
            .setOutputFormat(outputFormat)
            .setTargetRotation(targetRotation)
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setAspectRatioStrategy(strategy)
                    .setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY)
                    .build()
            )
            .build()
            .also {
                if (front) it.screenFlash = screenFlash
                imageCapture = it
            }
    }

    /** Reuses the existing VideoCapture when possible so persistent recordings survive rebinds. */
    private fun obtainVideoCapture(info: CameraInfo, front: Boolean, safeMode: Boolean): VideoCapture<Recorder> {
        val caps = Recorder.getVideoCapabilities(info)
        val config = VideoConfig(
            quality = settings.videoQuality,
            hdr = !safeMode && settings.videoHdr && DynamicRange.HLG_10_BIT in caps.supportedDynamicRanges,
            stabilization = !safeMode && settings.videoStabilization && caps.isStabilizationSupported,
            mirror = settings.mirrorFront,
        )
        videoCapture?.let { existing -> if (recording != null || config == videoConfig) return existing }

        val preferred = when (config.quality) {
            VideoQualityOption.UHD -> Quality.UHD
            VideoQualityOption.FHD -> Quality.FHD
            VideoQualityOption.HD -> Quality.HD
            VideoQualityOption.SD -> Quality.SD
        }
        val recorder = Recorder.Builder()
            .setQualitySelector(QualitySelector.from(preferred, FallbackStrategy.lowerQualityOrHigherThan(preferred)))
            .build()
        return VideoCapture.Builder(recorder)
            .setDynamicRange(if (config.hdr) DynamicRange.HLG_10_BIT else DynamicRange.SDR)
            .setVideoStabilizationEnabled(config.stabilization)
            .setMirrorMode(if (config.mirror) MirrorMode.MIRROR_MODE_ON_FRONT_ONLY else MirrorMode.MIRROR_MODE_OFF)
            .setTargetRotation(targetRotation)
            .build()
            .also {
                videoCapture = it
                videoConfig = config
            }
    }

    private fun buildHistogramAnalysis(aspect: AspectRatioStrategy): ImageAnalysis {
        var lastEmit = 0L
        return ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setAspectRatioStrategy(aspect)
                    .setResolutionStrategy(ResolutionStrategy(Size(640, 480), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER))
                    .build()
            )
            .build()
            .also { a ->
                a.setAnalyzer(analysisExecutor) { proxy ->
                    try {
                        val now = SystemClock.uptimeMillis()
                        if (now - lastEmit >= HISTOGRAM_INTERVAL_MS) {
                            lastEmit = now
                            val plane = proxy.planes[0]
                            val histogram = Histogram.fromLuma(plane.buffer, proxy.width, proxy.height, plane.rowStride, plane.pixelStride)
                            emit(CameraEvent.HistogramUpdated(histogram))
                        }
                    } finally {
                        proxy.close()
                    }
                }
                analysis = a
            }
    }

    private fun buildBarcodeAnalysis(): ImageAnalysis {
        val scanner = barcodeScanner ?: BarcodeScanning.getClient().also { barcodeScanner = it }
        val transformFactory = ImageProxyTransformFactory().apply { isUsingRotationDegrees = true }
        return ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setResolutionStrategy(ResolutionStrategy(Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                    .build()
            )
            .build()
            .also { a ->
                a.setAnalyzer(analysisExecutor) { proxy ->
                    val media = proxy.image
                    if (media == null) {
                        proxy.close()
                        return@setAnalyzer
                    }
                    val sourceTransform = transformFactory.getOutputTransform(proxy)
                    val input = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
                    scanner.process(input).addOnCompleteListener(mainExecutor) { task ->
                        proxy.close()
                        val codes = task.result.orEmpty().mapNotNull { it.rawValue?.let { raw -> raw to it.boundingBox } }
                        if (!task.isSuccessful || codes.isEmpty()) return@addOnCompleteListener
                        val target = previewView.outputTransform
                        val mapper = target?.let { CoordinateTransform(sourceTransform, it) }
                        val results = codes.map { (raw, rect) ->
                            val box = rect?.let { r -> RectF(r).also { mapper?.mapRect(it) } }
                            DetectedCode(ScanContent.parse(raw), box)
                        }
                        emit(CameraEvent.BarcodesDetected(results))
                    }
                }
                analysis = a
            }
    }

    private val liveExposureCallback = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
            val iso = result.get(CaptureResult.SENSOR_SENSITIVITY) ?: return
            val exposure = result.get(CaptureResult.SENSOR_EXPOSURE_TIME) ?: return
            val focus = result.get(CaptureResult.LENS_FOCUS_DISTANCE) ?: 0f
            liveExposure.set(LiveExposure(iso, exposure, focus))
        }
    }

    private fun observeCamera(info: CameraInfo) {
        observedInfo?.let { old ->
            zoomObserver?.let { old.zoomState.removeObserver(it) }
            cameraStateObserver?.let { old.cameraState.removeObserver(it) }
        }
        val zoom = Observer<ZoomState> { z ->
            _state.update { it.copy(zoomRatio = z.zoomRatio, minZoom = z.minZoomRatio, maxZoom = z.maxZoomRatio) }
        }
        val cameraState = Observer<XCameraState> { s ->
            val error = s.error ?: return@Observer
            val message = when (error.code) {
                XCameraState.ERROR_CAMERA_IN_USE, XCameraState.ERROR_MAX_CAMERAS_IN_USE -> "Camera is in use by another app"
                XCameraState.ERROR_CAMERA_DISABLED -> "Camera is disabled by device policy"
                XCameraState.ERROR_DO_NOT_DISTURB_MODE_ENABLED -> "Camera unavailable while Do Not Disturb is on"
                XCameraState.ERROR_CAMERA_FATAL_ERROR -> "Camera stopped working"
                else -> "Camera error (${error.code})"
            }
            emit(CameraEvent.Error(message, error.cause))
        }
        info.zoomState.observe(lifecycleOwner, zoom)
        info.cameraState.observe(lifecycleOwner, cameraState)
        observedInfo = info
        zoomObserver = zoom
        cameraStateObserver = cameraState
    }

    // ---------------------------------------------------------------------
    // Mode, lens and setting changes
    // ---------------------------------------------------------------------

    fun setMode(mode: CaptureMode) {
        val st = _state.value
        if (st.mode == mode) return
        if (recording != null && mode != CaptureMode.VIDEO) return
        // Video and scan only have a torch; photo modes never keep it on. Leaving Pro restores auto
        // exposure because applyManual() clears Camera2 options outside PRO.
        val flash = when {
            mode.usesTorch -> if (st.flash == FlashOption.TORCH) FlashOption.TORCH else FlashOption.OFF
            st.flash == FlashOption.TORCH -> FlashOption.OFF
            else -> st.flash
        }
        _state.update { it.copy(mode = mode, flash = flash) }
        rebind()
    }

    /** Toggles front/back, returning to the platform default camera for that facing. */
    fun switchFacing() {
        requestedCameraId = null
        _state.update { it.copy(isFront = !it.isFront, flash = if (it.flash == FlashOption.TORCH) FlashOption.OFF else it.flash) }
        rebind()
    }

    /** Binds a specific Camera2 id chosen in the lens picker. */
    fun selectCamera(cameraId: String) {
        val lens = _state.value.lenses.firstOrNull { it.cameraId == cameraId } ?: return
        requestedCameraId = cameraId
        _state.update { it.copy(isFront = lens.isFront) }
        rebind()
    }

    /** Returns to the platform default camera for the given facing. */
    fun selectCameraDefault(front: Boolean) {
        requestedCameraId = null
        _state.update { it.copy(isFront = front) }
        rebind()
    }

    /** Explicitly selected camera id, or null when using the default for the current facing. */
    val selectedCameraId: String? get() = requestedCameraId

    fun setAspect(aspect: AspectOption) {
        if (_state.value.aspect == aspect) return
        _state.update { it.copy(aspect = aspect) }
        if (_state.value.mode != CaptureMode.VIDEO) rebind()
    }

    fun setHdr(enabled: Boolean) {
        if (_state.value.hdrEnabled == enabled) return
        _state.update { it.copy(hdrEnabled = enabled) }
        if (_state.value.mode == CaptureMode.PHOTO) rebind()
    }

    fun setFlash(option: FlashOption) {
        _state.update { it.copy(flash = option) }
        applyFlash()
    }

    /** Re-reads capture-affecting preferences (called when returning from Settings). */
    fun refreshFromSettings() = rebind()

    private fun applyFlash() {
        val st = _state.value
        val cam = camera ?: return
        val torch = st.mode.usesTorch && st.flash == FlashOption.TORCH && cam.cameraInfo.hasFlashUnit()
        cam.cameraControl.enableTorch(torch)
        val ic = imageCapture ?: return
        ic.flashMode = when (st.flash) {
            FlashOption.OFF, FlashOption.TORCH -> ImageCapture.FLASH_MODE_OFF
            FlashOption.AUTO -> if (st.hasFlashUnit) ImageCapture.FLASH_MODE_AUTO else ImageCapture.FLASH_MODE_OFF
            FlashOption.ON -> when {
                st.hasFlashUnit -> ImageCapture.FLASH_MODE_ON
                st.isFront && ic.screenFlash != null -> ImageCapture.FLASH_MODE_SCREEN
                else -> ImageCapture.FLASH_MODE_OFF
            }
        }
    }

    // ---------------------------------------------------------------------
    // Zoom, focus, exposure
    // ---------------------------------------------------------------------

    fun setZoomRatio(ratio: Float) {
        val st = _state.value
        camera?.cameraControl?.setZoomRatio(ratio.coerceIn(st.minZoom, st.maxZoom))
    }

    fun setExposureIndex(index: Int) {
        val st = _state.value
        if (!st.evSupported) return
        val clamped = index.coerceIn(st.evMin, st.evMax)
        camera?.cameraControl?.setExposureCompensationIndex(clamped)
        _state.update { it.copy(evIndex = clamped) }
    }

    /**
     * Focuses and meters at a PreviewView coordinate.
     * @param lock keep focus/exposure locked until [unlockFocus] (long-press).
     */
    fun focusAt(x: Float, y: Float, lock: Boolean) {
        val cam = camera ?: return
        val st = _state.value
        val point = previewView.meteringPointFactory.createPoint(x, y)
        var flags = FocusMeteringAction.FLAG_AE or FocusMeteringAction.FLAG_AWB
        if (!(st.mode == CaptureMode.PRO && st.manual.focusDiopters != null)) flags = flags or FocusMeteringAction.FLAG_AF
        val action = FocusMeteringAction.Builder(point, flags).apply {
            if (lock) disableAutoCancel() else setAutoCancelDuration(AUTO_CANCEL_SECONDS, TimeUnit.SECONDS)
        }.build()
        val future = cam.cameraControl.startFocusAndMetering(action)
        _state.update { it.copy(focusLocked = lock) }
        future.addListener({
            val success = runCatching { future.get().isFocusSuccessful }.getOrDefault(false)
            emit(CameraEvent.FocusResult(success))
        }, mainExecutor)
    }

    fun unlockFocus() {
        camera?.cameraControl?.cancelFocusAndMetering()
        _state.update { it.copy(focusLocked = false) }
    }

    // ---------------------------------------------------------------------
    // Pro mode
    // ---------------------------------------------------------------------

    fun setManual(manual: ManualSettings) {
        _state.update { it.copy(manual = manual) }
        applyManual()
    }

    /** Most recent auto-exposure values from the preview stream (null until the first frame). */
    fun liveExposure(): LiveExposure? = liveExposure.get()

    private fun applyManual() {
        val cam = camera ?: return
        val st = _state.value
        val control = Camera2CameraControl.from(cam.cameraControl)
        val m = st.manual
        if (st.mode != CaptureMode.PRO || m.isAllAuto) {
            control.clearCaptureRequestOptions()
            return
        }
        val caps = st.manualCapabilities
        val builder = CaptureRequestOptions.Builder()
        if (caps.manualSensor && (m.iso != null || m.exposureNs != null)) {
            // Camera2 has no shutter/ISO priority: with AE off both must be set, so the
            // auto side is frozen at its last metered value.
            val live = liveExposure.get()
            builder.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF)
            builder.setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, m.iso ?: live?.iso ?: DEFAULT_ISO)
            builder.setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, m.exposureNs ?: live?.exposureNs ?: DEFAULT_EXPOSURE_NS)
        }
        if (m.awbMode != ManualSettings.AWB_AUTO && m.awbMode in caps.awbModes) {
            builder.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, m.awbMode)
        }
        if (caps.manualFocus && m.focusDiopters != null) {
            builder.setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_OFF)
            builder.setCaptureRequestOption(CaptureRequest.LENS_FOCUS_DISTANCE, m.focusDiopters)
        }
        control.setCaptureRequestOptions(builder.build())
    }

    // ---------------------------------------------------------------------
    // Capture
    // ---------------------------------------------------------------------

    fun setTargetRotation(rotation: Int) {
        targetRotation = rotation
        imageCapture?.targetRotation = rotation
        analysis?.targetRotation = rotation
        // VideoCapture rotation is latched when a recording starts; never change it mid-recording.
        if (recording == null) videoCapture?.targetRotation = rotation
    }

    /** Saves a photo to MediaStore (DCIM/CustomCamera). Results arrive as [CameraEvent]s. */
    fun takePhoto(location: Location?) {
        val ic = imageCapture ?: return emit(CameraEvent.Error("Camera not ready"))
        val time = Date()
        val metadata = ImageCapture.Metadata().apply {
            isReversedHorizontal = _state.value.isFront && settings.mirrorFront
            this.location = location
        }
        val jpeg = MediaStoreHelper.target(MediaNaming.Kind.PHOTO, time)
        val jpegOptions = ImageCapture.OutputFileOptions.Builder(context.contentResolver, jpeg.collection, jpeg.values)
            .setMetadata(metadata)
            .build()
        val callback = savedCallback()
        if (ic.outputFormat == ImageCapture.OUTPUT_FORMAT_RAW_JPEG) {
            val raw = MediaStoreHelper.target(MediaNaming.Kind.RAW, time)
            val rawOptions = ImageCapture.OutputFileOptions.Builder(context.contentResolver, raw.collection, raw.values)
                .setMetadata(metadata)
                .build()
            ic.takePicture(rawOptions, jpegOptions, mainExecutor, callback)
        } else {
            ic.takePicture(jpegOptions, mainExecutor, callback)
        }
    }

    /** Captures into a private file (used for ACTION_IMAGE_CAPTURE review). */
    fun takePhotoToFile(file: File) {
        val ic = imageCapture ?: return emit(CameraEvent.Error("Camera not ready"))
        val metadata = ImageCapture.Metadata().apply { isReversedHorizontal = _state.value.isFront && settings.mirrorFront }
        val options = ImageCapture.OutputFileOptions.Builder(file).setMetadata(metadata).build()
        ic.takePicture(options, mainExecutor, savedCallback())
    }

    private fun savedCallback() = object : ImageCapture.OnImageSavedCallback {
        override fun onCaptureStarted() = emit(CameraEvent.CaptureStarted)
        override fun onImageSaved(output: ImageCapture.OutputFileResults) = emit(CameraEvent.PhotoSaved(output.savedUri))
        override fun onError(exception: ImageCaptureException) =
            emit(CameraEvent.Error("Photo capture failed: ${exception.message ?: exception.imageCaptureError}", exception))
    }

    val isRecording: Boolean get() = recording != null

    /**
     * Starts a persistent recording (survives camera switches).
     * Audio is recorded only when [withAudio] is true and the permission is granted.
     */
    @SuppressLint("MissingPermission") // Checked explicitly below.
    fun startRecording(withAudio: Boolean, location: Location?) {
        val vc = videoCapture ?: return emit(CameraEvent.Error("Video not ready"))
        if (recording != null) return
        val target = MediaStoreHelper.target(MediaNaming.Kind.VIDEO, Date())
        val options = MediaStoreOutputOptions.Builder(context.contentResolver, target.collection)
            .setContentValues(target.values)
            .apply { location?.let { setLocation(it) } }
            .build()
        var pending = vc.output.prepareRecording(context, options).asPersistentRecording()
        val audioGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (withAudio && audioGranted) pending = pending.withAudioEnabled()
        recording = pending.start(mainExecutor) { event -> onRecordEvent(event) }
    }

    private fun onRecordEvent(event: VideoRecordEvent) {
        val nanos = event.recordingStats.recordedDurationNanos
        when (event) {
            is VideoRecordEvent.Start -> _state.update { it.copy(recording = RecordingStatus.RECORDING, recordingNanos = 0) }
            is VideoRecordEvent.Pause -> _state.update { it.copy(recording = RecordingStatus.PAUSED, recordingNanos = nanos) }
            is VideoRecordEvent.Resume -> _state.update { it.copy(recording = RecordingStatus.RECORDING, recordingNanos = nanos) }
            is VideoRecordEvent.Status -> _state.update { it.copy(recordingNanos = nanos) }
            is VideoRecordEvent.Finalize -> {
                recording = null
                _state.update { it.copy(recording = RecordingStatus.IDLE, recordingNanos = 0) }
                val uri = event.outputResults.outputUri
                if (event.hasError() && event.error != VideoRecordEvent.Finalize.ERROR_SOURCE_INACTIVE) {
                    if (uri != android.net.Uri.EMPTY) emit(CameraEvent.VideoSaved(uri))
                    emit(CameraEvent.Error("Recording stopped: error ${event.error}", event.cause))
                } else if (uri != android.net.Uri.EMPTY) {
                    emit(CameraEvent.VideoSaved(uri))
                }
                // Apply any rotation change that happened while recording.
                videoCapture?.targetRotation = targetRotation
            }
        }
    }

    fun pauseRecording() = recording?.pause()
    fun resumeRecording() = recording?.resume()
    fun stopRecording() = recording?.stop()

    /** Releases resources that outlive the lifecycle-bound use cases. */
    fun release() {
        recording?.stop()
        recording = null
        barcodeScanner?.close()
        barcodeScanner = null
        analysisExecutor.shutdown()
        extensionExecutor.shutdownNow()
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private fun emit(event: CameraEvent) {
        if (!_events.tryEmit(event)) Log.w(TAG, "Dropped event $event")
    }

    private fun idOf(info: CameraInfo): String = Camera2CameraInfo.from(info).cameraId

    private fun isFront(info: CameraInfo): Boolean = info.lensFacing == CameraSelector.LENS_FACING_FRONT

    private fun describeLenses(infos: List<CameraInfo>): List<LensDescriptor> = infos.mapNotNull { info ->
        runCatching {
            val c2 = Camera2CameraInfo.from(info)
            val focal = c2.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull()
            val sensor = c2.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
            val eq = if (focal != null && sensor != null) LensMath.equivalentFocalLength(focal, sensor.width, sensor.height) else null
            LensDescriptor(
                cameraId = c2.cameraId,
                isFront = isFront(info),
                equivalentFocalMm = eq,
                category = LensMath.category(eq),
                isLogicalMultiCamera = info.isLogicalMultiCameraSupported,
                hasFlash = info.hasFlashUnit(),
            )
        }.onFailure { Log.w(TAG, "Could not describe camera", it) }.getOrNull()
    }

    private fun manualCapabilities(info: CameraInfo): ManualCapabilities = runCatching {
        val c2 = Camera2CameraInfo.from(info)
        val caps = c2.getCameraCharacteristic(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: IntArray(0)
        val iso = c2.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
        val exposure = c2.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
        val awb = c2.getCameraCharacteristic(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES) ?: IntArray(0)
        val minFocus = c2.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f
        val manualSensor = CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR in caps
        ManualCapabilities(
            manualSensor = manualSensor,
            isoRange = iso?.let { it.lower..it.upper }?.takeIf { manualSensor },
            exposureRangeNs = exposure?.let { it.lower..minOf(it.upper, MAX_EXPOSURE_NS) }?.takeIf { manualSensor },
            minFocusDiopters = if (CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR in caps) minFocus else 0f,
            awbModes = awb.filter { it != CameraMetadata.CONTROL_AWB_MODE_OFF }.toList(),
        )
    }.getOrDefault(ManualCapabilities.NONE)

    companion object {
        private const val TAG = "CameraController"
        private const val DEFAULT_BACK_KEY = "default-back"
        private const val DEFAULT_FRONT_KEY = "default-front"
        private const val EXTENSION_INIT_TIMEOUT_S = 60L
        private const val AUTO_CANCEL_SECONDS = 5L
        private const val HISTOGRAM_INTERVAL_MS = 125L
        private const val DEFAULT_ISO = 100
        private const val DEFAULT_EXPOSURE_NS = 16_666_666L

        /** Longer exposures stall the preview for seconds per frame; cap the UI at 4 s. */
        private const val MAX_EXPOSURE_NS = 4_000_000_000L

        init {
            // ExtensionIds mirrors ExtensionMode so the core layer stays Android-free.
            check(ExtensionIds.NIGHT == ExtensionMode.NIGHT && ExtensionIds.BOKEH == ExtensionMode.BOKEH && ExtensionIds.HDR == ExtensionMode.HDR) {
                "ExtensionIds out of sync with androidx.camera.extensions.ExtensionMode"
            }
        }
    }
}
