package com.customcamera.app.pip

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.Log
import android.view.*
import android.widget.FrameLayout
import androidx.camera.view.PreviewView
import androidx.core.view.ViewCompat
import com.customcamera.app.plugins.PiPPosition
import com.customcamera.app.plugins.PiPSize
import kotlin.math.*

/**
 * PiPOverlayView provides a draggable, resizable picture-in-picture overlay
 * for displaying a secondary camera feed on top of the main camera preview.
 */
class PiPOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private var previewView: PreviewView
    private var currentPosition = PiPPosition.TOP_RIGHT
    private var currentSize = PiPSize.MEDIUM
    private var isDraggable = true
    private var snapToCorners = true
    private var pipOpacity = 1.0f

    // Drag state
    private var isDragging = false
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var initialX = 0f
    private var initialY = 0f

    // Layout constraints
    private val cornerSnapDistance = 50f // pixels
    private val minDistanceFromEdge = 20f // pixels
    private val borderWidth = 4f // pixels
    private val cornerRadius = 12f // pixels

    // Callbacks
    private var onPositionChangedListener: ((PiPPosition) -> Unit)? = null
    private var onSwapRequestListener: (() -> Unit)? = null

    // Paint for border and effects
    private val borderPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = borderWidth
        isAntiAlias = true
    }

    private val shadowPaint = Paint().apply {
        color = Color.BLACK
        alpha = 80
        isAntiAlias = true
    }

    init {
        // Create and configure the preview view
        previewView = PreviewView(context).apply {
            id = ViewCompat.generateViewId()
            scaleType = PreviewView.ScaleType.FILL_CENTER
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT
            )
        }

        addView(previewView)

        // Configure the overlay
        setupOverlay()
        applyCurrentLayout()

        Log.i(TAG, "PiPOverlayView initialized")
    }

    /**
     * Get the preview view for camera binding
     */
    fun getPreviewView(): PreviewView = previewView

    /**
     * Set the position of the PiP overlay
     */
    fun setPosition(position: PiPPosition) {
        if (currentPosition != position) {
            currentPosition = position
            applyCurrentLayout()
            Log.d(TAG, "Position set to: ${position.name}")
        }
    }

    /**
     * Set the size of the PiP overlay
     */
    fun setSize(size: PiPSize) {
        if (currentSize != size) {
            currentSize = size
            applyCurrentLayout()
            Log.d(TAG, "Size set to: ${size.name}")
        }
    }

    /**
     * Enable or disable dragging
     */
    fun setDraggable(draggable: Boolean) {
        isDraggable = draggable
        Log.d(TAG, "Draggable set to: $draggable")
    }

    /**
     * Set opacity of the overlay
     */
    fun setOpacity(opacity: Float) {
        pipOpacity = opacity.coerceIn(0.1f, 1.0f)
        alpha = pipOpacity
        Log.d(TAG, "Opacity set to: $pipOpacity")
    }

    /**
     * Enable or disable snap to corners
     */
    fun setSnapToCorners(snap: Boolean) {
        snapToCorners = snap
        Log.d(TAG, "Snap to corners set to: $snap")
    }

    /**
     * Set position change listener
     */
    fun setOnPositionChangedListener(listener: (PiPPosition) -> Unit) {
        onPositionChangedListener = listener
    }

    /**
     * Set swap request listener (triggered by double tap)
     */
    fun setOnSwapRequestListener(listener: () -> Unit) {
        onSwapRequestListener = listener
    }

    /**
     * Apply current layout based on position and size
     */
    private fun applyCurrentLayout() {
        post {
            val parent = parent as? ViewGroup ?: return@post
            val parentWidth = parent.width
            val parentHeight = parent.height

            if (parentWidth == 0 || parentHeight == 0) {
                // Parent not measured yet, try again later
                post { applyCurrentLayout() }
                return@post
            }

            val overlayWidth = (parentWidth * currentSize.widthPercent).toInt()
            val overlayHeight = (parentHeight * currentSize.heightPercent).toInt()

            val layoutParams = LayoutParams(overlayWidth, overlayHeight)

            // Calculate position based on current position enum
            val (x, y) = calculatePositionCoordinates(
                parentWidth, parentHeight,
                overlayWidth, overlayHeight,
                currentPosition
            )

            // Apply position
            this.x = x
            this.y = y
            this.layoutParams = layoutParams

            Log.d(TAG, "Layout applied: position=${currentPosition.name}, size=${currentSize.name}, x=$x, y=$y")
        }
    }

    /**
     * Calculate coordinates for a given position
     */
    private fun calculatePositionCoordinates(
        parentWidth: Int,
        parentHeight: Int,
        overlayWidth: Int,
        overlayHeight: Int,
        position: PiPPosition
    ): Pair<Float, Float> {
        val margin = minDistanceFromEdge

        return when (position) {
            PiPPosition.TOP_LEFT -> Pair(margin, margin)
            PiPPosition.TOP_RIGHT -> Pair(
                parentWidth - overlayWidth - margin,
                margin
            )
            PiPPosition.BOTTOM_LEFT -> Pair(
                margin,
                parentHeight - overlayHeight - margin
            )
            PiPPosition.BOTTOM_RIGHT -> Pair(
                parentWidth - overlayWidth - margin,
                parentHeight - overlayHeight - margin
            )
            PiPPosition.CENTER_LEFT -> Pair(
                margin,
                (parentHeight - overlayHeight) / 2f
            )
            PiPPosition.CENTER_RIGHT -> Pair(
                parentWidth - overlayWidth - margin,
                (parentHeight - overlayHeight) / 2f
            )
            PiPPosition.TOP_CENTER -> Pair(
                (parentWidth - overlayWidth) / 2f,
                margin
            )
            PiPPosition.BOTTOM_CENTER -> Pair(
                (parentWidth - overlayWidth) / 2f,
                parentHeight - overlayHeight - margin
            )
        }
    }

    /**
     * Setup overlay appearance and behavior
     */
    private fun setupOverlay() {
        // Set initial appearance
        elevation = 8f
        setWillNotDraw(false)

        // Configure click handling
        isClickable = true
        isFocusable = true

        // Add gesture detection for double tap
        val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                onSwapRequestListener?.invoke()
                return true
            }
        })

        setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)

            if (isDraggable) {
                handleDragTouch(event)
            } else {
                false
            }
        }
    }

    /**
     * Handle touch events for dragging
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun handleDragTouch(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                isDragging = true
                lastTouchX = event.rawX
                lastTouchY = event.rawY
                initialX = x
                initialY = y

                // Bring to front during drag
                bringToFront()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (!isDragging) return false

                val deltaX = event.rawX - lastTouchX
                val deltaY = event.rawY - lastTouchY

                val newX = (x + deltaX).coerceIn(
                    minDistanceFromEdge,
                    (parent as View).width - width - minDistanceFromEdge
                )
                val newY = (y + deltaY).coerceIn(
                    minDistanceFromEdge,
                    (parent as View).height - height - minDistanceFromEdge
                )

                x = newX
                y = newY

                lastTouchX = event.rawX
                lastTouchY = event.rawY
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    isDragging = false

                    if (snapToCorners) {
                        snapToNearestPosition()
                    }

                    return true
                }
                return false
            }
        }
        return false
    }

    /**
     * Snap to the nearest predefined position
     */
    private fun snapToNearestPosition() {
        val parent = parent as? ViewGroup ?: return
        val parentWidth = parent.width
        val parentHeight = parent.height

        val centerX = x + width / 2f
        val centerY = y + height / 2f

        // Find the closest position
        val nearestPosition = PiPPosition.values().minByOrNull { position ->
            val (targetX, targetY) = calculatePositionCoordinates(
                parentWidth, parentHeight, width, height, position
            )
            val targetCenterX = targetX + width / 2f
            val targetCenterY = targetY + height / 2f

            sqrt((centerX - targetCenterX).pow(2) + (centerY - targetCenterY).pow(2))
        }

        nearestPosition?.let { position ->
            currentPosition = position
            applyCurrentLayout()
            onPositionChangedListener?.invoke(position)

            Log.d(TAG, "Snapped to position: ${position.name}")
        }
    }

    /**
     * Draw custom border and shadow effects
     */
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val bounds = RectF(0f, 0f, width.toFloat(), height.toFloat())

        // Draw shadow
        canvas.drawRoundRect(
            bounds.left + 2f,
            bounds.top + 2f,
            bounds.right + 2f,
            bounds.bottom + 2f,
            cornerRadius,
            cornerRadius,
            shadowPaint
        )

        // Draw border
        canvas.drawRoundRect(
            bounds,
            cornerRadius,
            cornerRadius,
            borderPaint
        )
    }

    /**
     * Update layout when parent size changes
     */
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w != oldw || h != oldh) {
            applyCurrentLayout()
        }
    }

    /**
     * Get current position information
     */
    fun getCurrentInfo(): Map<String, Any> {
        return mapOf(
            "position" to currentPosition.name,
            "size" to currentSize.name,
            "x" to x,
            "y" to y,
            "width" to width,
            "height" to height,
            "draggable" to isDraggable,
            "opacity" to pipOpacity,
            "snapToCorners" to snapToCorners,
            "isDragging" to isDragging
        )
    }

    companion object {
        private const val TAG = "PiPOverlayView"
    }
}