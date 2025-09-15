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
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.customcamera.app.databinding.ActivityCameraBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class CameraActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityCameraBinding
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var cameraIndex: Int = 0
    private var isFlashOn: Boolean = false
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startCamera()
        } else {
            Toast.makeText(this, getString(R.string.camera_permission_required), Toast.LENGTH_LONG).show()
            finish()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "=== CameraActivity onCreate START ===")
        
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
        
        // Get camera index from intent with detailed logging
        cameraIndex = intent.getIntExtra(CameraSelectionActivity.EXTRA_CAMERA_INDEX, 0)
        Log.i(TAG, "=== CAMERA INDEX FROM INTENT ===")
        Log.i(TAG, "Intent extra value: ${intent.getIntExtra(CameraSelectionActivity.EXTRA_CAMERA_INDEX, -999)}")
        Log.i(TAG, "Using camera index: $cameraIndex")
        Log.i(TAG, "Intent extras: ${intent.extras?.keySet()?.joinToString()}")
        
        setupUI()
        
        if (hasCameraPermission()) {
            startCamera()
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
    
    private fun startCamera() {
        Log.i(TAG, "Starting camera initialization...")
        
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                bindCameraUseCases()
            } catch (e: Exception) {
                Log.e(TAG, "Camera provider initialization failed", e)
                handleCameraError("Camera initialization failed: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(this))
    }
    
    private fun bindCameraUseCases() {
        val cameraProvider = cameraProvider ?: return
        
        try {
            Log.i(TAG, "Binding camera use cases...")
            
            // Unbind any existing use cases
            cameraProvider.unbindAll()
            
            // Create use cases
            val preview = Preview.Builder().build()
            imageCapture = ImageCapture.Builder().build()
            
            // Set up preview
            preview.setSurfaceProvider(binding.previewView.surfaceProvider)
            
            // Select camera with graceful fallback
            val cameraSelector = selectCamera()
            
            // Bind to lifecycle
            camera = cameraProvider.bindToLifecycle(
                this,
                cameraSelector,
                preview,
                imageCapture
            )
            
            Log.i(TAG, "✅ Camera bound successfully")
            updateFlashButton()
            
        } catch (e: Exception) {
            Log.e(TAG, "Camera binding failed", e)
            handleCameraError("Failed to start camera: ${e.message}")
        }
    }
    
    private fun selectCamera(): CameraSelector {
        val availableCameras = cameraProvider?.availableCameraInfos ?: emptyList()
        
        Log.i(TAG, "=== CAMERA SELECTION DEBUG ===")
        Log.i(TAG, "Available cameras: ${availableCameras.size}")
        Log.i(TAG, "Requested camera index: $cameraIndex")
        
        // Log details about each available camera
        availableCameras.forEachIndexed { index, cameraInfo ->
            val facing = when (cameraInfo.lensFacing) {
                CameraSelector.LENS_FACING_FRONT -> "Front"
                CameraSelector.LENS_FACING_BACK -> "Back"
                else -> "External"
            }
            Log.i(TAG, "Camera $index: $facing facing")
        }
        
        return when {
            availableCameras.isEmpty() -> {
                Log.e(TAG, "❌ No cameras available, using default back camera")
                CameraSelector.DEFAULT_BACK_CAMERA
            }
            cameraIndex in 0 until availableCameras.size -> {
                Log.i(TAG, "✅ Using requested camera $cameraIndex (valid)")
                val selector = createCameraSelectorForIndex(cameraIndex, availableCameras)
                Log.i(TAG, "✅ Camera selector created for index $cameraIndex")
                selector
            }
            else -> {
                Log.w(TAG, "⚠️ Camera $cameraIndex not available (only ${availableCameras.size} cameras), falling back to camera 0")
                val fallbackIndex = 0
                cameraIndex = fallbackIndex // Update for consistency
                Log.i(TAG, "📍 Updated camera index to $fallbackIndex")
                createCameraSelectorForIndex(fallbackIndex, availableCameras)
            }
        }
    }
    
    private fun createCameraSelectorForIndex(index: Int, cameras: List<androidx.camera.core.CameraInfo>): CameraSelector {
        val targetCamera = cameras[index]
        return CameraSelector.Builder()
            .addCameraFilter { cameraInfos ->
                cameraInfos.filter { it == targetCamera }
            }
            .build()
    }
    
    private fun capturePhoto() {
        val imageCapture = imageCapture ?: return
        
        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val photoFile = File(filesDir, "CAMERA_$timestamp.jpg")
            
            val outputFileOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
            
            imageCapture.takePicture(
                outputFileOptions,
                ContextCompat.getMainExecutor(this),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        Toast.makeText(this@CameraActivity, "Photo saved: ${photoFile.name}", Toast.LENGTH_SHORT).show()
                        Log.i(TAG, "Photo saved: ${photoFile.absolutePath}")
                        animateCaptureButton()
                    }
                    
                    override fun onError(exception: ImageCaptureException) {
                        Log.e(TAG, "Photo capture failed", exception)
                        Toast.makeText(this@CameraActivity, "Photo capture failed", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Photo capture setup failed", e)
            Toast.makeText(this, "Capture failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun switchCamera() {
        val availableCameras = cameraProvider?.availableCameraInfos ?: return
        
        if (availableCameras.size > 1) {
            // Cycle to next camera
            cameraIndex = (cameraIndex + 1) % availableCameras.size
            Log.i(TAG, "Switching to camera $cameraIndex")
            
            bindCameraUseCases()
            animateSwitchButton()
        } else {
            Toast.makeText(this, "Only one camera available", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun toggleFlash() {
        val camera = camera ?: return
        
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
        val camera = camera ?: return
        
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
            bindCameraUseCases()
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
        cameraProvider?.unbindAll()
    }
    
    companion object {
        private const val TAG = "CameraActivity"
    }
}