package com.customcamera.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.customcamera.app.databinding.ActivityCameraSelectionBinding
import java.util.concurrent.ExecutionException

class CameraSelectionActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityCameraSelectionBinding
    private var selectedCameraIndex: Int = 0
    private var availableCameras: List<androidx.camera.core.CameraInfo> = emptyList()
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            detectAvailableCameras()
        } else {
            Toast.makeText(this, getString(R.string.camera_permission_required), Toast.LENGTH_LONG).show()
            finish()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "CameraSelectionActivity onCreate")
        
        binding = ActivityCameraSelectionBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupClickListeners()
        
        if (hasCameraPermission()) {
            detectAvailableCameras()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
    
    private fun setupClickListeners() {
        binding.continueButton.setOnClickListener {
            Log.i(TAG, "=== CONTINUE BUTTON CLICKED ===")
            Log.i(TAG, "Selected camera index: $selectedCameraIndex")

            // Launch the camera activity with full plugin system
            val intent = Intent(this, CameraActivityEngine::class.java)
            intent.putExtra(EXTRA_CAMERA_INDEX, selectedCameraIndex)

            Log.i(TAG, "Launching CameraActivityEngine (full plugin system) with camera index: $selectedCameraIndex")
            Log.i(TAG, "Intent extra key: $EXTRA_CAMERA_INDEX")

            startActivity(intent)
            finish()
        }

        binding.backButton.setOnClickListener {
            finish()
        }

        binding.skipButton.setOnClickListener {
            // Use camera 2 if available, otherwise camera 0
            val defaultCameraIndex = if (availableCameras.size > 2) 2 else 0
            val intent = Intent(this, CameraActivityEngine::class.java)
            intent.putExtra(EXTRA_CAMERA_INDEX, defaultCameraIndex)
            Log.i(TAG, "Skip button: using camera $defaultCameraIndex (default: camera 2)")
            startActivity(intent)
            finish()
        }
    }
    
    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this, 
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    private fun detectAvailableCameras() {
        Log.i(TAG, "Detecting available cameras...")
        
        try {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
            cameraProviderFuture.addListener({
                try {
                    val cameraProvider = cameraProviderFuture.get()
                    availableCameras = cameraProvider.availableCameraInfos
                    
                    Log.i(TAG, "Found ${availableCameras.size} cameras")
                    setupCameraButtons()
                    
                } catch (e: ExecutionException) {
                    Log.e(TAG, "Camera provider failed", e)
                    showNoCamerasMessage()
                } catch (e: InterruptedException) {
                    Log.e(TAG, "Camera provider interrupted", e)
                    showNoCamerasMessage()
                }
            }, ContextCompat.getMainExecutor(this))
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get camera provider", e)
            showNoCamerasMessage()
        }
    }
    
    private fun setupCameraButtons() {
        binding.cameraButtonsContainer.removeAllViews()
        
        if (availableCameras.isEmpty()) {
            showNoCamerasMessage()
            return
        }
        
        availableCameras.forEachIndexed { index, cameraInfo ->
            val button = createCameraButton(index, cameraInfo)
            binding.cameraButtonsContainer.addView(button)
        }
        
        // Auto-select camera 2 if available, otherwise first camera
        if (availableCameras.isNotEmpty()) {
            selectedCameraIndex = if (availableCameras.size > 2) 2 else 0
            Log.i(TAG, "Auto-selected camera $selectedCameraIndex (default: camera 2)")
            updateButtonSelection()
        }
    }
    
    private fun createCameraButton(index: Int, cameraInfo: androidx.camera.core.CameraInfo): android.widget.Button {
        val button = android.widget.Button(this)

        val cameraType = when (cameraInfo.lensFacing) {
            androidx.camera.core.CameraSelector.LENS_FACING_FRONT -> "Front Camera"
            androidx.camera.core.CameraSelector.LENS_FACING_BACK -> "Back Camera"
            else -> "External Camera"
        }

        val cameraIcon = when (cameraInfo.lensFacing) {
            androidx.camera.core.CameraSelector.LENS_FACING_FRONT -> "🤳"
            androidx.camera.core.CameraSelector.LENS_FACING_BACK -> "📷"
            else -> "📹"
        }

        val hasFlash = if (cameraInfo.hasFlashUnit()) " ⚡" else ""

        button.text = "$cameraIcon Camera $index\n$cameraType$hasFlash\n${cameraInfo.sensorRotationDegrees}° rotation"
        button.textSize = 14f
        button.setPadding(24, 32, 24, 32)

        val params = android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(8, 12, 8, 12)
        button.layoutParams = params

        // Enhanced click handling with animation
        button.setOnClickListener {
            Log.i(TAG, "Camera button clicked - selecting camera $index")

            // Animate button selection
            button.animate()
                .scaleX(0.95f)
                .scaleY(0.95f)
                .setDuration(100)
                .withEndAction {
                    button.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(100)
                }

            selectedCameraIndex = index
            Log.i(TAG, "Selected camera index updated to: $selectedCameraIndex")
            updateButtonSelection()
        }

        button.tag = index
        return button
    }
    
    private fun updateButtonSelection() {
        for (i in 0 until binding.cameraButtonsContainer.childCount) {
            val button = binding.cameraButtonsContainer.getChildAt(i) as? android.widget.Button
            button?.let {
                val index = it.tag as? Int
                if (index == selectedCameraIndex) {
                    // Selected camera - bright and elevated
                    it.setBackgroundColor(ContextCompat.getColor(this, R.color.md_theme_primary))
                    it.elevation = 8f
                    it.alpha = 1.0f

                    // Animate selection
                    it.animate()
                        .scaleX(1.05f)
                        .scaleY(1.05f)
                        .setDuration(200)
                        .start()
                } else {
                    // Unselected camera - dimmed
                    it.setBackgroundColor(ContextCompat.getColor(this, android.R.color.darker_gray))
                    it.elevation = 2f
                    it.alpha = 0.7f

                    // Reset scale
                    it.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(200)
                        .start()
                }
            }
        }
    }
    
    private fun showNoCamerasMessage() {
        binding.statusText.text = getString(R.string.no_cameras_available)
        binding.continueButton.isEnabled = false
        binding.skipButton.isEnabled = false
    }
    
    companion object {
        private const val TAG = "CameraSelection"
        const val EXTRA_CAMERA_INDEX = "camera_index"
    }
}