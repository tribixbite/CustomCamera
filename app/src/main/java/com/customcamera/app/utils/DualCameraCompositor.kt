package com.customcamera.app.utils

import android.content.ContentResolver
import android.content.ContentValues
import android.graphics.*
import android.graphics.ImageFormat
import android.graphics.YuvImage
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
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
     * Composite dual camera images using Bitmap for PiP (Preview-based capture)
     * Optimized for PreviewView.bitmap approach to reduce hardware use cases
     *
     * @param mainImage Main camera ImageProxy
     * @param pipBitmap PiP camera Bitmap from PreviewView
     * @param pipRect Position and size of PiP overlay on screen (normalized 0-1)
     * @param outputFile File to save composite image
     * @return true if successful, false otherwise
     */
    fun compositeImages(
        mainImage: ImageProxy,
        pipBitmap: Bitmap,
        pipRect: RectF,
        outputFile: File
    ): Boolean {
        return try {
            Log.i(TAG, "Starting dual camera composite (PreviewView-based PiP)...")
            Log.i(TAG, "Main image: ${mainImage.width}x${mainImage.height}, format: ${mainImage.format}")
            Log.i(TAG, "PiP bitmap: ${pipBitmap.width}x${pipBitmap.height}")
            Log.i(TAG, "PiP rect: $pipRect")

            // Convert main ImageProxy to Bitmap
            val mainBitmap = imageProxyToBitmap(mainImage)
            if (mainBitmap == null) {
                Log.e(TAG, "Failed to convert main image to bitmap")
                return false
            }
            Log.i(TAG, "Main bitmap created: ${mainBitmap.width}x${mainBitmap.height}")

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
            composite.recycle()

            Log.i(TAG, "✅ Dual camera composite saved to: ${outputFile.absolutePath}")
            true

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to composite images", e)
            false
        }
    }

    /**
     * Composite dual camera images using Bitmap for PiP and save to MediaStore URI
     * MediaStore-compatible version for Android 10+ (API 29+)
     *
     * @param mainImage Main camera ImageProxy
     * @param pipBitmap PiP camera Bitmap from PreviewView
     * @param pipRect Position and size of PiP overlay on screen (normalized 0-1)
     * @param contentResolver ContentResolver for MediaStore access
     * @param imageUri MediaStore URI to save composite image
     * @param contentValues ContentValues with image metadata
     * @return true if successful, false otherwise
     */
    fun compositeImagesToUri(
        mainImage: ImageProxy,
        pipBitmap: Bitmap,
        pipRect: RectF,
        contentResolver: ContentResolver,
        imageUri: Uri,
        contentValues: ContentValues
    ): Boolean {
        return try {
            Log.i(TAG, "Starting dual camera composite to MediaStore URI...")
            Log.i(TAG, "Main image: ${mainImage.width}x${mainImage.height}, format: ${mainImage.format}")
            Log.i(TAG, "PiP bitmap: ${pipBitmap.width}x${pipBitmap.height}")
            Log.i(TAG, "PiP rect: $pipRect")

            // Convert main ImageProxy to Bitmap
            val mainBitmap = imageProxyToBitmap(mainImage)
            if (mainBitmap == null) {
                Log.e(TAG, "Failed to convert main image to bitmap")
                return false
            }
            Log.i(TAG, "Main bitmap created: ${mainBitmap.width}x${mainBitmap.height}")

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

            // Save composite to MediaStore URI
            contentResolver.openOutputStream(imageUri)?.use { out ->
                composite.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }

            // Mark as complete (remove IS_PENDING flag)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                contentResolver.update(imageUri, contentValues, null, null)
            }

            // Clean up
            mainBitmap.recycle()
            composite.recycle()

            Log.i(TAG, "✅ Dual camera composite saved to MediaStore: $imageUri")
            true

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to composite images to MediaStore", e)
            false
        }
    }

    /**
     * Convert ImageProxy to Bitmap
     * Handles YUV_420_888 format from CameraX
     */
    private fun imageProxyToBitmap(image: ImageProxy): Bitmap? {
        return try {
            val planes = image.planes

            // Check if image is JPEG format (format 256, single plane)
            if (image.format == android.graphics.ImageFormat.JPEG || planes.size == 1) {
                Log.d(TAG, "Converting JPEG image (format: ${image.format}, planes: ${planes.size})")
                val buffer = planes[0].buffer
                buffer.rewind()
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }

            // YUV_420_888 format: planes[0] = Y, planes[1] = U, planes[2] = V
            if (planes.size < 3) {
                Log.e(TAG, "Unexpected plane count: ${planes.size} for format ${image.format}")
                return null
            }

            val yPlane = planes[0]
            val uPlane = planes[1]
            val vPlane = planes[2]

            val yBuffer = yPlane.buffer
            val uBuffer = uPlane.buffer
            val vBuffer = vPlane.buffer

            // Get buffer sizes
            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()

            // Create NV21 byte array (Y followed by interleaved VU)
            val nv21 = ByteArray(ySize + uSize + vSize)

            // Copy Y plane
            yBuffer.position(0) // Reset buffer position
            yBuffer.get(nv21, 0, ySize)
            Log.d(TAG, "Copied Y plane: $ySize bytes")

            // Get row and pixel strides
            val yRowStride = yPlane.rowStride
            val yPixelStride = yPlane.pixelStride
            val uvRowStride = uPlane.rowStride
            val uvPixelStride = uPlane.pixelStride

            Log.d(TAG, "Image: ${image.width}x${image.height}, Y stride: $yRowStride/$yPixelStride, UV stride: $uvRowStride/$uvPixelStride")
            Log.d(TAG, "Buffer sizes: Y=$ySize, U=$uSize, V=$vSize, NV21=${nv21.size}")

            // Reset UV buffer positions
            uBuffer.position(0)
            vBuffer.position(0)

            // If pixel stride is 1, we can do bulk copy
            if (uvPixelStride == 1) {
                // Simple case: tightly packed UV data
                Log.d(TAG, "Using bulk copy for UV planes (pixelStride=1)")
                vBuffer.get(nv21, ySize, vSize)
                uBuffer.get(nv21, ySize + vSize, uSize)
                Log.d(TAG, "Copied V plane: $vSize bytes, U plane: $uSize bytes")
            } else {
                // Complex case: need to interleave VU manually
                Log.d(TAG, "Using manual interleaving for UV planes (pixelStride=$uvPixelStride)")
                val uvWidth = image.width / 2
                val uvHeight = image.height / 2

                var nv21Index = ySize
                for (row in 0 until uvHeight) {
                    for (col in 0 until uvWidth) {
                        val vIndex = row * uvRowStride + col * uvPixelStride
                        val uIndex = row * uvRowStride + col * uvPixelStride

                        if (vIndex < vSize && uIndex < uSize) {
                            nv21[nv21Index++] = vBuffer[vIndex]
                            nv21[nv21Index++] = uBuffer[uIndex]
                        } else {
                            Log.e(TAG, "Buffer index out of bounds: vIndex=$vIndex/$vSize, uIndex=$uIndex/$uSize at row=$row, col=$col")
                            break
                        }
                    }
                }
                Log.d(TAG, "Interleaved UV planes: wrote ${nv21Index - ySize} bytes")
            }

            // Convert NV21 to Bitmap via YuvImage
            val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
            val out = java.io.ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 95, out)
            val imageBytes = out.toByteArray()

            BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to convert ImageProxy to Bitmap", e)
            Log.e(TAG, "Image format: ${image.format}, size: ${image.width}x${image.height}")
            null
        }
    }
}
