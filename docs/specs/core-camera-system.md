# Core Camera System Specification

## Feature Overview
**Feature Name**: Core Camera System
**Priority**: P0
**Status**: Complete
**Target Version**: 2.0.0+

### Summary
Foundation camera system providing detection, enumeration, permission handling, preview, and capture capabilities using CameraX API.

### Motivation
Provide robust, reliable camera functionality that works across diverse Android devices with proper lifecycle management and modern API usage.

## Requirements

### Functional Requirements
1. **FR-1**: Detect and enumerate all available cameras on device
2. **FR-2**: Request and handle camera/audio permissions using modern Activity Result API
3. **FR-3**: Initialize CameraX provider and bind camera lifecycle
4. **FR-4**: Provide live camera preview with proper surface management
5. **FR-5**: Capture photos with timestamp-based naming to external storage
6. **FR-6**: Support camera switching between available cameras at runtime
7. **FR-7**: Control flash modes (off, on, auto, torch)
8. **FR-8**: Support both single and concurrent camera modes
9. **FR-9**: Graceful error handling and user feedback

### Non-Functional Requirements
1. **NFR-1**: Performance - Camera preview at 60fps target, photo capture < 500ms
2. **NFR-2**: Usability - Clear camera selection UI, auto-select first camera if single option
3. **NFR-3**: Reliability - Proper lifecycle management, no memory leaks, graceful degradation
4. **NFR-4**: Compatibility - Android 8.0+ (API 26+), CameraX 1.3.1

### User Stories
- **As a** user, **I want** the app to detect all my device cameras, **so that** I can choose which one to use
- **As a** user, **I want** smooth camera preview, **so that** I can frame my shots properly
- **As a** user, **I want** to switch cameras during session, **so that** I can use different lenses
- **As a** user, **I want** clear permission requests, **so that** I understand why access is needed

## Technical Design

### Architecture
```
MainActivity
    ↓
CameraSelectionActivity (camera enumeration)
    ↓ (pass camera index via Intent)
CameraActivityEngine
    ↓
CameraEngine (central coordinator)
    ├── ProcessCameraProvider
    ├── Preview UseCase
    ├── ImageCapture UseCase
    ├── VideoCapture UseCase (single mode only)
    └── ImageAnalysis UseCase (single mode only)
```

### Component Breakdown

1. **MainActivity**
   - App entry point
   - Launches CameraSelectionActivity

2. **CameraSelectionActivity**
   - Enumerates cameras via `CameraManager.getCameraIdList()`
   - Parses camera characteristics (lens facing, capabilities)
   - Creates selection UI dynamically
   - Passes selected camera index to CameraActivityEngine

3. **CameraActivityEngine**
   - Primary camera interface activity
   - Manages UI (preview, controls, overlays)
   - Initializes CameraEngine with selected camera
   - Coordinates photo/video capture
   - Handles gesture controls

4. **CameraEngine**
   - Central camera coordinator
   - Manages ProcessCameraProvider lifecycle
   - Binds/unbinds UseCases
   - Switches between Single and Concurrent modes
   - Coordinates plugin system

### Data Structures
```kotlin
// Camera mode state
sealed class CameraMode {
    object Single : CameraMode()
    data class Concurrent(
        val mainCamera: Camera,
        val pipCamera: Camera
    ) : CameraMode()
}

// Camera selection intent data
const val EXTRA_CAMERA_INDEX = "camera_index"

// Permission state
data class PermissionState(
    val cameraGranted: Boolean,
    val audioGranted: Boolean
)
```

### API/Interface Design
```kotlin
// CameraEngine public interface
class CameraEngine(private val context: CameraContext) {
    fun initialize(cameraIndex: Int)
    fun bindCamera()
    fun unbindCamera()
    fun switchToConcurrentMode(mainIndex: Int, pipIndex: Int)
    fun switchToSingleMode()
    fun getCurrentMode(): CameraMode
    fun getProvider(): ProcessCameraProvider?
    fun getPreview(): Preview?
    fun cleanup()
}

// CameraContext interface
interface CameraContext {
    val lifecycleOwner: LifecycleOwner
    val context: Context
    val cameraEngine: CameraEngine?
}
```

### State Management
- **Camera Index**: Passed via Intent extras (EXTRA_CAMERA_INDEX)
- **Camera Mode**: StateFlow in CameraEngine (Single/Concurrent)
- **Permission State**: Activity Result API callback
- **Flash Mode**: Local state in CameraActivityEngine
- **Persistence**: Camera selection stored in SharedPreferences for next launch

## Implementation Plan

### Phase 1: Foundation (Complete)
**Duration**: Initial implementation
**Deliverables**:
- [x] CameraX dependency integration
- [x] MainActivity with camera launch button
- [x] Permission handling with Activity Result API
- [x] Basic camera enumeration

### Phase 2: Selection UI (Complete)
**Duration**: 1 day
**Deliverables**:
- [x] CameraSelectionActivity
- [x] Dynamic camera button generation
- [x] Camera characteristics parsing
- [x] Intent-based camera index passing

### Phase 3: Camera Engine (Complete)
**Duration**: 2 days
**Deliverables**:
- [x] CameraEngine class
- [x] ProcessCameraProvider initialization
- [x] UseCase binding (Preview, ImageCapture)
- [x] Lifecycle management
- [x] Error handling

### Phase 4: Capture & Controls (Complete)
**Duration**: 1 day
**Deliverables**:
- [x] Photo capture with timestamp naming
- [x] Camera switching at runtime
- [x] Flash control
- [x] External storage integration

### Phase 5: Concurrent Camera Support (Complete)
**Duration**: 3 days
**Deliverables**:
- [x] ConcurrentCameraCapability detection
- [x] CameraMode sealed class
- [x] Concurrent camera API implementation
- [x] Mode switching (Single ↔ Concurrent)
- [x] UseCase limit handling (max 2 per camera)

## Testing Strategy

### Unit Tests
- Camera enumeration returns valid camera IDs
- Camera selector creation for specific indices
- Mode state transitions (Single ↔ Concurrent)
- Flash mode toggling logic

### Integration Tests
- Full camera initialization flow
- Photo capture end-to-end
- Camera switching preserves preview
- Permission grant/deny flows
- Concurrent camera binding

### UI/UX Tests
- Camera selection buttons display correctly
- Selected camera visual indication works
- Permission dialogs appear when needed
- Error messages shown for failures
- Loading states during initialization

### Performance Tests
- Preview latency < 100ms from bind
- Photo capture < 500ms from button press
- Camera switch < 300ms
- Memory stable during extended usage

## Dependencies

### Internal Dependencies
- CameraActivityEngine (UI layer)
- PluginManager (integration layer)
- SettingsManager (configuration)

### External Dependencies
- androidx.camera:camera-camera2:1.3.1
- androidx.camera:camera-lifecycle:1.3.1
- androidx.camera:camera-view:1.3.1
- androidx.camera:camera-core:1.3.1
- Kotlin Coroutines 1.7.3

### Breaking Changes
- [x] Requires Android 8.0+ (API 26+) for concurrent camera features
- [x] Video recording disabled in concurrent camera mode (UseCase limit)

## Security Considerations
- **Camera permission**: Required, requested at app start via Activity Result API
- **Storage permission**: Uses scoped storage (Android 10+), no permission needed
- **Audio permission**: Required for video recording with audio
- **Privacy**: No camera data transmitted externally, all local storage

## Error Handling

### Error Scenarios
1. **No cameras available**: Show error dialog, disable camera features
2. **Permission denied**: Show rationale, offer settings navigation
3. **Camera provider init fails**: Log error, show user-friendly message, retry option
4. **Camera binding fails**: Attempt fallback to different camera, show error if all fail
5. **Concurrent camera not supported**: Graceful fallback to single camera mode
6. **UseCase limit exceeded**: Disable incompatible UseCases (video in PiP mode)

### Fallback Behavior
- If concurrent camera fails → switch to single camera mode
- If selected camera unavailable → auto-select first available camera
- If all cameras fail → show error, offer app restart

## Documentation Updates
- [x] Architecture docs updated with camera flow
- [x] CLAUDE.md quick reference added
- [x] Camera selection status documented
- [x] Concurrent camera implementation notes added

## Success Metrics
- **Camera detection**: 100% detection rate of device cameras
- **Permission success**: > 95% permission grant rate
- **Preview latency**: < 100ms from bind to first frame
- **Crash rate**: < 0.1% on camera initialization
- **Acceptance**: Camera system works reliably across test devices

## Implementation Notes

### Camera Enumeration
Uses `CameraManager.getCameraIdList()` instead of CameraX `cameraProvider.availableCameraInfos` for better control and characteristics access.

### Intent-Based Selection
Camera index passed via Intent extras (EXTRA_CAMERA_INDEX) from CameraSelectionActivity to CameraActivityEngine, ensuring proper initialization order.

### ProcessCameraProvider Sharing
Single ProcessCameraProvider instance shared between CameraEngine and all camera operations (critical for concurrent camera mode).

### UseCase Limits
- **Single Mode**: Up to 4 UseCases (Preview + ImageCapture + VideoCapture + ImageAnalysis)
- **Concurrent Mode**: Max 2 UseCases per camera (main: Preview + ImageCapture, pip: Preview only)

### Lifecycle Management
- UseCases bound to activity lifecycle via LifecycleOwner
- Proper cleanup in onDestroy prevents memory leaks
- Coroutines use lifecycleScope for automatic cancellation

## Future Enhancements
- Camera preview thumbnails in selection UI (deferred - UI polish)
- Multi-camera synchronized capture (deferred - advanced feature)
- Camera capability filtering/sorting (deferred - UX improvement)
- Camera settings persistence per camera (deferred - settings enhancement)

---

**Created**: 2025-10-19
**Last Updated**: 2025-10-19
**Owner**: CustomCamera Development Team
**Status**: Complete, Production-Ready
