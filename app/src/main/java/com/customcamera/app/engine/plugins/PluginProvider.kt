package com.customcamera.app.engine.plugins

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

/**
 * Provider interface for camera plugins that combines metadata definition with factory pattern.
 *
 * This interface should be implemented by each plugin's companion object to provide:
 * - Static metadata (name, description, icon, category)
 * - Device capability checking
 * - Plugin instantiation factory method
 *
 * **Architecture Benefits:**
 * - Single source of truth: Each plugin defines its own metadata
 * - No duplication: Metadata and instantiation in same place
 * - Type-safe: Resource IDs ensure localization support
 * - Testable: Can query metadata without instantiating plugin
 * - Scalable: Adding plugin requires only ONE location change
 *
 * **Example Implementation:**
 * ```kotlin
 * class GridOverlayPlugin(
 *     private val context: Context,
 *     private val logger: DebugLogger
 * ) : CameraPlugin() {
 *     // ... plugin implementation ...
 *
 *     companion object : PluginProvider {
 *         override val id = "grid_overlay"
 *         override val displayNameRes = R.string.plugin_grid_display_name
 *         override val descriptionRes = R.string.plugin_grid_description
 *         override val iconResId = R.drawable.ic_grid
 *         override val category = PluginCategory.OVERLAYS
 *         override val userToggleable = true
 *
 *         override fun isSupported(context: Context): Boolean = true
 *
 *         override fun create(dependencies: PluginDependencies): CameraPlugin {
 *             return GridOverlayPlugin(dependencies.context, dependencies.debugLogger)
 *         }
 *     }
 * }
 * ```
 *
 * **Usage in Registry:**
 * ```kotlin
 * class PluginRegistry(private val context: Context) {
 *     private val allProviders: List<PluginProvider> = listOf(
 *         GridOverlayPlugin,  // Companion object reference
 *         BarcodePlugin,
 *         CropPlugin,
 *         // ... all other plugins
 *     )
 *
 *     fun getSupportedPlugins(): List<PluginProvider> {
 *         return allProviders.filter { it.isSupported(context) }
 *     }
 * }
 * ```
 *
 * **Usage in CameraEngine:**
 * ```kotlin
 * fun initializePlugins() {
 *     val dependencies = PluginDependencies(context, debugLogger)
 *
 *     pluginRegistry.getSupportedPlugins()
 *         .map { provider -> provider.create(dependencies) }
 *         .forEach { plugin -> pluginManager.registerPlugin(plugin) }
 * }
 * ```
 *
 * @see PluginDependencies
 * @see CameraPlugin
 * @see PluginCategory
 */
interface PluginProvider {

    /**
     * Stable, non-translatable identifier for the plugin.
     *
     * This ID is used for:
     * - Settings persistence (SharedPreferences keys)
     * - Plugin lookup and registration
     * - Debug logging and analytics
     *
     * **Format**: lowercase_snake_case (e.g., "grid_overlay", "barcode_scanner")
     *
     * **Requirements**:
     * - Must be unique across all plugins
     * - Must not change (breaking change for settings)
     * - Must not contain special characters or spaces
     * - Should be descriptive and match the plugin's purpose
     *
     * **Example**: "grid_overlay" for GridOverlayPlugin
     */
    val id: String

    /**
     * User-facing display name resource ID.
     *
     * This string resource will be displayed to users in:
     * - Settings screen plugin toggles
     * - Plugin dropdown menu
     * - Toast notifications
     *
     * **Format**: `R.string.plugin_{id}_display_name`
     * **Example**: `R.string.plugin_grid_display_name` → "Grid Overlay"
     *
     * Using resource IDs (instead of hardcoded strings) enables:
     * - Full localization support (multiple languages)
     * - Easy updates without code changes
     * - Consistency across the app
     *
     * @see StringRes
     */
    @get:StringRes
    val displayNameRes: Int

    /**
     * User-facing description resource ID.
     *
     * This string resource provides a brief description of the plugin's functionality.
     * Displayed in:
     * - Settings screen under plugin name
     * - Plugin dropdown menu (if space permits)
     *
     * **Format**: `R.string.plugin_{id}_description`
     * **Example**: `R.string.plugin_grid_description` → "Composition grids for better framing"
     *
     * **Guidelines**:
     * - Keep under 60 characters for best readability
     * - Focus on user benefit, not technical details
     * - Use active voice ("Scan QR codes" not "QR code scanning")
     *
     * @see StringRes
     */
    @get:StringRes
    val descriptionRes: Int

    /**
     * Icon drawable resource ID for visual representation.
     *
     * This icon will be displayed in:
     * - Plugin dropdown menu (next to plugin name)
     * - Settings screen (if icon mode enabled)
     *
     * **Format**: Vector drawable XML (for scalability)
     * **Size**: 24dp x 24dp (Material Design standard)
     * **Color**: White (#FFFFFF) with alpha for dark mode compatibility
     *
     * **Icon Guidelines**:
     * - Use unique icon per plugin for easy visual differentiation
     * - Avoid reusing same icon for multiple plugins
     * - Follow Material Design icon principles
     * - Test visibility on dark backgrounds
     *
     * **Example**: `R.drawable.ic_grid` for GridOverlayPlugin
     *
     * @see DrawableRes
     */
    @get:DrawableRes
    val iconResId: Int

    /**
     * Plugin category for grouping in UI.
     *
     * Categories are used to:
     * - Group plugins in settings screen
     * - Organize plugin dropdown menu
     * - Filter plugins by functionality type
     *
     * **Available Categories**:
     * - `OVERLAYS`: Visual overlays (grid, crop, histogram)
     * - `ANALYSIS`: Frame analysis (barcode, motion, sharpness)
     * - `CONTROLS`: Camera controls (focus, exposure, zoom)
     * - `AI`: AI-powered features (scene detection, object recognition)
     * - `CAPTURE`: Capture modes (RAW, HDR, PiP, video)
     * - `OTHER`: Miscellaneous plugins
     *
     * **Example**: `PluginCategory.OVERLAYS` for GridOverlayPlugin
     *
     * @see PluginCategory
     */
    val category: PluginCategory

    /**
     * Whether users can enable/disable this plugin.
     *
     * **true**: Plugin appears in settings and can be toggled by user
     * **false**: Plugin is always enabled, hidden from user controls
     *
     * Most plugins should be `true` to give users control.
     *
     * Use `false` only for:
     * - Core functionality that must always be active
     * - System-level plugins not intended for user interaction
     *
     * **Example**: `true` for most plugins
     */
    val userToggleable: Boolean

    /**
     * Checks if this plugin is supported on the current device.
     *
     * This method is called during plugin registry initialization to filter out
     * plugins that cannot work on the current device due to:
     * - Missing hardware capabilities (e.g., RAW capture requires Camera2 RAW support)
     * - Insufficient OS version (e.g., features requiring Android 11+)
     * - Missing dependencies (e.g., ML Kit not available)
     *
     * **Implementation Guidelines**:
     * - Return `true` if plugin works on all devices (default case)
     * - Return `false` if device lacks required capability
     * - Check capabilities synchronously (no network calls)
     * - Log reason for unsupported status (for debugging)
     *
     * **Example - Always Supported:**
     * ```kotlin
     * override fun isSupported(context: Context): Boolean = true
     * ```
     *
     * **Example - RAW Capture (requires Camera2 RAW capability):**
     * ```kotlin
     * override fun isSupported(context: Context): Boolean {
     *     val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
     *     val backCameraId = manager.cameraIdList.firstOrNull {
     *         manager.getCameraCharacteristics(it)
     *             .get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
     *     } ?: return false
     *
     *     val characteristics = manager.getCameraCharacteristics(backCameraId)
     *     val capabilities = characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
     *
     *     return capabilities?.contains(CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_RAW) == true
     * }
     * ```
     *
     * @param context Android application context for capability checking
     * @return true if plugin is supported on this device, false otherwise
     */
    fun isSupported(context: Context): Boolean

    /**
     * Factory method to create an instance of the plugin.
     *
     * This method is called by CameraEngine when initializing plugins.
     * It receives all common dependencies needed by plugins and should
     * construct and return a new plugin instance.
     *
     * **Implementation Guidelines**:
     * - Use dependencies parameter for construction
     * - Do NOT store static references (can cause memory leaks)
     * - Do NOT perform heavy initialization here (use plugin.initialize() instead)
     * - Return a new instance each time (plugins are not singletons)
     *
     * **Example:**
     * ```kotlin
     * override fun create(dependencies: PluginDependencies): CameraPlugin {
     *     return GridOverlayPlugin(
     *         context = dependencies.context,
     *         debugLogger = dependencies.debugLogger
     *     )
     * }
     * ```
     *
     * **Dependency Injection:**
     * All plugins receive the same PluginDependencies, which contains:
     * - `context`: Application context for accessing resources and system services
     * - `debugLogger`: Shared logger for plugin activity tracking
     *
     * @param dependencies Common dependencies required by all plugins
     * @return New instance of the plugin
     * @see PluginDependencies
     */
    fun create(dependencies: PluginDependencies): CameraPlugin
}
