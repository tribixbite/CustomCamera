package com.customcamera.app

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.customcamera.app.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupClickListeners()
    }
    
    private fun setupClickListeners() {
        binding.openCameraButton.setOnClickListener {
            val intent = Intent(this, CameraSelectionActivity::class.java)
            startActivity(intent)
        }
        
        binding.exitButton.setOnClickListener {
            finish()
        }
    }
}