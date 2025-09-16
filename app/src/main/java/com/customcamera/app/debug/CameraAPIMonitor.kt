package com.customcamera.app.debug

import android.util.Log
import androidx.camera.core.UseCase
import com.customcamera.app.engine.CameraContext
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Enhanced CameraAPIMonitor provides comprehensive monitoring
 * of camera API calls and operations.
 */
class CameraAPIMonitor(
    private val cameraContext: CameraContext
) {

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

        cameraContext.debugLogger.logCameraAPI(
            "CameraProvider.$method",
            params
        )
    }

    /**
     * Log camera binding operations
     */
    fun logCameraBinding(cameraId: String, useCases: List<UseCase>) {
        val startTime = System.currentTimeMillis()

        val params = mapOf(
            "cameraId" to cameraId,
            "useCaseCount" to useCases.size,
            "useCases" to useCases.map { it.javaClass.simpleName }
        )

        val call = APICall(
            timestamp = startTime,
            method = "bindToLifecycle",
            params = params
        )

        addAPICall(call)

        Log.i(TAG, "🔗 Camera binding - ID: $cameraId, UseCases: ${useCases.size}")

        cameraContext.debugLogger.logCameraBinding(
            cameraId,
            com.customcamera.app.engine.BindingResult(
                success = true,
                useCases = useCases.map { it.javaClass.simpleName }
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

        cameraContext.debugLogger.logCameraAPI(
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

        cameraContext.debugLogger.logCameraAPI(
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

        cameraContext.debugLogger.logPerformance(
            "Frame processing tracked",
            0L, // Duration tracked elsewhere
            mapOf("timestamp" to System.currentTimeMillis())
        )
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