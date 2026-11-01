package com.customcamera.app.util

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import java.io.File
import kotlin.math.max

/**
 * Memory-safe image decoding that honours EXIF orientation.
 *
 * API 28+ uses ImageDecoder (applies orientation itself and downsamples in the
 * decoder). Older versions sample with BitmapFactory and rotate via ExifInterface.
 */
object BitmapDecoding {

    fun decode(resolver: ContentResolver, uri: Uri, maxDimension: Int): Bitmap? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            decodeWithImageDecoder(ImageDecoder.createSource(resolver, uri), maxDimension)
        } else {
            legacyDecode(
                open = { resolver.openInputStream(uri) },
                maxDimension = maxDimension,
            )
        }

    fun decode(file: File, maxDimension: Int): Bitmap? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            decodeWithImageDecoder(ImageDecoder.createSource(file), maxDimension)
        } else {
            legacyDecode(open = { file.inputStream() }, maxDimension = maxDimension)
        }

    private fun decodeWithImageDecoder(source: ImageDecoder.Source, maxDimension: Int): Bitmap? = try {
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val largest = max(info.size.width, info.size.height)
            if (largest > maxDimension) {
                val scale = maxDimension.toFloat() / largest
                decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1), (info.size.height * scale).toInt().coerceAtLeast(1))
            }
            // Software allocation keeps the bitmap usable by Matrix-based views on every device.
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    } catch (e: Exception) {
        null
    }

    private fun legacyDecode(open: () -> java.io.InputStream?, maxDimension: Int): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open()?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxDimension) sample *= 2
        val bitmap = open()?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
        val orientation = open()?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
            ?: ExifInterface.ORIENTATION_NORMAL
        bitmap?.let { rotate(it, orientation) }
    } catch (e: Exception) {
        null
    }

    private fun rotate(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.postRotate(90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.postRotate(270f); matrix.postScale(-1f, 1f) }
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
