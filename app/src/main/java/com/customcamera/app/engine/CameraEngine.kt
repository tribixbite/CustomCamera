package com.customcamera.app.engine

import android.content.Context
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.FallbackStrategy
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.customcamera.app.engine.plugins.CameraPlugin
import com.customcamera.app.engine.plugins.PluginManager
import com.customcamera.app.plugins.RAWCapturePlugin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Central camera coordination engine that manages camera lifecycle,
 * plugin system, and provides a unified interface for camera operations.
 *
 * This engine serves as the main entry point for all camera functionality
 * and coordinates between CameraX APIs and the plugin system.
 */
class CameraEngine(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val pluginRegistry: com.customcamera.app.engine.plugins.PluginRegistry,
    private val settingsManager: SettingsManager
) {
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var currentCameraSelector: CameraSelector? = null
    private var apiMonitor: com.customcamera.app.debug.CameraAPIMonitor? = null

    // Camera mode tracking for single vs concurrent operation
    private var currentMode: CameraMode = CameraMode.Single
    private var singleCamera: Camera? = null
    private var concurrentCamera: ConcurrentCamera? = null // Concurrent camera for PiP mode

    // State preservation for concurrent mode
    private var videoWasEnabled = false
    private var analysisWasEnabled = false

    private val pluginManager = PluginManager(context)
    private val _isInitialized = MutableStateFlow(false)
    private val _currentCameraIndex = MutableStateFlow(0)
    private val _availableCameras = MutableStateFlow<List<CameraInfo>>(emptyList())

    // Public state flows for observing engine state
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()
    val currentCameraIndex: StateFlow<Int> = _currentCameraIndex.asStateFlow()
    val availableCameras: StateFlow<List<CameraInfo>> = _availableCameras.asStateFlow()

    // Use cases that can be managed by the engine
    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var imageAnalysis: ImageAnalysis? = null

    // Managed coroutine scope for camera operations (properly cancelled on cleanup)
    private val engineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    /**
     * Initialize the camera engine and set up the camera provider
     */
    suspend fun initialize(): Result<Unit> {
        return try {
            Log.i(TAG, "========================================")
            Log.i(TAG, "🚀 Initializing CameraEngine...")
            Log.i(TAG, "========================================")

            // Log device and system information
            logSystemInfo()

            // Log all available sensors (important for Samsung AI Manager detection)
            logAvailableSensors()

            // Log permissions status
            logPermissionsStatus()

            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProvider = cameraProviderFuture.get()

            // Detect available cameras
            val cameras = cameraProvider?.availableCameraInfos ?: emptyList()
            _availableCameras.value = cameras
            Log.i(TAG, "📷 Found ${cameras.size} available cameras")

            // Log detailed camera info
            cameras.forEachIndexed { index, cameraInfo ->
                val lensFacing = when (cameraInfo.lensFacing) {
                    CameraSelector.LENS_FACING_BACK -> "BACK"
                    CameraSelector.LENS_FACING_FRONT -> "FRONT"
                    else -> "UNKNOWN"
                }
                Log.i(TAG, "   Camera $index: $lensFacing - $cameraInfo")
            }

            // Create API monitor for debugging
            val provider = cameraProvider ?: return Result.failure(
                IllegalStateException("Camera provider initialization failed")
            )
            this.apiMonitor = com.customcamera.app.debug.CameraAPIMonitor(
                com.customcamera.app.engine.CameraContext(
                    context = context,
                    cameraProvider = provider,
                    debugLogger = DebugLogger(),
                    settingsManager = SettingsManager.getInstance(context),
                    cameraEngine = this,
                    apiMonitor = null // Circular reference prevention
                )
            )

            // Set as global instance for DebugActivity access
            val monitor = apiMonitor ?: return Result.failure(
                IllegalStateException("API monitor initialization failed")
            )
            com.customcamera.app.debug.GlobalAPIMonitor.setInstance(monitor)
            Log.i(TAG, "✅ API monitor initialized and registered globally")

            // Log camera provider initialization
            this.apiMonitor?.logCameraProviderCall(
                "getInstance",
                mapOf(
                    "availableCameras" to cameras.size,
                    "timestamp" to System.currentTimeMillis()
                )
            )

            // Initialize plugin manager with camera context including API monitor
            val cameraContext = CameraContext(
                context = context,
                cameraProvider = provider,
                debugLogger = DebugLogger(),
                settingsManager = SettingsManager.getInstance(context),
                cameraEngine = this,
                apiMonitor = this.apiMonitor
            )
            pluginManager.initialize(cameraContext)

            // Auto-register plugins from registry using Provider Pattern
            initializePluginsFromRegistry(cameraContext)

            _isInitialized.value = true
            Log.i(TAG, "✅ CameraEngine initialized successfully")
            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "❌ CameraEngine initialization failed", e)
            Result.failure(e)
        }
    }

    /**
     * Initialize and register plugins from PluginRegistry using Provider Pattern.
     *
     * This method automatically:
     * 1. Gets all supported plugin providers from the registry
     * 2. Creates plugin dependencies (context, debugLogger)
     * 3. Creates plugin instances using provider.create()
     * 4. Registers them with the plugin manager
     *
     * This eliminates the need for manual plugin instantiation in CameraActivityEngine.
     */
    private fun initializePluginsFromRegistry(cameraContext: CameraContext) {
        Log.i(TAG, "🔌 Initializing plugins from registry...")

        try {
            // Create plugin dependencies for provider instantiation
            val dependencies = com.customcamera.app.engine.plugins.PluginDependencies(
                context = cameraContext.context,
                debugLogger = cameraContext.debugLogger
            )

            // Get all supported providers from registry
            val supportedProviders = pluginRegistry.getSupportedProviders()
            Log.i(TAG, "Found ${supportedProviders.size} supported plugin providers")

            // Create and register each plugin
            supportedProviders.forEach { provider ->
                try {
                    // Create plugin instance using provider
                    val plugin = provider.create(dependencies)

                    // Register with plugin manager
                    pluginManager.registerPlugin(plugin)

                    Log.d(TAG, "✅ Registered plugin: ${provider.id} (${plugin.name})")

                } catch (e: Exception) {
                    Log.e(TAG, "❌ Failed to create/register plugin: ${provider.id}", e)
                }
            }

            val registeredCount = pluginManager.getAllPlugins().size
            Log.i(TAG, "✅ Successfully registered $registeredCount plugins from registry")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to initialize plugins from registry", e)
        }
    }

    /**
     * Bind camera with specified configuration and use cases
     */
    suspend fun bindCamera(config: CameraConfig): Result<Camera> {
        val provider = cameraProvider ?: return Result.failure(
            IllegalStateException("CameraEngine not initialized")
        )

        return try {
            Log.i(TAG, "Binding camera with config: $config")

            // Unbind any existing use cases
            provider.unbindAll()

            // Create camera selector
            currentCameraSelector = createCameraSelector(config.cameraIndex)
            _currentCameraIndex.value = config.cameraIndex

            // Build use cases based on configuration
            val useCases = buildUseCases(config)

            // Bind to lifecycle
            val selector = currentCameraSelector ?: return Result.failure(
                IllegalStateException("Camera selector not created")
            )
            camera = provider.bindToLifecycle(
                lifecycleOwner,
                selector,
                *useCases.toTypedArray()
            )

            val boundCamera = camera ?: return Result.failure(
                IllegalStateException("Camera binding returned null")
            )

            // Query and log video frame rate capabilities if video is enabled
            if (config.enableVideoCapture && videoCapture != null) {
                queryVideoFrameRateCapabilities(boundCamera, config.videoFrameRate)
            }

            // Log successful camera binding
            apiMonitor?.logCameraBinding(
                cameraId = "camera_${config.cameraIndex}",
                useCases = useCases,
                success = true
            )

            // Log exposure state for debugging over-exposure issues
            camera?.cameraInfo?.exposureState?.let { exposureState ->
                Log.i(TAG, "📊 Exposure State: " +
                    "Range=${exposureState.exposureCompensationRange}, " +
                    "Step=${exposureState.exposureCompensationStep}, " +
                    "Index=${exposureState.exposureCompensationIndex}, " +
                    "Supported=${exposureState.isExposureCompensationSupported}")
            }

            // Log initial camera state (snapshot at bind time)
            val currentState = camera?.cameraInfo?.cameraState?.value
            currentState?.let { state ->
                apiMonitor?.logCameraState(
                    "camera_${config.cameraIndex}",
                    state.type.name,
                    mapOf("atBindTime" to true)
                )
                Log.i(TAG, "📸 Camera initial state: ${state.type.name}")

                // Log any errors present at bind time
                state.error?.let { error ->
                    val errorDetails = mapOf(
                        "errorCode" to error.code,
                        "errorType" to when (error.code) {
                            androidx.camera.core.CameraState.ERROR_STREAM_CONFIG -> "STREAM_CONFIG"
                            androidx.camera.core.CameraState.ERROR_CAMERA_IN_USE -> "CAMERA_IN_USE"
                            androidx.camera.core.CameraState.ERROR_MAX_CAMERAS_IN_USE -> "MAX_CAMERAS_IN_USE"
                            androidx.camera.core.CameraState.ERROR_OTHER_RECOVERABLE_ERROR -> "OTHER_RECOVERABLE"
                            androidx.camera.core.CameraState.ERROR_CAMERA_DISABLED -> "CAMERA_DISABLED"
                            androidx.camera.core.CameraState.ERROR_CAMERA_FATAL_ERROR -> "CAMERA_FATAL"
                            androidx.camera.core.CameraState.ERROR_DO_NOT_DISTURB_MODE_ENABLED -> "DO_NOT_DISTURB"
                            else -> "UNKNOWN"
                        },
                        "atBindTime" to true
                    )
                    apiMonitor?.logCameraState("camera_${config.cameraIndex}", "ERROR", errorDetails)
                    Log.e(TAG, "❌ Camera ERROR at bind time - camera_${config.cameraIndex}: ${errorDetails["errorType"]}, Code: ${error.code}")
                }
            }

            // Observe camera state changes to track CLOSED → OPEN transition
            camera?.cameraInfo?.cameraState?.observe(lifecycleOwner) { state ->
                val stateDetails = mutableMapOf<String, Any>(
                    "stateType" to state.type.name,
                    "timestamp" to System.currentTimeMillis()
                )

                Log.i(TAG, "🔄 Camera state changed: ${state.type.name} for camera_${config.cameraIndex}")

                state.error?.let { error ->
                    val errorType = when (error.code) {
                        androidx.camera.core.CameraState.ERROR_STREAM_CONFIG -> "STREAM_CONFIG"
                        androidx.camera.core.CameraState.ERROR_CAMERA_IN_USE -> "CAMERA_IN_USE"
                        androidx.camera.core.CameraState.ERROR_MAX_CAMERAS_IN_USE -> "MAX_CAMERAS_IN_USE"
                        androidx.camera.core.CameraState.ERROR_OTHER_RECOVERABLE_ERROR -> "OTHER_RECOVERABLE"
                        androidx.camera.core.CameraState.ERROR_CAMERA_DISABLED -> "CAMERA_DISABLED"
                        androidx.camera.core.CameraState.ERROR_CAMERA_FATAL_ERROR -> "CAMERA_FATAL"
                        androidx.camera.core.CameraState.ERROR_DO_NOT_DISTURB_MODE_ENABLED -> "DO_NOT_DISTURB"
                        else -> "UNKNOWN"
                    }
                    stateDetails["errorCode"] = error.code
                    stateDetails["errorType"] = errorType
                    stateDetails["errorCause"] = error.cause?.toString() ?: "no cause"

                    Log.e(TAG, "❌ Camera ERROR: $errorType (code=${error.code}) - camera_${config.cameraIndex}")
                    Log.e(TAG, "   Error cause: ${error.cause}")

                    apiMonitor?.logCameraState("camera_${config.cameraIndex}", "ERROR", stateDetails)
                } ?: run {
                    // No error, just state change
                    apiMonitor?.logCameraState("camera_${config.cameraIndex}", state.type.name, stateDetails)

                    when (state.type) {
                        androidx.camera.core.CameraState.Type.PENDING_OPEN -> {
                            Log.i(TAG, "⏳ Camera PENDING_OPEN - camera_${config.cameraIndex}")
                        }
                        androidx.camera.core.CameraState.Type.OPENING -> {
                            Log.i(TAG, "🔓 Camera OPENING - camera_${config.cameraIndex}")
                        }
                        androidx.camera.core.CameraState.Type.OPEN -> {
                            Log.i(TAG, "✅ Camera OPEN - camera_${config.cameraIndex} - Preview should be visible")
                        }
                        androidx.camera.core.CameraState.Type.CLOSING -> {
                            Log.i(TAG, "🔒 Camera CLOSING - camera_${config.cameraIndex}")
                        }
                        androidx.camera.core.CameraState.Type.CLOSED -> {
                            Log.w(TAG, "⚠️ Camera CLOSED - camera_${config.cameraIndex} - This may indicate camera startup failure")
                        }
                    }
                }
            }

            // Notify plugins that camera is ready
            pluginManager.onCameraReady(boundCamera)

            Log.i(TAG, "✅ Camera bound successfully")
            Result.success(boundCamera)

        } catch (e: Exception) {
            // Log failed camera binding with error details
            apiMonitor?.logCameraBinding(
                cameraId = "camera_${config.cameraIndex}",
                useCases = emptyList(),
                success = false,
                error = "${e.javaClass.simpleName}: ${e.message}"
            )
            apiMonitor?.logError("camera_${config.cameraIndex}", "bindCamera", e)

            Log.e(TAG, "❌ Camera binding failed", e)
            Result.failure(e)
        }
    }

    /**
     * Register a new plugin with the engine
     */
    fun registerPlugin(plugin: CameraPlugin) {
        Log.i(TAG, "Registering plugin: ${plugin.name}")
        pluginManager.registerPlugin(plugin)
    }

    /**
     * Unregister a plugin from the engine
     */
    fun unregisterPlugin(pluginName: String) {
        Log.i(TAG, "Unregistering plugin: $pluginName")
        pluginManager.unregisterPlugin(pluginName)
    }

    /**
     * Switch to a different camera
     */
    suspend fun switchCamera(newCameraIndex: Int): Result<Camera> {
        val cameras = _availableCameras.value
        if (newCameraIndex !in cameras.indices) {
            return Result.failure(
                IndexOutOfBoundsException("Camera index $newCameraIndex out of range")
            )
        }

        val currentConfig = CameraConfig(
            cameraIndex = newCameraIndex,
            enablePreview = preview != null,
            enableImageCapture = imageCapture != null,
            enableVideoCapture = videoCapture != null,
            enableImageAnalysis = imageAnalysis != null
        )

        return bindCamera(currentConfig)
    }

    /**
     * Get current camera instance
     */
    fun getCurrentCamera(): Camera? = camera

    /**
     * Get current camera selector
     */
    fun getCurrentCameraSelector(): CameraSelector? = currentCameraSelector

    /**
     * Get current camera state (CLOSED, OPENING, OPEN, etc.)
     * Used to prevent premature camera switches during initialization
     */
    fun getCurrentCameraState(): androidx.camera.core.CameraState.Type? {
        return camera?.cameraInfo?.cameraState?.value?.type
    }

    /**
     * Get plugin by name
     */
    fun getPlugin(name: String): CameraPlugin? = pluginManager.getPlugin(name)

    /**
     * Get all registered plugins
     */
    fun getAllPlugins(): List<CameraPlugin> = pluginManager.getAllPlugins()

    /**
     * Get all user-toggleable plugins (for UI display)
     */
    fun getToggleablePlugins(): List<CameraPlugin> = pluginManager.getToggleablePlugins()

    fun getProvider(): ProcessCameraProvider? = cameraProvider

    /**
     * Get specific use case instance
     */
    fun getPreview(): Preview? = preview
    fun getImageCapture(): ImageCapture? = imageCapture
    fun getVideoCapture(): VideoCapture<Recorder>? = videoCapture
    fun getImageAnalysis(): ImageAnalysis? = imageAnalysis

    /**
     * Process a frame through all registered plugins
     */
    fun processFrame(image: ImageProxy) {
        pluginManager.processFrame(image)
    }

    /**
     * Switch to concurrent camera mode for PiP functionality
     *
     * Uses CameraX 1.3+ ConcurrentCamera API to bind both cameras together.
     *
     * @param mainCameraIndex Index of the main (primary) camera
     * @param pipCameraIndex Index of the PiP (secondary) camera
     * @param mainPreviewView PreviewView for the main camera feed
     * @param pipPreviewView PreviewView for the PiP camera feed
     * @param onSuccess Callback invoked when mode switch succeeds
     * @param onFailure Callback invoked with exception if mode switch fails
     */
    fun switchToConcurrentMode(
        mainCameraIndex: Int,
        pipCameraIndex: Int,
        mainPreviewView: PreviewView,
        pipPreviewView: PreviewView,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        // Launch in managed coroutine scope to handle async operations
        engineScope.launch {
            try {
                Log.i(TAG, "=== Switching to concurrent camera mode ===")
                Log.i(TAG, "Main camera: $mainCameraIndex, PiP camera: $pipCameraIndex")

                val provider = cameraProvider ?: throw IllegalStateException("Camera provider not initialized")

                // Preserve current state before switching modes
                videoWasEnabled = videoCapture != null
                analysisWasEnabled = imageAnalysis != null
                Log.i(TAG, "Preserving state: video=$videoWasEnabled, analysis=$analysisWasEnabled")

                // Unbind current camera (if any)
                unbindCurrentCamera()

                // Create PiP preview use case
                val pipPreview = Preview.Builder()
                    .build()
                    .apply {
                        setSurfaceProvider(pipPreviewView.surfaceProvider)
                    }

                // Build use cases for main camera
                // IMPORTANT: Concurrent cameras support max 2 UseCases per camera
                // We use Preview + ImageCapture only (no video, no image analysis in concurrent mode)
                val mainUseCases = mutableListOf<UseCase>()

                // Main camera preview - connect to main PreviewView
                val mainPreview = Preview.Builder().build().apply {
                    setSurfaceProvider(mainPreviewView.surfaceProvider)
                }
                mainUseCases.add(mainPreview)
                preview = mainPreview

                Log.i(TAG, "Main camera preview connected to PreviewView")

                // Main camera image capture
                val mainCapture = ImageCapture.Builder().build()
                mainUseCases.add(mainCapture)
                imageCapture = mainCapture

                // NOTE: VideoCapture and ImageAnalysis are disabled in concurrent mode
                // to stay within the 2 UseCase limit
                Log.i(TAG, "Concurrent mode: Using ${mainUseCases.size} use cases for main camera (Preview + ImageCapture)")

                // Build UseCaseGroup for main camera
                val mainUseCaseGroup = UseCaseGroup.Builder().apply {
                    mainUseCases.forEach { addUseCase(it) }
                }.build()

                // Build UseCaseGroup for PiP camera (preview only)
                // PiP frame will be captured from PreviewView.bitmap when taking photo
                val pipUseCaseGroup = UseCaseGroup.Builder()
                    .addUseCase(pipPreview)
                    .build()

                Log.i(TAG, "PiP camera: Preview only (frame captured from PreviewView.bitmap)")

                // Create camera selectors
                val mainSelector = createCameraSelector(mainCameraIndex)
                val pipSelector = createCameraSelector(pipCameraIndex)

                // Build SingleCameraConfigs using CameraX 1.3 API
                val primaryConfig = ConcurrentCamera.SingleCameraConfig(
                    mainSelector,
                    mainUseCaseGroup,
                    lifecycleOwner
                )

                val secondaryConfig = ConcurrentCamera.SingleCameraConfig(
                    pipSelector,
                    pipUseCaseGroup,
                    lifecycleOwner
                )

                // Bind concurrent cameras
                concurrentCamera = provider.bindToLifecycle(
                    listOf(primaryConfig, secondaryConfig)
                )

                // Update state
                currentMode = CameraMode.Concurrent(mainCameraIndex, pipCameraIndex)
                _currentCameraIndex.value = mainCameraIndex

                // Get the primary camera for plugin notifications
                val primaryCamera = concurrentCamera?.cameras?.firstOrNull()
                if (primaryCamera != null) {
                    camera = primaryCamera
                    withContext(Dispatchers.Main) {
                        pluginManager.onCameraReady(primaryCamera)
                    }
                }

                Log.i(TAG, "✅ Successfully switched to concurrent camera mode")
                onSuccess()

            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to switch to concurrent mode: ${e.message}", e)

                // Log specific error details
                when {
                    e.message?.contains("surface combination") == true -> {
                        Log.e(TAG, "Surface combination not supported - likely too many use cases")
                        Log.e(TAG, "Concurrent cameras support max 2 UseCases per camera at 720p/1440p")
                    }
                    e.message?.contains("concurrent") == true -> {
                        Log.e(TAG, "Device may not support concurrent camera operation")
                    }
                }

                // Fall back to single camera
                try {
                    switchToSingleMode()
                } catch (fallbackException: Exception) {
                    Log.e(TAG, "❌ Fallback to single mode also failed", fallbackException)
                }
                onFailure(e)
            }
        }
    }

    /**
     * Switch back to single camera mode (exit PiP)
     */
    fun switchToSingleMode() {
        Log.i(TAG, "Switching to single camera mode")
        Log.i(TAG, "Restoring state: video=$videoWasEnabled, analysis=$analysisWasEnabled")

        unbindCurrentCamera()

        // Rebind single camera with preserved config
        val currentConfig = CameraConfig(
            cameraIndex = _currentCameraIndex.value,
            enablePreview = true,
            enableImageCapture = true,
            enableVideoCapture = videoWasEnabled,
            enableImageAnalysis = analysisWasEnabled
        )

        // Note: This is a blocking operation, but it's simple enough
        try {
            val provider = cameraProvider ?: throw IllegalStateException("Camera provider not initialized")

            val useCases = buildUseCases(currentConfig)
            currentCameraSelector = createCameraSelector(currentConfig.cameraIndex)

            val selector = currentCameraSelector ?: throw IllegalStateException("Camera selector not created")
            singleCamera = provider.bindToLifecycle(
                lifecycleOwner,
                selector,
                *useCases.toTypedArray()
            )

            camera = singleCamera
            currentMode = CameraMode.Single

            // Notify plugins
            singleCamera?.let { cam ->
                engineScope.launch {
                    pluginManager.onCameraReady(cam)
                }
            }

            Log.i(TAG, "✅ Switched to single camera mode")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to switch to single camera mode", e)
        }
    }

    /**
     * Unbind the current camera based on mode
     */
    private fun unbindCurrentCamera() {
        when (currentMode) {
            is CameraMode.Single -> {
                cameraProvider?.unbindAll()
                singleCamera = null
                camera = null
            }
            is CameraMode.Concurrent -> {
                cameraProvider?.unbindAll()
                concurrentCamera = null
                camera = null
            }
        }
    }

    /**
     * Get current camera mode
     */
    fun getCurrentMode(): CameraMode = currentMode

    /**
     * Clean up resources and plugins
     */
    fun cleanup() {
        Log.i(TAG, "🧹 Cleaning up CameraEngine...")

        // Cancel all coroutines in managed scope to prevent leaks
        engineScope.cancel()
        Log.i(TAG, "✅ Cancelled engine coroutine scope")

        // Clear global API monitor to prevent memory leak
        com.customcamera.app.debug.GlobalAPIMonitor.clearInstance()

        // CRITICAL: Unbind all use cases FIRST
        // This triggers CameraX to properly clean up all internal callbacks and SurfaceRequests
        try {
            cameraProvider?.unbindAll()
            Log.i(TAG, "✅ Unbound all use cases from CameraProvider")
        } catch (e: Exception) {
            Log.e(TAG, "⚠️ Error unbinding use cases", e)
        }

        // CRITICAL: Clear Preview SurfaceProvider AFTER unbinding
        // This breaks remaining reference: Preview -> SurfaceProvider -> PreviewView -> Activity
        // Note: SurfaceRequest keeps transformationInfoListener even after unbind
        try {
            preview?.setSurfaceProvider(null)
            Log.i(TAG, "✅ Cleared Preview SurfaceProvider")
        } catch (e: Exception) {
            Log.e(TAG, "⚠️ Error clearing SurfaceProvider", e)
        }

        // Clean up plugin references
        pluginManager.cleanup()

        // Null out all references to allow GC
        camera = null
        cameraProvider = null
        preview = null
        imageCapture = null
        videoCapture = null
        imageAnalysis = null
        apiMonitor = null

        _isInitialized.value = false
        Log.i(TAG, "✅ CameraEngine cleanup complete")
    }

    /**
     * Create camera selector for specific camera index
     */
    private fun createCameraSelector(cameraIndex: Int): CameraSelector {
        val cameras = _availableCameras.value
        if (cameraIndex !in cameras.indices) {
            Log.w(TAG, "Invalid camera index $cameraIndex, using default back camera")
            return CameraSelector.DEFAULT_BACK_CAMERA
        }

        val targetCamera = cameras[cameraIndex]
        return CameraSelector.Builder()
            .addCameraFilter { cameraInfos ->
                cameraInfos.filter { it == targetCamera }
            }
            .build()
    }

    /**
     * Query and log supported video frame rates for current camera
     * CameraX 1.5.0: Frame rate querying via getSupportedFrameRateRanges()
     *
     * Note: Full implementation requires SessionConfig which is @ExperimentalSessionConfig
     * For now, we log the target frame rate and note that device-specific support varies
     */
    private fun queryVideoFrameRateCapabilities(camera: Camera, targetFps: Int) {
        try {
            // TODO: Implement getSupportedFrameRateRanges() when SessionConfig API is stable
            // Current limitation: UseCaseConfig != SessionConfig type mismatch
            //
            // Future implementation:
            // 1. Create SessionConfig from VideoCapture
            // 2. Query: cameraInfo.getSupportedFrameRateRanges(sessionConfig)
            // 3. Apply: sessionConfigBuilder.setExpectedFrameRateRange(range)
            //
            // Reference: https://developer.android.com/jetpack/androidx/releases/camera#1.5.0

            Log.i(TAG, "📹 Video frame rate configuration:")
            Log.i(TAG, "   Target: ${targetFps} fps")
            Log.i(TAG, "   Note: Device will use closest supported rate")
            Log.i(TAG, "   TODO: Implement SessionConfig.setExpectedFrameRateRange() when API is stable")

        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Unable to query frame rate capabilities: ${e.message}")
        }
    }

    /**
     * Build use cases based on configuration
     */
    private fun buildUseCases(config: CameraConfig): List<UseCase> {
        val useCases = mutableListOf<UseCase>()

        if (config.enablePreview) {
            val previewUseCase = Preview.Builder().build()
            preview = previewUseCase
            useCases.add(previewUseCase)
        }

        if (config.enableImageCapture) {
            val builder = ImageCapture.Builder()

            // Configure RAW capture if RAWCapturePlugin is enabled
            try {
                val rawPlugin = pluginManager.getPlugin("RAWCapture") as? RAWCapturePlugin
                if (rawPlugin != null) {
                    Log.d(TAG, "RAWCapturePlugin found, checking if RAW capture is enabled")
                    if (rawPlugin.isRawEnabled() && rawPlugin.isRawSupported()) {
                        Log.i(TAG, "Configuring ImageCapture for RAW capture")
                        rawPlugin.configureImageCapture(builder)
                    } else {
                        Log.d(TAG, "RAW capture not enabled or not supported")
                    }
                } else {
                    Log.d(TAG, "RAWCapturePlugin not found in PluginManager")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error configuring RAW capture", e)
            }

            val imageCaptureUseCase = builder.build()
            imageCapture = imageCaptureUseCase
            useCases.add(imageCaptureUseCase)
        }

        if (config.enableVideoCapture) {
            // Configure Recorder with QualitySelector and FallbackStrategy
            // CameraX 1.5.0+ supports frame rate configuration via SessionConfig

            // Read user's video quality setting
            val userQuality = settingsManager.getVideoQuality()
            val targetQuality = mapVideoQuality(userQuality)
            Log.i(TAG, "📹 Video quality: $userQuality → $targetQuality")

            val qualitySelector = androidx.camera.video.QualitySelector.from(
                targetQuality,
                androidx.camera.video.FallbackStrategy.lowerQualityOrHigherThan(androidx.camera.video.Quality.SD)
            )

            // Build Recorder with quality configuration
            val recorder = Recorder.Builder()
                .setQualitySelector(qualitySelector)
                .build()

            // Create VideoCapture use case
            // CameraX 1.5.0: Frame rate configuration will be added via SessionConfig
            // TODO: Implement setExpectedFrameRateRange() when @ExperimentalSessionConfig is stable
            // For now, log the target frame rate from config
            val targetFps = config.videoFrameRate
            Log.i(TAG, "📹 Video capture initialized with target frame rate: ${targetFps} fps")
            if (config.enableVariableFrameRate) {
                Log.i(TAG, "   Variable frame rate enabled (allows dynamic adjustment)")
            }

            val videoCaptureUseCase = VideoCapture.withOutput(recorder)
            videoCapture = videoCaptureUseCase
            useCases.add(videoCaptureUseCase)
        }

        if (config.enableImageAnalysis) {
            val imageAnalysisUseCase = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .apply {
                    // Performance Optimization (Session 44): Use background executor
                    // to offload frame processing from main thread
                    setAnalyzer(
                        java.util.concurrent.Executors.newSingleThreadExecutor()
                    ) { image ->
                        processFrame(image)
                        image.close()
                    }
                }
            imageAnalysis = imageAnalysisUseCase
            useCases.add(imageAnalysisUseCase)
        }

        return useCases
    }

    /**
     * Log comprehensive system information for debugging
     */
    private fun logSystemInfo() {
        Log.i(TAG, "📱 Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
        Log.i(TAG, "   Android: ${android.os.Build.VERSION.RELEASE} (SDK ${android.os.Build.VERSION.SDK_INT})")
        Log.i(TAG, "   Build: ${android.os.Build.DISPLAY}")
    }

    /**
     * Log all available sensors (critical for Samsung AI Manager issues)
     */
    private fun logAvailableSensors() {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? android.hardware.SensorManager
        if (sensorManager == null) {
            Log.e(TAG, "❌ SensorManager not available!")
            return
        }

        val allSensors = sensorManager.getSensorList(android.hardware.Sensor.TYPE_ALL)
        Log.i(TAG, "🔬 Available sensors: ${allSensors.size} total")

        // Check for critical sensors
        val magnetometer = sensorManager.getDefaultSensor(android.hardware.Sensor.TYPE_MAGNETIC_FIELD)
        val accelerometer = sensorManager.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER)
        val gyroscope = sensorManager.getDefaultSensor(android.hardware.Sensor.TYPE_GYROSCOPE)

        Log.i(TAG, "   Magnetometer: ${if (magnetometer != null) "✅ Available (${magnetometer.name})" else "❌ NOT AVAILABLE - May cause Samsung AI Manager issues!"}")
        Log.i(TAG, "   Accelerometer: ${if (accelerometer != null) "✅ Available (${accelerometer.name})" else "❌ NOT AVAILABLE"}")
        Log.i(TAG, "   Gyroscope: ${if (gyroscope != null) "✅ Available (${gyroscope.name})" else "⚠️ Not available"}")

        // List all sensors for complete diagnosis
        allSensors.forEach { sensor ->
            Log.d(TAG, "      - ${sensor.name} (type=${sensor.type}, vendor=${sensor.vendor})")
        }
    }

    /**
     * Log permissions status
     */
    private fun logPermissionsStatus() {
        val cameraPermission = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.CAMERA
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        val audioPermission = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        val storagePermission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.READ_MEDIA_IMAGES
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }

        Log.i(TAG, "🔐 Permissions:")
        Log.i(TAG, "   Camera: ${if (cameraPermission) "✅ Granted" else "❌ DENIED - Camera will not work!"}")
        Log.i(TAG, "   Audio: ${if (audioPermission) "✅ Granted" else "⚠️ Denied (video recording disabled)"}")
        Log.i(TAG, "   Storage: ${if (storagePermission) "✅ Granted" else "⚠️ Denied (save may fail)"}")
    }

    /**
     * Map user-friendly video quality strings to CameraX Quality enums
     */
    private fun mapVideoQuality(qualityString: String): androidx.camera.video.Quality {
        return when (qualityString.lowercase()) {
            "4k", "2160p" -> androidx.camera.video.Quality.UHD // 3840x2160
            "1080p", "fhd" -> androidx.camera.video.Quality.FHD // 1920x1080
            "720p", "hd" -> androidx.camera.video.Quality.HD   // 1280x720
            "480p", "sd" -> androidx.camera.video.Quality.SD   // 720x480
            "highest" -> androidx.camera.video.Quality.HIGHEST
            "lowest" -> androidx.camera.video.Quality.LOWEST
            else -> {
                Log.w(TAG, "Unknown video quality '$qualityString', using 1080p")
                androidx.camera.video.Quality.FHD // Default to 1080p
            }
        }
    }

    companion object {
        private const val TAG = "CameraEngine"
    }
}

/**
 * Configuration class for camera binding
 */
data class CameraConfig(
    val cameraIndex: Int = 0,
    val enablePreview: Boolean = true,
    val enableImageCapture: Boolean = true,
    val enableVideoCapture: Boolean = false,
    val enableImageAnalysis: Boolean = false,
    val videoFrameRate: Int = 30, // Target frame rate for video recording (24/30/60)
    val enableVariableFrameRate: Boolean = false // Allow frame rate range instead of fixed
)