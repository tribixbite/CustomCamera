package com.customcamera.app.plugins

import android.content.Context
import android.graphics.*
import android.util.Log
import android.view.View
import androidx.camera.core.Camera
import com.customcamera.app.engine.CameraContext
import com.customcamera.app.engine.plugins.UIEvent
import com.customcamera.app.engine.plugins.UIPlugin
import com.customcamera.app.barcode.DetectedBarcode

/**
 * ScanningOverlayPlugin provides scanning UI overlay
 * with barcode highlighting and scan result display.
 */
class ScanningOverlayPlugin : UIPlugin() {

    override val name: String = "ScanningOverlay"
    override val version: String = "1.0.0"
    override val priority: Int = 45 // Medium priority for UI

    private var cameraContext: CameraContext? = null
    private var scanningOverlayView: ScanningOverlayView? = null

    // Overlay configuration
    private var isOverlayVisible: Boolean = true
    private var scanningMode: ScanningMode = ScanningMode.AUTO
    private var highlightColor: Int = Color.GREEN

    enum class ScanningMode {
        AUTO,    // Continuous scanning
        MANUAL,  // Scan on demand
        OFF      // No scanning
    }

    override suspend fun initialize(context: CameraContext) {
        this.cameraContext = context
        Log.i(TAG, "ScanningOverlayPlugin initialized")

        loadSettings(context)

        context.debugLogger.logPlugin(
            name,
            "initialized",
            mapOf(
                "overlayVisible" to isOverlayVisible,
                "scanningMode" to scanningMode.name,
                "highlightColor" to highlightColor
            )
        )
    }

    override suspend fun onCameraReady(camera: Camera) {
        Log.i(TAG, "Camera ready for scanning overlay")

        if (isOverlayVisible) {
            createScanningOverlay()
        }

        cameraContext?.debugLogger?.logPlugin(
            name,
            "camera_ready",
            mapOf("overlayCreated" to (scanningOverlayView != null))
        )
    }

    override suspend fun onCameraReleased(camera: Camera) {
        Log.i(TAG, "Camera released, hiding scanning overlay")
        hideScanningOverlay()
    }

    override fun createUIView(context: CameraContext): View? {
        if (!isEnabled || !isOverlayVisible) {
            return null
        }

        Log.i(TAG, "Creating scanning overlay view")

        scanningOverlayView = ScanningOverlayView(context.context).apply {
            setScanningMode(scanningMode)
            setHighlightColor(highlightColor)
        }

        return scanningOverlayView
    }

    override fun updateUI(camera: Camera) {
        scanningOverlayView?.updateForCamera(camera)
        Log.d(TAG, "Scanning overlay UI updated")
    }

    override fun onUIEvent(event: UIEvent) {
        when (event) {
            is UIEvent.Show -> showScanningOverlay()
            is UIEvent.Hide -> hideScanningOverlay()
            is UIEvent.StateChange -> {
                when (event.state) {
                    "scanning_mode" -> {
                        if (event.data is ScanningMode) {
                            setScanningMode(event.data)
                        }
                    }
                    "highlight_color" -> {
                        if (event.data is Int) {
                            setHighlightColor(event.data)
                        }
                    }
                }
            }
            else -> Log.d(TAG, "Unhandled UI event: $event")
        }
    }

    /**
     * Barcode highlighting with bounding boxes
     */
    fun highlightBarcodes(barcodes: List<DetectedBarcode>) {
        scanningOverlayView?.updateBarcodes(barcodes)
        Log.d(TAG, "Highlighted ${barcodes.size} barcodes")
    }

    /**
     * QR code corner detection indicators
     */
    fun highlightQRCorners(qrCodes: List<QRScannerPlugin.QRCode>) {
        scanningOverlayView?.updateQRCodes(qrCodes)
        Log.d(TAG, "Highlighted ${qrCodes.size} QR corner indicators")
    }

    /**
     * Scan result display and actions
     */
    fun displayScanResult(result: String, actionSuggestion: String?) {
        scanningOverlayView?.showScanResult(result, actionSuggestion)
        Log.i(TAG, "Displayed scan result: $result")
    }

    /**
     * Show scanning overlay
     */
    fun showScanningOverlay() {
        if (!isOverlayVisible) {
            isOverlayVisible = true
            createScanningOverlay()
            saveSettings()
            Log.i(TAG, "Scanning overlay shown")
        }
    }

    /**
     * Hide scanning overlay
     */
    fun hideScanningOverlay() {
        if (isOverlayVisible) {
            isOverlayVisible = false
            scanningOverlayView?.visibility = View.GONE
            saveSettings()
            Log.i(TAG, "Scanning overlay hidden")
        }
    }

    /**
     * Toggle scanning overlay visibility
     */
    fun toggleScanningOverlay() {
        if (isOverlayVisible) {
            hideScanningOverlay()
        } else {
            showScanningOverlay()
        }
    }

    /**
     * Set scanning mode
     */
    fun setScanningMode(mode: ScanningMode) {
        if (scanningMode != mode) {
            scanningMode = mode
            scanningOverlayView?.setScanningMode(mode)
            saveSettings()

            Log.i(TAG, "Scanning mode changed to: $mode")

            cameraContext?.debugLogger?.logPlugin(
                name,
                "scanning_mode_changed",
                mapOf("newMode" to mode.name)
            )
        }
    }

    /**
     * Set highlight color
     */
    fun setHighlightColor(color: Int) {
        if (highlightColor != color) {
            highlightColor = color
            scanningOverlayView?.setHighlightColor(color)
            saveSettings()

            Log.i(TAG, "Highlight color changed")
        }
    }

    override fun cleanup() {
        Log.i(TAG, "Cleaning up ScanningOverlayPlugin")

        hideScanningOverlay()
        scanningOverlayView = null
        cameraContext = null
    }

    private fun createScanningOverlay() {
        if (scanningOverlayView == null && cameraContext != null) {
            scanningOverlayView = ScanningOverlayView(cameraContext!!.context).apply {
                setScanningMode(scanningMode)
                setHighlightColor(highlightColor)
                visibility = View.VISIBLE
            }
        }
    }

    private fun loadSettings(context: CameraContext) {
        val settings = context.settingsManager

        try {
            isOverlayVisible = settings.getPluginSetting(name, "overlayVisible", "true").toBoolean()

            val modeString = settings.getPluginSetting(name, "scanningMode", ScanningMode.AUTO.name)
            scanningMode = ScanningMode.valueOf(modeString)

            highlightColor = settings.getPluginSetting(name, "highlightColor", Color.GREEN.toString()).toInt()

            Log.i(TAG, "Loaded settings: visible=$isOverlayVisible, mode=$scanningMode")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load settings, using defaults", e)
        }
    }

    private fun saveSettings() {
        val settings = cameraContext?.settingsManager ?: return

        settings.setPluginSetting(name, "overlayVisible", isOverlayVisible.toString())
        settings.setPluginSetting(name, "scanningMode", scanningMode.name)
        settings.setPluginSetting(name, "highlightColor", highlightColor.toString())
    }

    companion object {
        private const val TAG = "ScanningOverlayPlugin"
    }
}

/**
 * Custom view for scanning overlay with barcode highlighting
 */
class ScanningOverlayView(context: Context) : View(context) {

    private val paint = Paint().apply {
        color = Color.GREEN
        strokeWidth = 4f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 18f
        isAntiAlias = true
    }

    private var detectedBarcodes: List<DetectedBarcode> = emptyList()
    private var detectedQRCodes: List<QRScannerPlugin.QRCode> = emptyList()
    private var scanningMode: ScanningOverlayPlugin.ScanningMode = ScanningOverlayPlugin.ScanningMode.AUTO

    fun updateBarcodes(barcodes: List<DetectedBarcode>) {
        detectedBarcodes = barcodes
        invalidate()
    }

    fun updateQRCodes(qrCodes: List<QRScannerPlugin.QRCode>) {
        detectedQRCodes = qrCodes
        invalidate()
    }

    fun setScanningMode(mode: ScanningOverlayPlugin.ScanningMode) {
        scanningMode = mode
        invalidate()
    }

    fun setHighlightColor(color: Int) {
        paint.color = color
        invalidate()
    }

    fun showScanResult(result: String, action: String?) {
        Log.d("ScanningOverlayView", "Showing scan result: $result")
        // In production: show result UI
    }

    fun updateForCamera(camera: Camera) {
        // Update overlay for camera changes
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (scanningMode == ScanningOverlayPlugin.ScanningMode.OFF) {
            return
        }

        // Draw scanning frame
        drawScanningFrame(canvas)

        // Draw barcode highlights
        detectedBarcodes.forEach { barcode ->
            canvas.drawRect(barcode.boundingBox, paint)
        }

        // Draw scanning mode indicator
        drawModeIndicator(canvas)
    }

    private fun drawScanningFrame(canvas: Canvas) {
        val centerX = width / 2f
        val centerY = height / 2f
        val frameSize = 200f

        val frameRect = RectF(
            centerX - frameSize,
            centerY - frameSize,
            centerX + frameSize,
            centerY + frameSize
        )

        canvas.drawRect(frameRect, paint)

        // Draw corner indicators
        val cornerSize = 30f
        paint.strokeWidth = 6f

        // Top-left
        canvas.drawLine(frameRect.left, frameRect.top, frameRect.left + cornerSize, frameRect.top, paint)
        canvas.drawLine(frameRect.left, frameRect.top, frameRect.left, frameRect.top + cornerSize, paint)

        // Top-right
        canvas.drawLine(frameRect.right, frameRect.top, frameRect.right - cornerSize, frameRect.top, paint)
        canvas.drawLine(frameRect.right, frameRect.top, frameRect.right, frameRect.top + cornerSize, paint)

        // Bottom-left
        canvas.drawLine(frameRect.left, frameRect.bottom, frameRect.left + cornerSize, frameRect.bottom, paint)
        canvas.drawLine(frameRect.left, frameRect.bottom, frameRect.left, frameRect.bottom - cornerSize, paint)

        // Bottom-right
        canvas.drawLine(frameRect.right, frameRect.bottom, frameRect.right - cornerSize, frameRect.bottom, paint)
        canvas.drawLine(frameRect.right, frameRect.bottom, frameRect.right, frameRect.bottom - cornerSize, paint)

        paint.strokeWidth = 4f // Reset
    }

    private fun drawModeIndicator(canvas: Canvas) {
        val modeText = "SCAN: ${scanningMode.name}"
        canvas.drawText(modeText, 20f, 50f, textPaint)
    }
}