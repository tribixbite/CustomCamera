package com.customcamera.app.core

import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Lens description helpers for the camera picker.
 */
object LensMath {

    /** Diagonal of a 36×24 mm full-frame sensor. */
    private const val FULL_FRAME_DIAGONAL_MM = 43.2666f

    /**
     * 35 mm-equivalent focal length from the physical focal length and sensor size.
     * Returns null when the sensor size is unknown/invalid.
     */
    fun equivalentFocalLength(focalMm: Float, sensorWidthMm: Float, sensorHeightMm: Float): Int? {
        if (focalMm <= 0f || sensorWidthMm <= 0f || sensorHeightMm <= 0f) return null
        val diagonal = sqrt(sensorWidthMm * sensorWidthMm + sensorHeightMm * sensorHeightMm)
        return (focalMm * FULL_FRAME_DIAGONAL_MM / diagonal).roundToInt()
    }

    /**
     * Coarse lens category from a 35 mm-equivalent focal length.
     */
    fun category(equivalentMm: Int?): LensCategory = when {
        equivalentMm == null -> LensCategory.STANDARD
        equivalentMm < 20 -> LensCategory.ULTRA_WIDE
        equivalentMm < 35 -> LensCategory.WIDE
        equivalentMm < 60 -> LensCategory.STANDARD
        else -> LensCategory.TELEPHOTO
    }

    enum class LensCategory { ULTRA_WIDE, WIDE, STANDARD, TELEPHOTO }
}
