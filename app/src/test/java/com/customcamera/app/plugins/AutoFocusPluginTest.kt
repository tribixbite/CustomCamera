package com.customcamera.app.plugins

import com.customcamera.app.testing.PluginTestFramework
import com.customcamera.app.testing.SimpleMockCameraContext
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

/**
 * AutoFocus Plugin Tests
 *
 * Validates automatic focus management functionality.
 */
class AutoFocusPluginTest {

    private lateinit var plugin: AutoFocusPlugin
    private lateinit var testFramework: PluginTestFramework

    @Before
    fun setup() {
        plugin = AutoFocusPlugin()
        testFramework = PluginTestFramework()
    }

    @Test
    fun testPluginMetadata() {
        assertEquals("Auto Focus", plugin.name)
        assertEquals("1.0", plugin.version)
        assertTrue("Priority should be positive", plugin.priority > 0)
    }

    @Test
    fun testPluginLifecycle() = runTest {
        val mockContext = SimpleMockCameraContext.createBasic()

        val result = testFramework.testPluginLifecycle(
            plugin = plugin,
            context = mockContext,
            camera = null
        )

        result.assertSuccess()
        result.assertContainsStep("INIT_SUCCESS")
        result.assertContainsStep("CLEANUP_SUCCESS")
        result.assertCompletesWithin(1000)
    }

    @Test
    fun testEnableDisableToggle() = runTest {
        val mockContext = SimpleMockCameraContext.createBasic()
        plugin.initialize(mockContext)

        // Default state
        val initialState = plugin.isEnabled

        // Toggle
        if (initialState) {
            plugin.disable()
            assertFalse(plugin.isEnabled)
        } else {
            plugin.enable()
            assertTrue(plugin.isEnabled)
        }
    }

    @Test
    fun testMultipleInitializationsSafe() = runTest {
        val mockContext = SimpleMockCameraContext.createBasic()

        // Initialize multiple times
        plugin.initialize(mockContext)
        plugin.initialize(mockContext)
        plugin.initialize(mockContext)

        // Should be functional
        plugin.enable()
        assertTrue(plugin.isEnabled)
    }

    @Test
    fun testCleanupSafety() = runTest {
        val mockContext = SimpleMockCameraContext.createBasic()
        plugin.initialize(mockContext)
        plugin.enable()

        // Cleanup should not crash
        plugin.cleanup()

        // Multiple cleanups should be safe
        plugin.cleanup()
        plugin.cleanup()
    }
}
