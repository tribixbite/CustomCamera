package com.customcamera.app.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.FileProvider
import java.io.File

/**
 * Photo preview overlay that appears after capturing a photo,
 * allowing quick review, share, or delete actions.
 */
class PhotoPreviewOverlay(
    private val context: Context,
    private val parentView: ViewGroup
) {

    private var overlayView: FrameLayout? = null
    private var imageView: ImageView? = null
    private var currentPhotoFile: File? = null

    var onDismiss: (() -> Unit)? = null
    var onDelete: ((File) -> Unit)? = null

    /**
     * Initialize the preview overlay
     */
    fun initialize() {
        createOverlayView()
        Log.i(TAG, "Photo preview overlay initialized")
    }

    private fun createOverlayView() {
        overlayView = FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#DD000000"))
            visibility = View.GONE
        }

        // Add image view
        imageView = ImageView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ).apply {
                gravity = Gravity.CENTER
            }
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(32, 32, 32, 32)
        }
        overlayView?.addView(imageView)

        // Add button container at bottom
        val buttonContainer = createButtonContainer()
        overlayView?.addView(buttonContainer)

        parentView.addView(overlayView)
    }

    private fun createButtonContainer(): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM
                setMargins(32, 32, 32, 64)
            }

            // Share button
            addView(createActionButton("📤 Share") {
                currentPhotoFile?.let { sharePhoto(it) }
            })

            // Delete button
            addView(createActionButton("🗑️ Delete") {
                currentPhotoFile?.let { file ->
                    onDelete?.invoke(file)
                    hide()
                }
            })

            // Close button
            addView(createActionButton("✕ Close") {
                hide()
            })
        }
    }

    private fun createActionButton(text: String, onClick: () -> Unit): Button {
        return Button(context).apply {
            this.text = text
            textSize = 16f
            setPadding(32, 24, 32, 24)
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                setMargins(8, 0, 8, 0)
            }
            setOnClickListener { onClick() }
        }
    }

    /**
     * Show preview for a captured photo
     */
    fun show(photoFile: File) {
        currentPhotoFile = photoFile

        try {
            // Load and display the photo
            val bitmap = BitmapFactory.decodeFile(photoFile.absolutePath)
            imageView?.setImageBitmap(bitmap)

            // Show overlay with fade-in animation
            overlayView?.let { overlay ->
                overlay.alpha = 0f
                overlay.visibility = View.VISIBLE
                overlay.animate()
                    .alpha(1f)
                    .setDuration(300)
                    .start()
            }

            Log.i(TAG, "Showing preview for: ${photoFile.name}")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to load photo preview", e)
            Toast.makeText(context, "Failed to load preview: ${e.message}", Toast.LENGTH_SHORT).show()
            hide()
        }
    }

    /**
     * Hide the preview overlay
     */
    fun hide() {
        overlayView?.animate()
            ?.alpha(0f)
            ?.setDuration(200)
            ?.withEndAction {
                overlayView?.visibility = View.GONE
                imageView?.setImageBitmap(null)
                currentPhotoFile = null
                onDismiss?.invoke()
                Log.i(TAG, "Preview hidden")
            }
            ?.start()
    }

    /**
     * Share the photo using Android's share intent
     */
    private fun sharePhoto(file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "Share photo via")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)

            Log.i(TAG, "Sharing photo: ${file.name}")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to share photo", e)
            Toast.makeText(context, "Failed to share: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Cleanup resources
     */
    fun cleanup() {
        imageView?.setImageBitmap(null)
        currentPhotoFile = null
    }

    companion object {
        private const val TAG = "PhotoPreviewOverlay"
    }
}
