package com.customcamera.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
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

    private var cameraIndex: Int = 0
    @Volatile private var isFlashOn: Boolean = false
    @Volatile private var isRecording: Boolean = false
    private var activeRecording: Recording? = null
    @Volatile private var isManualControlsVisible: Boolean = false
    private var manualControlsPanel: android.widget.LinearLayout? = null
    @Volatile private var isNightModeEnabled: Boolean = false
    @Volatile private var isHistogramVisible: Boolean = false
    private var histogramView: com.customcamera.app.analysis.HistogramView? = null
    @Volatile private var isBarcodeScanningEnabled: Boolean = false
    @Volatile private var isPiPEnabled: Boolean = false
    private var loadingIndicator: android.widget.TextView? = null
    private var pipOverlayView: com.customcamera.app.pip.PiPOverlayView? = null
    private var camera2ISOController: com.customcamera.app.camera2.Camera2ISOController? = null
    private var zoomController: com.customcamera.app.camera2.ZoomController? = null
    private var scaleGestureDetector: ScaleGestureDetector? = null
    private var zoomIndicator: android.widget.TextView? = null
    private var shutterSpeedController: com.customcamera.app.camera2.ShutterSpeedController? = null
    private var focusDistanceController: com.customcamera.app.camera2.FocusDistanceController? = null
    private var focusPeakingOverlay: com.customcamera.app.focus.FocusPeakingOverlay? = null
    @Volatile private var isFocusPeakingEnabled: Boolean = false
    private var lastTapTime = 0L
    private var tapCount = 0
    private var barcodeOverlayView: com.customcamera.app.barcode.BarcodeOverlayView? = null
    private var camera2Controller: com.customcamera.app.camera2.Camera2Controller? = null
    private var performanceMonitor: com.customcamera.app.monitoring.PerformanceMonitor? = null

    // Plugins
    private lateinit var autoFocusPlugin: AutoFocusPlugin
    private lateinit var gridOverlayPlugin: GridOverlayPlugin
    private lateinit var cameraInfoPlugin: CameraInfoPlugin
    private lateinit var proControlsPlugin: ProControlsPlugin
    private lateinit var exposureControlPlugin: ExposureControlPlugin
    private lateinit var cropPlugin: CropPlugin
    private lateinit var dualCameraPiPPlugin: DualCameraPiPPlugin
    private lateinit var advancedVideoRecordingPlugin: AdvancedVideoRecordingPlugin

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
            Toast.makeText(this, getString(R.string.camera_permission_required), Toast.LENGTH_LONG).show()
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
        } catch (e: Exception) {
            Log.e(TAG, "💥 Layout inflation failed", e)
            Toast.makeText(this, "Camera interface failed to load", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // Set fullscreen flags
        setupFullscreen()

        // Get camera index from intent
        cameraIndex = intent.getIntExtra(CameraSelectionActivity.EXTRA_CAMERA_INDEX, 0)
        Log.i(TAG, "Using camera index: $cameraIndex")

        // Initialize UI enhancement components
        loadingIndicatorManager = LoadingIndicatorManager(this)

        // Initialize camera engine and plugins
        initializeCameraEngine()

        setupUI()

        if (hasCameraPermission()) {
            startCameraWithEngine()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun setupFullscreen() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            // Modern approach for Android 11+
            window.insetsController?.let { controller ->
                controller.hide(android.view.WindowInsets.Type.statusBars() or android.view.WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior = android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
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
        }
    }

    private fun setupUI() {
        // Enhanced button setup with animations and feedback
        setupEnhancedButton(binding.captureButton, true) { capturePhoto() }
        setupEnhancedButton(binding.videoRecordButton) { toggleVideoRecording() }
        setupEnhancedButton(binding.nightModeButton) { toggleNightMode() }
        setupEnhancedButton(binding.pipButton) { togglePiP() }
        setupEnhancedButton(binding.switchCameraButton) { switchCamera() }
        setupEnhancedButton(binding.flashButton) { toggleFlash() }
        setupEnhancedButton(binding.galleryButton) { openGallery() }
        setupEnhancedButton(binding.settingsButton) { toggleManualControlsPanel() }

        // Long press for full settings
        binding.settingsButton.setOnLongClickListener {
            // Enhanced feedback for long press
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            animateButtonLongPress(binding.settingsButton)
            openFullSettings()
            true
        }

        // Wire up new plugin control buttons with enhanced feedback
        setupEnhancedButton(binding.gridToggleButton) { toggleGrid() }
        setupEnhancedButton(binding.barcodeToggleButton) { toggleBarcodeScanning() }
        setupEnhancedButton(binding.manualControlsToggleButton) { toggleManualControlsPanel() }

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
                        // Six-tap - toggle object detection
                        toggleObjectDetection()
                        tapCount = 0 // Reset after six-tap
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

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun initializeCameraEngine() {
        Log.i(TAG, "Initializing camera engine and plugins...")

        // Create camera engine
        cameraEngine = CameraEngine(this, this)

        // Create and register plugins
        autoFocusPlugin = AutoFocusPlugin()
        cameraEngine.registerPlugin(autoFocusPlugin)

        gridOverlayPlugin = GridOverlayPlugin()
        cameraEngine.registerPlugin(gridOverlayPlugin)

        cameraInfoPlugin = CameraInfoPlugin()
        cameraEngine.registerPlugin(cameraInfoPlugin)

        proControlsPlugin = ProControlsPlugin()
        cameraEngine.registerPlugin(proControlsPlugin)

        exposureControlPlugin = ExposureControlPlugin()
        cameraEngine.registerPlugin(exposureControlPlugin)

        // Add new plugins from roadmap implementation
        val manualFocusPlugin = ManualFocusPlugin()
        cameraEngine.registerPlugin(manualFocusPlugin)

        val histogramPlugin = HistogramPlugin()
        cameraEngine.registerPlugin(histogramPlugin)

        val barcodePlugin = BarcodePlugin()
        cameraEngine.registerPlugin(barcodePlugin)

        val qrScannerPlugin = QRScannerPlugin()
        cameraEngine.registerPlugin(qrScannerPlugin)

        // Add Phase 8G AI-Powered Camera Features
        val smartScenePlugin = SmartScenePlugin()
        cameraEngine.registerPlugin(smartScenePlugin)

        val objectDetectionPlugin = ObjectDetectionPlugin()
        cameraEngine.registerPlugin(objectDetectionPlugin)

        val smartAdjustmentsPlugin = SmartAdjustmentsPlugin()
        cameraEngine.registerPlugin(smartAdjustmentsPlugin)

        val motionDetectionPlugin = MotionDetectionPlugin()
        cameraEngine.registerPlugin(motionDetectionPlugin)

        cropPlugin = CropPlugin()
        cameraEngine.registerPlugin(cropPlugin)

        val nightModePlugin = NightModePlugin()
        cameraEngine.registerPlugin(nightModePlugin)

        val hdrPlugin = HDRPlugin()
        cameraEngine.registerPlugin(hdrPlugin)

        dualCameraPiPPlugin = DualCameraPiPPlugin()
        cameraEngine.registerPlugin(dualCameraPiPPlugin)

        advancedVideoRecordingPlugin = AdvancedVideoRecordingPlugin()
        cameraEngine.registerPlugin(advancedVideoRecordingPlugin)

        // TODO: Add Phase 8H Professional Manual Controls (temporarily disabled due to API migration)
        // These plugins need to be migrated from old CameraManager API to new CameraContext API
        // isoPlugin = AdvancedISOControlPlugin()
        // cameraEngine.registerPlugin(isoPlugin)

        // shutterPlugin = ProfessionalShutterControlPlugin()
        // cameraEngine.registerPlugin(shutterPlugin)

        // aperturePlugin = ManualApertureControlPlugin()
        // cameraEngine.registerPlugin(aperturePlugin)

        // whiteBalancePlugin = AdvancedWhiteBalancePlugin()
        // cameraEngine.registerPlugin(whiteBalancePlugin)

        // focusPlugin = ManualFocusControlPlugin()
        // cameraEngine.registerPlugin(focusPlugin)

        // bracketingPlugin = ExposureBracketingPlugin()
        // cameraEngine.registerPlugin(bracketingPlugin)

        Log.i(TAG, "✅ Camera engine and plugins initialized (14 core plugins, 6 professional plugins temporarily disabled)")
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

                // Set up preview
                val preview = cameraEngine.getPreview()
                preview?.setSurfaceProvider(binding.previewView.surfaceProvider)

                // Configure autofocus plugin with preview
                autoFocusPlugin.setPreviewView(binding.previewView)

                // Initialize Camera2 controller for manual controls
                initializeCamera2Controller()

                // Initialize performance monitor
                initializePerformanceMonitor()

                // Add grid overlay to camera layout if enabled
                setupGridOverlay()

                // Set up dual camera PiP system
                setupDualCameraPiP()

                // Set up advanced video recording
                setupAdvancedVideoRecording()

                // Update flash button state
                updateFlashButton()

                // Hide enhanced loading indicator
                loadingIndicatorManager.hideLoading()

                Log.i(TAG, "✅ Camera started successfully with engine")

            } catch (e: Exception) {
                Log.e(TAG, "Failed to start camera with engine", e)
                loadingIndicatorManager.hideLoading()
                handleCameraError("Camera startup failed: ${e.message}")
            }
        }
    }

    private fun capturePhoto() {
        val imageCapture = cameraEngine.getImageCapture() ?: return

        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val photoFile = File(filesDir, "CAMERA_ENGINE_$timestamp.jpg")
            val outputFileOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

            // Check if night mode is enabled for long exposure capture
            val nightModePlugin = cameraEngine.getPlugin("NightMode") as? NightModePlugin

            if (nightModePlugin?.isNightModeActive() == true) {
                // Use long exposure capture for night mode
                captureLongExposurePhoto(outputFileOptions, photoFile, timestamp)
            } else {
                // Use regular photo capture
                captureRegularPhoto(outputFileOptions, photoFile, timestamp)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Photo capture setup failed with engine", e)
            Toast.makeText(this, "Capture failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun captureRegularPhoto(outputFileOptions: ImageCapture.OutputFileOptions, photoFile: File, timestamp: String) {
        val imageCapture = cameraEngine.getImageCapture() ?: return

        // Show photo capture loading
        loadingIndicatorManager.showLoading(
            binding.root as ViewGroup,
            LoadingIndicatorManager.LoadingType.PHOTO_CAPTURE,
            autoDismiss = 2000L
        )

        imageCapture.takePicture(
            outputFileOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    loadingIndicatorManager.hideLoading()
                    Toast.makeText(this@CameraActivityEngine, "Photo saved: ${photoFile.name}", Toast.LENGTH_SHORT).show()
                    Log.i(TAG, "Photo saved with engine: ${photoFile.absolutePath}")
                    animateCaptureButton()
                }

                override fun onError(exception: ImageCaptureException) {
                    loadingIndicatorManager.hideLoading()
                    Log.e(TAG, "Photo capture failed with engine", exception)
                    Toast.makeText(this@CameraActivityEngine, "Photo capture failed", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun captureLongExposurePhoto(outputFileOptions: ImageCapture.OutputFileOptions, photoFile: File, timestamp: String) {
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
                Toast.makeText(this@CameraActivityEngine, "Capturing long exposure (${exposureTime}ms)...", Toast.LENGTH_LONG).show()

                val success = nightModePlugin.captureLongExposurePhoto(outputFileOptions)

                loadingIndicatorManager.hideLoading()

                if (success) {
                    Toast.makeText(this@CameraActivityEngine, "Long exposure photo saved: ${photoFile.name}", Toast.LENGTH_LONG).show()
                    Log.i(TAG, "Long exposure photo saved: ${photoFile.absolutePath}")
                    animateCaptureButton()
                } else {
                    Log.e(TAG, "Long exposure photo capture failed")
                    Toast.makeText(this@CameraActivityEngine, "Long exposure capture failed", Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                loadingIndicatorManager.hideLoading()
                Log.e(TAG, "Long exposure capture error", e)
                Toast.makeText(this@CameraActivityEngine, "Long exposure error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }


    private fun switchCamera() {
        lifecycleScope.launch {
            try {
                // Stop video recording if active during camera switch
                if (advancedVideoRecordingPlugin.isRecording.value) {
                    Log.i(TAG, "Stopping video recording for camera switch")
                    advancedVideoRecordingPlugin.stopRecording()
                }

                val availableCameras = cameraEngine.availableCameras.value

                if (availableCameras.size > 1) {
                    // Cycle to next camera
                    cameraIndex = (cameraIndex + 1) % availableCameras.size
                    Log.i(TAG, "Switching to camera $cameraIndex with engine")

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

                        updateFlashButton()
                        animateSwitchButton()
                        Log.i(TAG, "✅ Camera switched successfully with video support")
                    } else {
                        Log.e(TAG, "❌ Camera switch failed: ${result.exceptionOrNull()?.message}")
                        Toast.makeText(this@CameraActivityEngine, "Failed to switch camera", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this@CameraActivityEngine, "Only one camera available", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error switching camera with engine", e)
                Toast.makeText(this@CameraActivityEngine, "Switch failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun toggleFlash() {
        val camera = cameraEngine.getCurrentCamera() ?: return

        if (camera.cameraInfo.hasFlashUnit()) {
            isFlashOn = !isFlashOn
            camera.cameraControl.enableTorch(isFlashOn)
            updateFlashButton()
            animateFlashButton()
        } else {
            Toast.makeText(this, "Flash not available", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(this, "Gallery error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openSettings() {
        try {
            val intent = Intent(this, SimpleSettingsActivity::class.java)
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open settings", e)
            Toast.makeText(this, "Settings error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openFullSettings() {
        try {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
            Log.i(TAG, "Opened full settings page")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open full settings", e)
            // Fallback to simple settings
            try {
                val fallbackIntent = Intent(this, SimpleSettingsActivity::class.java)
                startActivity(fallbackIntent)
                Log.i(TAG, "Opened fallback simple settings")
            } catch (e2: Exception) {
                Log.e(TAG, "Even fallback settings failed", e2)
                Toast.makeText(this, "Settings error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun toggleNightMode() {
        isNightModeEnabled = !isNightModeEnabled

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
                            Toast.makeText(this@CameraActivityEngine, message, Toast.LENGTH_LONG).show()

                        } else {
                            nightModePlugin.toggleNightMode()

                            // Remove night mode overlay if present
                            nightModePlugin.getNightModeOverlay()?.let { overlay ->
                                val rootView = binding.root
                                rootView.removeView(overlay)
                                Log.i(TAG, "Night mode overlay removed from UI")
                            }

                            Toast.makeText(this@CameraActivityEngine, "Night mode disabled", Toast.LENGTH_SHORT).show()
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

                        Log.i(TAG, "Night mode v2.0 ${if (isNightModeEnabled) "enabled" else "disabled"}")

                    } catch (e: Exception) {
                        Log.e(TAG, "Error in night mode async toggle", e)
                        Toast.makeText(this@CameraActivityEngine, "Night mode error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }

            } else {
                Toast.makeText(this, "Night mode plugin not available", Toast.LENGTH_SHORT).show()
                Log.w(TAG, "Night mode plugin not found")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error toggling night mode", e)
            Toast.makeText(this, "Night mode error: ${e.message}", Toast.LENGTH_SHORT).show()
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

    private fun toggleManualControlsPanel() {
        if (isManualControlsVisible) {
            hideManualControls()
        } else {
            showManualControls()
        }
    }

    private fun showManualControls() {
        try {
            if (manualControlsPanel == null) {
                // Create manual controls panel
                val panel = android.widget.LinearLayout(this).apply {
                    orientation = android.widget.LinearLayout.VERTICAL
                    setBackgroundColor(android.graphics.Color.argb(200, 0, 0, 0))
                    setPadding(16, 16, 16, 16)
                }
                manualControlsPanel = panel

                // Add manual controls with Camera2 capabilities
                val helper = com.customcamera.app.camera2.ManualControlHelper(this)
                helper.initializeForCamera(cameraIndex.toString())

                // Initialize Camera2 controllers
                camera2ISOController = com.customcamera.app.camera2.Camera2ISOController(this).apply {
                    initialize(cameraIndex.toString())
                }

                zoomController = com.customcamera.app.camera2.ZoomController(this).apply {
                    initialize(cameraIndex.toString())
                }

                shutterSpeedController = com.customcamera.app.camera2.ShutterSpeedController(this).apply {
                    initialize(cameraIndex.toString())
                }

                focusDistanceController = com.customcamera.app.camera2.FocusDistanceController(this).apply {
                    initialize(cameraIndex.toString())
                }

                // Setup pinch-to-zoom
                setupPinchToZoom()

                val titleView = android.widget.TextView(this).apply {
                    text = "Manual Controls"
                    textSize = 18f
                    setTextColor(android.graphics.Color.WHITE)
                    setPadding(0, 0, 0, 4)
                }
                panel.addView(titleView)

                // Add instruction for full settings
                val instructionView = android.widget.TextView(this).apply {
                    text = "💡 Long press settings button for full settings page"
                    textSize = 12f
                    setTextColor(android.graphics.Color.LTGRAY)
                    setPadding(0, 0, 0, 12)
                }
                panel.addView(instructionView)

                // Show camera capabilities
                val capabilitiesView = android.widget.TextView(this).apply {
                    val capabilities = helper.getManualControlCapabilities()
                    text = "Camera Capabilities:\n${capabilities["isoRange"]}\nManual: ${capabilities["manualControlSupported"]}"
                    textSize = 12f
                    setTextColor(android.graphics.Color.LTGRAY)
                    setPadding(0, 0, 0, 16)
                }
                panel.addView(capabilitiesView)

                // Add exposure compensation control with real camera connection
                val exposureText = android.widget.TextView(this).apply {
                    text = "Exposure: 0 EV (Real Camera Control)"
                    setTextColor(android.graphics.Color.WHITE)
                }
                panel.addView(exposureText)

                val exposureSeekBar = android.widget.SeekBar(this).apply {
                    max = 12 // -6 to +6 EV
                    progress = 6 // 0 EV
                    setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                        override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                            if (fromUser) {
                                val ev = progress - 6
                                exposureText.text = "Exposure: ${if (ev >= 0) "+" else ""}$ev EV (Camera2 API)"
                                // Apply real exposure compensation
                                lifecycleScope.launch {
                                    val result = exposureControlPlugin.setExposureCompensation(ev)
                                    Log.d(TAG, "Exposure compensation result: $result")
                                }
                            }
                        }
                        override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
                        override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
                    })
                }
                panel.addView(exposureSeekBar)

                // Add ISO control with real camera ranges
                val isoRange = helper.getISORange()
                val isoText = android.widget.TextView(this).apply {
                    text = if (isoRange != null) {
                        "ISO: Auto (${isoRange.lower}-${isoRange.upper})"
                    } else {
                        "ISO: Auto (Limited support)"
                    }
                    setTextColor(android.graphics.Color.WHITE)
                    setPadding(0, 16, 0, 0)
                }
                panel.addView(isoText)

                val isoSeekBar = android.widget.SeekBar(this).apply {
                    if (isoRange != null) {
                        max = isoRange.upper - isoRange.lower
                        progress = 100 - isoRange.lower // Default to ISO 100
                    } else {
                        max = 100
                        progress = 20
                    }

                    setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                        override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                            if (fromUser) {
                                val iso = if (isoRange != null) {
                                    (progress + isoRange.lower).coerceIn(isoRange.lower, isoRange.upper)
                                } else {
                                    (50 * 128.0.pow(progress / 100.0)).toInt().coerceIn(50, 6400)
                                }
                                isoText.text = "ISO: $iso ${if (isoRange != null) "(Hardware)" else "(Estimated)"}"

                                // Apply real ISO control through Camera2
                                camera2ISOController?.setISO(iso)
                                Log.d(TAG, "ISO adjusted to: $iso (Camera2 API called)")
                            }
                        }
                        override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
                        override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
                    })
                }
                panel.addView(isoSeekBar)

                // Add white balance control
                val wbText = android.widget.TextView(this).apply {
                    text = "White Balance: Auto"
                    setTextColor(android.graphics.Color.WHITE)
                    setPadding(0, 16, 0, 0)
                }
                panel.addView(wbText)

                val wbSpinner = android.widget.Spinner(this).apply {
                    val wbOptions = arrayOf("Auto", "Daylight", "Cloudy", "Tungsten", "Fluorescent", "Flash")
                    val adapter = android.widget.ArrayAdapter(
                        this@CameraActivityEngine,
                        android.R.layout.simple_spinner_item,
                        wbOptions
                    )
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    this.adapter = adapter

                    onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                        override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                            wbText.text = "White Balance: ${wbOptions[position]}"

                            // Apply real white balance through Camera2
                            val colorTemp = when (position) {
                                0 -> 5500 // Auto/Daylight
                                1 -> 5500 // Daylight
                                2 -> 6500 // Cloudy
                                3 -> 3200 // Tungsten
                                4 -> 4000 // Fluorescent
                                5 -> 5500 // Flash
                                else -> 5500
                            }
                            camera2Controller?.setColorTemperature(colorTemp)
                            Log.d(TAG, "White balance set to: ${wbOptions[position]} (${colorTemp}K Camera2 applied)")
                        }
                        override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
                    }
                }
                panel.addView(wbSpinner)

                // Add shutter speed control with real Camera2 capabilities
                val shutterText = android.widget.TextView(this).apply {
                    val shutterSettings = shutterSpeedController?.getCurrentShutterSpeedSettings()
                    text = "Shutter: ${shutterSettings?.get("currentDisplayText") ?: "1/60s"} (Camera2)"
                    setTextColor(android.graphics.Color.WHITE)
                    setPadding(0, 16, 0, 0)
                }
                panel.addView(shutterText)

                val shutterSpinner = android.widget.Spinner(this).apply {
                    val availableShutterSpeeds = shutterSpeedController?.getAvailableShutterSpeeds() ?: emptyList()
                    val shutterOptions = if (availableShutterSpeeds.isNotEmpty()) {
                        availableShutterSpeeds.map { it.first }.toTypedArray()
                    } else {
                        arrayOf("1/60s", "1/30s", "1/15s", "1/8s", "1/4s", "1/2s", "1s", "2s")
                    }

                    val adapter = android.widget.ArrayAdapter(
                        this@CameraActivityEngine,
                        android.R.layout.simple_spinner_item,
                        shutterOptions
                    )
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    this.adapter = adapter

                    onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                        override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                            val selectedShutter = shutterOptions[position]
                            shutterText.text = "Shutter: $selectedShutter (Camera2)"

                            // Apply real shutter speed through Camera2
                            shutterSpeedController?.setShutterSpeedByName(selectedShutter)
                            Log.d(TAG, "Shutter speed set to: $selectedShutter")
                        }
                        override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
                    }
                }
                panel.addView(shutterSpinner)

                // Add focus distance control with real Camera2 capabilities
                val focusText = android.widget.TextView(this).apply {
                    val focusSettings = focusDistanceController?.getCurrentFocusDistanceSettings()
                    text = "Focus: ${focusSettings?.get("currentDisplayText") ?: "Auto"} (Camera2)"
                    setTextColor(android.graphics.Color.WHITE)
                    setPadding(0, 16, 0, 0)
                }
                panel.addView(focusText)

                val focusSpinner = android.widget.Spinner(this).apply {
                    val availableFocusPresets = focusDistanceController?.getAvailableFocusPresets() ?: emptyList()
                    val focusOptions = if (availableFocusPresets.isNotEmpty()) {
                        availableFocusPresets.map { it.first }.toTypedArray()
                    } else {
                        arrayOf("Auto", "Infinity", "Landscape", "Portrait", "Close", "Macro")
                    }

                    val adapter = android.widget.ArrayAdapter(
                        this@CameraActivityEngine,
                        android.R.layout.simple_spinner_item,
                        focusOptions
                    )
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    this.adapter = adapter

                    onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                        override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                            val selectedFocus = focusOptions[position]
                            focusText.text = "Focus: $selectedFocus (Camera2)"

                            // Apply real focus distance through Camera2
                            focusDistanceController?.setFocusDistanceByPreset(selectedFocus)
                            Log.d(TAG, "Focus distance set to: $selectedFocus")
                        }
                        override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
                    }
                }
                panel.addView(focusSpinner)

                // Add hyperfocal distance calculator
                val hyperfocalText = android.widget.TextView(this).apply {
                    val hyperfocal = focusDistanceController?.calculateHyperfocalDistance(4.0f, 1.8f) ?: "Unknown"
                    text = "📏 $hyperfocal"
                    textSize = 12f
                    setTextColor(android.graphics.Color.LTGRAY)
                    setPadding(0, 8, 0, 0)
                }
                panel.addView(hyperfocalText)

                // Add to camera layout
                val rootView = binding.root
                val layoutParams = android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                    android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = android.view.Gravity.BOTTOM
                }
                rootView.addView(manualControlsPanel, layoutParams)
            }

            manualControlsPanel?.visibility = android.view.View.VISIBLE
            isManualControlsVisible = true

            Log.i(TAG, "Manual controls panel shown")

        } catch (e: Exception) {
            Log.e(TAG, "Error showing manual controls", e)
            Toast.makeText(this, "Manual controls error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun hideManualControls() {
        manualControlsPanel?.visibility = android.view.View.GONE
        isManualControlsVisible = false
        Log.i(TAG, "Manual controls panel hidden")
    }

    private fun showAdvancedControls() {
        lifecycleScope.launch {
            try {
                // Show current camera info and controls status
                val cameraInfo = cameraInfoPlugin.getCameraInfo()
                val exposureSettings = exposureControlPlugin.getCurrentSettings()
                val proControlsSettings = proControlsPlugin.getCurrentSettings()

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

                Toast.makeText(
                    this,
                    "Barcode ${if (isBarcodeScanningEnabled) "enabled" else "disabled"}",
                    Toast.LENGTH_SHORT
                ).show()

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
                }

                // Update settings
                val settingsManager = com.customcamera.app.engine.SettingsManager(this)
                settingsManager.setPluginEnabled("Barcode", isBarcodeScanningEnabled)

                Toast.makeText(this, "Barcode scanning ${if (isBarcodeScanningEnabled) "enabled" else "disabled"}", Toast.LENGTH_SHORT).show()
            } else {
                Log.w(TAG, "BarcodePlugin not found")
                Toast.makeText(this, "Barcode plugin not available", Toast.LENGTH_SHORT).show()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error toggling barcode scanning", e)
            Toast.makeText(this, "Barcode scanning error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleGrid() {
        try {
            if (!::gridOverlayPlugin.isInitialized) {
                Log.e(TAG, "Grid plugin not initialized")
                Toast.makeText(this, "Camera not ready", Toast.LENGTH_SHORT).show()
                return
            }

            gridOverlayPlugin.toggleGrid()
            val isVisible = gridOverlayPlugin.isGridVisible()

            // Force UI refresh by requesting overlay redraw
            binding.root.post {
                binding.root.invalidate()
            }

            Toast.makeText(
                this@CameraActivityEngine,
                "Grid ${if (isVisible) "shown" else "hidden"}",
                Toast.LENGTH_SHORT
            ).show()
            Log.i(TAG, "Grid toggled: $isVisible")
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling grid", e)
            Toast.makeText(this, "Grid toggle error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleCrop() {
        try {
            if (!::cropPlugin.isInitialized) {
                Log.e(TAG, "Crop plugin not initialized")
                Toast.makeText(this, "Camera not ready", Toast.LENGTH_SHORT).show()
                return
            }

            // Toggle crop mode
            if (cropPlugin.isEnabled) {
                cropPlugin.disableCrop()
                Toast.makeText(
                    this,
                    "Crop mode disabled",
                    Toast.LENGTH_SHORT
                ).show()
                Log.i(TAG, "Crop mode disabled")
            } else {
                cropPlugin.enableCrop()
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

    private suspend fun demonstrateExposureControl() {
        try {
            // Show current exposure
            val currentEV = exposureControlPlugin.getCurrentEV()
            Log.i(TAG, "Current exposure: ${currentEV}EV")

            // Perform exposure analysis
            val analysis = exposureControlPlugin.analyzeExposure()
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
            val currentIndex = exposureControlPlugin.getCurrentSettings()["currentExposureIndex"] as Int
            val testIndex = (currentIndex + 1).coerceIn(-2, 2) // Small adjustment

            exposureControlPlugin.setExposureCompensation(testIndex)
            Log.i(TAG, "Demonstrated exposure adjustment to index: $testIndex")

            // Reset back after a moment
            kotlinx.coroutines.delay(2000)
            exposureControlPlugin.setExposureCompensation(currentIndex)
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
                    // Get current camera for video recording
            val camera = cameraEngine.getCurrentCamera()

                    if (camera != null) {
                        val zoomApplied = zoomController?.processPinchGesture(scaleFactor, camera) ?: false
                        if (zoomApplied) {
                            updateZoomIndicator()
                        }
                        return true
                    }
                    return false
                }

                override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                    showZoomIndicator()
                    return true
                }

                override fun onScaleEnd(detector: ScaleGestureDetector) {
                    hideZoomIndicatorAfterDelay()
                }
            })

            // Add touch listener to preview view for pinch gestures
            binding.previewView.setOnTouchListener { _, event ->
                scaleGestureDetector?.onTouchEvent(event) ?: false

                // Also handle tap gestures (existing functionality)
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastTapTime < 300) {
                            tapCount++
                            when (tapCount) {
                                1 -> toggleGrid()
                                2 -> {
                                    toggleBarcodeScanning()
                                    tapCount = 0
                                }
                                3 -> {
                                    // Quadruple tap - show zoom info
                                    showZoomInfo()
                                    tapCount = 0
                                }
                                4 -> {
                                    // Five tap - toggle focus peaking
                                    toggleFocusPeaking()
                                    tapCount = 0
                                }
                            }
                        } else {
                            tapCount = 0
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

    private fun showZoomIndicator() {
        try {
            if (zoomIndicator == null) {
                zoomIndicator = android.widget.TextView(this).apply {
                    text = "1.0x"
                    textSize = 18f
                    setTextColor(android.graphics.Color.WHITE)
                    setBackgroundColor(android.graphics.Color.argb(180, 0, 0, 0))
                    setPadding(16, 8, 16, 8)
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
                    "gridEnabled" to gridOverlayPlugin.isGridVisible(),
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
                settingsManager = com.customcamera.app.engine.SettingsManager(this)
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
        // Update plugin states when returning from settings
        updatePluginStatesFromSettings()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "Cleaning up camera engine...")

        // Remove dynamically added views to prevent memory leaks
        barcodeOverlayView?.let {
            binding.root.removeView(it)
            barcodeOverlayView = null
        }

        manualControlsPanel?.let {
            binding.root.removeView(it)
            manualControlsPanel = null
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
        performanceMonitor = null

        cameraEngine.cleanup()
    }

    private fun setupGridOverlay() {
        try {
            val settingsManager = com.customcamera.app.engine.SettingsManager(this)
            val gridEnabled = settingsManager.isPluginEnabled("GridOverlay")

            if (gridEnabled) {
                // Create grid overlay view and add to camera layout
                val gridView = com.customcamera.app.plugins.GridOverlayView(this)

                // Add grid overlay on top of preview
                val rootView = binding.root
                val layoutParams = android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT
                )
                rootView.addView(gridView, layoutParams)

                Log.i(TAG, "Grid overlay added to camera UI")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error setting up grid overlay", e)
        }
    }

    private fun updatePluginStatesFromSettings() {
        try {
            val settingsManager = com.customcamera.app.engine.SettingsManager(this)

            // Update grid overlay visibility based on setting
            val gridEnabled = settingsManager.isPluginEnabled("GridOverlay")
            if (gridEnabled != gridOverlayPlugin.isGridVisible()) {
                if (gridEnabled) {
                    gridOverlayPlugin.showGrid()
                } else {
                    gridOverlayPlugin.hideGrid()
                }
                Log.i(TAG, "Grid overlay updated from settings: $gridEnabled")
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
            dualCameraPiPPlugin.setupMainPreview(binding.previewView)

            // Check if PiP should be enabled from settings
            val settingsManager = com.customcamera.app.engine.SettingsManager(this)
            val pipEnabled = settingsManager.isPluginEnabled("DualCameraPiP")

            if (pipEnabled) {
                dualCameraPiPPlugin.setPiPEnabled(true)
            }

            // Set up gesture detection for PiP toggle
            setupPiPGestureDetection()

            Log.i(TAG, "Dual camera PiP system set up successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up dual camera PiP", e)
        }
    }

    private fun setupPiPGestureDetection() {
        // Set up quadruple-tap gesture to toggle PiP (to avoid conflicts with existing gestures)
        val gestureDetector = android.view.GestureDetector(this, object : android.view.GestureDetector.SimpleOnGestureListener() {
            private var tapCount = 0
            private var lastTapTime = 0L
            private val multiTapTimeout = 500L // ms

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                val currentTime = System.currentTimeMillis()

                if (currentTime - lastTapTime > multiTapTimeout) {
                    tapCount = 1
                } else {
                    tapCount++
                }

                lastTapTime = currentTime

                // Check for quadruple tap
                if (tapCount == 4) {
                    toggleDualCameraPiP()
                    tapCount = 0
                    return true
                }

                // Check for quintuple tap
                if (tapCount == 5) {
                    toggleVideoRecording()
                    tapCount = 0
                    return true
                }

                return false
            }
        })

        binding.previewView.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            false // Allow other touch handling to continue
        }
    }

    private fun toggleDualCameraPiP() {
        try {
            val wasEnabled = dualCameraPiPPlugin.togglePiP()

            val message = if (wasEnabled) {
                "Dual camera PiP enabled"
            } else {
                "Dual camera PiP disabled"
            }

            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            Log.i(TAG, message)

        } catch (e: Exception) {
            Log.e(TAG, "Error toggling dual camera PiP", e)
            Toast.makeText(this, "PiP toggle failed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupAdvancedVideoRecording() {
        try {
            // Check if video recording should be enabled from settings
            val settingsManager = com.customcamera.app.engine.SettingsManager(this)
            // Check if video recording is enabled in settings
            // val videoEnabled = settingsManager.isPluginEnabled("AdvancedVideoRecording")

            // The video controls overlay is created automatically by the plugin's createUIView method
            // and will be added to the camera layout by the plugin system

            Log.i(TAG, "Advanced video recording system set up successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up advanced video recording", e)
        }
    }

    private fun toggleVideoRecording() {
        try {
            val plugin = advancedVideoRecordingPlugin
            Log.i(TAG, "Toggling video recording, current state: ${plugin.isRecording.value}")

            if (plugin.isRecording.value) {
                plugin.stopRecording()
                Toast.makeText(this, "Video recording stopped", Toast.LENGTH_SHORT).show()
            } else {
                // Check if video capture is available from engine
                val videoCapture = cameraEngine.getVideoCapture()
                Log.i(TAG, "Video capture from engine: $videoCapture")

                lifecycleScope.launch {
                    val result = plugin.startRecording()
                    if (result.isSuccess) {
                        Toast.makeText(this@CameraActivityEngine, "Video recording started", Toast.LENGTH_SHORT).show()
                    } else {
                        val error = result.exceptionOrNull()
                        Log.e(TAG, "Video recording failed", error)
                        Toast.makeText(this@CameraActivityEngine, "Failed to start recording: ${error?.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error toggling video recording", e)
            Toast.makeText(this, "Video toggle failed: ${e.message}", Toast.LENGTH_LONG).show()
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

    /**
     * Toggle professional manual controls dialog
     */
    private fun toggleManualControls() {
        try {
            // TODO: Re-enable professional controls after plugin API migration
            Toast.makeText(this, "Professional controls temporarily unavailable - under development", Toast.LENGTH_LONG).show()
            Log.i(TAG, "Professional controls requested but disabled (API migration in progress)")

            // Original code (temporarily disabled):
            /*
            val dialog = com.customcamera.app.ui.ProfessionalControlsDialog(
                this,
                isoPlugin,
                shutterPlugin,
                aperturePlugin,
                whiteBalancePlugin,
                focusPlugin,
                bracketingPlugin
            )
            dialog.show()
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            Toast.makeText(this, "Professional controls opened", Toast.LENGTH_SHORT).show()
            */

        } catch (e: Exception) {
            Log.e(TAG, "Error with manual controls", e)
            Toast.makeText(this, "Manual controls error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private const val TAG = "CameraActivityEngine"
    }
}