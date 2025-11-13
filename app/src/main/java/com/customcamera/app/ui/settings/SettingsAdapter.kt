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
 * - Switch settings
 * - Dropdown settings
 * - Slider settings
 * - Info displays
 * - Action buttons
 */
class SettingsAdapter(
    private val onCameraSelected: (cameraIndex: Int, isPipCamera: Boolean) -> Unit,
    private val onPluginToggled: (String, Boolean) -> Unit,
    private val onSwitchToggled: (String, Boolean) -> Unit = { _, _ -> },
    private val onDropdownChanged: (String, String) -> Unit = { _, _ -> },
    private val onSliderChanged: (String, Int) -> Unit = { _, _ -> },
    private val onButtonClicked: (String) -> Unit = { _ -> }
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
            is SettingsListItem.SwitchItem -> SettingsListItem.VIEW_TYPE_SWITCH_ITEM
            is SettingsListItem.DropdownItem -> SettingsListItem.VIEW_TYPE_DROPDOWN_ITEM
            is SettingsListItem.SliderItem -> SettingsListItem.VIEW_TYPE_SLIDER_ITEM
            is SettingsListItem.InfoItem -> SettingsListItem.VIEW_TYPE_INFO_ITEM
            is SettingsListItem.ButtonItem -> SettingsListItem.VIEW_TYPE_BUTTON_ITEM
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
            SettingsListItem.VIEW_TYPE_SWITCH_ITEM -> {
                SwitchItemViewHolder(createSwitchItemView(parent))
            }
            SettingsListItem.VIEW_TYPE_DROPDOWN_ITEM -> {
                DropdownItemViewHolder(createDropdownItemView(parent))
            }
            SettingsListItem.VIEW_TYPE_SLIDER_ITEM -> {
                SliderItemViewHolder(createSliderItemView(parent))
            }
            SettingsListItem.VIEW_TYPE_INFO_ITEM -> {
                InfoItemViewHolder(createInfoItemView(parent))
            }
            SettingsListItem.VIEW_TYPE_BUTTON_ITEM -> {
                ButtonItemViewHolder(createButtonItemView(parent))
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
            is SwitchItemViewHolder -> holder.bind(items[position] as SettingsListItem.SwitchItem, onSwitchToggled)
            is DropdownItemViewHolder -> holder.bind(items[position] as SettingsListItem.DropdownItem, onDropdownChanged)
            is SliderItemViewHolder -> holder.bind(items[position] as SettingsListItem.SliderItem, onSliderChanged)
            is InfoItemViewHolder -> holder.bind(items[position] as SettingsListItem.InfoItem)
            is ButtonItemViewHolder -> holder.bind(items[position] as SettingsListItem.ButtonItem, onButtonClicked)
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

        fun bind(item: SettingsListItem.CameraItem, onSelected: (Int, Boolean) -> Unit) {
            cameraName.text = item.cameraName
            radioButton.isChecked = item.isSelected

            itemView.setOnClickListener {
                onSelected(item.cameraIndex, item.isPipCamera)
            }

            radioButton.setOnClickListener {
                onSelected(item.cameraIndex, item.isPipCamera)
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

    /**
     * ViewHolder for switch/toggle settings
     */
    class SwitchItemViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val title: TextView = view.findViewById(R.id.settingTitle)
        private val description: TextView = view.findViewById(R.id.settingDescription)
        private val switch: com.google.android.material.materialswitch.MaterialSwitch = view.findViewById(R.id.settingSwitch)

        fun bind(item: SettingsListItem.SwitchItem, onToggled: (String, Boolean) -> Unit) {
            title.text = item.title
            description.text = item.description

            // Prevent recursive callbacks
            switch.setOnCheckedChangeListener(null)
            switch.isChecked = item.isChecked

            switch.setOnCheckedChangeListener { _, isChecked ->
                onToggled(item.key, isChecked)
            }

            itemView.setOnClickListener {
                switch.isChecked = !switch.isChecked
            }
        }
    }

    /**
     * ViewHolder for dropdown/spinner settings
     */
    class DropdownItemViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val title: TextView = view.findViewById(R.id.settingTitle)
        private val description: TextView = view.findViewById(R.id.settingDescription)
        private val spinner: Spinner = view.findViewById(R.id.settingSpinner)

        fun bind(item: SettingsListItem.DropdownItem, onChanged: (String, String) -> Unit) {
            title.text = item.title
            description.text = item.description

            // Create adapter with display names
            val displayNames = item.options.map { it.first }
            val adapter = ArrayAdapter(
                itemView.context,
                android.R.layout.simple_spinner_item,
                displayNames
            )
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinner.adapter = adapter

            // Set current selection
            val currentIndex = item.options.indexOfFirst { it.second == item.currentValue }
            if (currentIndex >= 0) {
                spinner.setSelection(currentIndex)
            }

            // Set listener
            spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    val selectedValue = item.options.getOrNull(position)?.second
                    if (selectedValue != null && selectedValue != item.currentValue) {
                        onChanged(item.key, selectedValue)
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {
                    // Do nothing
                }
            }
        }
    }

    /**
     * ViewHolder for slider/seekbar settings
     */
    class SliderItemViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val title: TextView = view.findViewById(R.id.settingTitle)
        private val description: TextView = view.findViewById(R.id.settingDescription)
        private val seekBar: SeekBar = view.findViewById(R.id.settingSeekBar)
        private val valueLabel: TextView = view.findViewById(R.id.settingValue)

        fun bind(item: SettingsListItem.SliderItem, onChanged: (String, Int) -> Unit) {
            title.text = item.title
            description.text = item.description

            // Configure seekbar
            seekBar.max = item.max - item.min
            seekBar.progress = item.currentValue - item.min
            valueLabel.text = item.currentValue.toString()

            // Set listener
            seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val value = progress + item.min
                    valueLabel.text = value.toString()
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {
                    // Do nothing
                }

                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    val value = (seekBar?.progress ?: 0) + item.min
                    onChanged(item.key, value)
                }
            })
        }
    }

    /**
     * ViewHolder for info/read-only display
     */
    class InfoItemViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val title: TextView = view.findViewById(R.id.settingTitle)
        private val description: TextView = view.findViewById(R.id.settingDescription)
        private val value: TextView = view.findViewById(R.id.settingValue)

        fun bind(item: SettingsListItem.InfoItem) {
            title.text = item.title
            description.text = item.description
            value.text = item.value
        }
    }

    /**
     * ViewHolder for action buttons
     */
    class ButtonItemViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val title: TextView = view.findViewById(R.id.settingTitle)
        private val description: TextView = view.findViewById(R.id.settingDescription)

        fun bind(item: SettingsListItem.ButtonItem, onClicked: (String) -> Unit) {
            title.text = item.title
            description.text = item.description

            itemView.setOnClickListener {
                onClicked(item.key)
            }
        }
    }

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

    private fun createSwitchItemView(parent: ViewGroup): View {
        return LayoutInflater.from(parent.context).inflate(
            R.layout.item_settings_switch,
            parent,
            false
        )
    }

    private fun createDropdownItemView(parent: ViewGroup): View {
        return LayoutInflater.from(parent.context).inflate(
            R.layout.item_settings_dropdown,
            parent,
            false
        )
    }

    private fun createSliderItemView(parent: ViewGroup): View {
        return LayoutInflater.from(parent.context).inflate(
            R.layout.item_settings_slider,
            parent,
            false
        )
    }

    private fun createInfoItemView(parent: ViewGroup): View {
        return LayoutInflater.from(parent.context).inflate(
            R.layout.item_settings_info,
            parent,
            false
        )
    }

    private fun createButtonItemView(parent: ViewGroup): View {
        return LayoutInflater.from(parent.context).inflate(
            R.layout.item_settings_button,
            parent,
            false
        )
    }
}
