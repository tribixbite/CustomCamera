package com.customcamera.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.customcamera.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.cos
import kotlin.math.sin

/**
 * Drives [LevelView] with synthetic accelerometer readings and checks the pixels
 * it draws, so the "turns the accent colour when level" behaviour is verified
 * without someone tilting a phone. The view is not attached, so the real sensor
 * never feeds it.
 */
@RunWith(AndroidJUnit4::class)
class LevelViewTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val accent = ContextCompat.getColor(context, R.color.accent)

    @Test
    fun uprightPortraitDrawsTheAccentLine() {
        val view = levelView(orientation = 0)
        feed(view, rollDeg = 0f)
        assertTrue(view.isLevel)
        assertColour(accent, centrePixel(view))
    }

    @Test
    fun aFewDegreesOffLevelDrawsWhite() {
        val view = levelView(orientation = 0)
        feed(view, rollDeg = 5f)
        assertFalse(view.isLevel)
        // Off-level line: translucent white (alpha 200), not the accent colour.
        val pixel = centrePixel(view)
        assertEquals(200, Color.alpha(pixel), 2)
        assertColour(Color.rgb(255, 255, 255), pixel or (0xFF shl 24))
    }

    @Test
    fun justInsideTheToleranceStillCountsAsLevel() {
        val view = levelView(orientation = 0)
        feed(view, rollDeg = LevelView.LEVEL_TOLERANCE_DEG * 0.6f)
        assertTrue(view.isLevel)
        feed(view, rollDeg = LevelView.LEVEL_TOLERANCE_DEG * 3f)
        assertFalse(view.isLevel)
    }

    @Test
    fun landscapeHoldReadsLevelWhenTheLongEdgeIsHorizontal() {
        // Gravity along +x: the phone is on its side; the activity reports 270°.
        val view = levelView(orientation = 270)
        feed(view, rollDeg = 90f)
        assertTrue(view.isLevel)
        assertColour(accent, centrePixel(view))
    }

    @Test
    fun lyingFlatDrawsNothing() {
        val view = levelView(orientation = 0)
        repeat(SETTLE_READINGS) { view.onAcceleration(0f, 0f, G) }
        assertFalse(view.isLevel)
        assertEquals(Color.TRANSPARENT, centrePixel(view))
    }

    private fun levelView(orientation: Int): LevelView {
        lateinit var view: LevelView
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            view = LevelView(context).apply { deviceOrientation = orientation }
            val spec = View.MeasureSpec.makeMeasureSpec(SIZE_PX, View.MeasureSpec.EXACTLY)
            view.measure(spec, spec)
            view.layout(0, 0, SIZE_PX, SIZE_PX)
        }
        return view
    }

    /** Feeds a steady gravity vector at [rollDeg] until the low-pass filter settles. */
    private fun feed(view: LevelView, rollDeg: Float) {
        val r = Math.toRadians(rollDeg.toDouble())
        val x = (G * sin(r)).toFloat()
        val y = (G * cos(r)).toFloat()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            repeat(SETTLE_READINGS) { view.onAcceleration(x, y, 0f) }
        }
    }

    private fun centrePixel(view: LevelView): Int {
        val bitmap = Bitmap.createBitmap(SIZE_PX, SIZE_PX, Bitmap.Config.ARGB_8888)
        InstrumentationRegistry.getInstrumentation().runOnMainSync { view.draw(Canvas(bitmap)) }
        return bitmap.getPixel(SIZE_PX / 2, SIZE_PX / 2).also { bitmap.recycle() }
    }

    private fun assertColour(expected: Int, actual: Int) {
        val msg = "expected #%08X, got #%08X".format(expected, actual)
        assertEquals(msg, Color.red(expected), Color.red(actual), 3)
        assertEquals(msg, Color.green(expected), Color.green(actual), 3)
        assertEquals(msg, Color.blue(expected), Color.blue(actual), 3)
    }

    private fun assertEquals(expected: Int, actual: Int, tolerance: Int) =
        assertEquals("expected $expected±$tolerance, got $actual", expected.toFloat(), actual.toFloat(), tolerance.toFloat())

    private fun assertEquals(message: String, expected: Int, actual: Int, tolerance: Int) =
        assertEquals(message, expected.toFloat(), actual.toFloat(), tolerance.toFloat())

    private companion object {
        const val G = 9.81f
        const val SIZE_PX = 400

        /** 0.8 low-pass factor: 60 readings leave < 1e-5 of the starting value. */
        const val SETTLE_READINGS = 60
    }
}
