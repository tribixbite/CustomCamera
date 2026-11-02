package com.customcamera.app.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import android.view.View
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.customcamera.app.R
import com.customcamera.app.camera.RecordingStatus
import com.customcamera.app.core.CaptureMode
import com.customcamera.app.core.FlashOption
import com.customcamera.app.core.TimerOption
import com.customcamera.app.gallery.MediaItem
import com.customcamera.app.gallery.MediaRepository
import com.customcamera.app.settings.AppSettings
import com.customcamera.app.waitUntil
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end viewfinder tests on a real camera. Every capture made here is
 * deleted afterwards, and the app's preferences are restored.
 */
@RunWith(AndroidJUnit4::class)
class CameraActivityTest {

    @get:Rule
    val permissions: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    private val repository = MediaRepository.get(context)
    private lateinit var savedPrefs: Map<String, *>
    private lateinit var existing: Set<android.net.Uri>

    @Before
    fun setUp() {
        assumeTrue("needs scoped storage to save without a runtime prompt", Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        savedPrefs = HashMap(prefs.all)
        // Deterministic, quiet starting state: photo mode, back camera, no timer.
        prefs.edit()
            .putString(AppSettings.Keys.LAST_MODE, CaptureMode.PHOTO.key)
            .putBoolean(AppSettings.Keys.LAST_FACING_FRONT, false)
            .remove(AppSettings.Keys.LAST_CAMERA_ID)
            .putString(AppSettings.Keys.TIMER, TimerOption.OFF.key)
            .putBoolean(AppSettings.Keys.SHUTTER_SOUND, false)
            .putBoolean(AppSettings.Keys.LOCATION, false)
            .commit()
        existing = runBlocking { repository.loadAll().map { it.uri }.toSet() }
    }

    @After
    fun tearDown() {
        if (!::existing.isInitialized) return
        runBlocking { newItems().forEach { repository.delete(it) } }
        prefs.edit().clear().apply {
            savedPrefs.forEach { (k, v) ->
                when (v) {
                    is Boolean -> putBoolean(k, v)
                    is Int -> putInt(k, v)
                    is Long -> putLong(k, v)
                    is Float -> putFloat(k, v)
                    is String -> putString(k, v)
                }
            }
        }.commit()
    }

    private fun newItems(): List<MediaItem> = runBlocking { repository.loadAll().filter { it.uri !in existing } }

    private fun ActivityScenario<CameraActivity>.state() = run {
        var s: com.customcamera.app.camera.CameraState? = null
        onActivity { s = it.cameraStateForTest() }
        s!!
    }

    private fun ActivityScenario<CameraActivity>.awaitReady() =
        waitUntil(15_000, message = "camera ready") { state().ready }

    private fun ActivityScenario<CameraActivity>.selectMode(mode: CaptureMode) {
        onActivity { activity ->
            val tabs = activity.findViewById<TabLayout>(R.id.modeTabs)
            val tab = (0 until tabs.tabCount).map { tabs.getTabAt(it)!! }.first { it.tag == mode }
            tab.select()
        }
        waitUntil(10_000, message = "mode $mode bound") { state().let { it.mode == mode && it.ready } }
    }

    private fun ActivityScenario<CameraActivity>.clickShutter() =
        onActivity { it.findViewById<View>(R.id.shutterButton).performClick() }

    @Test
    fun bindsCameraAndEveryModeShowsItsControls() {
        ActivityScenario.launch(CameraActivity::class.java).use { scenario ->
            scenario.awaitReady()
            val modes = scenario.state().availableModes
            assertTrue(modes.containsAll(listOf(CaptureMode.PHOTO, CaptureMode.VIDEO, CaptureMode.PRO, CaptureMode.SCAN)))
            for (mode in modes) {
                scenario.selectMode(mode)
                scenario.onActivity { a ->
                    val pro = a.findViewById<View>(R.id.proPanel)
                    val scan = a.findViewById<View>(R.id.barcodeOverlay)
                    val shutter = a.findViewById<ShutterButton>(R.id.shutterButton)
                    assertEquals("pro panel in $mode", mode == CaptureMode.PRO, pro.visibility == View.VISIBLE)
                    assertEquals("scan overlay in $mode", mode == CaptureMode.SCAN, scan.visibility == View.VISIBLE)
                    assertEquals("shutter in $mode", mode != CaptureMode.SCAN, shutter.visibility == View.VISIBLE)
                    if (mode.isVideo) assertEquals(ShutterButton.Style.VIDEO, shutter.style)
                    if (mode.isPhotoLike) assertEquals(ShutterButton.Style.PHOTO, shutter.style)
                }
            }
        }
    }

    @Test
    fun switchingCamerasRebindsEachLens() {
        ActivityScenario.launch(CameraActivity::class.java).use { scenario ->
            scenario.awaitReady()
            val start = scenario.state()
            assumeTrue("needs front and back cameras", start.lenses.any { it.isFront } && start.lenses.any { !it.isFront })
            scenario.onActivity { it.findViewById<View>(R.id.flipButton).performClick() }
            waitUntil(10_000, message = "front camera") { scenario.state().let { it.ready && it.isFront } }
            // Every individually exposed camera must bind (or fall back) without crashing.
            for (lens in start.lenses) {
                scenario.onActivity { it.selectCameraForTest(lens.cameraId) }
                waitUntil(10_000, message = "camera ${lens.cameraId}") { scenario.state().let { it.ready && it.currentCameraId != null } }
            }
        }
    }

    @Test
    fun photoIsSavedToDcim() {
        ActivityScenario.launch(CameraActivity::class.java).use { scenario ->
            scenario.awaitReady()
            scenario.clickShutter()
            waitUntil(20_000, message = "photo in MediaStore") { newItems().any { !it.isVideo } }
            val photo = newItems().first { !it.isVideo }
            assertTrue(photo.displayName, Regex("""IMG_\d{8}_\d{6}_\d{3}\.jpg""").matches(photo.displayName))
            assertTrue("photo is empty", photo.sizeBytes > 0)
        }
    }

    @Test
    fun videoIsRecordedPausedAndSaved() {
        ActivityScenario.launch(CameraActivity::class.java).use { scenario ->
            scenario.awaitReady()
            scenario.selectMode(CaptureMode.VIDEO)
            scenario.clickShutter()
            waitUntil(10_000, message = "recording started") { scenario.state().recording == RecordingStatus.RECORDING }
            scenario.onActivity { it.findViewById<View>(R.id.pauseButton).performClick() }
            waitUntil(5_000, message = "paused") { scenario.state().recording == RecordingStatus.PAUSED }
            scenario.onActivity { it.findViewById<View>(R.id.pauseButton).performClick() }
            waitUntil(5_000, message = "resumed") { scenario.state().recording == RecordingStatus.RECORDING }
            Thread.sleep(1_500)
            scenario.clickShutter()
            waitUntil(15_000, message = "video in MediaStore") { newItems().any { it.isVideo } }
            // MediaStore fills DURATION asynchronously after the file is published.
            waitUntil(15_000, message = "video duration indexed") { newItems().any { it.isVideo && it.durationMs > 0 } }
            val video = newItems().first { it.isVideo }
            assertTrue(video.displayName.endsWith(".mp4"))
            assertTrue("video too short: ${video.durationMs}ms", video.durationMs >= 1_000)
        }
    }

    @Test
    fun dualModeRecordsPictureInPictureVideo() {
        ActivityScenario.launch(CameraActivity::class.java).use { scenario ->
            scenario.awaitReady()
            assumeTrue("device reports no concurrent front+back pair", CaptureMode.DUAL in scenario.state().availableModes)
            scenario.selectMode(CaptureMode.DUAL)
            // Moving the inset and swapping cameras both rebind the concurrent session.
            val corner = scenario.state().pipCorner
            scenario.onActivity { it.setPipCornerForTest(corner.next()) }
            waitUntil(10_000, message = "inset moved") { scenario.state().let { it.ready && it.pipCorner == corner.next() } }
            val frontMain = scenario.state().dualFrontMain
            scenario.onActivity { it.findViewById<View>(R.id.flipButton).performClick() }
            waitUntil(10_000, message = "cameras swapped") { scenario.state().let { it.ready && it.dualFrontMain != frontMain && it.mode == CaptureMode.DUAL } }

            scenario.clickShutter()
            waitUntil(10_000, message = "recording started") { scenario.state().recording == RecordingStatus.RECORDING }
            Thread.sleep(2_000)
            scenario.clickShutter()
            waitUntil(15_000, message = "video in MediaStore") { newItems().any { it.isVideo } }
            waitUntil(15_000, message = "video duration indexed") { newItems().any { it.isVideo && it.durationMs > 0 } }
            assertTrue(newItems().first { it.isVideo }.durationMs >= 1_000)
        }
    }

    @Test
    fun focusPeakingAppearsWithManualFocus() {
        prefs.edit().putBoolean(AppSettings.Keys.FOCUS_PEAKING, true).commit()
        ActivityScenario.launch(CameraActivity::class.java).use { scenario ->
            scenario.awaitReady()
            scenario.selectMode(CaptureMode.PRO)
            assumeTrue("camera has no manual focus", scenario.state().manualCapabilities.manualFocus)
            val peaking = { var v = false; scenario.onActivity { v = it.findViewById<View>(R.id.focusPeaking).visibility == View.VISIBLE }; v }
            scenario.onActivity { it.setManualFocusForTest(null) }
            waitUntil(5_000, message = "peaking hidden on autofocus") { !peaking() }
            scenario.onActivity { it.setManualFocusForTest(0f) }
            waitUntil(5_000, message = "peaking shown on manual focus") { peaking() }
            waitUntil(5_000, message = "peaking frame delivered") {
                var drawn = false
                scenario.onActivity { drawn = it.findViewById<FocusPeakingView>(R.id.focusPeaking).hasFrame }
                drawn
            }
            scenario.onActivity { it.setManualFocusForTest(null) }
            waitUntil(5_000, message = "peaking hidden again") { !peaking() }
        }
    }

    @Test
    fun torchStrengthIsAdjustable() {
        ActivityScenario.launch(CameraActivity::class.java).use { scenario ->
            scenario.awaitReady()
            scenario.selectMode(CaptureMode.VIDEO)
            val st = scenario.state()
            assumeTrue("torch strength not adjustable", st.hasFlashUnit && st.torchStrengthAdjustable)
            scenario.onActivity { it.findViewById<View>(R.id.flashButton).performClick() }
            waitUntil(5_000, message = "torch on") { scenario.state().flash == FlashOption.TORCH }
            scenario.onActivity { a ->
                assertEquals(View.VISIBLE, a.findViewById<View>(R.id.torchBar).visibility)
                a.setTorchStrengthForTest(1)
            }
            waitUntil(5_000, message = "torch level 1") { scenario.state().torchStrength == 1 }
            scenario.onActivity { it.findViewById<View>(R.id.flashButton).performClick() }
            waitUntil(5_000, message = "torch off") { scenario.state().flash == FlashOption.OFF }
        }
    }

    @Test
    fun imageCaptureIntentReturnsThumbnail() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).setClass(context, CameraActivity::class.java)
        ActivityScenario.launchActivityForResult<CameraActivity>(intent).use { scenario ->
            scenario.awaitReady()
            scenario.onActivity { a ->
                // Capture intents hide the gallery entry point and mode switching.
                assertEquals(View.GONE, a.findViewById<View>(R.id.thumbnail).visibility)
                assertEquals(View.GONE, a.findViewById<View>(R.id.modeTabs).visibility)
            }
            scenario.clickShutter()
            waitUntil(20_000, message = "review shown") {
                var shown = false
                scenario.onActivity { shown = it.findViewById<View>(R.id.reviewPanel).visibility == View.VISIBLE }
                shown
            }
            scenario.onActivity { it.findViewById<View>(R.id.reviewUse).performClick() }
            val result = scenario.result
            assertEquals(Activity.RESULT_OK, result.resultCode)
            @Suppress("DEPRECATION")
            val bitmap = result.resultData.getParcelableExtra<Bitmap>("data")
            assertNotNull("no thumbnail in result", bitmap)
            assertTrue(bitmap!!.width in 1..320 && bitmap.height in 1..320)
            // The review photo must not leak into the gallery.
            assertTrue(newItems().isEmpty())
        }
    }
}
