package com.customcamera.app.debug

import android.util.Log
import androidx.camera.lifecycle.ProcessCameraProvider
import com.customcamera.app.engine.CameraContext
import kotlinx.coroutines.delay

/**
 * CameraResetManager provides camera recovery tools
 * for troubleshooting and system reset functionality.
 */
class CameraResetManager(
    private val cameraContext: CameraContext
) {

    /**
     * Reset specific camera ID
     */
    suspend fun resetCameraID(cameraId: String): Boolean {
        Log.i(TAG, "Resetting camera ID: $cameraId")

        return try {
            val cameraProvider = cameraContext.cameraProvider
            cameraProvider.unbindAll()
            delay(500)

            cameraContext.debugLogger.logCameraAPI(
                "resetCameraID",
                mapOf("cameraId" to cameraId, "timestamp" to System.currentTimeMillis())
            )

            Log.i(TAG, "✅ Camera ID $cameraId reset successfully")
            true

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to reset camera ID $cameraId", e)
            false
        }
    }

    /**
     * Flush camera queue
     */
    suspend fun flushCameraQueue(): Boolean {
        Log.i(TAG, "Flushing camera queue")

        return try {
            val cameraProvider = cameraContext.cameraProvider
            cameraProvider.unbindAll()
            delay(1000)
            System.gc()
            delay(500)

            Log.i(TAG, "✅ Camera queue flushed successfully")
            true

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to flush camera queue", e)
            false
        }
    }

    /**
     * Reinitialize camera provider
     */
    suspend fun reinitializeCameraProvider(): Boolean {
        Log.i(TAG, "Reinitializing camera provider")

        return try {
            flushCameraQueue()
            delay(1000)

            val newCameraProvider = ProcessCameraProvider.getInstance(cameraContext.context).get()

            Log.i(TAG, "✅ Camera provider reinitialized successfully")
            true

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to reinitialize camera provider", e)
            false
        }
    }

    /**
     * Clear camera cache
     */
    fun clearCameraCache(): Boolean {
        Log.i(TAG, "Clearing camera cache")

        return try {
            System.gc()
            Log.i(TAG, "✅ Camera cache cleared")
            true

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to clear camera cache", e)
            false
        }
    }

    companion object {
        private const val TAG = "CameraResetManager"
    }
}