package com.customcamera.app.core

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.InvertedLuminanceSource
import com.google.zxing.LuminanceSource
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.Result
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.multi.GenericMultipleBarcodeReader
import com.google.zxing.multi.qrcode.QRCodeMultiReader
import com.google.zxing.qrcode.detector.FinderPattern
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.min

/** Axis-aligned box in upright image pixels (+y down). */
data class CodeBox(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

/** One code found in a frame: its raw text, ZXing format name and where it is (upright pixels). */
data class DecodedCode(val text: String, val format: String, val box: CodeBox?)

/**
 * Finds QR codes and barcodes in a camera luminance (Y) plane with ZXing
 * (Apache-2.0, pure Java — no network, no native code, no Google services).
 *
 * The plane is turned upright by the frame's rotation first, so 1D barcodes held
 * level by the user read left-to-right and boxes come out in the same upright
 * coordinates that CameraX's `ImageProxyTransformFactory(isUsingRotationDegrees)`
 * maps onto the preview. Not thread-safe: use one instance per analysis thread.
 *
 * Limits compared with the ML Kit scanner this replaced: ZXing's Data Matrix and
 * Aztec detectors search outward from the frame centre, so those codes must cover
 * the middle of the viewfinder (where people aim anyway); QR and 1D codes are
 * found anywhere, several per frame.
 */
class BarcodeDecoder {

    private val qrHints = mapOf(DecodeHintType.TRY_HARDER to true)
    private val otherHints = mapOf(
        DecodeHintType.POSSIBLE_FORMATS to FORMATS - BarcodeFormat.QR_CODE,
        DecodeHintType.TRY_HARDER to true,
    )

    /**
     * QR codes go through QRCodeMultiReader, which looks for every set of finder
     * patterns at once. GenericMultipleBarcodeReader only searches sub-regions
     * after a whole-frame decode succeeds, and with two QR codes in view the
     * single-code detector mixes their finder patterns and finds nothing.
     */
    private val qrReader = QRCodeMultiReader()
    private val otherReader = MultiFormatReader().apply { setHints(otherHints) }
    private val otherMultiReader = GenericMultipleBarcodeReader(otherReader)

    /** Reused upright luminance buffer; reallocated only when the frame size grows. */
    private var upright = ByteArray(0)
    private var frames = 0L

    /**
     * Decodes every code in the frame. [luma] is read from its current position
     * and left unchanged; [rowStride]/[pixelStride] describe its layout and
     * [rotationDegrees] (0/90/180/270, clockwise) turns it upright.
     *
     * White-on-black codes are tried every [INVERTED_EVERY] frames that found
     * nothing, so they still read without halving the normal decode rate.
     */
    fun decode(luma: ByteBuffer, width: Int, height: Int, rowStride: Int, pixelStride: Int, rotationDegrees: Int): List<DecodedCode> {
        val rotation = ((rotationDegrees % 360) + 360) % 360
        require(rotation % 90 == 0) { "rotation must be a multiple of 90: $rotationDegrees" }
        val swap = rotation == 90 || rotation == 270
        val outW = if (swap) height else width
        val outH = if (swap) width else height
        if (upright.size < outW * outH) upright = ByteArray(outW * outH)
        copyUpright(luma, width, height, rowStride, pixelStride, rotation, outW)

        val source = PlanarYUVLuminanceSource(upright, outW, outH, 0, 0, outW, outH, false)
        val found = decodeAll(source).ifEmpty {
            if (frames % INVERTED_EVERY == 0L) decodeAll(InvertedLuminanceSource(source)) else emptyList()
        }
        frames++
        return found.distinctBy { it.text }.map { r ->
            DecodedCode(r.text, r.barcodeFormat.name, boxFor(r, outW, outH))
        }
    }

    private fun decodeAll(source: LuminanceSource): List<Result> {
        val bitmap = BinaryBitmap(HybridBinarizer(source))
        val qr = try {
            qrReader.decodeMultiple(bitmap, qrHints).toList()
        } catch (_: ReaderException) {
            emptyList()
        } finally {
            qrReader.reset()
        }
        val other = try {
            otherMultiReader.decodeMultiple(bitmap, otherHints).toList()
        } catch (_: ReaderException) {
            emptyList()
        } finally {
            otherReader.reset()
        }
        return qr + other
    }

    /** Copies the plane into [upright], rotating clockwise by [rotation]; [outW] is the upright width. */
    private fun copyUpright(luma: ByteBuffer, width: Int, height: Int, rowStride: Int, pixelStride: Int, rotation: Int, outW: Int) {
        val base = luma.position()
        for (y in 0 until height) {
            val row = base + y * rowStride
            for (x in 0 until width) {
                val v = luma.get(row + x * pixelStride)
                // Clockwise: 90 → (h-1-y, x); 180 → (w-1-x, h-1-y); 270 → (y, w-1-x).
                val i = when (rotation) {
                    0 -> y * outW + x
                    90 -> x * outW + (height - 1 - y)
                    180 -> (height - 1 - y) * outW + (width - 1 - x)
                    else -> (width - 1 - x) * outW + y
                }
                upright[i] = v
            }
        }
    }

    companion object {
        /** Formats ML Kit offered before the switch; MaxiCode and RSS are left out (rare, slow). */
        val FORMATS: List<BarcodeFormat> = listOf(
            BarcodeFormat.QR_CODE, BarcodeFormat.DATA_MATRIX, BarcodeFormat.AZTEC, BarcodeFormat.PDF_417,
            BarcodeFormat.EAN_13, BarcodeFormat.EAN_8, BarcodeFormat.UPC_A, BarcodeFormat.UPC_E,
            BarcodeFormat.CODE_128, BarcodeFormat.CODE_39, BarcodeFormat.CODE_93, BarcodeFormat.CODABAR,
            BarcodeFormat.ITF,
        )

        const val INVERTED_EVERY = 3L

        /**
         * QR finder-pattern centres sit 3.5 modules inside the symbol's corners;
         * pad by this many modules so the box covers the whole code.
         */
        private const val QR_FINDER_PAD_MODULES = 3.5f

        /** QR padding relative to the finder-centre span when the module size is unknown. */
        private const val QR_FALLBACK_PAD_RATIO = 0.18f

        /** 1D results are two points on one scan line; give the box this height relative to its width. */
        private const val LINEAR_HEIGHT_RATIO = 0.4f

        /**
         * Box around a result's points, widened to cover the whole symbol:
         * QR points are finder centres (padded by module size), 1D points lie on
         * one scan line (given a height), other 2D formats report their corners.
         */
        internal fun boxFor(result: Result, imageW: Int, imageH: Int): CodeBox? {
            val pts = result.resultPoints?.filterNotNull().orEmpty()
            if (pts.isEmpty()) return null
            var l = pts.minOf { it.x }
            var r = pts.maxOf { it.x }
            var t = pts.minOf { it.y }
            var b = pts.maxOf { it.y }
            val module = pts.filterIsInstance<FinderPattern>().map { it.estimatedModuleSize }.average().toFloat()
            if (!module.isNaN() && module > 0f) {
                val pad = module * QR_FINDER_PAD_MODULES
                l -= pad; r += pad; t -= pad; b += pad
            } else if (result.barcodeFormat == BarcodeFormat.QR_CODE) {
                // Codes found in a sub-region by GenericMultipleBarcodeReader come back as
                // plain ResultPoints (no module size). Finder centres span (N - 7) of N
                // modules; for common versions (21–41 modules) 3.5 modules ≈ 15–25 % of that.
                val pad = max(r - l, b - t) * QR_FALLBACK_PAD_RATIO
                l -= pad; r += pad; t -= pad; b += pad
            } else if (b - t < (r - l) * 0.1f) {
                val half = (r - l) * LINEAR_HEIGHT_RATIO / 2f
                val cy = (t + b) / 2f
                t = cy - half; b = cy + half
            }
            return CodeBox(
                max(0f, l), max(0f, t),
                min(imageW.toFloat(), r), min(imageH.toFloat(), b),
            )
        }
    }
}
