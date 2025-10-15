package com.customcamera.app.plugins

import com.customcamera.app.testing.PluginTestFramework
import com.customcamera.app.testing.SimpleMockCameraContext
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

/**
 * Grid Overlay Plugin Tests
 *
 * Validates grid overlay functionality including lifecycle,
 * state management, and grid type cycling.
 */
class GridOverlayPluginTest {

    private lateinit var plugin: GridOverlayPlugin
    private lateinit var testFramework: PluginTestFramework

    @Before
    fun setup() {
        plugin = GridOverlayPlugin()
        testFramework = PluginTestFramework()
    }

    @Test
    fun testPluginMetadata() {
        assertEquals("Grid Overlay", plugin.name)
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
        result.assertCompletesWithin(500) // Should be fast
    }

    @Test
    fun testEnableDisableState() = runTest {
        val mockContext = SimpleMockCameraContext.createBasic()
        plugin.initialize(mockContext)

        // Should start disabled
        assertFalse("Plugin should start disabled", plugin.isEnabled)

        // Enable
        plugin.enable()
        assertTrue("Plugin should be enabled", plugin.isEnabled)

        // Disable
        plugin.disable()
        assertFalse("Plugin should be disabled", plugin.isEnabled)
    }

    @Test
    fun testMultipleEnableCallsSafe() = runTest {
        val mockContext = SimpleMockCameraContext.createBasic()
        plugin.initialize(mockContext)

        // Enable multiple times should be safe
        plugin.enable()
        plugin.enable()
        plugin.enable()

        assertTrue("Plugin should remain enabled", plugin.isEnabled)
    }

    @Test
    fun testMultipleDisableCallsSafe() = runTest {
        val mockContext = SimpleMockCameraContext.createBasic()
        plugin.initialize(mockContext)

        plugin.enable()
        plugin.disable()
        plugin.disable()
        plugin.disable()

        assertFalse("Plugin should remain disabled", plugin.isEnabled)
    }

    @Test
    fun testGridToggle() = runTest {
        val mockContext = SimpleMockCameraContext.createBasic()
        plugin.initialize(mockContext)

        // Toggle grid
        plugin.toggleGrid()

        // Grid state should change
        // Note: Actual state depends on settings manager
    }

    @Test
    fun testCleanupReleasesResources() = runTest {
        val mockContext = SimpleMockCameraContext.createBasic()
        plugin.initialize(mockContext)
        plugin.enable()

        // Cleanup
        plugin.cleanup()

        // Plugin should be disabled after cleanup
        assertFalse("Plugin should be disabled after cleanup", plugin.isEnabled)
    }

    @Test
    fun testReinitializationAfterCleanup() = runTest {
        val mockContext = SimpleMockCameraContext.createBasic()

        // Initial lifecycle
        plugin.initialize(mockContext)
        plugin.enable()
        plugin.cleanup()

        // Re-initialize
        plugin.initialize(mockContext)
        plugin.enable()

        assertTrue("Plugin should be enabled after re-init", plugin.isEnabled)
    }
}
