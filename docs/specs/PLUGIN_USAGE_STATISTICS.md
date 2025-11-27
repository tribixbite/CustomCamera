# Plugin Usage Statistics - Feature Specification

**Feature ID**: P3-001
**Priority**: P3 (Enhancement)
**Status**: Proposed
**Target Version**: 2.4.0
**Estimated Effort**: 2-3 hours
**Dependencies**: None (uses existing plugin system)

## Overview

Track and display plugin usage statistics to provide insights into feature utilization patterns. This enables data-driven decisions for plugin development and helps users understand their camera usage habits.

## Goals

### Primary Goals
1. **Track Plugin Activation** - Record when plugins are enabled/disabled
2. **Measure Usage Duration** - Track how long plugins remain active
3. **Count Usage Frequency** - Track number of activations per plugin
4. **Monitor Success Rates** - Track plugin operation success vs failure

### Secondary Goals
1. **Display Statistics** - Show usage data in Settings UI
2. **Export Statistics** - Include stats in plugin configuration export
3. **Reset Capability** - Allow users to clear statistics
4. **Performance Impact** - Minimal overhead (< 1ms per operation)

## User Stories

### As a User
- I want to see which plugins I use most frequently
- I want to understand my camera usage patterns
- I want to know if any plugins are causing issues
- I want to share usage statistics with developers for improvement

### As a Developer
- I want to identify most/least used plugins
- I want to prioritize development based on actual usage
- I want to detect problematic plugins with high failure rates
- I want to validate plugin performance in production

## Data Model

### PluginStatistics Data Class

```kotlin
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
    val usageFrequencyScore: Float = 0f, // Based on activations + duration
    val reliabilityScore: Float = 0f     // Based on success rate
)
```

### Storage Schema

**SharedPreferences Key Format**: `plugin_stats_{pluginName}`

**JSON Format**:
```json
{
  "pluginName": "GridOverlay",
  "totalActivations": 47,
  "currentlyActive": true,
  "firstUsedTimestamp": 1732647840000,
  "lastUsedTimestamp": 1732734240000,
  "totalActiveTimeMs": 125600,
  "averageSessionDurationMs": 2672,
  "longestSessionDurationMs": 15800,
  "successfulOperations": 142,
  "failedOperations": 3,
  "successRate": 0.979,
  "averageProcessingTimeMs": 12,
  "maxProcessingTimeMs": 45,
  "usageFrequencyScore": 8.7,
  "reliabilityScore": 9.8
}
```

## Implementation Components

### 1. PluginStatisticsManager

**Location**: `app/src/main/java/com/customcamera/app/engine/PluginStatisticsManager.kt`

**Responsibilities**:
- Track plugin activation/deactivation events
- Measure plugin operation duration and success
- Persist statistics to SharedPreferences
- Provide statistics retrieval and aggregation
- Calculate derived metrics (scores, averages, etc.)

**Core Methods**:
```kotlin
class PluginStatisticsManager(private val context: Context) {

    // Track events
    fun recordActivation(pluginName: String)
    fun recordDeactivation(pluginName: String)
    fun recordOperation(pluginName: String, success: Boolean, durationMs: Long)

    // Retrieve statistics
    fun getPluginStatistics(pluginName: String): PluginStatistics
    fun getAllStatistics(): List<PluginStatistics>
    fun getMostUsedPlugins(limit: Int = 5): List<PluginStatistics>
    fun getLeastReliablePlugins(limit: Int = 5): List<PluginStatistics>

    // Aggregate metrics
    fun getTotalActivations(): Int
    fun getTotalActiveTime(): Long
    fun getOverallSuccessRate(): Float

    // Management
    fun resetStatistics(pluginName: String? = null) // null = reset all
    fun exportStatistics(): String // JSON export
    fun importStatistics(json: String)
}
```

### 2. PluginManager Integration

**Modification**: `app/src/main/java/com/customcamera/app/engine/plugins/PluginManager.kt`

**Changes**:
- Add `PluginStatisticsManager` instance
- Call `recordActivation()` when plugin is enabled
- Call `recordDeactivation()` when plugin is disabled
- Call `recordOperation()` after each plugin operation
- Measure processing time for each plugin

**Example Integration**:
```kotlin
class PluginManager(/* ... */) {
    private val statisticsManager = PluginStatisticsManager(context)

    suspend fun enablePlugin(pluginName: String) {
        // Existing enable logic...
        statisticsManager.recordActivation(pluginName)
    }

    suspend fun disablePlugin(pluginName: String) {
        // Existing disable logic...
        statisticsManager.recordDeactivation(pluginName)
    }

    suspend fun processFrame(image: ImageProxy) {
        activePlugins.forEach { plugin ->
            val startTime = System.currentTimeMillis()
            val result = try {
                plugin.processFrame(image)
                true
            } catch (e: Exception) {
                Log.e(TAG, "Plugin ${plugin.name} failed", e)
                false
            }
            val duration = System.currentTimeMillis() - startTime
            statisticsManager.recordOperation(plugin.name, result, duration)
        }
    }
}
```

### 3. Settings UI Integration

**Modification**: `app/src/main/java/com/customcamera/app/SettingsActivity.kt`

**New Section**: "Plugin Statistics" (Section 11)

**UI Components**:
1. **Summary Card**:
   - Total plugin activations
   - Total active time (formatted: "2h 15m")
   - Overall success rate percentage
   - Most used plugin name

2. **View Statistics Button**:
   - Opens detailed statistics dialog/screen
   - Shows all plugins sorted by usage frequency
   - Displays per-plugin metrics (activations, success rate, avg duration)

3. **Reset Statistics Button**:
   - Confirmation dialog before reset
   - Clears all statistics data

**Example UI**:
```kotlin
private fun createPluginStatisticsSection(container: LinearLayout) {
    val section = createSection(container, "Plugin Statistics")

    // Summary card
    val summaryCard = createCard(section)
    val statistics = statisticsManager.getAllStatistics()
    val totalActivations = statistics.sumOf { it.totalActivations }
    val totalActiveTime = statistics.sumOf { it.totalActiveTimeMs }
    val mostUsed = statistics.maxByOrNull { it.usageFrequencyScore }

    addInfoRow(summaryCard, "Total Activations", totalActivations.toString())
    addInfoRow(summaryCard, "Total Active Time", formatDuration(totalActiveTime))
    addInfoRow(summaryCard, "Most Used Plugin", mostUsed?.pluginName ?: "None")

    // View statistics button
    addButton(section, "View Detailed Statistics") {
        showStatisticsDialog()
    }

    // Reset button
    addButton(section, "Reset Statistics") {
        showResetConfirmationDialog()
    }
}
```

### 4. Statistics Display Dialog

**Components**:
- **List of Plugins**: Sorted by usage frequency score (high to low)
- **Per-Plugin Display**:
  - Plugin name with enabled/disabled indicator
  - Activation count: "47 times"
  - Average session: "2.7 seconds"
  - Success rate: "97.9%" (with color: green > 95%, yellow 80-95%, red < 80%)
  - Total time: "2 minutes 5 seconds"

**Sort Options**:
- By Usage Frequency (default)
- By Reliability (success rate)
- By Total Activations
- By Total Time Active
- Alphabetically

### 5. Export Integration

**Modification**: Extend plugin configuration export to include statistics

**JSON Structure**:
```json
{
  "version": "1.0",
  "appVersion": "2.4.0",
  "appBuild": 41,
  "exportDate": 1732734240000,
  "exportDateFormatted": "2025-11-27 12:44:00",

  "pluginStates": { /* ... */ },
  "cameraSettings": { /* ... */ },
  "videoSettings": { /* ... */ },
  "focusSettings": { /* ... */ },
  "advancedSettings": { /* ... */ },

  "pluginStatistics": [
    {
      "pluginName": "GridOverlay",
      "totalActivations": 47,
      "totalActiveTimeMs": 125600,
      "successRate": 0.979,
      "usageFrequencyScore": 8.7
    },
    /* ... */
  ]
}
```

## Performance Considerations

### Optimization Strategies

1. **Lazy Persistence**:
   - Don't write to SharedPreferences on every event
   - Batch writes every 30 seconds or on app pause
   - Keep statistics in memory during active session

2. **Minimal Processing**:
   - Use simple counters and timestamps
   - Calculate derived metrics on-demand (not during tracking)
   - Avoid complex calculations in hot paths

3. **Efficient Storage**:
   - Use compact JSON format
   - Only store essential data
   - Compress long-term historical data

4. **Performance Target**:
   - < 1ms overhead per plugin operation
   - < 50KB total storage for all statistics
   - < 100ms for statistics UI refresh

### Measurement

```kotlin
// Measure overhead
val startTime = System.nanoTime()
statisticsManager.recordOperation(pluginName, success, durationMs)
val overhead = (System.nanoTime() - startTime) / 1_000_000 // Convert to ms
if (overhead > 1) {
    Log.w(TAG, "Statistics overhead: ${overhead}ms")
}
```

## Testing Strategy

### Unit Tests

1. **PluginStatisticsManager Tests**:
   - Test activation/deactivation tracking
   - Test operation success/failure counting
   - Test duration calculations
   - Test statistics retrieval and sorting
   - Test reset functionality
   - Test export/import

2. **PluginManager Integration Tests**:
   - Verify statistics are recorded on plugin enable
   - Verify statistics are recorded on plugin disable
   - Verify operation metrics are captured
   - Verify no statistics loss on app restart

### Manual Testing

1. **Basic Functionality**:
   - Enable/disable plugins and verify activation count increases
   - Use plugins and verify duration increases
   - Trigger plugin errors and verify failure count increases
   - Check that statistics persist across app restarts

2. **UI Verification**:
   - Open statistics screen and verify display
   - Sort by different criteria and verify order
   - Reset statistics and verify clearing
   - Export/import and verify statistics are included

3. **Performance Testing**:
   - Measure overhead with profiler
   - Verify no UI lag when updating statistics
   - Verify no memory leaks from statistics tracking

## UI/UX Design

### Statistics Screen Mockup

```
┌─────────────────────────────────────────┐
│  Plugin Statistics                      │
│                                         │
│  ┌───────────────────────────────────┐  │
│  │ Summary                           │  │
│  │ Total Activations: 234            │  │
│  │ Total Active Time: 12h 35m        │  │
│  │ Overall Success: 96.8%            │  │
│  │ Most Used: GridOverlay (47×)      │  │
│  └───────────────────────────────────┘  │
│                                         │
│  Sort by: [Usage ▼]                    │
│                                         │
│  ┌───────────────────────────────────┐  │
│  │ ✓ GridOverlay                     │  │
│  │   47 activations · 97.9% success  │  │
│  │   Avg: 2.7s · Total: 2m 5s        │  │
│  └───────────────────────────────────┘  │
│                                         │
│  ┌───────────────────────────────────┐  │
│  │ ○ Barcode                         │  │
│  │   12 activations · 100% success   │  │
│  │   Avg: 4.2s · Total: 50s          │  │
│  └───────────────────────────────────┘  │
│                                         │
│  ┌───────────────────────────────────┐  │
│  │ ✓ Histogram                       │  │
│  │   38 activations · 94.7% success  │  │
│  │   Avg: 1.1s · Total: 42s          │  │
│  └───────────────────────────────────┘  │
│                                         │
│  [View Detailed Report] [Reset Stats]  │
└─────────────────────────────────────────┘
```

## Future Enhancements (Post-v2.4.0)

### Phase 2 Features
1. **Usage Trends**: Graph showing plugin usage over time
2. **Recommendations**: Suggest plugins based on usage patterns
3. **Comparison**: Compare usage between different time periods
4. **Cloud Backup**: Sync statistics across devices
5. **Developer Mode**: Export detailed statistics for debugging

### Phase 3 Features
1. **Machine Learning**: Predict which plugins user will want next
2. **Usage Insights**: "You use Grid Overlay 3× more in landscape mode"
3. **Performance Alerts**: Notify if plugin performance degrades
4. **Usage Goals**: Gamification (e.g., "Try 10 different plugins")

## Security & Privacy

### Privacy Considerations
- **No PII**: Statistics contain no personally identifiable information
- **Local Storage**: All data stored locally, no cloud transmission
- **User Control**: Users can view, export, and reset statistics anytime
- **Opt-Out**: Consider making statistics tracking optional in settings

### Data Retention
- **No Automatic Cleanup**: Keep statistics indefinitely unless user resets
- **Export Before Reset**: Warn user to export before resetting
- **Backup Recommendation**: Include statistics in configuration backups

## Implementation Checklist

### Phase 1: Core Implementation (v2.4.0)
- [ ] Create `PluginStatisticsManager.kt`
- [ ] Implement data model and persistence
- [ ] Integrate with `PluginManager`
- [ ] Add unit tests for statistics manager
- [ ] Add integration tests for plugin manager

### Phase 2: UI Implementation
- [ ] Create statistics section in Settings
- [ ] Implement statistics display dialog
- [ ] Add sort and filter functionality
- [ ] Add reset confirmation dialog
- [ ] Test UI with various data sets

### Phase 3: Export/Import
- [ ] Extend plugin configuration export
- [ ] Update import to handle statistics
- [ ] Test round-trip export/import
- [ ] Verify statistics merge on import

### Phase 4: Documentation & Testing
- [ ] Update manual testing guide
- [ ] Add statistics testing procedures
- [ ] Document statistics format
- [ ] Create user documentation
- [ ] Performance testing and optimization

## Success Metrics

### Implementation Success
- Statistics tracking adds < 1ms overhead
- All 23 plugins statistics tracked correctly
- Statistics persist across app restarts
- UI displays statistics in < 100ms

### User Success
- Users can view their usage patterns
- Statistics help users discover underutilized plugins
- Export includes statistics for sharing/backup
- No performance degradation from statistics tracking

## Risks & Mitigation

### Risk 1: Performance Impact
**Mitigation**: Lazy persistence, batch writes, minimal processing

### Risk 2: Storage Growth
**Mitigation**: Compact JSON format, no historical archiving initially

### Risk 3: Privacy Concerns
**Mitigation**: Clear documentation, local storage only, user control

### Risk 4: Inaccurate Statistics
**Mitigation**: Comprehensive testing, validation checks, error handling

---

**Status**: Proposed
**Next Steps**: Review and approve specification
**Estimated Timeline**: 1-2 days for complete implementation
**Target Milestone**: v2.4.0 (next release)
