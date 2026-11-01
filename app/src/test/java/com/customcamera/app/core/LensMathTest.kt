package com.customcamera.app.core

import com.customcamera.app.core.LensMath.LensCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LensMathTest {

    @Test
    fun `full frame sensor equals physical focal length`() {
        assertEquals(50, LensMath.equivalentFocalLength(50f, 36f, 24f))
    }

    @Test
    fun `phone main camera is roughly 24mm equivalent`() {
        // 6.3 mm lens on a ~9.8 x 7.4 mm (1/1.3") sensor.
        assertEquals(22, LensMath.equivalentFocalLength(6.3f, 9.8f, 7.4f))
    }

    @Test
    fun `invalid input yields null`() {
        assertNull(LensMath.equivalentFocalLength(0f, 5f, 4f))
        assertNull(LensMath.equivalentFocalLength(4f, 0f, 4f))
    }

    @Test
    fun `categories`() {
        assertEquals(LensCategory.ULTRA_WIDE, LensMath.category(13))
        assertEquals(LensCategory.WIDE, LensMath.category(24))
        assertEquals(LensCategory.STANDARD, LensMath.category(50))
        assertEquals(LensCategory.TELEPHOTO, LensMath.category(111))
        assertEquals(LensCategory.STANDARD, LensMath.category(null))
    }
}
