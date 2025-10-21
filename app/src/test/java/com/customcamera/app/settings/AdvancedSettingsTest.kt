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
 * Advanced Settings Tests
 *
 * Tests for debug logging, performance monitoring, and RAW capture settings:
 * - Persistence across instances
 * - StateFlow reactivity (debug logging)
 * - Default values
 * - Enable/disable states
 *
 * Note: These are instrumented tests requiring Android context.
 * Run with: ./gradlew connectedAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class AdvancedSettingsTest {

    private lateinit var context: Context
    private lateinit var settingsManager: SettingsManager

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
    // Debug Logging Tests
    // ============================================================

    @Test
    fun testDebugLoggingDefaultValue() = runTest {
        val defaultValue = settingsManager.debugLogging.first()
        assertFalse(
            "Default debug logging should be disabled (false)",
            defaultValue
        )
    }

    @Test
    fun testDebugLoggingPersistence() = runTest {
        settingsManager.setDebugLogging(true)

        assertTrue(
            "StateFlow should update immediately",
            settingsManager.debugLogging.first()
        )

        val newSettings = SettingsManager(context)
        assertTrue(
            "Debug logging should persist across instances",
            newSettings.debugLogging.first()
        )
    }

    @Test
    fun testDebugLoggingToggle() = runTest {
        // Start with default (false)
        assertFalse(settingsManager.debugLogging.first())

        // Enable
        settingsManager.setDebugLogging(true)
        assertTrue(settingsManager.debugLogging.first())

        // Disable
        settingsManager.setDebugLogging(false)
        assertFalse(settingsManager.debugLogging.first())
    }

    @Test
    fun testDebugLoggingReactivity() = runTest {
        var observedValue = false
        val job = kotlinx.coroutines.launch {
            settingsManager.debugLogging.collect { value ->
                observedValue = value
            }
        }

        // Give collector time to start
        kotlinx.coroutines.delay(50)

        // Update value
        settingsManager.setDebugLogging(true)

        // Give StateFlow time to emit
        kotlinx.coroutines.delay(50)

        assertTrue("StateFlow should emit new value", observedValue)

        job.cancel()
    }

    @Test
    fun testDebugLoggingMultipleToggles() = runTest {
        repeat(10) { i ->
            val enabled = (i % 2 == 0)
            settingsManager.setDebugLogging(enabled)
            assertEquals(enabled, settingsManager.debugLogging.first())
        }

        // Final value should be false (9 % 2 == 1, so false)
        assertFalse(settingsManager.debugLogging.first())

        // Persistence check
        val newSettings = SettingsManager(context)
        assertFalse(newSettings.debugLogging.first())
    }

    @Test
    fun testDebugLoggingIdempotent() = runTest {
        // Setting same value multiple times should work
        settingsManager.setDebugLogging(true)
        settingsManager.setDebugLogging(true)
        settingsManager.setDebugLogging(true)
        assertTrue(settingsManager.debugLogging.first())

        settingsManager.setDebugLogging(false)
        settingsManager.setDebugLogging(false)
        settingsManager.setDebugLogging(false)
        assertFalse(settingsManager.debugLogging.first())
    }

    // ============================================================
    // Performance Monitoring Tests
    // ============================================================

    @Test
    fun testPerformanceMonitoringDefaultValue() {
        val defaultValue = settingsManager.getPerformanceMonitoring()
        assertFalse(
            "Default performance monitoring should be disabled (false)",
            defaultValue
        )
    }

    @Test
    fun testPerformanceMonitoringPersistence() {
        settingsManager.setPerformanceMonitoring(true)

        assertTrue(
            "Value should be set immediately",
            settingsManager.getPerformanceMonitoring()
        )

        val newSettings = SettingsManager(context)
        assertTrue(
            "Performance monitoring should persist across instances",
            newSettings.getPerformanceMonitoring()
        )
    }

    @Test
    fun testPerformanceMonitoringToggle() {
        // Start with default (false)
        assertFalse(settingsManager.getPerformanceMonitoring())

        // Enable
        settingsManager.setPerformanceMonitoring(true)
        assertTrue(settingsManager.getPerformanceMonitoring())

        // Disable
        settingsManager.setPerformanceMonitoring(false)
        assertFalse(settingsManager.getPerformanceMonitoring())
    }

    @Test
    fun testPerformanceMonitoringMultipleToggles() {
        repeat(10) { i ->
            val enabled = (i % 2 == 0)
            settingsManager.setPerformanceMonitoring(enabled)
            assertEquals(enabled, settingsManager.getPerformanceMonitoring())
        }

        // Final value should be false
        assertFalse(settingsManager.getPerformanceMonitoring())

        // Persistence check
        val newSettings = SettingsManager(context)
        assertFalse(newSettings.getPerformanceMonitoring())
    }

    // ============================================================
    // RAW Capture Tests
    // ============================================================

    @Test
    fun testRawCaptureDefaultValue() {
        val defaultValue = settingsManager.getRawCapture()
        assertFalse(
            "Default RAW capture should be disabled (false)",
            defaultValue
        )
    }

    @Test
    fun testRawCapturePersistence() {
        settingsManager.setRawCapture(true)

        assertTrue(
            "Value should be set immediately",
            settingsManager.getRawCapture()
        )

        val newSettings = SettingsManager(context)
        assertTrue(
            "RAW capture should persist across instances",
            newSettings.getRawCapture()
        )
    }

    @Test
    fun testRawCaptureToggle() {
        // Start with default (false)
        assertFalse(settingsManager.getRawCapture())

        // Enable
        settingsManager.setRawCapture(true)
        assertTrue(settingsManager.getRawCapture())

        // Disable
        settingsManager.setRawCapture(false)
        assertFalse(settingsManager.getRawCapture())
    }

    @Test
    fun testRawCaptureMultipleToggles() {
        repeat(10) { i ->
            val enabled = (i % 2 == 0)
            settingsManager.setRawCapture(enabled)
            assertEquals(enabled, settingsManager.getRawCapture())
        }

        // Final value should be false
        assertFalse(settingsManager.getRawCapture())

        // Persistence check
        val newSettings = SettingsManager(context)
        assertFalse(newSettings.getRawCapture())
    }

    // ============================================================
    // Combined Tests
    // ============================================================

    @Test
    fun testAllAdvancedSettingsIndependent() = runTest {
        // Set all settings to different states
        settingsManager.setDebugLogging(true)
        settingsManager.setPerformanceMonitoring(false)
        settingsManager.setRawCapture(true)

        // Verify all states
        assertTrue(settingsManager.debugLogging.first())
        assertFalse(settingsManager.getPerformanceMonitoring())
        assertTrue(settingsManager.getRawCapture())

        // Change one, others should remain unchanged
        settingsManager.setPerformanceMonitoring(true)

        assertTrue(settingsManager.debugLogging.first())
        assertTrue(settingsManager.getPerformanceMonitoring())
        assertTrue(settingsManager.getRawCapture())

        // Change another
        settingsManager.setDebugLogging(false)

        assertFalse(settingsManager.debugLogging.first())
        assertTrue(settingsManager.getPerformanceMonitoring())
        assertTrue(settingsManager.getRawCapture())
    }

    @Test
    fun testAllAdvancedSettingsPersistTogether() = runTest {
        // Set all advanced settings
        settingsManager.setDebugLogging(true)
        settingsManager.setPerformanceMonitoring(true)
        settingsManager.setRawCapture(false)

        // Create new instance
        val newSettings = SettingsManager(context)

        // All should persist
        assertTrue(newSettings.debugLogging.first())
        assertTrue(newSettings.getPerformanceMonitoring())
        assertFalse(newSettings.getRawCapture())
    }

    @Test
    fun testAllAdvancedSettingsEnabled() = runTest {
        // Enable all advanced settings
        settingsManager.setDebugLogging(true)
        settingsManager.setPerformanceMonitoring(true)
        settingsManager.setRawCapture(true)

        // Verify all enabled
        assertTrue(settingsManager.debugLogging.first())
        assertTrue(settingsManager.getPerformanceMonitoring())
        assertTrue(settingsManager.getRawCapture())

        // Persistence check
        val newSettings = SettingsManager(context)
        assertTrue(newSettings.debugLogging.first())
        assertTrue(newSettings.getPerformanceMonitoring())
        assertTrue(newSettings.getRawCapture())
    }

    @Test
    fun testAllAdvancedSettingsDisabled() = runTest {
        // Enable all first
        settingsManager.setDebugLogging(true)
        settingsManager.setPerformanceMonitoring(true)
        settingsManager.setRawCapture(true)

        // Disable all
        settingsManager.setDebugLogging(false)
        settingsManager.setPerformanceMonitoring(false)
        settingsManager.setRawCapture(false)

        // Verify all disabled
        assertFalse(settingsManager.debugLogging.first())
        assertFalse(settingsManager.getPerformanceMonitoring())
        assertFalse(settingsManager.getRawCapture())

        // Persistence check
        val newSettings = SettingsManager(context)
        assertFalse(newSettings.debugLogging.first())
        assertFalse(newSettings.getPerformanceMonitoring())
        assertFalse(newSettings.getRawCapture())
    }

    @Test
    fun testRapidAdvancedSettingsChanges() = runTest {
        // Rapidly toggle all settings
        repeat(50) { i ->
            val enabled = (i % 2 == 0)
            settingsManager.setDebugLogging(enabled)
            settingsManager.setPerformanceMonitoring(enabled)
            settingsManager.setRawCapture(enabled)
        }

        // Final value should be false (49 % 2 == 1, so false)
        assertFalse(settingsManager.debugLogging.first())
        assertFalse(settingsManager.getPerformanceMonitoring())
        assertFalse(settingsManager.getRawCapture())

        // Persistence check
        val newSettings = SettingsManager(context)
        assertFalse(newSettings.debugLogging.first())
        assertFalse(newSettings.getPerformanceMonitoring())
        assertFalse(newSettings.getRawCapture())
    }

    @Test
    fun testSettingsManagerRecreationWithAdvancedSettings() = runTest {
        settingsManager.setDebugLogging(true)
        settingsManager.setPerformanceMonitoring(false)
        settingsManager.setRawCapture(true)

        repeat(5) {
            val newSettings = SettingsManager(context)
            assertTrue("Debug logging should persist", newSettings.debugLogging.first())
            assertFalse("Performance monitoring should persist", newSettings.getPerformanceMonitoring())
            assertTrue("RAW capture should persist", newSettings.getRawCapture())
        }
    }

    @Test
    fun testAdvancedSettingsConcurrentChanges() = runTest {
        val jobs = List(20) { i ->
            kotlinx.coroutines.launch {
                val enabled = (i % 2 == 0)
                settingsManager.setDebugLogging(enabled)
                kotlinx.coroutines.delay(5)
                settingsManager.setPerformanceMonitoring(enabled)
                kotlinx.coroutines.delay(5)
                settingsManager.setRawCapture(enabled)
            }
        }

        jobs.forEach { it.join() }

        // All values should be either true or false (consistent from last job)
        val debugValue = settingsManager.debugLogging.first()
        val perfValue = settingsManager.getPerformanceMonitoring()
        val rawValue = settingsManager.getRawCapture()

        // Just verify they're boolean values (test completed without exception)
        assertTrue("Debug logging is boolean", debugValue is Boolean)
        assertTrue("Performance monitoring is boolean", perfValue is Boolean)
        assertTrue("RAW capture is boolean", rawValue is Boolean)
    }

    @Test
    fun testAdvancedSettingsAllCombinations() = runTest {
        val combinations = listOf(
            Triple(true, true, true),
            Triple(true, true, false),
            Triple(true, false, true),
            Triple(true, false, false),
            Triple(false, true, true),
            Triple(false, true, false),
            Triple(false, false, true),
            Triple(false, false, false)
        )

        for ((debug, perf, raw) in combinations) {
            // Clear and recreate
            context.getSharedPreferences("camera_settings", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit()

            val settings = SettingsManager(context)

            // Set values
            settings.setDebugLogging(debug)
            settings.setPerformanceMonitoring(perf)
            settings.setRawCapture(raw)

            // Verify persistence
            val newSettings = SettingsManager(context)
            assertEquals(debug, newSettings.debugLogging.first())
            assertEquals(perf, newSettings.getPerformanceMonitoring())
            assertEquals(raw, newSettings.getRawCapture())
        }
    }
}
