package com.customcamera.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ZoomMathTest {

    @Test
    fun `logical multi camera gets ultra wide and telephoto chips`() {
        assertEquals(listOf(0.6f, 1f, 2f, 3f, 5f), ZoomMath.presets(0.6f, 100f))
    }

    @Test
    fun `single lens camera with digital zoom`() {
        assertEquals(listOf(1f, 2f, 3f, 5f), ZoomMath.presets(1f, 8f))
    }

    @Test
    fun `fixed lens camera has a single chip`() {
        assertEquals(listOf(1f), ZoomMath.presets(1f, 1f))
    }

    @Test
    fun `camera whose minimum is above 1x still gets a chip`() {
        val presets = ZoomMath.presets(1.2f, 1.5f)
        assertEquals(listOf(1.2f), presets)
    }

    @Test
    fun `presets never exceed five and are ascending`() {
        val presets = ZoomMath.presets(0.5f, 30f)
        assertTrue(presets.size <= 5)
        assertEquals(presets.sorted(), presets)
    }

    @Test
    fun `active preset highlights largest preset not above ratio`() {
        val presets = listOf(0.6f, 1f, 2f, 5f)
        assertEquals(0, ZoomMath.activePresetIndex(presets, 0.6f))
        assertEquals(1, ZoomMath.activePresetIndex(presets, 1.5f))
        assertEquals(2, ZoomMath.activePresetIndex(presets, 1.97f))
        assertEquals(3, ZoomMath.activePresetIndex(presets, 30f))
        assertEquals(-1, ZoomMath.activePresetIndex(emptyList(), 1f))
    }

    @Test
    fun `zoom labels`() {
        assertEquals("1x", ZoomMath.format(1f))
        assertEquals("0.6x", ZoomMath.format(0.6f))
        assertEquals("2.5x", ZoomMath.format(2.48f))
        assertEquals("12x", ZoomMath.format(12.3f))
        assertEquals("2x", ZoomMath.format(1.98f))
    }

    @Test
    fun `pinch and step are clamped to range`() {
        assertEquals(10f, ZoomMath.applyPinch(8f, 2f, 1f, 10f), 0f)
        assertEquals(1f, ZoomMath.applyPinch(1.2f, 0.5f, 1f, 10f), 0f)
        assertEquals(2.2f, ZoomMath.step(2f, true, 1f, 10f), 1e-5f)
        assertEquals(1f, ZoomMath.step(1f, false, 1f, 10f), 0f)
    }
}
