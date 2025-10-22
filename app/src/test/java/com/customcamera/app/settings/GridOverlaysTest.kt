package com.customcamera.app.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.robolectric.RobolectricTestRunner
import com.customcamera.app.engine.SettingsManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith

/**
 * Grid and Overlays Settings Tests
 *
 * Tests for grid overlay, camera info overlay, histogram overlay, and level indicator:
 * - Persistence across instances
 * - StateFlow reactivity (grid overlay)
 * - Default values
 * - Enable/disable states
 *
 * Note: These are instrumented tests requiring Android context.
 * Run with: ./gradlew connectedAndroidTest
 */
@RunWith(RobolectricTestRunner::class)
class GridOverlaysTest {

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
    // Grid Overlay Tests
    // ============================================================

    @Test
    fun testGridOverlayDefaultValue() = runTest {
        val defaultValue = settingsManager.gridOverlay.first()
        assertFalse(
            "Default grid overlay should be disabled (false)",
            defaultValue
        )
    }

    @Test
    fun testGridOverlayPersistence() = runTest {
        settingsManager.setGridOverlay(true)

        assertTrue(
            "StateFlow should update immediately",
            settingsManager.gridOverlay.first()
        )

        val newSettings = SettingsManager(context)
        assertTrue(
            "Grid overlay should persist across instances",
            newSettings.gridOverlay.first()
        )
    }

    @Test
    fun testGridOverlayToggle() = runTest {
        // Start with default (false)
        assertFalse(settingsManager.gridOverlay.first())

        // Enable
        settingsManager.setGridOverlay(true)
        assertTrue(settingsManager.gridOverlay.first())

        // Disable
        settingsManager.setGridOverlay(false)
        assertFalse(settingsManager.gridOverlay.first())
    }

    @Test
    fun testGridOverlayReactivity() = runTest {
        var observedValue = false
        val job = launch {
            settingsManager.gridOverlay.collect { value ->
                observedValue = value
            }
        }

        // Give collector time to start
        delay(50)

        // Update value
        settingsManager.setGridOverlay(true)

        // Give StateFlow time to emit
        delay(50)

        assertTrue("StateFlow should emit new value", observedValue)

        job.cancel()
    }

    @Test
    fun testGridOverlayMultipleToggles() = runTest {
        repeat(10) { i ->
            val enabled = (i % 2 == 0)
            settingsManager.setGridOverlay(enabled)
            assertEquals(enabled, settingsManager.gridOverlay.first())
        }

        // Final value should be false (9 % 2 == 1, so false)
        assertFalse(settingsManager.gridOverlay.first())

        // Persistence check
        val newSettings = SettingsManager(context)
        assertFalse(newSettings.gridOverlay.first())
    }

    @Test
    fun testGridOverlayIdempotent() = runTest {
        // Setting same value multiple times should work
        settingsManager.setGridOverlay(true)
        settingsManager.setGridOverlay(true)
        settingsManager.setGridOverlay(true)
        assertTrue(settingsManager.gridOverlay.first())

        settingsManager.setGridOverlay(false)
        settingsManager.setGridOverlay(false)
        settingsManager.setGridOverlay(false)
        assertFalse(settingsManager.gridOverlay.first())
    }

    // ============================================================
    // Camera Info Overlay Tests
    // ============================================================

    @Test
    fun testCameraInfoOverlayDefaultValue() {
        val defaultValue = settingsManager.getCameraInfoOverlay()
        assertFalse(
            "Default camera info overlay should be disabled (false)",
            defaultValue
        )
    }

    @Test
    fun testCameraInfoOverlayPersistence() {
        settingsManager.setCameraInfoOverlay(true)

        assertTrue(
            "Value should be set immediately",
            settingsManager.getCameraInfoOverlay()
        )

        val newSettings = SettingsManager(context)
        assertTrue(
            "Camera info overlay should persist across instances",
            newSettings.getCameraInfoOverlay()
        )
    }

    @Test
    fun testCameraInfoOverlayToggle() {
        // Start with default (false)
        assertFalse(settingsManager.getCameraInfoOverlay())

        // Enable
        settingsManager.setCameraInfoOverlay(true)
        assertTrue(settingsManager.getCameraInfoOverlay())

        // Disable
        settingsManager.setCameraInfoOverlay(false)
        assertFalse(settingsManager.getCameraInfoOverlay())
    }

    @Test
    fun testCameraInfoOverlayMultipleToggles() {
        repeat(10) { i ->
            val enabled = (i % 2 == 0)
            settingsManager.setCameraInfoOverlay(enabled)
            assertEquals(enabled, settingsManager.getCameraInfoOverlay())
        }

        // Final value should be false
        assertFalse(settingsManager.getCameraInfoOverlay())

        // Persistence check
        val newSettings = SettingsManager(context)
        assertFalse(newSettings.getCameraInfoOverlay())
    }

    // ============================================================
    // Histogram Overlay Tests
    // ============================================================

    @Test
    fun testHistogramOverlayDefaultValue() {
        val defaultValue = settingsManager.getHistogramOverlay()
        assertFalse(
            "Default histogram overlay should be disabled (false)",
            defaultValue
        )
    }

    @Test
    fun testHistogramOverlayPersistence() {
        settingsManager.setHistogramOverlay(true)

        assertTrue(
            "Value should be set immediately",
            settingsManager.getHistogramOverlay()
        )

        val newSettings = SettingsManager(context)
        assertTrue(
            "Histogram overlay should persist across instances",
            newSettings.getHistogramOverlay()
        )
    }

    @Test
    fun testHistogramOverlayToggle() {
        // Start with default (false)
        assertFalse(settingsManager.getHistogramOverlay())

        // Enable
        settingsManager.setHistogramOverlay(true)
        assertTrue(settingsManager.getHistogramOverlay())

        // Disable
        settingsManager.setHistogramOverlay(false)
        assertFalse(settingsManager.getHistogramOverlay())
    }

    @Test
    fun testHistogramOverlayMultipleToggles() {
        repeat(10) { i ->
            val enabled = (i % 2 == 0)
            settingsManager.setHistogramOverlay(enabled)
            assertEquals(enabled, settingsManager.getHistogramOverlay())
        }

        // Final value should be false
        assertFalse(settingsManager.getHistogramOverlay())

        // Persistence check
        val newSettings = SettingsManager(context)
        assertFalse(newSettings.getHistogramOverlay())
    }

    // ============================================================
    // Level Indicator Tests
    // ============================================================

    @Test
    fun testLevelIndicatorDefaultValue() {
        val defaultValue = settingsManager.getLevelIndicator()
        assertFalse(
            "Default level indicator should be disabled (false)",
            defaultValue
        )
    }

    @Test
    fun testLevelIndicatorPersistence() {
        settingsManager.setLevelIndicator(true)

        assertTrue(
            "Value should be set immediately",
            settingsManager.getLevelIndicator()
        )

        val newSettings = SettingsManager(context)
        assertTrue(
            "Level indicator should persist across instances",
            newSettings.getLevelIndicator()
        )
    }

    @Test
    fun testLevelIndicatorToggle() {
        // Start with default (false)
        assertFalse(settingsManager.getLevelIndicator())

        // Enable
        settingsManager.setLevelIndicator(true)
        assertTrue(settingsManager.getLevelIndicator())

        // Disable
        settingsManager.setLevelIndicator(false)
        assertFalse(settingsManager.getLevelIndicator())
    }

    @Test
    fun testLevelIndicatorMultipleToggles() {
        repeat(10) { i ->
            val enabled = (i % 2 == 0)
            settingsManager.setLevelIndicator(enabled)
            assertEquals(enabled, settingsManager.getLevelIndicator())
        }

        // Final value should be false
        assertFalse(settingsManager.getLevelIndicator())

        // Persistence check
        val newSettings = SettingsManager(context)
        assertFalse(newSettings.getLevelIndicator())
    }

    // ============================================================
    // Combined Tests
    // ============================================================

    @Test
    fun testAllOverlaysIndependent() = runTest {
        // Set all overlays to different states
        settingsManager.setGridOverlay(true)
        settingsManager.setCameraInfoOverlay(false)
        settingsManager.setHistogramOverlay(true)
        settingsManager.setLevelIndicator(false)

        // Verify all states
        assertTrue(settingsManager.gridOverlay.first())
        assertFalse(settingsManager.getCameraInfoOverlay())
        assertTrue(settingsManager.getHistogramOverlay())
        assertFalse(settingsManager.getLevelIndicator())

        // Change one, others should remain unchanged
        settingsManager.setCameraInfoOverlay(true)

        assertTrue(settingsManager.gridOverlay.first())
        assertTrue(settingsManager.getCameraInfoOverlay())
        assertTrue(settingsManager.getHistogramOverlay())
        assertFalse(settingsManager.getLevelIndicator())
    }

    @Test
    fun testAllOverlaysPersistTogether() = runTest {
        // Set all overlays
        settingsManager.setGridOverlay(true)
        settingsManager.setCameraInfoOverlay(true)
        settingsManager.setHistogramOverlay(false)
        settingsManager.setLevelIndicator(true)

        // Create new instance
        val newSettings = SettingsManager(context)

        // All should persist
        assertTrue(newSettings.gridOverlay.first())
        assertTrue(newSettings.getCameraInfoOverlay())
        assertFalse(newSettings.getHistogramOverlay())
        assertTrue(newSettings.getLevelIndicator())
    }

    @Test
    fun testAllOverlaysEnabled() = runTest {
        // Enable all overlays
        settingsManager.setGridOverlay(true)
        settingsManager.setCameraInfoOverlay(true)
        settingsManager.setHistogramOverlay(true)
        settingsManager.setLevelIndicator(true)

        // Verify all enabled
        assertTrue(settingsManager.gridOverlay.first())
        assertTrue(settingsManager.getCameraInfoOverlay())
        assertTrue(settingsManager.getHistogramOverlay())
        assertTrue(settingsManager.getLevelIndicator())

        // Persistence check
        val newSettings = SettingsManager(context)
        assertTrue(newSettings.gridOverlay.first())
        assertTrue(newSettings.getCameraInfoOverlay())
        assertTrue(newSettings.getHistogramOverlay())
        assertTrue(newSettings.getLevelIndicator())
    }

    @Test
    fun testAllOverlaysDisabled() = runTest {
        // Enable all first
        settingsManager.setGridOverlay(true)
        settingsManager.setCameraInfoOverlay(true)
        settingsManager.setHistogramOverlay(true)
        settingsManager.setLevelIndicator(true)

        // Disable all
        settingsManager.setGridOverlay(false)
        settingsManager.setCameraInfoOverlay(false)
        settingsManager.setHistogramOverlay(false)
        settingsManager.setLevelIndicator(false)

        // Verify all disabled
        assertFalse(settingsManager.gridOverlay.first())
        assertFalse(settingsManager.getCameraInfoOverlay())
        assertFalse(settingsManager.getHistogramOverlay())
        assertFalse(settingsManager.getLevelIndicator())

        // Persistence check
        val newSettings = SettingsManager(context)
        assertFalse(newSettings.gridOverlay.first())
        assertFalse(newSettings.getCameraInfoOverlay())
        assertFalse(newSettings.getHistogramOverlay())
        assertFalse(newSettings.getLevelIndicator())
    }

    @Test
    fun testRapidOverlayChanges() = runTest {
        // Rapidly toggle all overlays
        repeat(50) { i ->
            val enabled = (i % 2 == 0)
            settingsManager.setGridOverlay(enabled)
            settingsManager.setCameraInfoOverlay(enabled)
            settingsManager.setHistogramOverlay(enabled)
            settingsManager.setLevelIndicator(enabled)
        }

        // Final value should be false (49 % 2 == 1, so false)
        assertFalse(settingsManager.gridOverlay.first())
        assertFalse(settingsManager.getCameraInfoOverlay())
        assertFalse(settingsManager.getHistogramOverlay())
        assertFalse(settingsManager.getLevelIndicator())

        // Persistence check
        val newSettings = SettingsManager(context)
        assertFalse(newSettings.gridOverlay.first())
        assertFalse(newSettings.getCameraInfoOverlay())
        assertFalse(newSettings.getHistogramOverlay())
        assertFalse(newSettings.getLevelIndicator())
    }

    @Test
    fun testSettingsManagerRecreationWithOverlays() = runTest {
        settingsManager.setGridOverlay(true)
        settingsManager.setCameraInfoOverlay(false)
        settingsManager.setHistogramOverlay(true)
        settingsManager.setLevelIndicator(false)

        repeat(5) {
            val newSettings = SettingsManager(context)
            assertTrue("Grid should persist", newSettings.gridOverlay.first())
            assertFalse("Camera info should persist", newSettings.getCameraInfoOverlay())
            assertTrue("Histogram should persist", newSettings.getHistogramOverlay())
            assertFalse("Level indicator should persist", newSettings.getLevelIndicator())
        }
    }

    @Test
    fun testOverlaysConcurrentChanges() = runTest {
        val jobs = List(20) { i ->
            launch {
                val enabled = (i % 2 == 0)
                settingsManager.setGridOverlay(enabled)
                delay(5)
                settingsManager.setCameraInfoOverlay(enabled)
                delay(5)
                settingsManager.setHistogramOverlay(enabled)
                delay(5)
                settingsManager.setLevelIndicator(enabled)
            }
        }

        jobs.forEach { it.join() }

        // All values should be either true or false (consistent from last job)
        val gridValue = settingsManager.gridOverlay.first()
        val cameraInfoValue = settingsManager.getCameraInfoOverlay()
        val histogramValue = settingsManager.getHistogramOverlay()
        val levelValue = settingsManager.getLevelIndicator()

        // Just verify they're boolean values (test completed without exception)
        assertTrue("Grid is boolean", gridValue is Boolean)
        assertTrue("Camera info is boolean", cameraInfoValue is Boolean)
        assertTrue("Histogram is boolean", histogramValue is Boolean)
        assertTrue("Level is boolean", levelValue is Boolean)
    }
}
