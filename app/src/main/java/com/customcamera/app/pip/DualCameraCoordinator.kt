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
                Log.i(TAG, "Initializing camera provider...")
                val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                cameraProvider = suspendCoroutine { continuation ->
                    cameraProviderFuture.addListener({
                        try {
                            val provider = cameraProviderFuture.get()
                            continuation.resumeWith(Result.success(provider))
                        } catch (e: Exception) {
                            continuation.resumeWith(Result.failure(e))
                        }
                    }, ContextCompat.getMainExecutor(context))
                }

                // Check if dual camera setup is supported
                val availableCameras = cameraProvider?.availableCameraInfos ?: emptyList()
                _isDualCameraSupported.value = availableCameras.size >= 2

                Log.i(TAG, "✅ Camera provider initialized successfully")
                Log.i(TAG, "Dual camera supported: ${_isDualCameraSupported.value}")
                Log.i(TAG, "Available cameras: ${availableCameras.size}")

            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to initialize camera provider", e)
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
     * Set up only the PiP camera without affecting the main camera.
     * Use this when the main camera is already bound by CameraEngine.
     */
    fun setupPipCameraOnly(
        pipCameraIndex: Int,
        pipPreviewView: PreviewView?
    ) {
        if (pipPreviewView == null) {
            Log.e(TAG, "❌ Cannot setup PiP camera: pipPreviewView is null")
            return
        }

        coordinatorScope.launch {
            try {
                Log.i(TAG, "=== Setting up PiP camera ===")
                Log.i(TAG, "PiP camera index: $pipCameraIndex")
                Log.i(TAG, "PreviewView: ${pipPreviewView.javaClass.simpleName}")
                Log.i(TAG, "PreviewView size: ${pipPreviewView.width}x${pipPreviewView.height}")

                // **CRITICAL FIX**: Wait for camera provider to be initialized
                var retries = 0
                while (cameraProvider == null && retries < 50) {
                    Log.i(TAG, "Waiting for camera provider initialization... (attempt ${retries + 1})")
                    delay(100)
                    retries++
                }

                val provider = cameraProvider
                if (provider == null) {
                    Log.e(TAG, "❌ Camera provider not available after waiting")
                    return@launch
                }

                Log.i(TAG, "✅ Camera provider ready")
                Log.i(TAG, "Available cameras: ${provider.availableCameraInfos.size}")

                // Check dual camera support
                if (provider.availableCameraInfos.size < 2) {
                    Log.w(TAG, "⚠️ Dual camera not supported (only ${provider.availableCameraInfos.size} cameras)")
                    _isDualCameraSupported.value = false
                    return@launch
                }

                _pipCameraIndex.value = pipCameraIndex

                // Wait for the view to be laid out if needed
                if (pipPreviewView.width == 0 || pipPreviewView.height == 0) {
                    Log.i(TAG, "Waiting for PreviewView layout...")
                    delay(200) // Give it more time to lay out
                }

                // Create camera selector for PiP camera
                pipCameraSelector = createCameraSelector(pipCameraIndex)

                if (pipCameraSelector == null) {
                    Log.e(TAG, "❌ Failed to create PiP camera selector for index $pipCameraIndex")
                    return@launch
                }

                Log.i(TAG, "✅ Camera selector created for PiP camera $pipCameraIndex")

                // Set up PiP preview
                pipPreview = Preview.Builder()
                    .setTargetRotation(android.view.Surface.ROTATION_0)
                    .build()
                    .also { preview ->
                        Log.i(TAG, "Setting surface provider for PiP preview...")
                        Log.i(TAG, "PreviewView details: width=${pipPreviewView.width}, height=${pipPreviewView.height}")
                        Log.i(TAG, "PreviewView visibility: ${pipPreviewView.visibility}")
                        Log.i(TAG, "PreviewView alpha: ${pipPreviewView.alpha}")
                        Log.i(TAG, "PreviewView background: ${pipPreviewView.background}")

                        preview.setSurfaceProvider(pipPreviewView.surfaceProvider)
                        Log.i(TAG, "✅ Surface provider set")

                        // Give the surface time to be created
                        delay(100)
                    }

                try {
                    // Unbind only the PiP camera if it exists
                    pipCamera?.let {
                        Log.i(TAG, "Unbinding existing PiP camera...")
                        provider.unbind(pipPreview)
                    }

                    Log.i(TAG, "Binding PiP camera to lifecycle...")

                    // **CRITICAL**: Ensure we're on the main thread for camera binding
                    withContext(Dispatchers.Main) {
                        pipCamera = provider.bindToLifecycle(
                            lifecycleOwner,
                            pipCameraSelector!!,
                            pipPreview
                        )

                        _isActive.value = true
                        Log.i(TAG, "✅✅✅ PiP camera bound successfully to index $pipCameraIndex")
                        Log.i(TAG, "PiP camera info: ${pipCamera?.cameraInfo}")
                        Log.i(TAG, "PiP camera state: ${pipCamera?.cameraInfo?.cameraState?.value}")
                        Log.i(TAG, "PiP preview surface requested: ${pipPreview?.attachedSurfaceResolution}")

                        // Monitor camera state changes
                        pipCamera?.cameraInfo?.cameraState?.observe(lifecycleOwner) { state ->
                            Log.i(TAG, "PiP camera state changed: type=${state.type}, error=${state.error}")
                        }

                        // Give camera time to start streaming
                        delay(300)
                        Log.i(TAG, "PiP camera should now be streaming...")
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "❌ Failed to bind PiP camera", e)
                    Log.e(TAG, "Error type: ${e.javaClass.simpleName}")
                    Log.e(TAG, "Error message: ${e.message}")
                    e.printStackTrace()
                    _isActive.value = false
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to setup PiP camera", e)
                Log.e(TAG, "Error type: ${e.javaClass.simpleName}")
                Log.e(TAG, "Error message: ${e.message}")
                e.printStackTrace()
                _isActive.value = false
            }
        }
    }

    /**
     * Stop only the PiP camera without affecting the main camera
     */
    fun stopPipCameraOnly() {
        coordinatorScope.launch {
            try {
                pipCamera?.let {
                    pipPreview?.let { preview ->
                        cameraProvider?.unbind(preview)
                    }
                    pipCamera = null
                    pipPreview = null
                    _isActive.value = false
                    Log.i(TAG, "✅ PiP camera stopped")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to stop PiP camera", e)
            }
        }
    }

    /**
     * Check if dual camera coordinator is currently active
     */
    fun isActive(): Boolean = _isActive.value

    companion object {
        private const val TAG = "DualCameraCoordinator"
    }
}