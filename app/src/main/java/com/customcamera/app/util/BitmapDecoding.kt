package com.customcamera.app.util

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.File
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Memory-safe image decoding that honours EXIF orientation.
 *
 * API 28+ uses ImageDecoder (applies orientation itself and downsamples in the
 * decoder). Older versions sample with BitmapFactory and rotate via ExifInterface.
 */
object BitmapDecoding {

    /**
     * Pixel cap for full-resolution decodes (≈50 MP). Bitmap pixels live in native
     * memory on API 26+, so the Java heap limit does not apply, but an ARGB_8888
     * bitmap still costs 4 bytes per pixel: 50 MP is ~200 MB, and an edit briefly
     * holds the cropped copy as well. Larger images (e.g. 200 MP sensor modes) are
     * downsampled to stay under the cap instead of risking an OOM kill.
     */
    const val MAX_FULL_RESOLUTION_PIXELS = 50_000_000L

    /** Decodes for display: the long side is scaled down to about [maxDimension]. */
    fun decode(resolver: ContentResolver, uri: Uri, maxDimension: Int): Bitmap? =
        decode(resolver, uri, MaxDimension(maxDimension))

    fun decode(file: File, maxDimension: Int): Bitmap? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            decodeWithImageDecoder(ImageDecoder.createSource(file), MaxDimension(maxDimension))
        } else {
            legacyDecode(open = { file.inputStream() }, limit = MaxDimension(maxDimension))
        }

    /**
     * Decodes at full resolution for re-encoding (photo editor), honouring EXIF
     * orientation. Only images above [maxPixels] are downsampled — see
     * [MAX_FULL_RESOLUTION_PIXELS]. Returns a mutable-safe software bitmap or null.
     */
    fun decodeFullResolution(
        resolver: ContentResolver,
        uri: Uri,
        maxPixels: Long = MAX_FULL_RESOLUTION_PIXELS,
    ): Bitmap? = decode(resolver, uri, MaxPixels(maxPixels))

    private fun decode(resolver: ContentResolver, uri: Uri, limit: SizeLimit): Bitmap? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            decodeWithImageDecoder(ImageDecoder.createSource(resolver, uri), limit)
        } else {
            legacyDecode(open = { resolver.openInputStream(uri) }, limit = limit)
        }

    /** How far a decode may be scaled down for a source of the given size. */
    private sealed interface SizeLimit {
        /** Exact scale factor (≤ 1) for decoders that resample freely. */
        fun scale(width: Int, height: Int): Float

        /** Power-of-two BitmapFactory sample size. */
        fun sampleSize(width: Int, height: Int): Int
    }

    /** Display decode: fit the long side to [maxDimension]; never sample below it. */
    private class MaxDimension(private val maxDimension: Int) : SizeLimit {
        override fun scale(width: Int, height: Int): Float {
            val largest = max(width, height)
            return if (largest > maxDimension) maxDimension.toFloat() / largest else 1f
        }

        override fun sampleSize(width: Int, height: Int): Int {
            var sample = 1
            while (max(width, height) / (sample * 2) >= maxDimension) sample *= 2
            return sample
        }
    }

    /** Memory cap: the decoded pixel count must not exceed [maxPixels]. */
    private class MaxPixels(private val maxPixels: Long) : SizeLimit {
        override fun scale(width: Int, height: Int): Float {
            val pixels = width.toLong() * height
            return if (pixels > maxPixels) sqrt(maxPixels.toDouble() / pixels).toFloat() else 1f
        }

        // Smallest power of two that brings the count under the cap (a hard bound, so round up).
        override fun sampleSize(width: Int, height: Int): Int {
            var sample = 1
            while (width.toLong() * height / (sample.toLong() * sample) > maxPixels) sample *= 2
            return sample
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun decodeWithImageDecoder(source: ImageDecoder.Source, limit: SizeLimit): Bitmap? = try {
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val scale = limit.scale(info.size.width, info.size.height)
            if (scale < 1f) {
                decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1), (info.size.height * scale).toInt().coerceAtLeast(1))
            }
            // Software allocation keeps the bitmap usable by Matrix-based views and
            // Bitmap.createBitmap/compress on every device.
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    } catch (e: Exception) {
        null
    }

    private fun legacyDecode(open: () -> java.io.InputStream?, limit: SizeLimit): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open()?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val sample = if (bounds.outWidth > 0 && bounds.outHeight > 0) limit.sampleSize(bounds.outWidth, bounds.outHeight) else 1
        val bitmap = open()?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
        val orientation = open()?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
            ?: ExifInterface.ORIENTATION_NORMAL
        bitmap?.let { rotate(it, orientation) }
    } catch (e: Exception) {
        null
    }

    /** Applies an EXIF orientation; the unrotated input is recycled when a copy was made. */
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
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        // Free the unrotated pixels now: at full resolution two copies may not fit.
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }
}
