package com.customcamera.app.engine

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages plugin usage statistics tracking and persistence.
 *
 * Tracks activation, duration, success rates, and performance metrics
 * for all plugins with minimal performance overhead.
 *
 * Performance Targets:
 * - < 1ms overhead per operation
 * - < 50KB total storage
 * - Lazy persistence (batch writes every 30s or on pause)
 */
class PluginStatisticsManager(private val context: Context) {

    companion object {
        private const val TAG = "PluginStatisticsManager"
        private const val PREFS_NAME = "plugin_statistics"
        private const val KEY_ALL_STATS = "all_statistics"

        // Persistence strategy
        private const val BATCH_WRITE_INTERVAL_MS = 30_000L // 30 seconds
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // In-memory cache for fast access
    private val statisticsCache = ConcurrentHashMap<String, PluginStatistics>()

    // Track active sessions (plugin name -> activation timestamp)
    private val activeSessions = ConcurrentHashMap<String, Long>()

    // Dirty flag for lazy persistence
    private var isDirty = false
    private var lastPersistTime = 0L

    init {
        // Load statistics from SharedPreferences on initialization
        loadStatistics()
    }

    /**
     * Record plugin activation event.
     * Updates activation count and starts duration tracking.
     */
    fun recordActivation(pluginName: String) {
        val now = System.currentTimeMillis()
        val stats = getOrCreateStatistics(pluginName)

        // Update activation metrics
        val updated = stats.copy(
            totalActivations = stats.totalActivations + 1,
            currentlyActive = true,
            firstUsedTimestamp = if (stats.firstUsedTimestamp == 0L) now else stats.firstUsedTimestamp,
            lastUsedTimestamp = now
        )

        statisticsCache[pluginName] = updated
        activeSessions[pluginName] = now
        isDirty = true

        persistIfNeeded()

        Log.d(TAG, "Recorded activation for $pluginName (${updated.totalActivations} total)")
    }

    /**
     * Record plugin deactivation event.
     * Updates duration metrics and ends session tracking.
     */
    fun recordDeactivation(pluginName: String) {
        val now = System.currentTimeMillis()
        val activationTime = activeSessions.remove(pluginName) ?: return
        val sessionDuration = now - activationTime

        val stats = getOrCreateStatistics(pluginName)

        // Update duration metrics
        val totalTime = stats.totalActiveTimeMs + sessionDuration
        val avgDuration = totalTime / stats.totalActivations
        val longestDuration = maxOf(stats.longestSessionDurationMs, sessionDuration)

        val updated = stats.copy(
            currentlyActive = false,
            totalActiveTimeMs = totalTime,
            averageSessionDurationMs = avgDuration,
            longestSessionDurationMs = longestDuration,
            lastUsedTimestamp = now
        )

        statisticsCache[pluginName] = updated
        isDirty = true

        persistIfNeeded()

        Log.d(TAG, "Recorded deactivation for $pluginName (session: ${sessionDuration}ms)")
    }

    /**
     * Record plugin operation result and duration.
     * Updates success rate and performance metrics.
     */
    fun recordOperation(pluginName: String, success: Boolean, durationMs: Long) {
        val stats = getOrCreateStatistics(pluginName)

        // Update success metrics
        val successOps = if (success) stats.successfulOperations + 1 else stats.successfulOperations
        val failedOps = if (!success) stats.failedOperations + 1 else stats.failedOperations
        val totalOps = successOps + failedOps
        val successRate = if (totalOps > 0) successOps.toFloat() / totalOps else 0f

        // Update performance metrics
        val totalProcessingTime = (stats.averageProcessingTimeMs * (totalOps - 1)) + durationMs
        val avgProcessingTime = totalProcessingTime / totalOps
        val maxProcessingTime = maxOf(stats.maxProcessingTimeMs, durationMs)

        val updated = stats.copy(
            successfulOperations = successOps,
            failedOperations = failedOps,
            successRate = successRate,
            averageProcessingTimeMs = avgProcessingTime,
            maxProcessingTimeMs = maxProcessingTime
        )

        statisticsCache[pluginName] = updated
        isDirty = true

        persistIfNeeded()
    }

    /**
     * Get statistics for a specific plugin.
     * Returns statistics with computed scores.
     */
    fun getPluginStatistics(pluginName: String): PluginStatistics {
        return computeScores(getOrCreateStatistics(pluginName))
    }

    /**
     * Get statistics for all plugins.
     * Returns list sorted by usage frequency score.
     */
    fun getAllStatistics(): List<PluginStatistics> {
        return statisticsCache.values
            .map { computeScores(it) }
            .sortedByDescending { it.usageFrequencyScore }
    }

    /**
     * Get most frequently used plugins.
     */
    fun getMostUsedPlugins(limit: Int = 5): List<PluginStatistics> {
        return getAllStatistics().take(limit)
    }

    /**
     * Get plugins with lowest reliability (for debugging).
     */
    fun getLeastReliablePlugins(limit: Int = 5): List<PluginStatistics> {
        return statisticsCache.values
            .map { computeScores(it) }
            .filter { it.successfulOperations + it.failedOperations > 0 }
            .sortedBy { it.reliabilityScore }
            .take(limit)
    }

    /**
     * Get total activations across all plugins.
     */
    fun getTotalActivations(): Int {
        return statisticsCache.values.sumOf { it.totalActivations }
    }

    /**
     * Get total active time across all plugins.
     */
    fun getTotalActiveTime(): Long {
        return statisticsCache.values.sumOf { it.totalActiveTimeMs }
    }

    /**
     * Get overall success rate across all plugins.
     */
    fun getOverallSuccessRate(): Float {
        val totalSuccess = statisticsCache.values.sumOf { it.successfulOperations }
        val totalFailed = statisticsCache.values.sumOf { it.failedOperations }
        val total = totalSuccess + totalFailed
        return if (total > 0) totalSuccess.toFloat() / total else 0f
    }

    /**
     * Reset statistics for a specific plugin or all plugins.
     * @param pluginName Plugin to reset, or null to reset all
     */
    suspend fun resetStatistics(pluginName: String? = null) = withContext(Dispatchers.IO) {
        if (pluginName != null) {
            statisticsCache.remove(pluginName)
            activeSessions.remove(pluginName)
            Log.i(TAG, "Reset statistics for $pluginName")
        } else {
            statisticsCache.clear()
            activeSessions.clear()
            Log.i(TAG, "Reset all statistics")
        }

        isDirty = true
        persistNow()
    }

    /**
     * Export all statistics to JSON string.
     */
    fun exportStatistics(): String {
        val jsonArray = JSONArray()

        statisticsCache.values.forEach { stats ->
            val statsWithScores = computeScores(stats)
            jsonArray.put(statsWithScores.toJson())
        }

        return jsonArray.toString(2) // Pretty print with 2-space indent
    }

    /**
     * Import statistics from JSON string.
     * Merges with existing statistics (keeps higher values).
     */
    suspend fun importStatistics(json: String) = withContext(Dispatchers.IO) {
        try {
            val jsonArray = JSONArray(json)
            var importCount = 0

            for (i in 0 until jsonArray.length()) {
                val jsonObj = jsonArray.getJSONObject(i)
                val stats = PluginStatistics.fromJson(jsonObj)

                // Merge with existing statistics (keep higher values)
                val existing = statisticsCache[stats.pluginName]
                val merged = if (existing != null) {
                    stats.copy(
                        totalActivations = maxOf(stats.totalActivations, existing.totalActivations),
                        totalActiveTimeMs = maxOf(stats.totalActiveTimeMs, existing.totalActiveTimeMs),
                        successfulOperations = maxOf(stats.successfulOperations, existing.successfulOperations),
                        failedOperations = maxOf(stats.failedOperations, existing.failedOperations)
                    )
                } else {
                    stats
                }

                statisticsCache[merged.pluginName] = merged
                importCount++
            }

            isDirty = true
            persistNow()

            Log.i(TAG, "Imported $importCount plugin statistics")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import statistics", e)
            throw e
        }
    }

    /**
     * Persist statistics on app pause.
     * Call this from Activity onPause() or Service onDestroy().
     */
    suspend fun onAppPause() = withContext(Dispatchers.IO) {
        // End all active sessions
        val now = System.currentTimeMillis()
        activeSessions.keys.forEach { pluginName ->
            recordDeactivation(pluginName)
        }

        // Force persist
        persistNow()
    }

    // ========== Private Helper Methods ==========

    private fun getOrCreateStatistics(pluginName: String): PluginStatistics {
        return statisticsCache.getOrPut(pluginName) {
            PluginStatistics(pluginName = pluginName)
        }
    }

    private fun computeScores(stats: PluginStatistics): PluginStatistics {
        // Usage frequency score (0-10): weighted combination of activations and time
        val normalizedActivations = minOf(stats.totalActivations / 10f, 10f)
        val normalizedTime = minOf(stats.totalActiveTimeMs / 60_000f, 10f) // Normalize to minutes
        val usageScore = (normalizedActivations * 0.6f) + (normalizedTime * 0.4f)

        // Reliability score (0-10): based on success rate
        val reliabilityScore = stats.successRate * 10f

        return stats.copy(
            usageFrequencyScore = usageScore,
            reliabilityScore = reliabilityScore
        )
    }

    private fun persistIfNeeded() {
        if (!isDirty) return

        val now = System.currentTimeMillis()
        if (now - lastPersistTime >= BATCH_WRITE_INTERVAL_MS) {
            persistNow()
        }
    }

    private fun persistNow() {
        if (!isDirty) return

        try {
            val jsonArray = JSONArray()
            statisticsCache.values.forEach { stats ->
                jsonArray.put(stats.toJson())
            }

            prefs.edit()
                .putString(KEY_ALL_STATS, jsonArray.toString())
                .apply()

            isDirty = false
            lastPersistTime = System.currentTimeMillis()

            Log.d(TAG, "Persisted statistics for ${statisticsCache.size} plugins")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist statistics", e)
        }
    }

    private fun loadStatistics() {
        try {
            val json = prefs.getString(KEY_ALL_STATS, null) ?: return
            val jsonArray = JSONArray(json)

            for (i in 0 until jsonArray.length()) {
                val jsonObj = jsonArray.getJSONObject(i)
                val stats = PluginStatistics.fromJson(jsonObj)
                statisticsCache[stats.pluginName] = stats
            }

            Log.i(TAG, "Loaded statistics for ${statisticsCache.size} plugins")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load statistics", e)
        }
    }
}

/**
 * Data class representing plugin usage statistics.
 */
data class PluginStatistics(
    val pluginName: String,

    // Activation metrics
    val totalActivations: Int = 0,
    val currentlyActive: Boolean = false,
    val firstUsedTimestamp: Long = 0L,
    val lastUsedTimestamp: Long = 0L,

    // Duration metrics
    val totalActiveTimeMs: Long = 0L,
    val averageSessionDurationMs: Long = 0L,
    val longestSessionDurationMs: Long = 0L,

    // Success metrics
    val successfulOperations: Int = 0,
    val failedOperations: Int = 0,
    val successRate: Float = 0f,

    // Performance metrics
    val averageProcessingTimeMs: Long = 0L,
    val maxProcessingTimeMs: Long = 0L,

    // Computed metrics
    val usageFrequencyScore: Float = 0f, // 0-10 scale
    val reliabilityScore: Float = 0f      // 0-10 scale
) {

    companion object {
        private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

        fun fromJson(json: JSONObject): PluginStatistics {
            return PluginStatistics(
                pluginName = json.getString("pluginName"),
                totalActivations = json.optInt("totalActivations", 0),
                currentlyActive = json.optBoolean("currentlyActive", false),
                firstUsedTimestamp = json.optLong("firstUsedTimestamp", 0L),
                lastUsedTimestamp = json.optLong("lastUsedTimestamp", 0L),
                totalActiveTimeMs = json.optLong("totalActiveTimeMs", 0L),
                averageSessionDurationMs = json.optLong("averageSessionDurationMs", 0L),
                longestSessionDurationMs = json.optLong("longestSessionDurationMs", 0L),
                successfulOperations = json.optInt("successfulOperations", 0),
                failedOperations = json.optInt("failedOperations", 0),
                successRate = json.optDouble("successRate", 0.0).toFloat(),
                averageProcessingTimeMs = json.optLong("averageProcessingTimeMs", 0L),
                maxProcessingTimeMs = json.optLong("maxProcessingTimeMs", 0L),
                usageFrequencyScore = json.optDouble("usageFrequencyScore", 0.0).toFloat(),
                reliabilityScore = json.optDouble("reliabilityScore", 0.0).toFloat()
            )
        }
    }

    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("pluginName", pluginName)
            put("totalActivations", totalActivations)
            put("currentlyActive", currentlyActive)
            put("firstUsedTimestamp", firstUsedTimestamp)
            put("lastUsedTimestamp", lastUsedTimestamp)
            put("totalActiveTimeMs", totalActiveTimeMs)
            put("averageSessionDurationMs", averageSessionDurationMs)
            put("longestSessionDurationMs", longestSessionDurationMs)
            put("successfulOperations", successfulOperations)
            put("failedOperations", failedOperations)
            put("successRate", successRate.toDouble())
            put("averageProcessingTimeMs", averageProcessingTimeMs)
            put("maxProcessingTimeMs", maxProcessingTimeMs)
            put("usageFrequencyScore", usageFrequencyScore.toDouble())
            put("reliabilityScore", reliabilityScore.toDouble())
        }
    }

    /**
     * Get formatted first used date.
     */
    fun getFirstUsedFormatted(): String {
        return if (firstUsedTimestamp > 0) {
            dateFormat.format(Date(firstUsedTimestamp))
        } else {
            "Never"
        }
    }

    /**
     * Get formatted last used date.
     */
    fun getLastUsedFormatted(): String {
        return if (lastUsedTimestamp > 0) {
            dateFormat.format(Date(lastUsedTimestamp))
        } else {
            "Never"
        }
    }

    /**
     * Get formatted total active time.
     */
    fun getTotalActiveTimeFormatted(): String {
        val seconds = totalActiveTimeMs / 1000
        val minutes = seconds / 60
        val hours = minutes / 60

        return when {
            hours > 0 -> "${hours}h ${minutes % 60}m"
            minutes > 0 -> "${minutes}m ${seconds % 60}s"
            else -> "${seconds}s"
        }
    }

    /**
     * Get formatted average session duration.
     */
    fun getAverageSessionFormatted(): String {
        val seconds = averageSessionDurationMs / 1000
        return if (seconds > 60) {
            "${seconds / 60}m ${seconds % 60}s"
        } else {
            "${seconds}s"
        }
    }
}
