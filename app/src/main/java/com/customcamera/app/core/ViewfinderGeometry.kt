package com.customcamera.app.core

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Pixel rectangle relative to the viewfinder window. */
data class PixelBox(val left: Int, val top: Int, val width: Int, val height: Int)

/**
 * Where the preview goes in the viewfinder window.
 *
 * Portrait windows (phones, tablets held upright) show the preview full-width
 * under the top bar, as before. Landscape windows (tablets and foldables on
 * Android 16+, which ignore the portrait lock on large screens, and landscape
 * multi-window) reserve a shutter rail on the end side and centre the preview
 * in the remaining area.
 */
object ViewfinderGeometry {

    /** A window is laid out in landscape when it is wider than it is tall. */
    fun isLandscape(width: Int, height: Int): Boolean = width > height

    /**
     * @param windowWidth viewfinder width in px.
     * @param windowHeight viewfinder height in px.
     * @param longOverShort capture aspect as long side / short side (4:3 → 1.333, 1:1 → 1).
     * @param topBarBottom bottom edge of the top bar in px (portrait only: the preview sits under it when it fits).
     * @param railWidth width in px reserved for the shutter rail, including any system-bar inset on that side (landscape only).
     * @param railOnLeft true when the rail is on the left (right-to-left layouts).
     */
    fun preview(
        windowWidth: Int,
        windowHeight: Int,
        longOverShort: Float,
        topBarBottom: Int,
        railWidth: Int,
        railOnLeft: Boolean,
    ): PixelBox {
        require(windowWidth > 0 && windowHeight > 0) { "empty window ${windowWidth}x$windowHeight" }
        require(longOverShort >= 1f) { "longOverShort must be ≥ 1: $longOverShort" }
        if (!isLandscape(windowWidth, windowHeight)) {
            val height = min((windowWidth * longOverShort).roundToInt(), windowHeight)
            // Under the top bar when there is room, otherwise centred (tall aspect on a short window).
            val top = if (topBarBottom + height <= windowHeight) topBarBottom else max(0, (windowHeight - height) / 2)
            return PixelBox(0, top, windowWidth, height)
        }
        val areaWidth = max(1, windowWidth - railWidth)
        // Landscape window: the long side of the frame runs horizontally.
        val width = min(areaWidth, (windowHeight * longOverShort).roundToInt())
        val height = min(windowHeight, (width / longOverShort).roundToInt())
        val areaLeft = if (railOnLeft) windowWidth - areaWidth else 0
        return PixelBox(areaLeft + (areaWidth - width) / 2, (windowHeight - height) / 2, width, height)
    }
}
