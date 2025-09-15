package com.customcamera.app.engine

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages camera settings and preferences with reactive state updates.
 * Provides type-safe access to camera configuration and user preferences.
 */
class SettingsManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // State flows for reactive settings
    private val _defaultCameraIndex = MutableStateFlow(getInt(KEY_DEFAULT_CAMERA_INDEX, 0))
    private val _photoQuality = MutableStateFlow(getInt(KEY_PHOTO_QUALITY, 95))
    private val _flashMode = MutableStateFlow(getString(KEY_FLASH_MODE, "auto"))
    private val _gridOverlay = MutableStateFlow(getBoolean(KEY_GRID_OVERLAY, false))
    private val _debugLogging = MutableStateFlow(getBoolean(KEY_DEBUG_LOGGING, false))

    val defaultCameraIndex: StateFlow<Int> = _defaultCameraIndex.asStateFlow()
    val photoQuality: StateFlow<Int> = _photoQuality.asStateFlow()
    val flashMode: StateFlow<String> = _flashMode.asStateFlow()
    val gridOverlay: StateFlow<Boolean> = _gridOverlay.asStateFlow()
    val debugLogging: StateFlow<Boolean> = _debugLogging.asStateFlow()

    /**
     * Camera Settings
     */
    fun setDefaultCameraIndex(index: Int) {
        putInt(KEY_DEFAULT_CAMERA_INDEX, index)
        _defaultCameraIndex.value = index
        Log.i(TAG, "Default camera index set to: $index")
    }

    fun setPhotoQuality(quality: Int) {
        val clampedQuality = quality.coerceIn(1, 100)
        putInt(KEY_PHOTO_QUALITY, clampedQuality)
        _photoQuality.value = clampedQuality
        Log.i(TAG, "Photo quality set to: $clampedQuality%")
    }

    fun setFlashMode(mode: String) {
        putString(KEY_FLASH_MODE, mode)
        _flashMode.value = mode
        Log.i(TAG, "Flash mode set to: $mode")
    }

    fun setGridOverlay(enabled: Boolean) {
        putBoolean(KEY_GRID_OVERLAY, enabled)
        _gridOverlay.value = enabled
        Log.i(TAG, "Grid overlay ${if (enabled) "enabled" else "disabled"}")
    }

    fun setDebugLogging(enabled: Boolean) {
        putBoolean(KEY_DEBUG_LOGGING, enabled)
        _debugLogging.value = enabled
        Log.i(TAG, "Debug logging ${if (enabled) "enabled" else "disabled"}")
    }

    /**
     * Photo Resolution Settings
     */
    fun getPhotoResolution(): String = getString(KEY_PHOTO_RESOLUTION, "auto")

    fun setPhotoResolution(resolution: String) {
        putString(KEY_PHOTO_RESOLUTION, resolution)
        Log.i(TAG, "Photo resolution set to: $resolution")
    }

    /**
     * Video Settings
     */
    fun getVideoQuality(): String = getString(KEY_VIDEO_QUALITY, "1080p")

    fun setVideoQuality(quality: String) {
        putString(KEY_VIDEO_QUALITY, quality)
        Log.i(TAG, "Video quality set to: $quality")
    }

    fun getVideoStabilization(): Boolean = getBoolean(KEY_VIDEO_STABILIZATION, true)

    fun setVideoStabilization(enabled: Boolean) {
        putBoolean(KEY_VIDEO_STABILIZATION, enabled)
        Log.i(TAG, "Video stabilization ${if (enabled) "enabled" else "disabled"}")
    }

    /**
     * Focus Settings
     */
    fun getAutoFocusMode(): String = getString(KEY_AUTO_FOCUS_MODE, "continuous")

    fun setAutoFocusMode(mode: String) {
        putString(KEY_AUTO_FOCUS_MODE, mode)
        Log.i(TAG, "Auto focus mode set to: $mode")
    }

    fun getTapToFocus(): Boolean = getBoolean(KEY_TAP_TO_FOCUS, true)

    fun setTapToFocus(enabled: Boolean) {
        putBoolean(KEY_TAP_TO_FOCUS, enabled)
        Log.i(TAG, "Tap to focus ${if (enabled) "enabled" else "disabled"}")
    }

    /**
     * UI Settings
     */
    fun getLevelIndicator(): Boolean = getBoolean(KEY_LEVEL_INDICATOR, false)

    fun setLevelIndicator(enabled: Boolean) {
        putBoolean(KEY_LEVEL_INDICATOR, enabled)
        Log.i(TAG, "Level indicator ${if (enabled) "enabled" else "disabled"}")
    }

    fun getHistogramOverlay(): Boolean = getBoolean(KEY_HISTOGRAM_OVERLAY, false)

    fun setHistogramOverlay(enabled: Boolean) {
        putBoolean(KEY_HISTOGRAM_OVERLAY, enabled)
        Log.i(TAG, "Histogram overlay ${if (enabled) "enabled" else "disabled"}")
    }

    fun getCameraInfoOverlay(): Boolean = getBoolean(KEY_CAMERA_INFO_OVERLAY, false)

    fun setCameraInfoOverlay(enabled: Boolean) {
        putBoolean(KEY_CAMERA_INFO_OVERLAY, enabled)
        Log.i(TAG, "Camera info overlay ${if (enabled) "enabled" else "disabled"}")
    }

    /**
     * Advanced Settings
     */
    fun getRawCapture(): Boolean = getBoolean(KEY_RAW_CAPTURE, false)

    fun setRawCapture(enabled: Boolean) {
        putBoolean(KEY_RAW_CAPTURE, enabled)
        Log.i(TAG, "RAW capture ${if (enabled) "enabled" else "disabled"}")
    }

    fun getPerformanceMonitoring(): Boolean = getBoolean(KEY_PERFORMANCE_MONITORING, false)

    fun setPerformanceMonitoring(enabled: Boolean) {
        putBoolean(KEY_PERFORMANCE_MONITORING, enabled)
        Log.i(TAG, "Performance monitoring ${if (enabled) "enabled" else "disabled"}")
    }

    /**
     * Plugin Settings
     */
    fun isPluginEnabled(pluginName: String): Boolean {
        return getBoolean("plugin_enabled_$pluginName", true)
    }

    fun setPluginEnabled(pluginName: String, enabled: Boolean) {
        putBoolean("plugin_enabled_$pluginName", enabled)
        Log.i(TAG, "Plugin '$pluginName' ${if (enabled) "enabled" else "disabled"}")
    }

    fun getPluginSetting(pluginName: String, settingKey: String, defaultValue: String): String {
        return getString("plugin_${pluginName}_$settingKey", defaultValue)
    }

    fun setPluginSetting(pluginName: String, settingKey: String, value: String) {
        putString("plugin_${pluginName}_$settingKey", value)
        Log.d(TAG, "Plugin '$pluginName' setting '$settingKey' = '$value'")
    }

    /**
     * Import/Export Settings
     */
    fun exportSettings(): Map<String, Any> {
        return prefs.all.filterValues { it != null }.mapValues { it.value!! }
    }

    fun importSettings(settings: Map<String, Any>) {
        val editor = prefs.edit()
        settings.forEach { (key, value) ->
            when (value) {
                is String -> editor.putString(key, value)
                is Int -> editor.putInt(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Float -> editor.putFloat(key, value)
                is Long -> editor.putLong(key, value)
                else -> Log.w(TAG, "Unsupported setting type for key: $key")
            }
        }
        editor.apply()
        refreshStateFlows()
        Log.i(TAG, "Settings imported: ${settings.size} items")
    }

    /**
     * Reset to defaults
     */
    fun resetToDefaults() {
        prefs.edit().clear().apply()
        refreshStateFlows()
        Log.i(TAG, "Settings reset to defaults")
    }

    /**
     * Get all current settings as a summary
     */
    fun getSettingsSummary(): SettingsSummary {
        return SettingsSummary(
            defaultCameraIndex = _defaultCameraIndex.value,
            photoQuality = _photoQuality.value,
            photoResolution = getPhotoResolution(),
            videoQuality = getVideoQuality(),
            flashMode = _flashMode.value,
            gridOverlay = _gridOverlay.value,
            levelIndicator = getLevelIndicator(),
            tapToFocus = getTapToFocus(),
            autoFocusMode = getAutoFocusMode(),
            rawCapture = getRawCapture(),
            debugLogging = _debugLogging.value,
            histogramOverlay = getHistogramOverlay(),
            performanceMonitoring = getPerformanceMonitoring()
        )
    }

    private fun refreshStateFlows() {
        _defaultCameraIndex.value = getInt(KEY_DEFAULT_CAMERA_INDEX, 0)
        _photoQuality.value = getInt(KEY_PHOTO_QUALITY, 95)
        _flashMode.value = getString(KEY_FLASH_MODE, "auto")
        _gridOverlay.value = getBoolean(KEY_GRID_OVERLAY, false)
        _debugLogging.value = getBoolean(KEY_DEBUG_LOGGING, false)
    }

    // Helper methods for SharedPreferences
    private fun getString(key: String, defaultValue: String): String =
        prefs.getString(key, defaultValue) ?: defaultValue

    private fun getInt(key: String, defaultValue: Int): Int =
        prefs.getInt(key, defaultValue)

    private fun getBoolean(key: String, defaultValue: Boolean): Boolean =
        prefs.getBoolean(key, defaultValue)

    private fun getFloat(key: String, defaultValue: Float): Float =
        prefs.getFloat(key, defaultValue)

    private fun putString(key: String, value: String) =
        prefs.edit().putString(key, value).apply()

    private fun putInt(key: String, value: Int) =
        prefs.edit().putInt(key, value).apply()

    private fun putBoolean(key: String, value: Boolean) =
        prefs.edit().putBoolean(key, value).apply()

    private fun putFloat(key: String, value: Float) =
        prefs.edit().putFloat(key, value).apply()

    companion object {
        private const val TAG = "SettingsManager"
        private const val PREFS_NAME = "custom_camera_settings"

        // Setting keys
        private const val KEY_DEFAULT_CAMERA_INDEX = "default_camera_index"
        private const val KEY_PHOTO_QUALITY = "photo_quality"
        private const val KEY_PHOTO_RESOLUTION = "photo_resolution"
        private const val KEY_VIDEO_QUALITY = "video_quality"
        private const val KEY_VIDEO_STABILIZATION = "video_stabilization"
        private const val KEY_FLASH_MODE = "flash_mode"
        private const val KEY_GRID_OVERLAY = "grid_overlay"
        private const val KEY_LEVEL_INDICATOR = "level_indicator"
        private const val KEY_AUTO_FOCUS_MODE = "auto_focus_mode"
        private const val KEY_TAP_TO_FOCUS = "tap_to_focus"
        private const val KEY_RAW_CAPTURE = "raw_capture"
        private const val KEY_DEBUG_LOGGING = "debug_logging"
        private const val KEY_HISTOGRAM_OVERLAY = "histogram_overlay"
        private const val KEY_CAMERA_INFO_OVERLAY = "camera_info_overlay"
        private const val KEY_PERFORMANCE_MONITORING = "performance_monitoring"
    }
}

/**
 * Settings summary data class
 */
data class SettingsSummary(
    val defaultCameraIndex: Int,
    val photoQuality: Int,
    val photoResolution: String,
    val videoQuality: String,
    val flashMode: String,
    val gridOverlay: Boolean,
    val levelIndicator: Boolean,
    val tapToFocus: Boolean,
    val autoFocusMode: String,
    val rawCapture: Boolean,
    val debugLogging: Boolean,
    val histogramOverlay: Boolean,
    val performanceMonitoring: Boolean
)