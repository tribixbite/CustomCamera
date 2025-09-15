package com.customcamera.app.settings

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.recyclerview.widget.RecyclerView
import com.customcamera.app.R

/**
 * RecyclerView adapter for displaying settings sections and items
 */
class SettingsAdapter(
    private var sections: List<SettingsSection>,
    private val onSettingChanged: (SettingsItem, Any) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = mutableListOf<SettingsDisplayItem>()

    init {
        updateItems()
    }

    fun updateSections(newSections: List<SettingsSection>) {
        sections = newSections
        updateItems()
        notifyDataSetChanged()
    }

    private fun updateItems() {
        items.clear()
        sections.forEach { section ->
            items.add(SettingsDisplayItem.Header(section))
            section.settings.forEach { setting ->
                items.add(SettingsDisplayItem.Setting(setting))
            }
        }
    }

    override fun getItemCount(): Int = items.size

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is SettingsDisplayItem.Header -> VIEW_TYPE_HEADER
            is SettingsDisplayItem.Setting -> {
                when ((items[position] as SettingsDisplayItem.Setting).item) {
                    is SettingsItem.Switch -> VIEW_TYPE_SWITCH
                    is SettingsItem.Slider -> VIEW_TYPE_SLIDER
                    is SettingsItem.Dropdown -> VIEW_TYPE_DROPDOWN
                    is SettingsItem.TextInput -> VIEW_TYPE_TEXT_INPUT
                    is SettingsItem.Button -> VIEW_TYPE_BUTTON
                    is SettingsItem.Info -> VIEW_TYPE_INFO
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)

        return when (viewType) {
            VIEW_TYPE_HEADER -> {
                val view = inflater.inflate(R.layout.item_settings_header, parent, false)
                HeaderViewHolder(view)
            }
            VIEW_TYPE_SWITCH -> {
                val view = inflater.inflate(R.layout.item_settings_switch, parent, false)
                SwitchViewHolder(view)
            }
            VIEW_TYPE_SLIDER -> {
                val view = inflater.inflate(R.layout.item_settings_slider, parent, false)
                SliderViewHolder(view)
            }
            VIEW_TYPE_DROPDOWN -> {
                val view = inflater.inflate(R.layout.item_settings_dropdown, parent, false)
                DropdownViewHolder(view)
            }
            VIEW_TYPE_TEXT_INPUT -> {
                val view = inflater.inflate(R.layout.item_settings_text_input, parent, false)
                TextInputViewHolder(view)
            }
            VIEW_TYPE_BUTTON -> {
                val view = inflater.inflate(R.layout.item_settings_button, parent, false)
                ButtonViewHolder(view)
            }
            VIEW_TYPE_INFO -> {
                val view = inflater.inflate(R.layout.item_settings_info, parent, false)
                InfoViewHolder(view)
            }
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is SettingsDisplayItem.Header -> {
                (holder as HeaderViewHolder).bind(item.section)
            }
            is SettingsDisplayItem.Setting -> {
                when (holder) {
                    is SwitchViewHolder -> holder.bind(item.item as SettingsItem.Switch, onSettingChanged)
                    is SliderViewHolder -> holder.bind(item.item as SettingsItem.Slider, onSettingChanged)
                    is DropdownViewHolder -> holder.bind(item.item as SettingsItem.Dropdown, onSettingChanged)
                    is TextInputViewHolder -> holder.bind(item.item as SettingsItem.TextInput, onSettingChanged)
                    is ButtonViewHolder -> holder.bind(item.item as SettingsItem.Button, onSettingChanged)
                    is InfoViewHolder -> holder.bind(item.item as SettingsItem.Info)
                }
            }
        }
    }

    // ViewHolders
    class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val titleText: TextView = view.findViewById(R.id.headerTitle)
        private val iconView: ImageView = view.findViewById(R.id.headerIcon)

        fun bind(section: SettingsSection) {
            titleText.text = section.title
            iconView.setImageResource(section.icon)
        }
    }

    class SwitchViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val titleText: TextView = view.findViewById(R.id.settingTitle)
        private val descriptionText: TextView = view.findViewById(R.id.settingDescription)
        private val switch: Switch = view.findViewById(R.id.settingSwitch)

        fun bind(setting: SettingsItem.Switch, onChanged: (SettingsItem, Any) -> Unit) {
            titleText.text = setting.title
            descriptionText.text = setting.description
            switch.isChecked = setting.isChecked

            switch.setOnCheckedChangeListener { _, isChecked ->
                onChanged(setting, isChecked)
            }
        }
    }

    class SliderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val titleText: TextView = view.findViewById(R.id.settingTitle)
        private val descriptionText: TextView = view.findViewById(R.id.settingDescription)
        private val valueText: TextView = view.findViewById(R.id.settingValue)
        private val seekBar: SeekBar = view.findViewById(R.id.settingSeekBar)

        fun bind(setting: SettingsItem.Slider, onChanged: (SettingsItem, Any) -> Unit) {
            titleText.text = setting.title
            descriptionText.text = setting.description
            valueText.text = setting.currentValue.toString()

            seekBar.min = setting.min
            seekBar.max = setting.max
            seekBar.progress = setting.currentValue

            seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        valueText.text = progress.toString()
                        onChanged(setting, progress)
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
    }

    class DropdownViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val titleText: TextView = view.findViewById(R.id.settingTitle)
        private val descriptionText: TextView = view.findViewById(R.id.settingDescription)
        private val spinner: Spinner = view.findViewById(R.id.settingSpinner)

        fun bind(setting: SettingsItem.Dropdown, onChanged: (SettingsItem, Any) -> Unit) {
            titleText.text = setting.title
            descriptionText.text = setting.description

            val adapter = ArrayAdapter(
                spinner.context,
                android.R.layout.simple_spinner_item,
                setting.options.map { it.first }
            ).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }

            spinner.adapter = adapter

            // Set current selection
            val currentIndex = setting.options.indexOfFirst { it.second == setting.currentValue }
            if (currentIndex >= 0) {
                spinner.setSelection(currentIndex)
            }

            spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    val selectedValue = setting.options[position].second
                    onChanged(setting, selectedValue)
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        }
    }

    class TextInputViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val titleText: TextView = view.findViewById(R.id.settingTitle)
        private val descriptionText: TextView = view.findViewById(R.id.settingDescription)
        private val editText: EditText = view.findViewById(R.id.settingEditText)

        fun bind(setting: SettingsItem.TextInput, onChanged: (SettingsItem, Any) -> Unit) {
            titleText.text = setting.title
            descriptionText.text = setting.description
            editText.setText(setting.currentValue)
            editText.inputType = setting.inputType

            editText.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    onChanged(setting, editText.text.toString())
                }
            }
        }
    }

    class ButtonViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val titleText: TextView = view.findViewById(R.id.settingTitle)
        private val descriptionText: TextView = view.findViewById(R.id.settingDescription)
        private val button: Button = view.findViewById(R.id.settingButton)

        fun bind(setting: SettingsItem.Button, onChanged: (SettingsItem, Any) -> Unit) {
            titleText.text = setting.title
            descriptionText.text = setting.description
            button.text = setting.buttonText

            button.setOnClickListener {
                onChanged(setting, "clicked")
            }
        }
    }

    class InfoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val titleText: TextView = view.findViewById(R.id.settingTitle)
        private val descriptionText: TextView = view.findViewById(R.id.settingDescription)
        private val valueText: TextView = view.findViewById(R.id.settingValue)

        fun bind(setting: SettingsItem.Info) {
            titleText.text = setting.title
            descriptionText.text = setting.description
            valueText.text = setting.value
        }
    }

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_SWITCH = 1
        private const val VIEW_TYPE_SLIDER = 2
        private const val VIEW_TYPE_DROPDOWN = 3
        private const val VIEW_TYPE_TEXT_INPUT = 4
        private const val VIEW_TYPE_BUTTON = 5
        private const val VIEW_TYPE_INFO = 6
    }
}

/**
 * Internal class for representing display items in the adapter
 */
sealed class SettingsDisplayItem {
    data class Header(val section: SettingsSection) : SettingsDisplayItem()
    data class Setting(val item: SettingsItem) : SettingsDisplayItem()
}