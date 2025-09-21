package com.customcamera.app.engine

import android.content.Context
import androidx.camera.lifecycle.ProcessCameraProvider

/**
 * Shared context and utilities for camera plugins.
 * Provides access to essential camera system components and shared state.
 */
data class CameraContext(
    val context: Context,
    val cameraProvider: ProcessCameraProvider,
    val debugLogger: DebugLogger,
    val settingsManager: SettingsManager,
    val cameraEngine: CameraEngine? = null
)