package com.customcamera.app.settings

import androidx.annotation.DrawableRes

/**
 * Data models for the settings system
 */

/**
 * Represents a section of settings with a title and icon
 */
data class SettingsSection(
    val title: String,
    @DrawableRes val icon: Int,
    val settings: List<SettingsItem>
)

/**
 * Base class for all settings items
 */
sealed class SettingsItem {
    abstract val key: String
    abstract val title: String
    abstract val description: String

    /**
     * Switch/Toggle setting
     */
    data class Switch(
        override val key: String,
        override val title: String,
        override val description: String,
        val isChecked: Boolean
    ) : SettingsItem()

    /**
     * Slider/SeekBar setting
     */
    data class Slider(
        override val key: String,
        override val title: String,
        override val description: String,
        val min: Int,
        val max: Int,
        val currentValue: Int
    ) : SettingsItem()

    /**
     * Dropdown/Spinner setting
     */
    data class Dropdown(
        override val key: String,
        override val title: String,
        override val description: String,
        val options: List<Pair<String, String>>, // Display name to value
        val currentValue: String
    ) : SettingsItem()

    /**
     * Text input setting
     */
    data class TextInput(
        override val key: String,
        override val title: String,
        override val description: String,
        val currentValue: String,
        val inputType: Int = android.text.InputType.TYPE_CLASS_TEXT
    ) : SettingsItem()

    /**
     * Button action setting
     */
    data class Button(
        override val key: String,
        override val title: String,
        override val description: String,
        val buttonText: String = "Action"
    ) : SettingsItem()

    /**
     * Information display setting (read-only)
     */
    data class Info(
        override val key: String,
        override val title: String,
        override val description: String,
        val value: String
    ) : SettingsItem()
}