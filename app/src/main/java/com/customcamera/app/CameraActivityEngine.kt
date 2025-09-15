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
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.customcamera.app.databinding.ActivityCameraBinding
import com.customcamera.app.engine.CameraConfig
import com.customcamera.app.engine.CameraEngine
import com.customcamera.app.plugins.AutoFocusPlugin
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Enhanced CameraActivity that uses the CameraEngine and plugin system.
 * This demonstrates how to integrate the plugin architecture with existing camera functionality.
 */
class CameraActivityEngine : AppCompatActivity() {

    private lateinit var binding: ActivityCameraBinding
    private lateinit var cameraEngine: CameraEngine

    private var cameraIndex: Int = 0
    private var isFlashOn: Boolean = false

    // Plugins
    private lateinit var autoFocusPlugin: AutoFocusPlugin

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
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )
    }

    private fun setupUI() {
        binding.captureButton.setOnClickListener { capturePhoto() }
        binding.switchCameraButton.setOnClickListener { switchCamera() }
        binding.flashButton.setOnClickListener { toggleFlash() }
        binding.galleryButton.setOnClickListener { openGallery() }
        binding.settingsButton.setOnClickListener { openSettings() }

        Log.i(TAG, "✅ UI setup complete")
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

        Log.i(TAG, "✅ Camera engine and plugins initialized")
    }

    private fun startCameraWithEngine() {
        Log.i(TAG, "Starting camera with engine...")

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
                    enableVideoCapture = false,
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

                // Update flash button state
                updateFlashButton()

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
            val intent = Intent(Intent.ACTION_PICK).apply {
                type = "image/*"
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Gallery not available", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openSettings() {
        Toast.makeText(this, "Camera settings coming soon", Toast.LENGTH_SHORT).show()
    }

    private fun handleCameraError(message: String) {
        Log.e(TAG, message)
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()

        // Try to gracefully fallback to camera selection
        if (cameraIndex > 0) {
            Log.i(TAG, "Attempting fallback to camera 0")
            cameraIndex = 0
            startCameraWithEngine()
        } else {
            Log.e(TAG, "No working cameras found, returning to selection")
            finish()
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

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "Cleaning up camera engine...")
        cameraEngine.cleanup()
    }

    companion object {
        private const val TAG = "CameraActivityEngine"
    }
}