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
import com.customcamera.app.core.PipCorner
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
        const val FOCUS_PEAKING = "pref_focus_peaking"
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
        const val TORCH_LEVEL = "state_torch_level"
        const val PIP_CORNER = "state_pip_corner"
        const val DUAL_FRONT_MAIN = "state_dual_front_main"
        const val PRO_ISO = "state_pro_iso"
        const val PRO_EXPOSURE_NS = "state_pro_exposure_ns"
        const val PRO_AWB = "state_pro_awb"
        const val PRO_FOCUS = "state_pro_focus"
    }

    var grid: GridOption
        get() = GridOption.fromKey(prefs.getString(Keys.GRID, GridOption.OFF.key))
        set(value) = prefs.edit { putString(Keys.GRID, value.key) }

    val showLevel: Boolean get() = prefs.getBoolean(Keys.LEVEL, true)
    val showHistogram: Boolean get() = prefs.getBoolean(Keys.HISTOGRAM, false)

    /** Highlight in-focus edges while focusing manually in Pro mode. */
    val focusPeaking: Boolean get() = prefs.getBoolean(Keys.FOCUS_PEAKING, true)
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

    /** Torch brightness level (1..max) on devices with adjustable torch strength; null = device default. */
    var torchLevel: Int?
        get() = prefs.getInt(Keys.TORCH_LEVEL, 0).takeIf { it > 0 }
        set(value) = prefs.edit { if (value == null) remove(Keys.TORCH_LEVEL) else putInt(Keys.TORCH_LEVEL, value) }

    /** Corner of the picture-in-picture inset in DUAL mode. */
    var pipCorner: PipCorner
        get() = PipCorner.fromKey(prefs.getString(Keys.PIP_CORNER, null))
        set(value) = prefs.edit { putString(Keys.PIP_CORNER, value.key) }

    /** DUAL mode: true = front camera fills the frame and the back camera is the inset. */
    var dualFrontMain: Boolean
        get() = prefs.getBoolean(Keys.DUAL_FRONT_MAIN, false)
        set(value) = prefs.edit { putBoolean(Keys.DUAL_FRONT_MAIN, value) }

    /**
     * Pro-mode manual values, restored on the next session. Each is null when
     * that control is on auto. White balance is a CONTROL_AWB_MODE_* value.
     */
    var proIso: Int?
        get() = prefs.getInt(Keys.PRO_ISO, 0).takeIf { it > 0 }
        set(value) = prefs.edit { if (value == null) remove(Keys.PRO_ISO) else putInt(Keys.PRO_ISO, value) }
    var proExposureNs: Long?
        get() = prefs.getLong(Keys.PRO_EXPOSURE_NS, 0L).takeIf { it > 0L }
        set(value) = prefs.edit { if (value == null) remove(Keys.PRO_EXPOSURE_NS) else putLong(Keys.PRO_EXPOSURE_NS, value) }
    var proAwb: Int?
        get() = prefs.getInt(Keys.PRO_AWB, -1).takeIf { it >= 0 }
        set(value) = prefs.edit { if (value == null) remove(Keys.PRO_AWB) else putInt(Keys.PRO_AWB, value) }
    var proFocusDiopters: Float?
        get() = prefs.getFloat(Keys.PRO_FOCUS, -1f).takeIf { it >= 0f }
        set(value) = prefs.edit { if (value == null) remove(Keys.PRO_FOCUS) else putFloat(Keys.PRO_FOCUS, value) }

    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.registerOnSharedPreferenceChangeListener(listener)

    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.unregisterOnSharedPreferenceChangeListener(listener)

    companion object {
        const val DEFAULT_JPEG_QUALITY = 95
    }
}
