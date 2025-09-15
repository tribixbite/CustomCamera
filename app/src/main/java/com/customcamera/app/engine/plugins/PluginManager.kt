package com.customcamera.app.engine.plugins

import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.ImageProxy
import com.customcamera.app.engine.CameraContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages the lifecycle and execution of camera plugins.
 * Handles plugin registration, initialization, frame processing, and cleanup.
 */
class PluginManager {

    private val plugins = ConcurrentHashMap<String, CameraPlugin>()
    private val processingPlugins = mutableListOf<ProcessingPlugin>()
    private val uiPlugins = mutableListOf<UIPlugin>()
    private val controlPlugins = mutableListOf<ControlPlugin>()

    private var cameraContext: CameraContext? = null
    private var currentCamera: Camera? = null

    // Plugin execution scope for coroutines
    private val pluginScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // State flows for monitoring plugin system
    private val _registeredPlugins = MutableStateFlow<List<String>>(emptyList())
    private val _activeProcessingPlugins = MutableStateFlow<List<String>>(emptyList())

    val registeredPlugins: StateFlow<List<String>> = _registeredPlugins.asStateFlow()
    val activeProcessingPlugins: StateFlow<List<String>> = _activeProcessingPlugins.asStateFlow()

    // Performance tracking
    private val frameProcessingTimes = mutableMapOf<String, MutableList<Long>>()
    private var totalFramesProcessed = 0L

    /**
     * Initialize the plugin manager with camera context
     */
    suspend fun initialize(context: CameraContext) {
        this.cameraContext = context
        Log.i(TAG, "PluginManager initialized with context")

        // Initialize all registered plugins
        plugins.values.forEach { plugin ->
            try {
                plugin.initialize(context)
                Log.i(TAG, "✅ Plugin '${plugin.name}' initialized")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to initialize plugin '${plugin.name}'", e)
            }
        }
    }

    /**
     * Register a new plugin
     */
    fun registerPlugin(plugin: CameraPlugin) {
        Log.i(TAG, "Registering plugin: ${plugin.name}")

        // Check for name conflicts
        if (plugins.containsKey(plugin.name)) {
            Log.w(TAG, "Plugin '${plugin.name}' already registered, replacing existing")
        }

        plugins[plugin.name] = plugin

        // Add to specialized collections based on type
        when (plugin) {
            is ProcessingPlugin -> {
                processingPlugins.add(plugin)
                // Sort by priority (lower numbers = higher priority)
                processingPlugins.sortBy { it.priority }
                frameProcessingTimes[plugin.name] = mutableListOf()
            }
            is UIPlugin -> {
                uiPlugins.add(plugin)
                uiPlugins.sortBy { it.priority }
            }
            is ControlPlugin -> {
                controlPlugins.add(plugin)
                controlPlugins.sortBy { it.priority }
            }
        }

        // Initialize plugin if context is available
        cameraContext?.let { context ->
            pluginScope.launch {
                try {
                    plugin.initialize(context)
                    Log.i(TAG, "✅ Plugin '${plugin.name}' initialized on registration")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Failed to initialize plugin '${plugin.name}' on registration", e)
                }
            }
        }

        // Notify plugin is ready if camera is available
        currentCamera?.let { camera ->
            pluginScope.launch {
                try {
                    plugin.onCameraReady(camera)
                    Log.i(TAG, "✅ Plugin '${plugin.name}' notified of ready camera")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Failed to notify plugin '${plugin.name}' of camera ready", e)
                }
            }
        }

        updatePluginStates()
    }

    /**
     * Unregister a plugin
     */
    fun unregisterPlugin(pluginName: String) {
        Log.i(TAG, "Unregistering plugin: $pluginName")

        val plugin = plugins.remove(pluginName)
        if (plugin != null) {
            // Remove from specialized collections
            processingPlugins.removeAll { it.name == pluginName }
            uiPlugins.removeAll { it.name == pluginName }
            controlPlugins.removeAll { it.name == pluginName }
            frameProcessingTimes.remove(pluginName)

            // Cleanup plugin resources
            try {
                plugin.cleanup()
                Log.i(TAG, "✅ Plugin '$pluginName' cleaned up and unregistered")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error cleaning up plugin '$pluginName'", e)
            }
        } else {
            Log.w(TAG, "Plugin '$pluginName' not found for unregistration")
        }

        updatePluginStates()
    }

    /**
     * Get a registered plugin by name
     */
    fun getPlugin(name: String): CameraPlugin? = plugins[name]

    /**
     * Get all plugins of a specific type
     */
    fun <T : CameraPlugin> getPluginsOfType(clazz: Class<T>): List<T> {
        return plugins.values.filterIsInstance(clazz)
    }

    /**
     * Notify all plugins that camera is ready
     */
    suspend fun onCameraReady(camera: Camera) {
        currentCamera = camera
        Log.i(TAG, "Notifying ${plugins.size} plugins that camera is ready")

        // Notify all plugins in parallel
        val jobs = plugins.values.map { plugin ->
            pluginScope.async {
                try {
                    if (plugin.isEnabled) {
                        plugin.onCameraReady(camera)
                        Log.d(TAG, "✅ Plugin '${plugin.name}' notified of camera ready")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Plugin '${plugin.name}' failed in onCameraReady", e)
                }
                Unit
            }
        }

        // Wait for all plugins to complete
        jobs.awaitAll()
        Log.i(TAG, "All plugins notified of camera ready")
    }

    /**
     * Notify all plugins that camera is being released
     */
    suspend fun onCameraReleased(camera: Camera) {
        Log.i(TAG, "Notifying ${plugins.size} plugins that camera is being released")

        val jobs = plugins.values.map { plugin ->
            pluginScope.async {
                try {
                    if (plugin.isEnabled) {
                        plugin.onCameraReleased(camera)
                        Log.d(TAG, "✅ Plugin '${plugin.name}' notified of camera release")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Plugin '${plugin.name}' failed in onCameraReleased", e)
                }
                Unit
            }
        }

        jobs.awaitAll()
        currentCamera = null
        Log.i(TAG, "All plugins notified of camera release")
    }

    /**
     * Process a frame through all enabled processing plugins
     */
    fun processFrame(image: ImageProxy) {
        if (processingPlugins.isEmpty()) return

        totalFramesProcessed++
        val frameStartTime = System.currentTimeMillis()

        // Process frame through all enabled processing plugins
        processingPlugins.forEach { plugin ->
            if (plugin.isEnabled) {
                pluginScope.launch {
                    val pluginStartTime = System.currentTimeMillis()
                    try {
                        val result = plugin.processFrame(image)
                        val processingTime = System.currentTimeMillis() - pluginStartTime

                        // Track performance
                        frameProcessingTimes[plugin.name]?.let { times ->
                            times.add(processingTime)
                            // Keep only last 100 measurements for performance
                            if (times.size > 100) {
                                times.removeFirst()
                            }
                        }

                        Log.d(TAG, "Plugin '${plugin.name}' processed frame in ${processingTime}ms: $result")

                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Plugin '${plugin.name}' failed to process frame", e)
                    }
                }
            }
        }

        val totalFrameTime = System.currentTimeMillis() - frameStartTime
        if (totalFrameTime > 33) { // Warn if frame processing takes longer than ~30fps
            Log.w(TAG, "Frame processing took ${totalFrameTime}ms (may impact performance)")
        }
    }

    /**
     * Enable a plugin by name
     */
    fun enablePlugin(pluginName: String): Boolean {
        val plugin = plugins[pluginName]
        if (plugin != null) {
            plugin.enable()
            updatePluginStates()
            Log.i(TAG, "Plugin '$pluginName' enabled")
            return true
        } else {
            Log.w(TAG, "Cannot enable plugin '$pluginName' - not found")
            return false
        }
    }

    /**
     * Disable a plugin by name
     */
    fun disablePlugin(pluginName: String): Boolean {
        val plugin = plugins[pluginName]
        if (plugin != null) {
            plugin.disable()
            updatePluginStates()
            Log.i(TAG, "Plugin '$pluginName' disabled")
            return true
        } else {
            Log.w(TAG, "Cannot disable plugin '$pluginName' - not found")
            return false
        }
    }

    /**
     * Get performance statistics for all plugins
     */
    fun getPerformanceStats(): Map<String, PluginPerformanceStats> {
        return frameProcessingTimes.mapValues { (pluginName, times) ->
            if (times.isEmpty()) {
                PluginPerformanceStats(pluginName, 0, 0.0, 0, 0)
            } else {
                PluginPerformanceStats(
                    pluginName = pluginName,
                    totalFrames = times.size,
                    averageTimeMs = times.average(),
                    minTimeMs = times.minOrNull() ?: 0,
                    maxTimeMs = times.maxOrNull() ?: 0
                )
            }
        }
    }

    /**
     * Get summary of plugin system status
     */
    fun getSystemStatus(): PluginSystemStatus {
        val enabledPlugins = plugins.values.count { it.isEnabled }
        val totalPlugins = plugins.size

        return PluginSystemStatus(
            totalPlugins = totalPlugins,
            enabledPlugins = enabledPlugins,
            processingPlugins = processingPlugins.size,
            uiPlugins = uiPlugins.size,
            controlPlugins = controlPlugins.size,
            totalFramesProcessed = totalFramesProcessed,
            isInitialized = cameraContext != null
        )
    }

    /**
     * Clean up all plugins and resources
     */
    fun cleanup() {
        Log.i(TAG, "Cleaning up PluginManager...")

        // Cancel all ongoing plugin operations
        pluginScope.cancel()

        // Cleanup all plugins
        plugins.values.forEach { plugin ->
            try {
                plugin.cleanup()
                Log.d(TAG, "✅ Plugin '${plugin.name}' cleaned up")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error cleaning up plugin '${plugin.name}'", e)
            }
        }

        // Clear all collections
        plugins.clear()
        processingPlugins.clear()
        uiPlugins.clear()
        controlPlugins.clear()
        frameProcessingTimes.clear()

        cameraContext = null
        currentCamera = null
        totalFramesProcessed = 0L

        updatePluginStates()
        Log.i(TAG, "✅ PluginManager cleanup complete")
    }

    /**
     * Update state flows with current plugin information
     */
    private fun updatePluginStates() {
        _registeredPlugins.value = plugins.keys.toList()
        _activeProcessingPlugins.value = processingPlugins
            .filter { it.isEnabled }
            .map { it.name }
    }

    companion object {
        private const val TAG = "PluginManager"
    }
}

/**
 * Performance statistics for a plugin
 */
data class PluginPerformanceStats(
    val pluginName: String,
    val totalFrames: Int,
    val averageTimeMs: Double,
    val minTimeMs: Long,
    val maxTimeMs: Long
)

/**
 * Overall plugin system status
 */
data class PluginSystemStatus(
    val totalPlugins: Int,
    val enabledPlugins: Int,
    val processingPlugins: Int,
    val uiPlugins: Int,
    val controlPlugins: Int,
    val totalFramesProcessed: Long,
    val isInitialized: Boolean
)