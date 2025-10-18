package com.customcamera.app.engine

import android.content.Context
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.customcamera.app.engine.plugins.CameraPlugin
import com.customcamera.app.engine.plugins.PluginManager
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
    private val pluginRegistry: com.customcamera.app.engine.plugins.PluginRegistry
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

    // PiP frame capture for dual camera photos
    private var latestPipFrame: ImageProxy? = null
    private val pipFrameLock = Any()

    private val pluginManager = PluginManager()
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

    /**
     * Initialize the camera engine and set up the camera provider
     */
    suspend fun initialize(): Result<Unit> {
        return try {
            Log.i(TAG, "Initializing CameraEngine...")

            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProvider = cameraProviderFuture.get()

            // Detect available cameras
            val cameras = cameraProvider?.availableCameraInfos ?: emptyList()
            _availableCameras.value = cameras
            Log.i(TAG, "Found ${cameras.size} available cameras")

            // Create API monitor for debugging
            this.apiMonitor = com.customcamera.app.debug.CameraAPIMonitor(
                com.customcamera.app.engine.CameraContext(
                    context = context,
                    cameraProvider = cameraProvider!!,
                    debugLogger = DebugLogger(),
                    settingsManager = SettingsManager(context),
                    cameraEngine = this,
                    apiMonitor = null // Circular reference prevention
                )
            )

            // Set as global instance for DebugActivity access
            com.customcamera.app.debug.GlobalAPIMonitor.setInstance(this.apiMonitor!!)
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
                cameraProvider = cameraProvider!!,
                debugLogger = DebugLogger(),
                settingsManager = SettingsManager(context),
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
            camera = provider.bindToLifecycle(
                lifecycleOwner,
                currentCameraSelector!!,
                *useCases.toTypedArray()
            )

            // Log camera binding operation
            apiMonitor?.logCameraBinding(
                cameraId = "camera_${config.cameraIndex}",
                useCases = useCases
            )

            // Notify plugins that camera is ready
            pluginManager.onCameraReady(camera!!)

            Log.i(TAG, "✅ Camera bound successfully")
            Result.success(camera!!)

        } catch (e: Exception) {
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
        // Launch in coroutine to handle async operations
        CoroutineScope(Dispatchers.Main).launch {
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

                // Build UseCaseGroup for PiP camera (preview + analysis for frame capture)
                // Add ImageAnalysis to capture PiP frames for composite photos
                val pipAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .apply {
                        setAnalyzer(ContextCompat.getMainExecutor(context)) { image ->
                            // Store latest PiP frame for composite photos
                            synchronized(pipFrameLock) {
                                latestPipFrame?.close() // Close previous frame
                                latestPipFrame = image
                            }
                        }
                    }

                val pipUseCaseGroup = UseCaseGroup.Builder()
                    .addUseCase(pipPreview)
                    .addUseCase(pipAnalysis)
                    .build()

                Log.i(TAG, "PiP camera: Preview + ImageAnalysis (2 UseCases for frame capture)")

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

            singleCamera = provider.bindToLifecycle(
                lifecycleOwner,
                currentCameraSelector!!,
                *useCases.toTypedArray()
            )

            camera = singleCamera
            currentMode = CameraMode.Single

            // Notify plugins
            singleCamera?.let { cam ->
                CoroutineScope(Dispatchers.Main).launch {
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

                // Clean up PiP frame
                synchronized(pipFrameLock) {
                    latestPipFrame?.close()
                    latestPipFrame = null
                }
            }
        }
    }

    /**
     * Get current camera mode
     */
    fun getCurrentMode(): CameraMode = currentMode

    /**
     * Get the latest PiP frame for composite photos
     * Returns a copy of the frame that must be closed by the caller
     */
    fun getLatestPipFrame(): ImageProxy? {
        synchronized(pipFrameLock) {
            return latestPipFrame
        }
    }

    /**
     * Check if PiP frame is available for composite photos
     */
    fun hasPipFrame(): Boolean {
        synchronized(pipFrameLock) {
            return latestPipFrame != null
        }
    }

    /**
     * Clean up resources and plugins
     */
    fun cleanup() {
        Log.i(TAG, "Cleaning up CameraEngine...")

        // Clear global API monitor to prevent memory leak
        com.customcamera.app.debug.GlobalAPIMonitor.clearInstance()

        // CRITICAL: Clear Preview SurfaceProvider before unbinding
        // This breaks the reference chain: Preview -> SurfaceProvider -> PreviewView -> Activity
        preview?.setSurfaceProvider(null)
        Log.i(TAG, "Cleared Preview SurfaceProvider to prevent memory leak")

        cameraProvider?.unbindAll()
        pluginManager.cleanup()

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
     * Build use cases based on configuration
     */
    private fun buildUseCases(config: CameraConfig): List<UseCase> {
        val useCases = mutableListOf<UseCase>()

        if (config.enablePreview) {
            preview = Preview.Builder().build()
            useCases.add(preview!!)
        }

        if (config.enableImageCapture) {
            imageCapture = ImageCapture.Builder().build()
            useCases.add(imageCapture!!)
        }

        if (config.enableVideoCapture) {
            val recorder = Recorder.Builder().build()
            videoCapture = VideoCapture.withOutput(recorder)
            useCases.add(videoCapture!!)
        }

        if (config.enableImageAnalysis) {
            imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .apply {
                    setAnalyzer(
                        ContextCompat.getMainExecutor(context)
                    ) { image ->
                        processFrame(image)
                        image.close()
                    }
                }
            useCases.add(imageAnalysis!!)
        }

        return useCases
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
    val enableImageAnalysis: Boolean = false
)