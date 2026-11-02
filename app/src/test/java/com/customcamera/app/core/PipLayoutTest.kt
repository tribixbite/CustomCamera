package com.customcamera.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PipLayoutTest {

    @Test
    fun `bottom right inset matches the CameraX reference layout`() {
        // developer blog example: scale 1/3, offset (2/3 - 0.1, -2/3 + 0.1).
        val c = PipLayout.inset(PipCorner.BOTTOM_RIGHT, scale = 1 / 3f, margin = 0.1f)
        assertEquals(2 / 3f - 0.1f, c.offsetX, 1e-5f)
        assertEquals(-2 / 3f + 0.1f, c.offsetY, 1e-5f)
    }

    @Test
    fun `inset rect lies inside the frame in the requested corner`() {
        PipCorner.entries.forEach { corner ->
            val r = PipLayout.insetRect(PipLayout.inset(corner))
            assertTrue("$corner $r", r.left >= 0f && r.top >= 0f && r.right <= 1f && r.bottom <= 1f)
            val right = r.left > 0.5f
            val bottom = r.top > 0.5f
            assertEquals(corner == PipCorner.BOTTOM_RIGHT || corner == PipCorner.TOP_RIGHT, right)
            assertEquals(corner == PipCorner.BOTTOM_RIGHT || corner == PipCorner.BOTTOM_LEFT, bottom)
        }
    }

    @Test
    fun `full frame covers the view`() {
        assertEquals(NormRect(0f, 0f, 1f, 1f), PipLayout.insetRect(PipLayout.FULL))
    }

    @Test
    fun `corners cycle and keys round trip`() {
        var c = PipCorner.BOTTOM_RIGHT
        repeat(PipCorner.entries.size) { c = c.next() }
        assertEquals(PipCorner.BOTTOM_RIGHT, c)
        PipCorner.entries.forEach { assertEquals(it, PipCorner.fromKey(it.key)) }
        assertEquals(PipCorner.TOP_RIGHT, PipCorner.fromKey("nope"))
        assertEquals(PipCorner.BOTTOM_RIGHT, PipCorner.TOP_RIGHT.next())
    }

    @Test
    fun `hit test`() {
        val r = PipLayout.insetRect(PipLayout.inset(PipCorner.BOTTOM_RIGHT))
        assertTrue(r.contains((r.left + r.right) / 2, (r.top + r.bottom) / 2))
        assertFalse(r.contains(0.1f, 0.1f))
    }
}
