package com.customcamera.app

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * MainActivity UI Tests
 *
 * Tests the main app entry point and navigation to camera.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class MainActivityUITest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun testMainActivityLaunches() {
        // Verify main activity displays
        onView(withId(R.id.root))
            .check(matches(isDisplayed()))
    }

    @Test
    fun testLaunchCameraButtonExists() {
        // Verify launch camera button is present
        onView(withId(R.id.launchCameraButton))
            .check(matches(isDisplayed()))
    }

    @Test
    fun testLaunchCameraButtonClickable() {
        // Verify button is clickable
        onView(withId(R.id.launchCameraButton))
            .check(matches(isClickable()))
    }

    @Test
    fun testAppTitleDisplayed() {
        // Verify app title is shown
        onView(withId(R.id.appTitle))
            .check(matches(isDisplayed()))
            .check(matches(withText("CustomCamera")))
    }

    @Test
    fun testLaunchCameraButtonHasText() {
        // Verify button has correct text
        onView(withId(R.id.launchCameraButton))
            .check(matches(withText(R.string.launch_camera)))
    }
}
