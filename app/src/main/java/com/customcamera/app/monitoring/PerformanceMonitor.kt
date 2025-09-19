package com.customcamera.app.monitoring

import android.content.Context
import android.util.Log
import com.customcamera.app.engine.CameraContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

/**
 * PerformanceMonitor provides real-time FPS display,
 * frame processing time analysis, and camera performance metrics.
 */
class PerformanceMonitor(
    private val context: Context,
    private val cameraContext: CameraContext
) {

    // Frame rate monitoring
    private val frameCount = AtomicLong(0)
    private val lastFPSCalculation = AtomicLong(System.currentTimeMillis())
    private var currentFPS: Float = 0f

    // Frame processing monitoring
    private val processingTimes = mutableListOf<Long>()
    private val maxProcessingHistory = 30 // Keep last 30 processing times
    private var averageProcessingTime: Float = 0f

    // Camera performance metrics
    private var memoryUsageMB: Long = 0
    private var cameraFrameDrops: Int = 0
    private var pluginProcessingLoad: Float = 0f

    // Monitoring state
    private var isMonitoringActive: Boolean = false
    private var monitoringScope: CoroutineScope? = null

    /**
     * Start real-time FPS monitoring
     */
    fun startFPSMonitoring() {
        if (isMonitoringActive) return

        isMonitoringActive = true
        monitoringScope = CoroutineScope(Dispatchers.Default)

        monitoringScope!!.launch {
            while (isMonitoringActive) {
                try {
                    calculateFPS()
                    updateMemoryUsage()
                    calculatePluginLoad()

                    delay(1000) // Update every second

                } catch (e: Exception) {
                    Log.e(TAG, "Error in FPS monitoring", e)
                }
            }
        }

        Log.i(TAG, "FPS monitoring started")
    }

    /**
     * Stop FPS monitoring
     */
    fun stopFPSMonitoring() {
        isMonitoringActive = false
        monitoringScope = null
        Log.i(TAG, "FPS monitoring stopped")
    }

    /**
     * Record frame processing time
     */
    fun recordFrameProcessingTime(processingTimeMs: Long) {
        synchronized(processingTimes) {
            processingTimes.add(processingTimeMs)

            // Maintain history size
            while (processingTimes.size > maxProcessingHistory) {
                processingTimes.removeAt(0)
            }

            // Calculate average
            averageProcessingTime = if (processingTimes.isNotEmpty()) {
                processingTimes.average().toFloat()
            } else {
                0f
            }
        }

        // Count frame
        frameCount.incrementAndGet()
    }

    /**
     * Get current performance metrics
     */
    fun getPerformanceMetrics(): PerformanceMetrics {
        return PerformanceMetrics(
            currentFPS = currentFPS,
            averageProcessingTimeMs = averageProcessingTime,
            memoryUsageMB = memoryUsageMB,
            cameraFrameDrops = cameraFrameDrops,
            pluginProcessingLoad = pluginProcessingLoad,
            totalFramesProcessed = frameCount.get(),
            isMonitoringActive = isMonitoringActive
        )
    }

    /**
     * Get real-time performance display text
     */
    fun getPerformanceDisplayText(): String {
        val metrics = getPerformanceMetrics()

        return """
            📊 Real-time Performance

            🎬 Frame Rate:
            FPS: ${String.format("%.1f", metrics.currentFPS)}
            Processing: ${String.format("%.1f", metrics.averageProcessingTimeMs)}ms avg
            Frames: ${metrics.totalFramesProcessed}

            💾 Memory:
            Usage: ${metrics.memoryUsageMB}MB
            Load: ${String.format("%.1f", metrics.pluginProcessingLoad)}%

            📱 System:
            Status: ${if (metrics.isMonitoringActive) "🟢 Active" else "🔴 Inactive"}
            Drops: ${metrics.cameraFrameDrops}
        """.trimIndent()
    }

    /**
     * Get performance analysis and recommendations
     */
    fun getPerformanceAnalysis(): PerformanceAnalysis {
        val metrics = getPerformanceMetrics()

        val fpsStatus = when {
            metrics.currentFPS >= 25f -> PerformanceStatus.EXCELLENT
            metrics.currentFPS >= 20f -> PerformanceStatus.GOOD
            metrics.currentFPS >= 15f -> PerformanceStatus.ACCEPTABLE
            else -> PerformanceStatus.POOR
        }

        val processingStatus = when {
            metrics.averageProcessingTimeMs <= 16f -> PerformanceStatus.EXCELLENT // 60+ FPS processing
            metrics.averageProcessingTimeMs <= 33f -> PerformanceStatus.GOOD      // 30+ FPS processing
            metrics.averageProcessingTimeMs <= 50f -> PerformanceStatus.ACCEPTABLE // 20+ FPS processing
            else -> PerformanceStatus.POOR
        }

        val memoryStatus = when {
            metrics.memoryUsageMB <= 100 -> PerformanceStatus.EXCELLENT
            metrics.memoryUsageMB <= 200 -> PerformanceStatus.GOOD
            metrics.memoryUsageMB <= 400 -> PerformanceStatus.ACCEPTABLE
            else -> PerformanceStatus.POOR
        }

        val recommendations = generateRecommendations(fpsStatus, processingStatus, memoryStatus)

        return PerformanceAnalysis(
            fpsStatus = fpsStatus,
            processingStatus = processingStatus,
            memoryStatus = memoryStatus,
            overallStatus = listOf(fpsStatus, processingStatus, memoryStatus).minByOrNull { it.ordinal } ?: PerformanceStatus.POOR,
            recommendations = recommendations
        )
    }

    private fun calculateFPS() {
        val currentTime = System.currentTimeMillis()
        val lastTime = lastFPSCalculation.get()
        val timeDelta = currentTime - lastTime

        if (timeDelta >= 1000) { // Calculate every second
            val frames = frameCount.get()
            val fps = (frames * 1000f) / timeDelta

            currentFPS = fps
            frameCount.set(0)
            lastFPSCalculation.set(currentTime)

            Log.d(TAG, "FPS calculated: ${String.format("%.1f", fps)}")
        }
    }

    private fun updateMemoryUsage() {
        val runtime = Runtime.getRuntime()
        memoryUsageMB = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024
    }

    private fun calculatePluginLoad() {
        // Estimate plugin processing load based on active features
        var load = 0f

        // Base camera processing
        load += 10f

        // Add load for each active feature
        // Note: In production, this would get actual plugin metrics
        load += 5f  // Base plugins always active
        load += if (cameraContext.settingsManager.getHistogramOverlay()) 15f else 0f
        load += if (cameraContext.settingsManager.getPerformanceMonitoring()) 5f else 0f
        // Additional plugin load calculations would go here

        pluginProcessingLoad = load.coerceIn(0f, 100f)
    }

    private fun generateRecommendations(
        fpsStatus: PerformanceStatus,
        processingStatus: PerformanceStatus,
        memoryStatus: PerformanceStatus
    ): List<String> {
        val recommendations = mutableListOf<String>()

        if (fpsStatus == PerformanceStatus.POOR) {
            recommendations.add("• Disable non-essential plugins to improve frame rate")
            recommendations.add("• Reduce image analysis frequency")
        }

        if (processingStatus == PerformanceStatus.POOR) {
            recommendations.add("• Increase processing intervals for analysis plugins")
            recommendations.add("• Disable histogram and advanced analysis temporarily")
        }

        if (memoryStatus == PerformanceStatus.POOR) {
            recommendations.add("• Clear image cache and force garbage collection")
            recommendations.add("• Reduce image resolution for processing")
            recommendations.add("• Disable memory-intensive features")
        }

        if (recommendations.isEmpty()) {
            recommendations.add("• Performance is optimal - all systems running efficiently")
        }

        return recommendations
    }

    companion object {
        private const val TAG = "PerformanceMonitor"
    }
}

/**
 * Performance metrics data class
 */
data class PerformanceMetrics(
    val currentFPS: Float,
    val averageProcessingTimeMs: Float,
    val memoryUsageMB: Long,
    val cameraFrameDrops: Int,
    val pluginProcessingLoad: Float,
    val totalFramesProcessed: Long,
    val isMonitoringActive: Boolean
)

/**
 * Performance analysis results
 */
data class PerformanceAnalysis(
    val fpsStatus: PerformanceStatus,
    val processingStatus: PerformanceStatus,
    val memoryStatus: PerformanceStatus,
    val overallStatus: PerformanceStatus,
    val recommendations: List<String>
)

/**
 * Performance status levels
 */
enum class PerformanceStatus {
    EXCELLENT, GOOD, ACCEPTABLE, POOR
}