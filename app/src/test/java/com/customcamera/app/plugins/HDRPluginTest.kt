package com.customcamera.app.plugins

import com.customcamera.app.testing.PluginTestFramework
import com.customcamera.app.testing.SimpleMockCameraContext
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

/**
 * HDR Plugin Tests
 *
 * Validates HDR photography functionality including lifecycle,
 * state management, exposure bracketing configuration, and tone mapping.
 */
class HDRPluginTest {

    private lateinit var plugin: HDRPlugin
    private lateinit var testFramework: PluginTestFramework

    @Before
    fun setup() {
        plugin = HDRPlugin()
        testFramework = PluginTestFramework()
    }

    @Test
    fun testPluginMetadata() {
        assertEquals("HDR", plugin.name)
        assertEquals("HDR Mode", plugin.displayName)
        assertEquals("High dynamic range photography", plugin.description)
        assertEquals("1.0.0", plugin.version)
        assertTrue("Priority should be positive", plugin.priority > 0)
        assertTrue("HDR should be user toggleable", plugin.userToggleable)
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
    fun testEnableHDR() = runTest {
        val mockContext = SimpleMockCameraContext.createBasic()
        plugin.initialize(mockContext)

        // Enable HDR mode
        plugin.enableHDR()

        val status = plugin.getHDRStatus()
        assertTrue("HDR should be enabled", status["hdrEnabled"] as Boolean)
    }

    @Test
    fun testDisableHDR() = runTest {
        val mockContext = SimpleMockCameraContext.createBasic()
        plugin.initialize(mockContext)

        // Enable then disable
        plugin.enableHDR()
        plugin.disableHDR()

        val status = plugin.getHDRStatus()
        assertFalse("HDR should be disabled", status["hdrEnabled"] as Boolean)
    }

    @Test
    fun testBracketingStepsConfiguration() = runTest {
        val mockContext = SimpleMockCameraContext.createBasic()
        plugin.initialize(mockContext)

        // Set custom bracketing steps
        val customSteps = intArrayOf(-3, -1, 0, 1, 3)
        plugin.setBracketingSteps(customSteps)

        val status = plugin.getHDRStatus()
        val stepsString = status["bracketingSteps"] as String
        assertEquals("-3, -1, 0, 1, 3", stepsString)
    }

    @Test
    fun testDefaultBracketingSteps() = runTest {
        val mockContext = SimpleMockCameraContext.createBasic()
        plugin.initialize(mockContext)

        val status = plugin.getHDRStatus()
        val stepsString = status["bracketingSteps"] as String

        // Default should be -2, 0, 2
        assertEquals("-2, 0, 2", stepsString)
    }

    @Test
    fun testAutoHDRDetection() = runTest {
        val mockContext = SimpleMockCameraContext.createBasic()
        plugin.initialize(mockContext)

        val status = plugin.getHDRStatus()

        // Auto detection should be enabled by default
        assertTrue("Auto HDR detection should be enabled by default",
            status["autoDetection"] as Boolean)
    }

    @Test
    fun testToneMappingEnabled() = runTest {
        val mockContext = SimpleMockCameraContext.createBasic()
        plugin.initialize(mockContext)

        val status = plugin.getHDRStatus()

        // Tone mapping should be enabled by default
        assertTrue("Tone mapping should be enabled by default",
            status["toneMappingEnabled"] as Boolean)
    }

    @Test
    fun testHDRStatus() = runTest {
        val mockContext = SimpleMockCameraContext.createBasic()
        plugin.initialize(mockContext)

        val status = plugin.getHDRStatus()

        // Verify all status fields present
        assertTrue("Status should contain hdrEnabled", status.containsKey("hdrEnabled"))
        assertTrue("Status should contain autoDetection", status.containsKey("autoDetection"))
        assertTrue("Status should contain captureInProgress", status.containsKey("captureInProgress"))
        assertTrue("Status should contain processingInProgress", status.containsKey("processingInProgress"))
        assertTrue("Status should contain bracketingSteps", status.containsKey("bracketingSteps"))
        assertTrue("Status should contain toneMappingEnabled", status.containsKey("toneMappingEnabled"))
        assertTrue("Status should contain capturedFrames", status.containsKey("capturedFrames"))
    }

    @Test
    fun testInitialCaptureState() = runTest {
        val mockContext = SimpleMockCameraContext.createBasic()
        plugin.initialize(mockContext)

        val status = plugin.getHDRStatus()

        // Should not be capturing or processing initially
        assertFalse("Should not be capturing initially",
            status["captureInProgress"] as Boolean)
        assertFalse("Should not be processing initially",
            status["processingInProgress"] as Boolean)
        assertEquals("Should have zero captured frames initially", 0,
            status["capturedFrames"])
    }

    @Test
    fun testMultipleEnableCallsSafe() = runTest {
        val mockContext = SimpleMockCameraContext.createBasic()
        plugin.initialize(mockContext)

        // Enable multiple times should be safe
        plugin.enableHDR()
        plugin.enableHDR()
        plugin.enableHDR()

        val status = plugin.getHDRStatus()
        assertTrue("HDR should remain enabled", status["hdrEnabled"] as Boolean)
    }

    @Test
    fun testMultipleDisableCallsSafe() = runTest {
        val mockContext = SimpleMockCameraContext.createBasic()
        plugin.initialize(mockContext)

        plugin.enableHDR()
        plugin.disableHDR()
        plugin.disableHDR()
        plugin.disableHDR()

        val status = plugin.getHDRStatus()
        assertFalse("HDR should remain disabled", status["hdrEnabled"] as Boolean)
    }

    @Test
    fun testCleanupReleasesResources() = runTest {
        val mockContext = SimpleMockCameraContext.createBasic()
        plugin.initialize(mockContext)
        plugin.enableHDR()

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
        plugin.enableHDR()
        plugin.cleanup()

        // Re-initialize
        plugin.initialize(mockContext)
        plugin.enableHDR()

        assertTrue("Plugin should be enabled after re-init", plugin.isEnabled)
        val status = plugin.getHDRStatus()
        assertTrue("HDR should be enabled after re-init", status["hdrEnabled"] as Boolean)
    }

    @Test
    fun testBracketingStepsArrayCopy() = runTest {
        val mockContext = SimpleMockCameraContext.createBasic()
        plugin.initialize(mockContext)

        // Set custom steps
        val originalSteps = intArrayOf(-2, 0, 2)
        plugin.setBracketingSteps(originalSteps)

        // Modify original array
        originalSteps[0] = -5

        // Plugin should have cloned the array
        val status = plugin.getHDRStatus()
        val stepsString = status["bracketingSteps"] as String
        assertEquals("Plugin should clone array", "-2, 0, 2", stepsString)
    }

    @Test
    fun testComponentsInitializedOnSetup() = runTest {
        val mockContext = SimpleMockCameraContext.createBasic()
        plugin.initialize(mockContext)

        // After initialization, HDR components should be created
        // This is implicitly tested by successful HDR operations
        plugin.enableHDR()
        val status = plugin.getHDRStatus()
        assertNotNull("Status should be available after init", status)
    }

    @Test
    fun testProviderPattern() {
        // Test PluginProvider implementation
        val provider = HDRPlugin.Companion

        assertEquals("hdr", provider.id)
        assertTrue("HDR should be user toggleable", provider.userToggleable)
        assertTrue("HDR should show in dropdown", provider.showInDropdown)
        assertEquals("HDR category should be CAPTURE",
            com.customcamera.app.engine.plugins.PluginCategory.CAPTURE,
            provider.category)
    }
}
