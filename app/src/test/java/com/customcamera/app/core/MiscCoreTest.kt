package com.customcamera.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.util.Date
import java.util.TimeZone

class MediaNamingTest {

    private val utc = TimeZone.getTimeZone("UTC")
    private val time = Date(1_793_000_000_123L) // 2026-10-26T07:33:20.123Z

    @Test
    fun `names follow dcim convention`() {
        assertEquals("IMG_20261026_073320_123.jpg", MediaNaming.fileName(MediaNaming.Kind.PHOTO, time, utc))
        assertEquals("VID_20261026_073320_123.mp4", MediaNaming.fileName(MediaNaming.Kind.VIDEO, time, utc))
        assertEquals("IMG_20261026_073320_123.dng", MediaNaming.fileName(MediaNaming.Kind.RAW, time, utc))
    }

    @Test
    fun `own names are recognised`() {
        assertTrue(MediaNaming.isOwnName("IMG_20261026_073320_123.jpg"))
        assertTrue(MediaNaming.isOwnName("VID_20261026_073320_123.mp4"))
        assertFalse(MediaNaming.isOwnName("IMG_1234.jpg"))
        assertFalse(MediaNaming.isOwnName("CAMERA_ENGINE_20250101_000000.jpg"))
    }

    @Test
    fun `edited copies get a recognisable suffix`() {
        assertEquals("IMG_20261026_073320_123_edit.jpg", MediaNaming.editedFileName(time, utc))
        assertTrue(MediaNaming.isOwnName(MediaNaming.editedFileName(time, utc)))
        assertFalse(MediaNaming.isOwnName("VID_20261026_073320_123_copy.mp4"))
    }

    @Test
    fun `raw photo shares the jpeg base name`() {
        assertEquals(
            MediaNaming.baseName(MediaNaming.Kind.PHOTO, time, utc),
            MediaNaming.baseName(MediaNaming.Kind.RAW, time, utc),
        )
    }
}

class OrientationMathTest {

    @Test
    fun `snap keeps current orientation inside hysteresis band`() {
        assertEquals(0, OrientationMath.snap(50, 0))
        assertEquals(0, OrientationMath.snap(355, 0))
        assertEquals(90, OrientationMath.snap(65, 0))
        assertEquals(90, OrientationMath.snap(40, 90))
        assertEquals(0, OrientationMath.snap(25, 90))
        assertEquals(270, OrientationMath.snap(280, 0))
        assertEquals(180, OrientationMath.snap(181, 90))
    }

    @Test
    fun `unknown angle keeps current`() {
        assertEquals(90, OrientationMath.snap(-1, 90))
    }

    @Test
    fun `surface rotation follows camerax mapping`() {
        assertEquals(0, OrientationMath.surfaceRotation(0))
        assertEquals(3, OrientationMath.surfaceRotation(90))
        assertEquals(2, OrientationMath.surfaceRotation(180))
        assertEquals(1, OrientationMath.surfaceRotation(270))
    }

    @Test
    fun `icon rotation takes the shortest path`() {
        assertEquals(-90f, OrientationMath.iconRotation(90, 0f), 0f)
        assertEquals(90f, OrientationMath.iconRotation(270, 0f), 0f)
        // From -90 going to 270-degree orientation: +90 would be a 180 spin, -270 equivalent avoided.
        assertEquals(-270f, OrientationMath.iconRotation(270, -180f), 0f)
    }

    @Test
    fun `roll and flat detection`() {
        assertEquals(0f, OrientationMath.rollDegrees(0f, 9.8f), 1e-4f)
        assertEquals(90f, OrientationMath.rollDegrees(9.8f, 0f), 1e-4f)
        assertTrue(OrientationMath.isFlat(0.1f, 0.2f, 9.8f))
        assertFalse(OrientationMath.isFlat(0f, 9.8f, 1f))
        assertTrue(OrientationMath.isFlat(0f, 0f, 0f))
    }

    @Test
    fun `tilt folds to nearest horizon`() {
        assertEquals(0f, OrientationMath.tiltFromLevel(0f, 0), 1e-4f)
        assertEquals(5f, OrientationMath.tiltFromLevel(5f, 0), 1e-4f)
        assertEquals(0f, OrientationMath.tiltFromLevel(-90f, 90), 1e-4f)
        assertEquals(-3f, OrientationMath.tiltFromLevel(87f, 0), 1e-4f)
        val t = OrientationMath.tiltFromLevel(170f, 0)
        assertTrue(t > -45f && t <= 45f)
    }
}

class HistogramTest {

    private fun plane(values: IntArray): ByteBuffer =
        ByteBuffer.wrap(ByteArray(values.size) { values[it].toByte() })

    @Test
    fun `counts land in correct bins`() {
        val buf = plane(intArrayOf(0, 3, 4, 255))
        val h = Histogram.fromLuma(buf, width = 4, height = 1, rowStride = 4, binCount = 64, step = 1)
        assertEquals(2, h.bins[0])
        assertEquals(1, h.bins[1])
        assertEquals(1, h.bins[63])
        assertEquals(4L, h.total)
    }

    @Test
    fun `row stride padding is skipped`() {
        // 2x2 image with 2 bytes of padding per row; padding bytes are 200 and must be ignored.
        val buf = plane(intArrayOf(10, 10, 200, 200, 10, 10, 200, 200))
        val h = Histogram.fromLuma(buf, width = 2, height = 2, rowStride = 4, binCount = 256, step = 1)
        assertEquals(4, h.bins[10])
        assertEquals(0, h.bins[200])
    }

    @Test
    fun `sub sampling reduces sample count`() {
        val buf = plane(IntArray(16 * 16) { 128 })
        val h = Histogram.fromLuma(buf, 16, 16, 16, step = 4)
        assertEquals(16L, h.total)
        assertEquals(128f + 2f, h.meanLuma(), 0.01f) // centre of bin 32 with 4-wide bins
    }

    @Test
    fun `clipping fractions and normalisation`() {
        val h = Histogram(intArrayOf(5, 0, 0, 15))
        assertEquals(0.75f, h.highlightClipping(), 1e-6f)
        assertEquals(0.25f, h.shadowClipping(), 1e-6f)
        val n = h.normalized()
        assertEquals(1f, n[3], 0f)
        assertEquals(5f / 15f, n[0], 1e-6f)
        assertEquals(0f, Histogram(IntArray(4)).normalized().sum(), 0f)
        assertEquals(0f, Histogram(IntArray(4)).meanLuma(), 0f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `bin count must divide 256`() {
        Histogram.fromLuma(plane(intArrayOf(1)), 1, 1, 1, binCount = 3)
    }
}

class TimeFormatTest {
    @Test
    fun `recording clock`() {
        assertEquals("00:00", TimeFormat.recording(0))
        assertEquals("00:07", TimeFormat.recording(7_400_000_000L))
        assertEquals("12:34", TimeFormat.recording(754_000_000_000L))
        assertEquals("1:02:03", TimeFormat.recording(3_723_000_000_000L))
    }

    @Test
    fun `video badge and file size`() {
        assertEquals("0:07", TimeFormat.videoBadge(7_999))
        assertEquals("1:00:00", TimeFormat.videoBadge(3_600_000))
        assertEquals("512 B", TimeFormat.fileSize(512))
        assertEquals("2 KB", TimeFormat.fileSize(2048))
        assertEquals("4.2 MB", TimeFormat.fileSize(4_404_019))
    }
}

class GridGeometryTest {
    @Test
    fun `thirds and golden lines`() {
        val thirds = GridGeometry.lines(GridOption.THIRDS, 100f, 100f)
        assertEquals(listOf(1f / 3, 2f / 3), thirds.vertical)
        val golden = GridGeometry.lines(GridOption.GOLDEN, 100f, 100f)
        assertEquals(0.382f, golden.vertical[0], 1e-3f)
        assertEquals(0.618f, golden.vertical[1], 1e-3f)
        assertTrue(GridGeometry.lines(GridOption.OFF, 1f, 1f).vertical.isEmpty())
    }

    @Test
    fun `square grid has square cells`() {
        val w = 400f
        val h = 700f
        val lines = GridGeometry.lines(GridOption.SQUARE, w, h)
        assertEquals(3, lines.vertical.size)
        val ys = lines.horizontal.map { it * h }
        for (i in 1 until ys.size) assertEquals(100f, ys[i] - ys[i - 1], 1e-3f)
        assertTrue(GridGeometry.lines(GridOption.SQUARE, 0f, 10f).vertical.isEmpty())
    }
}
