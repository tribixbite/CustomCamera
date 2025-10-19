# CustomCamera Architecture

## Overview
Modern Kotlin camera app with Samsung/Google-style floating UI, robust camera selection, and full plugin system integration for advanced features.

**Technology Stack**: Kotlin, CameraX, Material3, ViewBinding, Provider Pattern Plugin Architecture
**Architecture Pattern**: Clean Android + CameraEngine + Plugin System + StateFlow Reactive

## Directory Structure

### Core Source Files
```
app/src/main/java/com/customcamera/app/
├── MainActivity.kt                    # App entry point with camera launch
├── CameraSelectionActivity.kt        # Camera detection and selection UI
├── CameraActivityEngine.kt           # ✅ PRIMARY: Full plugin system camera
├── CameraActivity.kt                  # ⚠️ DEPRECATED: Legacy basic camera (unused)
├── SimpleSettingsActivity.kt         # Settings with StateFlow reactive architecture
├── GalleryActivity.kt                # Photo/video gallery
├── DebugActivity.kt                  # Comprehensive debug tools
└── engine/
    ├── CameraEngine.kt               # Central camera coordinator
    ├── SettingsManager.kt            # Reactive StateFlow settings
    └── plugins/
        ├── PluginManager.kt          # Plugin registration & lifecycle
        ├── CameraPlugin.kt           # Base plugin classes
        └── PluginRegistry.kt         # Single source of truth for plugins
```

### Plugin Categories
```
app/src/main/java/com/customcamera/app/engine/plugins/

Core Plugins:
├── GridOverlayPlugin.kt              # Composition grids (rule of thirds, etc.)
├── AutoFocusPlugin.kt                # Automatic focus management
├── CropPlugin.kt                     # Pre-shot crop with aspect ratios
├── ProControlsPlugin.kt              # Professional camera controls
└── ExposureControlPlugin.kt          # Exposure compensation

Analysis & Detection:
├── BarcodePlugin.kt                  # QR/barcode scanning with ML Kit
├── QRScannerPlugin.kt                # Dedicated QR code scanning
├── HistogramPlugin.kt                # Real-time histogram display
└── MotionDetectionPlugin.kt          # Motion-based capture

AI-Powered Features:
├── SmartScenePlugin.kt               # AI scene detection
├── ObjectDetectionPlugin.kt          # Real-time object recognition
└── SmartAdjustmentsPlugin.kt         # AI-powered auto-adjustments

Advanced Capture:
├── HDRPlugin.kt                      # High dynamic range photography
├── NightModePlugin.kt                # Low-light optimization
├── DualCameraPiPPlugin.kt            # Picture-in-picture dual camera
├── AdvancedVideoRecordingPlugin.kt   # Professional video features
├── RAWCapturePlugin.kt               # DNG/RAW photo capture
└── ManualFocusPlugin.kt              # Manual focus control
```

### UI Components
```
app/src/main/java/com/customcamera/app/ui/

Custom Views:
├── BarcodeOverlayView.kt             # QR/barcode overlay rendering
├── CropOverlayView.kt                # Crop area display
├── HistogramView.kt                  # Real-time histogram
└── PiPOverlayView.kt                 # Dual camera PiP overlay

Presentation Layer:
├── DemoShowcaseManager.kt            # Interactive feature showcase
├── PerformanceMonitor.kt             # Real-time FPS/memory monitoring
├── EnhancedHapticManager.kt          # Sophisticated haptic feedback
├── GestureHintsOverlay.kt            # First-run tutorial
└── EnhancedToast.kt                  # Professional notifications

UX Enhancement Components (Ready for Integration):
├── QuickSettingsDrawer.kt            # Slide-out settings drawer
├── PhotoPreviewOverlay.kt            # Full-screen photo preview
├── ActiveFeaturesIndicator.kt        # Active modes badge
├── GestureTutorialOverlay.kt         # Gesture tutorial overlay
├── EnhancedCaptureFeedback.kt        # Multi-sensory feedback
└── SmartErrorRecovery.kt             # Intelligent error recovery
```

### Managers
```
app/src/main/java/com/customcamera/app/managers/

AI Managers (8 total):
├── SceneDetectionManager.kt
├── ObjectRecognitionManager.kt
└── [6 more AI managers]

Video Managers (8 total):
├── VideoRecordingManager.kt
├── StabilizationManager.kt
└── [6 more video managers]

Hardware Managers (5 total):
├── MultiCameraManager.kt
├── DepthSensorManager.kt
└── [3 more hardware managers]

UI Managers (5 total):
├── AnimationManager.kt
├── ThemeManager.kt
└── [3 more UI managers]

Performance Managers:
├── BatteryOptimizationManager.kt
└── MemoryManager.kt
```

### Layouts
```
app/src/main/res/layout/
├── activity_main.xml                 # Simple launcher with camera button
├── activity_camera_selection.xml     # Camera detection and selection UI
├── activity_camera.xml              # Modern floating camera interface
├── activity_settings.xml             # Camera settings screen
└── item_camera_option.xml            # Camera selection list item
```

### Resources
```
app/src/main/res/
├── drawable/                         # UI graphics and icons
├── values/
│   ├── strings.xml                   # App text resources
│   ├── colors.xml                    # Material3 color scheme
│   └── themes.xml                    # Material3 theme configuration
└── mipmap-*/                         # App launcher icons
```

## Critical Data Flow

```
MainActivity → CameraSelectionActivity → CameraActivityEngine (✅ PRIMARY)
     ↓              ↓                             ↓
Launch camera → Select camera index → Initialize CameraEngine with plugins
                     ↓                             ↓
               Pass via Intent extras ──────> Register 20+ plugins
               Key: EXTRA_CAMERA_INDEX (Int)      ↓
                                            Setup plugin lifecycle & UI
```

## Plugin System Architecture

### Provider Pattern
All plugins implement the Provider Pattern with:
- Single source of truth: `PluginRegistry`
- Metadata provision: `PluginProvider` interface
- Visibility control: `showInDropdown`, `showInSettings`
- Standardized metadata: name, description, icon, category

### Plugin Lifecycle
1. **Registration**: Plugins registered in `PluginRegistry` via provider pattern
2. **Initialization**: `CameraEngine` creates plugin instances from registry
3. **Binding**: Plugins bound to camera lifecycle events
4. **Processing**: Sequential image processing prevents resource exhaustion
5. **Cleanup**: Proper ImageProxy cleanup prevents memory leaks

### Plugin Categories
- **UIPlugin**: Overlays and visual elements (Grid, Crop, Barcode)
- **ProcessingPlugin**: Image analysis and processing (AI, Histogram, Motion)
- **ControlPlugin**: Camera controls and settings (ProControls, Focus, Exposure)

## StateFlow Reactive Architecture

### Settings Management
- **SettingsManager**: Central StateFlow-based settings store
- **No Broadcasts**: Pure reactive architecture using StateFlow
- **Type-Safe**: Kotlin data classes for settings
- **Persistent**: SharedPreferences backing store
- **Reactive**: UI automatically updates on settings changes

### Camera State
- **CameraMode**: Sealed class (Single/Concurrent)
- **Plugin State**: Individual StateFlow per plugin
- **UI State**: Reactive binding to state changes

## Camera Modes

### Single Camera Mode
- Standard camera operation
- All use cases available: Preview + ImageCapture + VideoCapture + ImageAnalysis
- Full plugin system active

### Concurrent Camera Mode (PiP)
- Dual camera operation (main + PiP)
- UseCase limit: max 2 per camera
- Main camera: Preview + ImageCapture
- PiP camera: Preview only
- Video recording disabled in PiP mode

## Key Components

### CameraEngine
Central coordinator responsible for:
- Camera lifecycle management
- Plugin registration and initialization
- Use case binding and management
- Mode switching (Single ↔ Concurrent)
- Image processing pipeline coordination

### PluginManager
Plugin lifecycle and execution manager:
- Sequential plugin processing
- Priority-based execution order
- Resource management
- Error handling and recovery

### SettingsManager
Reactive settings management:
- StateFlow-based reactive updates
- Type-safe settings access
- Persistent storage
- No broadcast mechanism

## Performance Optimizations

### Memory Management
- Proper ImageProxy cleanup (no leaks)
- WeakReference for context in monitors
- Lifecycle-aware coroutine scopes
- Automatic resource cleanup on destroy

### Sequential Processing
- Plugins process images sequentially
- Prevents resource exhaustion
- Maintains 60fps target
- Efficient camera buffer management

### Battery Optimization
- Smart sensor usage
- Conditional feature activation
- Background task management

## Error Handling

### Exception Hierarchy
```kotlin
CameraException
├── CameraConfigurationException
├── CaptureFailedException
└── NoCamerasAvailableException
```

### Recovery Strategies
- Graceful degradation
- User-friendly error messages
- Actionable recovery options
- Comprehensive logging

## Testing Architecture

### Test Infrastructure
- Plugin Test Framework: Lifecycle, performance, concurrency testing
- Test Image Factory: Mock ImageProxy generation
- Mock Camera Context: Isolated plugin testing
- Total: 38+ automated tests

### Test Categories
- Plugin Unit Tests: 13+ tests
- UI Tests (Espresso): 17+ tests
- Instrumented Tests: 6+ tests
- Memory Leak Detection: 5+ tests

## Build System

### Gradle Configuration
- Kotlin DSL
- CameraX 1.3.1
- Material3 theming
- ViewBinding enabled
- Test dependencies configured

### CI/CD Pipeline
- 8-job GitHub Actions workflow
- Automated testing and building
- Code coverage reporting
- Security scanning
- Automatic releases on main branch

## Feature Flags

### Gesture Controls
- Double-tap: Grid overlay
- Triple-tap: Barcode scanning
- Quadruple-tap: Crop mode
- Five-tap: Smart scene detection
- Six-tap: Object detection
- Pinch: Zoom control
- Long-press: AI features status

### Plugin Visibility
- **Dropdown Menu (15 plugins)**: User-toggleable features
- **Always Active (6 plugins)**: Core functionality, no toggle needed

## Technical Debt

Current known issues:
- ViewBinding could be further leveraged
- Error handling could use custom exceptions more
- Camera selection UI could be more polished

See `memory/todo.md` for current development priorities.
