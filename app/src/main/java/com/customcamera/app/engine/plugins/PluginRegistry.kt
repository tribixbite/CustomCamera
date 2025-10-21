package com.customcamera.app.engine.plugins

import android.content.Context
import com.customcamera.app.plugins.*

/**
 * Central registry of all available camera plugins using Provider Pattern.
 *
 * **Single Source of Truth**: Each plugin defines its own metadata via companion object.
 * This registry simply maintains the list of plugin providers.
 *
 * **Usage**:
 * ```kotlin
 * val registry = PluginRegistry(context)
 * val supportedPlugins = registry.getSupportedProviders()
 * val dependencies = PluginDependencies(context, debugLogger)
 * supportedPlugins.forEach { provider ->
 *     val plugin = provider.create(dependencies)
 *     pluginManager.registerPlugin(plugin)
 * }
 * ```
 */
class PluginRegistry(private val context: Context) {

    /**
     * Plugin metadata for UI generation (backward compatibility)
     */
    data class PluginInfo(
        val name: String,
        val displayName: String,
        val description: String,
        val iconResId: Int,
        val category: PluginCategory,
        val userToggleable: Boolean
    )

    /**
     * Complete list of all plugin providers.
     *
     * **Adding a New Plugin**:
     * 1. Create plugin class with companion object implementing PluginProvider
     * 2. Add companion object reference to this list
     * 3. Done! Everything else auto-generates
     */
    private val allProviders: List<PluginProvider> = listOf(
        // OVERLAYS (1)
        GridOverlayPlugin,

        // ANALYSIS (7)
        BarcodePlugin,
        HistogramPlugin,
        CameraInfoPlugin,
        ExposureAnalysisPlugin,
        MotionDetectionPlugin,
        QRScannerPlugin,
        SharpnessAnalysisPlugin,

        // CONTROLS (4)
        AutoFocusPlugin,
        ExposureControlPlugin,
        ManualFocusPlugin,
        ProControlsPlugin,

        // AI (3)
        SmartScenePlugin,
        SmartAdjustmentsPlugin,
        ObjectDetectionPlugin,

        // CAPTURE (7)
        CropPlugin,
        DualCameraPiPPlugin,
        RAWCapturePlugin,
        AdvancedVideoRecordingPlugin,
        NightModePlugin,
        HDRPlugin,

        // DEBUG (1)
        DiagnosticOverlayPlugin
    )

    /**
     * Get all registered plugin providers
     */
    fun getAllProviders(): List<PluginProvider> {
        return allProviders
    }

    /**
     * Get only supported plugin providers (based on device capabilities)
     */
    fun getSupportedProviders(): List<PluginProvider> {
        return allProviders.filter { provider ->
            provider.isSupported(context)
        }
    }

    /**
     * Get supported and user-toggleable providers
     */
    fun getToggleableProviders(): List<PluginProvider> {
        return getSupportedProviders().filter { it.userToggleable }
    }

    /**
     * Get providers grouped by category
     */
    fun getProvidersByCategory(): Map<PluginCategory, List<PluginProvider>> {
        return getToggleableProviders().groupBy { it.category }
    }

    /**
     * Get provider by ID
     */
    fun getProviderById(id: String): PluginProvider? {
        return allProviders.find { it.id == id }
    }

    // ============================================================
    // Backward Compatibility Methods for UI
    // ============================================================

    /**
     * Get all plugins' metadata (backward compatibility)
     * Converts PluginProvider metadata to PluginInfo for existing UI code
     */
    fun getAllPlugins(): List<PluginInfo> {
        return allProviders.map { provider ->
            PluginInfo(
                name = provider.id,
                displayName = context.getString(provider.displayNameRes),
                description = context.getString(provider.descriptionRes),
                iconResId = provider.iconResId,
                category = provider.category,
                userToggleable = provider.userToggleable
            )
        }
    }

    /**
     * Get only user-toggleable plugins (backward compatibility)
     */
    fun getToggleablePlugins(): List<PluginInfo> {
        return getAllPlugins().filter { it.userToggleable }
    }

    /**
     * Get plugins grouped by category (backward compatibility)
     */
    fun getPluginsByCategory(): Map<PluginCategory, List<PluginInfo>> {
        return getToggleablePlugins().groupBy { it.category }
    }

    /**
     * Get plugin info by name (backward compatibility)
     */
    fun getPluginInfo(name: String): PluginInfo? {
        return getAllPlugins().find { it.name == name }
    }

    companion object {
        /**
         * Total number of registered plugins
         */
        fun getTotalPluginCount(): Int = 23 // Updated as plugins are added
    }
}
