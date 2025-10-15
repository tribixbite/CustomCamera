package com.customcamera.app.presentation

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.Log
import android.view.View
import java.util.LinkedList

/**
 * Performance Monitor - Real-time metrics for conference demos
 *
 * Displays:
 * - FPS (frames per second)
 * - Frame processing time
 * - Memory usage
 * - Camera metrics
 * - Plugin performance
 */
class PerformanceMonitor @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val graphPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var isVisible = false
    private var currentFps = 0f
    private var avgProcessingTime = 0f
    private var memoryUsageMb = 0f
    private var activePlugins = 0

    private val fpsHistory = LinkedList<Float>()
    private val maxHistorySize = 60 // 1 second at 60fps

    private var lastFrameTime = System.nanoTime()
    private var frameCount = 0
    private var fpsUpdateTime = System.currentTimeMillis()

    init {
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(180, 0, 0, 0)

        textPaint.color = Color.WHITE
        textPaint.textSize = 32f
        textPaint.typeface = Typeface.MONOSPACE
        textPaint.setShadowLayer(4f, 0f, 2f, Color.BLACK)

        graphPaint.style = Paint.Style.STROKE
        graphPaint.strokeWidth = 3f
        graphPaint.color = Color.GREEN
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (!isVisible) return

        val width = width.toFloat()
        val height = 300f
        val padding = 20f

        // Background
        canvas.drawRoundRect(
            padding, padding,
            width - padding, height,
            20f, 20f,
            paint
        )

        // Draw metrics
        var yPos = padding + 50f

        // FPS
        val fpsColor = when {
            currentFps >= 55f -> Color.GREEN
            currentFps >= 30f -> Color.YELLOW
            else -> Color.RED
        }
        textPaint.color = fpsColor
        canvas.drawText("FPS: ${String.format("%.1f", currentFps)}", padding + 30f, yPos, textPaint)
        yPos += 45f

        // Processing time
        textPaint.color = Color.CYAN
        canvas.drawText("Processing: ${String.format("%.1f", avgProcessingTime)}ms", padding + 30f, yPos, textPaint)
        yPos += 45f

        // Memory
        textPaint.color = Color.MAGENTA
        canvas.drawText("Memory: ${String.format("%.1f", memoryUsageMb)}MB", padding + 30f, yPos, textPaint)
        yPos += 45f

        // Active plugins
        textPaint.color = Color.WHITE
        canvas.drawText("Plugins: $activePlugins active", padding + 30f, yPos, textPaint)

        // Draw FPS graph
        drawFpsGraph(canvas, width, height, padding)
    }

    /**
     * Draw FPS history graph
     */
    private fun drawFpsGraph(canvas: Canvas, width: Float, height: Float, padding: Float) {
        if (fpsHistory.size < 2) return

        val graphHeight = 80f
        val graphTop = height - graphHeight - padding
        val graphWidth = width - (padding * 2)

        // Graph background
        paint.color = Color.argb(100, 0, 0, 0)
        canvas.drawRect(padding, graphTop, width - padding, height - padding, paint)

        // FPS line
        graphPaint.color = Color.GREEN
        val path = Path()
        var isFirst = true

        fpsHistory.forEachIndexed { index, fps ->
            val x = padding + (index / maxHistorySize.toFloat()) * graphWidth
            val y = graphTop + graphHeight - ((fps / 60f) * graphHeight).coerceIn(0f, graphHeight)

            if (isFirst) {
                path.moveTo(x, y)
                isFirst = false
            } else {
                path.lineTo(x, y)
            }
        }

        canvas.drawPath(path, graphPaint)

        // 60fps reference line
        graphPaint.color = Color.argb(100, 255, 255, 255)
        canvas.drawLine(padding, graphTop, width - padding, graphTop, graphPaint)

        // 30fps reference line
        val fps30Y = graphTop + graphHeight / 2f
        canvas.drawLine(padding, fps30Y, width - padding, fps30Y, graphPaint)
    }

    /**
     * Update frame
     */
    fun onFrame() {
        frameCount++

        val currentTime = System.currentTimeMillis()
        val elapsed = currentTime - fpsUpdateTime

        if (elapsed >= 1000) {
            currentFps = (frameCount * 1000f) / elapsed
            frameCount = 0
            fpsUpdateTime = currentTime

            // Add to history
            fpsHistory.add(currentFps)
            if (fpsHistory.size > maxHistorySize) {
                fpsHistory.removeFirst()
            }
        }

        invalidate()
    }

    /**
     * Update processing time
     */
    fun updateProcessingTime(timeMs: Float) {
        avgProcessingTime = avgProcessingTime * 0.9f + timeMs * 0.1f // Exponential moving average
    }

    /**
     * Update memory usage
     */
    fun updateMemoryUsage() {
        val runtime = Runtime.getRuntime()
        val usedMemory = runtime.totalMemory() - runtime.freeMemory()
        memoryUsageMb = usedMemory / (1024f * 1024f)
    }

    /**
     * Update active plugins count
     */
    fun updatePluginCount(count: Int) {
        activePlugins = count
    }

    /**
     * Show/hide performance monitor
     */
    fun setVisible(visible: Boolean) {
        isVisible = visible
        visibility = if (visible) VISIBLE else GONE
        invalidate()

        Log.i(TAG, "Performance monitor ${if (visible) "shown" else "hidden"}")
    }

    /**
     * Toggle visibility
     */
    fun toggle() {
        setVisible(!isVisible)
    }

    companion object {
        private const val TAG = "PerformanceMonitor"
    }
}
