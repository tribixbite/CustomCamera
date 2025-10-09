package com.customcamera.app.engine

import android.content.Context
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
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
    private val lifecycleOwner: LifecycleOwner
) {
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var currentCameraSelector: CameraSelector? = null

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

            // Initialize plugin manager with camera context
            val cameraContext = CameraContext(
                context = context,
                cameraProvider = cameraProvider!!,
                debugLogger = DebugLogger(),
                settingsManager = SettingsManager(context),
                cameraEngine = this
            )
            pluginManager.initialize(cameraContext)

            _isInitialized.value = true
            Log.i(TAG, "✅ CameraEngine initialized successfully")
            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "❌ CameraEngine initialization failed", e)
            Result.failure(e)
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
     * Clean up resources and plugins
     */
    fun cleanup() {
        Log.i(TAG, "Cleaning up CameraEngine...")

        cameraProvider?.unbindAll()
        pluginManager.cleanup()

        camera = null
        cameraProvider = null
        preview = null
        imageCapture = null
        videoCapture = null
        imageAnalysis = null

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