# CustomCamera - Claude Code Configuration

## Project Overview
Modern Kotlin camera app with Samsung/Google-style floating UI, robust camera selection, and full plugin system integration for advanced features.

**Status**: Production-Ready ✅ (Performance Optimized 2025-11-27)
**Technology**: Kotlin, CameraX, Material3, ViewBinding, Provider Pattern Plugin Architecture
**Current Version**: 2.4.1 (build 42) - Sessions 42-45 Complete

## Quick Navigation

### Essential Documentation
- **🔥 ACTIVE TODOS**: `memory/ACTIVE_TODOS.md` - **CHECK FIRST FOR CURRENT WORK**
- **Task Management**: `memory/todo.md` - Historical task tracking
- **Architecture**: `docs/ARCHITECTURE.md` - System design, directory structure, data flows
- **Session History**: `docs/SESSION_HISTORY.md` - Completed work and implementation details
- **Build Guide**: See "Build Commands" section below
- **Feature Specs**: See "Features Implemented" section below

### Development Documentation
- **Provider Pattern Refactoring**: `memory/PROVIDER_PATTERN_REFACTORING.md`
- **Phase 8 Summary**: `PHASE8_SUMMARY.md`
- **Video Stabilization Guide**: `VIDEO_STABILIZATION_GUIDE.md`
- **Conference Demo Guide**: `CONFERENCE_DEMO_GUIDE.md`
- **UX Improvements**: `memory/UX_IMPROVEMENTS.md`
- **PiP Implementation**: `memory/PIP.md`
- **Test Documentation**: `app/src/test/README_TESTS.md`
- **Settings Testing**: `memory/SETTINGS_TESTING_CHECKLIST.md` - **CHECK FOR MANUAL TESTING**
- **Plugin UI Audit**: `memory/PLUGIN_UI_AUDIT.md` - Toggle vs Action button analysis

## Build Commands

### Recommended Build Script
```bash
./build-and-install.sh              # Automated build with app stop/uninstall
./build-and-install.sh clean        # Clean build with app cleanup
```

**Note**: Script automatically stops and uninstalls existing app before building to prevent file locking issues.

### Manual Build Commands
```bash
./gradlew assembleDebug                                      # Build debug APK only
./gradlew clean assembleDebug                                # Clean build only
adb install -r app/build/outputs/apk/debug/app-debug.apk   # Manual install
adb logcat -d | grep "customcamera\|CameraActivity"         # Check app logs
```

### Test Commands
```bash
./gradlew test                                  # Run all tests
./gradlew test --tests "MyTest"                # Run specific test
./gradlew testDebugUnitTestCoverage            # With coverage
```

## Current Status

### ✅ Completed Major Features
- **Plugin System**: 20+ active plugins with Provider Pattern
- **Settings System**: StateFlow reactive architecture (no broadcasts)
- **Dual Camera PiP**: Concurrent camera mode with photo compositing
- **Video Stabilization**: Hardware + software fallback (9 modes)
- **AI Features**: Scene detection, object recognition, smart adjustments
- **Professional Controls**: ISO, shutter speed, focus distance, zoom
- **Advanced Capture**: HDR, Night Mode, RAW/DNG, Long Exposure
- **UX Polish**: Haptic feedback, gesture hints, demo showcase, performance monitor
- **CI/CD**: Automated testing, building, releases on GitHub
- **Test Infrastructure**: 38+ automated tests across all categories

### Current Development Focus
See `memory/todo.md` for active tasks and priorities.

### Known Issues
See `memory/todo.md` for current issues and technical debt.

## Features Implemented

### ✅ Camera Core
- [x] Camera detection and enumeration (4 cameras on test device)
- [x] Permission handling (modern Activity Result API)
- [x] CameraX integration with lifecycle management
- [x] Photo capture with timestamp naming
- [x] Camera switching between available cameras
- [x] Flash control with state management
- [x] Plugin System Architecture (20+ plugins)
- [x] Advanced Video Recording (quality control, duration tracking)
- [x] RAW/DNG Capture (Camera2 interop)
- [x] Dual Camera PiP (concurrent camera mode)
- [x] Video Stabilization (hardware + software, 9 modes)

### ✅ UI/UX
- [x] Material3 theme integration
- [x] Samsung/Google-style floating UI design
- [x] Fullscreen immersive camera experience
- [x] Smooth button animations (scale, rotation)
- [x] Modern Kotlin with ViewBinding
- [x] Gesture Controls (multi-tap + pinch + long-press)
- [x] Professional Manual Controls (ISO, shutter, focus, zoom)
- [x] Enhanced Haptic Feedback (contextual vibration patterns)
- [x] Gesture Hints Overlay (first-run tutorial)
- [x] Demo Showcase Mode (conference presentation)
- [x] Performance Monitor (real-time FPS/memory)
- [x] Enhanced Toast Notifications (icons + colors)

### ✅ Error Handling
- [x] Graceful permission denial handling
- [x] Camera provider initialization error handling
- [x] Camera binding failure recovery
- [x] No cameras available scenario
- [x] Comprehensive logging for debugging
- [x] Sequential Plugin Processing (prevents resource exhaustion)
- [x] Proper ImageProxy Cleanup (no memory leaks)
- [x] Smart Error Recovery (intelligent error analysis)

### ✅ Plugin System (20+ Active Plugins)

**Core Plugins:**
- GridOverlayPlugin - Composition grids (rule of thirds, 9x3, golden ratio)
- AutoFocusPlugin - Automatic focus management
- CropPlugin - Pre-shot crop with aspect ratio control
- ProControlsPlugin - Professional camera controls
- ExposureControlPlugin - Exposure compensation

**Analysis & Detection:**
- BarcodePlugin - QR/barcode scanning with ML Kit
- QRScannerPlugin - Dedicated QR code scanning
- HistogramPlugin - Real-time histogram display
- MotionDetectionPlugin - Motion-based capture

**AI-Powered Features:**
- SmartScenePlugin - AI scene detection (landscapes, portraits, etc.)
- ObjectDetectionPlugin - Real-time object recognition
- SmartAdjustmentsPlugin - AI-powered auto-adjustments

**Advanced Capture:**
- HDRPlugin - High dynamic range photography
- NightModePlugin - Low-light optimization
- DualCameraPiPPlugin - Picture-in-picture dual camera
- AdvancedVideoRecordingPlugin - Professional video features
- RAWCapturePlugin - DNG/RAW photo capture
- ManualFocusPlugin - Manual focus control

**Visibility Configuration:**
- **Dropdown Menu (15)**: GridOverlay, Barcode, Histogram, CameraInfo, ExposureAnalysis, MotionDetection, QRScanner, SharpnessAnalysis, SmartScene, SmartAdjustments, ObjectDetection, Crop, RAWCapture, AdvancedVideoRecording, HDR
- **Always Active (6)**: NightMode, DualCameraPiP (dedicated buttons), AutoFocus, ExposureControl, ManualFocus, ProControls

### Gesture Controls Reference
| Gesture | Feature | Haptic Feedback |
|---------|---------|-----------------|
| 2× tap | Grid overlay | Medium |
| 3× tap | Barcode scanning | Medium |
| 4× tap | Pre-shot crop | Medium |
| 5× tap | Smart scene detection | Medium |
| 6× tap | Gesture hints overlay | Medium |
| 7× tap | Demo showcase mode | Success |
| Pinch | Zoom control | - |
| Long-press preview | AI features status | Long-press |

## Development Workflow

### Session Startup Protocol
1. `cd ~/git/swype/CustomCamera`
2. Check current status: `git status`
3. **Review master task list**: `cat memory/todo.md` - **CRITICAL: ALWAYS CHECK FIRST**
4. Focus on highest priority P0 issues first

### Development Cycle
1. **Plan**: Update `memory/todo.md` with tasks
2. **Code**: Make changes with proper error handling
3. **Build**: `./build-and-install.sh`
4. **Test**: Test on device with `adb logcat -d | grep "customcamera"`
5. **Document**: Update `memory/todo.md` with progress
6. **Commit**: Conventional commits with descriptive messages

### Session End Protocol
1. **Update Tasks**: Mark progress in `memory/todo.md`
2. **Commit Changes**: Descriptive commit messages
3. **Update CLAUDE.md**: Only if major architecture changes (rare)

### Emergency Session Recovery
If lost or confused:
```bash
cd ~/git/swype/CustomCamera
cat memory/todo.md | head -50        # Current tasks and priorities
cat docs/ARCHITECTURE.md | head -100  # System overview
git status                           # Current git state
git log --oneline -10                # Recent commits
```

## Architecture Quick Reference

### Primary Camera Activity
`CameraActivityEngine.kt` - Full plugin system camera (PRIMARY)
⚠️ `CameraActivity.kt` - DEPRECATED legacy camera (unused)

### Data Flow
```
MainActivity → CameraSelectionActivity → CameraActivityEngine
     ↓              ↓                             ↓
Launch        Select camera index       Initialize CameraEngine
                     ↓                             ↓
              Pass via Intent           Register 20+ plugins
              (EXTRA_CAMERA_INDEX)               ↓
                                         Setup plugin lifecycle
```

### Core Systems
- **CameraEngine**: Central camera coordinator
- **PluginManager**: Plugin registration & lifecycle
- **SettingsManager**: Reactive StateFlow settings
- **PluginRegistry**: Single source of truth for plugins

See `docs/ARCHITECTURE.md` for complete system design.

## Key Function Reference

### CameraActivityEngine.kt (Primary)
- `onCreate()`: Activity initialization
- `setupCamera()`: Camera engine initialization
- `captureRegularPhoto()`: Photo capture (with dual camera support)
- `toggleDualCameraPiP()`: Enable/disable PiP mode
- `setupPinchToZoom()`: Zoom gesture handling
- `setupGestureControls()`: Multi-tap gesture system

### CameraEngine.kt
- `initialize()`: Engine setup with plugin registration
- `bindCamera()`: Camera binding with use cases
- `switchToConcurrentMode()`: Enable dual camera PiP
- `switchToSingleMode()`: Disable dual camera PiP
- `cleanup()`: Resource cleanup and memory leak prevention

### PluginManager.kt
- `registerPlugin()`: Plugin registration
- `processImage()`: Sequential image processing pipeline
- `enablePlugin()` / `disablePlugin()`: Plugin lifecycle

See `docs/ARCHITECTURE.md` for complete function reference.

## Debugging Reference

### Camera Issues
```bash
# Check logs for camera enumeration
adb logcat -d | grep "Available cameras"

# Verify intent passing
adb logcat -d | grep "Intent extra value"

# Trace camera binding
adb logcat -d | grep "Camera bound successfully"

# Monitor plugin execution
adb logcat -d | grep "Plugin"
```

### Common Issues
- **Camera not starting**: Check permissions, camera enumeration logs
- **Gestures not working**: Verify tap count logic, check touch event logs
- **PiP issues**: Check concurrent camera capability, UseCase limit (max 2 per camera)
- **Video recording fails**: Check PiP mode (video disabled in PiP), audio permission

## Spec-Driven Development

### Adding New Features
1. **Define Spec**: Create spec document in `docs/specs/`
2. **Update Architecture**: Modify `docs/ARCHITECTURE.md` if needed
3. **Plan Tasks**: Add to `memory/todo.md`
4. **Implement**: Follow architecture patterns
5. **Test**: Add tests to test infrastructure
6. **Document**: Update relevant docs

### Plugin Development Pattern
1. **Create Provider**: Implement `PluginProvider` interface
2. **Register**: Add to `PluginRegistry`
3. **Implement Plugin**: Extend appropriate base class (UI/Processing/Control)
4. **Configure Visibility**: Set `showInDropdown`, `showInSettings`
5. **Add Tests**: Unit tests + integration tests
6. **Document**: Add to architecture docs

### Settings Addition Pattern
1. **Define Setting**: Add to `SettingsManager` data class
2. **Create UI**: Add to appropriate settings screen
3. **Wire StateFlow**: Connect UI to reactive state
4. **Persist**: Ensure SharedPreferences backing
5. **Test**: Verify reactive updates work

## Quality Standards

### Code Quality Requirements
- Modern Kotlin with proper null safety
- ViewBinding throughout
- Proper lifecycle management
- Comprehensive error handling
- Accessibility support
- Material3 theming

### Architecture Requirements
- Clean separation of concerns
- Provider Pattern for plugins
- StateFlow reactive architecture
- No circular dependencies
- Proper dependency injection

### Performance Requirements
- Proper coroutine usage
- Sequential plugin processing
- Memory leak prevention (ImageProxy cleanup)
- Battery optimization
- Target 60fps for camera preview

### Testing Requirements
- Unit tests for new plugins
- UI tests for new screens
- Integration tests for new flows
- Memory leak tests for new components

## External Resources

### GitHub Repository
**URL**: https://github.com/tribixbite/CustomCamera
**Releases**: https://github.com/tribixbite/CustomCamera/releases
**CI/CD**: `.github/workflows/ci.yml` - 8-job workflow

### Release Strategy
- Automatic releases on main branch push
- Version format: `v{MAJOR}.{MINOR}.{PATCH}-build{CODE}-{TIMESTAMP}`
- Both debug and release APKs uploaded
- Release notes include commit messages

---

**Last Updated**: 2025-10-19
**Current Focus**: See `memory/todo.md` for active development priorities
**For Detailed History**: See `docs/SESSION_HISTORY.md`
**For System Design**: See `docs/ARCHITECTURE.md`
