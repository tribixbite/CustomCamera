package com.customcamera.app.plugins

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.ImageProxy
import com.customcamera.app.engine.CameraContext
import com.customcamera.app.engine.plugins.ProcessingPlugin
import com.customcamera.app.engine.plugins.ProcessingResult
import com.customcamera.app.engine.plugins.ProcessingMetadata
import com.customcamera.app.barcode.DetectedBarcode
import java.util.regex.Pattern

/**
 * QRScannerPlugin provides specialized QR code handling
 * with content parsing and automatic actions.
 */
class QRScannerPlugin : ProcessingPlugin() {

    override val name: String = "QRScanner"
    override val version: String = "1.0.0"
    override val priority: Int = 35 // Higher priority than general barcode

    private var cameraContext: CameraContext? = null

    // QR scanning configuration
    private var autoActionEnabled: Boolean = true
    private var qrAutoScanEnabled: Boolean = true
    private var processingInterval: Long = 200L // Process every 200ms
    private var lastProcessingTime: Long = 0L

    // QR detection state
    private var detectedQRCodes: List<QRCode> = emptyList()
    private var qrHistory: MutableList<QRCode> = mutableListOf()

    data class QRCode(
        val rawData: String,
        val contentType: QRContentType,
        val parsedContent: Map<String, String>,
        val detectionTimestamp: Long = System.currentTimeMillis(),
        val boundingBox: android.graphics.Rect? = null
    )

    enum class QRContentType {
        URL, WIFI, CONTACT, TEXT, EMAIL, PHONE, SMS, GEO_LOCATION, UNKNOWN
    }

    override suspend fun initialize(context: CameraContext) {
        this.cameraContext = context
        Log.i(TAG, "QRScannerPlugin initialized")

        loadSettings(context)

        context.debugLogger.logPlugin(
            name,
            "initialized",
            mapOf(
                "autoActionEnabled" to autoActionEnabled,
                "qrAutoScanEnabled" to qrAutoScanEnabled,
                "processingInterval" to processingInterval
            )
        )
    }

    override suspend fun onCameraReady(camera: Camera) {
        Log.i(TAG, "Camera ready for QR code scanning")

        cameraContext?.debugLogger?.logPlugin(
            name,
            "camera_ready",
            mapOf("qrScanningEnabled" to isEnabled)
        )
    }

    override suspend fun onCameraReleased(camera: Camera) {
        Log.i(TAG, "Camera released, stopping QR detection")
        clearDetectedQRCodes()
    }

    override suspend fun processFrame(image: ImageProxy): ProcessingResult {
        val currentTime = System.currentTimeMillis()

        // Throttle processing
        if (currentTime - lastProcessingTime < processingInterval) {
            return ProcessingResult.Skip
        }

        lastProcessingTime = currentTime

        return try {
            // Simulate QR detection (replace with actual ML Kit implementation)
            val qrCodes = simulateQRDetection(image)

            if (qrCodes.isNotEmpty()) {
                detectedQRCodes = qrCodes

                // Process QR codes for auto-actions
                qrCodes.forEach { qr ->
                    processQRContent(qr)
                    addToHistory(qr)
                }

                Log.i(TAG, "Detected ${qrCodes.size} QR code(s)")

                cameraContext?.debugLogger?.logPlugin(
                    name,
                    "qr_codes_detected",
                    mapOf(
                        "count" to qrCodes.size,
                        "types" to qrCodes.map { it.contentType.name }
                    )
                )
            }

            val metadata = ProcessingMetadata(
                timestamp = currentTime,
                processingTimeMs = System.currentTimeMillis() - currentTime,
                frameNumber = 0L,
                imageSize = android.util.Size(image.width, image.height),
                additionalData = mapOf(
                    "qrCodesDetected" to qrCodes.size,
                    "autoActionEnabled" to autoActionEnabled
                )
            )

            ProcessingResult.Success(
                data = mapOf(
                    "qrCodes" to qrCodes,
                    "detectionCount" to qrCodes.size
                ),
                metadata = metadata
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error processing frame for QR codes", e)
            ProcessingResult.Failure("QR processing error: ${e.message}", e)
        }
    }

    /**
     * QR code content parsing (URLs, WiFi, contacts, text)
     */
    fun parseQRContent(rawData: String): QRCode {
        val contentType = determineContentType(rawData)
        val parsedContent = when (contentType) {
            QRContentType.URL -> parseURL(rawData)
            QRContentType.WIFI -> parseWiFi(rawData)
            QRContentType.CONTACT -> parseContact(rawData)
            QRContentType.EMAIL -> parseEmail(rawData)
            QRContentType.PHONE -> parsePhone(rawData)
            QRContentType.SMS -> parseSMS(rawData)
            QRContentType.GEO_LOCATION -> parseGeoLocation(rawData)
            else -> mapOf("text" to rawData)
        }

        return QRCode(
            rawData = rawData,
            contentType = contentType,
            parsedContent = parsedContent
        )
    }

    /**
     * Automatic action suggestions based on QR content
     */
    fun processQRContent(qrCode: QRCode) {
        if (!autoActionEnabled) return

        Log.i(TAG, "Processing QR content: ${qrCode.contentType}")

        when (qrCode.contentType) {
            QRContentType.URL -> {
                val url = qrCode.parsedContent["url"]
                if (url != null) {
                    suggestOpenURL(url)
                }
            }
            QRContentType.WIFI -> {
                val ssid = qrCode.parsedContent["ssid"]
                val password = qrCode.parsedContent["password"]
                if (ssid != null) {
                    suggestConnectWiFi(ssid, password)
                }
            }
            QRContentType.CONTACT -> {
                val name = qrCode.parsedContent["name"]
                if (name != null) {
                    suggestSaveContact(qrCode.parsedContent)
                }
            }
            QRContentType.PHONE -> {
                val number = qrCode.parsedContent["number"]
                if (number != null) {
                    suggestCallNumber(number)
                }
            }
            else -> {
                Log.d(TAG, "No auto-action for content type: ${qrCode.contentType}")
            }
        }

        cameraContext?.debugLogger?.logPlugin(
            name,
            "qr_content_processed",
            mapOf(
                "contentType" to qrCode.contentType.name,
                "autoActionTriggered" to autoActionEnabled
            )
        )
    }

    /**
     * QR code generation functionality
     */
    fun generateQRCode(content: String, contentType: QRContentType): ByteArray? {
        // In production, this would use a QR code generation library
        Log.i(TAG, "Generating QR code for: $contentType")

        return try {
            // Simulated QR generation
            // Real implementation would use libraries like ZXing
            Log.i(TAG, "QR code generated successfully")
            ByteArray(0) // Placeholder
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate QR code", e)
            null
        }
    }

    /**
     * Get QR scanning history
     */
    fun getQRHistory(): List<QRCode> = qrHistory.toList()

    /**
     * Clear QR scanning history
     */
    fun clearQRHistory() {
        qrHistory.clear()
        Log.i(TAG, "QR history cleared")
    }

    /**
     * Enable or disable auto actions
     */
    fun setAutoActionEnabled(enabled: Boolean) {
        if (autoActionEnabled != enabled) {
            autoActionEnabled = enabled
            saveSettings()
            Log.i(TAG, "QR auto actions ${if (enabled) "enabled" else "disabled"}")
        }
    }

    override fun cleanup() {
        Log.i(TAG, "Cleaning up QRScannerPlugin")

        clearDetectedQRCodes()
        qrHistory.clear()
        cameraContext = null
    }

    private fun simulateQRDetection(image: ImageProxy): List<QRCode> {
        // Simulate QR detection - replace with actual ML Kit
        return if (System.currentTimeMillis() % 8000 < 1500) {
            listOf(
                parseQRContent("https://github.com/tribixbite/CustomCamera"),
                parseQRContent("WIFI:T:WPA;S:MyNetwork;P:password123;;")
            )
        } else {
            emptyList()
        }
    }

    private fun determineContentType(data: String): QRContentType {
        return when {
            data.startsWith("http://") || data.startsWith("https://") -> QRContentType.URL
            data.startsWith("WIFI:") -> QRContentType.WIFI
            data.startsWith("BEGIN:VCARD") -> QRContentType.CONTACT
            data.startsWith("mailto:") -> QRContentType.EMAIL
            data.startsWith("tel:") -> QRContentType.PHONE
            data.startsWith("sms:") -> QRContentType.SMS
            data.startsWith("geo:") -> QRContentType.GEO_LOCATION
            data.matches(Regex("\\+?\\d{10,}")) -> QRContentType.PHONE
            else -> QRContentType.TEXT
        }
    }

    private fun parseURL(data: String): Map<String, String> = mapOf("url" to data)

    private fun parseWiFi(data: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val parts = data.removePrefix("WIFI:").split(";")

        parts.forEach { part ->
            when {
                part.startsWith("S:") -> result["ssid"] = part.removePrefix("S:")
                part.startsWith("P:") -> result["password"] = part.removePrefix("P:")
                part.startsWith("T:") -> result["security"] = part.removePrefix("T:")
            }
        }

        return result
    }

    private fun parseContact(data: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        // Basic vCard parsing
        data.lines().forEach { line ->
            when {
                line.startsWith("FN:") -> result["name"] = line.removePrefix("FN:")
                line.startsWith("TEL:") -> result["phone"] = line.removePrefix("TEL:")
                line.startsWith("EMAIL:") -> result["email"] = line.removePrefix("EMAIL:")
            }
        }
        return result
    }

    private fun parseEmail(data: String): Map<String, String> = mapOf("email" to data.removePrefix("mailto:"))
    private fun parsePhone(data: String): Map<String, String> = mapOf("number" to data.removePrefix("tel:"))
    private fun parseSMS(data: String): Map<String, String> = mapOf("number" to data.removePrefix("sms:"))
    private fun parseGeoLocation(data: String): Map<String, String> {
        val coords = data.removePrefix("geo:").split(",")
        return if (coords.size >= 2) {
            mapOf("latitude" to coords[0], "longitude" to coords[1])
        } else {
            mapOf("location" to data)
        }
    }

    private fun suggestOpenURL(url: String) {
        Log.i(TAG, "Suggesting URL open: $url")
        // In production: show user prompt to open URL
    }

    private fun suggestConnectWiFi(ssid: String, password: String?) {
        Log.i(TAG, "Suggesting WiFi connection: $ssid")
        // In production: show user prompt to connect to WiFi
    }

    private fun suggestSaveContact(contactData: Map<String, String>) {
        Log.i(TAG, "Suggesting save contact: ${contactData["name"]}")
        // In production: show user prompt to save contact
    }

    private fun suggestCallNumber(number: String) {
        Log.i(TAG, "Suggesting call number: $number")
        // In production: show user prompt to call number
    }

    private fun addToHistory(qrCode: QRCode) {
        if (!qrHistory.any { it.rawData == qrCode.rawData }) {
            qrHistory.add(qrCode)
            if (qrHistory.size > 100) {
                qrHistory.removeAt(0) // Keep last 100
            }
        }
    }

    private fun clearDetectedQRCodes() {
        detectedQRCodes = emptyList()
    }

    private fun loadSettings(context: CameraContext) {
        val settings = context.settingsManager

        try {
            autoActionEnabled = settings.getPluginSetting(name, "autoActionEnabled", "true").toBoolean()
            qrAutoScanEnabled = settings.getPluginSetting(name, "qrAutoScanEnabled", "true").toBoolean()
            processingInterval = settings.getPluginSetting(name, "processingInterval", "200").toLong()

            Log.i(TAG, "Loaded settings: autoAction=$autoActionEnabled, autoScan=$qrAutoScanEnabled")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load settings, using defaults", e)
        }
    }

    private fun saveSettings() {
        val settings = cameraContext?.settingsManager ?: return

        settings.setPluginSetting(name, "autoActionEnabled", autoActionEnabled.toString())
        settings.setPluginSetting(name, "qrAutoScanEnabled", qrAutoScanEnabled.toString())
        settings.setPluginSetting(name, "processingInterval", processingInterval.toString())
    }

    companion object {
        private const val TAG = "QRScannerPlugin"
    }
}