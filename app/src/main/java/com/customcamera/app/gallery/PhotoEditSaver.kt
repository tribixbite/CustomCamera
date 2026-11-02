package com.customcamera.app.gallery

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import android.provider.MediaStore
import androidx.annotation.WorkerThread
import androidx.exifinterface.media.ExifInterface
import com.customcamera.app.core.CropMath
import com.customcamera.app.core.PhotoEdit
import com.customcamera.app.settings.AppSettings
import com.customcamera.app.util.BitmapDecoding
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Renders a [PhotoEdit] at full resolution and saves it as a NEW photo in
 * DCIM/CustomCamera. The original is only ever opened for reading.
 *
 * Pipeline: decode (EXIF orientation applied, ≤50 MP) → crop + rotate/mirror in one
 * Bitmap.createBitmap call (so only the cropped result is allocated) → JPEG at the
 * user's quality into a private temp file → copy capture metadata into its EXIF →
 * publish to MediaStore. EXIF is written on the temp file because ExifInterface can
 * only rewrite a seekable file, which a MediaStore output stream is not.
 */
class PhotoEditSaver(context: Context) {

    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver

    /**
     * Full-resolution cap for this device. A decode plus its cropped/rotated copy
     * can briefly need ~8 bytes per pixel (twice that on API 26–27, where EXIF
     * rotation makes an extra copy), so low-RAM devices and small app memory
     * classes get a lower cap than [BitmapDecoding.MAX_FULL_RESOLUTION_PIXELS].
     */
    private fun pixelBudget(): Long {
        val am = appContext.getSystemService(android.app.ActivityManager::class.java)
        return when {
            am == null -> BitmapDecoding.MAX_FULL_RESOLUTION_PIXELS
            am.isLowRamDevice || am.largeMemoryClass < LARGE_MEMORY_CLASS_MB -> LOW_MEMORY_PIXELS
            else -> BitmapDecoding.MAX_FULL_RESOLUTION_PIXELS
        }
    }

    /** Saves [edit] of [source] and returns the new item's Uri. Throws on failure. */
    @WorkerThread
    fun save(source: Uri, edit: PhotoEdit): Uri {
        val decoded = BitmapDecoding.decodeFullResolution(resolver, source, maxPixels = pixelBudget())
            ?: throw IOException("Cannot decode $source")
        val rendered = try {
            render(decoded, edit)
        } catch (e: Throwable) {
            decoded.recycle()
            throw e
        }
        // createBitmap hands back its input when nothing changes; only free a real copy.
        if (rendered !== decoded) decoded.recycle()
        val temp = File.createTempFile("edit", ".jpg", File(appContext.cacheDir, TEMP_DIR).apply { mkdirs() })
        val width = rendered.width
        val height = rendered.height
        try {
            try {
                temp.outputStream().use { out ->
                    if (!rendered.compress(Bitmap.CompressFormat.JPEG, AppSettings(appContext).jpegQuality, out)) {
                        throw IOException("JPEG encoding failed")
                    }
                }
            } finally {
                rendered.recycle()
            }
            writeExif(source, temp, width, height)

            val target = MediaStoreHelper.editTarget(Date(), originalDateTaken(source))
            return MediaStoreHelper.publish(resolver, target) { out -> temp.inputStream().use { it.copyTo(out) } }
        } finally {
            temp.delete()
        }
    }

    /** Crops the source to the edit's pixel rect and applies its rotation/mirror. */
    private fun render(source: Bitmap, edit: PhotoEdit): Bitmap {
        val (dw, dh) = edit.transform.displaySize(source.width, source.height)
        val rect = CropMath.toSourcePixels(
            normalized = edit.crop,
            transform = edit.transform,
            sourceWidth = source.width,
            sourceHeight = source.height,
            ratio = edit.aspect.ratio(dw.toFloat(), dh.toFloat()),
        )
        // Same order as EditTransform: rotate clockwise, then mirror in display space.
        val matrix = Matrix().apply {
            postRotate(edit.transform.rotation.toFloat())
            if (edit.transform.flipped) postScale(-1f, 1f)
        }
        return Bitmap.createBitmap(source, rect.left, rect.top, rect.width, rect.height, matrix, true)
    }

    /**
     * Copies capture metadata (time, location, camera, exposure) from the original,
     * then records the new size and a NORMAL orientation: the pixels are already
     * upright, so the original's orientation tag would rotate them a second time.
     */
    // The stream is closed by `?.use {}`; lint's Recycle check misreads the safe call.
    @SuppressLint("Recycle")
    private fun writeExif(source: Uri, file: File, width: Int, height: Int) {
        val original = runCatching { resolver.openInputStream(source)?.use { ExifInterface(it) } }.getOrNull()
        val exif = ExifInterface(file)
        if (original != null) {
            for (tag in COPIED_TAGS) original.getAttribute(tag)?.let { exif.setAttribute(tag, it) }
        }
        exif.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
        exif.setAttribute(ExifInterface.TAG_PIXEL_X_DIMENSION, width.toString())
        exif.setAttribute(ExifInterface.TAG_PIXEL_Y_DIMENSION, height.toString())
        // DateTime is the file's modification time; DateTimeOriginal (copied) stays the capture time.
        exif.setAttribute(ExifInterface.TAG_DATETIME, SimpleDateFormat(EXIF_DATE_PATTERN, Locale.US).format(Date()))
        exif.saveAttributes()
    }

    /** The original's DATE_TAKEN, so the copy sorts beside it; now if unknown. */
    // The cursor is closed by `?.use {}`; lint's Recycle check misreads the safe call.
    @SuppressLint("Recycle")
    private fun originalDateTaken(source: Uri): Long = runCatching {
        resolver.query(source, arrayOf(MediaStore.MediaColumns.DATE_TAKEN), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getLong(0).takeIf { it > 0 } else null
        }
    }.getOrNull() ?: System.currentTimeMillis()

    private companion object {
        const val TEMP_DIR = "editor"

        /** 24 MP keeps the edit under ~200 MB of native memory on constrained devices. */
        const val LOW_MEMORY_PIXELS = 24_000_000L

        /** Devices whose largeHeap class is below this are treated as memory-constrained. */
        const val LARGE_MEMORY_CLASS_MB = 384
        const val EXIF_DATE_PATTERN = "yyyy:MM:dd HH:mm:ss"

        /** Metadata worth keeping on an edited copy. Size/orientation tags are rewritten instead. */
        val COPIED_TAGS = listOf(
            ExifInterface.TAG_DATETIME_ORIGINAL,
            ExifInterface.TAG_DATETIME_DIGITIZED,
            ExifInterface.TAG_OFFSET_TIME,
            ExifInterface.TAG_OFFSET_TIME_ORIGINAL,
            ExifInterface.TAG_OFFSET_TIME_DIGITIZED,
            ExifInterface.TAG_SUBSEC_TIME,
            ExifInterface.TAG_SUBSEC_TIME_ORIGINAL,
            ExifInterface.TAG_SUBSEC_TIME_DIGITIZED,
            ExifInterface.TAG_MAKE,
            ExifInterface.TAG_MODEL,
            ExifInterface.TAG_LENS_MAKE,
            ExifInterface.TAG_LENS_MODEL,
            ExifInterface.TAG_EXPOSURE_TIME,
            ExifInterface.TAG_F_NUMBER,
            ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
            ExifInterface.TAG_EXPOSURE_BIAS_VALUE,
            ExifInterface.TAG_FOCAL_LENGTH,
            ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM,
            ExifInterface.TAG_FLASH,
            ExifInterface.TAG_WHITE_BALANCE,
            ExifInterface.TAG_GPS_LATITUDE,
            ExifInterface.TAG_GPS_LATITUDE_REF,
            ExifInterface.TAG_GPS_LONGITUDE,
            ExifInterface.TAG_GPS_LONGITUDE_REF,
            ExifInterface.TAG_GPS_ALTITUDE,
            ExifInterface.TAG_GPS_ALTITUDE_REF,
            ExifInterface.TAG_GPS_TIMESTAMP,
            ExifInterface.TAG_GPS_DATESTAMP,
            ExifInterface.TAG_GPS_PROCESSING_METHOD,
        )
    }
}
