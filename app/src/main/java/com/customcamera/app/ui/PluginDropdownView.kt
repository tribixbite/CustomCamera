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

        // Create container for plugin items with Material3 card styling
        contentContainer = LinearLayout(context).apply {
            orientation = VERTICAL
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            )
            // Apply Material3-style background matching settings cards
            setBackgroundColor(Color.parseColor("#1E1E1E"))
            setPadding(
                dpToPx(12),
                dpToPx(12),
                dpToPx(12),
                dpToPx(12)
            )
            // Material3 elevation
            elevation = dpToPx(4).toFloat()

            // Rounded corners (Material3 standard)
            clipToOutline = true
            outlineProvider = android.view.ViewOutlineProvider.BACKGROUND

            // Initially hidden
            visibility = GONE
            alpha = 0f
            scaleY = 0.9f // Start slightly scaled down for animation
        }

        addView(contentContainer)

        // Set rounded corner background drawable
        setCardBackground()
    }

    /**
     * Apply rounded corner background with stroke (Material3 card style)
     */
    private fun setCardBackground() {
        val drawable = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(12).toFloat()
            setColor(Color.parseColor("#1E1E1E"))
            setStroke(dpToPx(1), Color.parseColor("#2A2A2A"))
        }
        contentContainer.background = drawable
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
     * Create individual plugin item view (Material3 card style)
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
            setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10))
            gravity = Gravity.CENTER_VERTICAL

            // Material3 card-style background with rounded corners
            background = createItemBackground()
            isClickable = true
            isFocusable = true

            // Add ripple effect for touch feedback
            foreground = android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(Color.parseColor("#33FFFFFF")),
                null,
                createRippleMask()
            )
        }

        return buildPluginItemContent(itemLayout, plugin)
    }

    /**
     * Create Material3-style background for plugin item
     */
    private fun createItemBackground(): android.graphics.drawable.Drawable {
        return android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(8).toFloat()
            setColor(Color.parseColor("#252525"))
        }
    }

    /**
     * Create ripple mask for touch feedback
     */
    private fun createRippleMask(): android.graphics.drawable.Drawable {
        return android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(8).toFloat()
            setColor(Color.WHITE)
        }
    }

    /**
     * Build plugin item content (icon, text, switch)
     */
    private fun buildPluginItemContent(itemLayout: LinearLayout, plugin: CameraPlugin): View {

        // Plugin icon with primary color tint
        val iconView = ImageView(context).apply {
            layoutParams = LayoutParams(dpToPx(24), dpToPx(24)).apply {
                marginEnd = dpToPx(12)
            }
            setImageResource(plugin.iconResId)
            // Apply primary color tint like settings cards
            setColorFilter(ContextCompat.getColor(context, R.color.md_theme_primary))
        }
        itemLayout.addView(iconView)

        // Plugin info container (name + description)
        val infoLayout = LinearLayout(context).apply {
            orientation = VERTICAL
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        }

        // Plugin name with sans-serif-medium
        val nameView = TextView(context).apply {
            text = plugin.displayName
            textSize = 14f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        }
        infoLayout.addView(nameView)

        // Plugin description with better color
        val descView = TextView(context).apply {
            text = plugin.description
            textSize = 11f
            setTextColor(Color.parseColor("#B0B0B0"))
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            setLineSpacing(0f, 1.2f)
        }
        infoLayout.addView(descView)

        itemLayout.addView(infoLayout)

        // Toggle switch with Material styling
        val toggleSwitch = Switch(context).apply {
            layoutParams = LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = dpToPx(12)
            }
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
     * Expand the dropdown with smooth Material3-style animation
     */
    fun expand() {
        if (isExpanded) return

        isExpanded = true
        contentContainer.visibility = VISIBLE

        // Smooth expand animation: alpha + scale + slide
        contentContainer.animate()
            .alpha(1f)
            .scaleY(1f)
            .translationY(0f)
            .setDuration(250)
            .setInterpolator(android.view.animation.DecelerateInterpolator(1.5f))
            .start()
    }

    /**
     * Collapse the dropdown with smooth Material3-style animation
     */
    fun collapse() {
        if (!isExpanded) return

        isExpanded = false

        // Smooth collapse animation: alpha + scale + slide
        contentContainer.animate()
            .alpha(0f)
            .scaleY(0.9f)
            .translationY(-dpToPx(8).toFloat())
            .setDuration(200)
            .setInterpolator(android.view.animation.AccelerateInterpolator(1.2f))
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
