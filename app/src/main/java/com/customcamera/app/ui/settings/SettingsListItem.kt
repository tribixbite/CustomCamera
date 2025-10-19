package com.customcamera.app.ui.settings

import com.customcamera.app.engine.plugins.PluginProvider
import com.customcamera.app.engine.plugins.PluginRegistry

/**
 * Sealed class representing different item types in the settings RecyclerView.
 *
 * This allows for a heterogeneous list with:
 * - Category headers
 * - Camera selection items
 * - Plugin toggle items
 * - Section dividers
 */
sealed class SettingsListItem {

    /**
     * Category header item (e.g., "Overlay Plugins", "Analysis Plugins")
     */
    data class CategoryHeader(
        val categoryName: String
    ) : SettingsListItem()

    /**
     * Camera selection item with radio button
     */
    data class CameraItem(
        val cameraIndex: Int,
        val cameraName: String,
        val isSelected: Boolean
    ) : SettingsListItem()

    /**
     * Plugin toggle item with switch
     */
    data class PluginItem(
        val provider: PluginProvider,
        val displayName: String,
        val description: String,
        val iconResId: Int,
        val isEnabled: Boolean,
        val pluginId: String  // Used for settings persistence
    ) : SettingsListItem() {
        companion object {
            /**
             * Create PluginItem from PluginRegistry.PluginInfo (backward compatibility)
             */
            fun fromPluginInfo(
                info: PluginRegistry.PluginInfo,
                isEnabled: Boolean
            ): PluginItem {
                // Note: We don't have direct access to provider here,
                // but we can use the info directly
                return PluginItem(
                    provider = DummyProvider, // Placeholder, not used in this context
                    displayName = info.displayName,
                    description = info.description,
                    iconResId = info.iconResId,
                    isEnabled = isEnabled,
                    pluginId = info.name
                )
            }
        }
    }

    /**
     * Section divider for visual separation
     */
    object SectionDivider : SettingsListItem()

    /**
     * Switch/Toggle setting item (e.g., "Enable Grid Overlay")
     */
    data class SwitchItem(
        val key: String,           // Settings key for persistence
        val title: String,         // Display title
        val description: String,   // Description/subtitle
        val isChecked: Boolean     // Current state
    ) : SettingsListItem()

    /**
     * Dropdown/Spinner setting item (e.g., "Photo Resolution")
     */
    data class DropdownItem(
        val key: String,                               // Settings key
        val title: String,                             // Display title
        val description: String,                       // Description/subtitle
        val options: List<Pair<String, String>>,      // (Display name, value) pairs
        val currentValue: String                       // Currently selected value
    ) : SettingsListItem()

    /**
     * Slider/SeekBar setting item (e.g., "Photo Quality: 1-100%")
     */
    data class SliderItem(
        val key: String,           // Settings key
        val title: String,         // Display title
        val description: String,   // Description/subtitle
        val min: Int,              // Minimum value
        val max: Int,              // Maximum value
        val currentValue: Int      // Current value
    ) : SettingsListItem()

    /**
     * Info/Read-only display item (e.g., "App Version: 2.0.1")
     */
    data class InfoItem(
        val key: String,           // Identifier
        val title: String,         // Display title
        val description: String,   // Description/subtitle
        val value: String          // Current value (read-only)
    ) : SettingsListItem()

    /**
     * Button/Action item (e.g., "Check for Updates")
     */
    data class ButtonItem(
        val key: String,           // Action identifier
        val title: String,         // Button text
        val description: String    // Description/subtitle
    ) : SettingsListItem()

    /**
     * Companion object with view type constants
     */
    companion object {
        const val VIEW_TYPE_CATEGORY_HEADER = 0
        const val VIEW_TYPE_CAMERA_ITEM = 1
        const val VIEW_TYPE_PLUGIN_ITEM = 2
        const val VIEW_TYPE_SECTION_DIVIDER = 3
        const val VIEW_TYPE_SWITCH_ITEM = 4
        const val VIEW_TYPE_DROPDOWN_ITEM = 5
        const val VIEW_TYPE_SLIDER_ITEM = 6
        const val VIEW_TYPE_INFO_ITEM = 7
        const val VIEW_TYPE_BUTTON_ITEM = 8
    }
}

/**
 * Dummy provider for backward compatibility
 * (Only used when creating items from PluginInfo)
 */
private object DummyProvider : PluginProvider {
    override val id: String = ""
    override val displayNameRes: Int = 0
    override val descriptionRes: Int = 0
    override val iconResId: Int = 0
    override val category: com.customcamera.app.engine.plugins.PluginCategory =
        com.customcamera.app.engine.plugins.PluginCategory.OTHER
    override val userToggleable: Boolean = false

    override fun isSupported(context: android.content.Context): Boolean = false

    override fun create(dependencies: com.customcamera.app.engine.plugins.PluginDependencies): com.customcamera.app.engine.plugins.CameraPlugin {
        throw UnsupportedOperationException("Dummy provider cannot create instances")
    }
}
