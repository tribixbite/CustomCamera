package com.customcamera.app.plugins

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.YuvImage
import android.util.Log
import android.view.View
import androidx.camera.core.Camera
import androidx.camera.core.ImageProxy
import com.customcamera.app.engine.CameraContext
import com.customcamera.app.engine.plugins.UIEvent
import com.customcamera.app.engine.plugins.UIPlugin
import com.customcamera.app.crop.CropOverlayView
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

/**
 * CropPlugin provides pre-shot crop functionality
 * with interactive crop area selection.
 */
class CropPlugin : UIPlugin() {

    override val name: String = "Crop"
    override val displayName: String = "Pre-Shot Crop"
    override val description: String = "Crop photos before capturing"
    override val iconResId: Int = com.customcamera.app.R.drawable.ic_camera
    override val category: com.customcamera.app.engine.plugins.PluginCategory = com.customcamera.app.engine.plugins.PluginCategory.CAPTURE
    override val userToggleable: Boolean = true
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
     * Apply crop to capture - converts ImageProxy to cropped Bitmap
     */
    fun applyCropToBitmap(image: ImageProxy): Bitmap? {
        if (!isCropEnabled) {
            return imageProxyToBitmap(image)
        }

        Log.d(TAG, "Applying crop to capture: $cropArea")

        try {
            // Convert ImageProxy to Bitmap
            val fullBitmap = imageProxyToBitmap(image) ?: run {
                Log.e(TAG, "Failed to convert ImageProxy to Bitmap")
                return null
            }

            // Calculate actual crop coordinates based on image size
            val imageWidth = fullBitmap.width
            val imageHeight = fullBitmap.height

            val cropX = (cropArea.left * imageWidth).toInt().coerceAtLeast(0)
            val cropY = (cropArea.top * imageHeight).toInt().coerceAtLeast(0)
            val cropWidth = ((cropArea.right - cropArea.left) * imageWidth).toInt().coerceAtMost(imageWidth - cropX)
            val cropHeight = ((cropArea.bottom - cropArea.top) * imageHeight).toInt().coerceAtMost(imageHeight - cropY)

            Log.i(TAG, "Crop coordinates: x=$cropX, y=$cropY, w=$cropWidth, h=$cropHeight (from ${imageWidth}x${imageHeight})")

            // Crop the bitmap
            val croppedBitmap = Bitmap.createBitmap(
                fullBitmap,
                cropX,
                cropY,
                cropWidth,
                cropHeight
            )

            // Recycle full bitmap to free memory
            if (fullBitmap != croppedBitmap) {
                fullBitmap.recycle()
            }

            cameraContext?.debugLogger?.logPlugin(
                name,
                "crop_applied",
                mapOf(
                    "originalSize" to "${imageWidth}x${imageHeight}",
                    "cropArea" to "${cropWidth}x${cropHeight}",
                    "cropRatio" to cropArea.toString()
                )
            )

            Log.i(TAG, "✅ Crop applied successfully: ${cropWidth}x${cropHeight}")
            return croppedBitmap

        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply crop", e)
            return imageProxyToBitmap(image)
        }
    }

    /**
     * Convert ImageProxy to Bitmap
     */
    private fun imageProxyToBitmap(image: ImageProxy): Bitmap? {
        return try {
            when (image.format) {
                android.graphics.ImageFormat.JPEG -> {
                    // JPEG format - direct decode
                    val buffer = image.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining())
                    buffer.get(bytes)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }
                android.graphics.ImageFormat.YUV_420_888 -> {
                    // YUV format - convert to JPEG then decode
                    yuvImageToBitmap(image)
                }
                else -> {
                    Log.e(TAG, "Unsupported image format: ${image.format}")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to convert ImageProxy to Bitmap", e)
            null
        }
    }

    /**
     * Convert YUV ImageProxy to Bitmap
     */
    private fun yuvImageToBitmap(image: ImageProxy): Bitmap? {
        return try {
            val yBuffer = image.planes[0].buffer
            val uBuffer = image.planes[1].buffer
            val vBuffer = image.planes[2].buffer

            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()

            val nv21 = ByteArray(ySize + uSize + vSize)

            // Y plane
            yBuffer.get(nv21, 0, ySize)

            // U and V planes - interleave to NV21 format
            val uvPixelStride = image.planes[1].pixelStride
            if (uvPixelStride == 1) {
                // Tightly packed
                vBuffer.get(nv21, ySize, vSize)
                uBuffer.get(nv21, ySize + vSize, uSize)
            } else {
                // Interleaved
                val uvWidth = image.width / 2
                val uvHeight = image.height / 2
                var nv21Index = ySize

                for (row in 0 until uvHeight) {
                    for (col in 0 until uvWidth) {
                        val vIndex = row * image.planes[2].rowStride + col * uvPixelStride
                        val uIndex = row * image.planes[1].rowStride + col * uvPixelStride
                        nv21[nv21Index++] = vBuffer[vIndex]
                        nv21[nv21Index++] = uBuffer[uIndex]
                    }
                }
            }

            val yuvImage = YuvImage(nv21, android.graphics.ImageFormat.NV21, image.width, image.height, null)
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 100, out)
            val imageBytes = out.toByteArray()

            BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to convert YUV to Bitmap", e)
            null
        }
    }

    /**
     * Check if crop is currently enabled
     */
    fun isCropEnabled(): Boolean = isCropEnabled

    /**
     * Get current crop area
     */
    fun getCropArea(): RectF = RectF(cropArea)

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

            // Set up callback to sync crop area changes
            onCropAreaChanged = { newArea ->
                setCropArea(newArea)
            }
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

    companion object : com.customcamera.app.engine.plugins.PluginProvider {
        private const val TAG = "CropPlugin"

        // PluginProvider implementation
        override val id: String = "crop"

        override val displayNameRes: Int = com.customcamera.app.R.string.plugin_crop_display_name

        override val descriptionRes: Int = com.customcamera.app.R.string.plugin_crop_description

        override val iconResId: Int = com.customcamera.app.R.drawable.ic_camera

        override val category: com.customcamera.app.engine.plugins.PluginCategory =
            com.customcamera.app.engine.plugins.PluginCategory.CAPTURE

        override val userToggleable: Boolean = true

        override val showInDropdown: Boolean = true

        override fun isSupported(context: android.content.Context): Boolean {
            // TODO: Add device capability checking if needed
            return true
        }

        override fun create(dependencies: com.customcamera.app.engine.plugins.PluginDependencies): com.customcamera.app.engine.plugins.CameraPlugin {
            return CropPlugin()
        }
    }
}