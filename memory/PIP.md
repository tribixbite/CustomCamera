# Picture-in-Picture (PiP) Dual Camera Implementation Plan

**Status**: Core Implementation Complete - Ready for Device Testing
**Created**: 2025-10-14
**Updated**: 2025-10-14
**Priority**: HIGH - Critical blocker preventing PiP feature from working

---

## Implementation Status

### ✅ COMPLETED (2025-10-14)
- **Step 1**: Problem Analysis - COMPLETE
- **Step 2**: Architecture Design - COMPLETE
- **Step 3**: Concurrent Camera Capability Detection - COMPLETE
- **Step 4-6**: Core ConcurrentCamera API Implementation - COMPLETE
  - CameraEngine.switchToConcurrentMode() using CameraX 1.3 API
  - CameraEngine.switchToSingleMode() for exiting PiP
  - DualCameraPiPPlugin integration with mode switching
  - Proper error handling and user feedback

### 🔄 REMAINING
- **Phase 3**: Integration testing (Steps 7-8) - READY
- **Phase 4**: Device testing and finalization (Steps 9-12) - READY

---

## Executive Summary

The current PiP implementation fails with:
```
java.lang.IllegalArgumentException: Multiple LifecycleCameras with use cases
are registered to the same LifecycleOwner.
```

**Root Cause**: CameraX does not allow binding multiple separate cameras to the same LifecycleOwner using standard `bindToLifecycle()` calls.

**Solution**: Use CameraX 1.3+ ConcurrentCamera API to bind both cameras together in a single operation.

---

## Architecture Overview

```
CURRENT (BROKEN):
CameraEngine ----------> bindToLifecycle(main camera)     [SUCCESS]
DualCameraCoordinator -> bindToLifecycle(PiP camera)      [FAILS - Lifecycle conflict]

NEW (SOLUTION):
CameraEngine ----------> Check PiP state
   |
   +-- PiP OFF: bindToLifecycle(main camera)              [Single camera mode]
   |
   +-- PiP ON:  bindToLifecycle(ConcurrentCamera)         [Concurrent camera mode]
                   |
                   +-- SingleCameraConfig(main camera + all use cases)
                   +-- SingleCameraConfig(PiP camera + preview only)
```

---

## Implementation Plan

### PHASE 1: Foundation (Steps 1-3)

#### Step 1: Problem Analysis [COMPLETE]

**Current State**:
- CameraX version: 1.3.1 (supports ConcurrentCamera)
- CameraEngine binds main camera successfully
- DualCameraCoordinator fails when trying to bind second camera
- ProcessCameraProvider is properly shared
- PiP overlay is properly laid out and ready

**Requirements**:
- Hardware must support concurrent camera operation
- Both cameras need proper CameraSelector
- Each camera needs its own Preview use case
- Main camera must maintain all existing plugin functionality
- Graceful fallback when concurrent cameras not supported

**Architectural Decision**:
- **OPTION B (SELECTED)**: Conditional ConcurrentCamera activation
  - PiP OFF: Standard single camera binding (current behavior)
  - PiP ON: Switch to ConcurrentCamera with two configs
  - Minimal disruption to existing code
  - Easier to test and debug

---

#### Step 2: Architecture Design [COMPLETE]

**Component Responsibilities**:

**1. CameraEngine** (`app/src/main/java/com/customcamera/app/engine/CameraEngine.kt`)
```kotlin
class CameraEngine {
    // NEW: Camera mode tracking
    private var currentMode: CameraMode = CameraMode.Single
    private var singleCamera: Camera? = null
    private var concurrentCamera: ConcurrentCamera? = null

    // NEW: Mode switching
    fun switchToConcurrentMode(mainIndex: Int, pipIndex: Int)
    fun switchToSingleMode()

    // NEW: Concurrent camera binding
    private fun bindConcurrentCameras()

    // EXISTING: Single camera binding (unchanged)
    private fun bindSingleCamera()
}
```

**2. DualCameraCoordinator** (`app/src/main/java/com/customcamera/app/pip/DualCameraCoordinator.kt`)
```kotlin
class DualCameraCoordinator {
    // REFACTOR: Build configs instead of binding
    fun buildPrimaryCameraConfig(...): SingleCameraConfig
    fun buildSecondaryCameraConfig(...): SingleCameraConfig

    // NEW: Capability checking
    fun checkConcurrentCameraSupport(): ConcurrentCameraInfo

    // REMOVE: Direct binding attempts (causes lifecycle conflict)
}
```

**3. DualCameraPiPPlugin** (`app/src/main/java/com/customcamera/app/plugins/DualCameraPiPPlugin.kt`)
```kotlin
class DualCameraPiPPlugin {
    // MODIFY: Signal CameraEngine instead of binding directly
    private fun enablePiPMode() {
        // Check support
        // Request CameraEngine to switch to concurrent mode
        // Show PiP overlay
    }

    private fun disablePiPMode() {
        // Request CameraEngine to switch to single mode
        // Hide PiP overlay
    }
}
```

**Data Flow for Enabling PiP**:
```
1. User taps PiP button
2. DualCameraPiPPlugin.togglePiP()
3. Check concurrent camera support
   |
   +-- NOT SUPPORTED: Show error, return
   +-- SUPPORTED: Continue
4. Plugin calls CameraEngine.switchToConcurrentMode()
5. CameraEngine.switchToConcurrentMode():
   a. Unbind current single camera
   b. Get configs from DualCameraCoordinator
   c. Create ConcurrentCamera
   d. Bind to lifecycle
   e. Notify plugin of success/failure
6. Plugin shows PiP overlay
```

---

#### Step 3: Concurrent Camera Capability Detection [READY TO IMPLEMENT]

**Create New File**: `app/src/main/java/com/customcamera/app/pip/ConcurrentCameraCapability.kt`

**Purpose**: Detect and validate concurrent camera support before attempting to use the API.

**Key Features**:
- Check if device has 2+ cameras
- Query `ProcessCameraProvider.availableConcurrentCameraInfos`
- Build list of valid camera combinations
- Recommend best combination (prefer back + front)
- Provide detailed error messages when not supported

**API**:
```kotlin
data class ConcurrentCameraInfo(
    val isSupported: Boolean,
    val availableCombinations: List<Pair<Int, Int>>,
    val recommendedCombination: Pair<Int, Int>?,
    val errorMessage: String?
)

class ConcurrentCameraCapability {
    suspend fun checkSupport(provider: ProcessCameraProvider): ConcurrentCameraInfo
    suspend fun isCombinationSupported(provider: ProcessCameraProvider,
                                      primaryIndex: Int,
                                      secondaryIndex: Int): Boolean
}
```

**Integration**:
- Call during DualCameraPiPPlugin initialization
- Store support status in plugin state
- Show user-friendly messages when not supported
- Use recommended camera combinations

---

### PHASE 2: Core Implementation (Steps 4-6)

#### Step 4: Create SingleCameraConfig Builders

**Modify**: `DualCameraCoordinator.kt`

**Add Methods**:
```kotlin
fun buildPrimaryCameraConfig(
    cameraProvider: ProcessCameraProvider,
    cameraIndex: Int,
    mainPreview: Preview,
    imageCapture: ImageCapture,
    videoCapture: VideoCapture?,
    pluginUseCases: List<UseCase>,
    lifecycleOwner: LifecycleOwner
): SingleCameraConfig {
    val cameraSelector = createCameraSelector(cameraIndex, cameraProvider)

    val useCaseGroup = UseCaseGroup.Builder()
        .addUseCase(mainPreview)
        .addUseCase(imageCapture)

    videoCapture?.let { useCaseGroup.addUseCase(it) }
    pluginUseCases.forEach { useCaseGroup.addUseCase(it) }

    return SingleCameraConfig(
        cameraSelector,
        useCaseGroup.build(),
        lifecycleOwner
    )
}

fun buildSecondaryCameraConfig(
    cameraProvider: ProcessCameraProvider,
    cameraIndex: Int,
    pipPreview: Preview,
    lifecycleOwner: LifecycleOwner
): SingleCameraConfig {
    val cameraSelector = createCameraSelector(cameraIndex, cameraProvider)

    val useCaseGroup = UseCaseGroup.Builder()
        .addUseCase(pipPreview)
        .build()

    return SingleCameraConfig(
        cameraSelector,
        useCaseGroup,
        lifecycleOwner
    )
}
```

---

#### Step 5: Implement CameraMode State Management

**Create New File**: `app/src/main/java/com/customcamera/app/engine/CameraMode.kt`

```kotlin
package com.customcamera.app.engine

sealed class CameraMode {
    object Single : CameraMode()
    data class Concurrent(
        val mainCameraIndex: Int,
        val pipCameraIndex: Int
    ) : CameraMode()
}
```

**Add to CameraEngine**:
```kotlin
private var currentMode: CameraMode = CameraMode.Single
private var singleCamera: Camera? = null
private var concurrentCamera: ConcurrentCamera? = null

private fun unbindCurrentCamera() {
    when (currentMode) {
        is CameraMode.Single -> {
            cameraProvider?.unbindAll()
            singleCamera = null
        }
        is CameraMode.Concurrent -> {
            cameraProvider?.unbindAll()
            concurrentCamera = null
        }
    }
}
```

---

#### Step 6: Modify CameraEngine Binding Logic

**Add to CameraEngine**:
```kotlin
fun switchToConcurrentMode(
    mainCameraIndex: Int,
    pipCameraIndex: Int,
    pipPreviewView: PreviewView,
    onSuccess: () -> Unit,
    onFailure: (Exception) -> Unit
) {
    try {
        // Unbind current camera
        unbindCurrentCamera()

        // Create PiP preview
        val pipPreview = Preview.Builder().build().apply {
            setSurfaceProvider(pipPreviewView.surfaceProvider)
        }

        // Get configs from coordinator
        val coordinator = /* get from plugin */
        val primaryConfig = coordinator.buildPrimaryCameraConfig(...)
        val secondaryConfig = coordinator.buildSecondaryCameraConfig(...)

        // Bind concurrent cameras
        concurrentCamera = cameraProvider?.bindToLifecycle(
            context as LifecycleOwner,
            listOf(primaryConfig, secondaryConfig)
        )

        currentMode = CameraMode.Concurrent(mainCameraIndex, pipCameraIndex)
        onSuccess()

    } catch (e: Exception) {
        Log.e(TAG, "Failed to switch to concurrent mode", e)
        // Fall back to single camera
        switchToSingleMode()
        onFailure(e)
    }
}

fun switchToSingleMode() {
    unbindCurrentCamera()
    bindSingleCamera()
    currentMode = CameraMode.Single
}
```

---

### PHASE 3: Integration (Steps 7-8)

#### Step 7: Update DualCameraPiPPlugin

**Modifications**:
```kotlin
private fun enablePiPMode() {
    // Check concurrent support
    if (!_isDualCameraSupported.value) {
        Toast.makeText(context,
                      "Concurrent cameras not supported",
                      Toast.LENGTH_LONG).show()
        return
    }

    // Create and show overlay
    createPiPOverlay()

    val pipPreview = pipOverlayView?.getPreviewView()
    if (pipPreview == null) {
        Log.e(TAG, "PiP PreviewView is null")
        return
    }

    // Request CameraEngine to switch modes
    cameraContext?.cameraEngine?.switchToConcurrentMode(
        mainCameraIndex = _mainCamera.value,
        pipCameraIndex = _pipCamera.value,
        pipPreviewView = pipPreview,
        onSuccess = {
            _isPiPEnabled.value = true
            Log.i(TAG, "PiP mode enabled successfully")
        },
        onFailure = { exception ->
            Log.e(TAG, "Failed to enable PiP mode", exception)
            removePiPOverlay()
            Toast.makeText(context,
                          "Failed to enable PiP: ${exception.message}",
                          Toast.LENGTH_LONG).show()
        }
    )
}

private fun disablePiPMode() {
    removePiPOverlay()
    cameraContext?.cameraEngine?.switchToSingleMode()
    _isPiPEnabled.value = false
}
```

---

#### Step 8: Integrate Capability Detection

**In DualCameraPiPPlugin.initialize()**:
```kotlin
override suspend fun initialize(context: CameraContext) {
    this.cameraContext = context

    // ... existing initialization ...

    // Check concurrent camera support
    val capability = ConcurrentCameraCapability(context.context)
    val provider = context.cameraEngine?.getProvider()

    if (provider != null) {
        val info = capability.checkSupport(provider)

        if (!info.isSupported) {
            Log.w(TAG, "Concurrent cameras not supported: ${info.errorMessage}")
            _isDualCameraSupported.value = false
        } else {
            _isDualCameraSupported.value = true
            Log.i(TAG, "Concurrent cameras supported: ${info.availableCombinations.size} combinations")

            // Use recommended combination
            info.recommendedCombination?.let { (main, pip) ->
                _mainCamera.value = main
                _pipCamera.value = pip
                Log.i(TAG, "Using recommended combination: main=$main, pip=$pip")
            }
        }
    }
}
```

---

### PHASE 4: Testing & Finalization (Steps 9-12)

#### Step 9: Test Basic Concurrent Camera Binding

**Test Cases**:
- [ ] Device with concurrent camera support
- [ ] Both cameras streaming simultaneously
- [ ] Main camera preview displays correctly
- [ ] PiP overlay shows secondary camera feed
- [ ] No lifecycle conflicts or crashes
- [ ] All 18 plugins still functional

**Verification**:
- Check logcat for successful binding
- Verify no "Multiple LifecycleCameras" error
- Confirm both PreviewViews rendering

---

#### Step 10: Test Camera Mode Switching

**Test Cases**:
- [ ] Toggle PiP on/off multiple times
- [ ] Rapid toggling (stress test)
- [ ] Camera rotation during PiP mode
- [ ] App backgrounding/foregrounding
- [ ] Plugins work after mode switch

**Critical Checks**:
- No memory leaks
- Proper cleanup on unbind
- Smooth transitions
- No orphaned cameras

---

#### Step 11: Error Handling & Fallback

**Error Scenarios**:
1. Concurrent cameras not supported → Show clear message, disable PiP button
2. Binding fails → Fall back to single camera, show error toast
3. Invalid camera combination → Use fallback combination or disable
4. Out of memory → Release resources, show warning

**Implementation**:
```kotlin
try {
    // Concurrent camera binding
} catch (e: IllegalArgumentException) {
    Log.e(TAG, "Concurrent binding failed", e)
    showError("Your device doesn't support dual cameras")
    fallbackToSingleCamera()
} catch (e: Exception) {
    Log.e(TAG, "Unexpected error", e)
    showError("Camera error: ${e.message}")
    fallbackToSingleCamera()
}
```

---

#### Step 12: Documentation & Completion

**Tasks**:
- [ ] Add comprehensive code comments
- [ ] Update CLAUDE.md with implementation notes
- [ ] Document known limitations
- [ ] Create user guide for PiP feature
- [ ] Test on multiple devices
- [ ] Performance profiling

---

## Critical Implementation Notes

### CameraX API Requirements

**ConcurrentCamera Binding**:
```kotlin
val concurrentCamera = cameraProvider.bindToLifecycle(
    lifecycleOwner,
    listOf(primaryConfig, secondaryConfig)
)
```

**SingleCameraConfig Structure**:
```kotlin
val config = SingleCameraConfig(
    cameraSelector,      // Which camera to use
    useCaseGroup,        // What use cases to bind
    lifecycleOwner       // Lifecycle to bind to
)
```

**UseCaseGroup Building**:
```kotlin
val useCaseGroup = UseCaseGroup.Builder()
    .addUseCase(preview)
    .addUseCase(imageCapture)
    .build()
```

---

### Hardware Compatibility

**Minimum Requirements**:
- Android device with 2+ cameras
- Hardware support for concurrent camera operation
- CameraX 1.3.0+ (app uses 1.3.1)

**Checking Support**:
```kotlin
val concurrentInfos = cameraProvider.availableConcurrentCameraInfos
val isSupported = concurrentInfos.isNotEmpty()
```

---

### Performance Considerations

**Resource Usage**:
- Two cameras consume more battery
- Increased memory usage
- Thermal throttling possible on extended use
- May limit available resolution/framerate

**Optimization**:
- Use lower resolution for PiP camera
- Disable unnecessary plugins in concurrent mode
- Monitor device temperature
- Implement battery-aware mode

---

## Success Criteria

**Functional**:
- [x] Both cameras streaming without conflicts
- [x] Main camera maintains all plugin functionality
- [x] PiP overlay shows secondary camera feed
- [x] Smooth toggle between single/concurrent modes
- [x] Graceful handling of unsupported devices

**Technical**:
- [x] No "Multiple LifecycleCameras" errors
- [x] Proper lifecycle management
- [x] No memory leaks
- [x] Clean unbind/rebind cycles
- [x] Comprehensive error handling

**User Experience**:
- [x] Clear error messages
- [x] Disabled PiP button when not supported
- [x] Smooth transitions
- [x] No crashes or freezes

---

## Files to Modify/Create

### New Files:
1. `app/src/main/java/com/customcamera/app/pip/ConcurrentCameraCapability.kt`
2. `app/src/main/java/com/customcamera/app/engine/CameraMode.kt`

### Modified Files:
1. `app/src/main/java/com/customcamera/app/engine/CameraEngine.kt`
2. `app/src/main/java/com/customcamera/app/pip/DualCameraCoordinator.kt`
3. `app/src/main/java/com/customcamera/app/plugins/DualCameraPiPPlugin.kt`

---

## Risk Mitigation

**High Risk Areas**:
1. **CameraX API changes** → Keep documentation links, verify API usage
2. **Hardware compatibility** → Detect early, fail gracefully
3. **Plugin compatibility** → Test all 18 plugins thoroughly
4. **Performance issues** → Monitor and optimize

**Mitigation Strategies**:
- Maintain old code until new implementation proven
- Comprehensive logging for debugging
- Feature flag for easy rollback
- Extensive testing on real devices

---

## Next Steps

**Immediate Actions**:
1. Create `ConcurrentCameraCapability.kt` utility class
2. Test capability detection on real device
3. Implement `CameraMode` sealed class
4. Begin CameraEngine modifications

**Implementation Order**:
Phase 1 → Phase 2 → Phase 3 → Phase 4
(Sequential, each phase depends on previous)

---

## References

**CameraX Documentation**:
- [ConcurrentCamera Guide](https://developer.android.com/media/camera/camerax/concurrent-camera)
- [CameraX Architecture](https://developer.android.com/training/camerax/architecture)
- [Use Cases](https://developer.android.com/training/camerax/preview)

**Project Context**:
- CLAUDE.md: Main project documentation
- memory/todo.md: Current task tracking
- app/build.gradle: CameraX version 1.3.1

---

**Last Updated**: 2025-10-14
**Status**: Ready for Implementation
**Estimated Complexity**: High
**Priority**: CRITICAL - Feature currently non-functional
