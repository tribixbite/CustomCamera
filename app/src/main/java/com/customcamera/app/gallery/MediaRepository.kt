package com.customcamera.app.gallery

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import com.customcamera.app.core.MediaNaming
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext

/** One photo or video in the app's album. */
data class MediaItem(
    val uri: Uri,
    val displayName: String,
    val isVideo: Boolean,
    val dateTaken: Long,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val durationMs: Long,
    val mimeType: String,
)

/**
 * Reads the app's captures from MediaStore and loads thumbnails.
 *
 * On API 29+ an app can always read media it created itself, so no storage
 * permission is requested. Captures made by a previous install (or another
 * app) are intentionally not shown — Play policy reserves READ_MEDIA_* for
 * apps whose core function is broad media access.
 */
class MediaRepository private constructor(context: Context) {

    private val resolver: ContentResolver = context.applicationContext.contentResolver

    /** Thumbnail cache sized to 1/8 of the heap. */
    private val thumbnails = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 8 / 1024).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount / 1024
    }

    suspend fun loadAll(): List<MediaItem> = withContext(Dispatchers.IO) {
        (query(isVideo = false) + query(isVideo = true)).sortedByDescending { it.dateTaken }
    }

    suspend fun latest(): MediaItem? = loadAll().firstOrNull()

    private fun query(isVideo: Boolean): List<MediaItem> {
        val collection = if (isVideo) MediaStoreHelper.videoCollection() else MediaStoreHelper.imageCollection()
        val projection = buildList {
            add(MediaStore.MediaColumns._ID)
            add(MediaStore.MediaColumns.DISPLAY_NAME)
            add(MediaStore.MediaColumns.DATE_TAKEN)
            add(MediaStore.MediaColumns.DATE_ADDED)
            add(MediaStore.MediaColumns.SIZE)
            add(MediaStore.MediaColumns.WIDTH)
            add(MediaStore.MediaColumns.HEIGHT)
            add(MediaStore.MediaColumns.MIME_TYPE)
            if (isVideo) add(MediaStore.Video.VideoColumns.DURATION)
        }.toTypedArray()

        val (selection, args) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ? AND ${MediaStore.MediaColumns.IS_PENDING} = 0" to
                arrayOf("${MediaNaming.RELATIVE_PATH}%")
        } else {
            @Suppress("DEPRECATION")
            "${MediaStore.MediaColumns.DATA} LIKE ?" to arrayOf("%/DCIM/${MediaNaming.ALBUM}/%")
        }

        val items = mutableListOf<MediaItem>()
        resolver.query(collection, projection, selection, args, "${MediaStore.MediaColumns.DATE_ADDED} DESC")?.use { c ->
            val id = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val name = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val taken = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_TAKEN)
            val added = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            val size = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val width = c.getColumnIndexOrThrow(MediaStore.MediaColumns.WIDTH)
            val height = c.getColumnIndexOrThrow(MediaStore.MediaColumns.HEIGHT)
            val mime = c.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
            val duration = if (isVideo) c.getColumnIndexOrThrow(MediaStore.Video.VideoColumns.DURATION) else -1
            while (c.moveToNext()) {
                val takenMs = c.getLong(taken).takeIf { it > 0 } ?: (c.getLong(added) * 1000)
                items += MediaItem(
                    uri = ContentUris.withAppendedId(collection, c.getLong(id)),
                    displayName = c.getString(name).orEmpty(),
                    isVideo = isVideo,
                    dateTaken = takenMs,
                    sizeBytes = c.getLong(size),
                    width = c.getInt(width),
                    height = c.getInt(height),
                    durationMs = if (duration >= 0) c.getLong(duration) else 0L,
                    mimeType = c.getString(mime).orEmpty(),
                )
            }
        }
        return items
    }

    /** Cached thumbnail; null if the item cannot be decoded (e.g. deleted externally). */
    suspend fun thumbnail(item: MediaItem, sizePx: Int): Bitmap? = thumbnail(item.uri, item.isVideo, sizePx)

    suspend fun thumbnail(uri: Uri, isVideo: Boolean, sizePx: Int): Bitmap? {
        val key = "$uri@$sizePx"
        thumbnails.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            val bitmap = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    resolver.loadThumbnail(uri, Size(sizePx, sizePx), null)
                } else {
                    legacyThumbnail(uri, isVideo)
                }
            } catch (e: Exception) {
                null
            }
            bitmap?.also { thumbnails.put(key, it) }
        }
    }

    @Suppress("DEPRECATION")
    private fun legacyThumbnail(uri: Uri, isVideo: Boolean): Bitmap? {
        val id = ContentUris.parseId(uri)
        return if (isVideo) {
            MediaStore.Video.Thumbnails.getThumbnail(resolver, id, MediaStore.Video.Thumbnails.MINI_KIND, null)
        } else {
            MediaStore.Images.Thumbnails.getThumbnail(resolver, id, MediaStore.Images.Thumbnails.MINI_KIND, null)
        }
    }

    fun delete(item: MediaItem): Boolean {
        val deleted = MediaStoreHelper.delete(resolver, item.uri)
        if (deleted) thumbnails.snapshot().keys.filter { it.startsWith(item.uri.toString()) }.forEach { thumbnails.remove(it) }
        return deleted
    }

    /** Emits Unit whenever image or video MediaStore content changes. */
    fun changes(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        resolver.registerContentObserver(MediaStoreHelper.imageCollection(), true, observer)
        resolver.registerContentObserver(MediaStoreHelper.videoCollection(), true, observer)
        awaitClose { resolver.unregisterContentObserver(observer) }
    }

    companion object {
        @Volatile private var instance: MediaRepository? = null

        /** Process-wide instance so the thumbnail cache is shared by every screen. */
        fun get(context: Context): MediaRepository =
            instance ?: synchronized(this) { instance ?: MediaRepository(context).also { instance = it } }
    }
}
