package com.customcamera.app.engine.plugins

import com.customcamera.app.plugins.*

/**
 * Central registry of all available camera plugins.
 * Provides static access to plugin metadata for settings UI generation.
 */
object PluginRegistry {

    /**
     * Plugin metadata for UI generation
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
     * Get all registered plugins' metadata
     * This list should match the plugins registered in CameraActivityEngine
     */
    fun getAllPlugins(): List<PluginInfo> {
        return listOf(
            // Overlays category
            PluginInfo(
                name = "GridOverlay",
                displayName = "Grid Overlay",
                description = "Composition grids for better photo framing",
                iconResId = com.customcamera.app.R.drawable.ic_extension,
                category = PluginCategory.OVERLAYS,
                userToggleable = true
            ),
            PluginInfo(
                name = "Crop",
                displayName = "Pre-Shot Crop",
                description = "Crop photos before capturing",
                iconResId = com.customcamera.app.R.drawable.ic_camera,
                category = PluginCategory.CAPTURE,
                userToggleable = true
            ),

            // Analysis category
            PluginInfo(
                name = "Barcode",
                displayName = "Barcode Scanner",
                description = "Scan QR codes and barcodes in real-time",
                iconResId = com.customcamera.app.R.drawable.ic_focus,
                category = PluginCategory.ANALYSIS,
                userToggleable = true
            ),
            PluginInfo(
                name = "Histogram",
                displayName = "Histogram",
                description = "Real-time exposure histogram",
                iconResId = com.customcamera.app.R.drawable.ic_info,
                category = PluginCategory.ANALYSIS,
                userToggleable = true
            ),
            PluginInfo(
                name = "CameraInfo",
                displayName = "Camera Info",
                description = "Real-time camera information display",
                iconResId = com.customcamera.app.R.drawable.ic_info,
                category = PluginCategory.ANALYSIS,
                userToggleable = true
            ),
            PluginInfo(
                name = "ExposureAnalysis",
                displayName = "Exposure Analysis",
                description = "Analyze and optimize exposure",
                iconResId = com.customcamera.app.R.drawable.ic_info,
                category = PluginCategory.ANALYSIS,
                userToggleable = true
            ),
            PluginInfo(
                name = "MotionDetection",
                displayName = "Motion Detection",
                description = "Detect motion and trigger capture",
                iconResId = com.customcamera.app.R.drawable.ic_focus,
                category = PluginCategory.ANALYSIS,
                userToggleable = true
            ),
            PluginInfo(
                name = "QRScanner",
                displayName = "QR Scanner",
                description = "Specialized QR code scanning",
                iconResId = com.customcamera.app.R.drawable.ic_focus,
                category = PluginCategory.ANALYSIS,
                userToggleable = true
            ),
            PluginInfo(
                name = "SharpnessAnalysis",
                displayName = "Sharpness Analysis",
                description = "Analyze image sharpness and focus",
                iconResId = com.customcamera.app.R.drawable.ic_info,
                category = PluginCategory.ANALYSIS,
                userToggleable = true
            ),

            // Controls category
            PluginInfo(
                name = "AutoFocus",
                displayName = "Auto Focus",
                description = "Automatic focus control with tap-to-focus",
                iconResId = com.customcamera.app.R.drawable.ic_focus,
                category = PluginCategory.CONTROLS,
                userToggleable = true
            ),
            PluginInfo(
                name = "ExposureControl",
                displayName = "Exposure Control",
                description = "Manual exposure compensation and analysis",
                iconResId = com.customcamera.app.R.drawable.ic_settings,
                category = PluginCategory.CONTROLS,
                userToggleable = true
            ),
            PluginInfo(
                name = "ManualFocus",
                displayName = "Manual Focus",
                description = "Precise manual focus control",
                iconResId = com.customcamera.app.R.drawable.ic_focus,
                category = PluginCategory.CONTROLS,
                userToggleable = true
            ),
            PluginInfo(
                name = "ProControls",
                displayName = "Pro Controls",
                description = "Professional manual camera controls",
                iconResId = com.customcamera.app.R.drawable.ic_settings,
                category = PluginCategory.CONTROLS,
                userToggleable = true
            ),
            PluginInfo(
                name = "ManualControls",
                displayName = "Manual Controls",
                description = "Basic manual camera controls",
                iconResId = com.customcamera.app.R.drawable.ic_settings,
                category = PluginCategory.CONTROLS,
                userToggleable = true
            ),

            // AI Features category
            PluginInfo(
                name = "SmartScene",
                displayName = "Smart Scene",
                description = "AI-powered scene detection",
                iconResId = com.customcamera.app.R.drawable.ic_camera,
                category = PluginCategory.AI,
                userToggleable = true
            ),
            PluginInfo(
                name = "SmartAdjustments",
                displayName = "Smart Adjustments",
                description = "AI-based camera parameter optimization",
                iconResId = com.customcamera.app.R.drawable.ic_settings,
                category = PluginCategory.AI,
                userToggleable = true
            ),
            PluginInfo(
                name = "ObjectDetection",
                displayName = "Object Detection",
                description = "Real-time object recognition",
                iconResId = com.customcamera.app.R.drawable.ic_focus,
                category = PluginCategory.AI,
                userToggleable = true
            ),

            // Capture category
            PluginInfo(
                name = "DualCameraPiP",
                displayName = "Dual Camera PiP",
                description = "Picture-in-picture with dual cameras",
                iconResId = com.customcamera.app.R.drawable.ic_pip,
                category = PluginCategory.CAPTURE,
                userToggleable = true
            ),
            PluginInfo(
                name = "RAWCapture",
                displayName = "RAW Capture",
                description = "Capture photos in DNG/RAW format",
                iconResId = com.customcamera.app.R.drawable.ic_camera,
                category = PluginCategory.CAPTURE,
                userToggleable = true
            ),
            PluginInfo(
                name = "AdvancedVideoRecording",
                displayName = "Advanced Video",
                description = "Professional video recording features",
                iconResId = com.customcamera.app.R.drawable.ic_videocam,
                category = PluginCategory.CAPTURE,
                userToggleable = true
            ),
            PluginInfo(
                name = "NightMode",
                displayName = "Night Mode",
                description = "Low-light and long exposure photography",
                iconResId = com.customcamera.app.R.drawable.ic_night_mode,
                category = PluginCategory.CAPTURE,
                userToggleable = true
            ),
            PluginInfo(
                name = "HDR",
                displayName = "HDR Mode",
                description = "High dynamic range photography",
                iconResId = com.customcamera.app.R.drawable.ic_camera,
                category = PluginCategory.CAPTURE,
                userToggleable = true
            )
        )
    }

    /**
     * Get only user-toggleable plugins
     */
    fun getToggleablePlugins(): List<PluginInfo> {
        return getAllPlugins().filter { it.userToggleable }
    }

    /**
     * Get plugins grouped by category
     */
    fun getPluginsByCategory(): Map<PluginCategory, List<PluginInfo>> {
        return getToggleablePlugins().groupBy { it.category }
    }

    /**
     * Get plugin info by name
     */
    fun getPluginInfo(name: String): PluginInfo? {
        return getAllPlugins().find { it.name == name }
    }
}
