package com.customcamera.app.core

/**
 * Placement of the picture-in-picture inset in DUAL mode.
 *
 * CameraX CompositionSettings use normalised device coordinates: the output
 * spans -1..1 on both axes with the origin in the centre and +y pointing up,
 * and `offset` moves the centre of a stream scaled by `scale`. Views use 0..1
 * with +y pointing down; [insetRect] converts so taps can be hit-tested.
 * The composited frame is display-oriented: on a Pixel 8 Pro forced to
 * landscape (ROTATION_90) a top-left request appeared top-left on screen.
 */
enum class PipCorner(val key: String) {
    // Top-right first: on most phones the zoom chips and mode tabs cover the bottom of the frame.
    TOP_RIGHT("tr"), BOTTOM_RIGHT("br"), BOTTOM_LEFT("bl"), TOP_LEFT("tl");

    /** Clockwise order, so repeated taps walk the inset around the frame. */
    fun next(): PipCorner = entries[(ordinal + 1) % entries.size]

    companion object {
        val DEFAULT = TOP_RIGHT

        fun fromKey(key: String?): PipCorner = entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}

/** Composition parameters for one stream, in CameraX NDC units. */
data class Composition(val offsetX: Float, val offsetY: Float, val scale: Float)

/** Axis-aligned rectangle in normalised view coordinates (0..1, +y down). */
data class NormRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    fun contains(x: Float, y: Float): Boolean = x in left..right && y in top..bottom
}

object PipLayout {
    /** Inset size as a fraction of the frame (equal on both axes to avoid stretching). */
    const val DEFAULT_SCALE = 0.32f

    /** Gap between the inset and the frame edge, in NDC units (2.0 = full frame). */
    const val DEFAULT_MARGIN = 0.06f

    /** The main stream fills the frame. */
    val FULL = Composition(0f, 0f, 1f)

    fun inset(corner: PipCorner, scale: Float = DEFAULT_SCALE, margin: Float = DEFAULT_MARGIN): Composition {
        require(scale > 0f && scale < 1f) { "scale must be in (0, 1)" }
        // A stream scaled by s spans s units either side of its centre in NDC.
        val reach = 1f - scale - margin
        val x = if (corner == PipCorner.BOTTOM_RIGHT || corner == PipCorner.TOP_RIGHT) reach else -reach
        val y = if (corner == PipCorner.TOP_LEFT || corner == PipCorner.TOP_RIGHT) reach else -reach
        return Composition(x, y, scale)
    }

    /** Where [composition] lands in a view showing the whole composited frame. */
    fun insetRect(composition: Composition): NormRect {
        val cx = (composition.offsetX + 1f) / 2f
        val cy = (1f - composition.offsetY) / 2f
        val half = composition.scale / 2f
        return NormRect(cx - half, cy - half, cx + half, cy + half)
    }
}
