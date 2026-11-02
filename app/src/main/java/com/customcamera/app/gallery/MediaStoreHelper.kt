package com.customcamera.app.gallery

import android.content.ContentResolver
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.customcamera.app.core.MediaNaming
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.util.Date

/**
 * Builds MediaStore insert targets so captures land in the shared gallery under
 * DCIM/CustomCamera on every supported API level.
 *
 * API 29+ uses RELATIVE_PATH (scoped storage, no permission needed).
 * API 26–28 needs WRITE_EXTERNAL_STORAGE (declared with maxSdkVersion 28) and
 * an explicit DATA path, otherwise MediaStore drops files in the volume root.
 */
object MediaStoreHelper {

    data class Target(val collection: Uri, val values: ContentValues, val displayName: String)

    fun imageCollection(): Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    } else {
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    }

    fun videoCollection(): Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    } else {
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    }

    /** Insert target for a capture of [kind] taken at [time]. */
    fun target(kind: MediaNaming.Kind, time: Date): Target =
        target(MediaNaming.fileName(kind, time), kind, time.time)

    /**
     * Insert target for an edited copy made at [editTime]. The name carries the edit
     * time (unique per edit); DATE_TAKEN is the original's so the copy sorts beside it.
     */
    fun editTarget(editTime: Date, originalDateTaken: Long): Target =
        target(MediaNaming.editedFileName(editTime), MediaNaming.Kind.PHOTO, originalDateTaken)

    private fun target(name: String, kind: MediaNaming.Kind, dateTaken: Long): Target {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, kind.mimeType)
            put(MediaStore.MediaColumns.DATE_TAKEN, dateTaken)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, MediaNaming.RELATIVE_PATH)
            } else {
                @Suppress("DEPRECATION")
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), MediaNaming.ALBUM)
                dir.mkdirs()
                @Suppress("DEPRECATION")
                put(MediaStore.MediaColumns.DATA, File(dir, name).absolutePath)
            }
        }
        val collection = if (kind == MediaNaming.Kind.VIDEO) videoCollection() else imageCollection()
        return Target(collection, values, name)
    }

    /**
     * Inserts [target] and fills it with [write]. On API 29+ the row stays pending
     * (hidden from other apps and from [MediaRepository]) until the bytes are complete.
     * On any failure the half-written row is removed and the exception rethrown.
     */
    fun publish(resolver: ContentResolver, target: Target, write: (OutputStream) -> Unit): Uri {
        val pending = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        val values = ContentValues(target.values).apply {
            if (pending) put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(target.collection, values)
            ?: throw IOException("MediaStore insert failed for ${target.displayName}")
        try {
            val stream = resolver.openOutputStream(uri) ?: throw IOException("Cannot open $uri for writing")
            stream.use(write)
            if (pending) {
                resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            }
            return uri
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }
    }

    /** Deletes a capture. Our own captures need no user confirmation on any API level. */
    fun delete(resolver: ContentResolver, uri: Uri): Boolean = try {
        resolver.delete(uri, null, null) > 0
    } catch (e: SecurityException) {
        false
    }
}
