package com.customcamera.app.plugins

import android.graphics.RectF
import android.util.Log
import android.view.View
import androidx.camera.core.Camera
import androidx.camera.core.ImageProxy
import com.customcamera.app.engine.CameraContext
import com.customcamera.app.engine.plugins.UIEvent
import com.customcamera.app.engine.plugins.UIPlugin
import com.customcamera.app.crop.CropOverlayView

/**
 * CropPlugin provides pre-shot crop functionality
 * with interactive crop area selection.
 */
class CropPlugin : UIPlugin() {

    override val name: String = "Crop"
    override val version: String = "1.0.0"
    override val priority: Int = 60 // Lower priority for UI overlay

    private var cameraContext: CameraContext? = null
    private var cropOverlayView: CropOverlayView? = null

    // Crop configuration
    private var cropArea: RectF = RectF(0.25f, 0.25f, 0.75f, 0.75f) // Default center crop
    private var isCropEnabled: Boolean = false
    private var aspectRatioLocked: Boolean = false
    private var currentAspectRatio: AspectRatio = AspectRatio.FREE

    enum class AspectRatio(val ratio: Float?, val displayName: String) {
        FREE(null, "Free"),
        SQUARE(1f, "1:1"),
        PHOTO_4_3(4f/3f, "4:3"),
        PHOTO_3_2(3f/2f, "3:2"),
        WIDE_16_9(16f/9f, "16:9"),
        PORTRAIT_9_16(9f/16f, "9:16")
    }

    override suspend fun initialize(context: CameraContext) {
        this.cameraContext = context
        Log.i(TAG, "CropPlugin initialized")

        loadSettings(context)

        context.debugLogger.logPlugin(
            name,
            "initialized",
            mapOf(
                "cropEnabled" to isCropEnabled,
                "aspectRatio" to currentAspectRatio.displayName,
                "cropArea" to cropArea.toString()
            )
        )
    }

    override suspend fun onCameraReady(camera: Camera) {
        Log.i(TAG, "Camera ready for crop functionality")

        if (isCropEnabled) {
            createCropOverlay()
        }

        cameraContext?.debugLogger?.logPlugin(
            name,
            "camera_ready",
            mapOf("cropOverlayCreated" to (cropOverlayView != null))
        )
    }

    override suspend fun onCameraReleased(camera: Camera) {
        Log.i(TAG, "Camera released, preserving crop settings")
        // Crop settings persist across camera switches
    }

    override fun createUIView(context: CameraContext): View? {
        if (!isEnabled || !isCropEnabled) {
            return null
        }

        // Only create new view if we don't already have one
        if (cropOverlayView == null) {
            Log.i(TAG, "Creating crop overlay view")
            createCropOverlay()
        }

        return cropOverlayView
    }

    override fun destroyUIView() {
        Log.i(TAG, "Destroying crop overlay view")
        cropOverlayView = null
    }

    override fun updateUI(camera: Camera) {
        cropOverlayView?.updateForCamera(camera)
        Log.d(TAG, "Crop UI updated for camera")
    }

    override fun onUIEvent(event: UIEvent) {
        when (event) {
            is UIEvent.Show -> enableCrop()
            is UIEvent.Hide -> disableCrop()
            is UIEvent.StateChange -> {
                when (event.state) {
                    "aspect_ratio" -> {
                        if (event.data is AspectRatio) {
                            setAspectRatio(event.data)
                        }
                    }
                    "crop_area" -> {
                        if (event.data is RectF) {
                            setCropArea(event.data)
                        }
                    }
                }
            }
            else -> Log.d(TAG, "Unhandled UI event: $event")
        }
    }

    /**
     * Apply crop to capture
     */
    fun applyCropToCapture(image: ImageProxy): ImageProxy {
        if (!isCropEnabled) {
            return image
        }

        Log.d(TAG, "Applying crop to capture: $cropArea")

        try {
            // Calculate actual crop coordinates based on image size
            val imageWidth = image.width
            val imageHeight = image.height

            val cropX = (cropArea.left * imageWidth).toInt()
            val cropY = (cropArea.top * imageHeight).toInt()
            val cropWidth = ((cropArea.right - cropArea.left) * imageWidth).toInt()
            val cropHeight = ((cropArea.bottom - cropArea.top) * imageHeight).toInt()

            Log.d(TAG, "Crop coordinates: x=$cropX, y=$cropY, w=$cropWidth, h=$cropHeight")

            // Note: In production, you'd actually crop the image here
            // This would require working with ImageProxy planes and creating a new cropped image

            cameraContext?.debugLogger?.logPlugin(
                name,
                "crop_applied",
                mapOf(
                    "originalSize" to "${imageWidth}x${imageHeight}",
                    "cropArea" to "${cropWidth}x${cropHeight}",
                    "cropRatio" to cropArea.toString()
                )
            )

            return image // Return original for now

        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply crop", e)
            return image
        }
    }

    /**
     * Enable crop mode
     */
    fun enableCrop() {
        if (!isCropEnabled) {
            isCropEnabled = true
            createCropOverlay()
            saveSettings()

            Log.i(TAG, "Crop mode enabled")

            cameraContext?.debugLogger?.logPlugin(
                name,
                "crop_enabled",
                mapOf("cropArea" to cropArea.toString())
            )
        }
    }

    /**
     * Disable crop mode
     */
    fun disableCrop() {
        if (isCropEnabled) {
            isCropEnabled = false
            cropOverlayView?.visibility = View.GONE
            saveSettings()

            Log.i(TAG, "Crop mode disabled")

            cameraContext?.debugLogger?.logPlugin(
                name,
                "crop_disabled",
                emptyMap()
            )
        }
    }

    /**
     * Set crop area (normalized coordinates 0.0-1.0)
     */
    fun setCropArea(area: RectF) {
        val clampedArea = RectF(
            area.left.coerceIn(0f, 1f),
            area.top.coerceIn(0f, 1f),
            area.right.coerceIn(0f, 1f),
            area.bottom.coerceIn(0f, 1f)
        )

        if (cropArea != clampedArea) {
            cropArea = clampedArea
            cropOverlayView?.setCropArea(cropArea)
            saveSettings()

            Log.d(TAG, "Crop area updated: $cropArea")
        }
    }

    /**
     * Set aspect ratio constraint
     */
    fun setAspectRatio(ratio: AspectRatio) {
        if (currentAspectRatio != ratio) {
            currentAspectRatio = ratio
            aspectRatioLocked = ratio != AspectRatio.FREE

            // Adjust crop area to match aspect ratio
            if (aspectRatioLocked && ratio.ratio != null) {
                adjustCropAreaToAspectRatio(ratio.ratio)
            }

            cropOverlayView?.setAspectRatio(ratio)
            saveSettings()

            Log.i(TAG, "Aspect ratio changed to: ${ratio.displayName}")

            cameraContext?.debugLogger?.logPlugin(
                name,
                "aspect_ratio_changed",
                mapOf(
                    "ratio" to ratio.displayName,
                    "locked" to aspectRatioLocked
                )
            )
        }
    }

    /**
     * Reset crop to full frame
     */
    fun resetCropToFullFrame() {
        setCropArea(RectF(0f, 0f, 1f, 1f))
        Log.i(TAG, "Crop reset to full frame")
    }

    /**
     * Save crop preset
     */
    fun saveCropPreset(name: String) {
        val settings = cameraContext?.settingsManager ?: return

        settings.setPluginSetting(this.name, "preset_$name", cropArea.toString())
        Log.i(TAG, "Crop preset '$name' saved: $cropArea")
    }

    /**
     * Load crop preset
     */
    fun loadCropPreset(name: String): Boolean {
        val settings = cameraContext?.settingsManager ?: return false

        return try {
            val presetData = settings.getPluginSetting(this.name, "preset_$name", "")
            if (presetData.isNotEmpty()) {
                // Parse RectF from string
                val coords = presetData.removeSurrounding("RectF(", ")")
                    .split(", ")
                    .map { it.toFloat() }

                if (coords.size == 4) {
                    setCropArea(RectF(coords[0], coords[1], coords[2], coords[3]))
                    Log.i(TAG, "Crop preset '$name' loaded: $cropArea")
                    true
                } else {
                    false
                }
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load crop preset '$name'", e)
            false
        }
    }

    override fun cleanup() {
        Log.i(TAG, "Cleaning up CropPlugin")

        cropOverlayView = null
        cameraContext = null
    }

    private fun createCropOverlay(): View? {
        if (cameraContext == null) return null

        cropOverlayView = CropOverlayView(cameraContext!!.context).apply {
            setCropArea(cropArea)
            setAspectRatio(currentAspectRatio)
            setAspectRatioLocked(aspectRatioLocked)
        }

        return cropOverlayView
    }

    private fun adjustCropAreaToAspectRatio(targetRatio: Float) {
        val currentWidth = cropArea.width()
        val currentHeight = cropArea.height()
        val currentRatio = currentWidth / currentHeight

        if (currentRatio != targetRatio) {
            val centerX = cropArea.centerX()
            val centerY = cropArea.centerY()

            val newWidth: Float
            val newHeight: Float

            if (currentRatio > targetRatio) {
                // Too wide, adjust width
                newHeight = currentHeight
                newWidth = newHeight * targetRatio
            } else {
                // Too tall, adjust height
                newWidth = currentWidth
                newHeight = newWidth / targetRatio
            }

            val newCropArea = RectF(
                centerX - newWidth / 2f,
                centerY - newHeight / 2f,
                centerX + newWidth / 2f,
                centerY + newHeight / 2f
            )

            // Ensure crop area stays within bounds
            if (newCropArea.left >= 0f && newCropArea.top >= 0f &&
                newCropArea.right <= 1f && newCropArea.bottom <= 1f) {
                cropArea = newCropArea
            }

            Log.d(TAG, "Adjusted crop area for aspect ratio $targetRatio: $cropArea")
        }
    }

    private fun loadSettings(context: CameraContext) {
        val settings = context.settingsManager

        try {
            isCropEnabled = settings.getPluginSetting(name, "cropEnabled", "false").toBoolean()
            aspectRatioLocked = settings.getPluginSetting(name, "aspectRatioLocked", "false").toBoolean()

            val ratioString = settings.getPluginSetting(name, "aspectRatio", AspectRatio.FREE.name)
            currentAspectRatio = AspectRatio.valueOf(ratioString)

            // Load crop area
            val cropString = settings.getPluginSetting(name, "cropArea", "0.25,0.25,0.75,0.75")
            val coords = cropString.split(",").map { it.toFloat() }
            if (coords.size == 4) {
                cropArea = RectF(coords[0], coords[1], coords[2], coords[3])
            }

            Log.i(TAG, "Loaded settings: enabled=$isCropEnabled, ratio=${currentAspectRatio.displayName}")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load settings, using defaults", e)
        }
    }

    private fun saveSettings() {
        val settings = cameraContext?.settingsManager ?: return

        settings.setPluginSetting(name, "cropEnabled", isCropEnabled.toString())
        settings.setPluginSetting(name, "aspectRatioLocked", aspectRatioLocked.toString())
        settings.setPluginSetting(name, "aspectRatio", currentAspectRatio.name)
        settings.setPluginSetting(name, "cropArea", "${cropArea.left},${cropArea.top},${cropArea.right},${cropArea.bottom}")
    }

    companion object {
        private const val TAG = "CropPlugin"
    }
}