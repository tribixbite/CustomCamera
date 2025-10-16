package com.customcamera.app.ui

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.*
import androidx.core.content.ContextCompat
import com.customcamera.app.R
import com.customcamera.app.engine.plugins.CameraPlugin
import com.customcamera.app.engine.plugins.PluginCategory

/**
 * Expandable dropdown view for displaying and toggling camera plugins
 *
 * Features:
 * - Smooth expand/collapse animations
 * - Plugin grouping by category
 * - Individual plugin toggle switches
 * - Plugin icons and descriptions
 * - Material Design 3 styling
 */
class PluginDropdownView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private var isExpanded = false
    private val contentContainer: LinearLayout
    private val pluginItems = mutableListOf<View>()

    /**
     * Callback when a plugin is toggled
     */
    var onPluginToggled: ((plugin: CameraPlugin, enabled: Boolean) -> Unit)? = null

    init {
        orientation = VERTICAL

        // Create container for plugin items
        contentContainer = LinearLayout(context).apply {
            orientation = VERTICAL
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            )
            // Apply Material3-style background
            setBackgroundColor(Color.parseColor("#1F1F1F"))
            setPadding(
                dpToPx(8),
                dpToPx(8),
                dpToPx(8),
                dpToPx(12)
            )
            elevation = dpToPx(8).toFloat()

            // Initially hidden
            visibility = GONE
            alpha = 0f
        }

        addView(contentContainer)
    }

    /**
     * Populate the dropdown with plugins
     */
    fun setPlugins(plugins: List<CameraPlugin>) {
        // Clear existing items
        contentContainer.removeAllViews()
        pluginItems.clear()

        if (plugins.isEmpty()) {
            // Show "No plugins" message
            val emptyView = TextView(context).apply {
                text = "No plugins available"
                textSize = 14f
                setTextColor(Color.parseColor("#999999"))
                gravity = Gravity.CENTER
                setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
            }
            contentContainer.addView(emptyView)
            return
        }

        // Group plugins by category
        val pluginsByCategory = plugins.groupBy { it.category }

        // Add plugins by category
        pluginsByCategory.forEach { (category, categoryPlugins) ->
            // Add category header (if not OTHER and more than one category)
            if (pluginsByCategory.size > 1 && category != PluginCategory.OTHER) {
                val categoryHeader = createCategoryHeader(category)
                contentContainer.addView(categoryHeader)
            }

            // Add plugin items
            categoryPlugins.forEach { plugin ->
                val pluginItem = createPluginItem(plugin)
                contentContainer.addView(pluginItem)
                pluginItems.add(pluginItem)
            }

            // Add spacing between categories
            if (category != pluginsByCategory.keys.last()) {
                val spacer = View(context).apply {
                    layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(8))
                }
                contentContainer.addView(spacer)
            }
        }
    }

    /**
     * Create category header view
     */
    private fun createCategoryHeader(category: PluginCategory): View {
        return TextView(context).apply {
            text = category.displayName
            textSize = 12f
            setTextColor(Color.parseColor("#AAAAAA"))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(4))
        }
    }

    /**
     * Create individual plugin item view
     */
    private fun createPluginItem(plugin: CameraPlugin): View {
        val itemLayout = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, dpToPx(4), 0, dpToPx(4))
            }
            setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8))
            gravity = Gravity.CENTER_VERTICAL

            // Rounded background
            background = ContextCompat.getDrawable(context, R.drawable.enhanced_button_background)
            isClickable = true
            isFocusable = true
        }

        // Plugin icon
        val iconView = ImageView(context).apply {
            layoutParams = LayoutParams(dpToPx(24), dpToPx(24)).apply {
                marginEnd = dpToPx(12)
            }
            setImageResource(plugin.iconResId)
            setColorFilter(Color.WHITE)
        }
        itemLayout.addView(iconView)

        // Plugin info container (name + description)
        val infoLayout = LinearLayout(context).apply {
            orientation = VERTICAL
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        }

        // Plugin name
        val nameView = TextView(context).apply {
            text = plugin.displayName
            textSize = 14f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        infoLayout.addView(nameView)

        // Plugin description
        val descView = TextView(context).apply {
            text = plugin.description
            textSize = 11f
            setTextColor(Color.parseColor("#CCCCCC"))
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        infoLayout.addView(descView)

        itemLayout.addView(infoLayout)

        // Toggle switch
        val toggleSwitch = Switch(context).apply {
            layoutParams = LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT
            )
            isChecked = plugin.isEnabled

            // Handle toggle change
            setOnCheckedChangeListener { _, isChecked ->
                onPluginToggled?.invoke(plugin, isChecked)
            }
        }
        itemLayout.addView(toggleSwitch)

        // Make entire item clickable to toggle switch
        itemLayout.setOnClickListener {
            toggleSwitch.isChecked = !toggleSwitch.isChecked
        }

        return itemLayout
    }

    /**
     * Toggle dropdown visibility
     */
    fun toggle() {
        if (isExpanded) {
            collapse()
        } else {
            expand()
        }
    }

    /**
     * Expand the dropdown
     */
    fun expand() {
        if (isExpanded) return

        isExpanded = true
        contentContainer.visibility = VISIBLE

        // Animate alpha and slide down
        contentContainer.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(200)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()
    }

    /**
     * Collapse the dropdown
     */
    fun collapse() {
        if (!isExpanded) return

        isExpanded = false

        // Animate alpha and slide up
        contentContainer.animate()
            .alpha(0f)
            .translationY(-dpToPx(10).toFloat())
            .setDuration(150)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction {
                contentContainer.visibility = GONE
            }
            .start()
    }

    /**
     * Check if dropdown is expanded
     */
    fun isExpanded(): Boolean = isExpanded

    /**
     * Update plugin enabled state in UI
     */
    fun updatePluginState(pluginName: String, enabled: Boolean) {
        // Find the plugin item and update its switch
        pluginItems.forEach { item ->
            val switch = (item as? ViewGroup)?.let { findSwitchInView(it) }
            val nameView = (item as? ViewGroup)?.let { findTextViewInView(it) }

            if (nameView?.text == pluginName) {
                switch?.isChecked = enabled
            }
        }
    }

    /**
     * Helper to find Switch in a ViewGroup
     */
    private fun findSwitchInView(viewGroup: ViewGroup): Switch? {
        for (i in 0 until viewGroup.childCount) {
            val child = viewGroup.getChildAt(i)
            if (child is Switch) return child
            if (child is ViewGroup) {
                val switch = findSwitchInView(child)
                if (switch != null) return switch
            }
        }
        return null
    }

    /**
     * Helper to find first TextView in a ViewGroup
     */
    private fun findTextViewInView(viewGroup: ViewGroup): TextView? {
        for (i in 0 until viewGroup.childCount) {
            val child = viewGroup.getChildAt(i)
            if (child is TextView) return child
            if (child is ViewGroup) {
                val textView = findTextViewInView(child)
                if (textView != null) return textView
            }
        }
        return null
    }

    /**
     * Convert dp to pixels
     */
    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
}
