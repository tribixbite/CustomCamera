package com.customcamera.app.utils

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import kotlinx.coroutines.*
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min

/**
 * Performance Optimization Manager
 *
 * Provides comprehensive performance monitoring and optimization for:
 * - Memory usage tracking and cleanup
 * - Background task management
 * - Resource caching with automatic cleanup
 * - Frame rate optimization
 * - Battery usage optimization
 */
class PerformanceOptimizer private constructor(private val context: Context) {

    companion object {
        private const val TAG = "PerformanceOptimizer"
        private const val MAX_MEMORY_THRESHOLD = 0.8f
        private const val LOW_MEMORY_THRESHOLD = 0.9f
        private const val CACHE_CLEANUP_INTERVAL = 30_000L // 30 seconds
        private const val MEMORY_CHECK_INTERVAL = 10_000L // 10 seconds

        @Volatile
        private var INSTANCE: PerformanceOptimizer? = null

        fun getInstance(context: Context): PerformanceOptimizer {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PerformanceOptimizer(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val memoryInfo = ActivityManager.MemoryInfo()

    // Resource caches with weak references
    private val bitmapCache = ConcurrentHashMap<String, WeakReference<Bitmap>>()
    private val taskCache = ConcurrentHashMap<String, Job>()

    // Performance monitoring
    private var memoryMonitoringJob: Job? = null
    private var cacheCleanupJob: Job? = null
    private var isOptimizationActive = false

    // Memory statistics
    private var peakMemoryUsage = 0L
    private var averageMemoryUsage = 0L
    private var memoryReadings = mutableListOf<Long>()

    /**
     * Start performance optimization monitoring
     */
    fun startOptimization() {
        if (isOptimizationActive) return

        isOptimizationActive = true
        Log.d(TAG, "Starting performance optimization")

        // Start memory monitoring
        memoryMonitoringJob = CoroutineScope(Dispatchers.IO).launch {
            while (isOptimizationActive) {
                monitorMemoryUsage()
                delay(MEMORY_CHECK_INTERVAL)
            }
        }

        // Start cache cleanup
        cacheCleanupJob = CoroutineScope(Dispatchers.IO).launch {
            while (isOptimizationActive) {
                cleanupCaches()
                delay(CACHE_CLEANUP_INTERVAL)
            }
        }
    }

    /**
     * Stop performance optimization monitoring
     */
    fun stopOptimization() {
        isOptimizationActive = false
        memoryMonitoringJob?.cancel()
        cacheCleanupJob?.cancel()
        Log.d(TAG, "Stopped performance optimization")
    }

    /**
     * Get current memory usage statistics
     */
    fun getMemoryStats(): MemoryStats {
        activityManager.getMemoryInfo(memoryInfo)

        val totalMemory = getTotalMemory()
        val availableMemory = memoryInfo.availMem
        val usedMemory = totalMemory - availableMemory
        val memoryPercentage = (usedMemory.toDouble() / totalMemory.toDouble() * 100).toFloat()

        return MemoryStats(
            totalMemory = totalMemory,
            usedMemory = usedMemory,
            availableMemory = availableMemory,
            memoryPercentage = memoryPercentage,
            isLowMemory = memoryInfo.lowMemory,
            peakUsage = peakMemoryUsage,
            averageUsage = averageMemoryUsage
        )
    }

    /**
     * Force memory cleanup when low on memory
     */
    fun forceMemoryCleanup() {
        Log.d(TAG, "Forcing memory cleanup")

        // Clear bitmap cache
        clearBitmapCache()

        // Cancel non-essential background tasks
        cancelBackgroundTasks()

        // Force garbage collection
        System.gc()

        Log.d(TAG, "Memory cleanup completed")
    }

    /**
     * Cache a bitmap with automatic cleanup
     */
    fun cacheBitmap(key: String, bitmap: Bitmap) {
        val memoryStats = getMemoryStats()

        // Don't cache if memory usage is high
        if (memoryStats.memoryPercentage > MAX_MEMORY_THRESHOLD * 100) {
            Log.w(TAG, "Skipping bitmap cache due to high memory usage")
            return
        }

        bitmapCache[key] = WeakReference(bitmap)
    }

    /**
     * Retrieve cached bitmap
     */
    fun getCachedBitmap(key: String): Bitmap? {
        return bitmapCache[key]?.get()
    }

    /**
     * Optimize camera preview frame rate based on device performance
     */
    fun getOptimalFrameRate(): Int {
        val memoryStats = getMemoryStats()
        val isLowEndDevice = isLowEndDevice()

        return when {
            isLowEndDevice || memoryStats.memoryPercentage > 80f -> 15 // Low frame rate for performance
            memoryStats.memoryPercentage > 60f -> 24 // Medium frame rate
            else -> 30 // High frame rate for smooth experience
        }
    }

    /**
     * Get optimal image processing quality based on device capabilities
     */
    fun getOptimalImageQuality(): ImageQuality {
        val memoryStats = getMemoryStats()
        val isLowEndDevice = isLowEndDevice()

        return when {
            isLowEndDevice -> ImageQuality.LOW
            memoryStats.memoryPercentage > 75f -> ImageQuality.MEDIUM
            else -> ImageQuality.HIGH
        }
    }

    /**
     * Optimize background task execution
     */
    fun executeOptimizedTask(
        key: String,
        task: suspend () -> Unit,
        priority: TaskPriority = TaskPriority.NORMAL
    ): Job {
        // Cancel existing task with same key if exists
        taskCache[key]?.cancel()

        val dispatcher = when (priority) {
            TaskPriority.HIGH -> Dispatchers.Main
            TaskPriority.NORMAL -> Dispatchers.Default
            TaskPriority.LOW -> Dispatchers.IO
        }

        val job = CoroutineScope(dispatcher).launch {
            try {
                task()
            } catch (e: Exception) {
                Log.e(TAG, "Task execution failed: $key", e)
            } finally {
                taskCache.remove(key)
            }
        }

        taskCache[key] = job
        return job
    }

    /**
     * Check if device should use power-saving optimizations
     */
    fun shouldUsePowerSaving(): Boolean {
        val memoryStats = getMemoryStats()
        return memoryStats.isLowMemory ||
               memoryStats.memoryPercentage > LOW_MEMORY_THRESHOLD * 100 ||
               isLowEndDevice()
    }

    // Private helper methods

    private fun monitorMemoryUsage() {
        val memoryStats = getMemoryStats()

        // Update peak usage
        if (memoryStats.usedMemory > peakMemoryUsage) {
            peakMemoryUsage = memoryStats.usedMemory
        }

        // Update average usage
        memoryReadings.add(memoryStats.usedMemory)
        if (memoryReadings.size > 100) { // Keep last 100 readings
            memoryReadings.removeAt(0)
        }
        averageMemoryUsage = memoryReadings.average().toLong()

        // Trigger cleanup if memory usage is high
        if (memoryStats.memoryPercentage > MAX_MEMORY_THRESHOLD * 100) {
            Log.w(TAG, "High memory usage detected: ${memoryStats.memoryPercentage}%")
            cleanupCaches()
        }

        // Force cleanup if critically low
        if (memoryStats.isLowMemory) {
            Log.w(TAG, "Low memory warning - forcing cleanup")
            forceMemoryCleanup()
        }
    }

    private fun cleanupCaches() {
        // Clean up bitmap cache
        val iterator = bitmapCache.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.value.get() == null) {
                iterator.remove()
            }
        }

        // Clean up completed tasks
        val taskIterator = taskCache.iterator()
        while (taskIterator.hasNext()) {
            val entry = taskIterator.next()
            if (!entry.value.isActive) {
                taskIterator.remove()
            }
        }
    }

    private fun clearBitmapCache() {
        bitmapCache.clear()
    }

    private fun cancelBackgroundTasks() {
        taskCache.values.forEach { job ->
            if (job.isActive) {
                job.cancel()
            }
        }
        taskCache.clear()
    }

    private fun getTotalMemory(): Long {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            activityManager.getMemoryInfo(memoryInfo)
            memoryInfo.totalMem
        } else {
            // Fallback for older devices
            Runtime.getRuntime().maxMemory()
        }
    }

    private fun isLowEndDevice(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            activityManager.isLowRamDevice
        } else {
            // Fallback for older devices
            val memoryClass = activityManager.memoryClass
            memoryClass <= 32 // Consider devices with <= 32MB as low-end
        }
    }

    // Data classes

    data class MemoryStats(
        val totalMemory: Long,
        val usedMemory: Long,
        val availableMemory: Long,
        val memoryPercentage: Float,
        val isLowMemory: Boolean,
        val peakUsage: Long,
        val averageUsage: Long
    )

    enum class ImageQuality {
        LOW, MEDIUM, HIGH
    }

    enum class TaskPriority {
        LOW, NORMAL, HIGH
    }
}