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
 * Focus Settings Tests
 *
 * Tests for auto focus mode and tap-to-focus settings:
 * - Persistence across instances
 * - Default values
 * - Valid focus modes
 * - Tap-to-focus enable/disable
 *
 * Note: These are instrumented tests requiring Android context.
 * Run with: ./gradlew connectedAndroidTest
 */
@RunWith(RobolectricTestRunner::class)
class FocusSettingsTest {

    private lateinit var context: Context
    private lateinit var settingsManager: SettingsManager

    companion object {
        // Common auto focus modes
        const val FOCUS_CONTINUOUS = "continuous"
        const val FOCUS_AUTO = "auto"
        const val FOCUS_MANUAL = "manual"
        const val FOCUS_MACRO = "macro"
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
    // Auto Focus Mode Tests
    // ============================================================

    @Test
    fun testAutoFocusModeDefaultValue() {
        val defaultValue = settingsManager.getAutoFocusMode()
        assertEquals(
            "Default auto focus mode should be 'continuous'",
            FOCUS_CONTINUOUS,
            defaultValue
        )
    }

    @Test
    fun testAutoFocusModePersistence() {
        settingsManager.setAutoFocusMode(FOCUS_AUTO)

        assertEquals(
            "Value should be set immediately",
            FOCUS_AUTO,
            settingsManager.getAutoFocusMode()
        )

        val newSettings = SettingsManager(context)
        assertEquals(
            "Auto focus mode should persist across instances",
            FOCUS_AUTO,
            newSettings.getAutoFocusMode()
        )
    }

    @Test
    fun testAutoFocusModeMultipleUpdates() {
        val modes = listOf(FOCUS_CONTINUOUS, FOCUS_AUTO, FOCUS_MANUAL, FOCUS_MACRO)

        for (mode in modes) {
            settingsManager.setAutoFocusMode(mode)
            assertEquals(
                "Focus mode '$mode' should be set correctly",
                mode,
                settingsManager.getAutoFocusMode()
            )
        }

        // Verify final value persists
        val newSettings = SettingsManager(context)
        assertEquals(FOCUS_MACRO, newSettings.getAutoFocusMode())
    }

    @Test
    fun testAutoFocusModeCustomValues() {
        val customModes = listOf(
            "infinity",
            "fixed",
            "edof",
            "continuous_video",
            "continuous_picture"
        )

        for (mode in customModes) {
            settingsManager.setAutoFocusMode(mode)
            assertEquals(mode, settingsManager.getAutoFocusMode())
        }
    }

    @Test
    fun testAutoFocusModeEmptyString() {
        settingsManager.setAutoFocusMode("")
        assertEquals("", settingsManager.getAutoFocusMode())

        val newSettings = SettingsManager(context)
        assertEquals("", newSettings.getAutoFocusMode())
    }

    @Test
    fun testAutoFocusModeCaseSensitivity() {
        val caseSensitiveModes = listOf(
            "CONTINUOUS",
            "Continuous",
            "AUTO",
            "Auto",
            "MANUAL",
            "Manual"
        )

        for (mode in caseSensitiveModes) {
            settingsManager.setAutoFocusMode(mode)
            assertEquals(
                "Focus mode '$mode' should be stored as-is",
                mode,
                settingsManager.getAutoFocusMode()
            )
        }
    }

    // ============================================================
    // Tap to Focus Tests
    // ============================================================

    @Test
    fun testTapToFocusDefaultValue() {
        val defaultValue = settingsManager.getTapToFocus()
        assertTrue(
            "Default tap-to-focus should be enabled (true)",
            defaultValue
        )
    }

    @Test
    fun testTapToFocusPersistence() {
        settingsManager.setTapToFocus(false)

        assertFalse(
            "Value should be set immediately",
            settingsManager.getTapToFocus()
        )

        val newSettings = SettingsManager(context)
        assertFalse(
            "Tap-to-focus should persist across instances",
            newSettings.getTapToFocus()
        )
    }

    @Test
    fun testTapToFocusToggle() {
        // Start with default (true)
        assertTrue(settingsManager.getTapToFocus())

        // Disable
        settingsManager.setTapToFocus(false)
        assertFalse(settingsManager.getTapToFocus())

        // Re-enable
        settingsManager.setTapToFocus(true)
        assertTrue(settingsManager.getTapToFocus())
    }

    @Test
    fun testTapToFocusMultipleToggles() {
        repeat(10) { i ->
            val enabled = (i % 2 == 0)
            settingsManager.setTapToFocus(enabled)
            assertEquals(enabled, settingsManager.getTapToFocus())
        }

        // Final value should be false (9 % 2 == 1, so false)
        assertFalse(settingsManager.getTapToFocus())

        // Persistence check
        val newSettings = SettingsManager(context)
        assertFalse(newSettings.getTapToFocus())
    }

    @Test
    fun testTapToFocusIdempotent() {
        // Setting same value multiple times should work
        settingsManager.setTapToFocus(true)
        settingsManager.setTapToFocus(true)
        settingsManager.setTapToFocus(true)
        assertTrue(settingsManager.getTapToFocus())

        settingsManager.setTapToFocus(false)
        settingsManager.setTapToFocus(false)
        settingsManager.setTapToFocus(false)
        assertFalse(settingsManager.getTapToFocus())
    }

    // ============================================================
    // Combined Tests
    // ============================================================

    @Test
    fun testAutoFocusModeAndTapToFocusIndependent() {
        // Set both values
        settingsManager.setAutoFocusMode(FOCUS_MANUAL)
        settingsManager.setTapToFocus(false)

        assertEquals(FOCUS_MANUAL, settingsManager.getAutoFocusMode())
        assertFalse(settingsManager.getTapToFocus())

        // Change focus mode, tap-to-focus unchanged
        settingsManager.setAutoFocusMode(FOCUS_AUTO)
        assertEquals(FOCUS_AUTO, settingsManager.getAutoFocusMode())
        assertFalse(settingsManager.getTapToFocus())

        // Change tap-to-focus, mode unchanged
        settingsManager.setTapToFocus(true)
        assertEquals(FOCUS_AUTO, settingsManager.getAutoFocusMode())
        assertTrue(settingsManager.getTapToFocus())
    }

    @Test
    fun testFocusSettingsPersistTogether() {
        settingsManager.setAutoFocusMode(FOCUS_MACRO)
        settingsManager.setTapToFocus(false)

        val newSettings = SettingsManager(context)

        assertEquals(FOCUS_MACRO, newSettings.getAutoFocusMode())
        assertFalse(newSettings.getTapToFocus())
    }

    @Test
    fun testRapidAutoFocusModeChanges() {
        val modes = listOf(FOCUS_CONTINUOUS, FOCUS_AUTO, FOCUS_MANUAL)

        repeat(100) { i ->
            settingsManager.setAutoFocusMode(modes[i % modes.size])
        }

        // Final value should be modes[99 % 3] = modes[0] = FOCUS_CONTINUOUS
        assertEquals(FOCUS_CONTINUOUS, settingsManager.getAutoFocusMode())

        val newSettings = SettingsManager(context)
        assertEquals(FOCUS_CONTINUOUS, newSettings.getAutoFocusMode())
    }

    @Test
    fun testRapidTapToFocusChanges() {
        repeat(100) { i ->
            settingsManager.setTapToFocus(i % 2 == 0)
        }

        // Final value should be (99 % 2 == 0) = false
        assertFalse(settingsManager.getTapToFocus())

        val newSettings = SettingsManager(context)
        assertFalse(newSettings.getTapToFocus())
    }

    @Test
    fun testSettingsManagerRecreationWithFocusSettings() {
        settingsManager.setAutoFocusMode(FOCUS_AUTO)
        settingsManager.setTapToFocus(false)

        repeat(5) {
            val newSettings = SettingsManager(context)
            assertEquals(
                "Auto focus mode should persist through recreation",
                FOCUS_AUTO,
                newSettings.getAutoFocusMode()
            )
            assertFalse(
                "Tap-to-focus should persist through recreation",
                newSettings.getTapToFocus()
            )
        }
    }

    // ============================================================
    // Edge Cases
    // ============================================================

    @Test
    fun testAutoFocusModeLongString() {
        val longMode = "focus_".repeat(100)
        settingsManager.setAutoFocusMode(longMode)
        assertEquals(longMode, settingsManager.getAutoFocusMode())

        val newSettings = SettingsManager(context)
        assertEquals(longMode, newSettings.getAutoFocusMode())
    }

    @Test
    fun testAutoFocusModeSpecialCharacters() {
        val specialModes = listOf(
            "continuous-video",
            "continuous_picture",
            "auto.focus",
            "manual focus"
        )

        for (mode in specialModes) {
            settingsManager.setAutoFocusMode(mode)
            assertEquals(mode, settingsManager.getAutoFocusMode())
        }
    }

    @Test
    fun testFocusSettingsAllCombinations() {
        val modes = listOf(FOCUS_CONTINUOUS, FOCUS_AUTO, FOCUS_MANUAL)
        val tapToFocusValues = listOf(true, false)

        for (mode in modes) {
            for (tapToFocus in tapToFocusValues) {
                // Clear and recreate
                context.getSharedPreferences("camera_settings", Context.MODE_PRIVATE)
                    .edit()
                    .clear()
                    .commit()

                val settings = SettingsManager(context)

                // Set values
                settings.setAutoFocusMode(mode)
                settings.setTapToFocus(tapToFocus)

                // Verify persistence
                val newSettings = SettingsManager(context)
                assertEquals(mode, newSettings.getAutoFocusMode())
                assertEquals(tapToFocus, newSettings.getTapToFocus())
            }
        }
    }

    @Test
    fun testTapToFocusWithDifferentAutoFocusModes() {
        val modes = listOf(FOCUS_CONTINUOUS, FOCUS_AUTO, FOCUS_MANUAL, FOCUS_MACRO)

        for (mode in modes) {
            // Tap-to-focus enabled with each mode
            settingsManager.setAutoFocusMode(mode)
            settingsManager.setTapToFocus(true)

            assertEquals(mode, settingsManager.getAutoFocusMode())
            assertTrue(settingsManager.getTapToFocus())

            // Verify persistence
            val newSettings = SettingsManager(context)
            assertEquals(mode, newSettings.getAutoFocusMode())
            assertTrue(newSettings.getTapToFocus())

            // Tap-to-focus disabled with each mode
            settingsManager.setTapToFocus(false)

            assertEquals(mode, settingsManager.getAutoFocusMode())
            assertFalse(settingsManager.getTapToFocus())

            // Verify persistence
            val newSettings2 = SettingsManager(context)
            assertEquals(mode, newSettings2.getAutoFocusMode())
            assertFalse(newSettings2.getTapToFocus())
        }
    }
}
