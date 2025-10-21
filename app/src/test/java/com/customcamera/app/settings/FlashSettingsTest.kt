package com.customcamera.app.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.customcamera.app.engine.SettingsManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith

/**
 * Flash Mode Settings Tests
 *
 * Tests for flash mode settings:
 * - Persistence across instances
 * - StateFlow reactivity
 * - Default values
 * - Valid flash modes (auto, on, off, torch)
 *
 * Note: These are instrumented tests requiring Android context.
 * Run with: ./gradlew connectedAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class FlashSettingsTest {

    private lateinit var context: Context
    private lateinit var settingsManager: SettingsManager

    companion object {
        // Valid flash modes
        const val FLASH_AUTO = "auto"
        const val FLASH_ON = "on"
        const val FLASH_OFF = "off"
        const val FLASH_TORCH = "torch"
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
    // Flash Mode Default Value
    // ============================================================

    @Test
    fun testFlashModeDefaultValue() = runTest {
        val defaultValue = settingsManager.flashMode.first()
        assertEquals(
            "Default flash mode should be 'auto'",
            FLASH_AUTO,
            defaultValue
        )
    }

    // ============================================================
    // Flash Mode Persistence
    // ============================================================

    @Test
    fun testFlashModeAutoPersistence() = runTest {
        settingsManager.setFlashMode(FLASH_AUTO)

        assertEquals(
            "StateFlow should update immediately",
            FLASH_AUTO,
            settingsManager.flashMode.first()
        )

        val newSettings = SettingsManager(context)
        assertEquals(
            "Flash mode 'auto' should persist",
            FLASH_AUTO,
            newSettings.flashMode.first()
        )
    }

    @Test
    fun testFlashModeOnPersistence() = runTest {
        settingsManager.setFlashMode(FLASH_ON)

        assertEquals(
            "StateFlow should update immediately",
            FLASH_ON,
            settingsManager.flashMode.first()
        )

        val newSettings = SettingsManager(context)
        assertEquals(
            "Flash mode 'on' should persist",
            FLASH_ON,
            newSettings.flashMode.first()
        )
    }

    @Test
    fun testFlashModeOffPersistence() = runTest {
        settingsManager.setFlashMode(FLASH_OFF)

        assertEquals(
            "StateFlow should update immediately",
            FLASH_OFF,
            settingsManager.flashMode.first()
        )

        val newSettings = SettingsManager(context)
        assertEquals(
            "Flash mode 'off' should persist",
            FLASH_OFF,
            newSettings.flashMode.first()
        )
    }

    @Test
    fun testFlashModeTorchPersistence() = runTest {
        settingsManager.setFlashMode(FLASH_TORCH)

        assertEquals(
            "StateFlow should update immediately",
            FLASH_TORCH,
            settingsManager.flashMode.first()
        )

        val newSettings = SettingsManager(context)
        assertEquals(
            "Flash mode 'torch' should persist",
            FLASH_TORCH,
            newSettings.flashMode.first()
        )
    }

    // ============================================================
    // Flash Mode Cycling
    // ============================================================

    @Test
    fun testFlashModeCycling() = runTest {
        // Cycle through all flash modes
        val modes = listOf(FLASH_AUTO, FLASH_ON, FLASH_OFF, FLASH_TORCH)

        for (mode in modes) {
            settingsManager.setFlashMode(mode)
            assertEquals(
                "Flash mode should be $mode",
                mode,
                settingsManager.flashMode.first()
            )
        }

        // Verify final state persists
        val newSettings = SettingsManager(context)
        assertEquals(FLASH_TORCH, newSettings.flashMode.first())
    }

    @Test
    fun testFlashModeMultipleUpdates() = runTest {
        // Update multiple times
        settingsManager.setFlashMode(FLASH_ON)
        assertEquals(FLASH_ON, settingsManager.flashMode.first())

        settingsManager.setFlashMode(FLASH_OFF)
        assertEquals(FLASH_OFF, settingsManager.flashMode.first())

        settingsManager.setFlashMode(FLASH_AUTO)
        assertEquals(FLASH_AUTO, settingsManager.flashMode.first())

        settingsManager.setFlashMode(FLASH_TORCH)
        assertEquals(FLASH_TORCH, settingsManager.flashMode.first())
    }

    // ============================================================
    // Flash Mode Reactivity
    // ============================================================

    @Test
    fun testFlashModeReactivity() = runTest {
        var observedValue = ""
        val job = kotlinx.coroutines.launch {
            settingsManager.flashMode.collect { value ->
                observedValue = value
            }
        }

        // Give collector time to start
        kotlinx.coroutines.delay(50)

        // Update value
        settingsManager.setFlashMode(FLASH_ON)

        // Give StateFlow time to emit
        kotlinx.coroutines.delay(50)

        assertEquals("StateFlow should emit new value", FLASH_ON, observedValue)

        job.cancel()
    }

    @Test
    fun testFlashModeMultipleObservers() = runTest {
        var observer1Value = ""
        var observer2Value = ""

        val job1 = kotlinx.coroutines.launch {
            settingsManager.flashMode.collect { value ->
                observer1Value = value
            }
        }

        val job2 = kotlinx.coroutines.launch {
            settingsManager.flashMode.collect { value ->
                observer2Value = value
            }
        }

        // Give collectors time to start
        kotlinx.coroutines.delay(50)

        // Update value
        settingsManager.setFlashMode(FLASH_TORCH)

        // Give StateFlows time to emit
        kotlinx.coroutines.delay(50)

        assertEquals("Observer 1 should receive new value", FLASH_TORCH, observer1Value)
        assertEquals("Observer 2 should receive new value", FLASH_TORCH, observer2Value)

        job1.cancel()
        job2.cancel()
    }

    // ============================================================
    // Invalid/Custom Flash Modes
    // ============================================================

    @Test
    fun testFlashModeCustomValue() = runTest {
        // Custom values should be allowed (camera engine handles validation)
        settingsManager.setFlashMode("custom_mode")
        assertEquals("custom_mode", settingsManager.flashMode.first())

        // Persistence check
        val newSettings = SettingsManager(context)
        assertEquals("custom_mode", newSettings.flashMode.first())
    }

    @Test
    fun testFlashModeEmptyString() = runTest {
        settingsManager.setFlashMode("")
        assertEquals("", settingsManager.flashMode.first())

        // Persistence check
        val newSettings = SettingsManager(context)
        assertEquals("", newSettings.flashMode.first())
    }

    @Test
    fun testFlashModeCaseSensitivity() = runTest {
        // Flash modes might be case-sensitive
        val caseSensitiveModes = listOf(
            "AUTO",  // Uppercase
            "Auto",  // Mixed case
            "ON",
            "On",
            "OFF",
            "Off",
            "TORCH",
            "Torch"
        )

        for (mode in caseSensitiveModes) {
            settingsManager.setFlashMode(mode)
            assertEquals(
                "Flash mode '$mode' should be stored as-is",
                mode,
                settingsManager.flashMode.first()
            )
        }
    }

    // ============================================================
    // Stress Tests
    // ============================================================

    @Test
    fun testRapidFlashModeChanges() = runTest {
        val modes = listOf(FLASH_AUTO, FLASH_ON, FLASH_OFF, FLASH_TORCH)

        // Rapidly change flash mode
        repeat(100) { i ->
            settingsManager.setFlashMode(modes[i % modes.size])
        }

        // Final value should be modes[99 % 4] = modes[3] = FLASH_TORCH
        assertEquals(FLASH_TORCH, settingsManager.flashMode.first())

        // Persistence should work
        val newSettings = SettingsManager(context)
        assertEquals(FLASH_TORCH, newSettings.flashMode.first())
    }

    @Test
    fun testFlashModeWithSpecialCharacters() = runTest {
        val specialModes = listOf(
            "flash-mode",
            "flash_mode",
            "flash.mode",
            "flash mode",
            "flash/mode",
            "flash\\mode"
        )

        for (mode in specialModes) {
            settingsManager.setFlashMode(mode)
            assertEquals(mode, settingsManager.flashMode.first())
        }
    }

    @Test
    fun testFlashModeLongString() = runTest {
        val longMode = "flash_".repeat(100)
        settingsManager.setFlashMode(longMode)
        assertEquals(longMode, settingsManager.flashMode.first())

        // Persistence check
        val newSettings = SettingsManager(context)
        assertEquals(longMode, newSettings.flashMode.first())
    }

    // ============================================================
    // Settings Manager Recreaton
    // ============================================================

    @Test
    fun testSettingsManagerRecreationWithFlashMode() = runTest {
        settingsManager.setFlashMode(FLASH_ON)

        // Recreate settings manager multiple times
        repeat(5) {
            val newSettings = SettingsManager(context)
            assertEquals(
                "Flash mode should persist through recreation",
                FLASH_ON,
                newSettings.flashMode.first()
            )
        }
    }

    @Test
    fun testFlashModeAllValidModesPersist() = runTest {
        val validModes = listOf(FLASH_AUTO, FLASH_ON, FLASH_OFF, FLASH_TORCH)

        for (mode in validModes) {
            // Clear preferences
            context.getSharedPreferences("camera_settings", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit()

            // Create fresh settings manager
            val settings = SettingsManager(context)

            // Set mode
            settings.setFlashMode(mode)

            // Verify persistence
            val newSettings = SettingsManager(context)
            assertEquals(
                "Mode '$mode' should persist",
                mode,
                newSettings.flashMode.first()
            )
        }
    }

    @Test
    fun testConcurrentFlashModeChanges() = runTest {
        val modes = listOf(FLASH_AUTO, FLASH_ON, FLASH_OFF, FLASH_TORCH)

        val jobs = List(20) { i ->
            kotlinx.coroutines.launch {
                settingsManager.setFlashMode(modes[i % modes.size])
                kotlinx.coroutines.delay(5)
            }
        }

        jobs.forEach { it.join() }

        // Final value should be one of the valid modes
        val finalMode = settingsManager.flashMode.first()
        assertTrue(
            "Final mode should be one of the valid modes",
            finalMode in modes
        )
    }
}
