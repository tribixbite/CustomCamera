package com.customcamera.app.gallery

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.customcamera.app.R
import com.customcamera.app.core.TimeFormat
import com.customcamera.app.databinding.ActivityGalleryBinding
import com.customcamera.app.databinding.ItemGalleryBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/** Grid of the app's own captures, newest first. */
class GalleryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGalleryBinding
    private val repository by lazy { MediaRepository.get(this) }
    private lateinit var adapter: GalleryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGalleryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.toolbar.setNavigationOnClickListener { finish() }

        val minCell = resources.displayMetrics.density * MIN_CELL_DP
        val span = (resources.displayMetrics.widthPixels / minCell).toInt().coerceIn(3, 8)
        val thumbPx = resources.displayMetrics.widthPixels / span
        adapter = GalleryAdapter(repository, thumbPx, lifecycleScope) { item ->
            startActivity(Intent(this, MediaViewerActivity::class.java).setData(item.uri))
        }
        binding.grid.layoutManager = GridLayoutManager(this, span)
        binding.grid.adapter = adapter
        binding.grid.setHasFixedSize(true)
        val basePadding = binding.grid.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.grid) { v, insets ->
            v.updatePadding(bottom = basePadding + insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom)
            insets
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                reload()
                @OptIn(kotlinx.coroutines.FlowPreview::class)
                repository.changes().debounce(300).collect { reload() }
            }
        }
    }

    private suspend fun reload() {
        val items = repository.loadAll()
        adapter.submitList(items)
        binding.emptyState.isVisible = items.isEmpty()
        binding.toolbar.subtitle = if (items.isEmpty()) null else getString(R.string.gallery_count, items.size)
    }

    companion object {
        private const val MIN_CELL_DP = 110
    }
}

/** FrameLayout that is always as tall as it is wide (grid cells). */
class SquareFrameLayout @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : FrameLayout(context, attrs) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) = super.onMeasure(widthMeasureSpec, widthMeasureSpec)
}

private class GalleryAdapter(
    private val repository: MediaRepository,
    private val thumbPx: Int,
    private val scope: kotlinx.coroutines.CoroutineScope,
    private val onClick: (MediaItem) -> Unit,
) : ListAdapter<MediaItem, GalleryAdapter.Holder>(Diff) {

    inner class Holder(val binding: ItemGalleryBinding) : RecyclerView.ViewHolder(binding.root) {
        var job: Job? = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemGalleryBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = getItem(position)
        val b = holder.binding
        b.image.setImageDrawable(null)
        b.videoBadge.isVisible = item.isVideo
        b.duration.text = TimeFormat.videoBadge(item.durationMs)
        b.root.contentDescription = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(item.dateTaken))
        b.root.setOnClickListener { onClick(item) }
        holder.job?.cancel()
        holder.job = scope.launch {
            repository.thumbnail(item, thumbPx)?.let { b.image.setImageBitmap(it) }
        }
    }

    override fun onViewRecycled(holder: Holder) {
        holder.job?.cancel()
        holder.binding.image.setImageDrawable(null)
    }

    private object Diff : DiffUtil.ItemCallback<MediaItem>() {
        override fun areItemsTheSame(a: MediaItem, b: MediaItem) = a.uri == b.uri
        override fun areContentsTheSame(a: MediaItem, b: MediaItem) = a == b
    }
}
