package com.customcamera.app

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.widget.GridView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.exifinterface.media.ExifInterface
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
                // Show photo detail view with metadata
                showPhotoDetail(mediaItem)
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

    private fun showPhotoDetail(mediaItem: MediaItem) {
        try {
            val detailInfo = extractPhotoDetails(mediaItem)

            val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("📷 Photo Details")
                .setMessage(detailInfo)
                .setPositiveButton("Open Photo") { _, _ ->
                    openPhotoExternally(mediaItem)
                }
                .setNeutralButton("Share") { _, _ ->
                    sharePhoto(mediaItem)
                }
                .setNegativeButton("Close", null)
                .create()

            dialog.show()

        } catch (e: Exception) {
            Log.e(TAG, "Error showing photo detail", e)
            Toast.makeText(this, "Photo detail error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Extract photo details from EXIF metadata
     */
    private fun extractPhotoDetails(mediaItem: MediaItem): String {
        return try {
            val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())

            // Read EXIF metadata from the photo file
            val exif = ExifInterface(mediaItem.file.absolutePath)

            // Extract EXIF data
            val make = exif.getAttribute(ExifInterface.TAG_MAKE) ?: "Unknown"
            val model = exif.getAttribute(ExifInterface.TAG_MODEL) ?: "Unknown"
            val iso = exif.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY) ?: "Auto"
            val exposureTime = exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME) ?: "Auto"
            val fNumber = exif.getAttribute(ExifInterface.TAG_F_NUMBER) ?: "Unknown"
            val focalLength = exif.getAttribute(ExifInterface.TAG_FOCAL_LENGTH) ?: "Unknown"
            val whiteBalance = when (exif.getAttributeInt(ExifInterface.TAG_WHITE_BALANCE, -1).toShort()) {
                ExifInterface.WHITE_BALANCE_AUTO -> "Auto"
                ExifInterface.WHITE_BALANCE_MANUAL -> "Manual"
                else -> "Unknown"
            }
            val flash = when (exif.getAttributeInt(ExifInterface.TAG_FLASH, -1).toShort()) {
                ExifInterface.FLAG_FLASH_FIRED -> "Fired"
                ExifInterface.FLAG_FLASH_NO_FLASH_FUNCTION -> "No Flash"
                else -> "Off"
            }
            val imageWidth = exif.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 0)
            val imageHeight = exif.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, 0)
            val orientation = when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> "90°"
                ExifInterface.ORIENTATION_ROTATE_180 -> "180°"
                ExifInterface.ORIENTATION_ROTATE_270 -> "270°"
                else -> "Normal"
            }

            """
                📁 File Information:
                Name: ${mediaItem.name}
                Size: ${mediaItem.sizeFormatted}
                Date: ${dateFormat.format(java.util.Date(mediaItem.timestamp))}
                Resolution: ${imageWidth}x${imageHeight}
                Orientation: $orientation

                📸 Camera Information:
                Make: $make
                Model: $model

                🎛️ Camera Settings:
                ISO: $iso
                Exposure Time: $exposureTime
                F-Number: f/$fNumber
                Focal Length: $focalLength
                White Balance: $whiteBalance
                Flash: $flash

                🔌 CustomCamera System:
                Engine: Professional Plugin Architecture
                Format: JPEG
                Processing: Multi-stage pipeline
                Quality: Plugin-enhanced
            """.trimIndent()

        } catch (e: Exception) {
            Log.e(TAG, "Error extracting photo details", e)

            // Fallback to basic file info if EXIF reading fails
            val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
            """
                📁 File Information:
                Name: ${mediaItem.name}
                Size: ${mediaItem.sizeFormatted}
                Date: ${dateFormat.format(java.util.Date(mediaItem.timestamp))}

                ⚠️ Unable to read EXIF metadata
                Error: ${e.message}
            """.trimIndent()
        }
    }

    private fun openPhotoExternally(mediaItem: MediaItem) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(androidx.core.content.FileProvider.getUriForFile(
                    this@GalleryActivity,
                    "${packageName}.fileprovider",
                    mediaItem.file
                ), "image/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error opening photo externally", e)
            Toast.makeText(this, "Cannot open photo: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sharePhoto(mediaItem: MediaItem) {
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = if (mediaItem.isVideo) "video/*" else "image/*"
                putExtra(Intent.EXTRA_STREAM, androidx.core.content.FileProvider.getUriForFile(
                    this@GalleryActivity,
                    "${packageName}.fileprovider",
                    mediaItem.file
                ))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Share ${if (mediaItem.isVideo) "Video" else "Photo"}"))
        } catch (e: Exception) {
            Log.e(TAG, "Error sharing media", e)
            Toast.makeText(this, "Cannot share: ${e.message}", Toast.LENGTH_SHORT).show()
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