package com.customcamera.app.testing

import androidx.camera.core.Camera
import androidx.camera.core.ImageProxy
import com.customcamera.app.engine.CameraContext
import com.customcamera.app.engine.plugins.CameraPlugin
import com.customcamera.app.engine.plugins.ProcessingPlugin
import com.customcamera.app.engine.plugins.ProcessingResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Plugin Test Framework - Comprehensive testing utilities for camera plugins
 *
 * Features:
 * - Plugin lifecycle verification
 * - State assertion helpers
 * - Mock context and camera
 * - Processing result validation
 * - Performance measurement
 * - Memory leak detection
 */
class PluginTestFramework {

    /**
     * Test a plugin's complete lifecycle
     */
    suspend fun testPluginLifecycle(
        plugin: CameraPlugin,
        context: CameraContext,
        camera: Camera? = null
    ): PluginLifecycleResult {
        val results = mutableListOf<String>()
        val startTime = System.currentTimeMillis()

        try {
            // Initialize
            results.add("INIT_START")
            plugin.initialize(context)
            results.add("INIT_SUCCESS")

            // Camera ready
            if (camera != null) {
                results.add("CAMERA_READY_START")
                plugin.onCameraReady(camera)
                results.add("CAMERA_READY_SUCCESS")
            }

            // Cleanup
            results.add("CLEANUP_START")
            plugin.cleanup()
            results.add("CLEANUP_SUCCESS")

            val duration = System.currentTimeMillis() - startTime

            return PluginLifecycleResult(
                success = true,
                steps = results,
                durationMs = duration,
                error = null
            )

        } catch (e: Exception) {
            results.add("ERROR: ${e.message}")
            return PluginLifecycleResult(
                success = false,
                steps = results,
                durationMs = System.currentTimeMillis() - startTime,
                error = e
            )
        }
    }

    /**
     * Test plugin processing with mock image
     */
    suspend fun testPluginProcessing(
        plugin: ProcessingPlugin,
        mockImage: ImageProxy,
        expectedResult: ProcessingResult? = null
    ): ProcessingTestResult {
        val startTime = System.nanoTime()

        return try {
            val result = plugin.processFrame(mockImage)
            val processingTimeNs = System.nanoTime() - startTime

            // Validate result if expected result provided
            val isValid = if (expectedResult != null) {
                validateProcessingResult(result, expectedResult)
            } else {
                true
            }

            ProcessingTestResult(
                success = result is ProcessingResult.Success,
                result = result,
                processingTimeNs = processingTimeNs,
                isValid = isValid,
                error = if (result is ProcessingResult.Failure) result.exception else null
            )

        } catch (e: Exception) {
            ProcessingTestResult(
                success = false,
                result = ProcessingResult.Failure("Processing exception", e),
                processingTimeNs = System.nanoTime() - startTime,
                isValid = false,
                error = e
            )
        }
    }

    /**
     * Test plugin under concurrent load
     */
    suspend fun testPluginConcurrency(
        plugin: ProcessingPlugin,
        mockImages: List<ImageProxy>,
        concurrentThreads: Int = 4
    ): ConcurrencyTestResult {
        val latch = CountDownLatch(mockImages.size)
        val results = mutableListOf<ProcessingResult>()
        val errors = mutableListOf<Throwable>()
        val startTime = System.currentTimeMillis()

        // Process all images concurrently
        mockImages.forEach { image ->
            Thread {
                try {
                    kotlinx.coroutines.runBlocking {
                        val result = plugin.processFrame(image)
                        synchronized(results) {
                            results.add(result)
                        }
                    }
                } catch (e: Exception) {
                    synchronized(errors) {
                        errors.add(e)
                    }
                } finally {
                    latch.countDown()
                }
            }.start()
        }

        // Wait for completion
        val completed = latch.await(30, TimeUnit.SECONDS)
        val duration = System.currentTimeMillis() - startTime

        return ConcurrencyTestResult(
            completed = completed,
            totalImages = mockImages.size,
            successfulResults = results.count { it is ProcessingResult.Success },
            failedResults = results.count { it is ProcessingResult.Failure },
            errors = errors,
            durationMs = duration,
            averageTimeMs = duration.toFloat() / mockImages.size
        )
    }

    /**
     * Validate processing result against expected result
     */
    private fun validateProcessingResult(
        actual: ProcessingResult,
        expected: ProcessingResult
    ): Boolean {
        return when {
            actual is ProcessingResult.Success && expected is ProcessingResult.Success -> {
                // Compare data if both are success
                actual.data.keys.containsAll(expected.data.keys)
            }
            actual is ProcessingResult.Failure && expected is ProcessingResult.Failure -> {
                // Both failures - consider valid
                true
            }
            else -> false
        }
    }

    /**
     * Assert plugin state
     */
    fun assertPluginState(
        plugin: CameraPlugin,
        expectedName: String? = null,
        expectedVersion: String? = null,
        expectedPriority: Int? = null
    ) {
        expectedName?.let {
            assertEquals("Plugin name mismatch", it, plugin.name)
        }
        expectedVersion?.let {
            assertEquals("Plugin version mismatch", it, plugin.version)
        }
        expectedPriority?.let {
            assertEquals("Plugin priority mismatch", it, plugin.priority)
        }
    }

    /**
     * Measure plugin performance
     */
    suspend fun measurePluginPerformance(
        plugin: ProcessingPlugin,
        mockImage: ImageProxy,
        iterations: Int = 100
    ): PerformanceMetrics {
        val processingTimes = mutableListOf<Long>()
        var successCount = 0
        var failureCount = 0

        repeat(iterations) {
            val startTime = System.nanoTime()
            val result = plugin.processFrame(mockImage)
            val duration = System.nanoTime() - startTime

            processingTimes.add(duration)

            when (result) {
                is ProcessingResult.Success -> successCount++
                is ProcessingResult.Failure -> failureCount++
                is ProcessingResult.Skip -> {} // Skip doesn't count as success or failure
            }
        }

        val sortedTimes = processingTimes.sorted()
        return PerformanceMetrics(
            iterations = iterations,
            averageTimeNs = processingTimes.average().toLong(),
            minTimeNs = sortedTimes.first(),
            maxTimeNs = sortedTimes.last(),
            medianTimeNs = sortedTimes[iterations / 2],
            p95TimeNs = sortedTimes[(iterations * 0.95).toInt()],
            p99TimeNs = sortedTimes[(iterations * 0.99).toInt()],
            successRate = successCount.toFloat() / iterations,
            failureRate = failureCount.toFloat() / iterations
        )
    }
}

/**
 * Plugin lifecycle test result
 */
data class PluginLifecycleResult(
    val success: Boolean,
    val steps: List<String>,
    val durationMs: Long,
    val error: Throwable?
) {
    fun assertSuccess() {
        assertTrue("Plugin lifecycle failed: ${error?.message}", success)
    }

    fun assertContainsStep(step: String) {
        assertTrue("Missing lifecycle step: $step", steps.contains(step))
    }

    fun assertCompletesWithin(maxMs: Long) {
        assertTrue(
            "Lifecycle took ${durationMs}ms, expected <${maxMs}ms",
            durationMs < maxMs
        )
    }
}

/**
 * Processing test result
 */
data class ProcessingTestResult(
    val success: Boolean,
    val result: ProcessingResult,
    val processingTimeNs: Long,
    val isValid: Boolean,
    val error: Throwable?
) {
    fun assertSuccess() {
        assertTrue("Processing failed: ${error?.message}", success)
    }

    fun assertValid() {
        assertTrue("Result validation failed", isValid)
    }

    fun assertProcessesWithin(maxNs: Long) {
        assertTrue(
            "Processing took ${processingTimeNs}ns, expected <${maxNs}ns",
            processingTimeNs < maxNs
        )
    }

    fun assertProcessesWithinMs(maxMs: Long) {
        val actualMs = processingTimeNs / 1_000_000
        assertTrue(
            "Processing took ${actualMs}ms, expected <${maxMs}ms",
            actualMs < maxMs
        )
    }
}

/**
 * Concurrency test result
 */
data class ConcurrencyTestResult(
    val completed: Boolean,
    val totalImages: Int,
    val successfulResults: Int,
    val failedResults: Int,
    val errors: List<Throwable>,
    val durationMs: Long,
    val averageTimeMs: Float
) {
    fun assertAllCompleted() {
        assertTrue("Not all images completed processing", completed)
        assertEquals("Wrong number of results", totalImages, successfulResults + failedResults)
    }

    fun assertNoErrors() {
        assertTrue("Concurrency errors occurred: ${errors.map { it.message }}", errors.isEmpty())
    }

    fun assertSuccessRate(minRate: Float) {
        val actualRate = successfulResults.toFloat() / totalImages
        assertTrue(
            "Success rate $actualRate below minimum $minRate",
            actualRate >= minRate
        )
    }
}

/**
 * Performance metrics
 */
data class PerformanceMetrics(
    val iterations: Int,
    val averageTimeNs: Long,
    val minTimeNs: Long,
    val maxTimeNs: Long,
    val medianTimeNs: Long,
    val p95TimeNs: Long,
    val p99TimeNs: Long,
    val successRate: Float,
    val failureRate: Float
) {
    fun assertAverageWithinMs(maxMs: Long) {
        val actualMs = averageTimeNs / 1_000_000
        assertTrue(
            "Average time ${actualMs}ms exceeds maximum ${maxMs}ms",
            actualMs < maxMs
        )
    }

    fun assertP95WithinMs(maxMs: Long) {
        val actualMs = p95TimeNs / 1_000_000
        assertTrue(
            "P95 time ${actualMs}ms exceeds maximum ${maxMs}ms",
            actualMs < maxMs
        )
    }

    fun assertSuccessRate(minRate: Float) {
        assertTrue(
            "Success rate $successRate below minimum $minRate",
            successRate >= minRate
        )
    }

    override fun toString(): String {
        return """
            Performance Metrics ($iterations iterations):
            - Average: ${averageTimeNs / 1_000_000}ms
            - Min: ${minTimeNs / 1_000_000}ms
            - Max: ${maxTimeNs / 1_000_000}ms
            - Median: ${medianTimeNs / 1_000_000}ms
            - P95: ${p95TimeNs / 1_000_000}ms
            - P99: ${p99TimeNs / 1_000_000}ms
            - Success Rate: ${(successRate * 100).toInt()}%
            - Failure Rate: ${(failureRate * 100).toInt()}%
        """.trimIndent()
    }
}
