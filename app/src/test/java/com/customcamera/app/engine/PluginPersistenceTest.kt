package com.customcamera.app.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Plugin Persistence Tests
 *
 * Tests for plugin enable/disable state and settings persistence:
 * - All 23 plugins enable/disable persistence
 * - Plugin-specific settings persistence
 * - Default enabled states
 * - Multiple plugins persistence
 * - Settings isolation between plugins
 *
 * Note: These are Robolectric tests running on JVM with simulated Android context.
 * Run with: ./gradlew test
 */
@RunWith(RobolectricTestRunner::class)
class PluginPersistenceTest {

    private lateinit var context: Context
    private lateinit var settingsManager: SettingsManager

    companion object {
        // All 23 plugin names from PluginRegistry
        val ALL_PLUGIN_NAMES = listOf(
            // OVERLAYS (1)
            "GridOverlay",

            // ANALYSIS (7)
            "Barcode",
            "Histogram",
            "CameraInfo",
            "ExposureAnalysis",
            "MotionDetection",
            "QRScanner",
            "SharpnessAnalysis",

            // CONTROLS (4)
            "AutoFocus",
            "ExposureControl",
            "ManualFocus",
            "ProControls",

            // AI (3)
            "SmartScene",
            "SmartAdjustments",
            "ObjectDetection",

            // CAPTURE (7)
            "Crop",
            "DualCameraPiP",
            "RAWCapture",
            "AdvancedVideoRecording",
            "NightMode",
            "HDR",

            // DEBUG (1)
            "DiagnosticOverlay"
        )
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
    // Default Enabled State Tests
    // ============================================================

    @Test
    fun testPluginDefaultEnabledState() {
        // All plugins should default to enabled (true)
        for (pluginName in ALL_PLUGIN_NAMES) {
            assertTrue(
                "Plugin '$pluginName' should be enabled by default",
                settingsManager.isPluginEnabled(pluginName)
            )
        }
    }

    // ============================================================
    // Individual Plugin Persistence Tests
    // ============================================================

    @Test
    fun testGridOverlayPluginPersistence() {
        testPluginPersistence("GridOverlay")
    }

    @Test
    fun testBarcodePluginPersistence() {
        testPluginPersistence("Barcode")
    }

    @Test
    fun testHistogramPluginPersistence() {
        testPluginPersistence("Histogram")
    }

    @Test
    fun testCameraInfoPluginPersistence() {
        testPluginPersistence("CameraInfo")
    }

    @Test
    fun testExposureAnalysisPluginPersistence() {
        testPluginPersistence("ExposureAnalysis")
    }

    @Test
    fun testMotionDetectionPluginPersistence() {
        testPluginPersistence("MotionDetection")
    }

    @Test
    fun testQRScannerPluginPersistence() {
        testPluginPersistence("QRScanner")
    }

    @Test
    fun testSharpnessAnalysisPluginPersistence() {
        testPluginPersistence("SharpnessAnalysis")
    }

    @Test
    fun testAutoFocusPluginPersistence() {
        testPluginPersistence("AutoFocus")
    }

    @Test
    fun testExposureControlPluginPersistence() {
        testPluginPersistence("ExposureControl")
    }

    @Test
    fun testManualFocusPluginPersistence() {
        testPluginPersistence("ManualFocus")
    }

    @Test
    fun testProControlsPluginPersistence() {
        testPluginPersistence("ProControls")
    }

    @Test
    fun testSmartScenePluginPersistence() {
        testPluginPersistence("SmartScene")
    }

    @Test
    fun testSmartAdjustmentsPluginPersistence() {
        testPluginPersistence("SmartAdjustments")
    }

    @Test
    fun testObjectDetectionPluginPersistence() {
        testPluginPersistence("ObjectDetection")
    }

    @Test
    fun testCropPluginPersistence() {
        testPluginPersistence("Crop")
    }

    @Test
    fun testDualCameraPiPPluginPersistence() {
        testPluginPersistence("DualCameraPiP")
    }

    @Test
    fun testRAWCapturePluginPersistence() {
        testPluginPersistence("RAWCapture")
    }

    @Test
    fun testAdvancedVideoRecordingPluginPersistence() {
        testPluginPersistence("AdvancedVideoRecording")
    }

    @Test
    fun testNightModePluginPersistence() {
        testPluginPersistence("NightMode")
    }

    @Test
    fun testHDRPluginPersistence() {
        testPluginPersistence("HDR")
    }

    @Test
    fun testDiagnosticOverlayPluginPersistence() {
        testPluginPersistence("DiagnosticOverlay")
    }

    /**
     * Helper method to test plugin persistence
     */
    private fun testPluginPersistence(pluginName: String) {
        // Disable plugin
        settingsManager.setPluginEnabled(pluginName, false)

        assertFalse(
            "Plugin should be disabled immediately",
            settingsManager.isPluginEnabled(pluginName)
        )

        // Create new instance to test persistence
        val newSettings = SettingsManager(context)
        assertFalse(
            "Plugin disabled state should persist across instances",
            newSettings.isPluginEnabled(pluginName)
        )

        // Re-enable plugin
        newSettings.setPluginEnabled(pluginName, true)

        assertTrue(
            "Plugin should be enabled immediately",
            newSettings.isPluginEnabled(pluginName)
        )

        // Verify persistence again
        val anotherSettings = SettingsManager(context)
        assertTrue(
            "Plugin enabled state should persist across instances",
            anotherSettings.isPluginEnabled(pluginName)
        )
    }

    // ============================================================
    // Multiple Plugins Persistence Tests
    // ============================================================

    @Test
    fun testAllPluginsDisablePersistence() {
        // Disable all plugins
        for (pluginName in ALL_PLUGIN_NAMES) {
            settingsManager.setPluginEnabled(pluginName, false)
        }

        // Verify all disabled
        for (pluginName in ALL_PLUGIN_NAMES) {
            assertFalse(
                "Plugin '$pluginName' should be disabled",
                settingsManager.isPluginEnabled(pluginName)
            )
        }

        // Create new instance
        val newSettings = SettingsManager(context)

        // Verify all still disabled
        for (pluginName in ALL_PLUGIN_NAMES) {
            assertFalse(
                "Plugin '$pluginName' disabled state should persist",
                newSettings.isPluginEnabled(pluginName)
            )
        }
    }

    @Test
    fun testMixedPluginStatesPersistence() {
        // Set every other plugin to disabled
        for ((index, pluginName) in ALL_PLUGIN_NAMES.withIndex()) {
            settingsManager.setPluginEnabled(pluginName, index % 2 == 0)
        }

        // Create new instance
        val newSettings = SettingsManager(context)

        // Verify states persisted
        for ((index, pluginName) in ALL_PLUGIN_NAMES.withIndex()) {
            val expectedState = (index % 2 == 0)
            assertEquals(
                "Plugin '$pluginName' state should persist",
                expectedState,
                newSettings.isPluginEnabled(pluginName)
            )
        }
    }

    @Test
    fun testPluginStatesIndependent() {
        // Disable first plugin
        settingsManager.setPluginEnabled(ALL_PLUGIN_NAMES[0], false)

        // Verify only first plugin is disabled
        assertFalse(
            "First plugin should be disabled",
            settingsManager.isPluginEnabled(ALL_PLUGIN_NAMES[0])
        )

        // Verify all other plugins are still enabled
        for (i in 1 until ALL_PLUGIN_NAMES.size) {
            assertTrue(
                "Plugin '${ALL_PLUGIN_NAMES[i]}' should still be enabled",
                settingsManager.isPluginEnabled(ALL_PLUGIN_NAMES[i])
            )
        }
    }

    // ============================================================
    // Plugin Settings Persistence Tests
    // ============================================================

    @Test
    fun testPluginSettingPersistence() {
        val pluginName = "GridOverlay"
        val settingKey = "gridType"
        val settingValue = "RULE_OF_THIRDS"

        // Set plugin setting
        settingsManager.setPluginSetting(pluginName, settingKey, settingValue)

        // Verify setting is set
        assertEquals(
            "Setting should be set immediately",
            settingValue,
            settingsManager.getPluginSetting(pluginName, settingKey, "")
        )

        // Create new instance
        val newSettings = SettingsManager(context)

        // Verify setting persisted
        assertEquals(
            "Plugin setting should persist across instances",
            settingValue,
            newSettings.getPluginSetting(pluginName, settingKey, "")
        )
    }

    @Test
    fun testMultiplePluginSettingsPersistence() {
        // Set settings for multiple plugins
        settingsManager.setPluginSetting("GridOverlay", "gridType", "GOLDEN_RATIO")
        settingsManager.setPluginSetting("Barcode", "scanMode", "continuous")
        settingsManager.setPluginSetting("HDR", "strength", "high")

        // Create new instance
        val newSettings = SettingsManager(context)

        // Verify all settings persisted
        assertEquals(
            "GOLDEN_RATIO",
            newSettings.getPluginSetting("GridOverlay", "gridType", "")
        )
        assertEquals(
            "continuous",
            newSettings.getPluginSetting("Barcode", "scanMode", "")
        )
        assertEquals(
            "high",
            newSettings.getPluginSetting("HDR", "strength", "")
        )
    }

    @Test
    fun testPluginSettingsIsolation() {
        val settingKey = "mode"

        // Set same setting key for different plugins
        settingsManager.setPluginSetting("Plugin1", settingKey, "value1")
        settingsManager.setPluginSetting("Plugin2", settingKey, "value2")
        settingsManager.setPluginSetting("Plugin3", settingKey, "value3")

        // Verify settings are isolated per plugin
        assertEquals("value1", settingsManager.getPluginSetting("Plugin1", settingKey, ""))
        assertEquals("value2", settingsManager.getPluginSetting("Plugin2", settingKey, ""))
        assertEquals("value3", settingsManager.getPluginSetting("Plugin3", settingKey, ""))
    }

    @Test
    fun testPluginSettingDefaultValue() {
        val defaultValue = "default_mode"

        // Get setting that doesn't exist
        val value = settingsManager.getPluginSetting(
            "NonexistentPlugin",
            "nonexistentSetting",
            defaultValue
        )

        assertEquals(
            "Should return default value for nonexistent setting",
            defaultValue,
            value
        )
    }

    @Test
    fun testPluginSettingEmptyString() {
        val pluginName = "TestPlugin"
        val settingKey = "emptySetting"

        // Set empty string
        settingsManager.setPluginSetting(pluginName, settingKey, "")

        // Verify empty string is stored
        assertEquals(
            "",
            settingsManager.getPluginSetting(pluginName, settingKey, "default")
        )

        // Verify persistence
        val newSettings = SettingsManager(context)
        assertEquals(
            "",
            newSettings.getPluginSetting(pluginName, settingKey, "default")
        )
    }

    // ============================================================
    // Stress Tests
    // ============================================================

    @Test
    fun testRapidPluginStateChanges() {
        val pluginName = "TestPlugin"

        // Rapidly toggle plugin state
        repeat(100) { i ->
            settingsManager.setPluginEnabled(pluginName, i % 2 == 0)
        }

        // Final state should be false (99 % 2 == 1)
        assertFalse(settingsManager.isPluginEnabled(pluginName))

        // Verify persistence
        val newSettings = SettingsManager(context)
        assertFalse(newSettings.isPluginEnabled(pluginName))
    }

    @Test
    fun testManyPluginSettings() {
        val pluginName = "TestPlugin"

        // Set many settings for one plugin
        for (i in 0 until 50) {
            settingsManager.setPluginSetting(pluginName, "setting_$i", "value_$i")
        }

        // Create new instance
        val newSettings = SettingsManager(context)

        // Verify all settings persisted
        for (i in 0 until 50) {
            assertEquals(
                "value_$i",
                newSettings.getPluginSetting(pluginName, "setting_$i", "")
            )
        }
    }

    @Test
    fun testPluginSettingsManagerRecreation() {
        // Set plugin states and settings
        settingsManager.setPluginEnabled("GridOverlay", false)
        settingsManager.setPluginSetting("GridOverlay", "gridType", "NINE_GRID")

        // Recreate settings manager multiple times
        repeat(5) {
            val newSettings = SettingsManager(context)

            assertFalse(
                "Plugin state should persist through recreation",
                newSettings.isPluginEnabled("GridOverlay")
            )
            assertEquals(
                "Plugin setting should persist through recreation",
                "NINE_GRID",
                newSettings.getPluginSetting("GridOverlay", "gridType", "")
            )
        }
    }

    @Test
    fun testAllPluginsWithSettingsPersistence() {
        // Set state and one setting for each plugin
        for (pluginName in ALL_PLUGIN_NAMES) {
            settingsManager.setPluginEnabled(pluginName, false)
            settingsManager.setPluginSetting(pluginName, "testSetting", "testValue_$pluginName")
        }

        // Create new instance
        val newSettings = SettingsManager(context)

        // Verify all plugins states and settings persisted
        for (pluginName in ALL_PLUGIN_NAMES) {
            assertFalse(
                "Plugin '$pluginName' should be disabled",
                newSettings.isPluginEnabled(pluginName)
            )
            assertEquals(
                "Plugin '$pluginName' setting should persist",
                "testValue_$pluginName",
                newSettings.getPluginSetting(pluginName, "testSetting", "")
            )
        }
    }
}
