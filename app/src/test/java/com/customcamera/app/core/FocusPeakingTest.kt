package com.customcamera.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer

class FocusPeakingTest {

    /** Builds a Y plane of [w]×[h] with an optional row padding, filled by [luma]. */
    private fun plane(w: Int, h: Int, rowStride: Int = w, luma: (Int, Int) -> Int): ByteBuffer {
        val buffer = ByteBuffer.allocate(rowStride * h)
        for (y in 0 until h) for (x in 0 until w) buffer.put(y * rowStride + x, luma(x, y).toByte())
        return buffer
    }

    @Test
    fun `flat frame marks nothing`() {
        val mask = FocusPeaking.detect(plane(64, 48) { _, _ -> 128 }, 64, 48, 64)
        assertEquals(0, mask.count)
    }

    @Test
    fun `sharp vertical edge is marked along the edge only`() {
        val mask = FocusPeaking.detect(plane(64, 48) { x, _ -> if (x < 32) 20 else 230 }, 64, 48, 64, step = 2)
        assertEquals(32, mask.width)
        assertEquals(24, mask.height)
        // Edge between sample columns 15 (x=30) and 16 (x=32).
        assertTrue(mask[15, 10])
        assertTrue(mask[16, 10])
        assertFalse(mask[5, 10])
        assertFalse(mask[26, 10])
    }

    @Test
    fun `soft gradient below the minimum is ignored`() {
        // 2 levels per pixel → central difference of 8 at step 2, well under the minimum.
        val mask = FocusPeaking.detect(plane(64, 48) { x, _ -> x * 2 }, 64, 48, 64)
        assertEquals(0, mask.count)
    }

    @Test
    fun `marked fraction stays within the budget on busy texture`() {
        val mask = FocusPeaking.detect(plane(80, 60) { x, y -> if ((x + y) % 2 == 0) 0 else 255 }, 80, 60, 80, step = 1, topFraction = 0.1f)
        assertTrue("marked ${mask.count}", mask.count <= (80 * 60 * 0.1f).toInt())
    }

    @Test
    fun `row stride padding is honoured`() {
        val mask = FocusPeaking.detect(plane(64, 48, rowStride = 80) { x, _ -> if (x < 32) 20 else 230 }, 64, 48, 80, step = 2)
        assertTrue(mask[16, 10])
        assertFalse(mask[5, 10])
    }

    @Test
    fun `reused scratch gives the same result as fresh buffers`() {
        val scratch = FocusPeaking.Scratch()
        val edge = plane(64, 48) { x, _ -> if (x < 32) 20 else 230 }
        val flat = plane(64, 48) { _, _ -> 90 }
        FocusPeaking.detect(edge, 64, 48, 64, scratch = scratch)
        // A stale gradient/histogram from the previous frame must not leak into this one.
        assertEquals(0, FocusPeaking.detect(flat, 64, 48, 64, scratch = scratch).count)
        val again = FocusPeaking.detect(edge, 64, 48, 64, scratch = scratch)
        assertEquals(FocusPeaking.detect(edge, 64, 48, 64).cells.toList(), again.cells.toList())
    }

    @Test
    fun `threshold for budget`() {
        val histogram = IntArray(511).also { it[10] = 100; it[200] = 5; it[300] = 3 }
        // Exactly the 8 strongest fit: any threshold in 11..200 marks them; the smallest is returned.
        assertEquals(11, FocusPeaking.thresholdForTopFraction(histogram, 8))
        // 7 cannot include the bin of 5 at 200 → only the 3 at 300 remain.
        assertEquals(201, FocusPeaking.thresholdForTopFraction(histogram, 7))
        // Nothing fits: above the highest populated bin.
        assertEquals(301, FocusPeaking.thresholdForTopFraction(histogram, 0))
        assertEquals(0, FocusPeaking.thresholdForTopFraction(histogram, 1000))
    }

    @Test
    fun `rotation moves cells clockwise`() {
        // 3x2 mask with only the top-left cell set.
        val mask = PeakingMask(3, 2, 2, booleanArrayOf(true, false, false, false, false, false))
        val r90 = mask.rotated(90)
        assertEquals(2, r90.width)
        assertEquals(3, r90.height)
        assertTrue(r90[1, 0]) // top-left → top-right
        val r180 = mask.rotated(180)
        assertTrue(r180[2, 1]) // → bottom-right
        val r270 = mask.rotated(270)
        assertTrue(r270[0, 2]) // → bottom-left
        assertEquals(1, r270.count)
        assertTrue(mask.rotated(360)[0, 0])
    }
}
