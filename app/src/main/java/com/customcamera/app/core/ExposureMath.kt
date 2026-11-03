package com.customcamera.app.core

import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Pure helpers for manual-exposure UI: formatting, stop ladders and slider mapping.
 */
object ExposureMath {

    private const val NANOS_PER_SECOND = 1_000_000_000L

    /** Standard full/third-stop shutter denominators photographers recognise. */
    private val SHUTTER_DENOMINATORS = longArrayOf(
        8000, 6400, 5000, 4000, 3200, 2500, 2000, 1600, 1250, 1000, 800, 640, 500, 400, 320,
        250, 200, 160, 125, 100, 80, 60, 50, 40, 30, 25, 20, 15, 13, 10, 8, 6, 5, 4, 3,
    )

    /** Whole and fractional second exposures beyond 1/3 s. */
    private val LONG_SHUTTER_SECONDS = doubleArrayOf(0.4, 0.5, 0.6, 0.8, 1.0, 1.3, 1.6, 2.0, 2.5, 3.2, 4.0, 5.0, 6.0, 8.0, 10.0, 15.0, 20.0, 30.0)

    /** Standard third-stop ISO values. */
    private val ISO_STOPS = intArrayOf(
        25, 32, 40, 50, 64, 80, 100, 125, 160, 200, 250, 320, 400, 500, 640, 800, 1000, 1250,
        1600, 2000, 2500, 3200, 4000, 5000, 6400, 8000, 10000, 12800, 16000, 20000, 25600,
    )

    /**
     * Human-readable shutter speed: "1/125" for fast exposures, "0.5\"" / "2\"" for long ones.
     * Fast speeds snap to the standard value photographers know ([displayDenominator]),
     * so live auto readings and sensor limits read "1/125" or "1/12000", not "1/117" or "1/11977".
     */
    fun formatShutter(exposureNanos: Long): String {
        require(exposureNanos > 0) { "exposure must be positive: $exposureNanos" }
        val seconds = exposureNanos.toDouble() / NANOS_PER_SECOND
        return if (seconds < 0.35) {
            "1/${displayDenominator(1.0 / seconds)}"
        } else {
            val rounded = (seconds * 10).roundToInt() / 10.0
            if (rounded == rounded.toLong().toDouble()) "${rounded.toLong()}\"" else "$rounded\""
        }
    }

    /**
     * Denominator to show for a 1/[raw] s exposure: the nearest standard third-stop
     * value when within a sixth of a stop of it, otherwise [raw] rounded to two
     * significant figures (speeds beyond 1/8000 or between ladder steps).
     */
    internal fun displayDenominator(raw: Double): Long {
        require(raw > 0) { "denominator must be positive: $raw" }
        val nearest = SHUTTER_DENOMINATORS.minBy { abs(ln(it / raw)) }
        if (abs(ln(nearest / raw)) <= SNAP_TOLERANCE) return nearest
        val unit = 10.0.pow(floor(log10(raw)) - 1)
        return maxOf(1L, ((raw / unit).roundToLong() * unit).roundToLong())
    }

    /** A sixth of a stop in natural-log units: half the gap between third-stop values. */
    private val SNAP_TOLERANCE = ln(2.0) / 6

    /**
     * Shutter speeds (nanoseconds, fastest first) that fall inside the sensor's
     * supported exposure range. Range bounds are always included so the slider
     * reaches the hardware limits.
     */
    fun shutterStops(minNanos: Long, maxNanos: Long): List<Long> {
        require(minNanos in 1..maxNanos) { "invalid exposure range [$minNanos, $maxNanos]" }
        val ladder = SHUTTER_DENOMINATORS.map { NANOS_PER_SECOND / it } +
            LONG_SHUTTER_SECONDS.map { (it * NANOS_PER_SECOND).roundToLong() }
        val inRange = ladder.filter { it in minNanos..maxNanos }
        return (listOf(minNanos) + inRange + listOf(maxNanos)).distinct().sorted()
    }

    /** ISO values within the sensor's sensitivity range, bounds included. */
    fun isoStops(minIso: Int, maxIso: Int): List<Int> {
        require(minIso in 1..maxIso) { "invalid ISO range [$minIso, $maxIso]" }
        val inRange = ISO_STOPS.filter { it in minIso..maxIso }
        return (listOf(minIso) + inRange + listOf(maxIso)).distinct().sorted()
    }

    /** Index of the stop closest to [value], used to seed sliders from live auto values. */
    fun nearestIndex(stops: List<Long>, value: Long): Int {
        require(stops.isNotEmpty()) { "stops must not be empty" }
        return stops.indices.minBy { abs(stops[it] - value) }
    }

    fun nearestIsoIndex(stops: List<Int>, value: Int): Int =
        nearestIndex(stops.map { it.toLong() }, value.toLong())

    /**
     * Exposure-compensation label, e.g. "+0.7", "-1.3", "0.0".
     * @param index CameraX exposure compensation index.
     * @param step EV per index (CameraX exposureCompensationStep as a float).
     */
    fun formatEv(index: Int, step: Float): String {
        val ev = index * step
        val text = String.format(Locale.US, "%.1f", abs(ev))
        return when {
            ev > 0.0001f -> "+$text"
            ev < -0.0001f -> "-$text"
            else -> "0.0"
        }
    }

    /**
     * Converts a lens focus distance in diopters (1/m) into a short label.
     * 0 diopters is infinity by Camera2 convention.
     */
    fun formatFocusDistance(diopters: Float): String = when {
        diopters <= 0.001f -> "∞"
        else -> {
            val meters = 1f / diopters
            if (meters >= 1f) String.format(Locale.US, "%.1fm", meters)
            else "${(meters * 100).roundToInt()}cm"
        }
    }

    /**
     * Maps a 0..1 slider position to diopters on a perceptual curve so the
     * near-focus end (where small diopter changes matter most) gets more travel.
     */
    fun sliderToDiopters(position: Float, minFocusDiopters: Float): Float {
        val p = position.coerceIn(0f, 1f)
        return minFocusDiopters * p.toDouble().pow(2.0).toFloat()
    }

    fun dioptersToSlider(diopters: Float, minFocusDiopters: Float): Float {
        if (minFocusDiopters <= 0f) return 0f
        return kotlin.math.sqrt((diopters / minFocusDiopters).coerceIn(0f, 1f))
    }
}
