package com.customcamera.app.gallery

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.customcamera.app.core.MediaNaming
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date

/**
 * Exercises the real MediaStore: insert into DCIM/CustomCamera, list, thumbnail, delete.
 */
@RunWith(AndroidJUnit4::class)
class MediaRepositoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val repository = MediaRepository.get(context)

    @Test
    fun insertedPhotoIsListedThumbnailedAndDeleted() = runBlocking {
        // Pre-Q writes need WRITE_EXTERNAL_STORAGE, granted only at runtime by the camera screen.
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        val target = MediaStoreHelper.target(MediaNaming.Kind.PHOTO, Date())
        assertEquals(MediaNaming.RELATIVE_PATH, target.values.getAsString(MediaStore.MediaColumns.RELATIVE_PATH))

        val resolver = context.contentResolver
        val uri = resolver.insert(target.collection, ContentValues(target.values).apply { put(MediaStore.MediaColumns.IS_PENDING, 1) })
        assertNotNull("insert failed", uri)
        try {
            resolver.openOutputStream(uri!!)!!.use { out ->
                Bitmap.createBitmap(64, 48, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.MAGENTA) }
                    .compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)

            val items = repository.loadAll()
            val item = items.firstOrNull { it.uri == uri }
            assertNotNull("inserted item not listed (${items.size} items)", item)
            assertEquals(target.displayName, item!!.displayName)
            assertFalse(item.isVideo)
            assertTrue("items must be newest first", items.zipWithNext().all { (a, b) -> a.dateTaken >= b.dateTaken })

            val thumb = repository.thumbnail(item, 96)
            assertNotNull("thumbnail failed", thumb)

            assertTrue(repository.delete(item))
            assertFalse(repository.loadAll().any { it.uri == uri })
        } finally {
            runCatching { resolver.delete(uri!!, null, null) }
        }
    }

    @Test
    fun videoTargetsUseVideoCollection() {
        val target = MediaStoreHelper.target(MediaNaming.Kind.VIDEO, Date())
        assertEquals(MediaStoreHelper.videoCollection(), target.collection)
        assertEquals("video/mp4", target.values.getAsString(MediaStore.MediaColumns.MIME_TYPE))
        assertTrue(target.displayName.endsWith(".mp4"))
    }
}
