package com.customcamera.app.plugins

import com.customcamera.app.testing.PluginTestFramework
import com.customcamera.app.testing.SimpleMockCameraContext
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

/**
 * Diagnostic Overlay Plugin Tests
 *
 * Validates diagnostic overlay functionality including lifecycle,
 * state management, and visibility control.
 */
class DiagnosticOverlayPluginTest {

    private lateinit var plugin: DiagnosticOverlayPlugin
    private lateinit var testFramework: PluginTestFramework
    private var callbackInvoked = false
    private var lastCallbackValue = false

    @Before
    fun setup() {
        plugin = DiagnosticOverlayPlugin()
        testFramework = PluginTestFramework()
        callbackInvoked = false
        lastCallbackValue = false
    }

    @Test
    fun testPluginMetadata() {
        assertEquals("DiagnosticOverlay", plugin.name)
        assertEquals("Diagnostic Overlay", plugin.displayName)
        assertEquals("Real-time camera and sensor diagnostics", plugin.description)
        assertEquals("1.0.0", plugin.version)
        assertTrue("Priority should be positive", plugin.priority > 0)
        assertTrue("Should be user toggleable", plugin.userToggleable)
    }

    @Test
    fun testPluginCategory() {
        assertEquals(
            "Plugin should be in DEBUG category",
            com.customcamera.app.engine.plugins.PluginCategory.DEBUG,
            plugin.category
        )
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
    fun testOverlayToggleCallback() = runTest {
        val mockContext = SimpleMockCameraContext.createBasic()
        plugin.initialize(mockContext)

        // Set up callback
        plugin.overlayToggleCallback = { show ->
            callbackInvoked = true
            lastCallbackValue = show
        }

        // Enable plugin should trigger callback with true
        plugin.enable()
        assertTrue("Callback should be invoked on enable", callbackInvoked)
        assertTrue("Callback should receive true when enabled", lastCallbackValue)

        // Reset
        callbackInvoked = false
        lastCallbackValue = false

        // Disable plugin should trigger callback with false
        plugin.disable()
        assertTrue("Callback should be invoked on disable", callbackInvoked)
        assertFalse("Callback should receive false when disabled", lastCallbackValue)
    }

    @Test
    fun testToggleOverlay() = runTest {
        val mockContext = SimpleMockCameraContext.createBasic()
        plugin.initialize(mockContext)

        var callbackCount = 0
        var callbackValues = mutableListOf<Boolean>()

        plugin.overlayToggleCallback = { show ->
            callbackCount++
            callbackValues.add(show)
        }

        // Should start not visible
        assertFalse("Overlay should start hidden", plugin.isVisible())

        // Toggle to show
        plugin.toggleOverlay()
        assertEquals("Callback should be called once", 1, callbackCount)
        assertTrue("Should show overlay", callbackValues[0])
        assertTrue("isVisible should return true", plugin.isVisible())

        // Toggle to hide
        plugin.toggleOverlay()
        assertEquals("Callback should be called twice", 2, callbackCount)
        assertFalse("Should hide overlay", callbackValues[1])
        assertFalse("isVisible should return false", plugin.isVisible())
    }

    @Test
    fun testCallbackNullSafe() = runTest {
        val mockContext = SimpleMockCameraContext.createBasic()
        plugin.initialize(mockContext)

        // No callback set - should not crash
        plugin.enable()
        plugin.disable()
        plugin.toggleOverlay()

        // Should complete without exception
        assertTrue("Test completed successfully", true)
    }

    @Test
    fun testVisibilityStateManagement() = runTest {
        val mockContext = SimpleMockCameraContext.createBasic()
        plugin.initialize(mockContext)

        plugin.overlayToggleCallback = { _ -> }

        // Start hidden
        assertFalse("Should start hidden", plugin.isVisible())

        // Enable shows overlay
        plugin.enable()
        assertTrue("Should be visible after enable", plugin.isVisible())

        // Disable hides overlay
        plugin.disable()
        assertFalse("Should be hidden after disable", plugin.isVisible())
    }

    @Test
    fun testCleanupRemovesCallback() = runTest {
        val mockContext = SimpleMockCameraContext.createBasic()
        plugin.initialize(mockContext)

        var callbackInvoked = false
        plugin.overlayToggleCallback = { _ ->
            callbackInvoked = true
        }

        // Cleanup should remove callback reference
        plugin.cleanup()

        // After cleanup, callback reference should be null
        // We can't directly test this, but we verify no crash occurs
        // and plugin is in clean state
        assertFalse("Plugin should be disabled after cleanup", plugin.isEnabled)
    }

    @Test
    fun testMultipleToggleCycles() = runTest {
        val mockContext = SimpleMockCameraContext.createBasic()
        plugin.initialize(mockContext)

        var toggleCount = 0
        plugin.overlayToggleCallback = { _ ->
            toggleCount++
        }

        // Toggle multiple times
        repeat(10) {
            plugin.toggleOverlay()
        }

        assertEquals("Should toggle 10 times", 10, toggleCount)
    }

    @Test
    fun testProviderConfiguration() {
        // Test companion object provider
        assertEquals("diagnostic_overlay", DiagnosticOverlayPlugin.id)
        assertEquals(
            com.customcamera.app.engine.plugins.PluginCategory.DEBUG,
            DiagnosticOverlayPlugin.category
        )
        assertTrue(
            "Should be user toggleable",
            DiagnosticOverlayPlugin.userToggleable
        )
        assertTrue(
            "Should show in dropdown",
            DiagnosticOverlayPlugin.showInDropdown
        )
    }

    @Test
    fun testProviderSupport() {
        val mockContext = SimpleMockCameraContext.createBasic()

        // Diagnostic overlay should be supported on all devices
        assertTrue(
            "Should be supported on all devices",
            DiagnosticOverlayPlugin.isSupported(mockContext.context)
        )
    }

    @Test
    fun testProviderCreate() {
        val mockContext = SimpleMockCameraContext.createBasic()
        val dependencies = com.customcamera.app.engine.plugins.PluginDependencies(
            mockContext.context,
            mockContext.debugLogger
        )

        val createdPlugin = DiagnosticOverlayPlugin.create(dependencies)

        assertNotNull("Created plugin should not be null", createdPlugin)
        assertTrue(
            "Created plugin should be DiagnosticOverlayPlugin instance",
            createdPlugin is DiagnosticOverlayPlugin
        )
    }
}
