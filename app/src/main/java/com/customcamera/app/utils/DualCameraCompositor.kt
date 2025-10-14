package com.customcamera.app.utils

import android.graphics.*
import android.graphics.ImageFormat
import android.graphics.YuvImage
import android.util.Log
import androidx.camera.core.ImageProxy
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer

/**
 * Utility class for compositing dual camera images
 * Combines main camera photo with PiP overlay
 */
object DualCameraCompositor {

    private const val TAG = "DualCameraCompositor"

    /**
     * Composite main camera image with PiP overlay image
     *
     * @param mainImage Main camera ImageProxy (will be base image)
     * @param pipImage PiP camera ImageProxy (will be overlaid)
     * @param pipRect Position and size of PiP overlay on screen (normalized 0-1)
     * @param outputFile File to save composite image
     * @return true if successful, false otherwise
     */
    fun compositeImages(
        mainImage: ImageProxy,
        pipImage: ImageProxy,
        pipRect: RectF, // Normalized coordinates (0-1)
        outputFile: File
    ): Boolean {
        return try {
            Log.i(TAG, "Starting dual camera composite...")
            Log.i(TAG, "Main image: ${mainImage.width}x${mainImage.height}, format: ${mainImage.format}")
            Log.i(TAG, "PiP image: ${pipImage.width}x${pipImage.height}, format: ${pipImage.format}")
            Log.i(TAG, "PiP rect: $pipRect")

            // Convert ImageProxy to Bitmap
            val mainBitmap = imageProxyToBitmap(mainImage)
            if (mainBitmap == null) {
                Log.e(TAG, "Failed to convert main image to bitmap")
                return false
            }
            Log.i(TAG, "Main bitmap created: ${mainBitmap.width}x${mainBitmap.height}")

            val pipBitmap = imageProxyToBitmap(pipImage)
            if (pipBitmap == null) {
                Log.e(TAG, "Failed to convert PiP image to bitmap")
                mainBitmap.recycle()
                return false
            }
            Log.i(TAG, "PiP bitmap created: ${pipBitmap.width}x${pipBitmap.height}")

            // Create composite
            val composite = Bitmap.createBitmap(
                mainBitmap.width,
                mainBitmap.height,
                Bitmap.Config.ARGB_8888
            )

            val canvas = Canvas(composite)

            // Draw main image as base
            canvas.drawBitmap(mainBitmap, 0f, 0f, null)

            // Calculate PiP position and size on the main image
            val pipLeft = pipRect.left * mainBitmap.width
            val pipTop = pipRect.top * mainBitmap.height
            val pipWidth = pipRect.width() * mainBitmap.width
            val pipHeight = pipRect.height() * mainBitmap.height

            val pipDestRect = RectF(
                pipLeft,
                pipTop,
                pipLeft + pipWidth,
                pipTop + pipHeight
            )

            // Draw PiP image on top with rounded corners and border
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val cornerRadius = pipWidth * 0.05f // 5% corner radius

            // Draw white border
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.STROKE
                strokeWidth = pipWidth * 0.02f // 2% border width
            }

            canvas.drawRoundRect(pipDestRect, cornerRadius, cornerRadius, borderPaint)

            // Draw PiP image with rounded corners
            val path = Path().apply {
                addRoundRect(pipDestRect, cornerRadius, cornerRadius, Path.Direction.CW)
            }
            canvas.save()
            canvas.clipPath(path)

            // Scale and draw PiP bitmap
            val pipSrcRect = Rect(0, 0, pipBitmap.width, pipBitmap.height)
            canvas.drawBitmap(pipBitmap, pipSrcRect, pipDestRect, paint)

            canvas.restore()

            // Save composite to file
            FileOutputStream(outputFile).use { out ->
                composite.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }

            // Clean up
            mainBitmap.recycle()
            pipBitmap.recycle()
            composite.recycle()

            Log.i(TAG, "✅ Dual camera composite saved to: ${outputFile.absolutePath}")
            true

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to composite images", e)
            false
        }
    }

    /**
     * Convert ImageProxy to Bitmap
     * Handles YUV_420_888 format from CameraX
     */
    private fun imageProxyToBitmap(image: ImageProxy): Bitmap? {
        return try {
            val yBuffer = image.planes[0].buffer // Y
            val vuBuffer = image.planes[2].buffer // V
            val uBuffer = image.planes[1].buffer // U

            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vuBuffer.remaining()

            val nv21 = ByteArray(ySize + uSize + vSize)

            // U and V are swapped
            yBuffer.get(nv21, 0, ySize)
            vuBuffer.get(nv21, ySize, vSize)
            uBuffer.get(nv21, ySize + vSize, uSize)

            val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
            val out = java.io.ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 95, out)
            val imageBytes = out.toByteArray()

            BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to convert ImageProxy to Bitmap", e)
            null
        }
    }
}
