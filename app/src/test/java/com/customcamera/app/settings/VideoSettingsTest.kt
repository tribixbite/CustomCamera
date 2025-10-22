package com.customcamera.app.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.robolectric.RobolectricTestRunner
import com.customcamera.app.engine.SettingsManager
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith

/**
 * Video Settings Tests
 *
 * Tests for video quality and video stabilization settings:
 * - Persistence across instances
 * - Default values
 * - Valid quality values
 * - Stabilization enable/disable
 *
 * Note: These are instrumented tests requiring Android context.
 * Run with: ./gradlew connectedAndroidTest
 */
@RunWith(RobolectricTestRunner::class)
class VideoSettingsTest {

    private lateinit var context: Context
    private lateinit var settingsManager: SettingsManager

    companion object {
        // Common video quality values
        const val QUALITY_720P = "720p"
        const val QUALITY_1080P = "1080p"
        const val QUALITY_4K = "4k"
        const val QUALITY_8K = "8k"
    }

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        // Clear any existing preferences before each test
        context.getSharedPreferences("camera_settings", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()

        settingsManager = SettingsManager(context)
    }

    @After
    fun tearDown() {
        // Clean up after each test
        context.getSharedPreferences("camera_settings", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    // ============================================================
    // Video Quality Tests
    // ============================================================

    @Test
    fun testVideoQualityDefaultValue() {
        val defaultValue = settingsManager.getVideoQuality()
        assertEquals(
            "Default video quality should be '1080p'",
            QUALITY_1080P,
            defaultValue
        )
    }

    @Test
    fun testVideoQualityPersistence() {
        settingsManager.setVideoQuality(QUALITY_4K)

        assertEquals(
            "Value should be set immediately",
            QUALITY_4K,
            settingsManager.getVideoQuality()
        )

        val newSettings = SettingsManager(context)
        assertEquals(
            "Video quality should persist across instances",
            QUALITY_4K,
            newSettings.getVideoQuality()
        )
    }

    @Test
    fun testVideoQualityMultipleUpdates() {
        val qualities = listOf(QUALITY_720P, QUALITY_1080P, QUALITY_4K, QUALITY_8K)

        for (quality in qualities) {
            settingsManager.setVideoQuality(quality)
            assertEquals(
                "Quality '$quality' should be set correctly",
                quality,
                settingsManager.getVideoQuality()
            )
        }

        // Verify final value persists
        val newSettings = SettingsManager(context)
        assertEquals(QUALITY_8K, newSettings.getVideoQuality())
    }

    @Test
    fun testVideoQualityCustomValues() {
        val customQualities = listOf(
            "auto",
            "max",
            "high",
            "medium",
            "low",
            "2160p",
            "1440p",
            "480p"
        )

        for (quality in customQualities) {
            settingsManager.setVideoQuality(quality)
            assertEquals(quality, settingsManager.getVideoQuality())
        }
    }

    @Test
    fun testVideoQualityEmptyString() {
        settingsManager.setVideoQuality("")
        assertEquals("", settingsManager.getVideoQuality())

        val newSettings = SettingsManager(context)
        assertEquals("", newSettings.getVideoQuality())
    }

    @Test
    fun testVideoQualitySpecialCharacters() {
        val specialQualities = listOf(
            "1080p-60fps",
            "4k_hdr",
            "ultra.hd",
            "high quality"
        )

        for (quality in specialQualities) {
            settingsManager.setVideoQuality(quality)
            assertEquals(quality, settingsManager.getVideoQuality())
        }
    }

    // ============================================================
    // Video Stabilization Tests
    // ============================================================

    @Test
    fun testVideoStabilizationDefaultValue() {
        val defaultValue = settingsManager.getVideoStabilization()
        assertTrue(
            "Default video stabilization should be enabled (true)",
            defaultValue
        )
    }

    @Test
    fun testVideoStabilizationPersistence() {
        settingsManager.setVideoStabilization(false)

        assertFalse(
            "Value should be set immediately",
            settingsManager.getVideoStabilization()
        )

        val newSettings = SettingsManager(context)
        assertFalse(
            "Video stabilization should persist across instances",
            newSettings.getVideoStabilization()
        )
    }

    @Test
    fun testVideoStabilizationToggle() {
        // Start with default (true)
        assertTrue(settingsManager.getVideoStabilization())

        // Disable
        settingsManager.setVideoStabilization(false)
        assertFalse(settingsManager.getVideoStabilization())

        // Re-enable
        settingsManager.setVideoStabilization(true)
        assertTrue(settingsManager.getVideoStabilization())
    }

    @Test
    fun testVideoStabilizationMultipleToggles() {
        repeat(10) { i ->
            val enabled = (i % 2 == 0)
            settingsManager.setVideoStabilization(enabled)
            assertEquals(enabled, settingsManager.getVideoStabilization())
        }

        // Final value should be false (9 % 2 == 1, so false)
        assertFalse(settingsManager.getVideoStabilization())

        // Persistence check
        val newSettings = SettingsManager(context)
        assertFalse(newSettings.getVideoStabilization())
    }

    @Test
    fun testVideoStabilizationIdempotent() {
        // Setting same value multiple times should work
        settingsManager.setVideoStabilization(true)
        settingsManager.setVideoStabilization(true)
        settingsManager.setVideoStabilization(true)
        assertTrue(settingsManager.getVideoStabilization())

        settingsManager.setVideoStabilization(false)
        settingsManager.setVideoStabilization(false)
        settingsManager.setVideoStabilization(false)
        assertFalse(settingsManager.getVideoStabilization())
    }

    // ============================================================
    // Combined Tests
    // ============================================================

    @Test
    fun testVideoQualityAndStabilizationIndependent() {
        // Set both values
        settingsManager.setVideoQuality(QUALITY_4K)
        settingsManager.setVideoStabilization(false)

        assertEquals(QUALITY_4K, settingsManager.getVideoQuality())
        assertFalse(settingsManager.getVideoStabilization())

        // Change quality, stabilization unchanged
        settingsManager.setVideoQuality(QUALITY_720P)
        assertEquals(QUALITY_720P, settingsManager.getVideoQuality())
        assertFalse(settingsManager.getVideoStabilization())

        // Change stabilization, quality unchanged
        settingsManager.setVideoStabilization(true)
        assertEquals(QUALITY_720P, settingsManager.getVideoQuality())
        assertTrue(settingsManager.getVideoStabilization())
    }

    @Test
    fun testVideoSettingsPersistTogether() {
        settingsManager.setVideoQuality(QUALITY_8K)
        settingsManager.setVideoStabilization(false)

        val newSettings = SettingsManager(context)

        assertEquals(QUALITY_8K, newSettings.getVideoQuality())
        assertFalse(newSettings.getVideoStabilization())
    }

    @Test
    fun testRapidVideoQualityChanges() {
        val qualities = listOf(QUALITY_720P, QUALITY_1080P, QUALITY_4K, QUALITY_8K)

        repeat(100) { i ->
            settingsManager.setVideoQuality(qualities[i % qualities.size])
        }

        // Final value should be qualities[99 % 4] = qualities[3] = QUALITY_8K
        assertEquals(QUALITY_8K, settingsManager.getVideoQuality())

        val newSettings = SettingsManager(context)
        assertEquals(QUALITY_8K, newSettings.getVideoQuality())
    }

    @Test
    fun testRapidStabilizationChanges() {
        repeat(100) { i ->
            settingsManager.setVideoStabilization(i % 2 == 0)
        }

        // Final value should be (99 % 2 == 0) = false
        assertFalse(settingsManager.getVideoStabilization())

        val newSettings = SettingsManager(context)
        assertFalse(newSettings.getVideoStabilization())
    }

    @Test
    fun testSettingsManagerRecreationWithVideoSettings() {
        settingsManager.setVideoQuality("2160p")
        settingsManager.setVideoStabilization(false)

        repeat(5) {
            val newSettings = SettingsManager(context)
            assertEquals(
                "Video quality should persist through recreation",
                "2160p",
                newSettings.getVideoQuality()
            )
            assertFalse(
                "Video stabilization should persist through recreation",
                newSettings.getVideoStabilization()
            )
        }
    }

    // ============================================================
    // Edge Cases
    // ============================================================

    @Test
    fun testVideoQualityLongString() {
        val longQuality = "quality_".repeat(100)
        settingsManager.setVideoQuality(longQuality)
        assertEquals(longQuality, settingsManager.getVideoQuality())

        val newSettings = SettingsManager(context)
        assertEquals(longQuality, newSettings.getVideoQuality())
    }

    @Test
    fun testVideoQualityNumericValues() {
        val numericQualities = listOf("720", "1080", "2160", "4320")

        for (quality in numericQualities) {
            settingsManager.setVideoQuality(quality)
            assertEquals(quality, settingsManager.getVideoQuality())
        }
    }

    @Test
    fun testVideoSettingsAllCombinations() {
        val qualities = listOf(QUALITY_720P, QUALITY_1080P, QUALITY_4K)
        val stabilizations = listOf(true, false)

        for (quality in qualities) {
            for (stabilization in stabilizations) {
                // Clear and recreate
                context.getSharedPreferences("camera_settings", Context.MODE_PRIVATE)
                    .edit()
                    .clear()
                    .commit()

                val settings = SettingsManager(context)

                // Set values
                settings.setVideoQuality(quality)
                settings.setVideoStabilization(stabilization)

                // Verify persistence
                val newSettings = SettingsManager(context)
                assertEquals(quality, newSettings.getVideoQuality())
                assertEquals(stabilization, newSettings.getVideoStabilization())
            }
        }
    }
}
