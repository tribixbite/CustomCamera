package com.customcamera.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewfinderGeometryTest {

    @Test
    fun `portrait 4-3 sits under the top bar at full width`() {
        val box = ViewfinderGeometry.preview(1080, 2400, 4f / 3f, topBarBottom = 150, railWidth = 0, railOnLeft = false)
        assertEquals(PixelBox(0, 150, 1080, 1440), box)
    }

    @Test
    fun `portrait 16-9 that does not fit under the bar is centred`() {
        val box = ViewfinderGeometry.preview(1080, 2000, 16f / 9f, topBarBottom = 150, railWidth = 0, railOnLeft = false)
        assertEquals(1920, box.height)
        assertEquals(40, box.top)
    }

    @Test
    fun `portrait aspect taller than the window is clamped to the window`() {
        val box = ViewfinderGeometry.preview(1000, 1200, 16f / 9f, topBarBottom = 100, railWidth = 0, railOnLeft = false)
        assertEquals(PixelBox(0, 0, 1000, 1200), box)
    }

    @Test
    fun `tablet landscape 4-3 fills the height left of the rail`() {
        // 2560x1600 tablet, 300 px rail: 4:3 at full height is 2133 px wide and fits in 2260.
        val box = ViewfinderGeometry.preview(2560, 1600, 4f / 3f, topBarBottom = 150, railWidth = 300, railOnLeft = false)
        assertEquals(2133, box.width)
        assertEquals(1600, box.height)
        assertEquals(0, box.top)
        assertEquals((2260 - 2133) / 2, box.left)
        assertTrue(box.left + box.width <= 2560 - 300)
    }

    @Test
    fun `phone landscape 16-9 is limited by the width and centred vertically`() {
        // 2244x1008 window, 273 px rail: 1971 px available, 16:9 at that width is 1109 tall > 1008.
        val box = ViewfinderGeometry.preview(2244, 1008, 16f / 9f, topBarBottom = 0, railWidth = 273, railOnLeft = false)
        assertEquals(1008, box.height)
        assertEquals(1792, box.width)
        assertEquals(0, box.top)
    }

    @Test
    fun `wide window with a narrow aspect leaves room on both sides`() {
        val box = ViewfinderGeometry.preview(2000, 1000, 1f, topBarBottom = 0, railWidth = 200, railOnLeft = false)
        assertEquals(PixelBox(400, 0, 1000, 1000), box)
    }

    @Test
    fun `right-to-left layouts put the rail on the left`() {
        val box = ViewfinderGeometry.preview(2000, 1000, 1f, topBarBottom = 0, railWidth = 200, railOnLeft = true)
        assertEquals(PixelBox(600, 0, 1000, 1000), box)
    }

    @Test
    fun `square window counts as portrait`() {
        assertFalse(ViewfinderGeometry.isLandscape(1000, 1000))
        assertTrue(ViewfinderGeometry.isLandscape(1001, 1000))
    }
}
