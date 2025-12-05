package com.customcamera.app

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.customcamera.app.databinding.ActivityCameraBinding
import com.customcamera.app.engine.CameraConfig
import com.customcamera.app.engine.CameraEngine
import com.customcamera.app.engine.CameraContext
import com.customcamera.app.engine.CameraMode
import com.customcamera.app.engine.DebugLogger
import com.customcamera.app.engine.SettingsManager
import com.customcamera.app.plugins.*
import com.customcamera.app.exceptions.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.pow
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.animation.AnimatorInflater
import android.view.animation.AnimationUtils
import android.animation.ObjectAnimator
import android.animation.AnimatorSet
import android.view.HapticFeedbackConstants
import android.view.ViewGroup
import com.customcamera.app.ui.LoadingIndicatorManager

/**
 * Enhanced CameraActivity that uses the CameraEngine and plugin system.
 * This demonstrates how to integrate the plugin architecture with existing camera functionality.
 */
class CameraActivityEngine : AppCompatActivity() {

    private lateinit var binding: ActivityCameraBinding
    private lateinit var cameraEngine: CameraEngine
    private lateinit var pluginRegistry: com.customcamera.app.engine.plugins.PluginRegistry
    private lateinit var settingsManager: SettingsManager

    private var cameraIndex: Int = 0
    @Volatile private var isFlashOn: Boolean = false
    @Volatile private var isRecording: Boolean = false
    private var activeRecording: Recording? = null
    @Volatile private var isNightModeEnabled: Boolean = false
    @Volatile private var isHistogramVisible: Boolean = false
    private var histogramView: com.customcamera.app.analysis.HistogramView? = null
    @Volatile private var isBarcodeScanningEnabled: Boolean = false
    @Volatile private var isPiPEnabled: Boolean = false

    // Camera mode selector
    private enum class CaptureMode {
        PHOTO, VIDEO, NIGHT
    }
    @Volatile private var currentMode: CaptureMode = CaptureMode.PHOTO
    private var loadingIndicator: android.widget.TextView? = null
    private var pipOverlayView: com.customcamera.app.pip.PiPOverlayView? = null
    private var camera2ISOController: com.customcamera.app.camera2.Camera2ISOController? = null
    private var zoomController: com.customcamera.app.camera2.ZoomController? = null
    private var scaleGestureDetector: ScaleGestureDetector? = null
    private var zoomIndicator: android.widget.TextView? = null
    private var shutterSpeedController: com.customcamera.app.camera2.ShutterSpeedController? = null
    private var focusDistanceController: com.customcamera.app.camera2.FocusDistanceController? = null

    // Conference presentation enhancements
    private lateinit var hapticManager: com.customcamera.app.presentation.EnhancedHapticManager
    private lateinit var demoShowcaseManager: com.customcamera.app.presentation.DemoShowcaseManager
    private var gestureHintsOverlay: com.customcamera.app.presentation.GestureHintsOverlay? = null
    private var presentationPerformanceMonitor: com.customcamera.app.presentation.PerformanceMonitor? = null
    private var focusPeakingOverlay: com.customcamera.app.focus.FocusPeakingOverlay? = null
    @Volatile private var isFocusPeakingEnabled: Boolean = false
    private var lastTapTime = 0L
    private var tapCount = 0
    private var barcodeOverlayView: com.customcamera.app.barcode.BarcodeOverlayView? = null
    private var camera2Controller: com.customcamera.app.camera2.Camera2Controller? = null
    private var performanceMonitor: com.customcamera.app.monitoring.PerformanceMonitor? = null

    // Plugins (retrieved from CameraEngine via Provider Pattern)
    private var autoFocusPlugin: AutoFocusPlugin? = null
    private var gridOverlayPlugin: GridOverlayPlugin? = null
    private var cameraInfoPlugin: CameraInfoPlugin? = null
    private var proControlsPlugin: ProControlsPlugin? = null
    private var exposureControlPlugin: ExposureControlPlugin? = null
    private var cropPlugin: CropPlugin? = null
    private var dualCameraPiPPlugin: DualCameraPiPPlugin? = null
    private var advancedVideoRecordingPlugin: AdvancedVideoRecordingPlugin? = null

    // Professional control plugins (temporarily disabled - API migration needed)
    // private lateinit var isoPlugin: AdvancedISOControlPlugin
    // private lateinit var shutterPlugin: ProfessionalShutterControlPlugin
    // private lateinit var aperturePlugin: ManualApertureControlPlugin
    // private lateinit var whiteBalancePlugin: AdvancedWhiteBalancePlugin
    // private lateinit var focusPlugin: ManualFocusControlPlugin
    // private lateinit var bracketingPlugin: ExposureBracketingPlugin

    // UI Enhancement Components
    private lateinit var loadingIndicatorManager: LoadingIndicatorManager

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startCameraWithEngine()
        } else {
            com.customcamera.app.presentation.EnhancedToast.error(this, getString(R.string.camera_permission_required), Toast.LENGTH_LONG)
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "=== CameraActivityEngine onCreate START ===")

        try {
            binding = ActivityCameraBinding.inflate(layoutInflater)
            setContentView(binding.root)
            Log.i(TAG, "✅ Layout inflated successfully")

            // Configure PreviewView to use PERFORMANCE mode for better dual camera compatibility
            // PERFORMANCE mode uses SurfaceView which matches PiP preview mode
            binding.previewView.implementationMode = androidx.camera.view.PreviewView.ImplementationMode.PERFORMANCE
            Log.i(TAG, "✅ PreviewView configured with PERFORMANCE mode (SurfaceView)")
        } catch (e: Exception) {
            Log.e(TAG, "💥 Layout inflation failed", e)
            com.customcamera.app.presentation.EnhancedToast.error(this, "Camera interface failed to load", Toast.LENGTH_LONG)
            finish()
            return
        }

        // Set fullscreen flags
        setupFullscreen()

        // Get camera index from intent, fallback to saved settings
        val intentCameraIndex = intent.getIntExtra(CameraSelectionActivity.EXTRA_CAMERA_INDEX, -1)
        if (intentCameraIndex != -1) {
            // Use camera from intent (CameraSelectionActivity)
            cameraIndex = intentCameraIndex
            Log.i(TAG, "Using camera index from intent: $cameraIndex")
        } else {
            // Fallback to saved default camera from settings (use singleton)
            val tempSettings = SettingsManager.getInstance(this)
            cameraIndex = tempSettings.defaultCameraIndex.value
            Log.i(TAG, "Using camera index from settings: $cameraIndex")
        }

        // Initialize UI enhancement components
        loadingIndicatorManager = LoadingIndicatorManager(this)

        // Initialize presentation enhancements
        hapticManager = com.customcamera.app.presentation.EnhancedHapticManager(this)
        demoShowcaseManager = com.customcamera.app.presentation.DemoShowcaseManager(this)
        gestureHintsOverlay = binding.gestureHintsOverlay
        presentationPerformanceMonitor = binding.performanceMonitor

        Log.i(TAG, "✅ Presentation enhancements initialized")

        // Initialize camera engine and plugins
        initializeCameraEngine()

        setupUI()

        if (hasCameraPermission()) {
            startCameraWithEngine()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        // Handle ADB testing intents
        handleTestIntent()
    }

    /**
     * Handle ADB testing intents for automated testing
     */
    private fun handleTestIntent() {
        when (intent?.action) {
            "com.customcamera.app.TEST_CAMERA" -> {
                Log.i(TAG, "🧪 TEST_CAMERA intent received - camera will launch normally")
            }
            "com.customcamera.app.TEST_PIP" -> {
                Log.i(TAG, "🧪 TEST_PIP intent received - will enable PiP mode after camera starts")
                lifecycleScope.launch {
                    kotlinx.coroutines.delay(2000) // Wait for camera to initialize
                    // Only toggle if PiP is NOT already enabled
                    val isEnabled = dualCameraPiPPlugin?.isPiPEnabled?.value ?: false
                    if (!isEnabled) {
                        togglePiP()
                        Log.i(TAG, "🧪 PiP mode enabled via test intent")
                    } else {
                        Log.i(TAG, "🧪 PiP mode already enabled (current state: $isEnabled), keeping it enabled")
                    }
                }
            }
            "com.customcamera.app.TEST_CAPTURE" -> {
                Log.i(TAG, "🧪 TEST_CAPTURE intent received - will capture photo after camera starts")
                lifecycleScope.launch {
                    // Disable PiP mode for simpler test scenario
                    val isPiPCurrentlyEnabled = dualCameraPiPPlugin?.isPiPEnabled?.value ?: false
                    if (isPiPCurrentlyEnabled) {
                        Log.i(TAG, "🧪 Disabling PiP mode for TEST_CAPTURE")
                        togglePiP() // Turn off PiP for cleaner test
                        kotlinx.coroutines.delay(2000) // Wait for mode switch
                    }

                    // Wait longer for camera to fully bind (especially after mode changes)
                    kotlinx.coroutines.delay(3000) // 3 seconds for camera binding

                    // Verify camera is bound before capturing
                    val imageCapture = cameraEngine.getImageCapture()
                    if (imageCapture == null) {
                        Log.e(TAG, "🧪 TEST_CAPTURE failed: ImageCapture not available")
                        com.customcamera.app.presentation.EnhancedToast.error(this@CameraActivityEngine, "Camera not ready for capture", android.widget.Toast.LENGTH_SHORT)
                        return@launch
                    }

                    Log.i(TAG, "🧪 Camera ready, capturing photo...")
                    capturePhoto()
                    Log.i(TAG, "🧪 Photo captured via test intent")
                }
            }
            "com.customcamera.app.TEST_VIDEO" -> {
                Log.i(TAG, "🧪 TEST_VIDEO intent received - will record video after camera starts")
                lifecycleScope.launch {
                    // Disable PiP mode for simpler test scenario (video doesn't work in PiP)
                    val isPiPCurrentlyEnabled = dualCameraPiPPlugin?.isPiPEnabled?.value ?: false
                    if (isPiPCurrentlyEnabled) {
                        Log.i(TAG, "🧪 Disabling PiP mode for TEST_VIDEO")
                        togglePiP() // Turn off PiP (required for video)
                        kotlinx.coroutines.delay(2000) // Wait for mode switch
                    }

                    // Wait for camera to fully bind
                    kotlinx.coroutines.delay(3000) // 3 seconds for camera binding

                    // Verify video capture is available
                    val videoCapture = cameraEngine.getVideoCapture()
                    if (videoCapture == null) {
                        Log.e(TAG, "🧪 TEST_VIDEO failed: VideoCapture not available")
                        com.customcamera.app.presentation.EnhancedToast.error(this@CameraActivityEngine, "Camera not ready for video", android.widget.Toast.LENGTH_SHORT)
                        return@launch
                    }

                    // Verify plugin is available
                    val plugin = advancedVideoRecordingPlugin
                    if (plugin == null) {
                        Log.e(TAG, "🧪 TEST_VIDEO failed: AdvancedVideoRecordingPlugin not available")
                        com.customcamera.app.presentation.EnhancedToast.error(this@CameraActivityEngine, "Video plugin not available", android.widget.Toast.LENGTH_SHORT)
                        return@launch
                    }

                    // Enable the plugin (plugin defaults to disabled for cleaner photo-first UI)
                    Log.i(TAG, "🧪 Enabling AdvancedVideoRecordingPlugin for test")
                    plugin.enable()

                    // Rebind camera to activate VideoCapture UseCase
                    Log.i(TAG, "🧪 Rebinding camera to activate VideoCapture...")
                    val rebindConfig = com.customcamera.app.engine.CameraConfig(
                        cameraIndex = cameraIndex,
                        enablePreview = true,
                        enableImageCapture = true,
                        enableVideoCapture = true,
                        enableImageAnalysis = false
                    )
                    cameraEngine.bindCamera(rebindConfig)
                    kotlinx.coroutines.delay(2000) // Wait for camera rebind to complete

                    Log.i(TAG, "🧪 Camera ready, starting video recording...")
                    val startResult = plugin.startRecording()
                    if (startResult.isFailure) {
                        Log.e(TAG, "🧪 TEST_VIDEO failed to start recording: ${startResult.exceptionOrNull()?.message}")
                        com.customcamera.app.presentation.EnhancedToast.error(this@CameraActivityEngine, "Failed to start recording", android.widget.Toast.LENGTH_SHORT)
                        return@launch
                    }

                    Log.i(TAG, "🧪 Video recording started, waiting for encoder initialization...")
                    kotlinx.coroutines.delay(2000) // Wait for video encoder to initialize and produce first frames

                    Log.i(TAG, "🧪 Recording now active, will record for 6 more seconds...")
                    kotlinx.coroutines.delay(6000) // Record for 6 seconds of actual video

                    Log.i(TAG, "🧪 Stopping video recording...")
                    plugin.stopRecording()

                    // Wait a moment for finalization
                    kotlinx.coroutines.delay(1000)
                    Log.i(TAG, "🧪 Video recording completed via test intent")
                }
            }
        }
    }

    private fun setupFullscreen() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            // Modern approach for Android 11+ (API 30+)
            // Enable edge-to-edge display
            // Note: setDecorFitsSystemWindows is NOT deprecated - Kotlin compiler false positive
            @Suppress("DEPRECATION")
            window.setDecorFitsSystemWindows(false)

            window.insetsController?.let { controller ->
                // Hide both status bar and navigation bar
                controller.hide(
                    android.view.WindowInsets.Type.statusBars()
                    or android.view.WindowInsets.Type.navigationBars()
                )
                // Make bars reappear temporarily on swipe
                controller.systemBarsBehavior =
                    android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }

            Log.i(TAG, "✅ Fullscreen mode enabled (Android 11+ edge-to-edge)")
        } else {
            // Legacy approach for older Android versions
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )

            Log.i(TAG, "✅ Fullscreen mode enabled (Legacy immersive sticky)")
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            // Re-apply fullscreen when window regains focus
            setupFullscreen()
        }
    }

    private fun setupUI() {
        // Enhanced button setup with animations and feedback
        // Capture button now respects current mode (Photo/Video/Night)
        setupEnhancedButton(binding.captureButton, true) { handleCapture() }
        setupEnhancedButton(binding.videoRecordButton) { toggleVideoRecording() }
        setupEnhancedButton(binding.nightModeButton) { toggleNightMode() }
        setupEnhancedButton(binding.pipButton) { togglePiP() }
        setupEnhancedButton(binding.scanBarcodeButton) { triggerBarcodeScanning() }
        setupEnhancedButton(binding.scanQrButton) { triggerQRScanning() }
        setupEnhancedButton(binding.switchCameraButton) { switchCamera() }
        setupEnhancedButton(binding.flashButton) { toggleFlash() }
        setupEnhancedButton(binding.galleryButton) { openGallery() }
        setupEnhancedButton(binding.settingsButton) { openFullSettings() }

        // Wire up master plugin button with dropdown
        setupEnhancedButton(binding.masterPluginButton) { togglePluginDropdown() }

        // Note: setupPluginDropdown() called AFTER camera engine initialization
        // See line ~290 after CameraEngine construction

        // Setup mode selector strip (Photo/Video/Night)
        setupModeSelector()

        // Add gesture controls for features including AI
        var lastTapTime = 0L
        var tapCount = 0
        binding.previewView.setOnClickListener {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastTapTime < 300) {
                tapCount++
                when (tapCount) {
                    1 -> {
                        // Double tap - toggle grid
                        toggleGrid()
                    }
                    2 -> {
                        // Triple tap - toggle barcode scanning
                        toggleBarcodeScanning()
                    }
                    3 -> {
                        // Quadruple tap - toggle crop mode
                        toggleCrop()
                    }
                    4 -> {
                        // Five-tap - toggle smart scene detection
                        toggleSmartSceneDetection()
                    }
                    5 -> {
                        // Six-tap - toggle gesture hints
                        gestureHintsOverlay?.toggle()
                        hapticManager.mediumTap()
                        com.customcamera.app.presentation.EnhancedToast.info(this@CameraActivityEngine, "Gesture hints toggled")
                    }
                    6 -> {
                        // Seven-tap - toggle demo showcase mode
                        if (demoShowcaseManager.isInShowcaseMode()) {
                            demoShowcaseManager.endShowcase(binding.root as android.view.ViewGroup)
                            hapticManager.success()
                            com.customcamera.app.presentation.EnhancedToast.info(this@CameraActivityEngine, "Demo mode ended")
                        } else {
                            demoShowcaseManager.startDemoShowcase(binding.root as android.view.ViewGroup)
                            hapticManager.success()
                            com.customcamera.app.presentation.EnhancedToast.success(this@CameraActivityEngine, "Demo mode activated!")
                        }
                        tapCount = 0 // Reset after seven-tap
                    }
                    else -> tapCount = 0
                }
            } else {
                tapCount = 0
            }
            lastTapTime = currentTime
        }

        // Long press for AI features status
        binding.previewView.setOnLongClickListener {
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            showAIFeaturesStatus()
            true
        }

        Log.i(TAG, "✅ UI setup complete with advanced controls")
    }

    /**
     * Setup mode selector strip for Photo/Video/Night modes
     * Modern Instagram/Snapchat style horizontal mode selector
     */
    private fun setupModeSelector() {
        // Photo mode button
        binding.photoModeButton.setOnClickListener {
            switchToMode(CaptureMode.PHOTO)
            hapticManager.mediumTap()
        }

        // Video mode button
        binding.videoModeButton.setOnClickListener {
            switchToMode(CaptureMode.VIDEO)
            hapticManager.mediumTap()
        }

        // Night mode button
        binding.nightModeSelector.setOnClickListener {
            switchToMode(CaptureMode.NIGHT)
            hapticManager.mediumTap()
        }

        // Initialize with photo mode active
        updateModeUI(CaptureMode.PHOTO)

        Log.i(TAG, "✅ Mode selector strip initialized")
    }

    /**
     * Switch to a different capture mode
     */
    private fun switchToMode(mode: CaptureMode) {
        if (currentMode == mode) {
            // Already in this mode, no action needed
            return
        }

        val previousMode = currentMode
        currentMode = mode

        Log.i(TAG, "Switching mode: $previousMode → $mode")

        when (mode) {
            CaptureMode.PHOTO -> {
                // Disable video recording if active
                if (isRecording) {
                    toggleVideoRecording()
                }
                // Disable night mode if active
                if (isNightModeEnabled) {
                    toggleNightMode()
                }
                com.customcamera.app.presentation.EnhancedToast.info(this, "Photo mode")
            }
            CaptureMode.VIDEO -> {
                // Disable night mode if active (video doesn't support night mode)
                if (isNightModeEnabled) {
                    toggleNightMode()
                }
                // Note: Video recording will be started on capture button press
                com.customcamera.app.presentation.EnhancedToast.info(this, "Video mode - Tap capture to start recording")
            }
            CaptureMode.NIGHT -> {
                // Disable video recording if active (night mode is photo only)
                if (isRecording) {
                    toggleVideoRecording()
                }
                // Enable night mode if not already enabled
                if (!isNightModeEnabled) {
                    toggleNightMode()
                }
                com.customcamera.app.presentation.EnhancedToast.info(this, "Night mode")
            }
        }

        updateModeUI(mode)
    }

    /**
     * Update mode selector UI to reflect active mode
     */
    private fun updateModeUI(mode: CaptureMode) {
        // Reset all modes to inactive state (dimmed)
        binding.photoModeButton.alpha = 0.5f
        binding.photoModeButton.textSize = 14f
        binding.photoModeButton.setBackgroundResource(0) // Remove background

        binding.videoModeButton.alpha = 0.5f
        binding.videoModeButton.textSize = 14f

        binding.nightModeSelector.alpha = 0.5f
        binding.nightModeSelector.textSize = 14f

        // Highlight active mode
        when (mode) {
            CaptureMode.PHOTO -> {
                binding.photoModeButton.alpha = 1.0f
                binding.photoModeButton.textSize = 15f
                binding.photoModeButton.setBackgroundResource(R.drawable.camera_control_background)
            }
            CaptureMode.VIDEO -> {
                binding.videoModeButton.alpha = 1.0f
                binding.videoModeButton.textSize = 15f
                binding.videoModeButton.setBackgroundResource(R.drawable.camera_control_background)
            }
            CaptureMode.NIGHT -> {
                binding.nightModeSelector.alpha = 1.0f
                binding.nightModeSelector.textSize = 15f
                binding.nightModeSelector.setBackgroundResource(R.drawable.camera_control_background)
            }
        }
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun initializeCameraEngine() {
        Log.i(TAG, "🔌 Initializing camera engine with Provider Pattern...")

        // Get singleton settings manager instance
        settingsManager = SettingsManager.getInstance(this)

        // Create plugin registry (single source of truth for all plugins)
        pluginRegistry = com.customcamera.app.engine.plugins.PluginRegistry(this)

        // Create camera engine with registry and settings - plugins auto-register via Provider Pattern
        cameraEngine = CameraEngine(this, this, pluginRegistry, settingsManager)

        // ✅ Provider Pattern Refactoring Complete!
        // All plugins now auto-register from PluginRegistry using companion object providers.
        // No more dual registration - just add to PluginRegistry.allProviders list!
        //
        // Active plugins (22 total):
        // - OVERLAYS (1): GridOverlay
        // - ANALYSIS (7): Barcode, Histogram, CameraInfo, ExposureAnalysis, MotionDetection, QRScanner, SharpnessAnalysis
        // - CONTROLS (4): AutoFocus, ExposureControl, ManualFocus, ProControls
        // - AI (3): SmartScene, SmartAdjustments, ObjectDetection
        // - CAPTURE (7): Crop, DualCameraPiP, RAWCapture, AdvancedVideoRecording, NightMode, HDR

        // NOTE: Plugin references retrieved after async initialize() completes in startCameraWithEngine()
        // Plugins are registered during cameraEngine.initialize(), so we can't get them here yet.
        // See line ~330 in startCameraWithEngine() for actual plugin reference assignment.

        Log.i(TAG, "🔌 Camera engine created, plugins will be registered during async initialize()")

        // Note: setupPluginDropdown() and plugin reference assignment happen in startCameraWithEngine() after async initialize()
    }

    private fun startCameraWithEngine() {
        Log.i(TAG, "Starting camera with engine...")

        // Show enhanced loading indicator
        loadingIndicatorManager.showLoading(
            binding.root as ViewGroup,
            LoadingIndicatorManager.LoadingType.CAMERA_INIT,
            autoDismiss = 3000L
        )

        lifecycleScope.launch {
            try {
                // Initialize the engine
                val initResult = cameraEngine.initialize()
                if (initResult.isFailure) {
                    handleCameraError("Camera engine initialization failed: ${initResult.exceptionOrNull()?.message}")
                    return@launch
                }

                // Get plugin references AFTER initialize() completes (plugins are now registered)
                autoFocusPlugin = cameraEngine.getPlugin("AutoFocus") as? AutoFocusPlugin
                gridOverlayPlugin = cameraEngine.getPlugin("GridOverlay") as? GridOverlayPlugin
                cameraInfoPlugin = cameraEngine.getPlugin("CameraInfo") as? CameraInfoPlugin
                proControlsPlugin = cameraEngine.getPlugin("ProControls") as? ProControlsPlugin
                exposureControlPlugin = cameraEngine.getPlugin("ExposureControl") as? ExposureControlPlugin
                cropPlugin = cameraEngine.getPlugin("Crop") as? CropPlugin
                dualCameraPiPPlugin = cameraEngine.getPlugin("DualCameraPiP") as? DualCameraPiPPlugin
                advancedVideoRecordingPlugin = cameraEngine.getPlugin("AdvancedVideoRecording") as? AdvancedVideoRecordingPlugin

                val registeredCount = cameraEngine.getAllPlugins().size
                Log.i(TAG, "✅ Retrieved $registeredCount plugin references after initialization")

                // Observe video recording state to hide/show grid overlay
                setupVideoRecordingObserver()

                // Setup plugin dropdown AFTER async initialize() completes (plugins now registered)
                setupPluginDropdown()

                // Create camera configuration
                val config = CameraConfig(
                    cameraIndex = cameraIndex,
                    enablePreview = true,
                    enableImageCapture = true,
                    enableVideoCapture = true,
                    enableImageAnalysis = false
                )

                // Bind camera with configuration
                val bindResult = cameraEngine.bindCamera(config)
                if (bindResult.isFailure) {
                    handleCameraError("Camera binding failed: ${bindResult.exceptionOrNull()?.message}")
                    return@launch
                }

                // Verify UseCase binding after camera initialization
                val imageCapture = cameraEngine.getImageCapture()
                val videoCapture = cameraEngine.getVideoCapture()
                val preview = cameraEngine.getPreview()

                Log.i(TAG, "📋 UseCase Verification:")
                Log.i(TAG, "   Preview: ${if (preview != null) "✅ Bound" else "❌ NULL"}")
                Log.i(TAG, "   ImageCapture: ${if (imageCapture != null) "✅ Bound" else "❌ NULL"}")
                Log.i(TAG, "   VideoCapture: ${if (videoCapture != null) "✅ Bound" else "❌ NULL"}")

                if (imageCapture == null) {
                    Log.e(TAG, "❌ CRITICAL: ImageCapture is NULL - photo capture will fail!")
                    handleCameraError("Photo capture not available - camera initialization failed")
                    return@launch
                }

                if (videoCapture == null) {
                    Log.w(TAG, "⚠️ WARNING: VideoCapture is NULL - video recording will not work")
                    Log.w(TAG, "   This may be because AdvancedVideoRecordingPlugin is disabled")
                    // Don't abort - allow photo capture to work
                }

                // Set up preview
                preview?.setSurfaceProvider(binding.previewView.surfaceProvider)

                // Configure autofocus plugin with preview (with null check)
                if (autoFocusPlugin != null) {
                    autoFocusPlugin!!.setPreviewView(binding.previewView)
                } else {
                    Log.w(TAG, "⚠️ AutoFocus plugin not initialized, skipping preview setup")
                }

                // Initialize Camera2 controller for manual controls
                initializeCamera2Controller()

                // Initialize performance monitor
                initializePerformanceMonitor()

                // Setup plugin UI overlays (grid, crop, barcode, etc.)
                setupPluginUIOverlays()

                // Set up dual camera PiP system
                setupDualCameraPiP()

                // Set up advanced video recording
                setupAdvancedVideoRecording()

                // Update flash button state
                updateFlashButton()

                // Update video button state based on camera mode
                updateVideoButtonState()

                // Initialize zoom controller for pinch-to-zoom
                val camera = cameraEngine.getCurrentCamera()
                if (camera != null && zoomController == null) {
                    zoomController = com.customcamera.app.camera2.ZoomController(this@CameraActivityEngine).apply {
                        initialize(cameraIndex.toString(), camera)
                    }
                    Log.i(TAG, "Zoom controller initialized for pinch-to-zoom")
                }

                // Setup pinch-to-zoom gestures
                setupPinchToZoom()

                // Hide enhanced loading indicator
                loadingIndicatorManager.hideLoading()

                // Initialize diagnostic overlay with current camera state and sensor info
                binding.diagnosticOverlay.updateSensorInfo()
                camera?.cameraInfo?.cameraState?.value?.let { state ->
                    binding.diagnosticOverlay.updateCameraState("camera_$cameraIndex", state)
                }
                binding.diagnosticOverlay.logLifecycleEvent("Camera initialized successfully")

                Log.i(TAG, "✅ Camera started successfully with engine")

            } catch (e: Exception) {
                Log.e(TAG, "Failed to start camera with engine", e)
                loadingIndicatorManager.hideLoading()
                handleCameraError("Camera startup failed: ${e.message}")
            }
        }
    }

    /**
     * Handle capture button press based on current mode
     * Routes to appropriate action (photo, video start/stop, or night photo)
     */
    private fun handleCapture() {
        when (currentMode) {
            CaptureMode.PHOTO -> {
                capturePhoto()
            }
            CaptureMode.VIDEO -> {
                // Toggle video recording (start/stop)
                toggleVideoRecording()
            }
            CaptureMode.NIGHT -> {
                // Capture photo with night mode enabled
                capturePhoto()
            }
        }
    }

    private fun capturePhoto() {
        Log.i(TAG, "📸 capturePhoto() called")

        // Detailed diagnostic logging
        val imageCapture = cameraEngine.getImageCapture()
        val currentMode = cameraEngine.getCurrentMode()
        val isPiPActive = isPiPEnabled

        Log.i(TAG, "📋 Capture State:")
        Log.i(TAG, "   ImageCapture: ${if (imageCapture != null) "✅ Available" else "❌ NULL"}")
        Log.i(TAG, "   Camera Mode: $currentMode")
        Log.i(TAG, "   PiP Enabled: $isPiPActive")
        Log.i(TAG, "   Camera Index: $cameraIndex")

        if (imageCapture == null) {
            Log.e(TAG, "❌ CRITICAL: ImageCapture is NULL - cannot take photo")
            Log.e(TAG, "   This indicates camera was not properly initialized")
            Log.e(TAG, "   Check UseCase binding logs above for details")

            hapticManager.error()
            com.customcamera.app.presentation.EnhancedToast.error(
                this,
                "Camera not ready - ImageCapture not available"
            )
            return
        }

        try {
            // Log photo capture operation
            com.customcamera.app.debug.GlobalAPIMonitor.getInstance()?.logCameraControl(
                "capturePhoto",
                mapOf(
                    "timestamp" to System.currentTimeMillis(),
                    "cameraIndex" to cameraIndex
                )
            )

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val displayName = "$timestamp.jpg"

            // Check if in concurrent camera mode (dual camera capture)
            val currentMode = cameraEngine.getCurrentMode()
            val isDualCamera = currentMode is CameraMode.Concurrent && dualCameraPiPPlugin != null

            // Check if crop is enabled
            val isCropEnabled = cropPlugin!!.isEnabled && cropPlugin!!.isCropEnabled()

            Log.i(TAG, "Photo capture setup: dualCamera=$isDualCamera, crop=$isCropEnabled")

            if (isDualCamera || isCropEnabled) {
                // Dual camera and crop modes need item URI for manual processing
                Log.i(TAG, "Using item URI approach for dual camera/crop mode")

                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/Camera")
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    }
                }

                val imageUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                if (imageUri == null) {
                    Log.e(TAG, "Failed to create MediaStore entry")
                    Toast.makeText(this, "Failed to create photo entry", Toast.LENGTH_SHORT).show()
                    return
                }

                // Use the legacy capture path that expects item URI
                captureRegularPhoto(null, imageUri, displayName, contentValues)
            } else {
                // Simple capture - use collection URI approach
                Log.i(TAG, "Using collection URI approach for simple capture")

                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/Camera")
                    }
                }

                val collectionUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                val outputFileOptions = ImageCapture.OutputFileOptions.Builder(
                    contentResolver,
                    collectionUri,
                    contentValues
                ).build()

                // Use the new collection URI capture path
                captureRegularPhoto(outputFileOptions, null, displayName, null)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Photo capture setup failed with engine", e)
            Toast.makeText(this, "Capture failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun captureRegularPhoto(
        outputFileOptions: ImageCapture.OutputFileOptions?,
        imageUri: android.net.Uri?,
        displayName: String,
        contentValues: ContentValues?
    ) {
        val imageCapture = cameraEngine.getImageCapture() ?: return

        // Show photo capture loading
        loadingIndicatorManager.showLoading(
            binding.root as ViewGroup,
            LoadingIndicatorManager.LoadingType.PHOTO_CAPTURE,
            autoDismiss = 2000L
        )

        // Check if in concurrent camera mode (dual camera capture)
        val currentMode = cameraEngine.getCurrentMode()
        val isDualCamera = currentMode is CameraMode.Concurrent && dualCameraPiPPlugin != null

        Log.i(TAG, "Photo capture mode check: currentMode=$currentMode, isDualCamera=$isDualCamera, pipPlugin=$dualCameraPiPPlugin")

        if (isDualCamera && dualCameraPiPPlugin != null) {
            // Dual camera capture: Get PiP bitmap from PreviewView
            Log.i(TAG, "📸 Capturing dual camera photo...")
            imageCapture.takePicture(
                ContextCompat.getMainExecutor(this),
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(mainImage: ImageProxy) {
                        try {
                            // Verify we're on main thread (required by PreviewView.getBitmap())
                            check(android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
                                "PreviewView.getBitmap() must be called on the main thread"
                            }

                            // Get PiP bitmap from PreviewView (must run on main thread)
                            val pipPreviewView = dualCameraPiPPlugin?.getPiPPreviewView()
                            val pipBitmap = pipPreviewView?.bitmap

                            if (pipBitmap == null) {
                                Log.e(TAG, "PiP bitmap not available, saving main camera only")
                                saveSingleImage(mainImage, imageUri!!, displayName, contentValues!!)
                                return
                            }

                            try {
                                // Get PiP overlay position
                                val pipRect = dualCameraPiPPlugin?.getPiPOverlayRect() ?: android.graphics.RectF(0.7f, 0.7f, 0.95f, 0.9f)

                                Log.i(TAG, "PiP bitmap: ${pipBitmap.width}x${pipBitmap.height}, PiP rect: $pipRect")

                                // Composite images using PreviewView.bitmap approach and save to MediaStore
                                Log.i(TAG, "Calling DualCameraCompositor.compositeImages() with MediaStore URI...")
                                val success = com.customcamera.app.utils.DualCameraCompositor.compositeImagesToUri(
                                    mainImage = mainImage,
                                    pipBitmap = pipBitmap,
                                    pipRect = pipRect,
                                    contentResolver = contentResolver,
                                    imageUri = imageUri!!,
                                    contentValues = contentValues!!
                                )

                                mainImage.close()

                                if (success) {
                                    loadingIndicatorManager.hideLoading()
                                    com.customcamera.app.presentation.EnhancedToast.dualCameraPhoto(this@CameraActivityEngine, displayName)
                                    hapticManager.photoCapture()
                                    Log.i(TAG, "✅ Dual camera photo saved: $imageUri")
                                    animateCaptureButton()
                                } else {
                                    Log.e(TAG, "Composite failed")
                                    loadingIndicatorManager.hideLoading()
                                    hapticManager.error()
                                    com.customcamera.app.presentation.EnhancedToast.error(this@CameraActivityEngine, "Failed to save photo")
                                }
                            } finally {
                                // Always recycle PiP bitmap to prevent memory leak
                                pipBitmap.recycle()
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Dual camera capture failed", e)
                            mainImage.close()
                            loadingIndicatorManager.hideLoading()
                            hapticManager.error()
                            com.customcamera.app.presentation.EnhancedToast.error(this@CameraActivityEngine, "Photo capture failed")
                        }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        loadingIndicatorManager.hideLoading()
                        hapticManager.error()
                        Log.e(TAG, "Photo capture failed with engine", exception)
                        com.customcamera.app.presentation.EnhancedToast.error(this@CameraActivityEngine, "Photo capture failed")
                    }
                }
            )
        } else {
            // Single camera capture
            if (isDualCamera) {
                Log.w(TAG, "⚠️ In dual camera mode but PiP plugin is unavailable. Falling back to regular capture.")
            }
            // Check if crop is enabled
            val isCropEnabled = cropPlugin!!.isEnabled && cropPlugin!!.isCropEnabled()

            if (isCropEnabled) {
                Log.i(TAG, "📸 Capturing photo with crop enabled...")
                // Crop enabled: Capture to memory, apply crop, then save
                imageCapture.takePicture(
                    ContextCompat.getMainExecutor(this),
                    object : ImageCapture.OnImageCapturedCallback() {
                        override fun onCaptureSuccess(image: ImageProxy) {
                            lifecycleScope.launch(Dispatchers.IO) {
                                try {
                                    // Apply crop and get cropped bitmap
                                    val croppedBitmap = cropPlugin!!.applyCropToBitmap(image)
                                    image.close()

                                    if (croppedBitmap != null) {
                                        // Save cropped bitmap to MediaStore
                                        contentResolver.openOutputStream(imageUri!!)?.use { out ->
                                            croppedBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, out)
                                        }
                                        croppedBitmap.recycle()

                                        // Mark as complete (remove IS_PENDING flag)
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                            contentValues!!.clear()
                                            contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                                            contentResolver.update(imageUri, contentValues, null, null)
                                        }

                                        withContext(Dispatchers.Main) {
                                            loadingIndicatorManager.hideLoading()
                                            com.customcamera.app.presentation.EnhancedToast.photoSaved(this@CameraActivityEngine, displayName)
                                            hapticManager.photoCapture()
                                            Log.i(TAG, "✅ Cropped photo saved: $imageUri")
                                            animateCaptureButton()
                                        }
                                    } else {
                                        withContext(Dispatchers.Main) {
                                            loadingIndicatorManager.hideLoading()
                                            hapticManager.error()
                                            com.customcamera.app.presentation.EnhancedToast.error(this@CameraActivityEngine, "Failed to crop photo")
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "Failed to save cropped photo", e)
                                    image.close()
                                    withContext(Dispatchers.Main) {
                                        loadingIndicatorManager.hideLoading()
                                        hapticManager.error()
                                        com.customcamera.app.presentation.EnhancedToast.error(this@CameraActivityEngine, "Photo capture failed")
                                    }
                                }
                            }
                        }

                        override fun onError(exception: ImageCaptureException) {
                            loadingIndicatorManager.hideLoading()
                            hapticManager.error()
                            Log.e(TAG, "Photo capture failed", exception)
                            com.customcamera.app.presentation.EnhancedToast.error(this@CameraActivityEngine, "Photo capture failed")
                        }
                    }
                )
            } else {
                // Crop disabled: Save directly to MediaStore (faster)
                Log.i(TAG, "📸 Capturing photo without crop...")

                // Collection URI path (outputFileOptions provided, imageUri/contentValues null)
                val finalOutputFileOptions = outputFileOptions ?: run {
                    // Fallback: Legacy item URI path (should not happen if logic above is correct)
                    Log.w(TAG, "⚠️ OutputFileOptions is null, using legacy item URI fallback")
                    ImageCapture.OutputFileOptions.Builder(contentResolver, imageUri!!, ContentValues()).build()
                }

                imageCapture.takePicture(
                    finalOutputFileOptions,
                    ContextCompat.getMainExecutor(this),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                            val savedUri = output.savedUri

                            // Only update IS_PENDING if using legacy item URI path
                            if (imageUri != null && contentValues != null) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                    contentValues.clear()
                                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                                    contentResolver.update(imageUri, contentValues, null, null)
                                }
                            }
                            // Collection URI path: CameraX handles IS_PENDING automatically

                            loadingIndicatorManager.hideLoading()
                            com.customcamera.app.presentation.EnhancedToast.photoSaved(this@CameraActivityEngine, displayName)
                            hapticManager.photoCapture()
                            Log.i(TAG, "✅ Photo saved successfully: $savedUri")
                            animateCaptureButton()
                        }

                        override fun onError(exception: ImageCaptureException) {
                            loadingIndicatorManager.hideLoading()
                            hapticManager.error()
                            Log.e(TAG, "❌ Photo capture failed", exception)
                            Log.e(TAG, "Exception details: ${exception.javaClass.simpleName}, code=${exception.imageCaptureError}")
                            com.customcamera.app.presentation.EnhancedToast.error(this@CameraActivityEngine, "Photo capture failed")
                        }
                    }
                )
            }
        }
    }

    /**
     * Helper method to save a single ImageProxy to file (fallback for dual camera)
     */
    private fun saveSingleImage(image: ImageProxy, imageUri: android.net.Uri, displayName: String, contentValues: ContentValues) {
        try {
            // Convert ImageProxy to bytes and save to MediaStore
            val buffer = image.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)

            contentResolver.openOutputStream(imageUri)?.use { out ->
                out.write(bytes)
            }
            image.close()

            // Mark as complete (remove IS_PENDING flag)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                contentResolver.update(imageUri, contentValues, null, null)
            }

            loadingIndicatorManager.hideLoading()
            com.customcamera.app.presentation.EnhancedToast.photoSaved(this@CameraActivityEngine, displayName)
            Log.i(TAG, "Photo saved (single): $imageUri")
            animateCaptureButton()
        } catch (e: Exception) {
            image.close()
            loadingIndicatorManager.hideLoading()
            Log.e(TAG, "Failed to save image", e)
            com.customcamera.app.presentation.EnhancedToast.error(this@CameraActivityEngine, "Failed to save photo")
        }
    }

    private fun captureLongExposurePhoto(outputFileOptions: ImageCapture.OutputFileOptions, imageUri: android.net.Uri, displayName: String, contentValues: ContentValues) {
        val nightModePlugin = cameraEngine.getPlugin("NightMode") as? NightModePlugin ?: return

        // Show long exposure capture loading
        loadingIndicatorManager.showLoading(
            binding.root as ViewGroup,
            LoadingIndicatorManager.LoadingType.PHOTO_CAPTURE,
            autoDismiss = 10000L // Longer timeout for long exposure
        )

        lifecycleScope.launch {
            try {
                val exposureTime = nightModePlugin.getCurrentExposureTime()
                com.customcamera.app.presentation.EnhancedToast.info(this@CameraActivityEngine, "Capturing long exposure (${exposureTime}ms)...", Toast.LENGTH_LONG)

                val success = nightModePlugin.captureLongExposurePhoto(outputFileOptions)

                loadingIndicatorManager.hideLoading()

                if (success) {
                    // Mark as complete (remove IS_PENDING flag)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        contentValues.clear()
                        contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                        contentResolver.update(imageUri, contentValues, null, null)
                    }
                    com.customcamera.app.presentation.EnhancedToast.photoSaved(this@CameraActivityEngine, displayName)
                    Log.i(TAG, "Long exposure photo saved: $imageUri")
                    animateCaptureButton()
                } else {
                    Log.e(TAG, "Long exposure photo capture failed")
                    com.customcamera.app.presentation.EnhancedToast.error(this@CameraActivityEngine, "Long exposure capture failed")
                }

            } catch (e: Exception) {
                loadingIndicatorManager.hideLoading()
                Log.e(TAG, "Long exposure capture error", e)
                com.customcamera.app.presentation.EnhancedToast.error(this@CameraActivityEngine, "Long exposure error: ${e.message}", Toast.LENGTH_LONG)
            }
        }
    }

    /**
     * Capture entire window including both camera surfaces using PixelCopy
     */
    private fun captureScreenFallback(photoFile: File) {
        // Check API level
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) {
            Log.e(TAG, "PixelCopy requires API 26+, current: ${android.os.Build.VERSION.SDK_INT}")
            loadingIndicatorManager.hideLoading()
            hapticManager.error()
            com.customcamera.app.presentation.EnhancedToast.error(this, "Screen capture requires Android 8.0+")
            return
        }

        lifecycleScope.launch(Dispatchers.Main) {
            try {
                Log.i(TAG, "📸 Capturing window with PixelCopy (both camera surfaces)")

                // Create bitmap for entire window
                val width = window.decorView.width
                val height = window.decorView.height
                val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)

                // Use PixelCopy to capture window including camera surfaces
                android.view.PixelCopy.request(
                    window,
                    bitmap,
                    { copyResult ->
                        lifecycleScope.launch(Dispatchers.IO) {
                            try {
                                if (copyResult == android.view.PixelCopy.SUCCESS) {
                                    Log.i(TAG, "✅ PixelCopy successful: ${bitmap.width}x${bitmap.height}")

                                    // Save bitmap
                                    photoFile.outputStream().use { out ->
                                        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, out)
                                    }

                                    Log.i(TAG, "✅ Dual camera photo saved: ${photoFile.name}")

                                    withContext(Dispatchers.Main) {
                                        loadingIndicatorManager.hideLoading()
                                        hapticManager.photoCapture()
                                        com.customcamera.app.presentation.EnhancedToast.success(
                                            this@CameraActivityEngine,
                                            "Dual camera photo saved: ${photoFile.name}"
                                        )
                                        animateCaptureButton()
                                    }
                                } else {
                                    Log.e(TAG, "❌ PixelCopy failed with result: $copyResult")
                                    withContext(Dispatchers.Main) {
                                        loadingIndicatorManager.hideLoading()
                                        hapticManager.error()
                                        com.customcamera.app.presentation.EnhancedToast.error(
                                            this@CameraActivityEngine,
                                            "Screen capture failed"
                                        )
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to save captured bitmap", e)
                                withContext(Dispatchers.Main) {
                                    loadingIndicatorManager.hideLoading()
                                    hapticManager.error()
                                    com.customcamera.app.presentation.EnhancedToast.error(
                                        this@CameraActivityEngine,
                                        "Failed to save photo: ${e.message}"
                                    )
                                }
                            } finally {
                                bitmap.recycle()
                            }
                        }
                    },
                    android.os.Handler(android.os.Looper.getMainLooper())
                )
            } catch (e: Exception) {
                Log.e(TAG, "Screen capture setup failed", e)
                loadingIndicatorManager.hideLoading()
                hapticManager.error()
                com.customcamera.app.presentation.EnhancedToast.error(this@CameraActivityEngine, "Screen capture failed: ${e.message}")
            }
        }
    }

    private fun switchCamera() {
        lifecycleScope.launch {
            try {
                // Stop video recording if active during camera switch
                if (advancedVideoRecordingPlugin?.isRecording?.value == true) {
                    Log.i(TAG, "Stopping video recording for camera switch")
                    advancedVideoRecordingPlugin?.stopRecording()
                }

                val availableCameras = cameraEngine.availableCameras.value

                if (availableCameras.size > 1) {
                    // Cycle to next camera
                    val oldCameraIndex = cameraIndex
                    cameraIndex = (cameraIndex + 1) % availableCameras.size
                    Log.i(TAG, "Switching to camera $cameraIndex with engine")

                    // Log camera switch operation
                    com.customcamera.app.debug.GlobalAPIMonitor.getInstance()?.logCameraControl(
                        "switchCamera",
                        mapOf(
                            "fromIndex" to oldCameraIndex,
                            "toIndex" to cameraIndex,
                            "timestamp" to System.currentTimeMillis()
                        )
                    )

                    // Switch camera using engine with video support
                    val config = CameraConfig(
                        cameraIndex = cameraIndex,
                        enablePreview = true,
                        enableImageCapture = true,
                        enableVideoCapture = true,
                        enableImageAnalysis = isBarcodeScanningEnabled
                    )

                    val result = cameraEngine.bindCamera(config)
                    if (result.isSuccess) {
                        // Reinitialize Camera2 controllers for new camera
                        initializeCamera2Controller()

                        // Update preview connection
                        val preview = cameraEngine.getPreview()
                        preview?.setSurfaceProvider(binding.previewView.surfaceProvider)

                        // Restore plugin UI overlays (grid, crop, etc.)
                        binding.pluginOverlayContainer.removeAllViews()
                        setupPluginUIOverlays()

                        updateFlashButton()

                        // ✅ CRITICAL FIX (Bug #1): Refresh plugin references to use new camera
                        // After camera switch, plugins still hold references to OLD camera's use cases
                        // This caused video recording to fail after switching cameras
                        advancedVideoRecordingPlugin = cameraEngine.getPlugin("AdvancedVideoRecording") as? AdvancedVideoRecordingPlugin

                        animateSwitchButton()

                        // Haptic feedback for successful camera switch
                        hapticManager.success()

                        Log.i(TAG, "✅ Camera switched successfully, plugins refreshed for new camera")
                    } else {
                        // Haptic feedback for error
                        hapticManager.error()

                        Log.e(TAG, "❌ Camera switch failed: ${result.exceptionOrNull()?.message}")
                        com.customcamera.app.presentation.EnhancedToast.error(this@CameraActivityEngine, "Failed to switch camera")
                    }
                } else {
                    // Haptic feedback for unavailable action
                    hapticManager.mediumTap()

                    com.customcamera.app.presentation.EnhancedToast.info(this@CameraActivityEngine, "Only one camera available")
                }
            } catch (e: Exception) {
                // Haptic feedback for error
                hapticManager.error()

                Log.e(TAG, "Error switching camera with engine", e)
                com.customcamera.app.presentation.EnhancedToast.error(this@CameraActivityEngine, "Switch failed: ${e.message}")
            }
        }
    }

    private fun toggleFlash() {
        val camera = cameraEngine.getCurrentCamera() ?: return

        if (camera.cameraInfo.hasFlashUnit()) {
            isFlashOn = !isFlashOn

            // Log flash toggle operation
            com.customcamera.app.debug.GlobalAPIMonitor.getInstance()?.logCameraControl(
                "toggleFlash",
                mapOf(
                    "enabled" to isFlashOn,
                    "cameraIndex" to cameraIndex,
                    "timestamp" to System.currentTimeMillis()
                )
            )

            camera.cameraControl.enableTorch(isFlashOn)
            updateFlashButton()
            animateFlashButton()

            // Haptic feedback for toggle
            hapticManager.mediumTap()
        } else {
            // Haptic feedback for unavailable action
            hapticManager.mediumTap()

            com.customcamera.app.presentation.EnhancedToast.info(this, "Flash not available")
        }
    }

    private fun updateFlashButton() {
        val camera = cameraEngine.getCurrentCamera() ?: return

        if (camera.cameraInfo.hasFlashUnit()) {
            binding.flashButton.visibility = View.VISIBLE
            binding.flashButton.alpha = if (isFlashOn) 1.0f else 0.6f
            // Update icon based on flash state
            val iconRes = if (isFlashOn) R.drawable.ic_flash_on else R.drawable.ic_flash_off
            binding.flashButton.setImageResource(iconRes)
        } else {
            binding.flashButton.visibility = View.GONE
        }
    }

    private fun openGallery() {
        try {
            val intent = Intent(this, GalleryActivity::class.java)
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open gallery", e)
            com.customcamera.app.presentation.EnhancedToast.error(this, "Gallery error: ${e.message}")
        }
    }

    private fun openSettings() {
        try {
            val intent = Intent(this, SimpleSettingsActivity::class.java)
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open settings", e)
            com.customcamera.app.presentation.EnhancedToast.error(this, "Settings error: ${e.message}")
        }
    }

    private fun openFullSettings() {
        try {
            val intent = Intent(this, SimpleSettingsActivity::class.java)
            startActivity(intent)
            Log.i(TAG, "Opened settings page")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open settings", e)
            com.customcamera.app.presentation.EnhancedToast.error(this, "Settings error: ${e.message}", Toast.LENGTH_LONG)
        }
    }

    private fun toggleNightMode() {
        isNightModeEnabled = !isNightModeEnabled

        // Log night mode toggle operation
        com.customcamera.app.debug.GlobalAPIMonitor.getInstance()?.logCameraControl(
            "toggleNightMode",
            mapOf(
                "enabled" to isNightModeEnabled,
                "cameraIndex" to cameraIndex,
                "timestamp" to System.currentTimeMillis()
            )
        )

        try {
            // Get the enhanced NightModePlugin from the registered plugins
            val nightModePlugin = cameraEngine.getPlugin("NightMode") as? NightModePlugin

            if (nightModePlugin != null) {
                lifecycleScope.launch {
                    try {
                        if (isNightModeEnabled) {
                            // Use new async toggle method
                            nightModePlugin.toggleNightMode()

                            // Add night mode overlay to UI if available
                            nightModePlugin.getNightModeOverlay()?.let { overlay ->
                                val rootView = binding.root
                                val layoutParams = android.widget.FrameLayout.LayoutParams(
                                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT
                                )
                                rootView.addView(overlay, layoutParams)
                                Log.i(TAG, "Night mode overlay added to UI")
                            }

                            // Show enhanced feedback with exposure info
                            val exposureTime = nightModePlugin.getCurrentExposureTime()
                            val message = "Night mode enabled - Long exposure: ${exposureTime}ms"
                            com.customcamera.app.presentation.EnhancedToast.featureActivated(this@CameraActivityEngine, "Night Mode")

                        } else {
                            nightModePlugin.toggleNightMode()

                            // Remove night mode overlay if present
                            nightModePlugin.getNightModeOverlay()?.let { overlay ->
                                val rootView = binding.root
                                rootView.removeView(overlay)
                                Log.i(TAG, "Night mode overlay removed from UI")
                            }

                            com.customcamera.app.presentation.EnhancedToast.featureDeactivated(this@CameraActivityEngine, "Night Mode")
                        }

                        // Update button appearance with enhanced visual feedback
                        binding.nightModeButton.alpha = if (isNightModeEnabled) 1.0f else 0.6f

                        // Add subtle glow effect for night mode
                        if (isNightModeEnabled) {
                            binding.nightModeButton.animate()
                                .scaleX(1.1f)
                                .scaleY(1.1f)
                                .setDuration(200)
                                .withEndAction {
                                    binding.nightModeButton.animate()
                                        .scaleX(1.0f)
                                        .scaleY(1.0f)
                                        .setDuration(200)
                                }
                        }

                        // Haptic feedback for successful toggle
                        hapticManager.mediumTap()

                        Log.i(TAG, "Night mode v2.0 ${if (isNightModeEnabled) "enabled" else "disabled"}")

                    } catch (e: Exception) {
                        Log.e(TAG, "Error in night mode async toggle", e)
                        com.customcamera.app.presentation.EnhancedToast.error(this@CameraActivityEngine, "Night mode error: ${e.message}")
                    }
                }

            } else {
                com.customcamera.app.presentation.EnhancedToast.error(this, "Night mode plugin not available")
                Log.w(TAG, "Night mode plugin not found")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error toggling night mode", e)
            com.customcamera.app.presentation.EnhancedToast.error(this, "Night mode error: ${e.message}")
        }
    }

    private fun toggleHistogram() {
        isHistogramVisible = !isHistogramVisible

        try {
            if (isHistogramVisible) {
                // Create and show histogram display
                if (histogramView == null) {
                    histogramView = com.customcamera.app.analysis.HistogramView(this)

                    // Add to camera layout
                    val rootView = binding.root
                    val layoutParams = android.widget.FrameLayout.LayoutParams(
                        400, // Fixed width
                        200  // Fixed height
                    ).apply {
                        gravity = android.view.Gravity.TOP or android.view.Gravity.START
                        topMargin = 100
                        leftMargin = 20
                    }
                    rootView.addView(histogramView, layoutParams)
                }

                histogramView?.visibility = android.view.View.VISIBLE

                // Enable histogram plugin and connect to display
                val histogramPlugin = cameraEngine.getPlugin("Histogram") as? HistogramPlugin
                histogramPlugin?.setHistogramEnabled(true)

                // Start real-time histogram updates
                startHistogramUpdates()

                Toast.makeText(this, "Histogram display enabled", Toast.LENGTH_SHORT).show()
                Log.i(TAG, "Histogram display shown")

            } else {
                // Hide histogram display
                histogramView?.visibility = android.view.View.GONE

                // Disable histogram plugin
                val histogramPlugin = cameraEngine.getPlugin("Histogram") as? HistogramPlugin
                histogramPlugin?.setHistogramEnabled(false)

                Toast.makeText(this, "Histogram display disabled", Toast.LENGTH_SHORT).show()
                Log.i(TAG, "Histogram display hidden")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error toggling histogram", e)
            Toast.makeText(this, "Histogram error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Toggle RAW/DNG capture mode (Phase 9A)
     */
    private fun toggleRawCapture() {
        try {
            val rawPlugin = cameraEngine.getPlugin("RAWCapture") as? RAWCapturePlugin

            if (rawPlugin != null) {
                val isEnabled = rawPlugin.toggleRawCapture()

                if (isEnabled) {
                    if (rawPlugin.isRawSupported()) {
                        val maxSize = rawPlugin.getMaxRawSize()
                        val message = "RAW capture enabled - DNG format${maxSize?.let { "\n${it.width}x${it.height}" } ?: ""}"
                        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                        Log.i(TAG, "RAW/DNG capture enabled: $maxSize")
                    } else {
                        Toast.makeText(this, "RAW capture not supported by this camera", Toast.LENGTH_LONG).show()
                        Log.w(TAG, "RAW capture not supported")
                    }
                } else {
                    Toast.makeText(this, "RAW capture disabled - Using JPEG only", Toast.LENGTH_SHORT).show()
                    Log.i(TAG, "RAW capture disabled")
                }

                // Provide haptic feedback
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)

            } else {
                Toast.makeText(this, "RAW capture plugin not available", Toast.LENGTH_SHORT).show()
                Log.w(TAG, "RAW capture plugin not found")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error toggling RAW capture", e)
            Toast.makeText(this, "RAW capture error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // NOTE: Manual controls are now handled by ProControlsPlugin and ManualControlsPluginSimple
    // These plugins are automatically integrated into the plugin dropdown system

    private fun showAdvancedControls() {
        lifecycleScope.launch {
            try {
                // Show current camera info and controls status
                val cameraInfo = cameraInfoPlugin!!.getCameraInfo()
                val exposureSettings = exposureControlPlugin!!.getCurrentSettings()
                val proControlsSettings = proControlsPlugin!!.getCurrentSettings()

                val info = buildString {
                    appendLine("=== Camera Information ===")
                    cameraInfo.forEach { (key, value) ->
                        appendLine("$key: $value")
                    }
                    appendLine()
                    appendLine("=== Exposure Controls ===")
                    exposureSettings.forEach { (key, value) ->
                        appendLine("$key: $value")
                    }
                    appendLine()
                    appendLine("=== Pro Controls ===")
                    proControlsSettings.forEach { (key, value) ->
                        appendLine("$key: $value")
                    }
                }

                Log.i(TAG, "Camera Controls Info:\n$info")
                Toast.makeText(this@CameraActivityEngine, "Camera info logged - Check logcat", Toast.LENGTH_SHORT).show()

                // Demonstrate exposure adjustment
                demonstrateExposureControl()

            } catch (e: Exception) {
                Log.e(TAG, "Error showing advanced controls", e)
                Toast.makeText(this@CameraActivityEngine, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun togglePiP() {
        // Use the new dual camera PiP system
        toggleDualCameraPiP()
    }

    private fun toggleBarcodeScanning() {
        try {
            val barcodePlugin = cameraEngine.getPlugin("Barcode") as? BarcodePlugin

            if (barcodePlugin != null) {
                // Use the new toggle method from BarcodePlugin
                isBarcodeScanningEnabled = barcodePlugin.toggleScanning()

                Log.i(TAG, "Barcode scanning ${if (isBarcodeScanningEnabled) "enabled" else "disabled"}")
                Log.i(TAG, "BarcodePlugin state - isEnabled: ${barcodePlugin.isEnabled}, isScanningEnabled: ${barcodePlugin.isScanningEnabled()}")

                // Enhanced feedback
                if (isBarcodeScanningEnabled) {
                    hapticManager.featureActivated()
                    com.customcamera.app.presentation.EnhancedToast.featureActivated(this@CameraActivityEngine, "Barcode Scanner")
                } else {
                    hapticManager.featureDeactivated()
                    com.customcamera.app.presentation.EnhancedToast.featureDeactivated(this@CameraActivityEngine, "Barcode Scanner")
                }

                // Enable image analysis and UI if needed (run in background to avoid freezing)
                if (isBarcodeScanningEnabled) {
                    val config = CameraConfig(
                        cameraIndex = cameraIndex,
                        enablePreview = true,
                        enableImageCapture = true,
                        enableVideoCapture = true,
                        enableImageAnalysis = true
                    )

                    // Run camera rebind in background coroutine to avoid UI freeze
                    lifecycleScope.launch(Dispatchers.IO) {
                        val bindResult = cameraEngine.bindCamera(config)
                        if (bindResult.isSuccess) {
                            Log.i(TAG, "Image analysis enabled for barcode detection")
                        }

                        // Switch to Main thread for UI operations
                        withContext(Dispatchers.Main) {
                            // Reconnect preview after rebinding camera with image analysis
                            val preview = cameraEngine.getPreview()
                            preview?.setSurfaceProvider(binding.previewView.surfaceProvider)
                            Log.i(TAG, "Preview reconnected after enabling image analysis")

                            // Add barcode overlay to UI
                            if (barcodeOverlayView == null) {
                                val overlay = com.customcamera.app.barcode.BarcodeOverlayView(this@CameraActivityEngine)
                                barcodeOverlayView = overlay

                                val rootView = binding.root
                                val layoutParams = android.widget.FrameLayout.LayoutParams(
                                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT
                                )
                                rootView.addView(overlay, layoutParams)

                                // Connect overlay to barcode plugin
                                barcodePlugin.setBarcodeOverlay(overlay)
                            }
                            barcodeOverlayView?.setOverlayEnabled(true)

                            // Start barcode detection updates from plugin
                            startBarcodeDetectionUpdates()
                        }
                    }
                } else {
                    // Disable overlay and clear detections
                    barcodeOverlayView?.setOverlayEnabled(false)
                    barcodeOverlayView?.let { overlay ->
                        val rootView = binding.root
                        rootView.removeView(overlay)
                        barcodeOverlayView = null
                    }

                    // Disable image analysis to save resources
                    lifecycleScope.launch(Dispatchers.IO) {
                        val config = CameraConfig(
                            cameraIndex = cameraIndex,
                            enablePreview = true,
                            enableImageCapture = true,
                            enableVideoCapture = true,
                            enableImageAnalysis = false
                        )

                        val bindResult = cameraEngine.bindCamera(config)
                        if (bindResult.isSuccess) {
                            Log.i(TAG, "Image analysis disabled to save resources")
                        }

                        withContext(Dispatchers.Main) {
                            // Reconnect preview after rebinding
                            val preview = cameraEngine.getPreview()
                            preview?.setSurfaceProvider(binding.previewView.surfaceProvider)
                        }
                    }
                }

                // Update settings
                val settingsManager = com.customcamera.app.engine.SettingsManager.getInstance(this)
                settingsManager.setPluginEnabled("Barcode", isBarcodeScanningEnabled)
            } else {
                Log.w(TAG, "BarcodePlugin not found")
                com.customcamera.app.presentation.EnhancedToast.error(this, "Barcode plugin not available")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error toggling barcode scanning", e)
            com.customcamera.app.presentation.EnhancedToast.error(this, "Barcode scanning error: ${e.message}")
        }
    }

    /**
     * Trigger barcode scanning action (action button)
     * Enables barcode scanning temporarily, shows results, then returns to normal camera
     */
    private fun triggerBarcodeScanning() {
        try {
            if (!isBarcodeScanningEnabled) {
                // Enable barcode scanning (same as toggle)
                toggleBarcodeScanning()

                // Provide haptic feedback
                hapticManager.mediumTap()
                com.customcamera.app.presentation.EnhancedToast.info(this, "Scanning for barcodes...")
            } else {
                // Already scanning - disable
                toggleBarcodeScanning()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error triggering barcode scanning", e)
            com.customcamera.app.presentation.EnhancedToast.error(this, "Barcode scan error: ${e.message}")
        }
    }

    /**
     * Trigger QR code scanning action (action button)
     * Enables QR scanning temporarily, shows results, then returns to normal camera
     */
    private fun triggerQRScanning() {
        try {
            val qrPlugin = cameraEngine.getPlugin("QRScanner") as? QRScannerPlugin

            if (qrPlugin != null) {
                // Get current state from settings manager
                val settingsManager = com.customcamera.app.engine.SettingsManager.getInstance(this)
                val currentlyEnabled = settingsManager.isPluginEnabled("QRScanner")
                val newState = !currentlyEnabled

                // Update plugin state via settings manager
                settingsManager.setPluginEnabled("QRScanner", newState)

                // Provide haptic feedback
                if (newState) {
                    hapticManager.mediumTap()
                    com.customcamera.app.presentation.EnhancedToast.info(this, "Scanning for QR codes...")
                } else {
                    hapticManager.mediumTap()
                    com.customcamera.app.presentation.EnhancedToast.info(this, "QR scanning stopped")
                }

                Log.i(TAG, "QR Scanner ${if (newState) "enabled" else "disabled"}")
            } else {
                Log.w(TAG, "QRScannerPlugin not found")
                com.customcamera.app.presentation.EnhancedToast.error(this, "QR scanner not available")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error triggering QR scanning", e)
            com.customcamera.app.presentation.EnhancedToast.error(this, "QR scan error: ${e.message}")
        }
    }

    private fun toggleGrid() {
        try {
            if (gridOverlayPlugin == null) {
                Log.e(TAG, "Grid plugin not initialized")
                Toast.makeText(this, "Camera not ready", Toast.LENGTH_SHORT).show()
                return
            }

            gridOverlayPlugin!!.toggleGrid()
            val isVisible = gridOverlayPlugin!!.isGridVisible()

            // Enhanced feedback
            if (isVisible) {
                hapticManager.featureActivated()
                com.customcamera.app.presentation.EnhancedToast.featureActivated(this@CameraActivityEngine, "Grid Overlay")
            } else {
                hapticManager.featureDeactivated()
                com.customcamera.app.presentation.EnhancedToast.featureDeactivated(this@CameraActivityEngine, "Grid Overlay")
            }

            // Refresh plugin UI overlays
            lifecycleScope.launch {
                binding.pluginOverlayContainer.removeAllViews()
                setupPluginUIOverlays()
            }

            Log.i(TAG, "Grid toggled: $isVisible")
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling grid", e)
            Toast.makeText(this, "Grid toggle error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleCrop() {
        try {
            if (cropPlugin == null) {
                Log.e(TAG, "Crop plugin not initialized")
                Toast.makeText(this, "Camera not ready", Toast.LENGTH_SHORT).show()
                return
            }

            // Toggle crop mode
            if (cropPlugin!!.isEnabled) {
                cropPlugin!!.disableCrop()

                // Refresh plugin UI overlays
                lifecycleScope.launch {
                    binding.pluginOverlayContainer.removeAllViews()
                    setupPluginUIOverlays()
                }

                Toast.makeText(
                    this,
                    "Crop mode disabled",
                    Toast.LENGTH_SHORT
                ).show()
                Log.i(TAG, "Crop mode disabled")
            } else {
                cropPlugin!!.enableCrop()
                Toast.makeText(
                    this,
                    "Crop mode enabled - drag to adjust crop area",
                    Toast.LENGTH_LONG
                ).show()
                Log.i(TAG, "Crop mode enabled")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling crop", e)
            Toast.makeText(this, "Crop toggle failed", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Setup plugin dropdown with filtered plugins that have showInDropdown = true
     */
    private fun setupPluginDropdown() {
        Log.i(TAG, "📋 Setting up plugin dropdown menu...")

        try {
            // Get all registered plugin instances from CameraEngine
            val allPlugins = cameraEngine.getAllPlugins()
            Log.d(TAG, "Found ${allPlugins.size} registered plugins")

            // Plugins excluded from dropdown (have dedicated UI buttons)
            val excludedPlugins = setOf(
                "NightMode",        // Has dedicated button
                "DualCameraPiP",    // Has dedicated button
                "AutoFocus",        // Always active (no toggle)
                "ExposureControl",  // Always active (no toggle)
                "ManualFocus",      // Always active (no toggle)
                "ProControls"       // Always active (no toggle)
            )

            // Filter plugins: user-toggleable AND not excluded
            val dropdownPlugins = allPlugins.filter { plugin ->
                plugin.userToggleable && !excludedPlugins.contains(plugin.name)
            }

            Log.i(TAG, "Filtered to ${dropdownPlugins.size} dropdown plugins (excluding dedicated buttons)")
            dropdownPlugins.forEach { plugin ->
                Log.d(TAG, "  - ${plugin.name} (${plugin.displayName})")
            }

            // Pass plugins to dropdown view
            binding.pluginDropdownView.setPlugins(dropdownPlugins)

            // Set up toggle callback
            binding.pluginDropdownView.onPluginToggled = { plugin, enabled ->
                handlePluginToggle(plugin, enabled)
            }

            // Wire up DiagnosticOverlay plugin to the overlay view
            val diagnosticPlugin = allPlugins.find { it.name == "DiagnosticOverlay" }
            if (diagnosticPlugin is com.customcamera.app.plugins.DiagnosticOverlayPlugin) {
                diagnosticPlugin.overlayToggleCallback = { show ->
                    binding.diagnosticOverlay.visibility = if (show) android.view.View.VISIBLE else android.view.View.GONE
                    Log.i(TAG, "DiagnosticOverlay visibility: ${if (show) "VISIBLE" else "GONE"}")
                }
                Log.i(TAG, "DiagnosticOverlay plugin wired to overlay view")
            }

            Log.i(TAG, "✅ Plugin dropdown configured successfully")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to setup plugin dropdown", e)
            Toast.makeText(this, "Plugin menu setup failed", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Toggle plugin dropdown visibility
     */
    private fun togglePluginDropdown() {
        try {
            binding.pluginDropdownView.toggle()
            hapticManager.mediumTap()

            if (binding.pluginDropdownView.isExpanded()) {
                Log.i(TAG, "Plugin dropdown expanded")
            } else {
                Log.i(TAG, "Plugin dropdown collapsed")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling plugin dropdown", e)
        }
    }

    /**
     * Handle plugin toggle from dropdown menu
     */
    private fun handlePluginToggle(plugin: com.customcamera.app.engine.plugins.CameraPlugin, enabled: Boolean) {
        try {
            Log.i(TAG, "Plugin '${plugin.name}' toggled: $enabled")

            if (enabled) {
                plugin.enable()
                hapticManager.featureActivated()
                com.customcamera.app.presentation.EnhancedToast.featureActivated(
                    this@CameraActivityEngine,
                    plugin.displayName
                )
            } else {
                plugin.disable()
                hapticManager.featureDeactivated()
                com.customcamera.app.presentation.EnhancedToast.featureDeactivated(
                    this@CameraActivityEngine,
                    plugin.displayName
                )
            }

            // Refresh plugin UI overlays if this is a UI plugin
            if (plugin is com.customcamera.app.engine.plugins.UIPlugin) {
                lifecycleScope.launch {
                    binding.pluginOverlayContainer.removeAllViews()
                    setupPluginUIOverlays()
                }
            }

            // Update dropdown to reflect current state
            binding.pluginDropdownView.updatePluginState(plugin.displayName, plugin.isEnabled)

        } catch (e: Exception) {
            Log.e(TAG, "Error handling plugin toggle for '${plugin.name}'", e)
            com.customcamera.app.presentation.EnhancedToast.error(
                this@CameraActivityEngine,
                "Failed to toggle ${plugin.displayName}"
            )
        }
    }

    private suspend fun demonstrateExposureControl() {
        try {
            // Show current exposure
            val currentEV = exposureControlPlugin!!.getCurrentEV()
            Log.i(TAG, "Current exposure: ${currentEV}EV")

            // Perform exposure analysis
            val analysis = exposureControlPlugin!!.analyzeExposure()
            analysis?.let {
                Log.i(TAG, "Exposure analysis: $it")

                if (!it.isOptimal) {
                    Toast.makeText(
                        this,
                        "Exposure ${if (it.underExposed) "under" else if (it.overExposed) "over" else "sub-optimal"}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            // Demonstrate manual exposure adjustment (small change)
            val currentIndex = exposureControlPlugin!!.getCurrentSettings()["currentExposureIndex"] as Int
            val testIndex = (currentIndex + 1).coerceIn(-2, 2) // Small adjustment

            exposureControlPlugin!!.setExposureCompensation(testIndex)
            Log.i(TAG, "Demonstrated exposure adjustment to index: $testIndex")

            // Reset back after a moment
            kotlinx.coroutines.delay(2000)
            exposureControlPlugin!!.setExposureCompensation(currentIndex)
            Log.i(TAG, "Reset exposure to original: $currentIndex")

        } catch (e: Exception) {
            Log.e(TAG, "Error in exposure demonstration", e)
        }
    }

    private fun handleCameraError(message: String, exception: Throwable? = null) {
        Log.e(TAG, message, exception)

        // Enhanced error handling with better user feedback
        val userMessage = when {
            message.contains("initialization") -> "Camera system initialization failed. Please restart the app."
            message.contains("binding") -> "Camera $cameraIndex is not working. Trying another camera..."
            message.contains("unavailable") -> "Camera $cameraIndex is not available. Please check device cameras."
            else -> "Camera error: $message"
        }

        Toast.makeText(this, userMessage, Toast.LENGTH_LONG).show()

        // Enhanced recovery strategies
        when {
            message.contains("binding") -> {
                // Try fallback to different camera
                if (cameraIndex > 0) {
                    Log.i(TAG, "Attempting fallback to camera 0")
                    cameraIndex = 0
                    startCameraWithEngine()
                } else {
                    Log.e(TAG, "No working cameras found, returning to selection")
                    finish()
                }
            }
            message.contains("initialization") -> {
                Log.e(TAG, "Camera initialization failed, finishing activity")
                finish()
            }
            else -> {
                Log.e(TAG, "Unhandled camera error, finishing activity")
                finish()
            }
        }
    }

    private fun initializeCamera2Controller() {
        try {
            val helper = com.customcamera.app.camera2.ManualControlHelper(this)
            val success = helper.initializeForCamera(cameraIndex.toString())

            if (success) {
                val capabilities = helper.getManualControlCapabilities()
                Log.i(TAG, "Camera2 manual control capabilities: $capabilities")

                // Store for use in manual controls
                // In production, this would enable real Camera2 API usage
                Toast.makeText(this, "Manual controls: ${if (helper.isManualControlSupported()) "Available" else "Limited"}", Toast.LENGTH_SHORT).show()
            } else {
                Log.w(TAG, "Camera2 controller initialization failed")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Camera2 controller", e)
        }
    }

    private fun setupPinchToZoom() {
        try {
            // Create scale gesture detector for pinch-to-zoom
            scaleGestureDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    val scaleFactor = detector.scaleFactor
                    Log.d(TAG, "onScale called: scaleFactor=$scaleFactor")

                    // Get current camera for video recording
                    val camera = cameraEngine.getCurrentCamera()

                    if (camera != null) {
                        Log.d(TAG, "Camera available, processing pinch gesture")
                        val zoomApplied = zoomController?.processPinchGesture(scaleFactor, camera) ?: false
                        Log.d(TAG, "Zoom applied: $zoomApplied")
                        if (zoomApplied) {
                            updateZoomIndicator()
                        }
                        return true
                    } else {
                        Log.w(TAG, "Camera not available for zoom")
                    }
                    return false
                }

                override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                    Log.d(TAG, "onScaleBegin: Pinch gesture started")
                    showZoomIndicator()
                    return true
                }

                override fun onScaleEnd(detector: ScaleGestureDetector) {
                    Log.d(TAG, "onScaleEnd: Pinch gesture ended")
                    hideZoomIndicatorAfterDelay()
                }
            })

            // Add touch listener to preview view for pinch gestures
            binding.previewView.setOnTouchListener { _, event ->
                // Let ScaleGestureDetector process the event first
                val scaleHandled = scaleGestureDetector?.onTouchEvent(event) == true

                if (event.pointerCount > 1) {
                    Log.v(TAG, "Multi-touch event detected: pointers=${event.pointerCount}, scaleHandled=$scaleHandled")
                }

                // If scale gesture consumed the event, don't process taps
                if (scaleHandled) {
                    Log.d(TAG, "Scale gesture handled, consuming event")
                    return@setOnTouchListener true
                }

                // Only process tap gestures for single-touch events (not during pinch)
                if (event.pointerCount > 1) {
                    return@setOnTouchListener false
                }

                // Handle tap gestures (existing functionality)
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastTapTime < 300) {
                            tapCount++
                            Log.d(TAG, "Tap count: $tapCount")
                            when (tapCount) {
                                2 -> {
                                    // Double tap - toggle grid
                                    toggleGrid()
                                    tapCount = 0
                                }
                                3 -> {
                                    // Triple tap - toggle barcode
                                    toggleBarcodeScanning()
                                    tapCount = 0
                                }
                                4 -> {
                                    // Quadruple tap - toggle crop
                                    toggleCrop()
                                    tapCount = 0
                                }
                                5 -> {
                                    // Five tap - toggle smart scene
                                    toggleSmartSceneDetection()
                                    tapCount = 0
                                }
                                6 -> {
                                    // Six tap - toggle object detection
                                    toggleObjectDetection()
                                    tapCount = 0
                                }
                            }
                        } else {
                            // Single tap - perform tap-to-focus
                            tapCount = 0
                            handleTapToFocus(event.x, event.y)
                        }
                        lastTapTime = currentTime
                        true
                    }
                    else -> false
                }
            }

            Log.i(TAG, "Pinch-to-zoom setup complete")

        } catch (e: Exception) {
            Log.e(TAG, "Error setting up pinch-to-zoom", e)
        }
    }

    /**
     * Handle tap-to-focus gesture
     * Triggers camera focus and metering at the tapped point
     */
    private fun handleTapToFocus(x: Float, y: Float) {
        lifecycleScope.launch {
            try {
                val camera = cameraEngine.getCurrentCamera()
                if (camera == null) {
                    Log.w(TAG, "Tap-to-focus: Camera not available")
                    return@launch
                }

                // Create metering point factory based on preview view dimensions
                val factory = SurfaceOrientedMeteringPointFactory(
                    binding.previewView.width.toFloat(),
                    binding.previewView.height.toFloat()
                )

                // Create metering point at tap coordinates
                val point = factory.createPoint(x, y)

                // Build focus and metering action
                val action = FocusMeteringAction.Builder(point)
                    .addPoint(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
                    .setAutoCancelDuration(5, java.util.concurrent.TimeUnit.SECONDS)
                    .build()

                Log.d(TAG, "Tap-to-focus at ($x, $y)")

                // Haptic feedback for tap
                hapticManager.lightTap()

                // Start focus and metering
                val result = camera.cameraControl.startFocusAndMetering(action)
                result.addListener({
                    try {
                        val focusResult = result.get()
                        if (focusResult.isFocusSuccessful) {
                            Log.i(TAG, "Tap-to-focus: SUCCESS")
                            // Success haptic feedback
                            hapticManager.success()
                        } else {
                            Log.w(TAG, "Tap-to-focus: FAILED")
                            // Light error feedback (not critical)
                            hapticManager.lightTap()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error getting focus result", e)
                    }
                }, ContextCompat.getMainExecutor(this@CameraActivityEngine))

            } catch (e: Exception) {
                Log.e(TAG, "Tap-to-focus failed", e)
                // Error haptic feedback
                hapticManager.error()
            }
        }
    }

    private fun showZoomIndicator() {
        try {
            if (zoomIndicator == null) {
                zoomIndicator = android.widget.TextView(this).apply {
                    text = "1.0x"
                    textSize = 18f
                    setTextColor(android.graphics.Color.WHITE)

                    // Create pill-shaped background with rounded corners
                    val pillBackground = android.graphics.drawable.GradientDrawable().apply {
                        shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                        cornerRadius = 50f // Rounded corners for pill shape
                        setColor(android.graphics.Color.argb(200, 0, 0, 0)) // Slightly more opaque for better readability
                    }
                    background = pillBackground

                    setPadding(32, 12, 32, 12) // More horizontal padding for pill shape
                    gravity = android.view.Gravity.CENTER
                }

                val rootView = binding.root
                val layoutParams = android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                    android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = android.view.Gravity.CENTER
                }
                rootView.addView(zoomIndicator, layoutParams)
            }

            zoomIndicator?.visibility = android.view.View.VISIBLE
            updateZoomIndicator()

        } catch (e: Exception) {
            Log.e(TAG, "Error showing zoom indicator", e)
        }
    }

    private fun updateZoomIndicator() {
        val zoomText = zoomController?.getZoomDisplayText() ?: "1.0x"
        zoomIndicator?.text = zoomText
    }

    private fun hideZoomIndicatorAfterDelay() {
        lifecycleScope.launch {
            kotlinx.coroutines.delay(2000) // Hide after 2 seconds
            zoomIndicator?.visibility = android.view.View.GONE
        }
    }

    private fun startHistogramUpdates() {
        lifecycleScope.launch {
            while (isHistogramVisible) {
                try {
                    val histogramPlugin = cameraEngine.getPlugin("Histogram") as? HistogramPlugin
                    val currentHistogram = histogramPlugin?.getCurrentHistogram()

                    if (currentHistogram != null && histogramView != null) {
                        histogramView!!.updateHistogram(currentHistogram)
                    }

                    kotlinx.coroutines.delay(200) // Update every 200ms

                } catch (e: Exception) {
                    Log.e(TAG, "Error updating histogram", e)
                    break
                }
            }
        }
    }

    private fun startBarcodeDetectionUpdates() {
        lifecycleScope.launch {
            while (isBarcodeScanningEnabled && barcodeOverlayView != null) {
                try {
                    val barcodePlugin = cameraEngine.getPlugin("Barcode") as? BarcodePlugin
                    val detectionStats = barcodePlugin?.getDetectionStats()

                    if (detectionStats != null) {
                        val currentDetections = detectionStats["currentDetections"] as? Int ?: 0
                        if (currentDetections > 0) {
                            Log.d(TAG, "Barcode detections available: $currentDetections")
                            // In a full implementation, you'd get the actual barcode data
                            // and update the overlay with real bounding boxes
                        }
                    }

                    kotlinx.coroutines.delay(500) // Check every 500ms

                } catch (e: Exception) {
                    Log.e(TAG, "Error updating barcode detection", e)
                    break
                }
            }
        }
    }

    private fun toggleFocusPeaking() {
        isFocusPeakingEnabled = !isFocusPeakingEnabled

        try {
            if (isFocusPeakingEnabled) {
                // Create focus peaking overlay
                if (focusPeakingOverlay == null) {
                    focusPeakingOverlay = com.customcamera.app.focus.FocusPeakingOverlay(this)

                    val rootView = binding.root
                    val layoutParams = android.widget.FrameLayout.LayoutParams(
                        android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                        android.widget.FrameLayout.LayoutParams.MATCH_PARENT
                    )
                    rootView.addView(focusPeakingOverlay, layoutParams)
                }

                focusPeakingOverlay?.setFocusPeakingEnabled(true)

                // Enable image analysis for focus peaking
                val config = CameraConfig(
                    cameraIndex = cameraIndex,
                    enablePreview = true,
                    enableImageCapture = true,
                    enableVideoCapture = true,
                    enableImageAnalysis = true
                )

                lifecycleScope.launch {
                    cameraEngine.bindCamera(config)
                }

                Toast.makeText(this, "Focus peaking enabled - highlights sharp areas in red", Toast.LENGTH_LONG).show()
                Log.i(TAG, "Focus peaking enabled")

            } else {
                focusPeakingOverlay?.setFocusPeakingEnabled(false)
                Toast.makeText(this, "Focus peaking disabled", Toast.LENGTH_SHORT).show()
                Log.i(TAG, "Focus peaking disabled")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error toggling focus peaking", e)
            Toast.makeText(this, "Focus peaking error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showZoomInfo() {
        try {
            val zoomCapabilities = zoomController?.getZoomCapabilities()
            val info = if (zoomCapabilities != null) {
                "Zoom: ${zoomCapabilities["currentZoomRatio"]}x / ${zoomCapabilities["maxZoomRatio"]}x"
            } else {
                "Zoom info not available"
            }

            Toast.makeText(this, info, Toast.LENGTH_SHORT).show()
            Log.i(TAG, "Zoom info: $zoomCapabilities")

        } catch (e: Exception) {
            Log.e(TAG, "Error showing zoom info", e)
        }
    }

    private fun showLoadingIndicator(message: String) {
        try {
            if (loadingIndicator == null) {
                loadingIndicator = android.widget.TextView(this).apply {
                    text = message
                    textSize = 16f
                    setTextColor(android.graphics.Color.WHITE)
                    setBackgroundColor(android.graphics.Color.argb(200, 0, 0, 0))
                    setPadding(24, 24, 24, 24)
                    gravity = android.view.Gravity.CENTER
                }

                val rootView = binding.root
                val layoutParams = android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                    android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = android.view.Gravity.CENTER
                }
                rootView.addView(loadingIndicator, layoutParams)
            }

            loadingIndicator?.text = message
            loadingIndicator?.visibility = android.view.View.VISIBLE

            Log.d(TAG, "Loading indicator shown: $message")

        } catch (e: Exception) {
            Log.e(TAG, "Error showing loading indicator", e)
        }
    }

    private fun hideLoadingIndicator() {
        loadingIndicator?.visibility = android.view.View.GONE
        Log.d(TAG, "Loading indicator hidden")
    }

    private fun capturePhotoMetadata(timestamp: String): com.customcamera.app.gallery.PhotoMetadata {
        return try {
            // Get current camera settings
            // Get current camera for video recording
            // val camera = cameraEngine.getCurrentCamera()
            val exposurePlugin = cameraEngine.getPlugin("ExposureControl") as? ExposureControlPlugin

            val exposureSettings = com.customcamera.app.gallery.ExposureSettings(
                iso = 100, // Default - Camera2 API needed for real ISO
                exposureTime = "1/60s", // Default - Camera2 API needed for real shutter
                exposureCompensation = exposurePlugin?.getCurrentEV() ?: 0f,
                aperture = 1.8f, // Typical smartphone aperture
                focalLength = 4.0f, // Typical smartphone focal length
                whiteBalance = "Auto", // Default - Camera2 API needed for real WB
                flashMode = if (isFlashOn) "On" else "Off",
                focusMode = "Auto" // Default - focus plugin integration needed
            )

            com.customcamera.app.gallery.PhotoMetadata(
                cameraId = cameraIndex.toString(),
                timestamp = Date(),
                location = null, // Location services not implemented
                exposureSettings = exposureSettings,
                imageSize = android.util.Size(1920, 1080), // Default - real capture size needed
                cropArea = null, // Crop area tracking to be implemented
                customData = mapOf(
                    "nightMode" to isNightModeEnabled,
                    "pipMode" to isPiPEnabled,
                    "gridEnabled" to gridOverlayPlugin!!.isGridVisible(),
                    "timestamp" to timestamp,
                    "app" to "CustomCamera"
                )
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error capturing metadata", e)
            // Return minimal metadata on error
            com.customcamera.app.gallery.PhotoMetadata(
                cameraId = cameraIndex.toString(),
                timestamp = Date(),
                exposureSettings = com.customcamera.app.gallery.ExposureSettings(
                    iso = 100, exposureTime = "1/60s", exposureCompensation = 0f,
                    aperture = 1.8f, focalLength = 4.0f, whiteBalance = "Auto",
                    flashMode = "Off", focusMode = "Auto"
                ),
                imageSize = android.util.Size(1920, 1080)
            )
        }
    }


    private fun initializePerformanceMonitor() {
        try {
            val cameraContext = com.customcamera.app.engine.CameraContext(
                context = this,
                cameraProvider = androidx.camera.lifecycle.ProcessCameraProvider.getInstance(this).get(),
                debugLogger = com.customcamera.app.engine.DebugLogger(),
                settingsManager = com.customcamera.app.engine.SettingsManager.getInstance(this)
            )

            performanceMonitor = com.customcamera.app.monitoring.PerformanceMonitor(this, cameraContext)
            performanceMonitor!!.startFPSMonitoring()

            Log.i(TAG, "Performance monitor initialized")

        } catch (e: Exception) {
            Log.e(TAG, "Error initializing performance monitor", e)
            performanceMonitor = null
        }
    }

    // Animation methods
    private fun animateCaptureButton() {
        binding.captureButton.animate()
            .scaleX(0.8f)
            .scaleY(0.8f)
            .setDuration(100)
            .withEndAction {
                binding.captureButton.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(100)
            }
    }

    private fun animateSwitchButton() {
        binding.switchCameraButton.animate()
            .rotationY(180f)
            .setDuration(300)
    }

    private fun animateFlashButton() {
        binding.flashButton.animate()
            .scaleX(1.2f)
            .scaleY(1.2f)
            .setDuration(150)
            .withEndAction {
                binding.flashButton.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(150)
            }
    }

    override fun onResume() {
        super.onResume()

        // Ensure fullscreen persists after resume
        setupFullscreen()

        // Check if camera selection changed in settings
        if (::cameraEngine.isInitialized && hasCameraPermission()) {
            val settingsManager = SettingsManager.getInstance(this)
            val settingsDefaultCamera = settingsManager.defaultCameraIndex.value

            if (settingsDefaultCamera != cameraIndex) {
                Log.i(TAG, "Camera selection changed in settings: $cameraIndex → $settingsDefaultCamera")

                // Check if in concurrent camera mode (PiP active)
                val currentMode = cameraEngine.getCurrentMode()
                if (currentMode is com.customcamera.app.engine.CameraMode.Concurrent) {
                    Log.w(TAG, "⚠️ Cannot switch camera while in PiP mode")
                    Log.w(TAG, "   Disable PiP first, then change camera in settings")
                    // Don't update cameraIndex - keep current camera until PiP is disabled
                    Toast.makeText(
                        this,
                        "Disable PiP mode before switching cameras",
                        Toast.LENGTH_LONG
                    ).apply {
                        setGravity(android.view.Gravity.TOP or android.view.Gravity.CENTER_HORIZONTAL, 0, 200)
                    }.show()
                    return
                }

                // TRUE ROOT CAUSE FIX: Don't switch camera if it's already in the process of opening
                // This prevents rapid sequential camera binds that cause cameras to close immediately
                val currentCameraState = cameraEngine.getCurrentCameraState()
                if (currentCameraState != null &&
                    (currentCameraState == androidx.camera.core.CameraState.Type.OPENING ||
                     currentCameraState == androidx.camera.core.CameraState.Type.PENDING_OPEN)) {
                    Log.w(TAG, "⚠️ Camera is currently opening (state=$currentCameraState), deferring switch to $settingsDefaultCamera")
                    Log.w(TAG, "   This prevents premature camera closure. Switch will occur on next resume.")
                    // Update cameraIndex so next resume won't trigger another switch attempt
                    cameraIndex = settingsDefaultCamera
                    return
                }

                cameraIndex = settingsDefaultCamera

                // Reinitialize camera with new index
                lifecycleScope.launch {
                    try {
                        val switchResult = cameraEngine.switchCamera(cameraIndex)
                        if (switchResult.isSuccess) {
                            // Reconfigure preview after camera switch
                            val preview = cameraEngine.getPreview()
                            preview?.setSurfaceProvider(binding.previewView.surfaceProvider)

                            // Update flash button state for new camera
                            updateFlashButton()

                            Toast.makeText(this@CameraActivityEngine, "Camera switched", Toast.LENGTH_SHORT).show()
                            Log.i(TAG, "✅ Camera switched to index: $cameraIndex")
                        } else {
                            Log.e(TAG, "Failed to switch camera: ${switchResult.exceptionOrNull()?.message}")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error switching camera on resume", e)
                    }
                }
            }
        }

        // Refresh plugin overlays when returning from settings to reflect any changes
        try {
            if (::cameraEngine.isInitialized && ::binding.isInitialized) {
                lifecycleScope.launch {
                    binding.pluginOverlayContainer.removeAllViews()
                    setupPluginUIOverlays()
                }
                Log.i(TAG, "Plugin overlays refreshed on resume")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing overlays on resume", e)
        }

        // Update plugin states when returning from settings
        updatePluginStatesFromSettings()
    }

    override fun onDestroy() {
        Log.i(TAG, "🧹 Starting cleanup in onDestroy()...")

        // CRITICAL: Cleanup CameraEngine FIRST to unbind all use cases
        // This breaks Preview -> SurfaceProvider -> PreviewView -> Activity reference chain
        cameraEngine.cleanup()
        Log.i(TAG, "✅ CameraEngine cleanup complete")

        // Remove dynamically added views to prevent memory leaks
        barcodeOverlayView?.let {
            binding.root.removeView(it)
            barcodeOverlayView = null
        }

        focusPeakingOverlay?.let {
            binding.root.removeView(it)
            focusPeakingOverlay = null
        }

        histogramView?.let {
            binding.root.removeView(it)
            histogramView = null
        }

        pipOverlayView?.let {
            binding.root.removeView(it)
            pipOverlayView = null
        }

        // Cleanup controllers
        camera2ISOController = null
        zoomController = null
        shutterSpeedController = null
        focusDistanceController = null
        camera2Controller = null

        // Stop performance monitoring to prevent coroutine leak
        performanceMonitor?.stopFPSMonitoring()
        performanceMonitor = null

        super.onDestroy()
        Log.i(TAG, "✅ Activity cleanup complete")
    }

    /**
     * Setup plugin UI overlays by creating their views and adding to container
     */
    private fun setupPluginUIOverlays() {
        try {
            val cameraContext = CameraContext(
                context = this,
                cameraProvider = cameraEngine.getProvider() ?: return,
                debugLogger = DebugLogger(),
                settingsManager = SettingsManager.getInstance(this),
                cameraEngine = cameraEngine
            )

            // Get the plugin overlay container
            val overlayContainer = binding.pluginOverlayContainer

            // Create and add grid overlay UI (with null check)
            if (gridOverlayPlugin != null) {
                val gridView: View? = gridOverlayPlugin!!.createUIView(cameraContext)
                if (gridView != null) {
                    // Remove from parent first if already attached
                    (gridView.parent as? android.view.ViewGroup)?.removeView(gridView)
                    overlayContainer.addView(gridView)
                    Log.i(TAG, "Added grid overlay to UI container")
                }
            } else {
                Log.w(TAG, "⚠️ Grid overlay plugin not initialized, skipping UI setup")
            }

            // Create and add crop overlay UI (with null check)
            if (cropPlugin != null) {
                val cropView: View? = cropPlugin!!.createUIView(cameraContext)
                if (cropView != null) {
                    // Remove from parent first if already attached
                    (cropView.parent as? android.view.ViewGroup)?.removeView(cropView)
                    overlayContainer.addView(cropView)
                    Log.i(TAG, "Added crop overlay to UI container")
                }
            } else {
                Log.w(TAG, "⚠️ Crop plugin not initialized, skipping UI setup")
            }

            // Create and add video controls overlay UI (with null check)
            if (advancedVideoRecordingPlugin != null) {
                val videoView: View? = advancedVideoRecordingPlugin!!.createUIView(cameraContext)
                if (videoView != null) {
                    // Remove from parent first if already attached
                    (videoView.parent as? android.view.ViewGroup)?.removeView(videoView)
                    overlayContainer.addView(videoView)
                    Log.i(TAG, "Added video controls overlay to UI container")
                }
            } else {
                Log.w(TAG, "⚠️ Video recording plugin not initialized, skipping UI setup")
            }

            // Barcode is ProcessingPlugin, not UIPlugin - doesn't have visual overlay
            // The barcode scanning results are shown via Toast messages

            Log.i(TAG, "✅ Plugin UI overlays setup complete")

        } catch (e: Exception) {
            Log.e(TAG, "Error setting up plugin UI overlays", e)
            Toast.makeText(this, "Plugin UI setup error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupGridOverlay() {
        // Legacy function - replaced by setupPluginUIOverlays()
        // Keeping for compatibility but no longer used
    }

    private fun updatePluginStatesFromSettings() {
        try {
            val settingsManager = com.customcamera.app.engine.SettingsManager.getInstance(this)

            // Update grid overlay visibility based on setting (with null check)
            if (gridOverlayPlugin != null) {
                val gridEnabled = settingsManager.isPluginEnabled("GridOverlay")
                if (gridEnabled != gridOverlayPlugin!!.isGridVisible()) {
                    if (gridEnabled) {
                        gridOverlayPlugin!!.showGrid()
                    } else {
                        gridOverlayPlugin!!.hideGrid()
                    }
                    Log.i(TAG, "Grid overlay updated from settings: $gridEnabled")
                }
            } else {
                Log.w(TAG, "⚠️ Grid overlay plugin not initialized, skipping settings update")
            }

            // Update barcode scanning state from settings
            val barcodeEnabled = settingsManager.isPluginEnabled("Barcode")
            val barcodePlugin = cameraEngine.getPlugin("Barcode") as? BarcodePlugin
            barcodePlugin?.setScanning(barcodeEnabled)

            // Update other plugin states as needed
            Log.d(TAG, "Plugin states updated from settings")

        } catch (e: Exception) {
            Log.e(TAG, "Error updating plugin states from settings", e)
        }
    }



    private fun setupBarcodeOverlay() {
        try {
            if (barcodeOverlayView == null) {
                barcodeOverlayView = com.customcamera.app.barcode.BarcodeOverlayView(this)

                // Add barcode overlay on top of preview
                val rootView = binding.root
                val layoutParams = android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT
                )
                rootView.addView(barcodeOverlayView, layoutParams)

                // Connect overlay to barcode plugin
                val barcodePlugin = cameraEngine.getPlugin("Barcode") as? BarcodePlugin
                barcodePlugin?.setBarcodeOverlay(barcodeOverlayView!!)

                Log.i(TAG, "Barcode overlay added to camera UI")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up barcode overlay", e)
        }
    }

    private fun clearBarcodeOverlay() {
        try {
            barcodeOverlayView?.let { overlay ->
                val rootView = binding.root
                rootView.removeView(overlay)
                barcodeOverlayView = null
                Log.i(TAG, "Barcode overlay removed from camera UI")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing barcode overlay", e)
        }
    }

    private fun setupDualCameraPiP() {
        try {
            // Set up main preview view for PiP plugin
            dualCameraPiPPlugin!!.setupMainPreview(binding.previewView)

            // Check if PiP should be enabled from settings
            val settingsManager = com.customcamera.app.engine.SettingsManager.getInstance(this)
            val pipEnabled = settingsManager.isPluginEnabled("DualCameraPiP")

            if (pipEnabled) {
                dualCameraPiPPlugin!!.setPiPEnabled(true)
            }

            // Set up gesture detection for PiP toggle
            setupPiPGestureDetection()

            Log.i(TAG, "Dual camera PiP system set up successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up dual camera PiP", e)
        }
    }

    private fun setupPiPGestureDetection() {
        // IMPORTANT: This function is called AFTER setupPinchToZoom()
        // We must NOT overwrite the existing touch listener, but enhance it instead
        // The pinch-to-zoom touch listener already handles both scale and tap gestures
        // So we don't need to set a new touch listener here - it's already handled in setupPinchToZoom()

        Log.i(TAG, "PiP gesture detection integrated with existing pinch-to-zoom handler")

        // Note: setupPinchToZoom() at line 1465 already sets up the touch listener
        // which handles both pinch gestures (zoom) and tap gestures (grid, barcode, etc.)
    }

    private fun toggleDualCameraPiP() {
        try {
            // Check if plugin is initialized
            if (dualCameraPiPPlugin == null) {
                // Haptic feedback for error
                hapticManager.error()

                Log.e(TAG, "PiP plugin not initialized")
                com.customcamera.app.presentation.EnhancedToast.error(this, "PiP not available")
                return
            }

            // Validate camera count before enabling
            val cameraManager = getSystemService(android.hardware.camera2.CameraManager::class.java)
            val cameraCount = cameraManager?.cameraIdList?.size ?: 0

            if (cameraCount < 2 && !dualCameraPiPPlugin!!.isPiPEnabled.value) {
                // Haptic feedback for unavailable action
                hapticManager.error()

                Log.w(TAG, "PiP requires at least 2 cameras, found: $cameraCount")
                com.customcamera.app.presentation.EnhancedToast.warning(
                    this,
                    "PiP requires at least 2 cameras (found $cameraCount)",
                    Toast.LENGTH_LONG
                )
                return
            }

            // Toggle PiP state
            val wasEnabled = dualCameraPiPPlugin!!.togglePiP()

            // Haptic feedback for successful toggle
            hapticManager.success()

            if (wasEnabled) {
                com.customcamera.app.presentation.EnhancedToast.featureActivated(this, "Dual Camera PiP")
            } else {
                com.customcamera.app.presentation.EnhancedToast.featureDeactivated(this, "Dual Camera PiP")
            }
            Log.i(TAG, "Dual camera PiP ${if (wasEnabled) "enabled" else "disabled"} (cameras available: $cameraCount)")

            // Update video button state based on camera mode
            // Video recording is disabled in concurrent mode due to UseCase limit
            updateVideoButtonState()

            // Reconnect preview if PiP was disabled (switched to single mode)
            if (!wasEnabled) {
                lifecycleScope.launch(Dispatchers.Main) {
                    val preview = cameraEngine.getPreview()
                    preview?.setSurfaceProvider(binding.previewView.surfaceProvider)
                    Log.i(TAG, "Preview reconnected after disabling PiP")
                }
            }

        } catch (e: Exception) {
            // Haptic feedback for error
            hapticManager.error()

            Log.e(TAG, "Error toggling dual camera PiP", e)
            com.customcamera.app.presentation.EnhancedToast.error(this, "PiP toggle failed: ${e.message}", Toast.LENGTH_LONG)
        }
    }

    /**
     * Update video button enabled state based on current camera mode.
     * Video recording is disabled in concurrent (PiP) mode due to UseCase limits.
     */
    private fun updateVideoButtonState() {
        val currentMode = cameraEngine.getCurrentMode()
        val isVideoAvailable = currentMode is com.customcamera.app.engine.CameraMode.Single

        binding.videoRecordButton.apply {
            isEnabled = isVideoAvailable
            alpha = if (isVideoAvailable) 1.0f else 0.5f
        }

        if (!isVideoAvailable) {
            Log.i(TAG, "Video recording disabled in concurrent camera mode (PiP active)")
        } else {
            Log.i(TAG, "Video recording enabled in single camera mode")
        }
    }

    private fun setupAdvancedVideoRecording() {
        try {
            // Check if video recording should be enabled from settings
            val settingsManager = com.customcamera.app.engine.SettingsManager.getInstance(this)
            // Check if video recording is enabled in settings
            // val videoEnabled = settingsManager.isPluginEnabled("AdvancedVideoRecording")

            // The video controls overlay is created automatically by the plugin's createUIView method
            // and will be added to the camera layout by the plugin system

            Log.i(TAG, "Advanced video recording system set up successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up advanced video recording", e)
        }
    }

    /**
     * Observe video recording state and manage grid overlay visibility
     */
    private fun setupVideoRecordingObserver() {
        val videoPlugin = advancedVideoRecordingPlugin
        val gridPlugin = gridOverlayPlugin

        if (videoPlugin == null) {
            Log.w(TAG, "Video recording plugin not available, skipping observer setup")
            return
        }

        lifecycleScope.launch {
            videoPlugin.isRecording.collect { isRecording ->
                if (isRecording) {
                    // Hide grid overlay when recording starts
                    gridPlugin?.hideGrid()
                    Log.i(TAG, "🎬 Recording started - grid overlay hidden")
                } else {
                    // Show grid overlay when recording stops (only if it was enabled in settings)
                    val gridEnabled = settingsManager.isPluginEnabled("GridOverlay")
                    if (gridEnabled) {
                        gridPlugin?.showGrid()
                        Log.i(TAG, "🎬 Recording stopped - grid overlay restored")
                    }
                }
            }
        }

        Log.i(TAG, "✅ Video recording observer set up for grid management")
    }

    private fun toggleVideoRecording() {
        try {
            val plugin = advancedVideoRecordingPlugin
            if (plugin == null) {
                // Haptic feedback for error
                hapticManager.error()

                Log.e(TAG, "Video recording plugin not available")
                Toast.makeText(this, "Video recording not available", Toast.LENGTH_SHORT).apply {
                    setGravity(android.view.Gravity.TOP or android.view.Gravity.CENTER_HORIZONTAL, 0, 200)
                }.show()
                return
            }

            Log.i(TAG, "Toggling video recording, current state: ${plugin.isRecording.value}")

            if (plugin.isRecording.value) {
                plugin.stopRecording()

                // Haptic feedback for stop recording
                hapticManager.mediumTap()

                Toast.makeText(this, "Video recording stopped", Toast.LENGTH_SHORT).apply {
                    setGravity(android.view.Gravity.TOP or android.view.Gravity.CENTER_HORIZONTAL, 0, 200)
                }.show()
            } else {
                // Check if in concurrent camera mode (PiP active)
                val currentMode = cameraEngine.getCurrentMode()
                if (currentMode is com.customcamera.app.engine.CameraMode.Concurrent) {
                    // Haptic feedback for unavailable action
                    hapticManager.error()

                    Log.w(TAG, "Video recording not available in concurrent camera mode")
                    Toast.makeText(
                        this,
                        "Video recording unavailable in PiP mode. Disable PiP to record video.",
                        Toast.LENGTH_LONG
                    ).apply {
                        setGravity(android.view.Gravity.TOP or android.view.Gravity.CENTER_HORIZONTAL, 0, 200)
                    }.show()
                    return
                }

                // Check if video capture is available from engine
                val videoCapture = cameraEngine.getVideoCapture()

                Log.i(TAG, "📋 Video Recording State:")
                Log.i(TAG, "   VideoCapture: ${if (videoCapture != null) "✅ Available" else "❌ NULL"}")
                Log.i(TAG, "   Plugin Enabled: ${plugin.isEnabled}")
                Log.i(TAG, "   Camera Mode: ${cameraEngine.getCurrentMode()}")

                if (videoCapture == null) {
                    // Haptic feedback for error
                    hapticManager.error()

                    Log.e(TAG, "❌ CRITICAL: VideoCapture is NULL - cannot record video")
                    Log.e(TAG, "   This indicates VideoCapture UseCase was not bound")
                    Log.e(TAG, "   Possible causes:")
                    Log.e(TAG, "     1. AdvancedVideoRecordingPlugin was disabled during camera init")
                    Log.e(TAG, "     2. Camera binding failed to create VideoCapture")
                    Log.e(TAG, "     3. enableVideoCapture was false in CameraConfig")
                    Log.e(TAG, "   Check UseCase binding logs above for details")

                    com.customcamera.app.presentation.EnhancedToast.error(
                        this,
                        "Video recording not available - VideoCapture not initialized"
                    )
                    return
                }

                lifecycleScope.launch {
                    val result = plugin.startRecording()
                    if (result.isSuccess) {
                        // Haptic feedback for successful start
                        hapticManager.success()

                        // Position toast at top to avoid blocking manual controls
                        Toast.makeText(this@CameraActivityEngine, "Video recording started", Toast.LENGTH_SHORT).apply {
                            setGravity(android.view.Gravity.TOP or android.view.Gravity.CENTER_HORIZONTAL, 0, 200)
                        }.show()
                    } else {
                        // Haptic feedback for error
                        hapticManager.error()

                        val error = result.exceptionOrNull()
                        Log.e(TAG, "Video recording failed", error)
                        Toast.makeText(this@CameraActivityEngine, "Failed to start recording: ${error?.message}", Toast.LENGTH_LONG).apply {
                            setGravity(android.view.Gravity.TOP or android.view.Gravity.CENTER_HORIZONTAL, 0, 200)
                        }.show()
                    }
                }
            }

        } catch (e: Exception) {
            // Haptic feedback for error
            hapticManager.error()

            Log.e(TAG, "Error toggling video recording", e)
            Toast.makeText(this, "Video toggle failed: ${e.message}", Toast.LENGTH_LONG).apply {
                setGravity(android.view.Gravity.TOP or android.view.Gravity.CENTER_HORIZONTAL, 0, 200)
            }.show()
        }
    }

    /**
     * Enhanced Button Animation and Feedback System
     */
    private fun setupEnhancedButton(button: View, isCaptureButton: Boolean = false, action: () -> Unit) {
        button.setOnClickListener {
            // Haptic feedback for button press
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)

            // Animate button press
            if (isCaptureButton) {
                animateCaptureButton(button)
            } else {
                animateStandardButton(button)
            }

            // Execute the action
            action()
        }
    }

    private fun animateStandardButton(button: View) {
        val scaleDown = ObjectAnimator.ofFloat(button, "scaleX", 1f, 0.95f).apply {
            duration = 75
        }
        val scaleDownY = ObjectAnimator.ofFloat(button, "scaleY", 1f, 0.95f).apply {
            duration = 75
        }
        val scaleUp = ObjectAnimator.ofFloat(button, "scaleX", 0.95f, 1f).apply {
            duration = 150
            startDelay = 75
        }
        val scaleUpY = ObjectAnimator.ofFloat(button, "scaleY", 0.95f, 1f).apply {
            duration = 150
            startDelay = 75
        }

        val alphaDown = ObjectAnimator.ofFloat(button, "alpha", 1f, 0.8f).apply {
            duration = 75
        }
        val alphaUp = ObjectAnimator.ofFloat(button, "alpha", 0.8f, 1f).apply {
            duration = 150
            startDelay = 75
        }

        AnimatorSet().apply {
            playTogether(scaleDown, scaleDownY, scaleUp, scaleUpY, alphaDown, alphaUp)
            start()
        }
    }

    private fun animateCaptureButton(button: View) {
        val scaleDown = ObjectAnimator.ofFloat(button, "scaleX", 1f, 0.9f).apply {
            duration = 100
        }
        val scaleDownY = ObjectAnimator.ofFloat(button, "scaleY", 1f, 0.9f).apply {
            duration = 100
        }
        val scaleUp = ObjectAnimator.ofFloat(button, "scaleX", 0.9f, 1.05f).apply {
            duration = 150
            startDelay = 100
        }
        val scaleUpY = ObjectAnimator.ofFloat(button, "scaleY", 0.9f, 1.05f).apply {
            duration = 150
            startDelay = 100
        }
        val scaleNormal = ObjectAnimator.ofFloat(button, "scaleX", 1.05f, 1f).apply {
            duration = 100
            startDelay = 250
        }
        val scaleNormalY = ObjectAnimator.ofFloat(button, "scaleY", 1.05f, 1f).apply {
            duration = 100
            startDelay = 250
        }

        val rotate = ObjectAnimator.ofFloat(button, "rotation", 0f, 5f, -5f, 0f).apply {
            duration = 350
        }

        AnimatorSet().apply {
            playTogether(scaleDown, scaleDownY, scaleUp, scaleUpY, scaleNormal, scaleNormalY, rotate)
            start()
        }
    }

    private fun animateButtonLongPress(button: View) {
        val pulse = ObjectAnimator.ofFloat(button, "scaleX", 1f, 1.1f, 1f).apply {
            duration = 300
        }
        val pulseY = ObjectAnimator.ofFloat(button, "scaleY", 1f, 1.1f, 1f).apply {
            duration = 300
        }
        val rotate = ObjectAnimator.ofFloat(button, "rotation", 0f, 10f, 0f).apply {
            duration = 300
        }

        AnimatorSet().apply {
            playTogether(pulse, pulseY, rotate)
            start()
        }
    }

    /**
     * Enhanced haptic feedback system
     */
    private fun performHapticFeedback(feedbackType: Int) {
        try {
            window.decorView.performHapticFeedback(
                feedbackType,
                HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
            )
        } catch (e: Exception) {
            Log.w(TAG, "Haptic feedback not available", e)
        }
    }

    // ========== AI-POWERED CAMERA FEATURES (Phase 8G) ==========

    /**
     * Toggle smart scene detection
     */
    private fun toggleSmartSceneDetection() {
        try {
            val smartScenePlugin = cameraEngine.getPlugin("SmartScene") as? SmartScenePlugin
            if (smartScenePlugin != null) {
                val currentState = smartScenePlugin.getCurrentSceneInfo()["detectionEnabled"] as? Boolean ?: false
                smartScenePlugin.setSceneDetectionEnabled(!currentState)

                val message = if (!currentState) "Smart scene detection enabled" else "Smart scene detection disabled"
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                Log.i(TAG, message)

                // Haptic feedback
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            } else {
                Toast.makeText(this, "Smart scene detection not available", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling smart scene detection", e)
            Toast.makeText(this, "Smart scene detection error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Toggle object detection
     */
    private fun toggleObjectDetection() {
        try {
            val objectDetectionPlugin = cameraEngine.getPlugin("ObjectDetection") as? ObjectDetectionPlugin
            if (objectDetectionPlugin != null) {
                val isEnabled = objectDetectionPlugin.toggleObjectDetection()

                val message = if (isEnabled) "Object detection enabled" else "Object detection disabled"
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                Log.i(TAG, message)

                // Haptic feedback
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            } else {
                Toast.makeText(this, "Object detection not available", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling object detection", e)
            Toast.makeText(this, "Object detection error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Toggle smart camera adjustments
     */
    private fun toggleSmartAdjustments() {
        try {
            val smartAdjustmentsPlugin = cameraEngine.getPlugin("SmartAdjustments") as? SmartAdjustmentsPlugin
            if (smartAdjustmentsPlugin != null) {
                val currentSettings = smartAdjustmentsPlugin.getCurrentSettings()
                val isEnabled = currentSettings["smartAdjustmentsEnabled"] as? Boolean ?: false
                smartAdjustmentsPlugin.setSmartAdjustmentsEnabled(!isEnabled)

                val message = if (!isEnabled) "Smart adjustments enabled" else "Smart adjustments disabled"
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                Log.i(TAG, message)

                // Haptic feedback
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            } else {
                Toast.makeText(this, "Smart adjustments not available", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling smart adjustments", e)
            Toast.makeText(this, "Smart adjustments error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Toggle motion detection and smart capture
     */
    private fun toggleMotionDetection() {
        try {
            val motionDetectionPlugin = cameraEngine.getPlugin("MotionDetection") as? MotionDetectionPlugin
            if (motionDetectionPlugin != null) {
                val motionStats = motionDetectionPlugin.getMotionStats()
                val isEnabled = motionStats["motionDetectionEnabled"] as? Boolean ?: false
                motionDetectionPlugin.setMotionDetectionEnabled(!isEnabled)

                val message = if (!isEnabled) "Motion detection enabled" else "Motion detection disabled"
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                Log.i(TAG, message)

                // Haptic feedback
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            } else {
                Toast.makeText(this, "Motion detection not available", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling motion detection", e)
            Toast.makeText(this, "Motion detection error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Show AI features status
     */
    private fun showAIFeaturesStatus() {
        try {
            val statusBuilder = StringBuilder("AI Features Status:\n\n")

            // Smart Scene Detection
            val smartScenePlugin = cameraEngine.getPlugin("SmartScene") as? SmartScenePlugin
            if (smartScenePlugin != null) {
                val sceneInfo = smartScenePlugin.getCurrentSceneInfo()
                val isEnabled = sceneInfo["detectionEnabled"] as? Boolean ?: false
                val currentScene = sceneInfo["currentScene"] as? String ?: "Unknown"
                val confidence = sceneInfo["confidence"] as? Float ?: 0.0f
                statusBuilder.append("🎬 Scene Detection: ${if (isEnabled) "ON" else "OFF"}\n")
                statusBuilder.append("   Current Scene: $currentScene (${String.format("%.1f", confidence * 100)}%)\n\n")
            }

            // Object Detection
            val objectDetectionPlugin = cameraEngine.getPlugin("ObjectDetection") as? ObjectDetectionPlugin
            if (objectDetectionPlugin != null) {
                val detectionStats = objectDetectionPlugin.getDetectionStats()
                val isEnabled = detectionStats["objectDetectionEnabled"] as? Boolean ?: false
                val currentDetections = detectionStats["currentDetections"] as? Int ?: 0
                statusBuilder.append("📦 Object Detection: ${if (isEnabled) "ON" else "OFF"}\n")
                statusBuilder.append("   Objects Detected: $currentDetections\n\n")
            }

            // Smart Adjustments
            val smartAdjustmentsPlugin = cameraEngine.getPlugin("SmartAdjustments") as? SmartAdjustmentsPlugin
            if (smartAdjustmentsPlugin != null) {
                val settings = smartAdjustmentsPlugin.getCurrentSettings()
                val isEnabled = settings["smartAdjustmentsEnabled"] as? Boolean ?: false
                val exposureLevel = settings["exposureLevel"] as? Float ?: 0.0f
                val whiteBalanceTemp = settings["whiteBalanceTemp"] as? Int ?: 5500
                statusBuilder.append("⚙️ Smart Adjustments: ${if (isEnabled) "ON" else "OFF"}\n")
                statusBuilder.append("   Exposure: ${String.format("%.1f", exposureLevel)} EV\n")
                statusBuilder.append("   White Balance: ${whiteBalanceTemp}K\n\n")
            }

            // Motion Detection
            val motionDetectionPlugin = cameraEngine.getPlugin("MotionDetection") as? MotionDetectionPlugin
            if (motionDetectionPlugin != null) {
                val motionStats = motionDetectionPlugin.getMotionStats()
                val isEnabled = motionStats["motionDetectionEnabled"] as? Boolean ?: false
                val motionLevel = motionStats["currentMotionLevel"] as? Float ?: 0.0f
                val isStill = motionStats["isSubjectStill"] as? Boolean ?: false
                statusBuilder.append("🏃 Motion Detection: ${if (isEnabled) "ON" else "OFF"}\n")
                statusBuilder.append("   Motion Level: ${String.format("%.2f", motionLevel)}\n")
                statusBuilder.append("   Subject: ${if (isStill) "Still" else "Moving"}\n")
            }

            // Show status dialog
            val statusText = statusBuilder.toString()
            android.app.AlertDialog.Builder(this)
                .setTitle("AI Features Status")
                .setMessage(statusText)
                .setPositiveButton("OK", null)
                .setNeutralButton("Toggle All") { _, _ ->
                    toggleAllAIFeatures()
                }
                .show()

        } catch (e: Exception) {
            Log.e(TAG, "Error showing AI features status", e)
            Toast.makeText(this, "AI features status error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Toggle all AI features at once
     */
    private fun toggleAllAIFeatures() {
        lifecycleScope.launch {
            try {
                // Check if any AI feature is enabled
                val smartScenePlugin = cameraEngine.getPlugin("SmartScene") as? SmartScenePlugin
                val objectDetectionPlugin = cameraEngine.getPlugin("ObjectDetection") as? ObjectDetectionPlugin
                val smartAdjustmentsPlugin = cameraEngine.getPlugin("SmartAdjustments") as? SmartAdjustmentsPlugin
                val motionDetectionPlugin = cameraEngine.getPlugin("MotionDetection") as? MotionDetectionPlugin

                val anyEnabled = listOf(
                    smartScenePlugin?.getCurrentSceneInfo()?.get("detectionEnabled") as? Boolean ?: false,
                    objectDetectionPlugin?.getDetectionStats()?.get("objectDetectionEnabled") as? Boolean ?: false,
                    smartAdjustmentsPlugin?.getCurrentSettings()?.get("smartAdjustmentsEnabled") as? Boolean ?: false,
                    motionDetectionPlugin?.getMotionStats()?.get("motionDetectionEnabled") as? Boolean ?: false
                ).any { it }

                val newState = !anyEnabled

                // Toggle all AI features to the new state
                smartScenePlugin?.setSceneDetectionEnabled(newState)
                objectDetectionPlugin?.setObjectDetectionEnabled(newState)
                smartAdjustmentsPlugin?.setSmartAdjustmentsEnabled(newState)
                motionDetectionPlugin?.setMotionDetectionEnabled(newState)

                val message = if (newState) "All AI features enabled" else "All AI features disabled"
                Toast.makeText(this@CameraActivityEngine, message, Toast.LENGTH_LONG).show()
                Log.i(TAG, message)

                // Enhanced haptic feedback for bulk operation
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)

            } catch (e: Exception) {
                Log.e(TAG, "Error toggling all AI features", e)
                Toast.makeText(this@CameraActivityEngine, "AI features toggle error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Apply scene-specific smart adjustments
     */
    private fun applySceneOptimizations() {
        try {
            val smartScenePlugin = cameraEngine.getPlugin("SmartScene") as? SmartScenePlugin
            val smartAdjustmentsPlugin = cameraEngine.getPlugin("SmartAdjustments") as? SmartAdjustmentsPlugin

            if (smartScenePlugin != null && smartAdjustmentsPlugin != null) {
                val sceneInfo = smartScenePlugin.getCurrentSceneInfo()
                val currentScene = sceneInfo["currentScene"] as? String ?: "UNKNOWN"

                // Apply scene-specific adjustments
                smartAdjustmentsPlugin.applySceneProfile(currentScene)

                Toast.makeText(this, "Applied optimizations for $currentScene scene", Toast.LENGTH_SHORT).show()
                Log.i(TAG, "Applied scene optimizations: $currentScene")

                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            } else {
                Toast.makeText(this, "Scene optimization not available", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error applying scene optimizations", e)
            Toast.makeText(this, "Scene optimization error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // NOTE: Professional manual controls are now handled by the plugin system
    // Access via the master plugin button → ProControlsPlugin or ManualControlsPluginSimple

    companion object {
        private const val TAG = "CameraActivityEngine"
    }
}