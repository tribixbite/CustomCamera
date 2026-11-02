package com.customcamera.app.gallery

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.view.View
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.customcamera.app.R
import com.customcamera.app.core.CropAspect
import com.customcamera.app.core.MediaNaming
import com.customcamera.app.waitUntil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date

/**
 * Drives the photo editor end to end against the real MediaStore: a generated
 * landscape JPEG is cropped 1:1 and saved; the copy must be a new, square row
 * while the original stays untouched. Both rows are deleted afterwards.
 */
@RunWith(AndroidJUnit4::class)
class PhotoEditorActivityTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val resolver = context.contentResolver

    @Test
    fun squareCropSavesSquareCopyAndKeepsOriginal() {
        // Pre-Q writes need WRITE_EXTERNAL_STORAGE, granted only at runtime by the camera screen.
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        val original = MediaStoreHelper.publish(resolver, MediaStoreHelper.target(MediaNaming.Kind.PHOTO, Date())) { out ->
            Bitmap.createBitmap(SOURCE_WIDTH, SOURCE_HEIGHT, Bitmap.Config.ARGB_8888)
                .apply { eraseColor(Color.CYAN) }
                .compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        var copy: Uri? = null
        try {
            ActivityScenario.launchActivityForResult<PhotoEditorActivity>(PhotoEditorActivity.intent(context, original)).use { scenario ->
                waitUntil(message = "editor preview") {
                    var ready = false
                    scenario.onActivity { ready = it.isPreviewReady }
                    ready
                }
                scenario.onActivity { activity ->
                    activity.selectAspectForTest(CropAspect.SQUARE)
                    val save = activity.findViewById<View>(R.id.saveButton)
                    assertTrue("Save must be enabled after cropping", save.isEnabled)
                    save.performClick()
                }
                // Blocks until the activity finishes (after the full-resolution save).
                val result = scenario.result
                assertEquals(Activity.RESULT_OK, result.resultCode)
                copy = result.resultData?.data
            }
            val saved = copy
            assertNotNull("editor returned no Uri", saved)
            assertNotEquals("original must never be overwritten", original, saved)

            // MediaStore fills WIDTH/HEIGHT when it scans the published file; poll for them.
            var size = 0 to 0
            waitUntil(message = "MediaStore dimensions of the copy") {
                size = mediaStoreSize(saved!!)
                size.first > 0
            }
            assertEquals("square copy", SOURCE_HEIGHT to SOURCE_HEIGHT, size)
            assertEquals("decoded pixels are square too", SOURCE_HEIGHT to SOURCE_HEIGHT, decodedSize(saved!!))
            assertTrue(displayName(saved).endsWith("${MediaNaming.EDIT_SUFFIX}.jpg"))
            resolver.openInputStream(saved)!!.use {
                assertEquals(ExifInterface.ORIENTATION_NORMAL, ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, -1))
            }

            assertEquals("original is untouched", SOURCE_WIDTH to SOURCE_HEIGHT, decodedSize(original))
        } finally {
            copy?.let { runCatching { resolver.delete(it, null, null) } }
            runCatching { resolver.delete(original, null, null) }
        }
    }

    private fun mediaStoreSize(uri: Uri): Pair<Int, Int> =
        resolver.query(uri, arrayOf(MediaStore.MediaColumns.WIDTH, MediaStore.MediaColumns.HEIGHT), null, null, null)!!.use { c ->
            if (c.moveToFirst()) c.getInt(0) to c.getInt(1) else 0 to 0
        }

    private fun displayName(uri: Uri): String =
        resolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)!!.use { c ->
            if (c.moveToFirst()) c.getString(0).orEmpty() else ""
        }

    private fun decodedSize(uri: Uri): Pair<Int, Int> {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, options) }
        return options.outWidth to options.outHeight
    }

    private companion object {
        const val SOURCE_WIDTH = 640
        const val SOURCE_HEIGHT = 480
    }
}
