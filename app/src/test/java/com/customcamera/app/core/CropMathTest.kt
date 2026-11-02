package com.customcamera.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CropMathTest {

    private val bounds = CropRect(0f, 0f, 400f, 300f)
    private val minSize = 40f
    private val eps = 1e-3f

    private fun assertRect(expected: CropRect, actual: CropRect) {
        assertEquals("left", expected.left, actual.left, eps)
        assertEquals("top", expected.top, actual.top, eps)
        assertEquals("right", expected.right, actual.right, eps)
        assertEquals("bottom", expected.bottom, actual.bottom, eps)
    }

    private fun assertInside(rect: CropRect, outer: CropRect = bounds) {
        assertTrue("$rect escapes $outer", rect.left >= outer.left - eps && rect.top >= outer.top - eps &&
            rect.right <= outer.right + eps && rect.bottom <= outer.bottom + eps)
    }

    // --- hit testing ----------------------------------------------------------------

    @Test
    fun `hit test prefers corners then edges then interior`() {
        val r = CropRect(100f, 100f, 300f, 200f)
        assertEquals(CropHandle.TOP_LEFT, CropMath.hitTest(r, 110f, 95f, 24f))
        assertEquals(CropHandle.BOTTOM_RIGHT, CropMath.hitTest(r, 290f, 210f, 24f))
        assertEquals(CropHandle.TOP, CropMath.hitTest(r, 160f, 105f, 24f))
        assertEquals(CropHandle.RIGHT, CropMath.hitTest(r, 315f, 150f, 24f))
        assertEquals(CropHandle.MOVE, CropMath.hitTest(r, 200f, 150f, 24f))
        assertNull(CropMath.hitTest(r, 20f, 20f, 24f))
        assertNull("beyond an edge's end is not that edge", CropMath.hitTest(r, 350f, 105f, 24f))
    }

    @Test
    fun `hit test on a tiny rect picks the nearer edge`() {
        val r = CropRect(100f, 100f, 120f, 120f)
        assertEquals(CropHandle.TOP_LEFT, CropMath.hitTest(r, 104f, 104f, 24f))
        assertEquals(CropHandle.BOTTOM_RIGHT, CropMath.hitTest(r, 116f, 116f, 24f))
    }

    // --- free resize, clamping, min size --------------------------------------------

    @Test
    fun `free corner drag moves only its two edges`() {
        val start = CropRect(100f, 100f, 300f, 200f)
        val out = CropMath.drag(start, CropHandle.BOTTOM_RIGHT, 50f, -20f, bounds, minSize, null)
        assertRect(CropRect(100f, 100f, 350f, 180f), out)
    }

    @Test
    fun `free drag clamps at image bounds`() {
        val start = CropRect(100f, 100f, 300f, 200f)
        val out = CropMath.drag(start, CropHandle.TOP_LEFT, -500f, -500f, bounds, minSize, null)
        assertRect(CropRect(0f, 0f, 300f, 200f), out)
        val right = CropMath.drag(start, CropHandle.RIGHT, 999f, 0f, bounds, minSize, null)
        assertEquals(400f, right.right, eps)
    }

    @Test
    fun `free drag cannot shrink below min size or invert`() {
        val start = CropRect(100f, 100f, 300f, 200f)
        val out = CropMath.drag(start, CropHandle.LEFT, 1000f, 0f, bounds, minSize, null)
        assertEquals(minSize, out.width, eps)
        assertEquals(300f, out.right, eps)
        val up = CropMath.drag(start, CropHandle.BOTTOM, 0f, -1000f, bounds, minSize, null)
        assertEquals(minSize, up.height, eps)
    }

    @Test
    fun `move translates and stops at bounds without resizing`() {
        val start = CropRect(100f, 100f, 300f, 200f)
        val out = CropMath.drag(start, CropHandle.MOVE, 500f, -500f, bounds, minSize, null)
        assertRect(CropRect(200f, 0f, 400f, 100f), out)
    }

    // --- aspect lock ----------------------------------------------------------------

    @Test
    fun `locked corner drag keeps ratio and anchors the opposite corner`() {
        val start = CropRect(100f, 100f, 200f, 200f)
        for ((dx, dy) in listOf(30f to 30f, 60f to 0f, 0f to -40f, -20f to 10f)) {
            val out = CropMath.drag(start, CropHandle.BOTTOM_RIGHT, dx, dy, bounds, minSize, 1f)
            assertEquals("square for drag $dx,$dy", out.width, out.height, eps)
            assertEquals(100f, out.left, eps)
            assertEquals(100f, out.top, eps)
        }
    }

    @Test
    fun `locked corner drag stops at the nearer bound`() {
        val start = CropRect(100f, 100f, 260f, 190f) // 16:9
        val out = CropMath.drag(start, CropHandle.BOTTOM_RIGHT, 1000f, 1000f, bounds, minSize, 16f / 9f)
        assertEquals(16f / 9f, out.width / out.height, eps)
        assertInside(out)
        // Bottom has 200px of room → 355.6px of width, more than the 300 to the right: width-limited.
        assertEquals(400f, out.right, eps)
    }

    @Test
    fun `locked corner drag respects min size on the short side`() {
        val start = CropRect(100f, 100f, 260f, 190f)
        val out = CropMath.drag(start, CropHandle.TOP_LEFT, 1000f, 1000f, bounds, minSize, 16f / 9f)
        assertEquals(minSize, out.height, eps)
        assertEquals(minSize * 16f / 9f, out.width, eps)
        assertEquals(260f, out.right, eps)
        assertEquals(190f, out.bottom, eps)
    }

    @Test
    fun `locked edge drag keeps ratio and stays inside`() {
        val start = CropRect(150f, 100f, 250f, 200f)
        val out = CropMath.drag(start, CropHandle.RIGHT, 500f, 0f, bounds, minSize, 1f)
        assertEquals(out.width, out.height, eps)
        assertInside(out)
        assertEquals(150f, out.left, eps)
        // 250px of room to the right is less than the 300px image height, so the width caps at 250.
        assertEquals(250f, out.width, eps)
        val down = CropMath.drag(start, CropHandle.BOTTOM, 0f, 1000f, bounds, minSize, 1f)
        assertEquals(down.width, down.height, eps)
        assertInside(down)
        assertEquals(100f, down.top, eps)
    }

    @Test
    fun `fit returns the largest centred rect of the ratio`() {
        assertRect(CropRect(50f, 0f, 350f, 300f), CropMath.fit(bounds, 1f))
        assertRect(CropRect(0f, 37.5f, 400f, 262.5f), CropMath.fit(bounds, 16f / 9f))
        assertRect(bounds, CropMath.fit(bounds, null))
        // Off-centre request is shifted back inside.
        assertRect(CropRect(100f, 0f, 400f, 300f), CropMath.fit(bounds, 1f, centerX = 390f))
    }

    @Test
    fun `aspect presets resolve ratios and swap on rotation`() {
        assertNull(CropAspect.FREE.ratio(4000f, 3000f))
        assertEquals(4f / 3f, CropAspect.ORIGINAL.ratio(4000f, 3000f)!!, eps)
        assertEquals(9f / 16f, CropAspect.RATIO_9_16.ratio(1f, 1f)!!, eps)
        assertEquals(CropAspect.RATIO_3_4, CropAspect.RATIO_4_3.rotated())
        assertEquals(CropAspect.RATIO_16_9, CropAspect.RATIO_9_16.rotated())
        assertEquals(CropAspect.SQUARE, CropAspect.SQUARE.rotated())
        assertEquals(CropAspect.RATIO_16_9, CropAspect.fromKey("16:9"))
        assertEquals(CropAspect.FREE, CropAspect.fromKey("bogus"))
    }

    // --- transform + rotation mapping ----------------------------------------------

    @Test
    fun `rotate while mirrored turns the stored rotation the other way`() {
        val t = EditTransform()
        assertEquals(EditTransform(90, false), t.rotatedClockwise())
        assertEquals(EditTransform(0, false), t.rotatedClockwise().rotatedClockwise().rotatedClockwise().rotatedClockwise())
        assertEquals(EditTransform(270, true), t.flippedHorizontally().rotatedClockwise())
        assertTrue(t.flippedHorizontally().flippedHorizontally().isIdentity)
        assertEquals(3000 to 4000, EditTransform(90).displaySize(4000, 3000))
    }

    @Test
    fun `normalized rect rotates and mirrors with the image`() {
        val r = CropRect(0.1f, 0.2f, 0.5f, 0.4f)
        assertRect(CropRect(0.6f, 0.1f, 0.8f, 0.5f), r.rotatedClockwise())
        assertRect(r, r.rotatedClockwise().rotatedClockwise().rotatedClockwise().rotatedClockwise())
        assertRect(CropRect(0.5f, 0.2f, 0.9f, 0.4f), r.flippedHorizontally())
    }

    @Test
    fun `normalize and denormalize round trip`() {
        val frame = CropRect(20f, 40f, 420f, 340f)
        val r = CropRect(120f, 90f, 220f, 190f)
        assertRect(r, CropMath.denormalize(CropMath.normalize(r, frame), frame))
        assertRect(CropRect(0.25f, 1f / 6f, 0.5f, 0.5f), CropMath.normalize(r, frame))
    }

    // Source 400×200. The crop picks out the displayed image's top-left quarter-ish region.
    private val n = CropRect(0f, 0f, 0.25f, 0.5f)

    @Test
    fun `identity maps straight to pixels`() {
        assertEquals(PixelRect(0, 0, 100, 100), CropMath.toSourcePixels(n, EditTransform(), 400, 200))
    }

    @Test
    fun `rotation 90 maps display top-left to source bottom-left`() {
        // Displayed 200×400: crop = x 0..50, y 0..200. Display top-left came from source bottom-left.
        assertEquals(PixelRect(0, 150, 200, 200), CropMath.toSourcePixels(n, EditTransform(90), 400, 200))
    }

    @Test
    fun `rotation 180 maps display top-left to source bottom-right`() {
        assertEquals(PixelRect(300, 100, 400, 200), CropMath.toSourcePixels(n, EditTransform(180), 400, 200))
    }

    @Test
    fun `rotation 270 maps display top-left to source top-right`() {
        assertEquals(PixelRect(200, 0, 400, 50), CropMath.toSourcePixels(n, EditTransform(270), 400, 200))
    }

    @Test
    fun `mirror maps display left to source right`() {
        assertEquals(PixelRect(300, 0, 400, 100), CropMath.toSourcePixels(n, EditTransform(0, true), 400, 200))
        // Mirrored after a 90° turn: display top-left came from source top-left.
        assertEquals(PixelRect(0, 0, 200, 50), CropMath.toSourcePixels(n, EditTransform(90, true), 400, 200))
    }

    @Test
    fun `point mapping agrees with a forward transform for every orientation`() {
        val w = 40
        val h = 20
        for (rotation in EditTransform.VALID_ROTATIONS) for (flipped in listOf(false, true)) {
            val t = EditTransform(rotation, flipped)
            val (dw, dh) = t.displaySize(w, h)
            // Forward-map every source pixel; each display pixel must come back to its source.
            for (x in 0 until w) for (y in 0 until h) {
                val (u0, v0) = when (rotation) {
                    0 -> x to y
                    90 -> (h - 1 - y) to x
                    180 -> (w - 1 - x) to (h - 1 - y)
                    else -> y to (w - 1 - x)
                }
                val u = if (flipped) dw - 1 - u0 else u0
                val v = v0
                val px = CropMath.toSourcePixels(
                    CropRect(u.toFloat() / dw, v.toFloat() / dh, (u + 1f) / dw, (v + 1f) / dh), t, w, h,
                )
                assertEquals("rot=$rotation flip=$flipped ($x,$y)", PixelRect(x, y, x + 1, y + 1), px)
            }
        }
    }

    @Test
    fun `locked ratio snaps to exact pixel ratio`() {
        // 0.333 of 1001px vs 0.444 of 751px would round to 333×333 or 334×333 depending on rounding.
        val square = CropMath.toSourcePixels(CropRect(0.1f, 0.1f, 0.4331f, 0.5441f), EditTransform(90), 751, 1001, ratio = 1f)
        assertEquals(square.width, square.height)
        // A ratio that would overflow the bottom shrinks the width instead.
        val tall = CropMath.toSourcePixels(CropRect(0f, 0.5f, 1f, 1f), EditTransform(), 100, 100, ratio = 1f)
        assertEquals(PixelRect(0, 50, 50, 100), tall)
    }

    @Test
    fun `edit is a no-op only when uncropped and untransformed`() {
        assertTrue(PhotoEdit().isNoOp)
        assertTrue("aspect alone changes nothing", PhotoEdit(aspect = CropAspect.ORIGINAL).isNoOp)
        assertTrue(!PhotoEdit(transform = EditTransform(90)).isNoOp)
        assertTrue(!PhotoEdit(crop = CropRect(0f, 0f, 0.99f, 1f)).isNoOp)
    }

    @Test
    fun `pixel rect is clamped and never empty`() {
        val r = CropMath.toSourcePixels(CropRect(-0.2f, 0.999f, 1.3f, 1.2f), EditTransform(), 100, 50)
        assertEquals(0, r.left)
        assertEquals(100, r.right)
        assertTrue(r.height >= 1 && r.bottom <= 50)
    }
}
