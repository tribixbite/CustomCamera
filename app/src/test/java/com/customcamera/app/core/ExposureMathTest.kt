package com.customcamera.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExposureMathTest {

    @Test
    fun `fast shutter speeds format as fractions`() {
        assertEquals("1/125", ExposureMath.formatShutter(8_000_000L))
        assertEquals("1/1000", ExposureMath.formatShutter(1_000_000L))
        assertEquals("1/3", ExposureMath.formatShutter(333_333_333L))
    }

    @Test
    fun `fast shutter labels snap to standard values`() {
        // Live auto reading between stops → nearest third stop.
        assertEquals("1/125", ExposureMath.formatShutter(1_000_000_000L / 117))
        assertEquals("1/60", ExposureMath.formatShutter(1_000_000_000L / 59))
        // Sensor limit beyond the ladder → two significant figures.
        assertEquals("1/12000", ExposureMath.formatShutter(1_000_000_000L / 11_977))
        assertEquals("1/100000", ExposureMath.formatShutter(10_000L))
        // Far from any stop (more than 1/6 stop) → rounded, not forced onto a stop.
        assertEquals(35L, ExposureMath.displayDenominator(34.6))
        assertEquals(8000L, ExposureMath.displayDenominator(8100.0))
    }

    @Test
    fun `long shutter speeds format as seconds`() {
        assertEquals("1\"", ExposureMath.formatShutter(1_000_000_000L))
        assertEquals("0.5\"", ExposureMath.formatShutter(500_000_000L))
        assertEquals("30\"", ExposureMath.formatShutter(30_000_000_000L))
        assertEquals("2.5\"", ExposureMath.formatShutter(2_500_000_000L))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `zero exposure is rejected`() {
        ExposureMath.formatShutter(0)
    }

    @Test
    fun `shutter stops stay inside the sensor range and include bounds`() {
        val min = 100_000L // 1/10000
        val max = 500_000_000L // 0.5 s
        val stops = ExposureMath.shutterStops(min, max)
        assertEquals(min, stops.first())
        assertEquals(max, stops.last())
        assertTrue(stops.all { it in min..max })
        assertEquals(stops.sorted(), stops)
        assertEquals(stops.distinct(), stops)
        assertTrue(1_000_000_000L / 125 in stops)
    }

    @Test
    fun `iso stops stay inside range and are sorted`() {
        val stops = ExposureMath.isoStops(50, 3200)
        assertEquals(50, stops.first())
        assertEquals(3200, stops.last())
        assertTrue(100 in stops && 800 in stops)
        assertTrue(25 !in stops && 6400 !in stops)
        assertEquals(stops.sorted(), stops)
    }

    @Test
    fun `odd iso range bounds are kept`() {
        val stops = ExposureMath.isoStops(42, 3333)
        assertEquals(42, stops.first())
        assertEquals(3333, stops.last())
    }

    @Test
    fun `nearest index picks the closest stop`() {
        val stops = listOf(10L, 20L, 40L, 80L)
        assertEquals(0, ExposureMath.nearestIndex(stops, 1L))
        assertEquals(2, ExposureMath.nearestIndex(stops, 39L))
        assertEquals(3, ExposureMath.nearestIndex(stops, 1_000L))
        assertEquals(1, ExposureMath.nearestIsoIndex(listOf(100, 200, 400), 210))
    }

    @Test
    fun `ev formatting uses sign and one decimal`() {
        val step = 1f / 3
        assertEquals("0.0", ExposureMath.formatEv(0, step))
        assertEquals("+0.3", ExposureMath.formatEv(1, step))
        assertEquals("-1.0", ExposureMath.formatEv(-3, step))
        assertEquals("+2.0", ExposureMath.formatEv(4, 0.5f))
    }

    @Test
    fun `focus distance formatting`() {
        assertEquals("∞", ExposureMath.formatFocusDistance(0f))
        assertEquals("2.0m", ExposureMath.formatFocusDistance(0.5f))
        assertEquals("10cm", ExposureMath.formatFocusDistance(10f))
    }

    @Test
    fun `focus slider mapping round-trips and is monotonic`() {
        val minFocus = 10f
        var previous = -1f
        for (i in 0..20) {
            val p = i / 20f
            val d = ExposureMath.sliderToDiopters(p, minFocus)
            assertTrue(d >= previous)
            previous = d
            assertEquals(p, ExposureMath.dioptersToSlider(d, minFocus), 1e-4f)
        }
        assertEquals(0f, ExposureMath.sliderToDiopters(0f, minFocus), 0f)
        assertEquals(minFocus, ExposureMath.sliderToDiopters(1f, minFocus), 1e-5f)
        assertEquals(0f, ExposureMath.dioptersToSlider(5f, 0f), 0f)
    }
}
