package com.customcamera.app

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.widget.GridView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.customcamera.app.gallery.GalleryAdapter
import com.customcamera.app.gallery.MediaItem
import kotlinx.coroutines.launch
import java.io.File

/**
 * GalleryActivity provides photo/video management
 * with grid view and media operations.
 */
class GalleryActivity : AppCompatActivity() {

    private lateinit var galleryGrid: GridView
    private lateinit var galleryAdapter: GalleryAdapter
    private val mediaItems = mutableListOf<MediaItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "GalleryActivity onCreate")

        createGalleryLayout()
        setupToolbar()
        loadMediaItems()
    }

    private fun createGalleryLayout() {
        galleryGrid = GridView(this).apply {
            numColumns = 3
            setPadding(16, 16, 16, 16)
            verticalSpacing = 8
            horizontalSpacing = 8
        }

        setContentView(galleryGrid)
    }

    private fun setupToolbar() {
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "Camera Gallery"
        }
    }

    private fun loadMediaItems() {
        lifecycleScope.launch {
            try {
                Log.i(TAG, "Loading media items from filesDir")

                val mediaFiles = filesDir.listFiles { file ->
                    file.name.startsWith("CAMERA_") || file.name.startsWith("VIDEO_")
                }?.sortedByDescending { it.lastModified() } ?: emptyList()

                mediaItems.clear()
                mediaFiles.forEach { file ->
                    val mediaItem = MediaItem(
                        file = file,
                        isVideo = file.name.startsWith("VIDEO_"),
                        timestamp = file.lastModified(),
                        size = file.length()
                    )
                    mediaItems.add(mediaItem)
                }

                Log.i(TAG, "Loaded ${mediaItems.size} media items")

                // Setup adapter
                galleryAdapter = GalleryAdapter(this@GalleryActivity, mediaItems) { mediaItem ->
                    openMediaItem(mediaItem)
                }
                galleryGrid.adapter = galleryAdapter

                if (mediaItems.isEmpty()) {
                    Toast.makeText(this@GalleryActivity, "No photos or videos found", Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error loading media items", e)
                Toast.makeText(this@GalleryActivity, "Error loading gallery: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun openMediaItem(mediaItem: MediaItem) {
        try {
            Log.i(TAG, "Opening media item: ${mediaItem.file.name}")

            if (mediaItem.isVideo) {
                // Open video with external player
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(androidx.core.content.FileProvider.getUriForFile(
                        this@GalleryActivity,
                        "${packageName}.fileprovider",
                        mediaItem.file
                    ), "video/*")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(intent)
            } else {
                // Open photo with external viewer
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(androidx.core.content.FileProvider.getUriForFile(
                        this@GalleryActivity,
                        "${packageName}.fileprovider",
                        mediaItem.file
                    ), "image/*")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(intent)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error opening media item", e)
            Toast.makeText(this, "Cannot open media: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh gallery when returning to activity
        loadMediaItems()
    }

    companion object {
        private const val TAG = "GalleryActivity"
    }
}