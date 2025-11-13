# Concurrent Camera PiP Feature Specification

**Feature**: Dual Camera Picture-in-Picture Mode
**Status**: Production ✅
**Version**: 2.0 (Post CameraX 1.5.0 Fixes)
**Last Updated**: 2025-11-12

---

## Overview

The Concurrent Camera PiP feature enables simultaneous operation of two cameras (typically back + front) with a picture-in-picture overlay. This allows users to capture photos that combine both camera feeds into a single composite image.

**Key Capabilities:**
- Simultaneous preview of two cameras
- Draggable PiP overlay with position and size controls
- Dual camera photo compositing with rounded corners and borders
- Automatic camera capability detection
- Hardware-aware use case management

---

## Architecture

### Core Components

1. **DualCameraPiPPlugin** (`plugins/DualCameraPiPPlugin.kt`)
   - Main plugin coordinating concurrent camera mode
   - Manages PiP overlay UI and settings
   - Provides PreviewView access for bitmap capture

2. **ConcurrentCameraCapability** (`pip/ConcurrentCameraCapability.kt`)
   - Detects concurrent camera support using CameraX API
   - Maps camera combinations using stable Camera IDs
   - Recommends optimal camera pairing (back + front)

3. **DualCameraCompositor** (`utils/DualCameraCompositor.kt`)
   - Composites main camera ImageProxy with PiP camera Bitmap
   - Applies rounded corners, borders, and positioning
   - Handles YUV→Bitmap conversion and JPEG compression

4. **CameraEngine** (`engine/CameraEngine.kt`)
   - Manages camera lifecycle and use case binding
   - Switches between single and concurrent modes
   - Coordinates plugin initialization

---

## CameraX 1.5.0 Regression Fixes

### Issue 1: Camera Detection Failure

**Problem**: After CameraX 1.5.0 upgrade, concurrent camera detection reported "not supported" on devices with hardware capability.

**Root Cause**: CameraX 1.5.0 changed behavior where `CameraInfo` objects returned by `availableConcurrentCameraInfos` are different instances than those in `availableCameraInfos`. This broke object identity-based `indexOf()` mapping, returning -1 for all cameras.

**Solution** (`ConcurrentCameraCapability.kt:68-95`):
```kotlin
// Pre-calculate map of camera IDs to indices for O(1) lookup
val cameraIdToIndexMap = availableCameras.mapIndexed { index, cameraInfo ->
    Camera2CameraInfo.from(cameraInfo).cameraId to index
}.toMap()

// Use stable Camera ID string for mapping instead of object identity
val firstCameraId = Camera2CameraInfo.from(cameraInfoList[0]).cameraId
val firstIndex = cameraIdToIndexMap[firstCameraId]
```

**Benefits:**
- Stable identifier-based matching (Camera ID: "0", "1", "2", etc.)
- Performance: O(1) lookup vs O(N) indexOfFirst
- Resilient to CameraX object instantiation changes

### Issue 2: Use Case Binding Failure

**Problem**: After detection fix, enabling PiP mode failed with "No supported surface combination is found for camera device".

**Root Cause**: Binding 4 use cases exceeded Samsung Galaxy S23 hardware limits:
- Main camera: Preview + ImageCapture (2 use cases)
- PiP camera: Preview + ImageAnalysis (2 use cases)

**Solution** (`CameraEngine.kt:507-513`, `CameraActivityEngine.kt:556-600`):
- **Removed ImageAnalysis** from PiP camera (reduces to 3 total use cases)
- **Use PreviewView.bitmap** for photo compositing instead of ImageAnalysis frames
- Added thread safety check for `PreviewView.getBitmap()` (main thread requirement)
- Added `pipBitmap.recycle()` to prevent memory leaks

**Trade-offs:**
- ✅ **Pro**: Works within hardware limits (3 use cases vs 4)
- ✅ **Pro**: Eliminates persistent high-framerate ImageAnalysis stream
- ⚠️ **Con**: PiP image limited to preview resolution (~1920x1080) vs full sensor
- ✅ **Acceptable**: Small PiP overlay size makes preview resolution sufficient

---

## Technical Design Decisions

### 1. PreviewView.bitmap Approach

**Rationale**: Hardware use case limits necessitate elimination of ImageAnalysis.

**Implementation** (`CameraActivityEngine.kt:562-600`):
```kotlin
// Verify main thread (required by PreviewView.getBitmap())
check(android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
    "PreviewView.getBitmap() must be called on the main thread"
}

// Capture PiP bitmap from PreviewView
val pipPreviewView = dualCameraPiPPlugin?.getPiPPreviewView()
val pipBitmap = pipPreviewView?.bitmap

try {
    // Composite with main camera image
    DualCameraCompositor.compositeImages(mainImage, pipBitmap, pipRect, outputFile)
} finally {
    // Always recycle to prevent memory leak
    pipBitmap.recycle()
}
```

**Memory Safety:**
- `PreviewView.bitmap` returns new Bitmap copy
- Must call `recycle()` to release native memory
- Finally block ensures cleanup even on errors

### 2. Camera ID-Based Mapping

**Rationale**: Object identity unreliable across CameraX versions.

**Implementation** (`ConcurrentCameraCapability.kt:67-95`):
- Use `Camera2CameraInfo.from(cameraInfo).cameraId` for stable identifiers
- Pre-calculate map for O(1) lookup performance
- Handles CameraX object instantiation variations

### 3. Concurrent Camera Detection Flow

```
ProcessCameraProvider
  ↓
availableConcurrentCameraInfos (List<List<CameraInfo>>)
  ↓
Extract Camera IDs using Camera2CameraInfo
  ↓
Map to indices in availableCameraInfos
  ↓
Validate combinations and recommend back+front
  ↓
ConcurrentCameraInfo (isSupported, combinations, recommended)
```

---

## Use Cases

### Primary Use Case: Dual Camera Photo

1. User enables PiP mode via DualCameraPiP button
2. `CameraEngine.switchToConcurrentMode()` binds concurrent cameras
3. Main camera preview shows full screen
4. PiP camera preview shows in draggable overlay
5. User positions PiP overlay and taps capture
6. `CameraActivityEngine.captureRegularPhoto()` captures main ImageProxy
7. `PreviewView.bitmap` captures PiP frame on main thread
8. `DualCameraCompositor` creates composite image
9. Composite saved to file with rounded corners and border

### Hardware Requirements

**Minimum:**
- 2+ physical cameras
- Device reports concurrent camera support via CameraX
- Hardware supports 3 concurrent use cases (Preview + ImageCapture + Preview)

**Tested Devices:**
- Samsung Galaxy S23 (SM-S938U1): ✅ 4 cameras, 2 concurrent combinations

**Known Limitations:**
- Some devices may not support concurrent cameras (hardware/driver limitation)
- Preview resolution for PiP image (~1920x1080, acceptable for overlay)

---

## Configuration

### Settings (via SettingsManager)

| Setting | Type | Default | Description |
|---------|------|---------|-------------|
| `enabled` | Boolean | false | PiP mode active state |
| `position` | PiPPosition | TOP_RIGHT | Overlay position |
| `size` | PiPSize | MEDIUM | Overlay size (SMALL/MEDIUM/LARGE) |
| `mainCamera` | Int | 0 | Main camera index |
| `pipCamera` | Int | 1 | PiP camera index |
| `draggable` | Boolean | true | Allow overlay dragging |
| `autoSwap` | Boolean | true | Auto-swap on camera switch |
| `opacity` | Float | 1.0 | PiP overlay opacity |
| `snapToCorners` | Boolean | true | Snap overlay to corners |

### PiP Sizes

- **SMALL**: 0.20 x 0.15 (20% width, 15% height)
- **MEDIUM**: 0.25 x 0.20 (25% width, 20% height)
- **LARGE**: 0.30 x 0.25 (30% width, 25% height)

---

## Error Handling

### Concurrent Camera Not Supported

**Detection**: `ConcurrentCameraCapability.checkSupport()` returns `isSupported = false`

**Handling**:
- Button remains disabled with "Not Supported" tooltip
- Log warning with specific error message
- Graceful degradation to single camera mode

**Common Causes:**
- Device has < 2 cameras
- Hardware/driver doesn't support concurrent operation
- Use case combination exceeds device limits

### PiP Mode Enable Failure

**Detection**: `CameraEngine.switchToConcurrentMode()` throws exception

**Handling**:
- Disable PiP mode (`_isDualCameraSupported.value = false`)
- Revert to single camera mode
- Show error toast to user
- Log exception with diagnostic information

### Photo Capture Failure

**Detection**: `DualCameraCompositor.compositeImages()` returns false

**Handling**:
- Fallback to screen capture via `captureScreenFallback()`
- Show error toast
- Log composite failure reason

---

## Testing

### Unit Tests

- `ConcurrentCameraCapability` detection logic
- Camera ID mapping correctness
- PiP overlay positioning calculations

### Integration Tests

- Dual camera photo capture end-to-end
- PiP overlay UI interactions
- Concurrent mode switching

### Device Testing

- Samsung Galaxy S23: ✅ Verified concurrent camera support
- Test concurrent camera detection on multiple devices
- Verify graceful degradation when not supported

---

## Performance Considerations

1. **Camera ID Lookup**: O(1) map-based lookup vs O(N) linear search
2. **Memory Management**: Bitmap recycling prevents OutOfMemoryError
3. **Use Case Optimization**: 3 use cases (within hardware limits) vs 4
4. **Thread Safety**: Main executor for PreviewView.bitmap access

---

## Future Enhancements

### Potential Improvements

1. **Selectable Camera Pairs**: Allow user to choose camera combination
2. **PiP Video Recording**: Extend to video capture mode
3. **Real-time Effects**: Apply effects to PiP stream
4. **Multi-PiP**: Support 3+ concurrent cameras on capable devices
5. **Full Resolution PiP**: Explore alternative approaches for full sensor resolution

### Technical Debt

- None identified (post code review fixes)

---

## References

- **CameraX Concurrent Camera Guide**: https://developer.android.com/media/camera/camerax/concurrent-camera
- **PreviewView API**: https://developer.android.com/reference/androidx/camera/view/PreviewView
- **Camera2 Interop**: https://developer.android.com/reference/androidx/camera/camera2/interop/Camera2CameraInfo

---

## Changelog

### v2.0 (2025-11-12) - CameraX 1.5.0 Fixes
- Fixed concurrent camera detection using Camera ID mapping
- Reduced use cases from 4 to 3 (removed ImageAnalysis)
- Implemented PreviewView.bitmap approach
- Added bitmap recycling and thread safety checks
- Performance optimization: O(1) camera ID lookup
- Comprehensive documentation

### v1.0 (2025-10-16) - Initial Implementation
- Basic concurrent camera support
- PiP overlay with drag-and-drop
- Dual camera photo compositing
- Settings and configuration system
