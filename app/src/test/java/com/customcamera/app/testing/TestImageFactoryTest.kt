package com.customcamera.app.testing

import org.junit.Test
import org.junit.Assert.*

/**
 * Test Image Factory Tests
 *
 * Simple tests to verify the TestImageFactory utility functions work correctly.
 * These tests don't require external dependencies or mocking libraries.
 */
class TestImageFactoryTest {

    @Test
    fun testCreateMockImageProxy() {
        val image = TestImageFactory.createMockImageProxy(
            width = 1920,
            height = 1080
        )

        assertNotNull(image)
        assertEquals(1920, image.width)
        assertEquals(1080, image.height)
    }

    @Test
    fun testCreateMockImageProxyWithDifferentSizes() {
        val sizes = listOf(
            TestImageFactory.ImageSize.THUMBNAIL,
            TestImageFactory.ImageSize.HD,
            TestImageFactory.ImageSize.FULL_HD
        )

        sizes.forEach { size ->
            val image = TestImageFactory.createMockImageProxy(
                width = size.width,
                height = size.height
            )

            assertNotNull(image)
            assertEquals(size.width, image.width)
            assertEquals(size.height, image.height)
        }
    }

    @Test
    fun testCreateTestBitmap() {
        val bitmap = TestImageFactory.createTestBitmap(
            size = TestImageFactory.ImageSize.HD
        )

        assertNotNull(bitmap)
        assertEquals(TestImageFactory.ImageSize.HD.width, bitmap.width)
        assertEquals(TestImageFactory.ImageSize.HD.height, bitmap.height)
    }

    @Test
    fun testCreateBitmapWithPattern() {
        val bitmap = TestImageFactory.createTestBitmap(
            size = TestImageFactory.ImageSize.THUMBNAIL,
            addPattern = true
        )

        assertNotNull(bitmap)
        assertTrue(bitmap.width > 0)
        assertTrue(bitmap.height > 0)
    }

    @Test
    fun testCreateBitmapWithBrightness() {
        // Test various brightness levels
        val brightnesses = listOf(0.0f, 0.5f, 1.0f)

        brightnesses.forEach { brightness ->
            val bitmap = TestImageFactory.createBitmapWithBrightness(
                size = TestImageFactory.ImageSize.THUMBNAIL,
                brightness = brightness
            )

            assertNotNull(bitmap)
            assertTrue(bitmap.width > 0)
            assertTrue(bitmap.height > 0)
        }
    }

    @Test
    fun testCreateGradientBitmap() {
        val bitmap = TestImageFactory.createGradientBitmap(
            size = TestImageFactory.ImageSize.THUMBNAIL
        )

        assertNotNull(bitmap)
        assertTrue(bitmap.width > 0)
        assertTrue(bitmap.height > 0)
    }

    @Test
    fun testCreateTestImageBatch() {
        val count = 5
        val images = TestImageFactory.createTestImageBatch(
            count = count,
            size = TestImageFactory.ImageSize.THUMBNAIL
        )

        assertNotNull(images)
        assertEquals(count, images.size)

        images.forEach { image ->
            assertNotNull(image)
            assertTrue(image.width > 0)
            assertTrue(image.height > 0)
        }
    }

    @Test
    fun testCreateEdgeCaseImages() {
        val edgeCases = TestImageFactory.createEdgeCaseImages()

        assertNotNull(edgeCases)
        assertTrue(edgeCases.isNotEmpty())

        edgeCases.forEach { image ->
            assertNotNull(image)
            assertTrue(image.width > 0)
            assertTrue(image.height > 0)
        }
    }

    @Test
    fun testCreateBitmapWithObjects() {
        val objectTypes = listOf(
            TestImageFactory.ObjectType.FACE,
            TestImageFactory.ObjectType.BARCODE,
            TestImageFactory.ObjectType.TEXT
        )

        objectTypes.forEach { objectType ->
            val bitmap = TestImageFactory.createBitmapWithObjects(
                size = TestImageFactory.ImageSize.THUMBNAIL,
                objectType = objectType
            )

            assertNotNull(bitmap)
            assertTrue(bitmap.width > 0)
            assertTrue(bitmap.height > 0)
        }
    }

    @Test
    fun testImageSizePresets() {
        // Verify all image size presets have valid dimensions
        val presets = listOf(
            TestImageFactory.ImageSize.THUMBNAIL,
            TestImageFactory.ImageSize.VGA,
            TestImageFactory.ImageSize.HD,
            TestImageFactory.ImageSize.FULL_HD,
            TestImageFactory.ImageSize.QUAD_HD,
            TestImageFactory.ImageSize.ULTRA_HD
        )

        presets.forEach { preset ->
            assertTrue("${preset.name} width should be positive", preset.width > 0)
            assertTrue("${preset.name} height should be positive", preset.height > 0)
        }
    }
}
