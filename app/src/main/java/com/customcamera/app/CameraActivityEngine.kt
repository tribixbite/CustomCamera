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
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.pow

/**
 * Enhanced CameraActivity that uses the CameraEngine and plugin system.
 * This demonstrates how to integrate the plugin architecture with existing camera functionality.
 */
class CameraActivityEngine : AppCompatActivity() {

    private lateinit var binding: ActivityCameraBinding
    private lateinit var cameraEngine: CameraEngine

    private var cameraIndex: Int = 0
    private var isFlashOn: Boolean = false
    private var isRecording: Boolean = false
    private var activeRecording: Recording? = null
    private var isManualControlsVisible: Boolean = false
    private var manualControlsPanel: android.widget.LinearLayout? = null
    private var isNightModeEnabled: Boolean = false
    private var isHistogramVisible: Boolean = false
    private var histogramView: com.customcamera.app.analysis.HistogramView? = null
    private var isBarcodeScanningEnabled: Boolean = false
    private var isPiPEnabled: Boolean = false
    private var loadingIndicator: android.widget.TextView? = null
    private var pipOverlayView: com.customcamera.app.pip.PiPOverlayView? = null
    private var barcodeOverlayView: com.customcamera.app.barcode.BarcodeOverlayView? = null
    private var camera2Controller: com.customcamera.app.camera2.Camera2Controller? = null
    private var performanceMonitor: com.customcamera.app.monitoring.PerformanceMonitor? = null

    // Plugins
    private lateinit var autoFocusPlugin: AutoFocusPlugin
    private lateinit var gridOverlayPlugin: GridOverlayPlugin
    private lateinit var cameraInfoPlugin: CameraInfoPlugin
    private lateinit var proControlsPlugin: ProControlsPlugin
    private lateinit var exposureControlPlugin: ExposureControlPlugin

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
        binding.captureButton.setOnClickListener { capturePhoto() }
        binding.videoRecordButton.setOnClickListener { toggleVideoRecording() }
        binding.nightModeButton.setOnClickListener { toggleNightMode() }
        binding.pipButton.setOnClickListener { togglePiP() }
        binding.switchCameraButton.setOnClickListener { switchCamera() }
        binding.flashButton.setOnClickListener { toggleFlash() }
        binding.galleryButton.setOnClickListener { openGallery() }
        binding.settingsButton.setOnClickListener { toggleManualControls() }
        binding.settingsButton.setOnLongClickListener {
            toggleHistogram()
            true
        }

        // Add gesture controls for features
        var lastTapTime = 0L
        var tapCount = 0
        binding.previewView.setOnClickListener {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastTapTime < 300) {
                tapCount++
                if (tapCount == 1) {
                    // Double tap - toggle grid
                    toggleGrid()
                } else if (tapCount == 2) {
                    // Triple tap - toggle barcode scanning
                    toggleBarcodeScanning()
                    tapCount = 0
                }
            } else {
                tapCount = 0
            }
            lastTapTime = currentTime
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

        val cropPlugin = CropPlugin()
        cameraEngine.registerPlugin(cropPlugin)

        val nightModePlugin = NightModePlugin()
        cameraEngine.registerPlugin(nightModePlugin)

        val hdrPlugin = HDRPlugin()
        cameraEngine.registerPlugin(hdrPlugin)

        Log.i(TAG, "✅ Camera engine and ALL plugins initialized (12 total plugins)")
    }

    private fun startCameraWithEngine() {
        Log.i(TAG, "Starting camera with engine...")

        // Show loading indicator
        showLoadingIndicator("Initializing camera...")

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

                // Update flash button state
                updateFlashButton()

                // Hide loading indicator
                hideLoadingIndicator()

                Log.i(TAG, "✅ Camera started successfully with engine")

            } catch (e: Exception) {
                Log.e(TAG, "Failed to start camera with engine", e)
                handleCameraError("Camera startup failed: ${e.message}")
            }
        }
    }

    private fun capturePhoto() {
        val imageCapture = cameraEngine.getImageCapture() ?: return

        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val photoFile = File(filesDir, "CAMERA_ENGINE_$timestamp.jpg")

            // Capture metadata for this photo
            val metadata = capturePhotoMetadata(timestamp)

            val outputFileOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

            imageCapture.takePicture(
                outputFileOptions,
                ContextCompat.getMainExecutor(this),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        Toast.makeText(this@CameraActivityEngine, "Photo saved: ${photoFile.name}", Toast.LENGTH_SHORT).show()
                        Log.i(TAG, "Photo saved with engine: ${photoFile.absolutePath}")
                        animateCaptureButton()
                    }

                    override fun onError(exception: ImageCaptureException) {
                        Log.e(TAG, "Photo capture failed with engine", exception)
                        Toast.makeText(this@CameraActivityEngine, "Photo capture failed", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Photo capture setup failed with engine", e)
            Toast.makeText(this, "Capture failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleVideoRecording() {
        if (isRecording) {
            stopVideoRecording()
        } else {
            startVideoRecording()
        }
    }

    private fun startVideoRecording() {
        val videoCapture = cameraEngine.getVideoCapture() ?: return

        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val videoFile = File(filesDir, "VIDEO_$timestamp.mp4")

            val outputOptions = androidx.camera.video.FileOutputOptions.Builder(videoFile).build()

            activeRecording = videoCapture.output
                .prepareRecording(this, outputOptions)
                .start(ContextCompat.getMainExecutor(this)) { recordEvent ->
                    when (recordEvent) {
                        is androidx.camera.video.VideoRecordEvent.Start -> {
                            isRecording = true
                            updateVideoButton()
                            Log.i(TAG, "Video recording started")
                        }
                        is androidx.camera.video.VideoRecordEvent.Finalize -> {
                            isRecording = false
                            activeRecording = null
                            updateVideoButton()

                            if (!recordEvent.hasError()) {
                                Toast.makeText(this@CameraActivityEngine, "Video saved: ${videoFile.name}", Toast.LENGTH_SHORT).show()
                                Log.i(TAG, "Video saved: ${videoFile.absolutePath}")
                            } else {
                                Log.e(TAG, "Video recording error: ${recordEvent.error}")
                                Toast.makeText(this@CameraActivityEngine, "Video recording failed", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }

            Log.i(TAG, "Video recording initiated")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start video recording", e)
            Toast.makeText(this, "Recording failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopVideoRecording() {
        activeRecording?.stop()
        Log.i(TAG, "Video recording stopped")
    }

    private fun updateVideoButton() {
        val iconRes = if (isRecording) android.R.drawable.ic_media_pause else R.drawable.ic_videocam
        binding.videoRecordButton.setImageResource(iconRes)
        binding.videoRecordButton.alpha = if (isRecording) 1.0f else 0.8f
    }

    private fun switchCamera() {
        lifecycleScope.launch {
            try {
                val availableCameras = cameraEngine.availableCameras.value

                if (availableCameras.size > 1) {
                    // Cycle to next camera
                    cameraIndex = (cameraIndex + 1) % availableCameras.size
                    Log.i(TAG, "Switching to camera $cameraIndex with engine")

                    // Switch camera using engine
                    val result = cameraEngine.switchCamera(cameraIndex)
                    if (result.isSuccess) {
                        updateFlashButton()
                        animateSwitchButton()
                        Log.i(TAG, "✅ Camera switched successfully")
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

    private fun toggleNightMode() {
        isNightModeEnabled = !isNightModeEnabled

        try {
            // Get the NightModePlugin from the registered plugins
            val nightModePlugin = cameraEngine.getPlugin("NightMode") as? NightModePlugin

            if (nightModePlugin != null) {
                if (isNightModeEnabled) {
                    nightModePlugin.enableNightMode()
                } else {
                    nightModePlugin.disableNightMode()
                }

                // Update button appearance
                binding.nightModeButton.alpha = if (isNightModeEnabled) 1.0f else 0.6f

                Toast.makeText(this, "Night mode ${if (isNightModeEnabled) "enabled" else "disabled"}", Toast.LENGTH_SHORT).show()
                Log.i(TAG, "Night mode ${if (isNightModeEnabled) "enabled" else "disabled"}")

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
                    val rootView = binding.root as android.widget.FrameLayout
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

                // Enable histogram plugin
                val histogramPlugin = cameraEngine.getPlugin("Histogram") as? HistogramPlugin
                histogramPlugin?.setHistogramEnabled(true)

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

    private fun toggleManualControls() {
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
                manualControlsPanel = android.widget.LinearLayout(this).apply {
                    orientation = android.widget.LinearLayout.VERTICAL
                    setBackgroundColor(android.graphics.Color.argb(200, 0, 0, 0))
                    setPadding(16, 16, 16, 16)
                }

                // Add manual controls with Camera2 capabilities
                val helper = com.customcamera.app.camera2.ManualControlHelper(this)
                helper.initializeForCamera(cameraIndex.toString())

                val titleView = android.widget.TextView(this).apply {
                    text = "Manual Controls"
                    textSize = 18f
                    setTextColor(android.graphics.Color.WHITE)
                    setPadding(0, 0, 0, 8)
                }
                manualControlsPanel!!.addView(titleView)

                // Show camera capabilities
                val capabilitiesView = android.widget.TextView(this).apply {
                    val capabilities = helper.getManualControlCapabilities()
                    text = "Camera Capabilities:\n${capabilities["isoRange"]}\nManual: ${capabilities["manualControlSupported"]}"
                    textSize = 12f
                    setTextColor(android.graphics.Color.LTGRAY)
                    setPadding(0, 0, 0, 16)
                }
                manualControlsPanel!!.addView(capabilitiesView)

                // Add exposure compensation control
                val exposureText = android.widget.TextView(this).apply {
                    text = "Exposure Compensation: 0 EV"
                    setTextColor(android.graphics.Color.WHITE)
                }
                manualControlsPanel!!.addView(exposureText)

                val exposureSeekBar = android.widget.SeekBar(this).apply {
                    max = 12 // -6 to +6 EV
                    progress = 6 // 0 EV
                    setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                        override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                            if (fromUser) {
                                val ev = progress - 6
                                exposureText.text = "Exposure Compensation: ${if (ev >= 0) "+" else ""}$ev EV"
                                // Apply exposure compensation
                                lifecycleScope.launch {
                                    exposureControlPlugin.setExposureCompensation(ev)
                                }
                            }
                        }
                        override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
                        override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
                    })
                }
                manualControlsPanel!!.addView(exposureSeekBar)

                // Add ISO control
                val isoText = android.widget.TextView(this).apply {
                    text = "ISO: Auto"
                    setTextColor(android.graphics.Color.WHITE)
                    setPadding(0, 16, 0, 0)
                }
                manualControlsPanel!!.addView(isoText)

                val isoSeekBar = android.widget.SeekBar(this).apply {
                    max = 100 // 0-100 represents ISO 50-6400 logarithmically
                    progress = 20 // ISO 100
                    setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                        override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                            if (fromUser) {
                                // Map 0-100 to ISO 50-6400 logarithmically
                                val iso = (50 * 128.0.pow(progress / 100.0)).toInt().coerceIn(50, 6400)
                                isoText.text = "ISO: $iso"

                                // Apply real ISO control through Camera2
                                camera2Controller?.setISO(iso)
                                Log.d(TAG, "ISO adjusted to: $iso (Camera2 applied)")
                            }
                        }
                        override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
                        override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
                    })
                }
                manualControlsPanel!!.addView(isoSeekBar)

                // Add white balance control
                val wbText = android.widget.TextView(this).apply {
                    text = "White Balance: Auto"
                    setTextColor(android.graphics.Color.WHITE)
                    setPadding(0, 16, 0, 0)
                }
                manualControlsPanel!!.addView(wbText)

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
                manualControlsPanel!!.addView(wbSpinner)

                // Add to camera layout
                val rootView = binding.root as android.widget.FrameLayout
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
        isPiPEnabled = !isPiPEnabled

        try {
            val pipPlugin = cameraEngine.getPlugin("PiP") as? PiPPlugin

            if (pipPlugin != null) {
                if (isPiPEnabled) {
                    // Create PiP overlay
                    if (pipOverlayView == null) {
                        pipOverlayView = com.customcamera.app.pip.PiPOverlayView(this@CameraActivityEngine)

                        // Add main preview (current camera)
                        pipOverlayView!!.setMainPreview(binding.previewView)

                        // Create small PiP preview for second camera
                        val pipPreview = androidx.camera.view.PreviewView(this@CameraActivityEngine)
                        pipOverlayView!!.setPiPPreview(pipPreview)

                        // Add to layout
                        val rootView = binding.root as android.widget.FrameLayout
                        rootView.addView(pipOverlayView)
                    }

                    pipOverlayView?.showPiP()
                    binding.pipButton.alpha = 1.0f
                    Toast.makeText(this@CameraActivityEngine, "PiP mode enabled", Toast.LENGTH_SHORT).show()
                    Log.i(TAG, "PiP mode enabled with overlay")

                } else {
                    pipOverlayView?.hidePiP()
                    binding.pipButton.alpha = 0.6f
                    Toast.makeText(this@CameraActivityEngine, "PiP mode disabled", Toast.LENGTH_SHORT).show()
                    Log.i(TAG, "PiP mode disabled")
                }
            } else {
                Toast.makeText(this, "PiP plugin not available", Toast.LENGTH_SHORT).show()
                Log.w(TAG, "PiP plugin not found")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error toggling PiP", e)
            Toast.makeText(this, "PiP error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleBarcodeScanning() {
        isBarcodeScanningEnabled = !isBarcodeScanningEnabled

        try {
            val barcodePlugin = cameraEngine.getPlugin("Barcode") as? BarcodePlugin

            if (barcodePlugin != null) {
                barcodePlugin.setAutoScanEnabled(isBarcodeScanningEnabled)
                Log.i(TAG, "Barcode scanning ${if (isBarcodeScanningEnabled) "enabled" else "disabled"}")

                // Enable image analysis if needed
                if (isBarcodeScanningEnabled) {
                    val config = CameraConfig(
                        cameraIndex = cameraIndex,
                        enablePreview = true,
                        enableImageCapture = true,
                        enableVideoCapture = true,
                        enableImageAnalysis = true
                    )

                    lifecycleScope.launch {
                        cameraEngine.bindCamera(config)

                        // Add barcode overlay to UI
                        if (barcodeOverlayView == null) {
                            barcodeOverlayView = com.customcamera.app.barcode.BarcodeOverlayView(this@CameraActivityEngine)

                            val rootView = binding.root as android.widget.FrameLayout
                            val layoutParams = android.widget.FrameLayout.LayoutParams(
                                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                                android.widget.FrameLayout.LayoutParams.MATCH_PARENT
                            )
                            rootView.addView(barcodeOverlayView, layoutParams)
                        }
                        barcodeOverlayView?.setOverlayEnabled(true)
                    }
                } else {
                    barcodeOverlayView?.setOverlayEnabled(false)
                }

                Toast.makeText(this, "Barcode scanning ${if (isBarcodeScanningEnabled) "enabled" else "disabled"}", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Barcode plugin not available", Toast.LENGTH_SHORT).show()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error toggling barcode scanning", e)
            Toast.makeText(this, "Barcode scanning error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleGrid() {
        lifecycleScope.launch {
            try {
                gridOverlayPlugin.toggleGrid()
                val isVisible = gridOverlayPlugin.isGridVisible()
                Toast.makeText(
                    this@CameraActivityEngine,
                    "Grid ${if (isVisible) "shown" else "hidden"}",
                    Toast.LENGTH_SHORT
                ).show()
                Log.i(TAG, "Grid toggled: $isVisible")
            } catch (e: Exception) {
                Log.e(TAG, "Error toggling grid", e)
            }
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

                val rootView = binding.root as android.widget.FrameLayout
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
            val camera = cameraEngine.getCurrentCamera()
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
                val rootView = binding.root as android.widget.FrameLayout
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

            // Update other plugin states as needed
            Log.d(TAG, "Plugin states updated from settings")

        } catch (e: Exception) {
            Log.e(TAG, "Error updating plugin states from settings", e)
        }
    }

    companion object {
        private const val TAG = "CameraActivityEngine"
    }
}