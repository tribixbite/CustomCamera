package com.customcamera.app.video

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.camera.video.Quality
import kotlinx.coroutines.*
// Removed kotlinx.serialization imports as not available
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

/**
 * Video Recording Analytics and Metrics Manager
 *
 * Provides comprehensive analytics for video recording performance:
 * - Real-time recording metrics (frame rate, bitrate, quality)
 * - Performance monitoring and optimization suggestions
 * - Recording session analytics and statistics
 * - Quality analysis and recommendations
 * - Battery and thermal impact tracking
 * - Export analytics for performance review
 */
class VideoAnalyticsManager(private val context: Context) {

    companion object {
        private const val TAG = "VideoAnalytics"
        private const val ANALYTICS_FILE = "video_analytics.json"
        private const val MAX_SESSIONS = 100
        private const val METRICS_UPDATE_INTERVAL = 1000L // 1 second
    }

    /**
     * Real-time recording metrics
     */
    data class RecordingMetrics(
        val timestamp: Long = System.currentTimeMillis(),
        val actualFrameRate: Float = 0.0f,
        val targetFrameRate: Int = 30,
        val actualBitrate: Long = 0L,
        val targetBitrate: Long = 0L,
        val droppedFrames: Int = 0,
        val encoderLoad: Float = 0.0f,        // CPU usage for encoding
        val memoryUsage: Long = 0L,           // Memory usage in bytes
        val batteryLevel: Float = 0.0f,       // Battery percentage
        val thermalState: Int = 0,            // Thermal throttling state
        val storageSpaceRemaining: Long = 0L  // Available storage in bytes
    )

    /**
     * Recording session analytics
     */
    data class RecordingSession(
        val sessionId: String = UUID.randomUUID().toString(),
        val startTime: Long = System.currentTimeMillis(),
        val endTime: Long = 0L,
        val duration: Long = 0L,              // Duration in milliseconds
        val quality: String = "HD",
        val codec: String = "H264",
        val frameRate: Int = 30,
        val resolution: String = "1920x1080",
        val fileSize: Long = 0L,              // Final file size in bytes
        val averageBitrate: Long = 0L,
        val effectsUsed: List<String> = emptyList(),
        val stabilizationMode: String = "OFF",
        val recordingMode: String = "STANDARD",
        val droppedFramesTotal: Int = 0,
        val batteryUsed: Float = 0.0f,        // Battery percentage consumed
        val maxTemperature: Float = 0.0f,     // Peak temperature during recording
        val qualityScore: Float = 0.0f,       // Overall quality score (0-1)
        val performanceScore: Float = 0.0f,   // Performance efficiency score (0-1)
        val issues: List<String> = emptyList(), // Issues encountered during recording
        val deviceInfo: DeviceInfo = DeviceInfo()
    )

    /**
     * Device information for analytics context
     */
    data class DeviceInfo(
        val manufacturer: String = Build.MANUFACTURER,
        val model: String = Build.MODEL,
        val androidVersion: String = Build.VERSION.RELEASE,
        val apiLevel: Int = Build.VERSION.SDK_INT,
        val totalMemory: Long = 0L,
        val availableStorage: Long = 0L,
        val cpuArchitecture: String = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"
    )

    /**
     * Analytics summary for performance insights
     */
    data class AnalyticsSummary(
        val totalSessions: Int = 0,
        val totalRecordingTime: Long = 0L,    // Total recording time in milliseconds
        val totalFileSize: Long = 0L,         // Total file size in bytes
        val averageQualityScore: Float = 0.0f,
        val averagePerformanceScore: Float = 0.0f,
        val mostUsedQuality: String = "",
        val mostUsedCodec: String = "",
        val mostUsedEffects: List<String> = emptyList(),
        val commonIssues: List<String> = emptyList(),
        val recommendations: List<String> = emptyList(),
        val lastUpdated: Long = System.currentTimeMillis()
    )

    private var currentSession: RecordingSession? = null
    private var sessionMetrics = mutableListOf<RecordingMetrics>()
    private var isRecording = false
    private var metricsJob: Job? = null

    // Analytics data
    private val sessions = mutableListOf<RecordingSession>()
    private var analyticsSummary = AnalyticsSummary()

    // Performance tracking
    private var initialBatteryLevel = 0.0f
    private var maxTemperatureRecorded = 0.0f

    /**
     * Initialize analytics manager
     */
    fun initialize(): Boolean {
        return try {
            loadAnalyticsData()
            Log.d(TAG, "Video analytics manager initialized with ${sessions.size} sessions")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize video analytics manager", e)
            false
        }
    }

    /**
     * Start recording session analytics
     */
    fun startRecordingSession(
        quality: Quality,
        codec: String,
        frameRate: Int,
        resolution: String,
        effectsUsed: List<String> = emptyList(),
        stabilizationMode: String = "OFF",
        recordingMode: String = "STANDARD"
    ) {
        val deviceInfo = DeviceInfo(
            totalMemory = getTotalMemory(),
            availableStorage = getAvailableStorage()
        )

        currentSession = RecordingSession(
            startTime = System.currentTimeMillis(),
            quality = quality.toString(),
            codec = codec,
            frameRate = frameRate,
            resolution = resolution,
            effectsUsed = effectsUsed,
            stabilizationMode = stabilizationMode,
            recordingMode = recordingMode,
            deviceInfo = deviceInfo
        )

        sessionMetrics.clear()
        isRecording = true
        initialBatteryLevel = getCurrentBatteryLevel()
        maxTemperatureRecorded = 0.0f

        startMetricsCollection()
        Log.d(TAG, "Started recording session: ${currentSession?.sessionId}")
    }

    /**
     * Stop recording session and finalize analytics
     */
    fun stopRecordingSession(fileSize: Long = 0L): RecordingSession? {
        if (!isRecording || currentSession == null) return null

        isRecording = false
        stopMetricsCollection()

        val session = currentSession!!
        val endTime = System.currentTimeMillis()
        val duration = endTime - session.startTime

        // Calculate session analytics
        val qualityScore = calculateQualityScore()
        val performanceScore = calculatePerformanceScore()
        val issues = detectIssues()
        val averageBitrate = calculateAverageBitrate()
        val batteryUsed = initialBatteryLevel - getCurrentBatteryLevel()

        val finalSession = session.copy(
            endTime = endTime,
            duration = duration,
            fileSize = fileSize,
            averageBitrate = averageBitrate,
            droppedFramesTotal = sessionMetrics.sumOf { it.droppedFrames },
            batteryUsed = batteryUsed,
            maxTemperature = maxTemperatureRecorded,
            qualityScore = qualityScore,
            performanceScore = performanceScore,
            issues = issues
        )

        // Store session
        sessions.add(finalSession)
        if (sessions.size > MAX_SESSIONS) {
            sessions.removeAt(0) // Remove oldest session
        }

        // Update summary
        updateAnalyticsSummary()
        saveAnalyticsData()

        currentSession = null
        Log.d(TAG, "Completed recording session: ${finalSession.sessionId}")
        return finalSession
    }

    /**
     * Update real-time metrics during recording
     */
    fun updateMetrics(
        actualFrameRate: Float,
        actualBitrate: Long,
        droppedFrames: Int,
        encoderLoad: Float
    ) {
        if (!isRecording) return

        val metrics = RecordingMetrics(
            actualFrameRate = actualFrameRate,
            targetFrameRate = currentSession?.frameRate ?: 30,
            actualBitrate = actualBitrate,
            targetBitrate = calculateTargetBitrate(),
            droppedFrames = droppedFrames,
            encoderLoad = encoderLoad,
            memoryUsage = getMemoryUsage(),
            batteryLevel = getCurrentBatteryLevel(),
            thermalState = getThermalState(),
            storageSpaceRemaining = getAvailableStorage()
        )

        sessionMetrics.add(metrics)

        // Update max temperature
        val currentTemp = getCurrentTemperature()
        if (currentTemp > maxTemperatureRecorded) {
            maxTemperatureRecorded = currentTemp
        }

        // Limit metrics history
        if (sessionMetrics.size > 300) { // Keep last 5 minutes at 1-second intervals
            sessionMetrics.removeAt(0)
        }
    }

    /**
     * Get current recording session
     */
    fun getCurrentSession(): RecordingSession? = currentSession

    /**
     * Get recent recording metrics
     */
    fun getRecentMetrics(count: Int = 10): List<RecordingMetrics> {
        return sessionMetrics.takeLast(count)
    }

    /**
     * Get all recording sessions
     */
    fun getAllSessions(): List<RecordingSession> = sessions.toList()

    /**
     * Get analytics summary
     */
    fun getAnalyticsSummary(): AnalyticsSummary = analyticsSummary

    /**
     * Get performance insights and recommendations
     */
    fun getPerformanceInsights(): List<String> {
        val insights = mutableListOf<String>()

        if (sessions.isEmpty()) {
            insights.add("No recording sessions available for analysis")
            return insights
        }

        val recentSessions = sessions.takeLast(10)
        val avgQuality = recentSessions.map { it.qualityScore }.average()
        val avgPerformance = recentSessions.map { it.performanceScore }.average()
        val totalDroppedFrames = recentSessions.sumOf { it.droppedFramesTotal }

        // Quality insights
        when {
            avgQuality < 0.5 -> insights.add("Video quality is below average. Consider using a lower resolution or reducing effects.")
            avgQuality < 0.7 -> insights.add("Video quality is moderate. Fine-tuning codec settings may improve quality.")
            else -> insights.add("Video quality is excellent. Current settings are optimal.")
        }

        // Performance insights
        when {
            avgPerformance < 0.5 -> insights.add("Recording performance needs improvement. Consider reducing frame rate or quality.")
            avgPerformance < 0.7 -> insights.add("Recording performance is adequate but can be optimized.")
            else -> insights.add("Recording performance is excellent.")
        }

        // Dropped frames insights
        if (totalDroppedFrames > 50) {
            insights.add("High number of dropped frames detected. Consider reducing video quality or closing background apps.")
        }

        // Battery insights
        val avgBatteryUsage = recentSessions.map { it.batteryUsed }.average()
        if (avgBatteryUsage > 10.0) {
            insights.add("High battery usage during recording. Consider enabling battery optimization mode.")
        }

        // Thermal insights
        val maxTemps = recentSessions.map { it.maxTemperature }.filter { it > 0 }
        if (maxTemps.isNotEmpty() && maxTemps.average() > 45.0) {
            insights.add("Device temperature is getting high during recording. Take breaks to prevent thermal throttling.")
        }

        return insights
    }

    /**
     * Export analytics data for external analysis
     */
    fun exportAnalytics(): String {
        val exportData = mapOf(
            "summary" to analyticsSummary,
            "sessions" to sessions,
            "deviceInfo" to DeviceInfo(),
            "exportDate" to SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        )

        // Simplified export without kotlinx.serialization
        return exportData.toString()
    }

    /**
     * Clear all analytics data
     */
    fun clearAnalytics() {
        sessions.clear()
        analyticsSummary = AnalyticsSummary()
        saveAnalyticsData()
        Log.d(TAG, "Analytics data cleared")
    }

    // Private helper methods

    private fun startMetricsCollection() {
        metricsJob = CoroutineScope(Dispatchers.Default).launch {
            while (isActive && isRecording) {
                try {
                    // Collect automatic metrics
                    updateMetrics(
                        actualFrameRate = 30.0f, // Simplified - would get from encoder
                        actualBitrate = calculateTargetBitrate(),
                        droppedFrames = 0,
                        encoderLoad = 0.5f
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Error collecting metrics", e)
                }

                delay(METRICS_UPDATE_INTERVAL)
            }
        }
    }

    private fun stopMetricsCollection() {
        metricsJob?.cancel()
        metricsJob = null
    }

    private fun calculateQualityScore(): Float {
        if (sessionMetrics.isEmpty()) return 0.5f

        val frameRateConsistency = calculateFrameRateConsistency()
        val bitrateConsistency = calculateBitrateConsistency()
        val droppedFrameRatio = calculateDroppedFrameRatio()

        // Weighted score
        return (frameRateConsistency * 0.4f + bitrateConsistency * 0.3f + (1.0f - droppedFrameRatio) * 0.3f)
            .coerceIn(0.0f, 1.0f)
    }

    private fun calculatePerformanceScore(): Float {
        if (sessionMetrics.isEmpty()) return 0.5f

        val avgEncoderLoad = sessionMetrics.map { it.encoderLoad }.average().toFloat()
        val memoryEfficiency = calculateMemoryEfficiency()
        val thermalEfficiency = calculateThermalEfficiency()

        // Weighted score (lower resource usage = higher score)
        return ((1.0f - avgEncoderLoad) * 0.4f + memoryEfficiency * 0.3f + thermalEfficiency * 0.3f)
            .coerceIn(0.0f, 1.0f)
    }

    private fun calculateFrameRateConsistency(): Float {
        val frameRates = sessionMetrics.map { it.actualFrameRate }
        if (frameRates.isEmpty()) return 0.5f

        val target = currentSession?.frameRate?.toFloat() ?: 30.0f
        val variance = frameRates.map { abs(it - target) }.average().toFloat()
        return (1.0f - (variance / target)).coerceIn(0.0f, 1.0f)
    }

    private fun calculateBitrateConsistency(): Float {
        val bitrates = sessionMetrics.map { it.actualBitrate }
        if (bitrates.isEmpty()) return 0.5f

        val target = calculateTargetBitrate()
        val variance = bitrates.map { abs(it - target).toFloat() / target }.average().toFloat()
        return (1.0f - variance).coerceIn(0.0f, 1.0f)
    }

    private fun calculateDroppedFrameRatio(): Float {
        val totalFrames = sessionMetrics.size * (currentSession?.frameRate ?: 30)
        val droppedFrames = sessionMetrics.sumOf { it.droppedFrames }
        return if (totalFrames > 0) droppedFrames.toFloat() / totalFrames else 0.0f
    }

    private fun calculateMemoryEfficiency(): Float {
        val memoryUsages = sessionMetrics.map { it.memoryUsage }
        if (memoryUsages.isEmpty()) return 0.5f

        val maxMemory = getTotalMemory()
        val avgUsage = memoryUsages.average()
        val efficiency = 1.0f - (avgUsage / maxMemory).toFloat()
        return efficiency.coerceIn(0.0f, 1.0f)
    }

    private fun calculateThermalEfficiency(): Float {
        return if (maxTemperatureRecorded > 0) {
            (1.0f - (maxTemperatureRecorded - 25.0f) / 25.0f).coerceIn(0.0f, 1.0f)
        } else {
            0.8f // Default efficiency if no thermal data
        }
    }

    private fun detectIssues(): List<String> {
        val issues = mutableListOf<String>()

        val droppedFrameRatio = calculateDroppedFrameRatio()
        if (droppedFrameRatio > 0.05f) {
            issues.add("High dropped frame rate (${(droppedFrameRatio * 100).toInt()}%)")
        }

        if (maxTemperatureRecorded > 50.0f) {
            issues.add("High device temperature (${maxTemperatureRecorded.toInt()}°C)")
        }

        val batteryUsed = initialBatteryLevel - getCurrentBatteryLevel()
        if (batteryUsed > 15.0f) {
            issues.add("High battery consumption (${batteryUsed.toInt()}%)")
        }

        val avgEncoderLoad = sessionMetrics.map { it.encoderLoad }.average()
        if (avgEncoderLoad > 0.8) {
            issues.add("High encoder load (${(avgEncoderLoad * 100).toInt()}%)")
        }

        return issues
    }

    private fun calculateAverageBitrate(): Long {
        return if (sessionMetrics.isNotEmpty()) {
            sessionMetrics.map { it.actualBitrate }.average().toLong()
        } else {
            calculateTargetBitrate()
        }
    }

    private fun calculateTargetBitrate(): Long {
        // Simplified calculation based on quality
        return when (currentSession?.quality) {
            "LOWEST" -> 1_000_000L
            "SD" -> 3_000_000L
            "HD" -> 8_000_000L
            "FHD" -> 15_000_000L
            "UHD" -> 45_000_000L
            else -> 8_000_000L
        }
    }

    private fun updateAnalyticsSummary() {
        if (sessions.isEmpty()) return

        analyticsSummary = AnalyticsSummary(
            totalSessions = sessions.size,
            totalRecordingTime = sessions.sumOf { it.duration },
            totalFileSize = sessions.sumOf { it.fileSize },
            averageQualityScore = sessions.map { it.qualityScore }.average().toFloat(),
            averagePerformanceScore = sessions.map { it.performanceScore }.average().toFloat(),
            mostUsedQuality = sessions.groupBy { it.quality }.maxByOrNull { it.value.size }?.key ?: "",
            mostUsedCodec = sessions.groupBy { it.codec }.maxByOrNull { it.value.size }?.key ?: "",
            mostUsedEffects = sessions.flatMap { it.effectsUsed }.groupBy { it }.toList()
                .sortedByDescending { it.second.size }.take(5).map { it.first },
            commonIssues = sessions.flatMap { it.issues }.groupBy { it }.toList()
                .sortedByDescending { it.second.size }.take(5).map { it.first },
            recommendations = getPerformanceInsights(),
            lastUpdated = System.currentTimeMillis()
        )
    }

    private fun loadAnalyticsData() {
        try {
            val file = File(context.filesDir, ANALYTICS_FILE)
            if (file.exists()) {
                // Simplified loading without kotlinx.serialization
                // In a full implementation, would parse JSON manually
                Log.d(TAG, "Analytics data file exists but skipping deserialization")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load analytics data", e)
        }
    }

    private fun saveAnalyticsData() {
        try {
            val file = File(context.filesDir, ANALYTICS_FILE)
            // Simplified saving without kotlinx.serialization
            val data = "sessions_count:${sessions.size}"
            file.writeText(data)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save analytics data", e)
        }
    }

    // System information methods (simplified implementations)

    private fun getCurrentBatteryLevel(): Float {
        return try {
            val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager
            batteryManager.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY).toFloat()
        } catch (e: Exception) {
            50.0f // Default if unable to read
        }
    }

    private fun getCurrentTemperature(): Float {
        // Simplified - in real implementation, would read thermal sensors
        return 35.0f + (Math.random() * 10).toFloat()
    }

    private fun getThermalState(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
                powerManager.currentThermalStatus
            } catch (e: Exception) {
                0
            }
        } else {
            0
        }
    }

    private fun getMemoryUsage(): Long {
        return try {
            val runtime = Runtime.getRuntime()
            runtime.totalMemory() - runtime.freeMemory()
        } catch (e: Exception) {
            0L
        }
    }

    private fun getTotalMemory(): Long {
        return try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val memoryInfo = android.app.ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memoryInfo)
            memoryInfo.totalMem
        } catch (e: Exception) {
            2_000_000_000L // 2GB default
        }
    }

    private fun getAvailableStorage(): Long {
        return try {
            val path = context.filesDir
            val stat = android.os.StatFs(path.path)
            stat.availableBlocksLong * stat.blockSizeLong
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * Clean up resources
     */
    fun cleanup() {
        stopMetricsCollection()
        saveAnalyticsData()
        Log.d(TAG, "Video analytics manager cleaned up")
    }
}