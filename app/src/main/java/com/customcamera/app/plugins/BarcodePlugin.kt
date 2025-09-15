package com.customcamera.app.plugins

import android.graphics.*
import android.util.Log
import android.view.View
import androidx.camera.core.Camera
import androidx.camera.core.ImageProxy
import com.customcamera.app.engine.CameraContext
import com.customcamera.app.engine.plugins.ProcessingPlugin
import com.customcamera.app.engine.plugins.ProcessingResult
import com.customcamera.app.engine.plugins.ProcessingMetadata
import com.customcamera.app.barcode.BarcodeOverlayView
import com.customcamera.app.barcode.DetectedBarcode

/**
 * BarcodePlugin provides ML Kit barcode scanning functionality
 * with real-time detection and highlighting.
 */
class BarcodePlugin : ProcessingPlugin() {

    override val name: String = "Barcode"
    override val version: String = "1.0.0"
    override val priority: Int = 40 // Medium priority for processing

    private var cameraContext: CameraContext? = null
    private var barcodeOverlay: BarcodeOverlayView? = null

    // Scanning configuration
    private var isAutoScanEnabled: Boolean = true
    private var highlightDetectedCodes: Boolean = true
    private var processingInterval: Long = 100L // Process every 100ms
    private var lastProcessingTime: Long = 0L

    // Detection state
    private var detectedBarcodes: List<DetectedBarcode> = emptyList()
    private var scanningHistory: MutableList<DetectedBarcode> = mutableListOf()

    override suspend fun initialize(context: CameraContext) {
        this.cameraContext = context
        Log.i(TAG, "BarcodePlugin initialized")

        // Note: In production, you'd initialize ML Kit here:
        // scanner = BarcodeScanning.getClient()

        loadSettings(context)

        context.debugLogger.logPlugin(
            name,
            "initialized",
            mapOf(
                "autoScanEnabled" to isAutoScanEnabled,
                "highlightEnabled" to highlightDetectedCodes,
                "processingInterval" to processingInterval
            )
        )
    }

    override suspend fun onCameraReady(camera: Camera) {
        Log.i(TAG, "Camera ready for barcode scanning")

        if (highlightDetectedCodes) {
            // Create barcode overlay for highlighting
            barcodeOverlay = BarcodeOverlayView(cameraContext!!.context)
        }

        cameraContext?.debugLogger?.logPlugin(
            name,
            "camera_ready",
            mapOf("overlayCreated" to (barcodeOverlay != null))
        )
    }

    override suspend fun onCameraReleased(camera: Camera) {
        Log.i(TAG, "Camera released, stopping barcode detection")
        clearDetectedBarcodes()
    }

    override suspend fun processFrame(image: ImageProxy): ProcessingResult {
        val currentTime = System.currentTimeMillis()

        // Throttle processing to avoid performance impact
        if (currentTime - lastProcessingTime < processingInterval) {
            return ProcessingResult.Skip
        }

        lastProcessingTime = currentTime

        return try {
            // Simulate barcode detection (replace with actual ML Kit implementation)
            val barcodes = simulateBarcodeDetection(image)

            if (barcodes.isNotEmpty()) {
                detectedBarcodes = barcodes
                updateBarcodeOverlay()

                // Add to history
                barcodes.forEach { barcode ->
                    if (!scanningHistory.any { it.data == barcode.data }) {
                        scanningHistory.add(barcode)
                        if (scanningHistory.size > 50) {
                            scanningHistory.removeAt(0) // Keep last 50
                        }
                    }
                }

                Log.i(TAG, "Detected ${barcodes.size} barcode(s)")

                cameraContext?.debugLogger?.logPlugin(
                    name,
                    "barcodes_detected",
                    mapOf(
                        "count" to barcodes.size,
                        "types" to barcodes.map { it.format }.distinct()
                    )
                )
            }

            val metadata = ProcessingMetadata(
                timestamp = currentTime,
                processingTimeMs = System.currentTimeMillis() - currentTime,
                frameNumber = 0L,
                imageSize = android.util.Size(image.width, image.height),
                additionalData = mapOf(
                    "barcodesDetected" to barcodes.size,
                    "autoScanEnabled" to isAutoScanEnabled
                )
            )

            ProcessingResult.Success(
                data = mapOf(
                    "barcodes" to barcodes,
                    "detectionCount" to barcodes.size
                ),
                metadata = metadata
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error processing frame for barcodes", e)
            ProcessingResult.Failure("Barcode processing error: ${e.message}", e)
        }
    }

    /**
     * Highlight detected codes on overlay
     */
    fun highlightDetectedCodes(barcodes: List<DetectedBarcode>) {
        if (!highlightDetectedCodes) return

        barcodeOverlay?.updateBarcodes(barcodes)
        Log.d(TAG, "Highlighted ${barcodes.size} detected barcodes")
    }

    /**
     * Enable or disable auto scanning
     */
    fun setAutoScanEnabled(enabled: Boolean) {
        if (isAutoScanEnabled != enabled) {
            isAutoScanEnabled = enabled
            saveSettings()

            Log.i(TAG, "Auto scan ${if (enabled) "enabled" else "disabled"}")

            cameraContext?.debugLogger?.logPlugin(
                name,
                "auto_scan_toggled",
                mapOf("enabled" to enabled)
            )
        }
    }

    /**
     * Enable or disable barcode highlighting
     */
    fun setHighlightingEnabled(enabled: Boolean) {
        if (highlightDetectedCodes != enabled) {
            highlightDetectedCodes = enabled

            if (!enabled) {
                clearDetectedBarcodes()
            }

            saveSettings()
            Log.i(TAG, "Barcode highlighting ${if (enabled) "enabled" else "disabled"}")
        }
    }

    /**
     * Set processing interval
     */
    fun setProcessingInterval(intervalMs: Long) {
        if (intervalMs > 0 && processingInterval != intervalMs) {
            processingInterval = intervalMs
            saveSettings()
            Log.i(TAG, "Processing interval set to: ${intervalMs}ms")
        }
    }

    /**
     * Get scanning history
     */
    fun getScanningHistory(): List<DetectedBarcode> {
        return scanningHistory.toList()
    }

    /**
     * Clear scanning history
     */
    fun clearScanningHistory() {
        scanningHistory.clear()
        Log.i(TAG, "Scanning history cleared")

        cameraContext?.debugLogger?.logPlugin(
            name,
            "history_cleared",
            emptyMap()
        )
    }

    /**
     * Get current detection statistics
     */
    fun getDetectionStats(): Map<String, Any> {
        return mapOf(
            "currentDetections" to detectedBarcodes.size,
            "historyCount" to scanningHistory.size,
            "autoScanEnabled" to isAutoScanEnabled,
            "highlightingEnabled" to highlightDetectedCodes,
            "processingInterval" to processingInterval,
            "lastProcessingTime" to lastProcessingTime
        )
    }

    override fun cleanup() {
        Log.i(TAG, "Cleaning up BarcodePlugin")

        clearDetectedBarcodes()
        barcodeOverlay = null
        scanningHistory.clear()
        cameraContext = null

        // In production: scanner?.close()
    }

    private fun simulateBarcodeDetection(image: ImageProxy): List<DetectedBarcode> {
        // This simulates barcode detection
        // In production, replace with actual ML Kit BarcodeScanning

        return if (System.currentTimeMillis() % 5000 < 1000) {
            // Simulate occasional barcode detection
            listOf(
                DetectedBarcode(
                    data = "https://github.com/tribixbite/CustomCamera",
                    format = "QR_CODE",
                    boundingBox = Rect(100, 100, 300, 300),
                    cornerPoints = arrayOf(
                        Point(100, 100), Point(300, 100),
                        Point(300, 300), Point(100, 300)
                    )
                )
            )
        } else {
            emptyList()
        }
    }

    private fun updateBarcodeOverlay() {
        if (highlightDetectedCodes && detectedBarcodes.isNotEmpty()) {
            highlightDetectedCodes(detectedBarcodes)
        }
    }

    private fun clearDetectedBarcodes() {
        detectedBarcodes = emptyList()
        barcodeOverlay?.clearBarcodes()
    }

    private fun loadSettings(context: CameraContext) {
        val settings = context.settingsManager

        try {
            isAutoScanEnabled = settings.getPluginSetting(name, "autoScanEnabled", "true").toBoolean()
            highlightDetectedCodes = settings.getPluginSetting(name, "highlightEnabled", "true").toBoolean()
            processingInterval = settings.getPluginSetting(name, "processingInterval", "100").toLong()

            Log.i(TAG, "Loaded settings: autoScan=$isAutoScanEnabled, highlight=$highlightDetectedCodes, interval=${processingInterval}ms")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load settings, using defaults", e)
        }
    }

    private fun saveSettings() {
        val settings = cameraContext?.settingsManager ?: return

        settings.setPluginSetting(name, "autoScanEnabled", isAutoScanEnabled.toString())
        settings.setPluginSetting(name, "highlightEnabled", highlightDetectedCodes.toString())
        settings.setPluginSetting(name, "processingInterval", processingInterval.toString())
    }

    companion object {
        private const val TAG = "BarcodePlugin"
    }
}