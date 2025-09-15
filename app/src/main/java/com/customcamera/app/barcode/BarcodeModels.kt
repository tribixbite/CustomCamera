package com.customcamera.app.barcode

import android.graphics.Point
import android.graphics.Rect

/**
 * Data models for barcode detection and processing
 */

/**
 * Represents a detected barcode with all relevant information
 */
data class DetectedBarcode(
    val data: String,
    val format: String,
    val boundingBox: Rect,
    val cornerPoints: Array<Point>,
    val detectionTimestamp: Long = System.currentTimeMillis(),
    val confidence: Float = 1.0f
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DetectedBarcode

        if (data != other.data) return false
        if (format != other.format) return false

        return true
    }

    override fun hashCode(): Int {
        var result = data.hashCode()
        result = 31 * result + format.hashCode()
        return result
    }

    override fun toString(): String {
        return "DetectedBarcode(data='$data', format='$format', bounds=$boundingBox)"
    }
}

/**
 * Barcode scanning configuration
 */
data class BarcodeScanConfig(
    val enabledFormats: Set<String> = setOf(
        "QR_CODE",
        "CODE_128",
        "CODE_39",
        "EAN_13",
        "EAN_8",
        "UPC_A",
        "UPC_E",
        "DATA_MATRIX",
        "PDF417"
    ),
    val autoActionEnabled: Boolean = true,
    val highlightingEnabled: Boolean = true,
    val scanningTimeout: Long = 5000L,
    val minimumConfidence: Float = 0.5f
)

/**
 * Barcode scanning statistics
 */
data class BarcodeScanStats(
    val totalScanned: Int,
    val scanningHistory: List<DetectedBarcode>,
    val formatCounts: Map<String, Int>,
    val averageConfidence: Float,
    val scanningTimeMs: Long
)