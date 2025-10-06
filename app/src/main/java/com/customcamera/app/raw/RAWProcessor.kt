package com.customcamera.app.raw

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.DngCreator
import android.media.Image
import android.util.Log
import androidx.camera.core.ImageProxy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer

/**
 * RAWProcessor handles RAW/DNG image processing and conversion
 *
 * Features:
 * - DNG file creation from RAW sensor data
 * - RAW image metadata preservation
 * - RAW to JPEG tone mapping (preview generation)
 * - Professional RAW processing pipeline
 */
class RAWProcessor {

    /**
     * Convert RAW ImageProxy to DNG file
     *
     * @param rawImage RAW sensor image from camera
     * @param outputFile Output DNG file
     * @param characteristics Camera characteristics for DNG metadata
     * @param captureResult Capture result metadata
     * @return Success status
     */
    suspend fun convertToDng(
        rawImage: ImageProxy,
        outputFile: File,
        characteristics: CameraCharacteristics,
        captureResult: android.hardware.camera2.TotalCaptureResult
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val image: Image = rawImage.image
                    ?: return@withContext false.also {
                        Log.e(TAG, "RAW image is null")
                    }

                // Verify image format is RAW
                if (image.format != ImageFormat.RAW_SENSOR &&
                    image.format != ImageFormat.RAW10 &&
                    image.format != ImageFormat.RAW12) {
                    Log.e(TAG, "Image format is not RAW: ${image.format}")
                    return@withContext false
                }

                // Create DNG creator with camera characteristics and capture result
                val dngCreator = DngCreator(characteristics, captureResult)

                // Set DNG orientation based on image rotation
                val orientation = when (rawImage.imageInfo.rotationDegrees) {
                    0 -> android.media.ExifInterface.ORIENTATION_NORMAL
                    90 -> android.media.ExifInterface.ORIENTATION_ROTATE_90
                    180 -> android.media.ExifInterface.ORIENTATION_ROTATE_180
                    270 -> android.media.ExifInterface.ORIENTATION_ROTATE_270
                    else -> android.media.ExifInterface.ORIENTATION_NORMAL
                }
                dngCreator.setOrientation(orientation)

                // Set DNG description
                dngCreator.setDescription("CustomCamera RAW Capture")

                // Write DNG file
                FileOutputStream(outputFile).use { output ->
                    dngCreator.writeImage(output, image)
                }

                dngCreator.close()

                val fileSizeMB = outputFile.length() / (1024.0 * 1024.0)
                Log.i(TAG, "DNG file created: ${outputFile.name} (${String.format("%.2f", fileSizeMB)} MB)")

                true

            } catch (e: Exception) {
                Log.e(TAG, "Error converting to DNG", e)
                false
            }
        }
    }

    /**
     * Generate JPEG preview from RAW image
     * This uses a simple tone mapping approach for quick preview generation
     *
     * @param rawImage RAW sensor image
     * @param outputFile Output JPEG file
     * @param quality JPEG quality (0-100)
     * @return Success status
     */
    suspend fun generateJpegPreview(
        rawImage: ImageProxy,
        outputFile: File,
        quality: Int = 95
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val image: Image = rawImage.image ?: return@withContext false

                // Extract RAW data
                val buffer: ByteBuffer = image.planes[0].buffer
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)

                // Simple RAW to RGB conversion (basic tone mapping)
                // In production, you'd want more sophisticated demosaicing and tone mapping
                val preview = applySimpleToneMapping(bytes, image.width, image.height)

                // Save as JPEG
                FileOutputStream(outputFile).use { output ->
                    preview.compress(Bitmap.CompressFormat.JPEG, quality, output)
                }

                Log.i(TAG, "JPEG preview created: ${outputFile.name}")
                true

            } catch (e: Exception) {
                Log.e(TAG, "Error generating JPEG preview", e)
                false
            }
        }
    }

    /**
     * Simple tone mapping for RAW preview
     * This is a basic implementation - production would use advanced algorithms
     */
    private fun applySimpleToneMapping(
        rawData: ByteArray,
        width: Int,
        height: Int
    ): Bitmap {
        // Create bitmap for preview
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        // Simple linear tone mapping
        // In production, use proper demosaicing, white balance, and gamma correction
        val pixels = IntArray(width * height)

        for (i in pixels.indices) {
            // Convert 16-bit RAW to 8-bit RGB (very simplified)
            val rawValue = if (i * 2 + 1 < rawData.size) {
                ((rawData[i * 2].toInt() and 0xFF) or
                 ((rawData[i * 2 + 1].toInt() and 0xFF) shl 8))
            } else {
                0
            }

            // Simple linear mapping from 16-bit to 8-bit
            val mapped = (rawValue shr 8).coerceIn(0, 255)

            // Create grayscale pixel (simplified - no Bayer pattern handling)
            pixels[i] = (0xFF shl 24) or (mapped shl 16) or (mapped shl 8) or mapped
        }

        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    /**
     * Get RAW image statistics
     */
    fun getRawImageStats(rawImage: ImageProxy): RawImageStats {
        val image: Image? = rawImage.image

        return RawImageStats(
            width = image?.width ?: 0,
            height = image?.height ?: 0,
            format = image?.format ?: 0,
            formatName = getFormatName(image?.format ?: 0),
            timestamp = rawImage.imageInfo.timestamp,
            rotation = rawImage.imageInfo.rotationDegrees,
            planeCount = image?.planes?.size ?: 0,
            estimatedSizeBytes = estimateRawSize(image)
        )
    }

    private fun getFormatName(format: Int): String {
        return when (format) {
            ImageFormat.RAW_SENSOR -> "RAW_SENSOR"
            ImageFormat.RAW10 -> "RAW10"
            ImageFormat.RAW12 -> "RAW12"
            ImageFormat.RAW_PRIVATE -> "RAW_PRIVATE"
            else -> "UNKNOWN ($format)"
        }
    }

    private fun estimateRawSize(image: Image?): Long {
        image ?: return 0L

        var totalSize = 0L
        image.planes.forEach { plane ->
            totalSize += plane.buffer.capacity()
        }

        return totalSize
    }

    /**
     * Validate RAW image format
     */
    fun isValidRawFormat(imageFormat: Int): Boolean {
        return imageFormat == ImageFormat.RAW_SENSOR ||
               imageFormat == ImageFormat.RAW10 ||
               imageFormat == ImageFormat.RAW12 ||
               imageFormat == ImageFormat.RAW_PRIVATE
    }

    companion object {
        private const val TAG = "RAWProcessor"
    }
}

/**
 * RAW image statistics
 */
data class RawImageStats(
    val width: Int,
    val height: Int,
    val format: Int,
    val formatName: String,
    val timestamp: Long,
    val rotation: Int,
    val planeCount: Int,
    val estimatedSizeBytes: Long
) {
    val megapixels: Double
        get() = (width * height) / 1_000_000.0

    val estimatedSizeMB: Double
        get() = estimatedSizeBytes / (1024.0 * 1024.0)

    override fun toString(): String {
        return "RAW Image: ${width}x${height} (${String.format("%.1f", megapixels)}MP), " +
               "$formatName, ${String.format("%.2f", estimatedSizeMB)}MB, " +
               "${planeCount} planes, ${rotation}° rotation"
    }
}
