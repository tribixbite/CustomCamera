package com.customcamera.app.settings

import android.content.Context
import android.content.res.XmlResourceParser
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.customcamera.app.R
import com.customcamera.app.core.AspectOption
import com.customcamera.app.core.CaptureMode
import com.customcamera.app.core.FlashOption
import com.customcamera.app.core.GridOption
import com.customcamera.app.core.PhotoQualityOption
import com.customcamera.app.core.TimerOption
import com.customcamera.app.core.VideoQualityOption
import com.customcamera.app.core.VolumeKeyAction
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Guards the contract between res/xml/preferences.xml (written by the Settings
 * screen) and [AppSettings] (read by the camera): same keys, same defaults,
 * and every list value parses to a known option.
 */
@RunWith(AndroidJUnit4::class)
class SettingsContractTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val prefs = context.getSharedPreferences("settings_contract_test", Context.MODE_PRIVATE)

    private data class PrefDecl(val key: String, val defaultValue: String?)

    @Before
    fun clear() = prefs.edit().clear().commit().let { }

    @After
    fun cleanup() = clear()

    private fun declaredPreferences(): List<PrefDecl> {
        val parser = context.resources.getXml(R.xml.preferences)
        val result = mutableListOf<PrefDecl>()
        while (parser.next() != XmlResourceParser.END_DOCUMENT) {
            if (parser.eventType != XmlResourceParser.START_TAG) continue
            val key = parser.getAttributeValue(APP_NS, "key") ?: continue
            result += PrefDecl(key, parser.getAttributeValue(APP_NS, "defaultValue"))
        }
        return result
    }

    @Test
    fun everyPreferenceKeyIsKnownToAppSettings() {
        val known = AppSettings.Keys::class.java.declaredFields
            .filter { it.type == String::class.java }
            .map { it.isAccessible = true; it.get(null) as String }
            .toSet()
        val declared = declaredPreferences().map { it.key }.filter { it.startsWith("pref_") }
        assertTrue("no preferences parsed", declared.isNotEmpty())
        declared.forEach { assertTrue("preferences.xml key '$it' missing from AppSettings.Keys", it in known) }
    }

    @Test
    fun xmlDefaultsMatchAppSettingsDefaults() {
        val settings = AppSettings(prefs)
        val defaults = declaredPreferences().associate { it.key to it.defaultValue }
        assertEquals(defaults[AppSettings.Keys.GRID], settings.grid.key)
        assertEquals(defaults[AppSettings.Keys.LEVEL]?.toBoolean(), settings.showLevel)
        assertEquals(defaults[AppSettings.Keys.HISTOGRAM]?.toBoolean(), settings.showHistogram)
        assertEquals(defaults[AppSettings.Keys.PHOTO_QUALITY], settings.photoQuality.key)
        assertEquals(defaults[AppSettings.Keys.JPEG_QUALITY]?.toInt(), settings.jpegQuality)
        assertEquals(defaults[AppSettings.Keys.ULTRA_HDR]?.toBoolean(), settings.ultraHdr)
        assertEquals(defaults[AppSettings.Keys.RAW]?.toBoolean(), settings.raw)
        assertEquals(defaults[AppSettings.Keys.MIRROR_FRONT]?.toBoolean(), settings.mirrorFront)
        assertEquals(defaults[AppSettings.Keys.LOCATION]?.toBoolean(), settings.saveLocation)
        assertEquals(defaults[AppSettings.Keys.VIDEO_QUALITY], settings.videoQuality.key)
        assertEquals(defaults[AppSettings.Keys.VIDEO_AUDIO]?.toBoolean(), settings.videoAudio)
        assertEquals(defaults[AppSettings.Keys.VIDEO_STABILIZATION]?.toBoolean(), settings.videoStabilization)
        assertEquals(defaults[AppSettings.Keys.VIDEO_HDR]?.toBoolean(), settings.videoHdr)
        assertEquals(defaults[AppSettings.Keys.SHUTTER_SOUND]?.toBoolean(), settings.shutterSound)
        assertEquals(defaults[AppSettings.Keys.HAPTICS]?.toBoolean(), settings.haptics)
        assertEquals(defaults[AppSettings.Keys.VOLUME_KEYS], settings.volumeKeyAction.key)
        assertEquals(defaults[AppSettings.Keys.REMEMBER_MODE]?.toBoolean(), settings.rememberMode)
    }

    @Test
    fun listEntryValuesAllParse() {
        val res = context.resources
        res.getStringArray(R.array.grid_values).forEach { assertEquals(it, GridOption.fromKey(it).key) }
        res.getStringArray(R.array.volume_values).forEach { assertEquals(it, VolumeKeyAction.fromKey(it).key) }
        res.getStringArray(R.array.photo_quality_values).forEach { assertEquals(it, PhotoQualityOption.fromKey(it).key) }
        res.getStringArray(R.array.video_quality_values).forEach { assertEquals(it, VideoQualityOption.fromKey(it).key) }
        assertEquals(res.getStringArray(R.array.grid_entries).size, res.getStringArray(R.array.grid_values).size)
        assertEquals(res.getStringArray(R.array.video_quality_entries).size, res.getStringArray(R.array.video_quality_values).size)
    }

    @Test
    fun viewfinderStateRoundTrips() {
        val settings = AppSettings(prefs)
        settings.lastMode = CaptureMode.PRO
        settings.lastFacingFront = true
        settings.lastCameraId = "2"
        settings.flash = FlashOption.AUTO
        settings.timer = TimerOption.TEN
        settings.aspect = AspectOption.RATIO_1_1
        settings.grid = GridOption.GOLDEN

        val reread = AppSettings(prefs)
        assertEquals(CaptureMode.PRO, reread.lastMode)
        assertTrue(reread.lastFacingFront)
        assertEquals("2", reread.lastCameraId)
        assertEquals(FlashOption.AUTO, reread.flash)
        assertEquals(TimerOption.TEN, reread.timer)
        assertEquals(AspectOption.RATIO_1_1, reread.aspect)
        assertEquals(GridOption.GOLDEN, reread.grid)

        reread.lastCameraId = null
        assertNull(AppSettings(prefs).lastCameraId)
    }

    @Test
    fun rememberModeOffAlwaysStartsInPhoto() {
        val settings = AppSettings(prefs)
        settings.lastMode = CaptureMode.VIDEO
        prefs.edit().putBoolean(AppSettings.Keys.REMEMBER_MODE, false).commit()
        assertEquals(CaptureMode.PHOTO, settings.lastMode)
        assertFalse(settings.rememberMode)
    }

    @Test
    fun jpegQualityIsClamped() {
        prefs.edit().putInt(AppSettings.Keys.JPEG_QUALITY, 5).commit()
        assertEquals(50, AppSettings(prefs).jpegQuality)
        prefs.edit().putInt(AppSettings.Keys.JPEG_QUALITY, 500).commit()
        assertEquals(100, AppSettings(prefs).jpegQuality)
    }

    companion object {
        private const val APP_NS = "http://schemas.android.com/apk/res-auto"
    }
}
