package com.customcamera.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraOptionsTest {

    @Test
    fun `extension modes are hidden when unsupported`() {
        val modes = CaptureMode.available(emptySet())
        assertEquals(listOf(CaptureMode.PHOTO, CaptureMode.VIDEO, CaptureMode.PRO, CaptureMode.SCAN), modes)
    }

    @Test
    fun `supported extension modes are listed before photo`() {
        val modes = CaptureMode.available(setOf(ExtensionIds.NIGHT))
        assertEquals(CaptureMode.NIGHT, modes.first())
        assertFalse(CaptureMode.PORTRAIT in modes)
    }

    @Test
    fun `dual mode is listed only with a concurrent camera pair`() {
        assertFalse(CaptureMode.DUAL in CaptureMode.available(emptySet()))
        val modes = CaptureMode.available(emptySet(), dualSupported = true)
        assertEquals(listOf(CaptureMode.PHOTO, CaptureMode.VIDEO, CaptureMode.DUAL, CaptureMode.PRO, CaptureMode.SCAN), modes)
    }

    @Test
    fun `dual records video and has no capture flash or torch`() {
        assertTrue(CaptureMode.DUAL.isVideo)
        assertTrue(CaptureMode.VIDEO.isVideo)
        assertFalse(CaptureMode.DUAL.isPhotoLike)
        assertFalse(CaptureMode.DUAL.usesTorch)
        assertFalse(CaptureMode.PHOTO.isVideo)
    }

    @Test
    fun `mode keys round trip and unknown keys fall back to photo`() {
        CaptureMode.entries.forEach { assertEquals(it, CaptureMode.fromKey(it.key)) }
        assertEquals(CaptureMode.PHOTO, CaptureMode.fromKey("bogus"))
        assertEquals(CaptureMode.PHOTO, CaptureMode.fromKey(null))
    }

    @Test
    fun `photo like modes`() {
        assertTrue(CaptureMode.PRO.isPhotoLike)
        assertTrue(CaptureMode.NIGHT.isPhotoLike)
        assertFalse(CaptureMode.VIDEO.isPhotoLike)
        assertFalse(CaptureMode.SCAN.isPhotoLike)
    }

    @Test
    fun `flash cycles off auto on in photo mode`() {
        assertEquals(FlashOption.AUTO, FlashOption.next(FlashOption.OFF, videoMode = false))
        assertEquals(FlashOption.ON, FlashOption.next(FlashOption.AUTO, videoMode = false))
        assertEquals(FlashOption.OFF, FlashOption.next(FlashOption.ON, videoMode = false))
        assertEquals(FlashOption.OFF, FlashOption.next(FlashOption.TORCH, videoMode = false))
    }

    @Test
    fun `flash toggles torch in video mode`() {
        assertEquals(FlashOption.TORCH, FlashOption.next(FlashOption.OFF, videoMode = true))
        assertEquals(FlashOption.TORCH, FlashOption.next(FlashOption.AUTO, videoMode = true))
        assertEquals(FlashOption.OFF, FlashOption.next(FlashOption.TORCH, videoMode = true))
    }

    @Test
    fun `timer and aspect cycle through every value`() {
        var t = TimerOption.OFF
        val seen = mutableListOf<TimerOption>()
        repeat(TimerOption.entries.size) { seen += t; t = t.next() }
        assertEquals(TimerOption.entries, seen)
        assertEquals(TimerOption.OFF, t)

        assertEquals(AspectOption.RATIO_16_9, AspectOption.RATIO_4_3.next())
        assertEquals(AspectOption.RATIO_4_3, AspectOption.RATIO_1_1.next())
        assertEquals(4f / 3f, AspectOption.RATIO_4_3.heightOverWidth, 1e-6f)
    }

    @Test
    fun `aspect label follows the window shape`() {
        assertEquals("3:4", AspectOption.RATIO_4_3.label(landscape = false))
        assertEquals("4:3", AspectOption.RATIO_4_3.label(landscape = true))
        assertEquals("16:9", AspectOption.RATIO_16_9.label(landscape = true))
        assertEquals("1:1", AspectOption.RATIO_1_1.label(landscape = true))
    }

    @Test
    fun `every option enum round trips its key`() {
        FlashOption.entries.forEach { assertEquals(it, FlashOption.fromKey(it.key)) }
        TimerOption.entries.forEach { assertEquals(it, TimerOption.fromKey(it.key)) }
        AspectOption.entries.forEach { assertEquals(it, AspectOption.fromKey(it.key)) }
        GridOption.entries.forEach { assertEquals(it, GridOption.fromKey(it.key)) }
        VolumeKeyAction.entries.forEach { assertEquals(it, VolumeKeyAction.fromKey(it.key)) }
        PhotoQualityOption.entries.forEach { assertEquals(it, PhotoQualityOption.fromKey(it.key)) }
        VideoQualityOption.entries.forEach { assertEquals(it, VideoQualityOption.fromKey(it.key)) }
        assertEquals(VideoQualityOption.FHD, VideoQualityOption.fromKey(null))
        assertEquals(GridOption.OFF, GridOption.fromKey("nope"))
    }
}
