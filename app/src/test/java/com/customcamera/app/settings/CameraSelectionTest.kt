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
 * Camera Selection Settings Tests
 *
 * Tests for main camera index and PiP camera index settings:
 * - Persistence across instances
 * - StateFlow reactivity
 * - Default values
 * - Value bounds and validation
 *
 * Note: These are instrumented tests requiring Android context.
 * Run with: ./gradlew connectedAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class CameraSelectionTest {

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
    // Main Camera Selection Tests
    // ============================================================

    @Test
    fun testMainCameraIndexDefaultValue() = runTest {
        val defaultValue = settingsManager.defaultCameraIndex.first()
        assertEquals(
            "Default camera index should be 0",
            0,
            defaultValue
        )
    }

    @Test
    fun testMainCameraIndexPersistence() = runTest {
        // Set camera index
        settingsManager.setDefaultCameraIndex(2)

        // Verify StateFlow updated
        assertEquals(
            "StateFlow should update immediately",
            2,
            settingsManager.defaultCameraIndex.first()
        )

        // Create new instance to test persistence
        val newSettings = SettingsManager(context)
        assertEquals(
            "Camera index should persist across instances",
            2,
            newSettings.defaultCameraIndex.first()
        )
    }

    @Test
    fun testMainCameraIndexMultipleUpdates() = runTest {
        // Update multiple times
        settingsManager.setDefaultCameraIndex(1)
        assertEquals(1, settingsManager.defaultCameraIndex.first())

        settingsManager.setDefaultCameraIndex(3)
        assertEquals(3, settingsManager.defaultCameraIndex.first())

        settingsManager.setDefaultCameraIndex(0)
        assertEquals(0, settingsManager.defaultCameraIndex.first())
    }

    @Test
    fun testMainCameraIndexReactivity() = runTest {
        var observedValue = -1
        val job = kotlinx.coroutines.launch {
            settingsManager.defaultCameraIndex.collect { value ->
                observedValue = value
            }
        }

        // Give collector time to start
        kotlinx.coroutines.delay(50)

        // Update value
        settingsManager.setDefaultCameraIndex(5)

        // Give StateFlow time to emit
        kotlinx.coroutines.delay(50)

        assertEquals("StateFlow should emit new value", 5, observedValue)

        job.cancel()
    }

    @Test
    fun testMainCameraIndexNegativeValue() = runTest {
        // Negative values should be allowed (handled by camera engine)
        settingsManager.setDefaultCameraIndex(-1)
        assertEquals(-1, settingsManager.defaultCameraIndex.first())
    }

    @Test
    fun testMainCameraIndexLargeValue() = runTest {
        // Large values should be allowed (handled by camera engine)
        settingsManager.setDefaultCameraIndex(99)
        assertEquals(99, settingsManager.defaultCameraIndex.first())
    }

    // ============================================================
    // PiP Camera Selection Tests
    // ============================================================

    @Test
    fun testPipCameraIndexDefaultValue() = runTest {
        val defaultValue = settingsManager.pipCameraIndex.first()
        assertEquals(
            "Default PiP camera index should be 1",
            1,
            defaultValue
        )
    }

    @Test
    fun testPipCameraIndexPersistence() = runTest {
        // Set PiP camera index
        settingsManager.setPipCameraIndex(0)

        // Verify StateFlow updated
        assertEquals(
            "StateFlow should update immediately",
            0,
            settingsManager.pipCameraIndex.first()
        )

        // Create new instance to test persistence
        val newSettings = SettingsManager(context)
        assertEquals(
            "PiP camera index should persist across instances",
            0,
            newSettings.pipCameraIndex.first()
        )
    }

    @Test
    fun testPipCameraIndexMultipleUpdates() = runTest {
        // Update multiple times
        settingsManager.setPipCameraIndex(2)
        assertEquals(2, settingsManager.pipCameraIndex.first())

        settingsManager.setPipCameraIndex(0)
        assertEquals(0, settingsManager.pipCameraIndex.first())

        settingsManager.setPipCameraIndex(3)
        assertEquals(3, settingsManager.pipCameraIndex.first())
    }

    @Test
    fun testPipCameraIndexReactivity() = runTest {
        var observedValue = -1
        val job = kotlinx.coroutines.launch {
            settingsManager.pipCameraIndex.collect { value ->
                observedValue = value
            }
        }

        // Give collector time to start
        kotlinx.coroutines.delay(50)

        // Update value
        settingsManager.setPipCameraIndex(4)

        // Give StateFlow time to emit
        kotlinx.coroutines.delay(50)

        assertEquals("StateFlow should emit new value", 4, observedValue)

        job.cancel()
    }

    @Test
    fun testPipCameraIndexIndependentFromMain() = runTest {
        // Set both cameras to different values
        settingsManager.setDefaultCameraIndex(0)
        settingsManager.setPipCameraIndex(1)

        assertEquals(
            "Main camera should be 0",
            0,
            settingsManager.defaultCameraIndex.first()
        )
        assertEquals(
            "PiP camera should be 1",
            1,
            settingsManager.pipCameraIndex.first()
        )

        // Swap them
        settingsManager.setDefaultCameraIndex(1)
        settingsManager.setPipCameraIndex(0)

        assertEquals(
            "Main camera should be 1",
            1,
            settingsManager.defaultCameraIndex.first()
        )
        assertEquals(
            "PiP camera should be 0",
            0,
            settingsManager.pipCameraIndex.first()
        )
    }

    // ============================================================
    // Edge Cases and Stress Tests
    // ============================================================

    @Test
    fun testRapidCameraIndexChanges() = runTest {
        // Rapidly change camera index
        repeat(50) { i ->
            settingsManager.setDefaultCameraIndex(i % 4)
        }

        // Final value should be 49 % 4 = 1
        assertEquals(1, settingsManager.defaultCameraIndex.first())

        // Persistence should work
        val newSettings = SettingsManager(context)
        assertEquals(1, newSettings.defaultCameraIndex.first())
    }

    @Test
    fun testConcurrentCameraSelections() = runTest {
        val jobs = List(10) { i ->
            kotlinx.coroutines.launch {
                settingsManager.setDefaultCameraIndex(i)
                kotlinx.coroutines.delay(10)
                settingsManager.setPipCameraIndex(i + 1)
            }
        }

        jobs.forEach { it.join() }

        // Final values should be from last job (i=9)
        val mainCamera = settingsManager.defaultCameraIndex.first()
        val pipCamera = settingsManager.pipCameraIndex.first()

        assertTrue(
            "Main camera should be one of the set values",
            mainCamera in 0..9
        )
        assertTrue(
            "PiP camera should be one of the set values",
            pipCamera in 1..10
        )
    }

    @Test
    fun testSettingsManagerRecreation() = runTest {
        // Set values
        settingsManager.setDefaultCameraIndex(2)
        settingsManager.setPipCameraIndex(3)

        // Recreate settings manager multiple times
        repeat(5) {
            val newSettings = SettingsManager(context)
            assertEquals(
                "Main camera should persist through recreation",
                2,
                newSettings.defaultCameraIndex.first()
            )
            assertEquals(
                "PiP camera should persist through recreation",
                3,
                newSettings.pipCameraIndex.first()
            )
        }
    }

    @Test
    fun testBothCamerasCanBeSameIndex() = runTest {
        // Set both to same camera
        settingsManager.setDefaultCameraIndex(2)
        settingsManager.setPipCameraIndex(2)

        assertEquals(2, settingsManager.defaultCameraIndex.first())
        assertEquals(2, settingsManager.pipCameraIndex.first())

        // Persistence check
        val newSettings = SettingsManager(context)
        assertEquals(2, newSettings.defaultCameraIndex.first())
        assertEquals(2, newSettings.pipCameraIndex.first())
    }
}
