package com.customcamera.app.ui

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.OrientationEventListener
import android.view.ScaleGestureDetector
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.customcamera.app.R
import com.customcamera.app.camera.CameraController
import com.customcamera.app.camera.CameraEvent
import com.customcamera.app.camera.CameraState
import com.customcamera.app.camera.RecordingStatus
import com.customcamera.app.core.AspectOption
import com.customcamera.app.core.CaptureMode
import com.customcamera.app.core.FlashOption
import com.customcamera.app.core.OrientationMath
import com.customcamera.app.core.ScanContent
import com.customcamera.app.core.TimeFormat
import com.customcamera.app.core.TimerOption
import com.customcamera.app.core.VideoQualityOption
import com.customcamera.app.core.VolumeKeyAction
import com.customcamera.app.core.ZoomMath
import com.customcamera.app.databinding.ActivityCameraBinding
import com.customcamera.app.gallery.MediaRepository
import com.customcamera.app.gallery.MediaViewerActivity
import com.customcamera.app.scan.ScanActions
import com.customcamera.app.settings.AppSettings
import com.customcamera.app.settings.SettingsActivity
import com.customcamera.app.util.AppShortcuts
import com.customcamera.app.util.BitmapDecoding
import com.customcamera.app.util.CaptureFeedback
import com.customcamera.app.util.LocationTracker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * The viewfinder. Launcher entry point and handler for system camera intents
 * (IMAGE_CAPTURE, VIDEO_CAPTURE, STILL_IMAGE_CAMERA, VIDEO_CAMERA).
 */
class CameraActivity : AppCompatActivity() {

    /** How the activity was launched; capture intents return a result instead of saving to the gallery. */
    private enum class LaunchMode { NORMAL, IMAGE_CAPTURE, VIDEO_CAPTURE }

    private lateinit var binding: ActivityCameraBinding
    private lateinit var settings: AppSettings
    private lateinit var controller: CameraController
    private lateinit var proPanel: ProPanelController
    private lateinit var feedback: CaptureFeedback
    private lateinit var location: LocationTracker
    private val repository by lazy { MediaRepository.get(this) }

    private var launchMode = LaunchMode.NORMAL
    private var secureLaunch = false
    private var cameraStarted = false
    private var renderedModes: List<CaptureMode> = emptyList()
    private var renderedPresets: List<Float> = emptyList()
    private var lastRendered: CameraState? = null
    private var updatingTabs = false

    private var deviceOrientation = 0
    private var countdownJob: Job? = null
    private var liveLabelJob: Job? = null
    private var captureBusy = false
    private var currentScan: ScanContent? = null
    private var lastCaptureUri: Uri? = null
    private var reviewFile: File? = null

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> settingsDirty = true }
    private var settingsDirty = false

    private val requiredPermissions: Array<String>
        get() = buildList {
            add(Manifest.permission.CAMERA)
            // Writing to MediaStore outside our own entries needs this before scoped storage.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }.toTypedArray()

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result[Manifest.permission.CAMERA] == true || hasCameraPermission()) {
            binding.permissionPanel.isVisible = false
            startCamera()
        } else {
            showPermissionPanel()
        }
    }

    private val audioPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) Toast.makeText(this, R.string.permission_audio_denied, Toast.LENGTH_SHORT).show()
        beginRecording()
    }

    // ---------------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        binding = ActivityCameraBinding.inflate(layoutInflater)
        setContentView(binding.root)

        settings = AppSettings(this)
        feedback = CaptureFeedback()
        location = LocationTracker(this)
        controller = CameraController(this, this, binding.previewView, settings)
        proPanel = ProPanelController(this, binding.proPanel, controller)

        parseLaunchIntent(intent)
        setupWindow()
        setupControls()
        setupGestures()
        setupOrientation()
        collectCamera()

        binding.screenFlashView.setScreenFlashWindow(window)
        controller.screenFlash = binding.screenFlashView.screenFlash
        settings.registerListener(prefListener)

        if (hasCameraPermission()) startCamera() else permissionLauncher.launch(requiredPermissions)
        if (savedInstanceState == null && launchMode == LaunchMode.NORMAL) AppShortcuts.publish(this)
    }

    override fun onResume() {
        super.onResume()
        applyViewSettings()
        if (settingsDirty && cameraStarted) {
            settingsDirty = false
            controller.histogramEnabled = histogramWanted()
            controller.refreshFromSettings()
        }
        if (!cameraStarted && hasCameraPermission()) {
            binding.permissionPanel.isVisible = false
            startCamera()
        }
        if (settings.saveLocation) location.start()
        if (launchMode == LaunchMode.NORMAL && !secureLaunch) refreshThumbnail()
    }

    override fun onPause() {
        cancelCountdown()
        location.stop()
        // A recording in progress is stopped when the viewfinder leaves the screen,
        // matching system camera behaviour and avoiding a background camera.
        if (controller.isRecording) controller.stopRecording()
        super.onPause()
    }

    override fun onDestroy() {
        settings.unregisterListener(prefListener)
        orientationListener.disable()
        controller.release()
        feedback.release()
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        parseLaunchIntent(intent)
        applyLaunchModeUi()
        // Force the mode tabs to rebuild for the new launch mode.
        renderedModes = emptyList()
        if (cameraStarted) {
            controller.setMode(initialMode())
            render(controller.state.value)
        }
    }

    /** Hides gallery/settings entry points for capture intents and lock-screen launches. */
    private fun applyLaunchModeUi() {
        val restricted = launchMode != LaunchMode.NORMAL || secureLaunch
        binding.thumbnail.isVisible = !restricted
        binding.settingsButton.isVisible = !secureLaunch
        if (launchMode == LaunchMode.NORMAL) binding.reviewPanel.isVisible = false
    }

    // ---------------------------------------------------------------------
    // Setup
    // ---------------------------------------------------------------------

    private fun parseLaunchIntent(intent: Intent) {
        launchMode = when (intent.action) {
            MediaStore.ACTION_IMAGE_CAPTURE, MediaStore.ACTION_IMAGE_CAPTURE_SECURE -> LaunchMode.IMAGE_CAPTURE
            MediaStore.ACTION_VIDEO_CAPTURE -> LaunchMode.VIDEO_CAPTURE
            else -> LaunchMode.NORMAL
        }
        secureLaunch = intent.action == MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE ||
            intent.action == MediaStore.ACTION_IMAGE_CAPTURE_SECURE
        if (secureLaunch && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            // Usable from the lock screen; gallery and settings stay locked away below.
            setShowWhenLocked(true)
        }
    }

    private fun initialMode(): CaptureMode = when {
        launchMode == LaunchMode.IMAGE_CAPTURE -> CaptureMode.PHOTO
        launchMode == LaunchMode.VIDEO_CAPTURE -> CaptureMode.VIDEO
        intent.action == MediaStore.INTENT_ACTION_VIDEO_CAMERA -> CaptureMode.VIDEO
        intent.action == MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA ||
            intent.action == MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE -> CaptureMode.PHOTO
        intent.hasExtra(EXTRA_MODE) -> CaptureMode.fromKey(intent.getStringExtra(EXTRA_MODE))
        else -> settings.lastMode
    }

    private fun setupWindow() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, binding.root).apply {
            hide(WindowInsetsCompat.Type.statusBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val types = WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            val bars = insets.getInsetsIgnoringVisibility(types)
            val cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            binding.topBar.updateLayoutParams<androidx.constraintlayout.widget.ConstraintLayout.LayoutParams> {
                topMargin = max(bars.top, cutout.top)
            }
            binding.bottomRow.updateLayoutParams<androidx.constraintlayout.widget.ConstraintLayout.LayoutParams> {
                bottomMargin = bars.bottom
            }
            binding.controls.setPadding(bars.left, 0, bars.right, 0)
            binding.reviewActions.setPadding(24.dp, 24.dp, 24.dp, 24.dp + bars.bottom)
            insets
        }
        binding.root.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> layoutPreview(controller.state.value) }
    }

    private fun setupControls() {
        binding.shutterButton.setOnClickListener { onShutter() }
        binding.flipButton.setOnClickListener { flipCamera() }
        binding.thumbnail.setOnClickListener { openLastCapture() }
        binding.settingsButton.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        binding.lensButton.setOnClickListener { showLensPicker() }
        binding.flipButton.setOnLongClickListener { showLensPicker(); true }
        binding.pauseButton.setOnClickListener {
            if (controller.state.value.recording == RecordingStatus.PAUSED) controller.resumeRecording() else controller.pauseRecording()
        }
        binding.flashButton.setOnClickListener {
            val st = controller.state.value
            var next = FlashOption.next(st.flash, st.mode.usesTorch)
            // Front cameras without a flash unit use the screen, which has no "auto".
            if (!st.mode.usesTorch && !st.hasFlashUnit && next == FlashOption.AUTO) next = FlashOption.ON
            controller.setFlash(next)
            if (!st.mode.usesTorch) settings.flash = next
        }
        binding.timerButton.setOnClickListener {
            settings.timer = settings.timer.next()
            renderTopBar(controller.state.value)
        }
        binding.aspectButton.setOnClickListener {
            val st = controller.state.value
            if (st.mode == CaptureMode.VIDEO) {
                cycleVideoQuality()
            } else {
                val next = st.aspect.next()
                settings.aspect = next
                controller.setAspect(next)
            }
        }
        binding.hdrButton.setOnClickListener {
            val enabled = !controller.state.value.hdrEnabled
            controller.setHdr(enabled)
            getPreferences(MODE_PRIVATE).edit().putBoolean(STATE_HDR, enabled).apply()
        }
        binding.permissionButton.setOnClickListener {
            if (permanentlyDenied()) {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
            } else {
                permissionLauncher.launch(requiredPermissions)
            }
        }
        binding.modeTabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                if (updatingTabs) return
                val mode = tab.tag as? CaptureMode ?: return
                selectMode(mode)
            }
            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })

        // Scan result actions.
        binding.scanCard.scanDismiss.setOnClickListener { dismissScan() }
        binding.scanCard.scanCopy.setOnClickListener { currentScan?.let { ScanActions.copy(this, it.raw) } }
        binding.scanCard.scanShare.setOnClickListener { currentScan?.let { ScanActions.share(this, it.raw) } }
        binding.scanCard.scanPrimary.setOnClickListener { currentScan?.let { ScanActions.performPrimary(this, it) } }

        // Capture-intent review.
        binding.reviewRetake.setOnClickListener {
            reviewFile?.delete()
            reviewFile = null
            binding.reviewPanel.isVisible = false
        }
        binding.reviewUse.setOnClickListener { deliverImageResult() }

        binding.exposureSlider.onValueChanged = { index -> controller.setExposureIndex(index) }
        binding.exposureSlider.onInteraction = { dragging ->
            binding.exposureSlider.removeCallbacks(hideExposureSlider)
            if (!dragging) binding.exposureSlider.postDelayed(hideExposureSlider, EV_HIDE_MS)
        }

        applyLaunchModeUi()
    }

    private val hideExposureSlider = Runnable { binding.exposureSlider.isVisible = false }

    private fun setupGestures() {
        var scaling = false
        val scaleDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                scaling = true
                return true
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val st = controller.state.value
                controller.setZoomRatio(ZoomMath.applyPinch(st.zoomRatio, detector.scaleFactor, st.minZoom, st.maxZoom))
                return true
            }
        })
        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true

            override fun onSingleTapUp(e: MotionEvent): Boolean {
                if (scaling) return false
                onPreviewTap(e.x, e.y, lock = false)
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                if (scaling) return
                onPreviewTap(e.x, e.y, lock = true)
            }

            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (scaling || e1 == null) return false
                val dx = e2.x - e1.x
                val dy = e2.y - e1.y
                val threshold = 80.dp
                return when {
                    abs(dx) > abs(dy) && abs(dx) > threshold -> { stepMode(if (dx < 0) 1 else -1); true }
                    abs(dy) > threshold && abs(velocityY) > 600 -> { flipCamera(); true }
                    else -> false
                }
            }
        })
        binding.previewContainer.setOnTouchListener { view, event ->
            scaleDetector.onTouchEvent(event)
            gestureDetector.onTouchEvent(event)
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                if (event.actionMasked == MotionEvent.ACTION_UP && !scaling) view.performClick()
                scaling = false
            }
            true
        }
    }

    private val orientationListener by lazy {
        object : OrientationEventListener(this) {
            override fun onOrientationChanged(degrees: Int) {
                val snapped = OrientationMath.snap(degrees, deviceOrientation)
                if (snapped == deviceOrientation) return
                deviceOrientation = snapped
                controller.setTargetRotation(OrientationMath.surfaceRotation(snapped))
                binding.levelView.deviceOrientation = snapped
                rotateIcons()
            }
        }
    }

    private fun setupOrientation() {
        if (orientationListener.canDetectOrientation()) orientationListener.enable()
    }

    private fun rotateIcons() {
        val views = listOf<View>(
            binding.settingsButton, binding.flashButton, binding.timerButton, binding.aspectButton, binding.hdrButton,
            binding.lensButton, binding.thumbnail, binding.flipButton, binding.pauseButton, binding.countdownText,
        ) + (0 until binding.zoomBar.childCount).map { binding.zoomBar.getChildAt(it) }
        views.forEach { v ->
            v.animate().rotation(OrientationMath.iconRotation(deviceOrientation, v.rotation)).setDuration(220).start()
        }
    }

    // ---------------------------------------------------------------------
    // Camera start and state rendering
    // ---------------------------------------------------------------------

    private fun hasCameraPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun permanentlyDenied() =
        !hasCameraPermission() && !shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)

    private fun showPermissionPanel() {
        binding.permissionPanel.isVisible = true
        binding.permissionButton.setText(if (permanentlyDenied()) R.string.permission_settings else R.string.permission_grant)
    }

    private fun startCamera() {
        if (cameraStarted) return
        cameraStarted = true
        controller.histogramEnabled = histogramWanted()
        val front = if (intent.hasExtra(EXTRA_FRONT)) intent.getBooleanExtra(EXTRA_FRONT, false) else settings.lastFacingFront
        val hdr = getPreferences(MODE_PRIVATE).getBoolean(STATE_HDR, false)
        lifecycleScope.launch {
            val ok = controller.start(initialMode(), front, settings.lastCameraId, settings.flash, settings.aspect, hdr)
            if (!ok) Toast.makeText(this@CameraActivity, R.string.error_no_camera, Toast.LENGTH_LONG).show()
        }
    }

    private fun collectCamera() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { controller.state.collect { render(it) } }
                launch { controller.events.collect { onCameraEvent(it) } }
            }
        }
    }

    private fun render(state: CameraState) {
        val previous = lastRendered
        lastRendered = state
        renderModes(state)
        renderTopBar(state)
        renderZoom(state)
        renderRecording(state)

        val mode = state.mode
        // INVISIBLE (not GONE) keeps the thumbnail/flip positions anchored to the shutter.
        binding.shutterButton.isInvisible = mode == CaptureMode.SCAN
        binding.shutterButton.style = when {
            state.recording != RecordingStatus.IDLE -> ShutterButton.Style.RECORDING
            mode == CaptureMode.VIDEO -> ShutterButton.Style.VIDEO
            else -> ShutterButton.Style.PHOTO
        }
        binding.proPanel.root.isVisible = mode == CaptureMode.PRO
        if (mode == CaptureMode.PRO) proPanel.render(state)
        binding.barcodeOverlay.isVisible = mode == CaptureMode.SCAN
        if (mode != CaptureMode.SCAN) dismissScan()
        binding.statusText.isVisible = mode == CaptureMode.SCAN && currentScan == null || captureBusy
        if (mode == CaptureMode.SCAN && currentScan == null) binding.statusText.setText(R.string.scan_hint)
        binding.histogram.isVisible = controller.histogramEnabled && mode.isPhotoLike && !mode.usesExtension
        applyViewSettings()

        if (previous == null || previous.mode != mode || previous.aspect != state.aspect) layoutPreview(state)
        if (previous?.mode != mode) {
            settings.lastMode = mode
            binding.exposureSlider.isVisible = false
            binding.focusIndicator.clear()
            updateLiveLabelLoop(mode == CaptureMode.PRO)
        }
        if (previous?.currentCameraId != state.currentCameraId && state.currentCameraId != null) {
            settings.lastFacingFront = state.isFront
            settings.lastCameraId = controller.selectedCameraId
        }
    }

    private fun renderModes(state: CameraState) {
        val captureIntent = launchMode != LaunchMode.NORMAL
        binding.modeTabs.isVisible = !captureIntent && state.recording == RecordingStatus.IDLE
        val modes = when (launchMode) {
            LaunchMode.IMAGE_CAPTURE -> listOf(CaptureMode.PHOTO)
            LaunchMode.VIDEO_CAPTURE -> listOf(CaptureMode.VIDEO)
            LaunchMode.NORMAL -> state.availableModes
        }
        updatingTabs = true
        if (modes != renderedModes) {
            renderedModes = modes
            binding.modeTabs.removeAllTabs()
            modes.forEach { mode ->
                binding.modeTabs.addTab(binding.modeTabs.newTab().setText(modeLabel(mode)).setTag(mode), false)
            }
        }
        val index = modes.indexOf(state.mode)
        if (index >= 0 && binding.modeTabs.selectedTabPosition != index) {
            binding.modeTabs.getTabAt(index)?.select()
            binding.modeTabs.post { binding.modeTabs.setScrollPosition(index, 0f, true) }
        }
        updatingTabs = false
    }

    private fun renderTopBar(state: CameraState) {
        val mode = state.mode
        val recording = state.recording != RecordingStatus.IDLE
        val flashUsable = when {
            mode.usesTorch -> state.hasFlashUnit
            mode.usesExtension -> false
            else -> state.hasFlashUnit || state.isFront
        }
        binding.flashButton.isVisible = flashUsable
        val (icon, description) = when {
            mode.usesTorch && state.flash == FlashOption.TORCH -> R.drawable.ic_torch to R.string.cd_torch_on
            mode.usesTorch -> R.drawable.ic_flash_off to R.string.cd_torch_off
            state.flash == FlashOption.AUTO -> R.drawable.ic_flash_auto to R.string.cd_flash_auto
            state.flash == FlashOption.ON -> R.drawable.ic_flash_on to R.string.cd_flash_on
            else -> R.drawable.ic_flash_off to R.string.cd_flash_off
        }
        binding.flashButton.setIconResource(icon)
        binding.flashButton.contentDescription = getString(description)
        binding.flashButton.iconTint = ContextCompat.getColorStateList(
            this, if (state.flash != FlashOption.OFF && flashUsable) R.color.accent else R.color.white,
        )

        val timer = settings.timer
        binding.timerButton.isVisible = mode != CaptureMode.SCAN && !recording
        binding.timerButton.text = if (timer == TimerOption.OFF) "" else getString(R.string.timer_seconds, timer.seconds)
        binding.timerButton.contentDescription = getString(
            R.string.cd_timer, if (timer == TimerOption.OFF) getString(R.string.timer_off) else getString(R.string.timer_seconds, timer.seconds),
        )
        binding.timerButton.iconTint = ContextCompat.getColorStateList(this, if (timer == TimerOption.OFF) R.color.white else R.color.accent)

        binding.aspectButton.isVisible = mode != CaptureMode.SCAN && !recording
        binding.aspectButton.text = if (mode == CaptureMode.VIDEO) settings.videoQuality.label else state.aspect.label
        binding.aspectButton.contentDescription = getString(R.string.cd_aspect, binding.aspectButton.text)

        binding.hdrButton.isVisible = state.hdrAvailable && mode == CaptureMode.PHOTO
        binding.hdrButton.setTextColor(ContextCompat.getColor(this, if (state.hdrEnabled) R.color.accent else R.color.white))
        binding.hdrButton.alpha = if (state.hdrEnabled) 1f else 0.7f

        val extraLenses = state.lenses.size > 2
        binding.lensButton.isVisible = extraLenses && !recording
        binding.settingsButton.isVisible = !recording && !secureLaunch
        binding.flipButton.isVisible = state.lenses.any { it.isFront } && state.lenses.any { !it.isFront }
    }

    private fun renderZoom(state: CameraState) {
        val showBar = state.mode != CaptureMode.PRO && state.mode != CaptureMode.SCAN && state.maxZoom > state.minZoom
        binding.zoomBar.isVisible = showBar
        if (!showBar) return
        val presets = ZoomMath.presets(state.minZoom, state.maxZoom)
        if (presets != renderedPresets) {
            renderedPresets = presets
            binding.zoomBar.removeAllViews()
            presets.forEach { ratio ->
                val chip = TextView(this).apply {
                    layoutParams = android.widget.LinearLayout.LayoutParams(resources.getDimensionPixelSize(R.dimen.zoom_chip), resources.getDimensionPixelSize(R.dimen.zoom_chip)).apply {
                        marginStart = 2.dp
                        marginEnd = 2.dp
                    }
                    gravity = android.view.Gravity.CENTER
                    setBackgroundResource(R.drawable.bg_round_scrim)
                    setTextColor(ContextCompat.getColorStateList(this@CameraActivity, R.color.zoom_chip_text))
                    textSize = 12f
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    rotation = OrientationMath.iconRotation(deviceOrientation, 0f)
                    setOnClickListener { controller.setZoomRatio(ratio); feedback.haptic(this, settings.haptics) }
                }
                binding.zoomBar.addView(chip)
            }
        }
        val active = ZoomMath.activePresetIndex(presets, state.zoomRatio)
        presets.forEachIndexed { i, ratio ->
            val chip = binding.zoomBar.getChildAt(i) as TextView
            val selected = i == active
            chip.isSelected = selected
            val label = if (selected) ZoomMath.format(state.zoomRatio) else ZoomMath.format(ratio)
            chip.text = label
            chip.contentDescription = getString(R.string.cd_zoom, ZoomMath.format(ratio))
        }
    }

    private fun renderRecording(state: CameraState) {
        val recording = state.recording != RecordingStatus.IDLE
        binding.recordingChip.isVisible = recording
        binding.recordingTime.text = if (state.recording == RecordingStatus.PAUSED) {
            "${TimeFormat.recording(state.recordingNanos)} · ${getString(R.string.recording_paused)}"
        } else {
            TimeFormat.recording(state.recordingNanos)
        }
        binding.recordingDot.alpha = if (state.recording == RecordingStatus.PAUSED) 0.4f else 1f
        binding.pauseButton.isVisible = recording && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
        binding.thumbnail.isVisible = !recording && launchMode == LaunchMode.NORMAL && !secureLaunch
        binding.pauseButton.setIconResource(if (state.recording == RecordingStatus.PAUSED) R.drawable.ic_videocam else R.drawable.ic_pause)
        binding.pauseButton.contentDescription = getString(
            if (state.recording == RecordingStatus.PAUSED) R.string.cd_resume_recording else R.string.cd_pause_recording,
        )
    }

    /** Sizes the preview to the capture aspect ratio and places it under the top bar. */
    private fun layoutPreview(state: CameraState) {
        val w = binding.root.width
        val h = binding.root.height
        if (w == 0 || h == 0) return
        val ratio = if (state.mode == CaptureMode.VIDEO) 16f / 9f else state.aspect.heightOverWidth
        val previewHeight = min((w * ratio).toInt(), h)
        val topBarBottom = binding.topBar.bottom
        val top = if (topBarBottom + previewHeight <= h) topBarBottom else max(0, (h - previewHeight) / 2)
        val lp = binding.previewContainer.layoutParams as FrameLayout.LayoutParams
        if (lp.height != previewHeight || lp.topMargin != top) {
            lp.height = previewHeight
            lp.topMargin = top
            binding.previewContainer.layoutParams = lp
        }
    }

    private fun applyViewSettings() {
        val mode = controller.state.value.mode
        binding.gridOverlay.option = if (mode == CaptureMode.SCAN) com.customcamera.app.core.GridOption.OFF else settings.grid
        binding.levelView.active = settings.showLevel && mode != CaptureMode.SCAN
    }

    private fun histogramWanted() = settings.showHistogram

    private fun modeLabel(mode: CaptureMode) = getString(
        when (mode) {
            CaptureMode.NIGHT -> R.string.mode_night
            CaptureMode.PORTRAIT -> R.string.mode_portrait
            CaptureMode.PHOTO -> R.string.mode_photo
            CaptureMode.VIDEO -> R.string.mode_video
            CaptureMode.PRO -> R.string.mode_pro
            CaptureMode.SCAN -> R.string.mode_scan
        },
    )

    /** Polls live ISO/shutter readings for the Pro chips (CaptureResults arrive at frame rate). */
    private fun updateLiveLabelLoop(enabled: Boolean) {
        liveLabelJob?.cancel()
        if (!enabled) return
        liveLabelJob = lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (isActive) {
                    proPanel.refreshLabels()
                    delay(LIVE_LABEL_INTERVAL_MS)
                }
            }
        }
    }

    // ---------------------------------------------------------------------
    // Events
    // ---------------------------------------------------------------------

    private fun onCameraEvent(event: CameraEvent) {
        when (event) {
            is CameraEvent.CaptureStarted -> Unit
            is CameraEvent.PhotoSaved -> {
                setCaptureBusy(false)
                if (launchMode == LaunchMode.IMAGE_CAPTURE) {
                    showReview()
                } else {
                    event.uri?.let { onMediaSaved(it, isVideo = false) }
                }
            }
            is CameraEvent.VideoSaved -> {
                feedback.recordingStopped(settings.shutterSound)
                if (launchMode == LaunchMode.VIDEO_CAPTURE) deliverVideoResult(event.uri) else onMediaSaved(event.uri, isVideo = true)
            }
            is CameraEvent.Error -> {
                setCaptureBusy(false)
                Toast.makeText(this, event.message, Toast.LENGTH_LONG).show()
            }
            is CameraEvent.FocusResult -> binding.focusIndicator.onFocusResult(event.success)
            is CameraEvent.BarcodesDetected -> onBarcodes(event)
            is CameraEvent.HistogramUpdated -> if (binding.histogram.isVisible) binding.histogram.update(event.histogram)
            is CameraEvent.CameraFallback -> Toast.makeText(
                this, getString(R.string.camera_fallback, event.failedCameraId ?: "default", event.boundCameraId), Toast.LENGTH_LONG,
            ).show()
        }
    }

    private fun onBarcodes(event: CameraEvent.BarcodesDetected) {
        if (controller.state.value.mode != CaptureMode.SCAN) return
        binding.barcodeOverlay.show(event.results.mapNotNull { it.box })
        val first = event.results.firstOrNull()?.content ?: return
        if (first.raw == currentScan?.raw) return
        currentScan = first
        feedback.haptic(binding.root, settings.haptics, strong = true)
        val p = ScanActions.present(this, first)
        with(binding.scanCard) {
            scanType.setText(p.typeLabel)
            scanIcon.setImageResource(p.icon)
            scanText.text = p.displayText
            scanPrimary.isVisible = p.primaryAction != null
            p.primaryAction?.let { scanPrimary.setText(it) }
            root.isVisible = true
        }
        binding.statusText.isVisible = false
    }

    private fun dismissScan() {
        currentScan = null
        binding.scanCard.root.isVisible = false
    }

    // ---------------------------------------------------------------------
    // Capture
    // ---------------------------------------------------------------------

    private fun onShutter() {
        val st = controller.state.value
        if (!st.ready || binding.reviewPanel.isVisible) return
        when {
            st.mode == CaptureMode.SCAN -> Unit
            st.recording != RecordingStatus.IDLE -> controller.stopRecording()
            countdownJob?.isActive == true -> cancelCountdown()
            captureBusy -> Unit
            settings.timer != TimerOption.OFF -> startCountdown(settings.timer.seconds) { trigger(st.mode) }
            else -> trigger(st.mode)
        }
    }

    private fun trigger(mode: CaptureMode) {
        if (mode == CaptureMode.VIDEO) startRecordingFlow() else capturePhoto()
    }

    private fun capturePhoto() {
        val st = controller.state.value
        feedback.shutter(settings.shutterSound)
        feedback.haptic(binding.shutterButton, settings.haptics)
        val screenFlash = st.isFront && !st.hasFlashUnit && st.flash == FlashOption.ON
        if (!screenFlash) blinkShutter()
        if (st.mode.usesExtension) setCaptureBusy(true)
        if (launchMode == LaunchMode.IMAGE_CAPTURE) {
            val file = File(cacheDir, "capture_review.jpg").also { reviewFile = it }
            controller.takePhotoToFile(file)
        } else {
            controller.takePhoto(if (settings.saveLocation) location.currentOrNull() else null)
        }
    }

    private fun setCaptureBusy(busy: Boolean) {
        captureBusy = busy
        binding.shutterButton.busy = busy
        if (busy) {
            binding.statusText.setText(R.string.hold_still)
            binding.statusText.isVisible = true
        } else if (controller.state.value.mode != CaptureMode.SCAN) {
            binding.statusText.isVisible = false
        }
    }

    private fun blinkShutter() {
        binding.shutterBlink.animate().cancel()
        binding.shutterBlink.alpha = 0.85f
        binding.shutterBlink.animate().alpha(0f).setDuration(180).start()
    }

    private fun startRecordingFlow() {
        val audioGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (settings.videoAudio && !audioGranted) {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            beginRecording()
        }
    }

    private fun beginRecording() {
        feedback.recordingStarted(settings.shutterSound)
        feedback.haptic(binding.shutterButton, settings.haptics)
        controller.startRecording(settings.videoAudio, if (settings.saveLocation) location.currentOrNull() else null)
    }

    private fun startCountdown(seconds: Int, action: () -> Unit) {
        cancelCountdown()
        countdownJob = lifecycleScope.launch {
            binding.countdownText.isVisible = true
            for (remaining in seconds downTo 1) {
                binding.countdownText.text = remaining.toString()
                binding.countdownText.scaleX = 1.4f
                binding.countdownText.scaleY = 1.4f
                binding.countdownText.animate().scaleX(1f).scaleY(1f).setDuration(300).start()
                feedback.haptic(binding.countdownText, settings.haptics)
                delay(1_000)
            }
            binding.countdownText.isVisible = false
            action()
        }
    }

    private fun cancelCountdown() {
        countdownJob?.cancel()
        countdownJob = null
        binding.countdownText.isVisible = false
    }

    private fun onMediaSaved(uri: Uri, isVideo: Boolean) {
        lastCaptureUri = uri
        lifecycleScope.launch {
            repository.thumbnail(uri, isVideo, THUMB_PX)?.let { setThumbnail(it) }
        }
    }

    private fun refreshThumbnail() {
        lifecycleScope.launch {
            val latest = repository.latest()
            lastCaptureUri = latest?.uri
            val bitmap = latest?.let { repository.thumbnail(it, THUMB_PX) }
            if (bitmap != null) setThumbnail(bitmap) else binding.thumbnail.setImageResource(R.drawable.ic_gallery)
        }
    }

    private fun setThumbnail(bitmap: Bitmap) {
        binding.thumbnail.setImageBitmap(bitmap)
        binding.thumbnail.scaleX = 0.6f
        binding.thumbnail.scaleY = 0.6f
        binding.thumbnail.animate().scaleX(1f).scaleY(1f).setDuration(220).start()
    }

    private fun openLastCapture() {
        val uri = lastCaptureUri
        val intent = Intent(this, MediaViewerActivity::class.java)
        if (uri != null) intent.data = uri
        startActivity(intent)
    }

    // ---------------------------------------------------------------------
    // Camera selection / modes
    // ---------------------------------------------------------------------

    private fun selectMode(mode: CaptureMode) {
        cancelCountdown()
        controller.setMode(mode)
        feedback.haptic(binding.modeTabs, settings.haptics)
    }

    private fun stepMode(direction: Int) {
        if (launchMode != LaunchMode.NORMAL || controller.isRecording) return
        val modes = renderedModes
        val i = modes.indexOf(controller.state.value.mode)
        val next = modes.getOrNull(i + direction) ?: return
        selectMode(next)
    }

    private fun flipCamera() {
        val st = controller.state.value
        if (!binding.flipButton.isVisible) return
        binding.flipButton.animate().rotationBy(180f).setDuration(300).start()
        binding.focusIndicator.clear()
        binding.exposureSlider.isVisible = false
        controller.switchFacing()
        settings.lastFacingFront = !st.isFront
        feedback.haptic(binding.flipButton, settings.haptics)
    }

    private fun showLensPicker() {
        val st = controller.state.value
        if (st.lenses.isEmpty() || st.recording != RecordingStatus.IDLE) return
        val entries = mutableListOf<Pair<String?, String>>()
        entries += null to getString(R.string.lens_default_back)
        entries += FRONT_DEFAULT to getString(R.string.lens_default_front)
        st.lenses.forEach { lens ->
            val facing = getString(if (lens.isFront) R.string.lens_front else R.string.lens_back)
            val category = getString(
                when (lens.category) {
                    com.customcamera.app.core.LensMath.LensCategory.ULTRA_WIDE -> R.string.lens_ultra_wide
                    com.customcamera.app.core.LensMath.LensCategory.WIDE -> R.string.lens_wide
                    com.customcamera.app.core.LensMath.LensCategory.STANDARD -> R.string.lens_standard
                    com.customcamera.app.core.LensMath.LensCategory.TELEPHOTO -> R.string.lens_tele
                },
            )
            var label = lens.equivalentFocalMm?.let { getString(R.string.lens_item_focal, facing, category, it, lens.cameraId) }
                ?: getString(R.string.lens_item, facing, category, lens.cameraId)
            if (lens.isLogicalMultiCamera) label += " (${getString(R.string.lens_multi)})"
            entries += lens.cameraId to label
        }
        val selectedId = controller.selectedCameraId
        val checked = entries.indexOfFirst { (id, _) ->
            when {
                selectedId != null -> id == selectedId
                st.isFront -> id == FRONT_DEFAULT
                else -> id == null
            }
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.lens_picker_title)
            .setSingleChoiceItems(entries.map { it.second }.toTypedArray(), checked) { dialog, which ->
                when (val id = entries[which].first) {
                    null -> controller.selectCameraDefault(front = false)
                    FRONT_DEFAULT -> controller.selectCameraDefault(front = true)
                    else -> controller.selectCamera(id)
                }
                settings.lastCameraId = controller.selectedCameraId
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun cycleVideoQuality() {
        val next = VideoQualityOption.entries[(settings.videoQuality.ordinal + 1) % VideoQualityOption.entries.size]
        androidx.preference.PreferenceManager.getDefaultSharedPreferences(this).edit()
            .putString(AppSettings.Keys.VIDEO_QUALITY, next.key).apply()
        settingsDirty = false
        controller.refreshFromSettings()
        renderTopBar(controller.state.value)
    }

    private fun onPreviewTap(x: Float, y: Float, lock: Boolean) {
        val st = controller.state.value
        if (!st.ready || st.mode == CaptureMode.SCAN) return
        if (!lock && st.focusLocked) {
            controller.unlockFocus()
            binding.focusIndicator.clear()
            return
        }
        controller.focusAt(x, y, lock)
        binding.focusIndicator.show(x, y, lock)
        if (lock) feedback.haptic(binding.previewContainer, settings.haptics, strong = true)
        showExposureSlider(x, y, st)
    }

    private fun showExposureSlider(x: Float, y: Float, st: CameraState) {
        val manualExposure = st.mode == CaptureMode.PRO && (st.manual.iso != null || st.manual.exposureNs != null)
        if (!st.evSupported || manualExposure) return
        val slider = binding.exposureSlider
        slider.setRange(st.evMin, st.evMax, st.evIndex)
        val container = binding.previewContainer
        val sliderW = slider.layoutParams.width
        val sliderH = slider.layoutParams.height
        // Place to the right of the focus ring, or to the left near the edge.
        val offset = 56.dp
        val left = if (x + offset + sliderW < container.width) x + offset else x - offset - sliderW
        slider.x = left.coerceIn(0f, (container.width - sliderW).toFloat())
        slider.y = (y - sliderH / 2f).coerceIn(0f, (container.height - sliderH).toFloat())
        slider.isVisible = true
        slider.removeCallbacks(hideExposureSlider)
        slider.postDelayed(hideExposureSlider, EV_HIDE_MS)
    }

    // ---------------------------------------------------------------------
    // Keys
    // ---------------------------------------------------------------------

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_CAMERA, KeyEvent.KEYCODE_HEADSETHOOK -> {
                if (event.repeatCount == 0) onShutter()
                return true
            }
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN -> when (settings.volumeKeyAction) {
                VolumeKeyAction.SHUTTER -> {
                    if (event.repeatCount == 0) onShutter()
                    return true
                }
                VolumeKeyAction.ZOOM -> {
                    val st = controller.state.value
                    controller.setZoomRatio(ZoomMath.step(st.zoomRatio, keyCode == KeyEvent.KEYCODE_VOLUME_UP, st.minZoom, st.maxZoom))
                    return true
                }
                VolumeKeyAction.VOLUME -> Unit
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        // Swallow the matching key-up so the system volume panel does not appear.
        val volume = keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
        if (volume && settings.volumeKeyAction != VolumeKeyAction.VOLUME) return true
        if (keyCode == KeyEvent.KEYCODE_CAMERA || keyCode == KeyEvent.KEYCODE_HEADSETHOOK) return true
        return super.onKeyUp(keyCode, event)
    }

    // ---------------------------------------------------------------------
    // Capture-intent results
    // ---------------------------------------------------------------------

    private fun showReview() {
        val file = reviewFile ?: return
        lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.IO) { BitmapDecoding.decode(file, REVIEW_MAX_PX) }
            binding.reviewImage.setImageBitmap(bitmap)
            binding.reviewPanel.isVisible = true
        }
    }

    private fun outputUri(): Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        intent.getParcelableExtra(MediaStore.EXTRA_OUTPUT, Uri::class.java)
    } else {
        @Suppress("DEPRECATION")
        intent.getParcelableExtra(MediaStore.EXTRA_OUTPUT)
    }

    private fun deliverImageResult() {
        val file = reviewFile ?: return
        lifecycleScope.launch {
            val output = outputUri()
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    if (output != null) {
                        contentResolver.openOutputStream(output, "w")?.use { out -> file.inputStream().use { it.copyTo(out) } }
                            ?: error("Cannot open $output")
                        Intent().setData(output)
                    } else {
                        // No EXTRA_OUTPUT: the contract is a small thumbnail in "data".
                        val thumb = BitmapDecoding.decode(file, RESULT_THUMB_PX)
                        Intent().putExtra("data", thumb)
                    }
                }
            }
            file.delete()
            result.onSuccess { setResult(RESULT_OK, it) }.onFailure {
                Toast.makeText(this@CameraActivity, getString(R.string.error_camera_generic, it.message), Toast.LENGTH_LONG).show()
                setResult(RESULT_CANCELED)
            }
            finish()
        }
    }

    private fun deliverVideoResult(saved: Uri) {
        lifecycleScope.launch {
            val output = outputUri()
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    if (output != null) {
                        contentResolver.openOutputStream(output, "w")?.use { out ->
                            contentResolver.openInputStream(saved)?.use { it.copyTo(out) }
                        } ?: error("Cannot open $output")
                        Intent().setData(output)
                    } else {
                        Intent().setData(saved).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                }
            }
            result.onSuccess { setResult(RESULT_OK, it) }.onFailure { setResult(RESULT_CANCELED) }
            finish()
        }
    }

    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()

    /** Current controller state, for instrumented tests. */
    @VisibleForTesting
    internal fun cameraStateForTest(): CameraState = controller.state.value

    /** Binds a specific Camera2 id as the lens picker would, for instrumented tests. */
    @VisibleForTesting
    internal fun selectCameraForTest(cameraId: String) = controller.selectCamera(cameraId)

    companion object {
        /** Shortcut / deep-link extra: initial [CaptureMode] key. */
        const val EXTRA_MODE = "com.customcamera.app.extra.MODE"

        /** Shortcut extra: start on the front camera. */
        const val EXTRA_FRONT = "com.customcamera.app.extra.FRONT"

        private const val STATE_HDR = "hdr"
        private const val FRONT_DEFAULT = "__front_default__"
        private const val EV_HIDE_MS = 3_000L
        private const val LIVE_LABEL_INTERVAL_MS = 300L
        private const val THUMB_PX = 192
        private const val REVIEW_MAX_PX = 2048
        private const val RESULT_THUMB_PX = 320
    }
}
