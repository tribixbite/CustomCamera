package com.customcamera.app.debug

import android.util.Log
import androidx.camera.core.UseCase
import com.customcamera.app.engine.CameraContext
import java.lang.ref.WeakReference
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Enhanced CameraAPIMonitor provides comprehensive monitoring
 * of camera API calls and operations.
 */
class CameraAPIMonitor(
    cameraContext: CameraContext
) {
    private val cameraContextRef = WeakReference(cameraContext)

    private val apiCallHistory = ConcurrentLinkedQueue<APICall>()
    private val maxHistorySize = 500
    private val timestampFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    data class APICall(
        val timestamp: Long,
        val method: String,
        val params: Map<String, Any>,
        val result: String? = null,
        val duration: Long = 0L,
        val success: Boolean = true
    )

    /**
     * Log camera provider calls
     */
    fun logCameraProviderCall(method: String, params: Map<String, Any>) {
        val call = APICall(
            timestamp = System.currentTimeMillis(),
            method = "CameraProvider.$method",
            params = params
        )

        addAPICall(call)

        Log.d(TAG, "📡 CameraProvider.$method - ${formatParams(params)}")

        cameraContextRef.get()?.debugLogger?.logCameraAPI(
            "CameraProvider.$method",
            params
        )
    }

    /**
     * Log camera binding operations
     */
    fun logCameraBinding(cameraId: String, useCases: List<UseCase>, success: Boolean = true, error: String? = null) {
        val startTime = System.currentTimeMillis()

        val params = mutableMapOf<String, Any>(
            "cameraId" to cameraId,
            "useCaseCount" to useCases.size,
            "useCases" to useCases.map { it.javaClass.simpleName }
        )

        if (error != null) {
            params["error"] = error
        }

        val call = APICall(
            timestamp = startTime,
            method = "bindToLifecycle",
            params = params,
            success = success
        )

        addAPICall(call)

        if (success) {
            Log.i(TAG, "🔗 Camera binding SUCCESS - ID: $cameraId, UseCases: ${useCases.size}")
        } else {
            Log.e(TAG, "❌ Camera binding FAILED - ID: $cameraId, Error: $error")
        }

        cameraContextRef.get()?.debugLogger?.logCameraBinding(
            cameraId,
            com.customcamera.app.engine.BindingResult(
                success = success,
                useCases = useCases.map { it.javaClass.simpleName },
                error = error
            )
        )
    }

    /**
     * Log camera control actions
     */
    fun logCameraControl(action: String, params: Map<String, Any>) {
        val call = APICall(
            timestamp = System.currentTimeMillis(),
            method = "CameraControl.$action",
            params = params
        )

        addAPICall(call)

        Log.d(TAG, "🎛️ CameraControl.$action - ${formatParams(params)}")

        cameraContextRef.get()?.debugLogger?.logCameraAPI(
            "CameraControl.$action",
            params
        )
    }

    /**
     * Log camera characteristics
     */
    fun logCameraCharacteristics(cameraId: String) {
        val call = APICall(
            timestamp = System.currentTimeMillis(),
            method = "getCameraCharacteristics",
            params = mapOf("cameraId" to cameraId)
        )

        addAPICall(call)

        Log.i(TAG, "📋 Camera characteristics requested for: $cameraId")

        cameraContextRef.get()?.debugLogger?.logCameraAPI(
            "getCameraCharacteristics",
            mapOf("cameraId" to cameraId)
        )
    }

    /**
     * Track frame processing pipeline
     */
    fun trackFrameProcessing() {
        val call = APICall(
            timestamp = System.currentTimeMillis(),
            method = "frameProcessing",
            params = mapOf("pipeline" to "active")
        )

        addAPICall(call)

        cameraContextRef.get()?.debugLogger?.logPerformance(
            "Frame processing tracked",
            0L, // Duration tracked elsewhere
            mapOf("timestamp" to System.currentTimeMillis())
        )
    }

    /**
     * Log preview surface provider state
     */
    fun logPreviewState(cameraId: String, state: String, details: Map<String, Any> = emptyMap()) {
        val params = mutableMapOf<String, Any>(
            "cameraId" to cameraId,
            "state" to state
        )
        params.putAll(details)

        val call = APICall(
            timestamp = System.currentTimeMillis(),
            method = "Preview.$state",
            params = params,
            success = state != "ERROR"
        )

        addAPICall(call)

        Log.d(TAG, "📺 Preview.$state - Camera: $cameraId, Details: $details")

        cameraContextRef.get()?.debugLogger?.logCameraAPI(
            "Preview.$state",
            params
        )
    }

    /**
     * Log camera state changes
     */
    fun logCameraState(cameraId: String, state: String, details: Map<String, Any> = emptyMap()) {
        val params = mutableMapOf<String, Any>(
            "cameraId" to cameraId,
            "state" to state
        )
        params.putAll(details)

        val call = APICall(
            timestamp = System.currentTimeMillis(),
            method = "CameraState.$state",
            params = params,
            success = state !in listOf("ERROR", "CLOSED")
        )

        addAPICall(call)

        val emoji = when (state) {
            "OPEN" -> "✅"
            "CLOSED" -> "⏸️"
            "OPENING" -> "⏳"
            "CLOSING" -> "⏳"
            "ERROR" -> "❌"
            else -> "📸"
        }

        Log.i(TAG, "$emoji CameraState.$state - Camera: $cameraId, Details: $details")

        cameraContextRef.get()?.debugLogger?.logCameraAPI(
            "CameraState.$state",
            params
        )
    }

    /**
     * Log camera errors
     */
    fun logError(cameraId: String, operation: String, error: Throwable) {
        val params = mapOf(
            "cameraId" to cameraId,
            "operation" to operation,
            "errorType" to error.javaClass.simpleName,
            "errorMessage" to (error.message ?: "Unknown error"),
            "stackTrace" to error.stackTrace.take(5).joinToString("\n") { "  at $it" }
        )

        val call = APICall(
            timestamp = System.currentTimeMillis(),
            method = "ERROR.$operation",
            params = params,
            success = false
        )

        addAPICall(call)

        Log.e(TAG, "❌ Camera error in $operation - Camera: $cameraId", error)

        cameraContextRef.get()?.debugLogger?.logError(
            "Camera $operation failed",
            error,
            params
        )
    }

    /**
     * Log frame delivery
     */
    fun logFrameDelivery(cameraId: String, frameNumber: Int, timestamp: Long) {
        // Only log every 30th frame to avoid spam
        if (frameNumber % 30 == 0) {
            val params = mapOf(
                "cameraId" to cameraId,
                "frameNumber" to frameNumber,
                "timestamp" to timestamp
            )

            val call = APICall(
                timestamp = System.currentTimeMillis(),
                method = "frameDelivered",
                params = params
            )

            addAPICall(call)

            Log.v(TAG, "🎞️ Frame delivered - Camera: $cameraId, Frame: $frameNumber")
        }
    }

    /**
     * Generate comprehensive debug report
     */
    fun generateDebugReport(): String {
        val recentCalls = apiCallHistory.toList().takeLast(50)

        return buildString {
            appendLine("=== Camera API Monitor Debug Report ===")
            appendLine("Generated: ${timestampFormat.format(Date())}")
            appendLine("Total API calls tracked: ${apiCallHistory.size}")
            appendLine("Recent calls (last 50):")
            appendLine()

            recentCalls.forEach { call ->
                appendLine("${formatTimestamp(call.timestamp)} - ${call.method}")
                if (call.params.isNotEmpty()) {
                    call.params.forEach { entry ->
                        appendLine("  ${entry.key}: ${entry.value}")
                    }
                }
                if (call.result != null) {
                    appendLine("  Result: ${call.result}")
                }
                if (call.duration > 0) {
                    appendLine("  Duration: ${call.duration}ms")
                }
                appendLine("  Success: ${call.success}")
                appendLine()
            }

            // Add summary statistics
            appendLine("=== API Call Statistics ===")
            val methodCounts = recentCalls.groupBy { it.method }.mapValues { it.value.size }
            methodCounts.forEach { entry ->
                appendLine("${entry.key}: ${entry.value} calls")
            }

            val failedCalls = recentCalls.count { !it.success }
            appendLine("Failed calls: $failedCalls")

            val averageDuration = recentCalls.filter { it.duration > 0 }.map { it.duration }.average()
            if (!averageDuration.isNaN()) {
                appendLine("Average call duration: ${String.format("%.1f", averageDuration)}ms")
            }
        }
    }

    /**
     * Get API call statistics
     */
    fun getAPICallStats(): Map<String, Any> {
        val calls = apiCallHistory.toList()

        val methodCounts = calls.groupBy { it.method }.mapValues { it.value.size }
        val failedCalls = calls.count { !it.success }
        val successRate = if (calls.isNotEmpty()) {
            ((calls.size - failedCalls).toFloat() / calls.size) * 100f
        } else {
            0f
        }

        return mapOf(
            "totalCalls" to calls.size,
            "failedCalls" to failedCalls,
            "successRate" to successRate,
            "methodCounts" to methodCounts,
            "oldestCall" to (calls.minByOrNull { it.timestamp }?.timestamp ?: 0L),
            "newestCall" to (calls.maxByOrNull { it.timestamp }?.timestamp ?: 0L)
        )
    }

    /**
     * Clear API call history
     */
    fun clearAPICallHistory() {
        apiCallHistory.clear()
        Log.i(TAG, "API call history cleared")
    }

    /**
     * Get recent API calls
     */
    fun getRecentAPICalls(limit: Int = 20): List<APICall> {
        return apiCallHistory.toList().takeLast(limit)
    }

    private fun addAPICall(call: APICall) {
        apiCallHistory.offer(call)

        // Maintain history size limit
        while (apiCallHistory.size > maxHistorySize) {
            apiCallHistory.poll()
        }
    }

    private fun formatParams(params: Map<String, Any>): String {
        if (params.isEmpty()) return ""
        return params.entries.joinToString(", ") { "${it.key}=${it.value}" }
    }

    private fun formatTimestamp(timestamp: Long): String {
        return timestampFormat.format(Date(timestamp))
    }

    companion object {
        private const val TAG = "CameraAPIMonitor"
    }
}