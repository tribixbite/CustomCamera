package com.customcamera.app.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.customcamera.app.core.AspectOption
import com.customcamera.app.core.CaptureMode
import com.customcamera.app.core.FlashOption
import com.customcamera.app.core.GridOption
import com.customcamera.app.core.PhotoQualityOption
import com.customcamera.app.core.TimerOption
import com.customcamera.app.core.VideoQualityOption
import com.customcamera.app.core.VolumeKeyAction

/**
 * Typed access to the app's SharedPreferences.
 *
 * Keys are shared with res/xml/preferences.xml (the Settings screen writes
 * them directly), so they live in [Keys] and are referenced from XML via
 * string resources with identical values.
 */
class AppSettings(private val prefs: SharedPreferences) {

    constructor(context: Context) : this(PreferenceManager.getDefaultSharedPreferences(context.applicationContext))

    object Keys {
        // Settings-screen keys (must match res/values/pref_keys.xml).
        const val GRID = "pref_grid"
        const val LEVEL = "pref_level"
        const val HISTOGRAM = "pref_histogram"
        const val SHUTTER_SOUND = "pref_shutter_sound"
        const val HAPTICS = "pref_haptics"
        const val VOLUME_KEYS = "pref_volume_keys"
        const val PHOTO_QUALITY = "pref_photo_quality"
        const val JPEG_QUALITY = "pref_jpeg_quality"
        const val ULTRA_HDR = "pref_ultra_hdr"
        const val MIRROR_FRONT = "pref_mirror_front"
        const val LOCATION = "pref_location"
        const val VIDEO_QUALITY = "pref_video_quality"
        const val VIDEO_AUDIO = "pref_video_audio"
        const val VIDEO_STABILIZATION = "pref_video_stabilization"
        const val VIDEO_HDR = "pref_video_hdr"
        const val RAW = "pref_raw"
        const val REMEMBER_MODE = "pref_remember_mode"

        // Viewfinder state persisted between sessions (not on the settings screen).
        const val LAST_MODE = "state_mode"
        const val LAST_FACING_FRONT = "state_front"
        const val LAST_CAMERA_ID = "state_camera_id"
        const val FLASH = "state_flash"
        const val TIMER = "state_timer"
        const val ASPECT = "state_aspect"
    }

    var grid: GridOption
        get() = GridOption.fromKey(prefs.getString(Keys.GRID, GridOption.OFF.key))
        set(value) = prefs.edit { putString(Keys.GRID, value.key) }

    val showLevel: Boolean get() = prefs.getBoolean(Keys.LEVEL, true)
    val showHistogram: Boolean get() = prefs.getBoolean(Keys.HISTOGRAM, false)
    val shutterSound: Boolean get() = prefs.getBoolean(Keys.SHUTTER_SOUND, true)
    val haptics: Boolean get() = prefs.getBoolean(Keys.HAPTICS, true)
    val volumeKeyAction: VolumeKeyAction
        get() = VolumeKeyAction.fromKey(prefs.getString(Keys.VOLUME_KEYS, VolumeKeyAction.SHUTTER.key))

    val photoQuality: PhotoQualityOption
        get() = PhotoQualityOption.fromKey(prefs.getString(Keys.PHOTO_QUALITY, PhotoQualityOption.QUALITY.key))

    /** JPEG quality 50..100; stored as an Int by the SeekBarPreference. */
    val jpegQuality: Int get() = prefs.getInt(Keys.JPEG_QUALITY, DEFAULT_JPEG_QUALITY).coerceIn(50, 100)
    val ultraHdr: Boolean get() = prefs.getBoolean(Keys.ULTRA_HDR, false)
    val mirrorFront: Boolean get() = prefs.getBoolean(Keys.MIRROR_FRONT, true)
    var saveLocation: Boolean
        get() = prefs.getBoolean(Keys.LOCATION, false)
        set(value) = prefs.edit { putBoolean(Keys.LOCATION, value) }
    val raw: Boolean get() = prefs.getBoolean(Keys.RAW, false)

    val videoQuality: VideoQualityOption
        get() = VideoQualityOption.fromKey(prefs.getString(Keys.VIDEO_QUALITY, VideoQualityOption.FHD.key))
    val videoAudio: Boolean get() = prefs.getBoolean(Keys.VIDEO_AUDIO, true)
    val videoStabilization: Boolean get() = prefs.getBoolean(Keys.VIDEO_STABILIZATION, true)
    val videoHdr: Boolean get() = prefs.getBoolean(Keys.VIDEO_HDR, false)
    val rememberMode: Boolean get() = prefs.getBoolean(Keys.REMEMBER_MODE, true)

    var lastMode: CaptureMode
        get() = if (rememberMode) CaptureMode.fromKey(prefs.getString(Keys.LAST_MODE, null)) else CaptureMode.PHOTO
        set(value) = prefs.edit { putString(Keys.LAST_MODE, value.key) }

    var lastFacingFront: Boolean
        get() = prefs.getBoolean(Keys.LAST_FACING_FRONT, false)
        set(value) = prefs.edit { putBoolean(Keys.LAST_FACING_FRONT, value) }

    /** Explicit camera chosen in the lens picker; null means "default for facing". */
    var lastCameraId: String?
        get() = prefs.getString(Keys.LAST_CAMERA_ID, null)
        set(value) = prefs.edit { if (value == null) remove(Keys.LAST_CAMERA_ID) else putString(Keys.LAST_CAMERA_ID, value) }

    var flash: FlashOption
        get() = FlashOption.fromKey(prefs.getString(Keys.FLASH, FlashOption.OFF.key))
        set(value) = prefs.edit { putString(Keys.FLASH, value.key) }

    var timer: TimerOption
        get() = TimerOption.fromKey(prefs.getString(Keys.TIMER, TimerOption.OFF.key))
        set(value) = prefs.edit { putString(Keys.TIMER, value.key) }

    var aspect: AspectOption
        get() = AspectOption.fromKey(prefs.getString(Keys.ASPECT, AspectOption.RATIO_4_3.key))
        set(value) = prefs.edit { putString(Keys.ASPECT, value.key) }

    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.registerOnSharedPreferenceChangeListener(listener)

    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.unregisterOnSharedPreferenceChangeListener(listener)

    companion object {
        const val DEFAULT_JPEG_QUALITY = 95
    }
}
