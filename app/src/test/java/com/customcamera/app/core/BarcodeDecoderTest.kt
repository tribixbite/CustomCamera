package com.customcamera.app.core

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import kotlin.math.abs

/**
 * Feeds [BarcodeDecoder] synthetic camera planes: codes drawn with ZXing's own
 * encoders onto a grey frame, then laid out as a sensor would deliver them
 * (rotated, with row padding) so the decoder has to turn them upright.
 */
class BarcodeDecoderTest {

    /** An upright greyscale image under construction. */
    private class Canvas(val w: Int, val h: Int) {
        val px = ByteArray(w * h) { GREY }

        /** Draws [m] with a [quiet]-pixel white margin, as printed codes have. */
        fun draw(m: BitMatrix, left: Int, top: Int, quiet: Int = 24) {
            for (y in top - quiet until top + m.height + quiet) for (x in left - quiet until left + m.width + quiet) {
                px[y * w + x] = WHITE
            }
            for (y in 0 until m.height) for (x in 0 until m.width) {
                px[(top + y) * w + left + x] = if (m[x, y]) BLACK else WHITE
            }
        }

        fun invert() { for (i in px.indices) px[i] = (255 - (px[i].toInt() and 0xFF)).toByte() }
    }

    /**
     * The sensor-orientation plane that [rotationDegrees] turns back into [c],
     * i.e. [c] rotated counter-clockwise, with [padding] bytes after each row.
     */
    private fun sensorPlane(c: Canvas, rotationDegrees: Int, padding: Int = 0): Frame {
        val swap = rotationDegrees == 90 || rotationDegrees == 270
        val w = if (swap) c.h else c.w
        val h = if (swap) c.w else c.h
        val stride = w + padding
        val out = ByteArray(stride * h)
        for (y in 0 until h) for (x in 0 until w) {
            // Inverse of the decoder's clockwise mapping.
            val (ux, uy) = when (rotationDegrees) {
                0 -> x to y
                90 -> (h - 1 - y) to x
                180 -> (w - 1 - x) to (h - 1 - y)
                else -> y to (w - 1 - x)
            }
            out[y * stride + x] = c.px[uy * c.w + ux]
        }
        return Frame(ByteBuffer.wrap(out), w, h, stride, rotationDegrees)
    }

    private data class Frame(val buf: ByteBuffer, val w: Int, val h: Int, val stride: Int, val rotation: Int)

    private fun BarcodeDecoder.decode(f: Frame) = decode(f.buf, f.w, f.h, f.stride, 1, f.rotation)

    private fun encode(text: String, format: BarcodeFormat, w: Int, h: Int): BitMatrix =
        MultiFormatWriter().encode(text, format, w, h, mapOf(EncodeHintType.MARGIN to 0))

    /** Asserts [box] is within [tol] pixels of the drawn symbol at ([l], [t], [w]x[h]). */
    private fun assertBox(box: CodeBox?, l: Int, t: Int, w: Int, h: Int, tol: Float) {
        assertNotNull("no box", box)
        val msg = "box $box vs drawn [$l,$t ${w}x$h]"
        assertTrue(msg, abs(box!!.left - l) <= tol)
        assertTrue(msg, abs(box.top - t) <= tol)
        assertTrue(msg, abs(box.right - (l + w)) <= tol)
        assertTrue(msg, abs(box.bottom - (t + h)) <= tol)
    }

    @Test
    fun qrUprightIsDecodedAndBoxed() {
        val c = Canvas(640, 480)
        val qr = encode("https://example.com/a", BarcodeFormat.QR_CODE, 210, 210)
        c.draw(qr, 200, 120)
        val found = BarcodeDecoder().decode(sensorPlane(c, 0))
        assertEquals(listOf("https://example.com/a"), found.map { it.text })
        assertEquals("QR_CODE", found[0].format)
        // 210 px / 21 modules = 10 px per module; box within one module of the symbol.
        assertBox(found[0].box, 200, 120, qr.width, qr.height, tol = 10f)
    }

    @Test
    fun everySensorRotationComesBackUpright() {
        for (rotation in listOf(0, 90, 180, 270)) {
            val c = Canvas(480, 640)
            val qr = encode("rotation $rotation", BarcodeFormat.QR_CODE, 210, 210)
            c.draw(qr, 60, 300)
            val found = BarcodeDecoder().decode(sensorPlane(c, rotation, padding = 32))
            assertEquals("rotation $rotation", listOf("rotation $rotation"), found.map { it.text })
            assertBox(found[0].box, 60, 300, qr.width, qr.height, tol = 10f)
        }
    }

    @Test
    fun levelEan13ReadsInAPortraitSensorFrame() {
        // Phone upright: the sensor delivers landscape planes with rotation 90, so a
        // barcode level in the scene is vertical in the raw plane.
        val c = Canvas(720, 1280)
        val ean = encode("5901234123457", BarcodeFormat.EAN_13, 380, 160)
        c.draw(ean, 170, 560)
        val found = BarcodeDecoder().decode(sensorPlane(c, 90))
        assertEquals(listOf("5901234123457"), found.map { it.text })
        assertEquals("EAN_13", found[0].format)
        val box = found[0].box!!
        assertTrue("box $box should span the bars", box.left < 200 && box.right > 520)
        assertTrue("box $box should give a 1D code some height", box.height > 50f)
        assertTrue("box $box should be centred on the bars", box.top < 640 && box.bottom > 640)
    }

    @Test
    fun twoCodesInOneFrameAreBothReported() {
        val c = Canvas(1280, 720)
        c.draw(encode("left", BarcodeFormat.QR_CODE, 252, 252), 80, 200)
        c.draw(encode("right", BarcodeFormat.QR_CODE, 252, 252), 900, 300)
        val found = BarcodeDecoder().decode(sensorPlane(c, 0))
        assertEquals(setOf("left", "right"), found.map { it.text }.toSet())
        // Each box must sit over its own code.
        val left = found.first { it.text == "left" }.box!!
        val right = found.first { it.text == "right" }.box!!
        assertTrue("left box $left", left.left < 120 && left.right in 300f..360f)
        assertTrue("right box $right", right.left in 860f..940f && right.right > 1120)
    }

    @Test
    fun otherFormatsDecode() {
        val cases = listOf(
            Triple(BarcodeFormat.DATA_MATRIX, "DM-12345", 200 to 200),
            Triple(BarcodeFormat.CODE_128, "CODE128-abc", 400 to 120),
            Triple(BarcodeFormat.AZTEC, "aztec text", 200 to 200),
            Triple(BarcodeFormat.PDF_417, "pdf417 payload", 420 to 140),
        )
        for ((format, text, size) in cases) {
            val c = Canvas(640, 480)
            // Centred: ZXing's Data Matrix and Aztec detectors start from the frame centre.
            c.draw(encode(text, format, size.first, size.second), (640 - size.first) / 2, (480 - size.second) / 2)
            val found = BarcodeDecoder().decode(sensorPlane(c, 0))
            assertEquals("$format", listOf(text), found.map { it.text })
            assertEquals(format.name, found[0].format)
        }
    }

    @Test
    fun whiteOnBlackCodeIsFoundOnAnInversionFrame() {
        val c = Canvas(640, 480)
        c.draw(encode("inverted", BarcodeFormat.QR_CODE, 210, 210), 200, 120)
        c.invert()
        val decoder = BarcodeDecoder()
        val frame = sensorPlane(c, 0)
        val results = (0 until BarcodeDecoder.INVERTED_EVERY.toInt()).map { decoder.decode(frame) }
        assertTrue("inverted code never read: $results", results.any { r -> r.map { it.text } == listOf("inverted") })
    }

    @Test
    fun emptySceneFindsNothingAndLeavesTheBufferPosition() {
        val c = Canvas(640, 480)
        val frame = sensorPlane(c, 90)
        frame.buf.position(0)
        assertTrue(BarcodeDecoder().decode(frame).isEmpty())
        assertEquals(0, frame.buf.position())
    }

    private companion object {
        val GREY: Byte = 160.toByte()
        val BLACK: Byte = 20
        val WHITE: Byte = 240.toByte()
    }
}
