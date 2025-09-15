package com.customcamera.app.engine

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Comprehensive logging system for camera operations and debugging.
 * Provides structured logging with filtering, export capabilities, and real-time monitoring.
 */
class DebugLogger {

    private val logEntries = ConcurrentLinkedQueue<LogEntry>()
    private val maxLogEntries = 1000 // Keep last 1000 entries in memory

    private val _logCount = MutableStateFlow(0)
    val logCount: StateFlow<Int> = _logCount.asStateFlow()

    private val timestampFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

    /**
     * Log camera API operations with detailed parameters
     */
    fun logCameraAPI(action: String, details: Map<String, Any> = emptyMap()) {
        val entry = LogEntry(
            timestamp = System.currentTimeMillis(),
            level = LogLevel.INFO,
            category = LogCategory.CAMERA_API,
            message = action,
            details = details,
            tag = "CameraAPI"
        )

        addLogEntry(entry)
        Log.i("CameraAPI", "$action - ${formatDetails(details)}")
    }

    /**
     * Log camera binding operations with results
     */
    fun logCameraBinding(cameraId: String, result: BindingResult) {
        val details = mapOf(
            "cameraId" to cameraId,
            "success" to result.success,
            "useCases" to result.useCases,
            "error" to (result.error ?: "none")
        )

        val entry = LogEntry(
            timestamp = System.currentTimeMillis(),
            level = if (result.success) LogLevel.INFO else LogLevel.ERROR,
            category = LogCategory.CAMERA_BINDING,
            message = "Camera binding ${if (result.success) "succeeded" else "failed"} for camera $cameraId",
            details = details,
            tag = "CameraBinding"
        )

        addLogEntry(entry)
        val logLevel = if (result.success) Log.INFO else Log.ERROR
        Log.println(logLevel, "CameraBinding", entry.message + " - ${formatDetails(details)}")
    }

    /**
     * Log plugin operations and lifecycle events
     */
    fun logPlugin(pluginName: String, operation: String, details: Map<String, Any> = emptyMap()) {
        val entry = LogEntry(
            timestamp = System.currentTimeMillis(),
            level = LogLevel.DEBUG,
            category = LogCategory.PLUGIN,
            message = "Plugin '$pluginName': $operation",
            details = details,
            tag = "Plugin"
        )

        addLogEntry(entry)
        Log.d("Plugin", entry.message + " - ${formatDetails(details)}")
    }

    /**
     * Log performance metrics and timing information
     */
    fun logPerformance(operation: String, durationMs: Long, details: Map<String, Any> = emptyMap()) {
        val allDetails = details.toMutableMap().apply {
            put("durationMs", durationMs)
        }

        val entry = LogEntry(
            timestamp = System.currentTimeMillis(),
            level = if (durationMs > 100) LogLevel.WARN else LogLevel.DEBUG,
            category = LogCategory.PERFORMANCE,
            message = "Performance: $operation took ${durationMs}ms",
            details = allDetails,
            tag = "Performance"
        )

        addLogEntry(entry)
        val logLevel = if (durationMs > 100) Log.WARN else Log.DEBUG
        Log.println(logLevel, "Performance", entry.message + " - ${formatDetails(allDetails)}")
    }

    /**
     * Log errors with context and stack traces
     */
    fun logError(message: String, exception: Throwable? = null, details: Map<String, Any> = emptyMap()) {
        val allDetails = details.toMutableMap().apply {
            exception?.let {
                put("exception", it.javaClass.simpleName)
                put("exceptionMessage", it.message ?: "No message")
            }
        }

        val entry = LogEntry(
            timestamp = System.currentTimeMillis(),
            level = LogLevel.ERROR,
            category = LogCategory.ERROR,
            message = message,
            details = allDetails,
            tag = "Error",
            exception = exception
        )

        addLogEntry(entry)
        Log.e("Error", message + " - ${formatDetails(allDetails)}", exception)
    }

    /**
     * Log general information messages
     */
    fun logInfo(message: String, details: Map<String, Any> = emptyMap(), tag: String = "Info") {
        val entry = LogEntry(
            timestamp = System.currentTimeMillis(),
            level = LogLevel.INFO,
            category = LogCategory.GENERAL,
            message = message,
            details = details,
            tag = tag
        )

        addLogEntry(entry)
        Log.i(tag, message + " - ${formatDetails(details)}")
    }

    /**
     * Log debug information (only shown when debug logging enabled)
     */
    fun logDebug(message: String, details: Map<String, Any> = emptyMap(), tag: String = "Debug") {
        val entry = LogEntry(
            timestamp = System.currentTimeMillis(),
            level = LogLevel.DEBUG,
            category = LogCategory.GENERAL,
            message = message,
            details = details,
            tag = tag
        )

        addLogEntry(entry)
        Log.d(tag, message + " - ${formatDetails(details)}")
    }

    /**
     * Get filtered log entries
     */
    fun getLogEntries(
        level: LogLevel? = null,
        category: LogCategory? = null,
        tag: String? = null,
        since: Long? = null,
        limit: Int = 100
    ): List<LogEntry> {
        return logEntries
            .filter { entry ->
                (level == null || entry.level == level) &&
                (category == null || entry.category == category) &&
                (tag == null || entry.tag.contains(tag, ignoreCase = true)) &&
                (since == null || entry.timestamp >= since)
            }
            .take(limit)
            .toList()
    }

    /**
     * Export debug log as formatted text
     */
    fun exportDebugLog(
        includeDetails: Boolean = true,
        level: LogLevel? = null,
        category: LogCategory? = null
    ): String {
        val entries = getLogEntries(level = level, category = category, limit = maxLogEntries)

        return buildString {
            appendLine("=== CustomCamera Debug Log ===")
            appendLine("Generated: ${timestampFormat.format(Date())}")
            appendLine("Total entries: ${entries.size}")
            appendLine("Log levels: ${LogLevel.values().joinToString()}")
            appendLine("Categories: ${LogCategory.values().joinToString()}")
            appendLine()

            entries.forEach { entry ->
                appendLine("${formatTimestamp(entry.timestamp)} [${entry.level}] ${entry.tag}: ${entry.message}")

                if (includeDetails && entry.details.isNotEmpty()) {
                    entry.details.forEach { (key, value) ->
                        appendLine("  $key: $value")
                    }
                }

                entry.exception?.let { exception ->
                    appendLine("  Exception: ${exception.javaClass.simpleName}")
                    appendLine("  Message: ${exception.message}")
                    appendLine("  Stack trace: ${exception.stackTraceToString()}")
                }

                appendLine()
            }
        }
    }

    /**
     * Clear all log entries
     */
    fun clearLogs() {
        logEntries.clear()
        _logCount.value = 0
        Log.i("DebugLogger", "Debug logs cleared")
    }

    /**
     * Get log statistics
     */
    fun getLogStats(): LogStats {
        val entries = logEntries.toList()
        val levelCounts = LogLevel.values().associateWith { level ->
            entries.count { it.level == level }
        }
        val categoryCounts = LogCategory.values().associateWith { category ->
            entries.count { it.category == category }
        }

        return LogStats(
            totalEntries = entries.size,
            levelCounts = levelCounts,
            categoryCounts = categoryCounts,
            oldestEntry = entries.minByOrNull { it.timestamp }?.timestamp,
            newestEntry = entries.maxByOrNull { it.timestamp }?.timestamp
        )
    }

    private fun addLogEntry(entry: LogEntry) {
        logEntries.offer(entry)

        // Remove oldest entries if we exceed max size
        while (logEntries.size > maxLogEntries) {
            logEntries.poll()
        }

        _logCount.value = logEntries.size
    }

    private fun formatDetails(details: Map<String, Any>): String {
        if (details.isEmpty()) return ""
        return details.entries.joinToString(", ") { "${it.key}=${it.value}" }
    }

    private fun formatTimestamp(timestamp: Long): String {
        return timestampFormat.format(Date(timestamp))
    }
}

/**
 * Log entry data class
 */
data class LogEntry(
    val timestamp: Long,
    val level: LogLevel,
    val category: LogCategory,
    val message: String,
    val details: Map<String, Any> = emptyMap(),
    val tag: String,
    val exception: Throwable? = null
)

/**
 * Log levels for filtering and display
 */
enum class LogLevel {
    DEBUG, INFO, WARN, ERROR
}

/**
 * Log categories for organizing different types of operations
 */
enum class LogCategory {
    GENERAL, CAMERA_API, CAMERA_BINDING, PLUGIN, PERFORMANCE, ERROR, UI, PROCESSING
}

/**
 * Camera binding result for logging
 */
data class BindingResult(
    val success: Boolean,
    val useCases: List<String> = emptyList(),
    val error: String? = null
)

/**
 * Log statistics summary
 */
data class LogStats(
    val totalEntries: Int,
    val levelCounts: Map<LogLevel, Int>,
    val categoryCounts: Map<LogCategory, Int>,
    val oldestEntry: Long?,
    val newestEntry: Long?
)