package com.customcamera.app.pip

import android.content.Context
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.coroutines.suspendCoroutine

/**
 * DualCameraCoordinator manages dual camera operations for PiP functionality.
 * Handles the complexity of managing two independent camera instances
 * with proper lifecycle management and error handling.
 */
class DualCameraCoordinator(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner
) {
    private var cameraProvider: ProcessCameraProvider? = null
    private var mainCamera: Camera? = null
    private var pipCamera: Camera? = null

    // Camera selectors
    private var mainCameraSelector: CameraSelector? = null
    private var pipCameraSelector: CameraSelector? = null

    // Use cases
    private var mainPreview: Preview? = null
    private var pipPreview: Preview? = null
    private var mainImageCapture: ImageCapture? = null

    // State management
    private val _isActive = MutableStateFlow(false)
    private val _isDualCameraSupported = MutableStateFlow(false)
    private val _mainCameraIndex = MutableStateFlow(0)
    private val _pipCameraIndex = MutableStateFlow(1)

    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()
    val isDualCameraSupported: StateFlow<Boolean> = _isDualCameraSupported.asStateFlow()
    val mainCameraIndex: StateFlow<Int> = _mainCameraIndex.asStateFlow()
    val pipCameraIndex: StateFlow<Int> = _pipCameraIndex.asStateFlow()

    // Coroutine scope for camera operations
    private val coordinatorScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    init {
        Log.i(TAG, "DualCameraCoordinator initialized")
        initializeCameraProvider()
    }

    /**
     * Initialize the camera provider and check dual camera support
     */
    private fun initializeCameraProvider() {
        coordinatorScope.launch {
            try {
                val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                cameraProvider = cameraProviderFuture.get()

                // Check if dual camera setup is supported
                val availableCameras = cameraProvider?.availableCameraInfos ?: emptyList()
                _isDualCameraSupported.value = availableCameras.size >= 2

                Log.i(TAG, "Camera provider initialized. Dual camera supported: ${_isDualCameraSupported.value}")
                Log.i(TAG, "Available cameras: ${availableCameras.size}")

            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize camera provider", e)
                _isDualCameraSupported.value = false
            }
        }
    }

    /**
     * Set up dual camera with main and PiP preview views
     */
    fun setupDualCamera(
        mainCameraIndex: Int,
        pipCameraIndex: Int,
        mainPreviewView: PreviewView? = null,
        pipPreviewView: PreviewView? = null
    ) {
        if (!_isDualCameraSupported.value) {
            Log.w(TAG, "Dual camera setup requested but not supported")
            return
        }

        coordinatorScope.launch {
            try {
                Log.i(TAG, "Setting up dual camera: main=$mainCameraIndex, pip=$pipCameraIndex")

                _mainCameraIndex.value = mainCameraIndex
                _pipCameraIndex.value = pipCameraIndex

                // Unbind any existing cameras
                cameraProvider?.unbindAll()

                // Create camera selectors
                mainCameraSelector = createCameraSelector(mainCameraIndex)
                pipCameraSelector = createCameraSelector(pipCameraIndex)

                if (mainCameraSelector == null || pipCameraSelector == null) {
                    Log.e(TAG, "Failed to create camera selectors")
                    return@launch
                }

                // Set up use cases for main camera
                setupMainCameraUseCases(mainPreviewView)

                // Set up use cases for PiP camera
                setupPipCameraUseCases(pipPreviewView)

                // Bind cameras
                bindMainCamera()
                bindPipCamera()

                _isActive.value = true
                Log.i(TAG, "✅ Dual camera setup complete")

            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to setup dual camera", e)
                _isActive.value = false
            }
        }
    }

    /**
     * Switch cameras (swap main and PiP)
     */
    fun swapCameras() {
        if (!_isActive.value) {
            Log.w(TAG, "Cannot swap cameras - dual camera not active")
            return
        }

        coordinatorScope.launch {
            try {
                Log.i(TAG, "Swapping cameras")

                val currentMain = _mainCameraIndex.value
                val currentPip = _pipCameraIndex.value

                // Unbind current cameras
                cameraProvider?.unbindAll()

                // Swap indices
                _mainCameraIndex.value = currentPip
                _pipCameraIndex.value = currentMain

                // Create new selectors
                mainCameraSelector = createCameraSelector(_mainCameraIndex.value)
                pipCameraSelector = createCameraSelector(_pipCameraIndex.value)

                // Rebind with swapped cameras
                bindMainCamera()
                bindPipCamera()

                Log.i(TAG, "✅ Camera swap complete: main=${_mainCameraIndex.value}, pip=${_pipCameraIndex.value}")

            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to swap cameras", e)
            }
        }
    }

    /**
     * Stop PiP camera while keeping main camera active
     */
    fun stopPiPCamera() {
        coordinatorScope.launch {
            try {
                pipCamera?.let {
                    cameraProvider?.unbind(pipPreview)
                    pipCamera = null
                    pipPreview = null
                    Log.i(TAG, "PiP camera stopped")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop PiP camera", e)
            }
        }
    }

    /**
     * Capture photo using main camera
     */
    suspend fun capturePhoto(): Result<ImageCapture.OutputFileResults> {
        val imageCapture = mainImageCapture ?: return Result.failure(
            IllegalStateException("Main camera not initialized for capture")
        )

        return withContext(Dispatchers.IO) {
            try {
                val outputFileOptions = ImageCapture.OutputFileOptions.Builder(
                    createPhotoFile()
                ).build()

                val result = suspendCoroutine { continuation: kotlin.coroutines.Continuation<ImageCapture.OutputFileResults> ->
                    imageCapture.takePicture(
                        outputFileOptions,
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                continuation.resumeWith(Result.success(output))
                            }

                            override fun onError(exception: ImageCaptureException) {
                                continuation.resumeWith(Result.failure(exception))
                            }
                        }
                    )
                }

                Log.i(TAG, "✅ Photo captured successfully")
                Result.success(result)

            } catch (e: Exception) {
                Log.e(TAG, "❌ Photo capture failed", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Get current dual camera status
     */
    fun getDualCameraStatus(): Map<String, Any> {
        return mapOf(
            "isActive" to _isActive.value,
            "isDualCameraSupported" to _isDualCameraSupported.value,
            "mainCameraIndex" to _mainCameraIndex.value,
            "pipCameraIndex" to _pipCameraIndex.value,
            "mainCameraBound" to (mainCamera != null),
            "pipCameraBound" to (pipCamera != null),
            "availableCameras" to (cameraProvider?.availableCameraInfos?.size ?: 0)
        )
    }

    /**
     * Cleanup resources
     */
    fun cleanup() {
        Log.i(TAG, "Cleaning up DualCameraCoordinator")

        coordinatorScope.launch {
            try {
                cameraProvider?.unbindAll()

                mainCamera = null
                pipCamera = null
                mainPreview = null
                pipPreview = null
                mainImageCapture = null

                _isActive.value = false

                Log.i(TAG, "✅ DualCameraCoordinator cleanup complete")
            } catch (e: Exception) {
                Log.e(TAG, "Error during cleanup", e)
            }
        }

        coordinatorScope.cancel()
    }

    /**
     * Create camera selector for specific camera index
     */
    private fun createCameraSelector(cameraIndex: Int): CameraSelector? {
        val availableCameras = cameraProvider?.availableCameraInfos ?: return null

        if (cameraIndex !in availableCameras.indices) {
            Log.w(TAG, "Invalid camera index: $cameraIndex")
            return null
        }

        val targetCamera = availableCameras[cameraIndex]
        return CameraSelector.Builder()
            .addCameraFilter { cameraInfos ->
                cameraInfos.filter { it == targetCamera }
            }
            .build()
    }

    /**
     * Set up use cases for main camera
     */
    private fun setupMainCameraUseCases(previewView: PreviewView?) {
        // Main camera preview
        mainPreview = Preview.Builder()
            .build()
            .apply {
                previewView?.let { setSurfaceProvider(it.surfaceProvider) }
            }

        // Main camera image capture
        mainImageCapture = ImageCapture.Builder()
            .build()
    }

    /**
     * Set up use cases for PiP camera
     */
    private fun setupPipCameraUseCases(previewView: PreviewView?) {
        // PiP camera preview only
        pipPreview = Preview.Builder()
            .build()
            .apply {
                previewView?.let { setSurfaceProvider(it.surfaceProvider) }
            }
    }

    /**
     * Bind main camera
     */
    private fun bindMainCamera() {
        val provider = cameraProvider ?: return
        val selector = mainCameraSelector ?: return

        try {
            mainCamera = provider.bindToLifecycle(
                lifecycleOwner,
                selector,
                mainPreview,
                mainImageCapture
            )

            Log.d(TAG, "Main camera bound successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind main camera", e)
        }
    }

    /**
     * Bind PiP camera
     */
    private fun bindPipCamera() {
        val provider = cameraProvider ?: return
        val selector = pipCameraSelector ?: return

        try {
            pipCamera = provider.bindToLifecycle(
                lifecycleOwner,
                selector,
                pipPreview
            )

            Log.d(TAG, "PiP camera bound successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind PiP camera", e)
        }
    }

    /**
     * Create file for photo capture
     */
    private fun createPhotoFile(): java.io.File {
        val timestamp = System.currentTimeMillis()
        val fileName = "dual_camera_${timestamp}.jpg"

        return java.io.File(
            context.getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES),
            fileName
        )
    }

    /**
     * Check if dual camera coordinator is currently active
     */
    fun isActive(): Boolean = _isActive.value

    companion object {
        private const val TAG = "DualCameraCoordinator"
    }
}