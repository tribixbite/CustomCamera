package com.customcamera.app.core

import kotlin.math.atan2
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Device-orientation helpers. On phones the viewfinder is locked to portrait
 * (like every major camera app) so the preview never relayouts; instead we
 * track physical orientation to rotate icons and tag captures correctly.
 * Large screens rotate the display, so icons counter-rotate only by the
 * difference between device and display ([uiOrientation]).
 */
object OrientationMath {

    /** Degrees of hysteresis so the UI does not flicker near 45° boundaries. */
    private const val HYSTERESIS = 15

    /**
     * Snaps a raw OrientationEventListener angle (0..359, clockwise) to the
     * nearest of 0/90/180/270, keeping [current] until the angle moves clearly
     * past the boundary.
     *
     * @return the snapped orientation in degrees, or [current] if [degrees] is unknown (-1).
     */
    fun snap(degrees: Int, current: Int): Int {
        if (degrees < 0) return current
        val d = degrees % 360
        val distance = angularDistance(d, current)
        if (distance <= 45 + HYSTERESIS) return current
        return ((d + 45) / 90 % 4) * 90
    }

    /**
     * android.view.Surface rotation constant (0..3) for a snapped device orientation.
     * Device rotated clockwise by 90° means the display's natural "top" points left,
     * which CameraX expresses as ROTATION_270.
     */
    fun surfaceRotation(orientationDegrees: Int): Int = when (orientationDegrees) {
        90 -> 3 // Surface.ROTATION_270
        180 -> 2 // Surface.ROTATION_180
        270 -> 1 // Surface.ROTATION_90
        else -> 0 // Surface.ROTATION_0
    }

    /**
     * Device orientation relative to the current display rotation, i.e. how far
     * the UI must counter-rotate icons. 0 whenever the display follows the device
     * (unlocked large screens); equal to [orientationDegrees] on a portrait-locked
     * phone (display rotation 0).
     *
     * @param orientationDegrees snapped OrientationEventListener angle (clockwise from natural).
     * @param displayRotation android.view.Surface rotation constant (0..3) of the display.
     */
    fun uiOrientation(orientationDegrees: Int, displayRotation: Int): Int =
        ((orientationDegrees + displayRotation * 90) % 360 + 360) % 360

    /**
     * Rotation to apply to on-screen icons so they stay upright. Returns the
     * target angle closest to [currentViewRotation] to avoid 270° spins.
     */
    fun iconRotation(orientationDegrees: Int, currentViewRotation: Float): Float {
        val target = (-orientationDegrees).toFloat()
        var best = target
        var bestDelta = Float.MAX_VALUE
        for (k in -2..2) {
            val candidate = target + 360f * k
            val delta = abs(candidate - currentViewRotation)
            if (delta < bestDelta) {
                bestDelta = delta
                best = candidate
            }
        }
        return best
    }

    /**
     * Roll angle in degrees (-180..180] of the device relative to the horizon,
     * from an accelerometer gravity vector, measured for a phone held upright
     * in portrait. 0 means perfectly level.
     */
    fun rollDegrees(x: Float, y: Float): Float =
        Math.toDegrees(atan2(x.toDouble(), y.toDouble())).toFloat()

    /**
     * True when the phone is lying roughly flat (screen up or down); the level
     * indicator hides in that pose because roll is meaningless.
     */
    fun isFlat(x: Float, y: Float, z: Float): Boolean {
        val g = sqrt(x * x + y * y + z * z)
        if (g < 1e-3f) return true
        return abs(z) / g > 0.9f
    }

    /**
     * Level-indicator tilt for the current snapped orientation: how far the
     * horizon line is from level, normalised to (-45, 45].
     */
    fun tiltFromLevel(rollDegrees: Float, orientationDegrees: Int): Float {
        var t = rollDegrees + orientationDegrees
        while (t > 180f) t -= 360f
        while (t <= -180f) t += 360f
        // Fold to the nearest horizon so landscape holds also read 0 when level.
        while (t > 45f) t -= 90f
        while (t <= -45f) t += 90f
        return t
    }

    private fun angularDistance(a: Int, b: Int): Int {
        val diff = abs(a - b) % 360
        return if (diff > 180) 360 - diff else diff
    }
}
