package com.customcamera.app.performance

import android.content.Context
import android.util.Log
import androidx.camera.core.ImageProxy
import com.customcamera.app.engine.CameraContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

/**
 * MemoryManager provides memory optimization for camera operations
 * including preview memory usage optimization and efficient bitmap handling.
 */
class MemoryManager(
    private val context: Context
) {

    private val imageProxyCache = mutableListOf<WeakReference<ImageProxy>>()
    private val maxCacheSize = 5

    // Memory monitoring
    private var lastMemoryCheck: Long = 0
    private val memoryCheckInterval = 5000L // Check every 5 seconds

    /**
     * Optimize camera preview memory usage
     *
     * IMPORTANT: Removed explicit System.gc() calls - Android's runtime
     * manages garbage collection better than manual invocation.
     */
    fun optimizePreviewMemory() {
        try {
            val runtime = Runtime.getRuntime()
            val usedMemory = runtime.totalMemory() - runtime.freeMemory()
            val maxMemory = runtime.maxMemory()
            val memoryUsagePercent = (usedMemory.toFloat() / maxMemory.toFloat()) * 100

            if (memoryUsagePercent > 80f) {
                Log.w(TAG, "High memory usage: ${String.format("%.1f", memoryUsagePercent)}%")

                // Clear image proxy cache to release references
                clearImageProxyCache()

                // Trust Android's GC to collect when appropriate
                Log.i(TAG, "Memory optimization performed (cache cleared)")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error optimizing preview memory", e)
        }
    }

    /**
     * Efficient bitmap handling for processing
     */
    fun processImageEfficiently(image: ImageProxy, processor: (ImageProxy) -> Unit) {
        try {
            // Add to cache with weak reference
            imageProxyCache.add(WeakReference(image))

            // Limit cache size
            while (imageProxyCache.size > maxCacheSize) {
                val oldRef = imageProxyCache.removeAt(0)
                oldRef.get()?.close() // Close old image if still available
            }

            // Process image
            processor(image)

        } catch (e: Exception) {
            Log.e(TAG, "Error in efficient image processing", e)
        }
    }

    /**
     * Background thread optimization for image analysis
     *
     * IMPORTANT: This is a suspend function that should be launched from a
     * lifecycle-aware scope (e.g., lifecycleScope in Activity) to ensure
     * proper cancellation when the component is destroyed.
     *
     * Usage: lifecycleScope.launch { memoryManager.optimizeBackgroundProcessing() }
     */
    suspend fun optimizeBackgroundProcessing() {
        try {
            // Monitor memory usage periodically
            // This loop will be cancelled when the calling scope is cancelled
            while (true) {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastMemoryCheck > memoryCheckInterval) {
                    checkMemoryUsage()
                    lastMemoryCheck = currentTime
                }

                kotlinx.coroutines.delay(1000)
            }

        } catch (e: kotlinx.coroutines.CancellationException) {
            Log.i(TAG, "Background processing optimization cancelled")
            throw e // Re-throw to properly handle cancellation
        } catch (e: Exception) {
            Log.e(TAG, "Error in background processing optimization", e)
        }
    }

    /**
     * Get memory usage statistics
     */
    fun getMemoryStats(): MemoryStats {
        val runtime = Runtime.getRuntime()
        val totalMemory = runtime.totalMemory()
        val freeMemory = runtime.freeMemory()
        val usedMemory = totalMemory - freeMemory
        val maxMemory = runtime.maxMemory()

        return MemoryStats(
            totalMemoryMB = totalMemory / 1024 / 1024,
            usedMemoryMB = usedMemory / 1024 / 1024,
            freeMemoryMB = freeMemory / 1024 / 1024,
            maxMemoryMB = maxMemory / 1024 / 1024,
            usagePercent = (usedMemory.toFloat() / maxMemory.toFloat()) * 100,
            imageCacheSize = imageProxyCache.size
        )
    }

    /**
     * Clear cached resources
     *
     * IMPORTANT: Removed explicit System.gc() call - trust Android's runtime
     */
    fun clearCache() {
        clearImageProxyCache()
        Log.i(TAG, "Memory cache cleared")
    }

    private fun clearImageProxyCache() {
        imageProxyCache.forEach { ref ->
            ref.get()?.close()
        }
        imageProxyCache.clear()
    }

    private fun checkMemoryUsage() {
        val stats = getMemoryStats()

        if (stats.usagePercent > 90f) {
            Log.w(TAG, "Critical memory usage: ${String.format("%.1f", stats.usagePercent)}%")
            clearCache()
        } else if (stats.usagePercent > 75f) {
            Log.i(TAG, "High memory usage: ${String.format("%.1f", stats.usagePercent)}%")
        }
    }

    companion object {
        private const val TAG = "MemoryManager"
    }
}

/**
 * Memory usage statistics
 */
data class MemoryStats(
    val totalMemoryMB: Long,
    val usedMemoryMB: Long,
    val freeMemoryMB: Long,
    val maxMemoryMB: Long,
    val usagePercent: Float,
    val imageCacheSize: Int
)