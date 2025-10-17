package com.customcamera.app.engine.plugins

import android.content.Context
import com.customcamera.app.engine.DebugLogger

/**
 * Container for common dependencies required by all camera plugins.
 *
 * This data class provides a clean way to pass shared dependencies to plugins
 * during instantiation via the PluginProvider.create() factory method.
 *
 * **Architecture Benefits:**
 * - **Single Parameter**: Pass one object instead of multiple parameters
 * - **Extensible**: Easy to add new dependencies without changing all plugin constructors
 * - **Type-Safe**: Kotlin data class ensures correct dependency types
 * - **Testable**: Easy to create mock dependencies for testing
 *
 * **Usage Example:**
 * ```kotlin
 * // In CameraEngine initialization
 * val dependencies = PluginDependencies(
 *     context = applicationContext,
 *     debugLogger = DebugLogger()
 * )
 *
 * // Create plugin using factory method
 * val plugin = GridOverlayPlugin.create(dependencies)
 * ```
 *
 * **Plugin Implementation:**
 * ```kotlin
 * class GridOverlayPlugin(
 *     private val context: Context,
 *     private val debugLogger: DebugLogger
 * ) : CameraPlugin() {
 *     companion object : PluginProvider {
 *         override fun create(dependencies: PluginDependencies): CameraPlugin {
 *             return GridOverlayPlugin(
 *                 context = dependencies.context,
 *                 debugLogger = dependencies.debugLogger
 *             )
 *         }
 *     }
 * }
 * ```
 *
 * **Testing Example:**
 * ```kotlin
 * @Test
 * fun testPluginCreation() {
 *     val mockContext = mock(Context::class.java)
 *     val mockLogger = mock(DebugLogger::class.java)
 *
 *     val dependencies = PluginDependencies(
 *         context = mockContext,
 *         debugLogger = mockLogger
 *     )
 *
 *     val plugin = GridOverlayPlugin.create(dependencies)
 *     assertNotNull(plugin)
 * }
 * ```
 *
 * **Design Principles:**
 * - Only include **long-lived, stable** dependencies
 * - Dependencies should be needed by **most** plugins (not plugin-specific)
 * - Avoid transient objects (camera sessions, image readers, etc.)
 * - Use lifecycle methods for transient state (plugin.onAttach(), onDetach())
 *
 * @property context Application context for accessing resources and system services.
 *                   This should be the Application context (not Activity context) to avoid memory leaks.
 * @property debugLogger Shared debug logger for plugin activity tracking and debugging.
 *                      All plugins use the same logger instance for centralized logging.
 *
 * @see PluginProvider
 * @see CameraPlugin
 */
data class PluginDependencies(
    /**
     * Application context for accessing Android resources and system services.
     *
     * **Purpose:**
     * - Access string resources for localization
     * - Access drawable resources for UI
     * - Query system services (CameraManager, WindowManager, etc.)
     * - Create views and layouts
     *
     * **Important:**
     * - This MUST be Application context, not Activity context
     * - Using Activity context can cause memory leaks
     * - Safe to store as long as it's Application context
     *
     * **Usage Example:**
     * ```kotlin
     * // Get string resource
     * val message = context.getString(R.string.plugin_enabled)
     *
     * // Get system service
     * val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
     *
     * // Create view
     * val overlayView = GridOverlayView(context)
     * ```
     */
    val context: Context,

    /**
     * Shared debug logger for plugin activity tracking.
     *
     * **Purpose:**
     * - Log plugin lifecycle events (initialize, cleanup)
     * - Log plugin processing results
     * - Track performance metrics
     * - Debug issues in production
     *
     * **Usage Guidelines:**
     * - Use structured logging with consistent format
     * - Include plugin name in all log messages
     * - Use appropriate log levels (DEBUG, INFO, WARN, ERROR)
     * - Avoid logging sensitive user data
     *
     * **Usage Example:**
     * ```kotlin
     * // Plugin initialization
     * debugLogger.logInfo("GridOverlayPlugin", "Initializing with rule-of-thirds grid")
     *
     * // Processing result
     * debugLogger.logDebug("BarcodePlugin", "Detected ${barcodes.size} barcodes")
     *
     * // Performance tracking
     * val startTime = System.currentTimeMillis()
     * // ... processing ...
     * val duration = System.currentTimeMillis() - startTime
     * debugLogger.logInfo("HDRPlugin", "HDR processing completed in ${duration}ms")
     *
     * // Error handling
     * try {
     *     // ... operation ...
     * } catch (e: Exception) {
     *     debugLogger.logError("RAWCapturePlugin", "RAW capture failed", e)
     * }
     * ```
     *
     * **Log Format Convention:**
     * ```
     * [PLUGIN_NAME] Message with details
     * [GridOverlayPlugin] Grid enabled: rule-of-thirds
     * [BarcodePlugin] Detected 2 barcodes: [QR_CODE, EAN_13]
     * ```
     */
    val debugLogger: DebugLogger
)

/**
 * Extension functions for common plugin dependency operations.
 */

/**
 * Resolves a string resource using the context.
 *
 * **Example:**
 * ```kotlin
 * val displayName = dependencies.getString(R.string.plugin_grid_display_name)
 * ```
 */
fun PluginDependencies.getString(resId: Int): String {
    return context.getString(resId)
}

/**
 * Resolves a formatted string resource using the context.
 *
 * **Example:**
 * ```kotlin
 * val message = dependencies.getString(R.string.plugin_enabled_message, "Grid Overlay")
 * // Result: "Grid Overlay enabled"
 * ```
 */
fun PluginDependencies.getString(resId: Int, vararg formatArgs: Any): String {
    return context.getString(resId, *formatArgs)
}

/**
 * Logs a plugin operation.
 *
 * **Example:**
 * ```kotlin
 * dependencies.logPlugin("GridOverlayPlugin", "Grid enabled", mapOf("type" to "rule-of-thirds"))
 * ```
 */
fun PluginDependencies.logPlugin(pluginName: String, operation: String, details: Map<String, Any> = emptyMap()) {
    debugLogger.logPlugin(pluginName, operation, details)
}
