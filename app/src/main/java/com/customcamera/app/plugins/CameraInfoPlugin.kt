package com.customcamera.app.plugins

import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.ImageProxy
import com.customcamera.app.engine.CameraContext
import com.customcamera.app.engine.plugins.ProcessingPlugin
import com.customcamera.app.engine.plugins.ProcessingResult
import com.customcamera.app.engine.plugins.ProcessingMetadata

/**
 * CameraInfoPlugin processes camera frames to extract and log camera information.
 * Demonstrates ProcessingPlugin functionality with frame analysis.
 */
class CameraInfoPlugin : ProcessingPlugin() {

    override val name: String = "CameraInfo"
    override val version: String = "1.0.0"
    override val priority: Int = 90 // Lower priority - informational only

    private var cameraContext: CameraContext? = null
    private var frameCount: Long = 0
    private var lastProcessingTime: Long = 0
    private var processingInterval: Long = 1000 // Process every 1 second

    // Camera information tracking
    private var currentCameraId: String = ""
    private var cameraCharacteristics: Map<String, Any> = emptyMap()

    override suspend fun initialize(context: CameraContext) {
        this.cameraContext = context
        Log.i(TAG, "CameraInfoPlugin initialized")

        // Load processing interval from settings
        val intervalString = context.settingsManager.getPluginSetting(
            name,
            "processingInterval",
            "1000"
        )

        try {
            processingInterval = intervalString.toLong()
        } catch (e: Exception) {
            Log.w(TAG, "Invalid processing interval, using default", e)
            processingInterval = 1000
        }

        context.debugLogger.logPlugin(
            name,
            "initialized",
            mapOf("processingInterval" to processingInterval)
        )
    }

    override suspend fun onCameraReady(camera: Camera) {
        Log.i(TAG, "Camera ready, collecting camera information")

        // Extract camera information
        val cameraInfo = camera.cameraInfo
        currentCameraId = extractCameraId(cameraInfo)

        cameraCharacteristics = mapOf(
            "cameraId" to currentCameraId,
            "lensFacing" to when (cameraInfo.lensFacing) {
                androidx.camera.core.CameraSelector.LENS_FACING_FRONT -> "Front"
                androidx.camera.core.CameraSelector.LENS_FACING_BACK -> "Back"
                else -> "External"
            },
            "hasFlashUnit" to cameraInfo.hasFlashUnit(),
            "sensorRotationDegrees" to cameraInfo.sensorRotationDegrees,
            "implementationType" to cameraInfo.implementationType
        )

        // Log camera information
        cameraContext?.debugLogger?.logPlugin(
            name,
            "camera_info_collected",
            cameraCharacteristics
        )

        Log.i(TAG, "Camera information: $cameraCharacteristics")
        frameCount = 0
    }

    override suspend fun onCameraReleased(camera: Camera) {
        Log.i(TAG, "Camera released, final frame count: $frameCount")

        cameraContext?.debugLogger?.logPlugin(
            name,
            "camera_released",
            mapOf(
                "finalFrameCount" to frameCount,
                "cameraId" to currentCameraId
            )
        )

        frameCount = 0
        cameraCharacteristics = emptyMap()
        currentCameraId = ""
    }

    override suspend fun processFrame(image: ImageProxy): ProcessingResult {
        val currentTime = System.currentTimeMillis()

        // Only process at specified intervals to avoid performance impact
        if (currentTime - lastProcessingTime < processingInterval) {
            return ProcessingResult.Skip
        }

        lastProcessingTime = currentTime
        frameCount++

        return try {
            val frameInfo = analyzeFrame(image)
            val metadata = ProcessingMetadata(
                timestamp = currentTime,
                processingTimeMs = System.currentTimeMillis() - currentTime,
                frameNumber = frameCount,
                imageSize = android.util.Size(image.width, image.height),
                additionalData = frameInfo
            )

            // Log frame information periodically
            if (frameCount % 30 == 0L) { // Every 30 frames
                cameraContext?.debugLogger?.logPlugin(
                    name,
                    "frame_processed",
                    mapOf(
                        "frameNumber" to frameCount,
                        "imageSize" to "${image.width}x${image.height}",
                        "format" to image.format,
                        "timestamp" to image.imageInfo.timestamp
                    )
                )
            }

            ProcessingResult.Success(
                data = frameInfo,
                metadata = metadata
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error processing frame", e)
            ProcessingResult.Failure("Frame processing error: ${e.message}", e)
        }
    }

    /**
     * Get current camera information
     */
    fun getCameraInfo(): Map<String, Any> {
        return cameraCharacteristics + mapOf(
            "frameCount" to frameCount,
            "lastProcessingTime" to lastProcessingTime,
            "processingInterval" to processingInterval
        )
    }

    /**
     * Get processing statistics
     */
    fun getProcessingStats(): Map<String, Any> {
        return mapOf(
            "totalFrames" to frameCount,
            "processingInterval" to processingInterval,
            "lastProcessingTime" to lastProcessingTime,
            "isEnabled" to isEnabled
        )
    }

    /**
     * Update processing interval
     */
    fun setProcessingInterval(intervalMs: Long) {
        if (intervalMs > 0) {
            processingInterval = intervalMs
            cameraContext?.settingsManager?.setPluginSetting(
                name,
                "processingInterval",
                intervalMs.toString()
            )
            Log.i(TAG, "Processing interval updated to: ${intervalMs}ms")
        }
    }

    override fun cleanup() {
        Log.i(TAG, "Cleaning up CameraInfoPlugin")

        cameraContext?.debugLogger?.logPlugin(
            name,
            "cleanup",
            mapOf("finalFrameCount" to frameCount)
        )

        cameraContext = null
        frameCount = 0
        cameraCharacteristics = emptyMap()
        currentCameraId = ""
    }

    private fun analyzeFrame(image: ImageProxy): Map<String, Any> {
        // Extract frame information
        val frameInfo = mutableMapOf<String, Any>(
            "width" to image.width,
            "height" to image.height,
            "format" to image.format,
            "timestamp" to image.imageInfo.timestamp,
            "rotationDegrees" to image.imageInfo.rotationDegrees
        )

        // Add plane information if available
        try {
            val planes = image.planes
            frameInfo["planeCount"] = planes.size
            if (planes.isNotEmpty()) {
                frameInfo["pixelStride"] = planes[0].pixelStride
                frameInfo["rowStride"] = planes[0].rowStride
                frameInfo["bufferSize"] = planes[0].buffer.remaining()
            }
        } catch (e: Exception) {
            Log.d(TAG, "Could not extract plane information", e)
        }

        return frameInfo
    }

    private fun extractCameraId(cameraInfo: androidx.camera.core.CameraInfo): String {
        // Extract a meaningful camera identifier
        return try {
            // This is a simplified approach - in production you might want more sophisticated ID extraction
            val facing = when (cameraInfo.lensFacing) {
                androidx.camera.core.CameraSelector.LENS_FACING_FRONT -> "front"
                androidx.camera.core.CameraSelector.LENS_FACING_BACK -> "back"
                else -> "external"
            }
            "${facing}_camera_${cameraInfo.hashCode()}"
        } catch (e: Exception) {
            Log.w(TAG, "Could not extract camera ID", e)
            "camera_unknown"
        }
    }

    companion object {
        private const val TAG = "CameraInfoPlugin"
    }
}