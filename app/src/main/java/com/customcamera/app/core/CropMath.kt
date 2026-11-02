package com.customcamera.app.core

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Float rectangle in an arbitrary uniformly-scaled space (view pixels, or
 * fractions 0..1 of the displayed image). Android's RectF is deliberately not
 * used so the crop geometry stays JVM-testable.
 */
data class CropRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f

    fun offset(dx: Float, dy: Float) = CropRect(left + dx, top + dy, right + dx, bottom + dy)

    /** This rect (in normalized 0..1 coordinates) after the image is turned 90° clockwise. */
    fun rotatedClockwise() = CropRect(1f - bottom, left, 1f - top, right)

    /** This rect (in normalized 0..1 coordinates) after the image is mirrored left-right. */
    fun flippedHorizontally() = CropRect(1f - right, top, 1f - left, bottom)

    companion object {
        /** The whole image in normalized coordinates. */
        val FULL = CropRect(0f, 0f, 1f, 1f)
    }
}

/** Integer pixel rectangle; [right] and [bottom] are exclusive. */
data class PixelRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

/**
 * Non-destructive edit transform from the (EXIF-oriented) source image to what the
 * user sees: first rotate clockwise by [rotation] degrees, then mirror left-right
 * when [flipped]. Every combination of quarter turns and mirrors reduces to this form.
 */
data class EditTransform(val rotation: Int = 0, val flipped: Boolean = false) {

    init {
        require(rotation in VALID_ROTATIONS) { "rotation must be one of $VALID_ROTATIONS, was $rotation" }
    }

    /** True when the displayed image's width/height are swapped relative to the source. */
    val swapsAxes: Boolean get() = rotation % 180 != 0

    val isIdentity: Boolean get() = rotation == 0 && !flipped

    /**
     * Turns the *displayed* image 90° clockwise. With a mirror already applied,
     * rotate(90)∘mirror = mirror∘rotate(-90), so the stored rotation goes the other way.
     */
    fun rotatedClockwise(): EditTransform =
        copy(rotation = normalize(rotation + if (flipped) -90 else 90))

    /** Mirrors the *displayed* image left-right (mirroring twice cancels out). */
    fun flippedHorizontally(): EditTransform = copy(flipped = !flipped)

    /** Display dimensions for a source of [sourceWidth]×[sourceHeight]. */
    fun displaySize(sourceWidth: Int, sourceHeight: Int): Pair<Int, Int> =
        if (swapsAxes) sourceHeight to sourceWidth else sourceWidth to sourceHeight

    companion object {
        val VALID_ROTATIONS = setOf(0, 90, 180, 270)
        private fun normalize(degrees: Int) = ((degrees % 360) + 360) % 360
    }
}

/**
 * A complete non-destructive edit: [transform] first, then [crop] (fractions of the
 * transformed image). [aspect] is kept so the saved pixels honour the ratio exactly.
 */
data class PhotoEdit(
    val transform: EditTransform = EditTransform(),
    val crop: CropRect = CropRect.FULL,
    val aspect: CropAspect = CropAspect.FREE,
) {
    /** True when saving would only re-encode the original unchanged. */
    val isNoOp: Boolean
        get() = transform.isIdentity &&
            crop.left <= NO_OP_EPSILON && crop.top <= NO_OP_EPSILON &&
            crop.right >= 1f - NO_OP_EPSILON && crop.bottom >= 1f - NO_OP_EPSILON

    private companion object {
        /** Sub-pixel tolerance for "uncropped" on any realistic image size. */
        const val NO_OP_EPSILON = 1e-4f
    }
}

/** Crop aspect presets. [w]:[h] is the ratio; FREE has none and ORIGINAL follows the image. */
enum class CropAspect(val key: String, private val w: Int, private val h: Int) {
    FREE("free", 0, 0),
    ORIGINAL("original", 0, 0),
    SQUARE("1:1", 1, 1),
    RATIO_4_3("4:3", 4, 3),
    RATIO_3_4("3:4", 3, 4),
    RATIO_16_9("16:9", 16, 9),
    RATIO_9_16("9:16", 9, 16);

    /** Width ÷ height to enforce for an image displayed at [imageWidth]×[imageHeight], or null when free. */
    fun ratio(imageWidth: Float, imageHeight: Float): Float? = when (this) {
        FREE -> null
        ORIGINAL -> if (imageWidth > 0f && imageHeight > 0f) imageWidth / imageHeight else null
        else -> w.toFloat() / h
    }

    /** The preset that matches this one after the image turns 90° (portrait ↔ landscape). */
    fun rotated(): CropAspect = when (this) {
        RATIO_4_3 -> RATIO_3_4
        RATIO_3_4 -> RATIO_4_3
        RATIO_16_9 -> RATIO_9_16
        RATIO_9_16 -> RATIO_16_9
        else -> this
    }

    companion object {
        fun fromKey(key: String?): CropAspect = entries.firstOrNull { it.key == key } ?: FREE
    }
}

/** Which part of the crop rectangle a drag grabbed. */
enum class CropHandle(val moveLeft: Boolean, val moveTop: Boolean, val moveRight: Boolean, val moveBottom: Boolean) {
    TOP_LEFT(true, true, false, false),
    TOP_RIGHT(false, true, true, false),
    BOTTOM_LEFT(true, false, false, true),
    BOTTOM_RIGHT(false, false, true, true),
    LEFT(true, false, false, false),
    TOP(false, true, false, false),
    RIGHT(false, false, true, false),
    BOTTOM(false, false, false, true),

    /** Inside the rectangle: translate without resizing. */
    MOVE(false, false, false, false);

    val isCorner: Boolean get() = (moveLeft || moveRight) && (moveTop || moveBottom)
}

/**
 * Crop-rectangle geometry: hit testing, aspect-locked resizing, clamping and the
 * mapping from the on-screen crop to source-image pixels.
 *
 * All drag math works in one uniformly-scaled space (view pixels) so aspect ratios
 * hold there; callers convert to normalized coordinates for storage.
 */
object CropMath {

    /**
     * Finds the handle under ([x], [y]). Corners win over edges, edges over the
     * interior. [touchRadius] is half the touch-target size (24dp for 48dp targets).
     * Edges are grabbable along their whole length, not only at the midpoint marker.
     */
    fun hitTest(rect: CropRect, x: Float, y: Float, touchRadius: Float): CropHandle? {
        val nearLeft = abs(x - rect.left) <= touchRadius
        val nearRight = abs(x - rect.right) <= touchRadius
        val nearTop = abs(y - rect.top) <= touchRadius
        val nearBottom = abs(y - rect.bottom) <= touchRadius
        val withinX = x >= rect.left - touchRadius && x <= rect.right + touchRadius
        val withinY = y >= rect.top - touchRadius && y <= rect.bottom + touchRadius
        // On a very small rect both opposite edges can be "near": prefer the closer one.
        val left = nearLeft && (!nearRight || abs(x - rect.left) <= abs(x - rect.right))
        val right = nearRight && !left
        val top = nearTop && (!nearBottom || abs(y - rect.top) <= abs(y - rect.bottom))
        val bottom = nearBottom && !top
        return when {
            left && top -> CropHandle.TOP_LEFT
            right && top -> CropHandle.TOP_RIGHT
            left && bottom -> CropHandle.BOTTOM_LEFT
            right && bottom -> CropHandle.BOTTOM_RIGHT
            left && withinY -> CropHandle.LEFT
            right && withinY -> CropHandle.RIGHT
            top && withinX -> CropHandle.TOP
            bottom && withinX -> CropHandle.BOTTOM
            x > rect.left && x < rect.right && y > rect.top && y < rect.bottom -> CropHandle.MOVE
            else -> null
        }
    }

    /**
     * Applies a drag of ([dx], [dy]) — measured from the gesture start — to [start].
     *
     * The result always lies inside [bounds], is at least [minSize] on each side
     * (or the bounds, if smaller), and when [ratio] (width ÷ height) is non-null keeps
     * that ratio exactly. Working from the gesture start rather than per-event deltas
     * means clamping never accumulates drift.
     */
    fun drag(
        start: CropRect,
        handle: CropHandle,
        dx: Float,
        dy: Float,
        bounds: CropRect,
        minSize: Float,
        ratio: Float?,
    ): CropRect = when {
        handle == CropHandle.MOVE -> clampInside(start.offset(dx, dy), bounds)
        ratio == null -> freeResize(start, handle, dx, dy, bounds, minSize)
        handle.isCorner -> lockedCornerResize(start, handle, dx, dy, bounds, minSize, ratio)
        else -> lockedEdgeResize(start, handle, dx, dy, bounds, minSize, ratio)
    }

    /** Translates [rect] (without resizing, unless larger than [bounds]) so it lies inside [bounds]. */
    fun clampInside(rect: CropRect, bounds: CropRect): CropRect {
        val w = min(rect.width, bounds.width)
        val h = min(rect.height, bounds.height)
        val left = rect.left.coerceIn(bounds.left, bounds.right - w)
        val top = rect.top.coerceIn(bounds.top, bounds.bottom - h)
        return CropRect(left, top, left + w, top + h)
    }

    /**
     * Largest rectangle of [ratio] that fits in [bounds], centred as close to
     * ([centerX], [centerY]) as the bounds allow. Null [ratio] returns [bounds].
     */
    fun fit(bounds: CropRect, ratio: Float?, centerX: Float = bounds.centerX, centerY: Float = bounds.centerY): CropRect {
        if (ratio == null || ratio <= 0f) return bounds
        var w = bounds.width
        var h = w / ratio
        if (h > bounds.height) {
            h = bounds.height
            w = h * ratio
        }
        return clampInside(CropRect(centerX - w / 2f, centerY - h / 2f, centerX + w / 2f, centerY + h / 2f), bounds)
    }

    /** Converts [rect] in the space of [frame] into fractions 0..1 of the frame. */
    fun normalize(rect: CropRect, frame: CropRect): CropRect = CropRect(
        (rect.left - frame.left) / frame.width,
        (rect.top - frame.top) / frame.height,
        (rect.right - frame.left) / frame.width,
        (rect.bottom - frame.top) / frame.height,
    )

    /** Inverse of [normalize]: places normalized [rect] onto [frame]. */
    fun denormalize(rect: CropRect, frame: CropRect): CropRect = CropRect(
        frame.left + rect.left * frame.width,
        frame.top + rect.top * frame.height,
        frame.left + rect.right * frame.width,
        frame.top + rect.bottom * frame.height,
    )

    /**
     * Maps a crop given as fractions of the *displayed* image to a pixel rect in the
     * *source* bitmap ([sourceWidth]×[sourceHeight]) under [transform].
     *
     * The rect is first snapped to whole pixels in display orientation — when [ratio]
     * is set the height is derived from the width so e.g. a 1:1 crop is exactly square —
     * then mapped back through the inverse rotation/mirror, which is exact on integers.
     * Cropping the source with that rect and then applying [transform] yields the edit.
     */
    fun toSourcePixels(
        normalized: CropRect,
        transform: EditTransform,
        sourceWidth: Int,
        sourceHeight: Int,
        ratio: Float? = null,
    ): PixelRect {
        require(sourceWidth > 0 && sourceHeight > 0) { "empty source ${sourceWidth}x$sourceHeight" }
        val (dw, dh) = transform.displaySize(sourceWidth, sourceHeight)
        val display = snapToPixels(normalized, dw, dh, ratio)

        // Undo the mirror (it was applied last, in display space).
        val l = if (transform.flipped) dw - display.right else display.left
        val r = if (transform.flipped) dw - display.left else display.right
        val t = display.top
        val b = display.bottom

        // Undo the clockwise rotation. Derivation for 90°: a source point (x, y) lands at
        // display (srcH - y, x), so display (u, v) came from source (v, dw - u).
        return when (transform.rotation) {
            0 -> PixelRect(l, t, r, b)
            90 -> PixelRect(t, dw - r, b, dw - l)
            180 -> PixelRect(dw - r, dh - b, dw - l, dh - t)
            270 -> PixelRect(dh - b, l, dh - t, r)
            else -> error("unreachable: rotation validated by EditTransform")
        }
    }

    /** Snaps a normalized rect to whole pixels inside a [dw]×[dh] image, at least 1 px each way. */
    private fun snapToPixels(n: CropRect, dw: Int, dh: Int, ratio: Float?): PixelRect {
        val left = (n.left * dw).roundToInt().coerceIn(0, dw - 1)
        val top = (n.top * dh).roundToInt().coerceIn(0, dh - 1)
        var width = (n.width * dw).roundToInt().coerceIn(1, dw - left)
        var height = (n.height * dh).roundToInt().coerceIn(1, dh - top)
        if (ratio != null && ratio > 0f) {
            // Derive one side from the other; shrink the width if the derived height won't fit.
            height = (width / ratio).roundToInt().coerceAtLeast(1)
            if (height > dh - top) {
                height = dh - top
                width = (height * ratio).roundToInt().coerceIn(1, dw - left)
            }
        }
        return PixelRect(left, top, left + width, top + height)
    }

    // --- resize helpers -------------------------------------------------------------

    private fun freeResize(start: CropRect, handle: CropHandle, dx: Float, dy: Float, bounds: CropRect, minSize: Float): CropRect {
        val minW = min(minSize, bounds.width)
        val minH = min(minSize, bounds.height)
        var (left, top, right, bottom) = start
        if (handle.moveLeft) left = (left + dx).coerceIn(bounds.left, right - minW)
        if (handle.moveRight) right = (right + dx).coerceIn(left + minW, bounds.right)
        if (handle.moveTop) top = (top + dy).coerceIn(bounds.top, bottom - minH)
        if (handle.moveBottom) bottom = (bottom + dy).coerceIn(top + minH, bounds.bottom)
        return CropRect(left, top, right, bottom)
    }

    /**
     * Corner drag with a locked ratio: the opposite corner stays fixed and the new
     * width is the average of what the horizontal and vertical finger motion each ask
     * for, so diagonal and single-axis drags both feel continuous.
     */
    private fun lockedCornerResize(
        start: CropRect, handle: CropHandle, dx: Float, dy: Float, bounds: CropRect, minSize: Float, ratio: Float,
    ): CropRect {
        val anchorX = if (handle.moveLeft) start.right else start.left
        val anchorY = if (handle.moveTop) start.bottom else start.top
        val wantW = start.width + if (handle.moveLeft) -dx else dx
        val wantH = start.height + if (handle.moveTop) -dy else dy
        val maxW = min(
            if (handle.moveLeft) anchorX - bounds.left else bounds.right - anchorX,
            (if (handle.moveTop) anchorY - bounds.top else bounds.bottom - anchorY) * ratio,
        )
        val w = clampWidth((wantW + wantH * ratio) / 2f, minSize, ratio, maxW)
        val h = w / ratio
        val left = if (handle.moveLeft) anchorX - w else anchorX
        val top = if (handle.moveTop) anchorY - h else anchorY
        return CropRect(left, top, left + w, top + h)
    }

    /**
     * Edge drag with a locked ratio: the opposite edge stays fixed, the perpendicular
     * size follows the ratio and stays centred on the original centre line, shifted
     * back inside the bounds where needed.
     */
    private fun lockedEdgeResize(
        start: CropRect, handle: CropHandle, dx: Float, dy: Float, bounds: CropRect, minSize: Float, ratio: Float,
    ): CropRect = if (handle.moveLeft || handle.moveRight) {
        val anchorX = if (handle.moveLeft) start.right else start.left
        val wantW = start.width + if (handle.moveLeft) -dx else dx
        val maxW = min(if (handle.moveLeft) anchorX - bounds.left else bounds.right - anchorX, bounds.height * ratio)
        val w = clampWidth(wantW, minSize, ratio, maxW)
        val h = w / ratio
        val left = if (handle.moveLeft) anchorX - w else anchorX
        val top = (start.centerY - h / 2f).coerceIn(bounds.top, bounds.bottom - h)
        CropRect(left, top, left + w, top + h)
    } else {
        val anchorY = if (handle.moveTop) start.bottom else start.top
        val wantH = start.height + if (handle.moveTop) -dy else dy
        val maxW = min(bounds.width, (if (handle.moveTop) anchorY - bounds.top else bounds.bottom - anchorY) * ratio)
        val w = clampWidth(wantH * ratio, minSize, ratio, maxW)
        val h = w / ratio
        val top = if (handle.moveTop) anchorY - h else anchorY
        val left = (start.centerX - w / 2f).coerceIn(bounds.left, bounds.right - w)
        CropRect(left, top, left + w, top + h)
    }

    /** Width that keeps both sides ≥ [minSize] (when room allows) and ≤ [maxW]. */
    private fun clampWidth(want: Float, minSize: Float, ratio: Float, maxW: Float): Float {
        val minW = max(minSize, minSize * ratio)
        return if (maxW <= minW) maxW else want.coerceIn(minW, maxW)
    }
}
