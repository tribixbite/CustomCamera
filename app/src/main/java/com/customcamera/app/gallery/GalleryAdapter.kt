package com.customcamera.app.gallery

import android.content.Context
import android.graphics.Color
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

        // Media type indicator
        val iconView = ImageView(context).apply {
            val iconRes = if (mediaItem.isVideo) {
                android.R.drawable.ic_menu_camera // Video icon
            } else {
                android.R.drawable.ic_menu_gallery // Photo icon
            }
            setImageResource(iconRes)
            layoutParams = LinearLayout.LayoutParams(80, 80)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
        }
        container.addView(iconView)

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
}