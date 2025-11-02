package com.customcamera.app.camera

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.DngCreator
import android.hardware.camera2.CaptureResult
import android.media.Image
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * DNG Writer for RAW Image Capture
 *
 * Handles writing RAW sensor images to DNG format with proper metadata embedding.
 * Uses timestamp-based pairing to match Image data with TotalCaptureResult metadata.
 *
 * Thread-safe implementation using single-threaded executor to serialize all operations.
 */
class DngWriter(
    private val context: Context,
    private val outputDir: File,
    private val executor: Executor = Executors.newSingleThreadExecutor()
) {

    companion object {
        private const val TAG = "DngWriter"
        private const val TIMEOUT_MS = 3000L // 3 seconds timeout for pairing
    }

    // Timestamp-based pairing maps
    private val captureResults = mutableMapOf<Long, Pair<TotalCaptureResult, CameraCharacteristics>>()
    private val pendingImages = mutableMapOf<Long, Image>()

    // Statistics
    private var filesWritten = 0
    private var pairingFailures = 0

    /**
     * Add TotalCaptureResult metadata for timestamp-based pairing
     * Called from Camera2 CaptureCallback
     */
    fun addCaptureResult(
        timestamp: Long,
        result: TotalCaptureResult,
        characteristics: CameraCharacteristics
    ) {
        executor.execute {
            try {
                val image = pendingImages.remove(timestamp)
                if (image != null) {
                    // We have a match, write the DNG immediately
                    Log.i(TAG, "Matched TotalCaptureResult with Image for timestamp: $timestamp")
                    writeDngFile(image, result, characteristics)
                    image.close()
                } else {
                    // No image yet, store the result and wait
                    Log.d(TAG, "Storing TotalCaptureResult for timestamp: $timestamp")
                    captureResults[timestamp] = Pair(result, characteristics)

                    // Schedule timeout cleanup
                    scheduleTimeoutCleanup(timestamp)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error adding capture result for timestamp $timestamp", e)
            }
        }
    }

    /**
     * Write RAW Image with timestamp-based pairing
     * Called from ImageReader onImageAvailableListener
     */
    fun writeImage(image: Image) {
        executor.execute {
            try {
                val timestamp = image.timestamp
                val metadata = captureResults.remove(timestamp)

                if (metadata != null) {
                    // We have a match, write the DNG immediately
                    Log.i(TAG, "Matched Image with TotalCaptureResult for timestamp: $timestamp")
                    writeDngFile(image, metadata.first, metadata.second)
                    image.close()
                } else {
                    // No metadata yet, store the image and wait
                    Log.d(TAG, "Storing Image for timestamp: $timestamp")
                    pendingImages[timestamp] = image

                    // Schedule timeout cleanup
                    scheduleTimeoutCleanup(timestamp)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error writing image with timestamp ${image.timestamp}", e)
                image.close() // Ensure cleanup on error
            }
        }
    }

    /**
     * Write DNG file with Image data and metadata
     */
    private fun writeDngFile(
        image: Image,
        captureResult: TotalCaptureResult,
        characteristics: CameraCharacteristics
    ): Uri? {
        return try {
            // Generate output file name with timestamp
            val timestamp = System.currentTimeMillis()
            val dngFile = File(outputDir, "RAW_${timestamp}.dng")

            Log.i(TAG, "Writing DNG file: ${dngFile.absolutePath}")

            // Create DNG creator with camera characteristics and capture result
            val dngCreator = DngCreator(characteristics, captureResult)

            // Set orientation if available
            val orientation = captureResult.get(CaptureResult.JPEG_ORIENTATION)
            if (orientation != null) {
                dngCreator.setOrientation(orientation)
            }

            // Set location if available (requires location permission)
            val location = captureResult.get(CaptureResult.JPEG_GPS_LOCATION)
            if (location != null) {
                dngCreator.setLocation(location)
            }

            // Write DNG file
            FileOutputStream(dngFile).use { outputStream ->
                dngCreator.writeImage(outputStream, image)
            }

            dngCreator.close()

            filesWritten++
            Log.i(TAG, "DNG file written successfully: ${dngFile.name} (total: $filesWritten)")

            // Return content URI using FileProvider
            try {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    dngFile
                )
            } catch (e: Exception) {
                Log.w(TAG, "Could not create content URI, returning file URI", e)
                Uri.fromFile(dngFile)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error writing DNG file", e)
            null
        }
    }

    /**
     * Schedule cleanup of unpaired data after timeout
     */
    private fun scheduleTimeoutCleanup(timestamp: Long) {
        executor.execute {
            try {
                Thread.sleep(TIMEOUT_MS)

                // Check if pairing failed (data still in maps after timeout)
                val orphanedResult = captureResults.remove(timestamp)
                val orphanedImage = pendingImages.remove(timestamp)

                if (orphanedResult != null || orphanedImage != null) {
                    pairingFailures++
                    Log.w(TAG, "Pairing timeout for timestamp $timestamp (failures: $pairingFailures)")

                    // Clean up orphaned image to prevent memory leak
                    orphanedImage?.close()
                }
            } catch (e: InterruptedException) {
                Log.d(TAG, "Timeout cleanup interrupted for timestamp $timestamp")
            } catch (e: Exception) {
                Log.e(TAG, "Error in timeout cleanup", e)
            }
        }
    }

    /**
     * Get DNG writing statistics
     */
    fun getStats(): Map<String, Any> {
        return mapOf(
            "filesWritten" to filesWritten,
            "pairingFailures" to pairingFailures,
            "pendingImages" to pendingImages.size,
            "pendingResults" to captureResults.size
        )
    }

    /**
     * Cleanup resources
     * Must be called when DNG writer is no longer needed
     */
    fun cleanup() {
        executor.execute {
            Log.i(TAG, "Cleaning up DngWriter")

            // Close all pending images to prevent memory leaks
            pendingImages.values.forEach { image ->
                try {
                    image.close()
                } catch (e: Exception) {
                    Log.e(TAG, "Error closing pending image", e)
                }
            }

            pendingImages.clear()
            captureResults.clear()

            Log.i(TAG, "DngWriter cleanup complete. Stats: $filesWritten files written, $pairingFailures failures")
        }
    }
}
