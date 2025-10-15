package com.customcamera.app.testing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.media.Image
import androidx.camera.core.ImageProxy
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.nio.ByteBuffer

/**
 * Test Image Factory - Generate mock images for testing
 *
 * Provides utilities to create:
 * - Mock ImageProxy instances
 * - Test bitmaps with specific characteristics
 * - YUV test data
 * - Various image formats
 *
 * Use Cases:
 * - Plugin processing tests
 * - Performance benchmarks
 * - Edge case testing (null, empty, malformed)
 */
object TestImageFactory {

    /**
     * Image dimensions presets
     */
    enum class ImageSize(val width: Int, val height: Int) {
        THUMBNAIL(320, 240),
        VGA(640, 480),
        HD(1280, 720),
        FULL_HD(1920, 1080),
        QUAD_HD(2560, 1440),
        ULTRA_HD(3840, 2160)
    }

    /**
     * Create mock ImageProxy with specified dimensions
     */
    fun createMockImageProxy(
        width: Int = 1920,
        height: Int = 1080,
        format: Int = android.graphics.ImageFormat.YUV_420_888,
        timestamp: Long = System.currentTimeMillis() * 1000000
    ): ImageProxy {
        val mockProxy = mock(ImageProxy::class.java)
        val mockImage = mock(Image::class.java)

        `when`(mockProxy.width).thenReturn(width)
        `when`(mockProxy.height).thenReturn(height)
        `when`(mockProxy.format).thenReturn(format)
        `when`(mockProxy.imageInfo).thenReturn(mock(androidx.camera.core.ImageInfo::class.java))
        `when`(mockProxy.image).thenReturn(mockImage)

        // Mock YUV planes
        val yPlane = createMockPlane(width * height)
        val uPlane = createMockPlane(width * height / 4)
        val vPlane = createMockPlane(width * height / 4)

        `when`(mockImage.planes).thenReturn(arrayOf(yPlane, uPlane, vPlane))
        `when`(mockImage.width).thenReturn(width)
        `when`(mockImage.height).thenReturn(height)
        `when`(mockImage.format).thenReturn(format)
        `when`(mockImage.timestamp).thenReturn(timestamp)

        return mockProxy
    }

    /**
     * Create mock Image.Plane with test data
     */
    private fun createMockPlane(size: Int): Image.Plane {
        val mockPlane = mock(Image.Plane::class.java)
        val buffer = ByteBuffer.allocateDirect(size)

        // Fill with test data (gray)
        for (i in 0 until size) {
            buffer.put(128.toByte())
        }
        buffer.rewind()

        `when`(mockPlane.buffer).thenReturn(buffer)
        `when`(mockPlane.pixelStride).thenReturn(1)
        `when`(mockPlane.rowStride).thenReturn(if (size > 1000) kotlin.math.sqrt(size.toDouble()).toInt() else size)

        return mockPlane
    }

    /**
     * Create test bitmap with specific characteristics
     */
    fun createTestBitmap(
        size: ImageSize = ImageSize.HD,
        backgroundColor: Int = Color.WHITE,
        addPattern: Boolean = false
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Fill background
        canvas.drawColor(backgroundColor)

        if (addPattern) {
            // Add test pattern (grid)
            val paint = Paint().apply {
                color = Color.BLACK
                strokeWidth = 2f
                style = Paint.Style.STROKE
            }

            val gridSize = 100
            for (x in 0 until size.width step gridSize) {
                canvas.drawLine(x.toFloat(), 0f, x.toFloat(), size.height.toFloat(), paint)
            }
            for (y in 0 until size.height step gridSize) {
                canvas.drawLine(0f, y.toFloat(), size.width.toFloat(), y.toFloat(), paint)
            }

            // Add center marker
            paint.color = Color.RED
            paint.strokeWidth = 4f
            val centerX = size.width / 2f
            val centerY = size.height / 2f
            canvas.drawCircle(centerX, centerY, 50f, paint)
        }

        return bitmap
    }

    /**
     * Create bitmap with specific brightness
     */
    fun createBitmapWithBrightness(
        size: ImageSize = ImageSize.HD,
        brightness: Float = 0.5f // 0.0 = black, 1.0 = white
    ): Bitmap {
        val grayValue = (brightness * 255).toInt().coerceIn(0, 255)
        val color = Color.rgb(grayValue, grayValue, grayValue)
        return createTestBitmap(size, color, false)
    }

    /**
     * Create bitmap with color gradient
     */
    fun createGradientBitmap(
        size: ImageSize = ImageSize.HD,
        startColor: Int = Color.BLACK,
        endColor: Int = Color.WHITE
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val paint = Paint()
        val shader = android.graphics.LinearGradient(
            0f, 0f, size.width.toFloat(), size.height.toFloat(),
            startColor, endColor,
            android.graphics.Shader.TileMode.CLAMP
        )
        paint.shader = shader
        canvas.drawRect(0f, 0f, size.width.toFloat(), size.height.toFloat(), paint)

        return bitmap
    }

    /**
     * Create collection of test images for batch testing
     */
    fun createTestImageBatch(
        count: Int = 10,
        size: ImageSize = ImageSize.HD
    ): List<ImageProxy> {
        return (0 until count).map {
            createMockImageProxy(size.width, size.height)
        }
    }

    /**
     * Create edge case test images
     */
    fun createEdgeCaseImages(): List<ImageProxy> {
        return listOf(
            createMockImageProxy(1, 1), // Minimum size
            createMockImageProxy(100, 100), // Small
            createMockImageProxy(10000, 10000), // Very large
            createMockImageProxy(1920, 1), // Wide
            createMockImageProxy(1, 1080) // Tall
        )
    }

    /**
     * Create test bitmap with specific objects for detection testing
     */
    fun createBitmapWithObjects(
        size: ImageSize = ImageSize.HD,
        objectType: ObjectType = ObjectType.FACE
    ): Bitmap {
        val bitmap = createTestBitmap(size, Color.WHITE, false)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply {
            style = Paint.Style.FILL
        }

        when (objectType) {
            ObjectType.FACE -> {
                // Simple face representation
                paint.color = Color.rgb(255, 220, 177) // Skin tone
                canvas.drawOval(
                    RectF(
                        (size.width / 3).toFloat(),
                        (size.height / 3).toFloat(),
                        (2 * size.width / 3).toFloat(),
                        (2 * size.height / 3).toFloat()
                    ),
                    paint
                )

                // Eyes
                paint.color = Color.BLACK
                canvas.drawCircle(size.width / 2f - 50, size.height / 2f - 30, 15f, paint)
                canvas.drawCircle(size.width / 2f + 50, size.height / 2f - 30, 15f, paint)
            }
            ObjectType.BARCODE -> {
                // Simple barcode pattern
                paint.color = Color.BLACK
                val barWidth = 10
                var x = size.width / 4
                while (x < 3 * size.width / 4) {
                    canvas.drawRect(
                        x.toFloat(),
                        size.height / 3f,
                        (x + barWidth).toFloat(),
                        2 * size.height / 3f,
                        paint
                    )
                    x += barWidth * 2
                }
            }
            ObjectType.TEXT -> {
                // Text representation
                paint.color = Color.BLACK
                paint.textSize = 48f
                canvas.drawText(
                    "TEST TEXT",
                    size.width / 4f,
                    size.height / 2f,
                    paint
                )
            }
        }

        return bitmap
    }

    enum class ObjectType {
        FACE,
        BARCODE,
        TEXT
    }
}
