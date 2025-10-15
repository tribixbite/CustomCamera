package com.customcamera.app

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.rule.GrantPermissionRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Camera Activity UI Tests
 *
 * Tests camera interface elements and user interactions.
 * Requires camera permissions.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class CameraActivityUITest {

    @get:Rule
    val activityRule = ActivityScenarioRule(CameraActivityEngine::class.java)

    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        android.Manifest.permission.CAMERA,
        android.Manifest.permission.RECORD_AUDIO,
        android.Manifest.permission.WRITE_EXTERNAL_STORAGE
    )

    @Test
    fun testCameraPreviewDisplayed() {
        // Verify camera preview is shown
        onView(withId(R.id.previewView))
            .check(matches(isDisplayed()))
    }

    @Test
    fun testCaptureButtonExists() {
        // Verify capture button is present
        onView(withId(R.id.captureButton))
            .check(matches(isDisplayed()))
            .check(matches(isClickable()))
    }

    @Test
    fun testFlashButtonExists() {
        // Verify flash button is present
        onView(withId(R.id.flashButton))
            .check(matches(isDisplayed()))
            .check(matches(isClickable()))
    }

    @Test
    fun testSwitchCameraButtonExists() {
        // Verify switch camera button is present
        onView(withId(R.id.switchCameraButton))
            .check(matches(isDisplayed()))
            .check(matches(isClickable()))
    }

    @Test
    fun testGalleryButtonExists() {
        // Verify gallery button is present
        onView(withId(R.id.galleryButton))
            .check(matches(isDisplayed()))
            .check(matches(isClickable()))
    }

    @Test
    fun testSettingsButtonExists() {
        // Verify settings button is present
        onView(withId(R.id.settingsButton))
            .check(matches(isDisplayed()))
            .check(matches(isClickable()))
    }

    @Test
    fun testPiPButtonExists() {
        // Verify PiP button is present
        onView(withId(R.id.pipButton))
            .check(matches(isDisplayed()))
            .check(matches(isClickable()))
    }

    @Test
    fun testVideoRecordButtonExists() {
        // Verify video record button is present
        onView(withId(R.id.videoRecordButton))
            .check(matches(isDisplayed()))
            .check(matches(isClickable()))
    }

    @Test
    fun testNightModeButtonExists() {
        // Verify night mode button is present
        onView(withId(R.id.nightModeButton))
            .check(matches(isDisplayed()))
            .check(matches(isClickable()))
    }

    @Test
    fun testPluginControlsPanelExists() {
        // Verify plugin controls panel is present
        onView(withId(R.id.pluginControlsPanel))
            .check(matches(isDisplayed()))
    }

    @Test
    fun testFlashButtonClickable() {
        // Click flash button - should cycle flash modes
        onView(withId(R.id.flashButton))
            .perform(click())

        // Should still be displayed after click
        onView(withId(R.id.flashButton))
            .check(matches(isDisplayed()))
    }

    @Test
    fun testCaptureButtonClickInteraction() {
        // Click capture button
        onView(withId(R.id.captureButton))
            .perform(click())

        // Button should still exist after capture
        onView(withId(R.id.captureButton))
            .check(matches(isDisplayed()))
    }
}
