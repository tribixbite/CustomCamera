package com.customcamera.app.gallery

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.ThumbnailUtils
import android.provider.MediaStore
import android.util.Size
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.*

/**
 * GalleryAdapter for displaying media items in a grid
 */
class GalleryAdapter(
    private val context: Context,
    private val mediaItems: List<MediaItem>,
    private val onItemClick: (MediaItem) -> Unit
) : BaseAdapter() {

    private val dateFormat = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())

    override fun getCount(): Int = mediaItems.size

    override fun getItem(position: Int): MediaItem = mediaItems[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val mediaItem = mediaItems[position]

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(8, 8, 8, 8)
            setBackgroundColor(Color.GRAY)
        }

        // Thumbnail image
        val thumbnailView = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(200, 200).apply {
                bottomMargin = 8
            }
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(Color.DKGRAY)

            // Load thumbnail asynchronously
            loadThumbnail(mediaItem, this)
        }
        container.addView(thumbnailView)

        // File name
        val nameView = TextView(context).apply {
            text = mediaItem.name
            textSize = 12f
            maxLines = 2
            setTextColor(Color.WHITE)
        }
        container.addView(nameView)

        // Timestamp
        val timeView = TextView(context).apply {
            text = dateFormat.format(Date(mediaItem.timestamp))
            textSize = 10f
            setTextColor(Color.LTGRAY)
        }
        container.addView(timeView)

        // Size
        val sizeView = TextView(context).apply {
            text = mediaItem.sizeFormatted
            textSize = 10f
            setTextColor(Color.LTGRAY)
        }
        container.addView(sizeView)

        container.setOnClickListener {
            onItemClick(mediaItem)
        }

        return container
    }

    /**
     * Load thumbnail for media item (image or video)
     */
    private fun loadThumbnail(mediaItem: MediaItem, imageView: ImageView) {
        Thread {
            try {
                val thumbnail: Bitmap? = if (mediaItem.isVideo) {
                    // Video thumbnail using ThumbnailUtils
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        // Android 10+ - use modern API
                        ThumbnailUtils.createVideoThumbnail(
                            mediaItem.file,
                            Size(200, 200),
                            null
                        )
                    } else {
                        // Android 7-9 - use legacy API
                        @Suppress("DEPRECATION")
                        ThumbnailUtils.createVideoThumbnail(
                            mediaItem.path,
                            MediaStore.Video.Thumbnails.MINI_KIND
                        )
                    }
                } else {
                    // Image thumbnail using BitmapFactory with sampling
                    val options = BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }
                    BitmapFactory.decodeFile(mediaItem.path, options)

                    // Calculate sample size for efficient memory usage
                    options.inSampleSize = calculateInSampleSize(options, 200, 200)
                    options.inJustDecodeBounds = false

                    BitmapFactory.decodeFile(mediaItem.path, options)
                }

                // Update UI on main thread
                (context as? android.app.Activity)?.runOnUiThread {
                    if (thumbnail != null) {
                        imageView.setImageBitmap(thumbnail)
                    } else {
                        // Fallback to icon if thumbnail generation fails
                        val iconRes = if (mediaItem.isVideo) {
                            android.R.drawable.ic_menu_camera
                        } else {
                            android.R.drawable.ic_menu_gallery
                        }
                        imageView.setImageResource(iconRes)
                    }
                }
            } catch (e: Exception) {
                // On error, show icon
                (context as? android.app.Activity)?.runOnUiThread {
                    val iconRes = if (mediaItem.isVideo) {
                        android.R.drawable.ic_menu_camera
                    } else {
                        android.R.drawable.ic_menu_gallery
                    }
                    imageView.setImageResource(iconRes)
                }
            }
        }.start()
    }

    /**
     * Calculate appropriate sample size for bitmap loading
     */
    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2

            while ((halfHeight / inSampleSize) >= reqHeight &&
                (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }

        return inSampleSize
    }
}