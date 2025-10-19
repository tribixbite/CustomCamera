# Plugin System Specification

## Feature Overview
**Feature Name**: Plugin System Architecture
**Priority**: P0
**Status**: Complete
**Target Version**: 2.0.0+

### Summary
Extensible plugin architecture using Provider Pattern for modular camera features with standardized lifecycle, metadata provision, and sequential image processing.

### Motivation
Enable rapid feature development without modifying core camera code. Provide single source of truth for plugin metadata, clean separation of concerns, and prevent resource exhaustion through controlled execution.

## Requirements

### Functional Requirements
1. **FR-1**: Provider Pattern for plugin metadata and instantiation
2. **FR-2**: Single source of truth (PluginRegistry) for all plugin definitions
3. **FR-3**: Plugin visibility control (showInDropdown, showInSettings)
4. **FR-4**: Standardized plugin lifecycle (init → bind → process → cleanup)
5. **FR-5**: Sequential image processing to prevent resource exhaustion
6. **FR-6**: Three plugin types: UIPlugin, ProcessingPlugin, ControlPlugin
7. **FR-7**: Priority-based execution order
8. **FR-8**: Enable/disable plugins at runtime
9. **FR-9**: Plugin state persistence across sessions
10. **FR-10**: Automatic ImageProxy cleanup (no memory leaks)

### Non-Functional Requirements
1. **NFR-1**: Performance - Sequential processing maintains 60fps target
2. **NFR-2**: Extensibility - Add new plugins without modifying core code
3. **NFR-3**: Maintainability - Single registry defines all plugins
4. **NFR-4**: Reliability - Proper error isolation (one plugin failure doesn't crash others)

### User Stories
- **As a** developer, **I want** to add new camera features via plugins, **so that** I don't modify core camera code
- **As a** user, **I want** to enable/disable features independently, **so that** I control app behavior
- **As a** developer, **I want** plugins to be discoverable and self-describing, **so that** UI can be generated automatically

## Technical Design

### Architecture
```
PluginRegistry (Provider Pattern - Single Source of Truth)
    ↓ provides metadata
PluginManager (Lifecycle & Execution)
    ↓ registers plugins
CameraEngine (Coordinator)
    ↓ binds to camera
Plugins (UIPlugin/ProcessingPlugin/ControlPlugin)
    ↓ process images
ImageAnalysis UseCase
```

### Component Breakdown

1. **PluginProvider Interface**
   - Provides plugin metadata (name, description, icon, category)
   - Creates plugin instances
   - Declares visibility (showInDropdown, showInSettings)

2. **PluginRegistry (Object)**
   - Single source of truth for all plugin definitions
   - Maps plugin IDs to PluginProvider instances
   - Filters plugins by visibility criteria
   - Provides plugin lists for UI generation

3. **PluginManager**
   - Registers plugins from PluginRegistry
   - Manages plugin lifecycle (initialize, bind, cleanup)
   - Coordinates sequential image processing
   - Handles plugin enable/disable state
   - Enforces execution priority order

4. **CameraEngine Integration**
   - Initializes PluginManager
   - Binds ImageAnalysis UseCase
   - Coordinates plugin processing pipeline
   - Provides camera context to plugins

5. **Plugin Base Classes**
   - **CameraPlugin**: Abstract base class
   - **UIPlugin**: Plugins with overlays (Grid, Crop, Barcode)
   - **ProcessingPlugin**: Image analysis (AI, Histogram, Motion)
   - **ControlPlugin**: Camera controls (ProControls, Focus, Exposure)

### Data Structures
```kotlin
// Provider interface
interface PluginProvider {
    val pluginId: String
    val name: String
    val description: String
    val icon: Int
    val category: PluginCategory
    val showInDropdown: Boolean
    val showInSettings: Boolean
    fun createPlugin(context: CameraContext): CameraPlugin
}

// Plugin categories
enum class PluginCategory {
    CORE,
    ANALYSIS,
    AI,
    ADVANCED_CAPTURE,
    CONTROL
}

// Plugin metadata for UI
data class PluginMetadata(
    val id: String,
    val name: String,
    val description: String,
    val icon: Int,
    val category: PluginCategory,
    val enabled: StateFlow<Boolean>
)

// Plugin state
data class PluginState(
    val pluginId: String,
    val enabled: Boolean,
    val priority: Int
)
```

### API/Interface Design
```kotlin
// PluginRegistry (single source of truth)
object PluginRegistry {
    fun getAllProviders(): List<PluginProvider>
    fun getDropdownProviders(): List<PluginProvider>
    fun getSettingsProviders(): List<PluginProvider>
    fun getProvider(pluginId: String): PluginProvider?
}

// PluginManager
class PluginManager {
    fun registerPlugin(plugin: CameraPlugin)
    fun processImage(image: ImageProxy)
    fun enablePlugin(pluginId: String)
    fun disablePlugin(pluginId: String)
    fun isEnabled(pluginId: String): Boolean
    fun getEnabledPlugins(): List<CameraPlugin>
    fun cleanup()
}

// Base plugin interface
abstract class CameraPlugin(
    val pluginId: String,
    protected val context: CameraContext
) {
    abstract suspend fun initialize()
    abstract suspend fun onCameraBound(camera: Camera)
    abstract suspend fun processImage(image: ImageProxy): PluginResult
    abstract fun cleanup()
    open val priority: Int = 100
}

// Plugin result types
sealed class PluginResult {
    object Success : PluginResult()
    data class WithData<T>(val data: T) : PluginResult()
    data class Error(val exception: Exception) : PluginResult()
}
```

### State Management
- **Plugin Enabled State**: StateFlow per plugin, persisted in SharedPreferences
- **Plugin Registry**: Static object, loaded at app start
- **Plugin Instances**: Created by PluginManager, lifecycle-aware
- **Processing Queue**: Sequential execution via PluginManager
- **UI State**: Reactive binding to plugin StateFlow

## Implementation Plan

### Phase 1: Foundation & Interfaces (Complete)
**Duration**: 1.5 hours
**Deliverables**:
- [x] PluginProvider interface
- [x] PluginCategory enum
- [x] Provider pattern design
- [x] Base plugin classes

### Phase 2: Example Implementations (Complete)
**Duration**: 0.5 hours
**Deliverables**:
- [x] GridOverlayPluginProvider
- [x] AutoFocusPluginProvider
- [x] Example provider pattern usage

### Phase 3: Batch Migration (Complete)
**Duration**: 0.5 hours
**Deliverables**:
- [x] 18 plugins migrated to provider pattern
- [x] All providers implement PluginProvider interface
- [x] Visibility flags configured

### Phase 4: Registry & Engine Refactoring (Complete)
**Duration**: 2.0 hours
**Deliverables**:
- [x] PluginRegistry object created
- [x] Single source of truth established
- [x] CameraEngine integration
- [x] PluginManager coordination

### Phase 5: UI Updates & Testing (Complete)
**Duration**: 1.0 hour
**Deliverables**:
- [x] Plugin dropdown menu generation
- [x] Settings UI integration
- [x] StateFlow reactive binding
- [x] Testing and validation

### Phase 6: RecyclerView Performance (Complete)
**Duration**: 3.0 hours
**Deliverables**:
- [x] RecyclerView-based plugin dropdown
- [x] ViewHolder pattern
- [x] DiffUtil for efficient updates
- [x] Material3 styling

### Phase 7: Icon Improvements (Complete)
**Duration**: 2.5 hours
**Deliverables**:
- [x] Plugin-specific icons
- [x] Category-based defaults
- [x] Visual consistency

### Phase 8: UI/UX Modernization (Complete)
**Duration**: 5.0 hours
**Deliverables**:
- [x] Material3 design
- [x] Smooth animations
- [x] Enhanced visual feedback
- [x] Smart filtering (15 shown, 6 excluded)

## Testing Strategy

### Unit Tests
- PluginRegistry returns correct providers
- Plugin visibility filtering works
- PluginProvider creates valid instances
- Plugin state persistence works
- Priority-based sorting correct

### Integration Tests
- Plugin lifecycle (init → bind → process → cleanup)
- Sequential processing maintains order
- Plugin enable/disable works at runtime
- ImageProxy cleanup prevents leaks
- Multiple plugins process same image correctly

### Performance Tests
- Sequential processing maintains 60fps
- Memory stable with all plugins enabled
- ImageProxy cleanup verified (no leaks)
- Plugin processing time < 16ms per frame

## Dependencies

### Internal Dependencies
- CameraEngine (coordinator)
- CameraContext (interface)
- SettingsManager (state persistence)

### External Dependencies
- Kotlin Coroutines 1.7.3
- StateFlow (reactive state)
- SharedPreferences (persistence)
- Material3 (UI components)

### Breaking Changes
- [x] All plugins must implement PluginProvider interface
- [x] PluginRegistry is now single source of truth
- [x] Dual activation methods removed (gestures + dropdown only)

## Security Considerations
- **Plugin Isolation**: Plugin errors don't crash other plugins
- **Resource Limits**: Sequential processing prevents resource exhaustion
- **Memory Safety**: Automatic ImageProxy cleanup
- **Permission Checks**: Plugins verify permissions before usage

## Error Handling

### Error Scenarios
1. **Plugin initialization fails**: Log error, disable plugin, continue with others
2. **Plugin processing throws exception**: Catch, log, mark plugin as failed, continue pipeline
3. **ImageProxy leak detected**: Log warning, force cleanup, investigate plugin
4. **Plugin provider missing**: Log error, skip plugin, continue loading others
5. **Priority conflict**: Sort by priority value, resolve ties alphabetically

### Fallback Behavior
- Failed plugin initialization → plugin marked as disabled
- Processing exception → plugin skipped for current frame, retried next frame
- Missing plugin → graceful degradation, app continues without feature

## Documentation Updates
- [x] Architecture docs updated with plugin flow
- [x] Provider pattern documented
- [x] Plugin development guide created
- [x] CLAUDE.md updated with plugin patterns

## Success Metrics
- **Plugin count**: 20+ active plugins in production
- **Code reuse**: 0 core camera modifications needed for new plugins
- **Performance**: 60fps maintained with all plugins enabled
- **Reliability**: No memory leaks detected in production
- **Acceptance**: New features added via plugins only

## Plugin Breakdown

### Dropdown Menu Plugins (15)
- GridOverlayPlugin
- BarcodePlugin
- HistogramPlugin
- CameraInfoPlugin
- ExposureAnalysisPlugin
- MotionDetectionPlugin
- QRScannerPlugin
- SharpnessAnalysisPlugin
- SmartScenePlugin
- SmartAdjustmentsPlugin
- ObjectDetectionPlugin
- CropPlugin
- RAWCapturePlugin
- AdvancedVideoRecordingPlugin
- HDRPlugin

### Always-Active Plugins (6)
- NightModePlugin (dedicated button)
- DualCameraPiPPlugin (dedicated button)
- AutoFocusPlugin (core functionality)
- ExposureControlPlugin (core functionality)
- ManualFocusPlugin (core functionality)
- ProControlsPlugin (core functionality)

## Implementation Notes

### Sequential Processing
Prevents resource exhaustion by processing plugins one at a time. Each plugin receives ImageProxy, processes it, and returns result before next plugin executes.

### Provider Pattern Benefits
- Single source of truth (PluginRegistry)
- Metadata separate from implementation
- Lazy instantiation (plugins created only when enabled)
- UI auto-generation from metadata
- Clean separation of concerns

### Visibility Control
- `showInDropdown = true`: User-toggleable features in plugin menu
- `showInDropdown = false`: Always-active or dedicated button features
- `showInSettings = true`: Configurable plugins in settings screen

### Memory Management
- ImageProxy passed by reference through pipeline
- Each plugin must NOT close() ImageProxy (managed by PluginManager)
- PluginManager ensures cleanup after all plugins process
- WeakReference used where appropriate

## Future Enhancements
- Plugin dependency resolution (deferred - complex)
- Plugin hot-reload for development (deferred - dev tool)
- Plugin marketplace/discovery (deferred - ecosystem)
- Plugin performance profiling UI (deferred - advanced tooling)

---

**Created**: 2025-10-19
**Last Updated**: 2025-10-19
**Owner**: CustomCamera Development Team
**Status**: Complete, Production-Ready
