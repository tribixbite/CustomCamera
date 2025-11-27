# Settings Architecture - Reactive StateFlow System

**Last Updated**: 2025-11-06
**Status**: 16/20 settings reactive (80% complete)

## Overview

The settings system uses a hybrid approach combining SharedPreferences for persistence with Kotlin StateFlow for reactive state management. This allows UI components to observe settings changes without manual refresh or broadcast receivers.

## Architecture Pattern

```
SharedPreferences (persistence)
        ↓
SettingsManager (singleton)
        ↓
StateFlow (reactive state)
        ↓
UI Components (observers)
```

## Reactive Settings (16/20 - 80%)

Settings with StateFlow support for automatic UI updates:

### Camera Settings (2)
- **defaultCameraIndex**: `StateFlow<Int>` - Main camera selection (0-3)
- **pipCameraIndex**: `StateFlow<Int>` - PiP secondary camera selection

### Photo Settings (3)
- **photoQuality**: `StateFlow<Int>` - JPEG quality (1-100)
- **photoResolution**: `StateFlow<String>` - Resolution (auto/4K/1080p/720p)
- **flashMode**: `StateFlow<String>` - Flash mode (auto/on/off/torch)

### Video Settings (2)
- **videoQuality**: `StateFlow<String>` - Recording quality (4K/1080p/720p/480p)
- **videoStabilization**: `StateFlow<Boolean>` - Video stabilization enable

### Focus Settings (2)
- **autoFocusMode**: `StateFlow<String>` - AF mode (continuous/single/off)
- **tapToFocus**: `StateFlow<Boolean>` - Tap-to-focus enable

### UI Overlay Settings (4)
- **gridOverlay**: `StateFlow<Boolean>` - Grid overlay default state
- **histogramOverlay**: `StateFlow<Boolean>` - Histogram overlay enable
- **cameraInfoOverlay**: `StateFlow<Boolean>` - Camera info overlay enable
- **levelIndicator**: `StateFlow<Boolean>` - Device level indicator enable

### Advanced Settings (3)
- **debugLogging**: `StateFlow<Boolean>` - Debug logging enable
- **performanceMonitoring**: `StateFlow<Boolean>` - Performance monitor enable
- **rawCapture**: `StateFlow<Boolean>` - RAW/DNG capture enable

## Non-Reactive Settings (4/20 - 20%)

Settings without StateFlow (use getter/setter pattern only):

### Plugin-Specific Settings
- **Plugin Enable/Disable**: Via `getPluginStateFlow(pluginName)` - Dynamic StateFlow map
- **Plugin Custom Settings**: Via `getPluginSetting(pluginName, key, defaultValue)` - No StateFlow

Plugin enable/disable states use a dynamic StateFlow map that creates StateFlows on-demand via `getPluginStateFlow()`.

## Usage Patterns

### Setting a Value (Updates Both Persistence and StateFlow)

```kotlin
val settingsManager = SettingsManager.getInstance(context)

// Update setting - automatically updates SharedPreferences AND StateFlow
settingsManager.setVideoQuality("1080p")
settingsManager.setRawCapture(true)
settingsManager.setPhotoResolution("4K")
```

### Observing Changes (Reactive UI)

```kotlin
// Collect StateFlow in coroutine
lifecycleScope.launch {
    settingsManager.videoQuality.collect { quality ->
        updateVideoQualityUI(quality)
    }
}

// Or use Flow operators
settingsManager.photoQuality
    .map { quality -> "$quality%" }
    .onEach { text -> qualityLabel.text = text }
    .launchIn(lifecycleScope)
```

### Plugin State Observation

```kotlin
// Get StateFlow for specific plugin
val histogramState = settingsManager.getPluginStateFlow("Histogram")

lifecycleScope.launch {
    histogramState.collect { enabled ->
        if (enabled) {
            showHistogramOverlay()
        } else {
            hideHistogramOverlay()
        }
    }
}
```

### Reading Current Value (Synchronous)

```kotlin
// Direct access to current StateFlow value
val currentQuality = settingsManager.videoQuality.value

// Or use getter method (reads from SharedPreferences)
val quality = settingsManager.getVideoQuality()
```

## Implementation Details

### Singleton Pattern

```kotlin
companion object {
    @Volatile
    private var INSTANCE: SettingsManager? = null

    fun getInstance(context: Context): SettingsManager {
        return INSTANCE ?: synchronized(this) {
            INSTANCE ?: SettingsManager(context.applicationContext).also {
                INSTANCE = it
            }
        }
    }
}
```

**Benefits:**
- Single source of truth across all activities
- No settings drift between components
- Prevents memory leaks with applicationContext

### StateFlow Declaration Pattern

```kotlin
// Private mutable StateFlow (internal state)
private val _videoQuality = MutableStateFlow(getString(KEY_VIDEO_QUALITY, "1080p"))

// Public immutable StateFlow (exposed to observers)
val videoQuality: StateFlow<String> = _videoQuality.asStateFlow()
```

### Setter Pattern (Updates Both Stores)

```kotlin
fun setVideoQuality(quality: String) {
    // 1. Persist to SharedPreferences
    putString(KEY_VIDEO_QUALITY, quality)

    // 2. Update reactive StateFlow
    _videoQuality.value = quality

    // 3. Log change
    Log.i(TAG, "Video quality set to: $quality")
}
```

### Import/Export with StateFlow Refresh

```kotlin
fun importSettings(settings: Map<String, Any>) {
    val editor = prefs.edit()
    settings.forEach { (key, value) ->
        when (value) {
            is String -> editor.putString(key, value)
            is Int -> editor.putInt(key, value)
            is Boolean -> editor.putBoolean(key, value)
            // ... other types
        }
    }
    editor.apply()

    // Refresh ALL StateFlows after import
    refreshStateFlows()
}

private fun refreshStateFlows() {
    _defaultCameraIndex.value = getInt(KEY_DEFAULT_CAMERA_INDEX, 0)
    _videoQuality.value = getString(KEY_VIDEO_QUALITY, "1080p")
    _rawCapture.value = getBoolean(KEY_RAW_CAPTURE, false)
    // ... all 16 StateFlows
}
```

## Benefits of StateFlow Architecture

### 1. Automatic UI Updates
```kotlin
// UI updates automatically when setting changes
settingsManager.setVideoQuality("4K")
// All collectors of videoQuality StateFlow receive "4K"
```

### 2. No Broadcast Receivers Needed
```kotlin
// OLD WAY (deprecated):
context.sendBroadcast(Intent(ACTION_SETTINGS_CHANGED))

// NEW WAY (reactive):
settingsManager.setVideoQuality("4K")  // StateFlow notifies observers
```

### 3. Type Safety
```kotlin
// Compile-time type checking
val quality: StateFlow<String> = settingsManager.videoQuality
val enabled: StateFlow<Boolean> = settingsManager.rawCapture
```

### 4. Lifecycle-Aware
```kotlin
lifecycleScope.launch {
    settingsManager.videoQuality.collect { quality ->
        // Automatically stops collecting when lifecycle destroyed
    }
}
```

### 5. Backpressure Handling
```kotlin
// StateFlow conflates - only latest value matters
settingsManager.setVideoQuality("4K")
settingsManager.setVideoQuality("1080p")
settingsManager.setVideoQuality("720p")
// Observers only receive "720p" (latest)
```

## Migration Checklist

When adding a new setting with StateFlow support:

1. **Add private MutableStateFlow**:
   ```kotlin
   private val _newSetting = MutableStateFlow(getBoolean(KEY_NEW_SETTING, false))
   ```

2. **Add public StateFlow exposure**:
   ```kotlin
   val newSetting: StateFlow<Boolean> = _newSetting.asStateFlow()
   ```

3. **Update setter to modify StateFlow**:
   ```kotlin
   fun setNewSetting(value: Boolean) {
       putBoolean(KEY_NEW_SETTING, value)
       _newSetting.value = value  // ADD THIS LINE
       Log.i(TAG, "New setting: $value")
   }
   ```

4. **Add to refreshStateFlows()**:
   ```kotlin
   private fun refreshStateFlows() {
       // ... existing flows ...
       _newSetting.value = getBoolean(KEY_NEW_SETTING, false)  // ADD THIS LINE
   }
   ```

5. **Update SettingsSummary data class** (if applicable):
   ```kotlin
   data class SettingsSummary(
       // ... existing fields ...
       val newSetting: Boolean  // ADD THIS LINE
   )
   ```

## Testing StateFlow Settings

```kotlin
@Test
fun testVideoQualityStateFlow() = runTest {
    val settingsManager = SettingsManager.getInstance(context)
    val collectedValues = mutableListOf<String>()

    // Collect StateFlow values
    val job = launch {
        settingsManager.videoQuality.collect { quality ->
            collectedValues.add(quality)
        }
    }

    // Change setting
    settingsManager.setVideoQuality("4K")
    advanceUntilIdle()

    // Verify StateFlow updated
    assertEquals("4K", collectedValues.last())
    assertEquals("4K", settingsManager.videoQuality.value)

    job.cancel()
}
```

## Performance Considerations

### StateFlow Memory Overhead
- Each StateFlow: ~100 bytes
- 16 StateFlows: ~1.6 KB
- Plugin StateFlows (dynamic): ~100 bytes × active plugins
- **Total overhead**: < 5 KB (negligible)

### Collector Performance
- StateFlow uses conflation (only latest value)
- No queue buildup for rapid changes
- Automatic cleanup when collectors cancel

### Cold vs. Hot Flow
- StateFlow is HOT (always active)
- Immediately emits current value to new collectors
- Survives configuration changes (singleton)

## Future Improvements

1. **Complete Migration**: Add StateFlows for remaining 4 plugin-specific settings
2. **Testing**: Add StateFlow tests for all 16 reactive settings
3. **Documentation**: Add KDoc comments to all StateFlow properties
4. **Migration Guide**: Document migration from broadcast-based to StateFlow-based updates

## Reference

- **Implementation**: `app/src/main/java/com/customcamera/app/engine/SettingsManager.kt`
- **Fix History**: `SETTINGS_FIXES_SUMMARY.md`
- **Testing**: `DEVICE_TESTING_CHECKLIST.md`
- **Architecture**: `docs/ARCHITECTURE.md`

---

**Last Updated**: 2025-11-06
**StateFlow Coverage**: 16/20 settings (80%)
**Status**: Production-ready for device testing
