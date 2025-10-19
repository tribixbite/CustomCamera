package com.customcamera.app.ui.settings

import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.recyclerview.widget.RecyclerView
import com.customcamera.app.R

/**
 * RecyclerView adapter for settings list with multiple view types.
 *
 * Supports:
 * - Category headers
 * - Camera selection items
 * - Plugin toggle items
 * - Section dividers
 */
class SettingsAdapter(
    private val onCameraSelected: (Int) -> Unit,
    private val onPluginToggled: (String, Boolean) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = mutableListOf<SettingsListItem>()

    /**
     * Update the entire list of items
     */
    fun submitList(newItems: List<SettingsListItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    /**
     * Update a single plugin's enabled state
     */
    fun updatePluginState(pluginId: String, isEnabled: Boolean) {
        val index = items.indexOfFirst {
            it is SettingsListItem.PluginItem && it.pluginId == pluginId
        }
        if (index != -1) {
            val item = items[index] as SettingsListItem.PluginItem
            items[index] = item.copy(isEnabled = isEnabled)
            notifyItemChanged(index)
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is SettingsListItem.CategoryHeader -> SettingsListItem.VIEW_TYPE_CATEGORY_HEADER
            is SettingsListItem.CameraItem -> SettingsListItem.VIEW_TYPE_CAMERA_ITEM
            is SettingsListItem.PluginItem -> SettingsListItem.VIEW_TYPE_PLUGIN_ITEM
            is SettingsListItem.SectionDivider -> SettingsListItem.VIEW_TYPE_SECTION_DIVIDER
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            SettingsListItem.VIEW_TYPE_CATEGORY_HEADER -> {
                CategoryHeaderViewHolder(createCategoryHeaderView(parent))
            }
            SettingsListItem.VIEW_TYPE_CAMERA_ITEM -> {
                CameraItemViewHolder(createCameraItemView(parent))
            }
            SettingsListItem.VIEW_TYPE_PLUGIN_ITEM -> {
                PluginItemViewHolder(createPluginItemView(parent))
            }
            SettingsListItem.VIEW_TYPE_SECTION_DIVIDER -> {
                SectionDividerViewHolder(createSectionDividerView(parent))
            }
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is CategoryHeaderViewHolder -> holder.bind(items[position] as SettingsListItem.CategoryHeader)
            is CameraItemViewHolder -> holder.bind(items[position] as SettingsListItem.CameraItem, onCameraSelected)
            is PluginItemViewHolder -> holder.bind(items[position] as SettingsListItem.PluginItem, onPluginToggled)
            is SectionDividerViewHolder -> { /* No binding needed */ }
        }
    }

    override fun getItemCount(): Int = items.size

    // ========================================================================
    // ViewHolder Classes
    // ========================================================================

    /**
     * ViewHolder for category headers
     */
    class CategoryHeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val titleView: TextView = view.findViewById(R.id.category_title)

        fun bind(item: SettingsListItem.CategoryHeader) {
            titleView.text = item.categoryName
        }
    }

    /**
     * ViewHolder for camera selection items
     */
    class CameraItemViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val radioButton: RadioButton = view.findViewById(R.id.camera_radio)
        private val cameraName: TextView = view.findViewById(R.id.camera_name)

        fun bind(item: SettingsListItem.CameraItem, onSelected: (Int) -> Unit) {
            cameraName.text = item.cameraName
            radioButton.isChecked = item.isSelected

            itemView.setOnClickListener {
                onSelected(item.cameraIndex)
            }

            radioButton.setOnClickListener {
                onSelected(item.cameraIndex)
            }
        }
    }

    /**
     * ViewHolder for plugin toggle items
     */
    class PluginItemViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val icon: ImageView = view.findViewById(R.id.plugin_icon)
        private val name: TextView = view.findViewById(R.id.plugin_name)
        private val description: TextView = view.findViewById(R.id.plugin_description)
        private val toggle: com.google.android.material.switchmaterial.SwitchMaterial = view.findViewById(R.id.plugin_toggle)

        fun bind(item: SettingsListItem.PluginItem, onToggled: (String, Boolean) -> Unit) {
            icon.setImageResource(item.iconResId)
            name.text = item.displayName
            description.text = item.description
            toggle.isChecked = item.isEnabled

            // Prevent recursive callbacks
            toggle.setOnCheckedChangeListener(null)
            toggle.isChecked = item.isEnabled

            toggle.setOnCheckedChangeListener { _, isChecked ->
                onToggled(item.pluginId, isChecked)
            }

            itemView.setOnClickListener {
                toggle.isChecked = !toggle.isChecked
            }
        }
    }

    /**
     * ViewHolder for section dividers
     */
    class SectionDividerViewHolder(view: View) : RecyclerView.ViewHolder(view)

    // ========================================================================
    // View Creation Functions
    // ========================================================================

    private fun createCategoryHeaderView(parent: ViewGroup): View {
        val context = parent.context
        return LayoutInflater.from(context).inflate(
            R.layout.item_category_header,
            parent,
            false
        )
    }

    private fun createCameraItemView(parent: ViewGroup): View {
        val context = parent.context
        return LayoutInflater.from(context).inflate(
            R.layout.item_camera_selection,
            parent,
            false
        )
    }

    private fun createPluginItemView(parent: ViewGroup): View {
        val context = parent.context
        return LayoutInflater.from(context).inflate(
            R.layout.item_plugin_setting,
            parent,
            false
        )
    }

    private fun createSectionDividerView(parent: ViewGroup): View {
        val context = parent.context
        return View(context).apply {
            layoutParams = RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT,
                (16 * context.resources.displayMetrics.density).toInt()
            )
        }
    }
}
