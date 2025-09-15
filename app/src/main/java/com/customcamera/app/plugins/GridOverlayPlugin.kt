package com.customcamera.app.plugins

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import android.view.View
import androidx.camera.core.Camera
import com.customcamera.app.engine.CameraContext
import com.customcamera.app.engine.plugins.UIEvent
import com.customcamera.app.engine.plugins.UIPlugin

/**
 * GridOverlayPlugin provides rule-of-thirds and other grid overlays
 * to assist with photo composition. Demonstrates UIPlugin functionality.
 */
class GridOverlayPlugin : UIPlugin() {

    override val name: String = "GridOverlay"
    override val version: String = "1.0.0"
    override val priority: Int = 50 // Medium priority for UI

    private var cameraContext: CameraContext? = null
    private var gridOverlayView: GridOverlayView? = null
    private var isOverlayVisible: Boolean = true
    private var gridType: GridType = GridType.RULE_OF_THIRDS

    enum class GridType {
        RULE_OF_THIRDS,    // 3x3 grid for rule of thirds
        GOLDEN_RATIO,      // Golden ratio grid
        CENTER_CROSS,      // Simple center cross
        DIAGONAL_LINES,    // Diagonal composition lines
        SQUARE_GRID        // 4x4 square grid
    }

    override suspend fun initialize(context: CameraContext) {
        this.cameraContext = context
        Log.i(TAG, "GridOverlayPlugin initialized")

        // Load settings from settings manager
        loadSettings(context)

        // Log current grid configuration
        context.debugLogger.logPlugin(
            name,
            "initialized",
            mapOf(
                "gridType" to gridType.name,
                "isVisible" to isOverlayVisible
            )
        )
    }

    override suspend fun onCameraReady(camera: Camera) {
        Log.i(TAG, "Camera ready, grid overlay available")

        // Grid overlay doesn't need direct camera access
        // It's purely a UI overlay

        cameraContext?.debugLogger?.logPlugin(
            name,
            "camera_ready",
            mapOf(
                "gridEnabled" to isEnabled,
                "gridVisible" to isOverlayVisible
            )
        )
    }

    override suspend fun onCameraReleased(camera: Camera) {
        Log.i(TAG, "Camera released, cleaning up grid overlay")
        // Grid overlay persists across camera switches
    }

    override fun createUIView(context: CameraContext): View? {
        if (!isEnabled || !isOverlayVisible) {
            return null
        }

        Log.i(TAG, "Creating grid overlay view")

        gridOverlayView = GridOverlayView(context.context).apply {
            setGridType(gridType)
            setGridEnabled(isOverlayVisible)
        }

        return gridOverlayView
    }

    override fun updateUI(camera: Camera) {
        // Grid overlay doesn't change based on camera state
        // But we could add features like exposure zones here
        Log.d(TAG, "UI update called (no changes needed for grid)")
    }

    override fun onUIEvent(event: UIEvent) {
        when (event) {
            is UIEvent.Show -> {
                showGrid()
            }
            is UIEvent.Hide -> {
                hideGrid()
            }
            is UIEvent.StateChange -> {
                if (event.state == "grid_type" && event.data is GridType) {
                    setGridType(event.data)
                }
            }
            else -> {
                Log.d(TAG, "Unhandled UI event: $event")
            }
        }
    }

    /**
     * Show the grid overlay
     */
    fun showGrid() {
        if (!isOverlayVisible) {
            isOverlayVisible = true
            gridOverlayView?.setGridEnabled(true)
            saveVisibilityState()
            Log.i(TAG, "Grid overlay shown")

            cameraContext?.debugLogger?.logPlugin(
                name,
                "grid_shown",
                mapOf("gridType" to gridType.name)
            )
        }
    }

    /**
     * Hide the grid overlay
     */
    fun hideGrid() {
        if (isOverlayVisible) {
            isOverlayVisible = false
            gridOverlayView?.setGridEnabled(false)
            saveVisibilityState()
            Log.i(TAG, "Grid overlay hidden")

            cameraContext?.debugLogger?.logPlugin(
                name,
                "grid_hidden",
                emptyMap()
            )
        }
    }

    /**
     * Toggle grid visibility
     */
    fun toggleGrid() {
        if (isOverlayVisible) {
            hideGrid()
        } else {
            showGrid()
        }
    }

    /**
     * Set the grid type
     */
    fun setGridType(type: GridType) {
        if (gridType != type) {
            gridType = type
            gridOverlayView?.setGridType(type)
            saveGridType()
            Log.i(TAG, "Grid type changed to: $type")

            cameraContext?.debugLogger?.logPlugin(
                name,
                "grid_type_changed",
                mapOf("newType" to type.name)
            )
        }
    }

    /**
     * Get current grid type
     */
    fun getGridType(): GridType = gridType

    /**
     * Check if grid is currently visible
     */
    fun isGridVisible(): Boolean = isOverlayVisible

    override fun cleanup() {
        Log.i(TAG, "Cleaning up GridOverlayPlugin")

        gridOverlayView = null
        cameraContext = null
    }

    private fun loadSettings(context: CameraContext) {
        val settings = context.settingsManager

        try {
            // Load grid type
            val typeString = settings.getPluginSetting(name, "gridType", GridType.RULE_OF_THIRDS.name)
            gridType = GridType.valueOf(typeString)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load grid type setting, using default", e)
            gridType = GridType.RULE_OF_THIRDS
        }

        // Load visibility setting
        try {
            val visibilityString = settings.getPluginSetting(name, "isVisible", "true")
            isOverlayVisible = visibilityString.toBoolean()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load visibility setting, using default", e)
            isOverlayVisible = true
        }

        Log.i(TAG, "Loaded settings: type=$gridType, visible=$isOverlayVisible")
    }

    private fun saveGridType() {
        cameraContext?.settingsManager?.setPluginSetting(name, "gridType", gridType.name)
    }

    private fun saveVisibilityState() {
        cameraContext?.settingsManager?.setPluginSetting(name, "isVisible", isOverlayVisible.toString())
    }

    companion object {
        private const val TAG = "GridOverlayPlugin"
    }
}

/**
 * Custom view that draws grid overlays for composition assistance
 */
class GridOverlayView(context: Context) : View(context) {

    private val paint = Paint().apply {
        color = Color.WHITE
        strokeWidth = 2f
        style = Paint.Style.STROKE
        alpha = 180 // Semi-transparent
    }

    private var gridType: GridOverlayPlugin.GridType = GridOverlayPlugin.GridType.RULE_OF_THIRDS
    private var isGridEnabled: Boolean = true

    fun setGridType(type: GridOverlayPlugin.GridType) {
        gridType = type
        invalidate() // Trigger redraw
    }

    fun setGridEnabled(enabled: Boolean) {
        isGridEnabled = enabled
        visibility = if (enabled) VISIBLE else GONE
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (!isGridEnabled) return

        val width = width.toFloat()
        val height = height.toFloat()

        if (width <= 0 || height <= 0) return

        when (gridType) {
            GridOverlayPlugin.GridType.RULE_OF_THIRDS -> {
                drawRuleOfThirds(canvas, width, height)
            }
            GridOverlayPlugin.GridType.GOLDEN_RATIO -> {
                drawGoldenRatio(canvas, width, height)
            }
            GridOverlayPlugin.GridType.CENTER_CROSS -> {
                drawCenterCross(canvas, width, height)
            }
            GridOverlayPlugin.GridType.DIAGONAL_LINES -> {
                drawDiagonalLines(canvas, width, height)
            }
            GridOverlayPlugin.GridType.SQUARE_GRID -> {
                drawSquareGrid(canvas, width, height)
            }
        }
    }

    private fun drawRuleOfThirds(canvas: Canvas, width: Float, height: Float) {
        // Vertical lines
        canvas.drawLine(width / 3f, 0f, width / 3f, height, paint)
        canvas.drawLine(2f * width / 3f, 0f, 2f * width / 3f, height, paint)

        // Horizontal lines
        canvas.drawLine(0f, height / 3f, width, height / 3f, paint)
        canvas.drawLine(0f, 2f * height / 3f, width, 2f * height / 3f, paint)
    }

    private fun drawGoldenRatio(canvas: Canvas, width: Float, height: Float) {
        val ratio = 1.618f
        val verticalGolden = width / ratio
        val horizontalGolden = height / ratio

        // Vertical lines at golden ratio positions
        canvas.drawLine(verticalGolden, 0f, verticalGolden, height, paint)
        canvas.drawLine(width - verticalGolden, 0f, width - verticalGolden, height, paint)

        // Horizontal lines at golden ratio positions
        canvas.drawLine(0f, horizontalGolden, width, horizontalGolden, paint)
        canvas.drawLine(0f, height - horizontalGolden, width, height - horizontalGolden, paint)
    }

    private fun drawCenterCross(canvas: Canvas, width: Float, height: Float) {
        val centerX = width / 2f
        val centerY = height / 2f

        // Vertical center line
        canvas.drawLine(centerX, 0f, centerX, height, paint)

        // Horizontal center line
        canvas.drawLine(0f, centerY, width, centerY, paint)
    }

    private fun drawDiagonalLines(canvas: Canvas, width: Float, height: Float) {
        // Diagonal from top-left to bottom-right
        canvas.drawLine(0f, 0f, width, height, paint)

        // Diagonal from top-right to bottom-left
        canvas.drawLine(width, 0f, 0f, height, paint)
    }

    private fun drawSquareGrid(canvas: Canvas, width: Float, height: Float) {
        // 4x4 grid
        for (i in 1..3) {
            // Vertical lines
            val x = i * width / 4f
            canvas.drawLine(x, 0f, x, height, paint)

            // Horizontal lines
            val y = i * height / 4f
            canvas.drawLine(0f, y, width, y, paint)
        }
    }
}