package com.customcamera.app.gallery

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.MediaController
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.customcamera.app.R
import com.customcamera.app.core.ExposureMath
import com.customcamera.app.core.TimeFormat
import com.customcamera.app.databinding.ActivityViewerBinding
import com.customcamera.app.databinding.ItemViewerPageBinding
import com.customcamera.app.util.BitmapDecoding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

/**
 * Full-screen swipeable viewer for the app's captures, with share, edit,
 * details and delete. Launch with the item's content Uri as intent data.
 */
class MediaViewerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityViewerBinding
    private val repository by lazy { MediaRepository.get(this) }
    private val items = mutableListOf<MediaItem>()
    private lateinit var adapter: PageAdapter
    private var chromeVisible = true

    /** In-app crop & rotate: on success, reload and show the new copy. */
    private val editorLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val saved = result.data?.data
        if (result.resultCode == RESULT_OK && saved != null) {
            Toast.makeText(this, R.string.editor_saved, Toast.LENGTH_SHORT).show()
            load(saved)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            binding.toolbar.updatePadding(top = bars.top, left = bars.left, right = bars.right)
            binding.bottomBar.updatePadding(bottom = bars.bottom + BOTTOM_PAD_PX, left = bars.left, right = bars.right)
            insets
        }

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.toolbar.setOnMenuItemClickListener {
            if (it.itemId == R.id.action_gallery) {
                startActivity(Intent(this, GalleryActivity::class.java))
                true
            } else false
        }
        adapter = PageAdapter()
        binding.pager.adapter = adapter
        binding.pager.offscreenPageLimit = 1
        binding.pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateTitle()
                adapter.stopVideos()
            }
        })
        binding.shareButton.setOnClickListener { current()?.let(::share) }
        binding.cropButton.setOnClickListener {
            current()?.takeUnless { it.isVideo }?.let { editorLauncher.launch(PhotoEditorActivity.intent(this, it.uri)) }
        }
        binding.editButton.setOnClickListener { current()?.let(::edit) }
        binding.infoButton.setOnClickListener { current()?.let(::showDetails) }
        binding.deleteButton.setOnClickListener { current()?.let(::confirmDelete) }

        load(intent.data)
    }

    override fun onPause() {
        adapter.stopVideos()
        super.onPause()
    }

    /** (Re)loads the album and shows [start] (or the newest item if it is not found). */
    private fun load(start: android.net.Uri?) {
        lifecycleScope.launch {
            val loaded = repository.loadAll()
            val previous = items.size
            items.clear()
            if (previous > 0) adapter.notifyItemRangeRemoved(0, previous)
            items += loaded
            adapter.notifyItemRangeInserted(0, loaded.size)
            val index = items.indexOfFirst { it.uri == start }.coerceAtLeast(0)
            binding.pager.setCurrentItem(index, false)
            updateEmptyState()
            updateTitle()
        }
    }

    private fun current(): MediaItem? = items.getOrNull(binding.pager.currentItem)

    private fun updateTitle() {
        val item = current()
        binding.toolbar.title = item?.let { DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it.dateTaken)) }
        binding.toolbar.subtitle = item?.let { DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it.dateTaken)) }
        binding.editButton.isVisible = item?.isVideo == false
        binding.cropButton.isVisible = item?.isVideo == false
    }

    private fun updateEmptyState() {
        val empty = items.isEmpty()
        binding.emptyState.isVisible = empty
        binding.bottomBar.isVisible = !empty && chromeVisible
    }

    private fun toggleChrome() {
        chromeVisible = !chromeVisible
        val alpha = if (chromeVisible) 1f else 0f
        binding.toolbar.animate().alpha(alpha).setDuration(150).withEndAction { binding.toolbar.isVisible = chromeVisible }.start()
        binding.bottomBar.animate().alpha(alpha).setDuration(150).withEndAction { binding.bottomBar.isVisible = chromeVisible }.start()
        if (chromeVisible) {
            binding.toolbar.isVisible = true
            binding.bottomBar.isVisible = true
        }
        WindowInsetsControllerCompat(window, binding.root).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (chromeVisible) show(WindowInsetsCompat.Type.systemBars()) else hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun share(item: MediaItem) {
        val send = Intent(Intent.ACTION_SEND)
            .setType(item.mimeType.ifEmpty { if (item.isVideo) "video/*" else "image/*" })
            .putExtra(Intent.EXTRA_STREAM, item.uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(Intent.createChooser(send, null))
    }

    private fun edit(item: MediaItem) {
        val intent = Intent(Intent.ACTION_EDIT)
            .setDataAndType(item.uri, item.mimeType.ifEmpty { "image/*" })
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        try {
            startActivity(Intent.createChooser(intent, null))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.no_app_for_action, Toast.LENGTH_SHORT).show()
        }
    }

    private fun confirmDelete(item: MediaItem) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_title)
            .setMessage(R.string.delete_body)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete_confirm) { _, _ ->
                lifecycleScope.launch {
                    val ok = withContext(Dispatchers.IO) { repository.delete(item) }
                    if (ok) {
                        val index = items.indexOf(item)
                        if (index >= 0) {
                            items.removeAt(index)
                            adapter.notifyItemRemoved(index)
                        }
                        Toast.makeText(this@MediaViewerActivity, R.string.deleted, Toast.LENGTH_SHORT).show()
                        updateEmptyState()
                        updateTitle()
                    } else {
                        Toast.makeText(this@MediaViewerActivity, R.string.delete_failed, Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .show()
    }

    private fun showDetails(item: MediaItem) {
        lifecycleScope.launch {
            val rows = mutableListOf<Pair<Int, String>>()
            rows += R.string.details_name to item.displayName
            rows += R.string.details_date to DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.MEDIUM).format(Date(item.dateTaken))
            if (item.width > 0 && item.height > 0) rows += R.string.details_resolution to "${item.width} × ${item.height}"
            rows += R.string.details_size to TimeFormat.fileSize(item.sizeBytes)
            if (item.isVideo) rows += R.string.details_duration to TimeFormat.videoBadge(item.durationMs)
            if (!item.isVideo) rows += withContext(Dispatchers.IO) { exifRows(item) }
            val message = rows.joinToString("\n") { (label, value) -> "${getString(label)}: $value" }
            MaterialAlertDialogBuilder(this@MediaViewerActivity)
                .setTitle(R.string.viewer_info)
                .setMessage(message)
                .setPositiveButton(R.string.close, null)
                .show()
        }
    }

    // The stream is closed by `?.use {}`; lint's Recycle check misreads the safe call.
    @SuppressLint("Recycle")
    private fun exifRows(item: MediaItem): List<Pair<Int, String>> = runCatching {
        contentResolver.openInputStream(item.uri)?.use { stream ->
            val exif = ExifInterface(stream)
            buildList {
                val model = listOfNotNull(exif.getAttribute(ExifInterface.TAG_MAKE), exif.getAttribute(ExifInterface.TAG_MODEL)).joinToString(" ")
                if (model.isNotBlank()) add(R.string.details_camera to model)
                val parts = mutableListOf<String>()
                exif.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, 0.0).takeIf { it > 0 }?.let {
                    parts += ExposureMath.formatShutter((it * 1_000_000_000).toLong())
                }
                exif.getAttribute(ExifInterface.TAG_F_NUMBER)?.toDoubleOrNull()?.let { parts += "f/$it" }
                exif.getAttributeInt(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, 0).takeIf { it > 0 }?.let { parts += "ISO $it" }
                exif.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH, 0.0).takeIf { it > 0 }?.let { parts += "${"%.1f".format(it)}mm" }
                if (parts.isNotEmpty()) add(R.string.details_exposure to parts.joinToString(" · "))
                exif.latLong?.let { add(R.string.details_location to "%.5f, %.5f".format(it[0], it[1])) }
            }
        }
    }.getOrNull().orEmpty()

    private inner class PageAdapter : RecyclerView.Adapter<PageAdapter.Holder>() {

        private val bound = mutableSetOf<Holder>()

        inner class Holder(val binding: ItemViewerPageBinding) : RecyclerView.ViewHolder(binding.root) {
            var job: Job? = null
        }

        override fun getItemCount() = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            Holder(ItemViewerPageBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val item = items[position]
            val b = holder.binding
            bound += holder
            b.video.isVisible = false
            b.image.isVisible = true
            b.image.setImageDrawable(null)
            b.image.onSingleTap = { toggleChrome() }
            b.playButton.isVisible = item.isVideo
            b.image.contentDescription = item.displayName
            b.playButton.setOnClickListener { play(holder, item) }
            holder.job?.cancel()
            val maxPx = maxOf(resources.displayMetrics.widthPixels, resources.displayMetrics.heightPixels)
            holder.job = lifecycleScope.launch {
                val bitmap = if (item.isVideo) {
                    repository.thumbnail(item, maxPx / 2)
                } else {
                    withContext(Dispatchers.IO) { BitmapDecoding.decode(contentResolver, item.uri, maxPx) }
                }
                b.image.setImageBitmap(bitmap)
            }
        }

        private fun play(holder: Holder, item: MediaItem) {
            val b = holder.binding
            b.playButton.isVisible = false
            b.image.isVisible = false
            b.video.isVisible = true
            val controller = MediaController(this@MediaViewerActivity).also { it.setAnchorView(b.video) }
            b.video.setMediaController(controller)
            b.video.setVideoURI(item.uri)
            b.video.setOnCompletionListener { resetVideo(holder) }
            b.video.setOnPreparedListener { it.start() }
        }

        private fun resetVideo(holder: Holder) {
            val b = holder.binding
            b.video.stopPlayback()
            b.video.isVisible = false
            b.image.isVisible = true
            b.playButton.isVisible = true
        }

        fun stopVideos() {
            bound.filter { it.binding.video.isVisible }.forEach { resetVideo(it) }
        }

        override fun onViewRecycled(holder: Holder) {
            holder.job?.cancel()
            if (holder.binding.video.isVisible) resetVideo(holder)
            bound -= holder
        }
    }

    companion object {
        private const val BOTTOM_PAD_PX = 12
    }
}
