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
            
            val intent = Intent(this, CameraActivity::class.java)
            intent.putExtra(EXTRA_CAMERA_INDEX, selectedCameraIndex)
            
            Log.i(TAG, "Launching CameraActivity with camera index: $selectedCameraIndex")
            Log.i(TAG, "Intent extra key: $EXTRA_CAMERA_INDEX")
            
            startActivity(intent)
            finish()
        }
        
        binding.backButton.setOnClickListener {
            finish()
        }
        
        binding.skipButton.setOnClickListener {
            // Use first available camera or default to 0
            val intent = Intent(this, CameraActivity::class.java)
            intent.putExtra(EXTRA_CAMERA_INDEX, 0)
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
        
        // Auto-select first camera for better UX
        if (availableCameras.isNotEmpty()) {
            selectedCameraIndex = 0
            Log.i(TAG, "Auto-selected camera 0 (first available camera)")
            updateButtonSelection()
        }
    }
    
    private fun createCameraButton(index: Int, cameraInfo: androidx.camera.core.CameraInfo): android.widget.Button {
        val button = android.widget.Button(this)
        
        val cameraType = when (cameraInfo.lensFacing) {
            androidx.camera.core.CameraSelector.LENS_FACING_FRONT -> "Front"
            androidx.camera.core.CameraSelector.LENS_FACING_BACK -> "Back" 
            else -> "External"
        }
        
        button.text = "📸 Camera $index\n($cameraType)"
        button.textSize = 16f
        
        val params = android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(0, 16, 0, 16)
        button.layoutParams = params
        
        button.setOnClickListener {
            Log.i(TAG, "Camera button clicked - selecting camera $index")
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
                    it.setBackgroundColor(ContextCompat.getColor(this, R.color.md_theme_primary))
                } else {
                    it.setBackgroundColor(ContextCompat.getColor(this, android.R.color.darker_gray))
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