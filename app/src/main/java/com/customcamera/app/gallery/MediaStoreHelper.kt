package com.customcamera.app.gallery

import android.content.ContentResolver
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.customcamera.app.core.MediaNaming
import java.io.File
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
    fun target(kind: MediaNaming.Kind, time: Date): Target {
        val name = MediaNaming.fileName(kind, time)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, kind.mimeType)
            put(MediaStore.MediaColumns.DATE_TAKEN, time.time)
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

    /** Deletes a capture. Our own captures need no user confirmation on any API level. */
    fun delete(resolver: ContentResolver, uri: Uri): Boolean = try {
        resolver.delete(uri, null, null) > 0
    } catch (e: SecurityException) {
        false
    }
}
