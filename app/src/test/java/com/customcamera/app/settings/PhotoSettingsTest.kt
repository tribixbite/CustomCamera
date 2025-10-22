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
 * Photo Settings Tests
 *
 * Tests for photo quality and photo resolution settings:
 * - Persistence across instances
 * - StateFlow reactivity (where applicable)
 * - Default values
 * - Value bounds and validation
 * - Quality clamping (1-100 range)
 *
 * Note: These are instrumented tests requiring Android context.
 * Run with: ./gradlew connectedAndroidTest
 */
@RunWith(RobolectricTestRunner::class)
class PhotoSettingsTest {

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
    // Photo Quality Tests
    // ============================================================

    @Test
    fun testPhotoQualityDefaultValue() = runTest {
        val defaultValue = settingsManager.photoQuality.first()
        assertEquals(
            "Default photo quality should be 95",
            95,
            defaultValue
        )
    }

    @Test
    fun testPhotoQualityPersistence() = runTest {
        // Set photo quality
        settingsManager.setPhotoQuality(85)

        // Verify StateFlow updated
        assertEquals(
            "StateFlow should update immediately",
            85,
            settingsManager.photoQuality.first()
        )

        // Create new instance to test persistence
        val newSettings = SettingsManager(context)
        assertEquals(
            "Photo quality should persist across instances",
            85,
            newSettings.photoQuality.first()
        )
    }

    @Test
    fun testPhotoQualityClampingLowerBound() = runTest {
        // Test clamping to minimum value (1)
        settingsManager.setPhotoQuality(0)
        assertEquals(
            "Quality should be clamped to 1",
            1,
            settingsManager.photoQuality.first()
        )

        settingsManager.setPhotoQuality(-50)
        assertEquals(
            "Negative quality should be clamped to 1",
            1,
            settingsManager.photoQuality.first()
        )
    }

    @Test
    fun testPhotoQualityClampingUpperBound() = runTest {
        // Test clamping to maximum value (100)
        settingsManager.setPhotoQuality(101)
        assertEquals(
            "Quality should be clamped to 100",
            100,
            settingsManager.photoQuality.first()
        )

        settingsManager.setPhotoQuality(500)
        assertEquals(
            "Large quality should be clamped to 100",
            100,
            settingsManager.photoQuality.first()
        )
    }

    @Test
    fun testPhotoQualityValidRange() = runTest {
        // Test various valid values
        val validQualities = listOf(1, 25, 50, 75, 95, 100)

        for (quality in validQualities) {
            settingsManager.setPhotoQuality(quality)
            assertEquals(
                "Quality $quality should be set correctly",
                quality,
                settingsManager.photoQuality.first()
            )
        }
    }

    @Test
    fun testPhotoQualityReactivity() = runTest {
        var observedValue = -1
        val job = launch {
            settingsManager.photoQuality.collect { value ->
                observedValue = value
            }
        }

        // Give collector time to start
        delay(50)

        // Update value
        settingsManager.setPhotoQuality(80)

        // Give StateFlow time to emit
        delay(50)

        assertEquals("StateFlow should emit new value", 80, observedValue)

        job.cancel()
    }

    @Test
    fun testPhotoQualityMultipleUpdates() = runTest {
        // Update multiple times
        settingsManager.setPhotoQuality(70)
        assertEquals(70, settingsManager.photoQuality.first())

        settingsManager.setPhotoQuality(90)
        assertEquals(90, settingsManager.photoQuality.first())

        settingsManager.setPhotoQuality(50)
        assertEquals(50, settingsManager.photoQuality.first())
    }

    @Test
    fun testPhotoQualityEdgeCases() = runTest {
        // Minimum valid value
        settingsManager.setPhotoQuality(1)
        assertEquals(1, settingsManager.photoQuality.first())

        // Maximum valid value
        settingsManager.setPhotoQuality(100)
        assertEquals(100, settingsManager.photoQuality.first())
    }

    // ============================================================
    // Photo Resolution Tests
    // ============================================================

    @Test
    fun testPhotoResolutionDefaultValue() {
        val defaultValue = settingsManager.getPhotoResolution()
        assertEquals(
            "Default photo resolution should be 'auto'",
            "auto",
            defaultValue
        )
    }

    @Test
    fun testPhotoResolutionPersistence() {
        // Set photo resolution
        settingsManager.setPhotoResolution("1920x1080")

        // Verify value
        assertEquals(
            "Resolution should be set immediately",
            "1920x1080",
            settingsManager.getPhotoResolution()
        )

        // Create new instance to test persistence
        val newSettings = SettingsManager(context)
        assertEquals(
            "Photo resolution should persist across instances",
            "1920x1080",
            newSettings.getPhotoResolution()
        )
    }

    @Test
    fun testPhotoResolutionMultipleUpdates() {
        // Common resolution strings
        val resolutions = listOf(
            "auto",
            "1920x1080",
            "3840x2160",
            "4096x2160",
            "1280x720"
        )

        for (resolution in resolutions) {
            settingsManager.setPhotoResolution(resolution)
            assertEquals(
                "Resolution '$resolution' should be set correctly",
                resolution,
                settingsManager.getPhotoResolution()
            )
        }
    }

    @Test
    fun testPhotoResolutionEmptyString() {
        // Empty string should be allowed
        settingsManager.setPhotoResolution("")
        assertEquals("", settingsManager.getPhotoResolution())

        // Persistence check
        val newSettings = SettingsManager(context)
        assertEquals("", newSettings.getPhotoResolution())
    }

    @Test
    fun testPhotoResolutionCustomValues() {
        // Test custom resolution strings
        val customResolutions = listOf(
            "max",
            "high",
            "medium",
            "low",
            "2560x1440",
            "1366x768"
        )

        for (resolution in customResolutions) {
            settingsManager.setPhotoResolution(resolution)
            assertEquals(resolution, settingsManager.getPhotoResolution())
        }
    }

    @Test
    fun testPhotoResolutionLongString() {
        // Test with very long string
        val longResolution = "x".repeat(1000)
        settingsManager.setPhotoResolution(longResolution)
        assertEquals(longResolution, settingsManager.getPhotoResolution())
    }

    // ============================================================
    // Combined Tests
    // ============================================================

    @Test
    fun testPhotoQualityAndResolutionIndependent() = runTest {
        // Set both values
        settingsManager.setPhotoQuality(85)
        settingsManager.setPhotoResolution("3840x2160")

        // Verify both are set correctly
        assertEquals(85, settingsManager.photoQuality.first())
        assertEquals("3840x2160", settingsManager.getPhotoResolution())

        // Change one, verify other unchanged
        settingsManager.setPhotoQuality(95)
        assertEquals(95, settingsManager.photoQuality.first())
        assertEquals("3840x2160", settingsManager.getPhotoResolution())

        settingsManager.setPhotoResolution("1920x1080")
        assertEquals(95, settingsManager.photoQuality.first())
        assertEquals("1920x1080", settingsManager.getPhotoResolution())
    }

    @Test
    fun testPhotoSettingsPersistTogether() = runTest {
        // Set both values
        settingsManager.setPhotoQuality(75)
        settingsManager.setPhotoResolution("2560x1440")

        // Create new instance
        val newSettings = SettingsManager(context)

        // Both should persist
        assertEquals(75, newSettings.photoQuality.first())
        assertEquals("2560x1440", newSettings.getPhotoResolution())
    }

    @Test
    fun testRapidPhotoQualityChanges() = runTest {
        // Rapidly change quality
        repeat(100) { i ->
            settingsManager.setPhotoQuality((i % 100) + 1)
        }

        // Final value should be ((99 % 100) + 1) = 100
        assertEquals(100, settingsManager.photoQuality.first())

        // Persistence should work
        val newSettings = SettingsManager(context)
        assertEquals(100, newSettings.photoQuality.first())
    }

    @Test
    fun testPhotoQualityBoundaryValues() = runTest {
        val boundaryTests = mapOf(
            -1000 to 1,  // Far below minimum
            0 to 1,      // At minimum boundary
            1 to 1,      // Valid minimum
            50 to 50,    // Middle value
            100 to 100,  // Valid maximum
            101 to 100,  // At maximum boundary
            1000 to 100  // Far above maximum
        )

        for ((input, expected) in boundaryTests) {
            settingsManager.setPhotoQuality(input)
            assertEquals(
                "Input $input should result in $expected",
                expected,
                settingsManager.photoQuality.first()
            )
        }
    }

    @Test
    fun testSettingsManagerRecreationWithPhotoSettings() = runTest {
        // Set values
        settingsManager.setPhotoQuality(88)
        settingsManager.setPhotoResolution("4096x2160")

        // Recreate settings manager multiple times
        repeat(5) {
            val newSettings = SettingsManager(context)
            assertEquals(
                "Photo quality should persist through recreation",
                88,
                newSettings.photoQuality.first()
            )
            assertEquals(
                "Photo resolution should persist through recreation",
                "4096x2160",
                newSettings.getPhotoResolution()
            )
        }
    }
}
