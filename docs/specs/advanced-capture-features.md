# Advanced Capture Features Specification

## Feature Overview
**Feature Name**: Advanced Capture Features (HDR, Night Mode, Dual Camera PiP, RAW/DNG)
**Priority**: P1
**Status**: Complete
**Target Version**: 2.0.0+

### Summary
Professional-grade capture capabilities including HDR photography, low-light optimization, concurrent dual camera with picture-in-picture compositing, and RAW/DNG image capture.

### Motivation
Provide advanced users with pro-level capture features matching flagship camera apps. Enable creative workflows through RAW capture and multi-camera compositions.

## Requirements

### Functional Requirements

#### HDR Plugin
1. **FR-1**: Multi-frame HDR capture with exposure bracketing
2. **FR-2**: Automatic tone mapping and contrast enhancement
3. **FR-3**: User-configurable HDR strength
4. **FR-4**: Preview indication when HDR active

#### Night Mode Plugin
5. **FR-5**: Long exposure capture for low-light scenes
6. **FR-6**: Multi-frame noise reduction
7. **FR-7**: Automatic scene detection (low-light conditions)
8. **FR-8**: Dedicated night mode button UI

#### Dual Camera PiP Plugin
9. **FR-9**: Concurrent camera mode (main + secondary camera)
10. **FR-10**: Picture-in-picture overlay positioning (8 positions)
11. **FR-11**: Configurable PiP size (small, medium, large)
12. **FR-12**: Dual camera photo compositing (YUV + PixelCopy fallback)
13. **FR-13**: Rounded corners and border on PiP overlay
14. **FR-14**: Hardware capability detection

#### RAW Capture Plugin
15. **FR-15**: DNG/RAW image capture via Camera2 interop
16. **FR-16**: Dual save (JPEG + DNG)
17. **FR-17**: Camera sensor capability detection
18. **FR-18**: RAW file metadata embedding

### Non-Functional Requirements
1. **NFR-1**: Performance - HDR/Night processing < 3s, dual camera 60fps preview
2. **NFR-2**: Quality - HDR dynamic range > 12 stops, Night mode noise reduction > 30%
3. **NFR-3**: Compatibility - Graceful fallback on unsupported devices
4. **NFR-4**: Storage - Efficient file formats, proper EXIF metadata

### User Stories
- **As a** photographer, **I want** HDR mode, **so that** I can capture high-contrast scenes
- **As a** user, **I want** night mode, **so that** I can take clear photos in low light
- **As a** creator, **I want** dual camera PiP, **so that** I can create picture-in-picture compositions
- **As a** pro user, **I want** RAW capture, **so that** I can post-process with full sensor data

## Technical Design

### Architecture
```
CameraActivityEngine
    ↓
CameraEngine (mode coordinator)
    ├── Single Camera Mode
    │   ├── HDRPlugin → Multi-frame capture
    │   ├── NightModePlugin → Long exposure
    │   └── RAWCapturePlugin → DNG save
    └── Concurrent Camera Mode
        └── DualCameraPiPPlugin
            ├── DualCameraCoordinator → Camera binding
            ├── PiPOverlayView → Preview display
            └── DualCameraCompositor → Image merging
```

### Component Breakdown

#### 1. HDRPlugin
**Responsibilities**:
- Capture 3 frames at different exposures (EV -1, 0, +1)
- Align frames to compensate for motion
- Merge frames with tone mapping
- Apply local contrast enhancement

**Key Classes**:
- `HDRPlugin.kt` - Main plugin implementation
- `HDRProcessor.kt` - Frame merging and tone mapping
- `ExposureBracketing.kt` - Exposure control

#### 2. NightModePlugin
**Responsibilities**:
- Detect low-light conditions (luminance < threshold)
- Capture long exposure (up to 4 seconds)
- Multi-frame capture and averaging (4-8 frames)
- Noise reduction via frame stacking

**Key Classes**:
- `NightModePlugin.kt` - Main plugin implementation
- `LongExposureCapture.kt` - Extended shutter time
- `NoiseReduction.kt` - Multi-frame averaging

#### 3. DualCameraPiPPlugin
**Responsibilities**:
- Detect concurrent camera hardware capability
- Switch camera engine to concurrent mode
- Manage PiP overlay positioning and sizing
- Composite dual camera images on capture

**Key Classes**:
- `DualCameraPiPPlugin.kt` - Main plugin implementation
- `DualCameraCoordinator.kt` - Concurrent camera binding
- `PiPOverlayView.kt` - Secondary camera preview
- `DualCameraCompositor.kt` - Image merging
- `ConcurrentCameraCapability.kt` - Hardware detection
- `CameraMode.kt` - State management

#### 4. RAWCapturePlugin
**Responsibilities**:
- Detect RAW capture capability
- Use Camera2 interop for RAW capture
- Save DNG file alongside JPEG
- Embed proper metadata

**Key Classes**:
- `RAWCapturePlugin.kt` - Main plugin implementation
- `Camera2Interop.kt` - Camera2 API access
- `DNGWriter.kt` - DNG file creation

### Data Structures
```kotlin
// HDR configuration
data class HDRConfig(
    val strength: Float = 0.5f, // 0.0 to 1.0
    val frameCount: Int = 3,
    val exposureStops: List<Float> = listOf(-1f, 0f, 1f)
)

// Night mode settings
data class NightModeConfig(
    val enabled: Boolean = false,
    val autoDetect: Boolean = true,
    val maxExposureMs: Long = 4000,
    val frameCount: Int = 8
)

// PiP configuration
enum class PiPPosition {
    TOP_LEFT, TOP_CENTER, TOP_RIGHT,
    CENTER_LEFT, CENTER_RIGHT,
    BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT
}

enum class PiPSize {
    SMALL(0.2f),
    MEDIUM(0.3f),
    LARGE(0.4f);
    val scale: Float
}

data class PiPConfig(
    val enabled: Boolean = false,
    val position: PiPPosition = PiPPosition.BOTTOM_RIGHT,
    val size: PiPSize = PiPSize.MEDIUM,
    val showBorder: Boolean = true,
    val roundedCorners: Boolean = true
)

// RAW settings
data class RAWConfig(
    val enabled: Boolean = false,
    val dualSave: Boolean = true, // Save both JPEG and DNG
    val embedMetadata: Boolean = true
)

// Camera mode state
sealed class CameraMode {
    object Single : CameraMode()
    data class Concurrent(
        val mainCamera: Camera,
        val pipCamera: Camera
    ) : CameraMode()
}
```

### API/Interface Design
```kotlin
// HDRPlugin
class HDRPlugin(context: CameraContext) : ProcessingPlugin("hdr", context) {
    suspend fun captureHDR(config: HDRConfig): ImageProxy
    fun setStrength(strength: Float)
}

// NightModePlugin
class NightModePlugin(context: CameraContext) : ProcessingPlugin("night_mode", context) {
    suspend fun captureLongExposure(durationMs: Long): ImageProxy
    fun detectLowLight(luminance: Float): Boolean
}

// DualCameraPiPPlugin
class DualCameraPiPPlugin(context: CameraContext) : UIPlugin("dual_camera_pip", context) {
    suspend fun enablePiP(mainIndex: Int, pipIndex: Int)
    suspend fun disablePiP()
    fun setPiPPosition(position: PiPPosition)
    fun setPiPSize(size: PiPSize)
    fun getPiPOverlayRect(): RectF
}

// RAWCapturePlugin
class RAWCapturePlugin(context: CameraContext) : ProcessingPlugin("raw_capture", context) {
    suspend fun captureRAW(imageCapture: ImageCapture): Pair<ImageProxy, File>
    fun supportsRAW(): Boolean
}

// DualCameraCompositor
object DualCameraCompositor {
    fun compositeImages(
        mainImage: ImageProxy,
        pipImage: ImageProxy,
        pipRect: RectF,
        outputFile: File
    ): Boolean
}
```

### State Management
- **HDR Enabled**: StateFlow in SettingsManager, persisted
- **Night Mode Active**: StateFlow, manual + auto detection
- **PiP Configuration**: StateFlow, persisted (position, size, enabled)
- **RAW Enabled**: StateFlow, persisted
- **Camera Mode**: StateFlow in CameraEngine (Single/Concurrent)

## Implementation Plan

### Phase 1: HDR Implementation (Complete)
**Duration**: 2 days
**Deliverables**:
- [x] HDRPlugin class
- [x] Exposure bracketing
- [x] Frame alignment
- [x] Tone mapping algorithm
- [x] UI toggle

### Phase 2: Night Mode Implementation (Complete)
**Duration**: 1.5 days
**Deliverables**:
- [x] NightModePlugin class
- [x] Long exposure capture
- [x] Multi-frame averaging
- [x] Low-light detection
- [x] Dedicated button

### Phase 3: Concurrent Camera Foundation (Complete)
**Duration**: 3 days
**Deliverables**:
- [x] ConcurrentCameraCapability detection
- [x] CameraMode sealed class
- [x] CameraEngine mode switching
- [x] UseCase limit handling (max 2 per camera)

### Phase 4: PiP UI and Compositing (Complete)
**Duration**: 4 days
**Deliverables**:
- [x] PiPOverlayView implementation
- [x] Position and size controls
- [x] DualCameraCompositor (YUV + PixelCopy)
- [x] Rounded corners and border
- [x] Settings integration

### Phase 5: RAW Capture (Complete)
**Duration**: 1.5 days
**Deliverables**:
- [x] RAWCapturePlugin class
- [x] Camera2 interop integration
- [x] DNG file writing
- [x] Metadata embedding
- [x] Capability detection

### Phase 6: Bug Fixes and Polish (Complete)
**Duration**: 2 days
**Deliverables**:
- [x] PiP transparent background fix
- [x] PiP view layout timing fix
- [x] ProcessCameraProvider sharing fix
- [x] YUV stride handling fix
- [x] Preview reconnection on PiP disable

## Testing Strategy

### Unit Tests
- HDR frame alignment algorithm
- Night mode luminance detection
- PiP position calculation (normalized coordinates)
- YUV to NV21 conversion
- RAW capability detection

### Integration Tests
- HDR capture end-to-end
- Night mode multi-frame capture
- Dual camera mode switching (Single ↔ Concurrent)
- PiP photo compositing (YUV path)
- PiP photo compositing (PixelCopy fallback)
- RAW + JPEG dual save

### Performance Tests
- HDR processing time < 3s
- Night mode capture time acceptable (< 5s for 8 frames)
- Dual camera preview maintains 60fps
- PiP compositing < 1s
- RAW save time < 2s

## Dependencies

### Internal Dependencies
- CameraEngine (mode coordinator)
- PluginManager (lifecycle)
- SettingsManager (configuration)
- CameraActivityEngine (UI integration)

### External Dependencies
- CameraX 1.3.1 (concurrent camera API)
- Camera2 API (RAW capture)
- AndroidX Camera Core
- Bitmap processing (Canvas, Paint)

### Breaking Changes
- [x] Video recording disabled in concurrent camera mode (UseCase limit)
- [x] Requires Android 8.0+ for concurrent camera and PixelCopy

## Security Considerations
- **Storage Access**: Uses scoped storage, no special permissions
- **Camera Data**: All processing local, no network transmission
- **RAW Files**: Contain full sensor data, user must manage privacy

## Error Handling

### Error Scenarios
1. **HDR capture fails**: Fallback to single-frame capture
2. **Night mode too dark**: Show warning, attempt capture anyway
3. **Concurrent camera not supported**: Graceful fallback to single camera
4. **PiP YUV compositing fails**: Automatic fallback to PixelCopy window capture
5. **RAW capture not supported**: Disable RAW toggle, show info toast
6. **UseCase limit exceeded**: Disable video/analysis UseCases in concurrent mode

### Fallback Behavior
- HDR unavailable → standard capture
- Night mode unavailable → standard capture with warning
- Concurrent camera unavailable → PiP disabled, single camera only
- YUV composite fails → PixelCopy window capture
- RAW unavailable → JPEG only

## Documentation Updates
- [x] Architecture docs updated with advanced capture flow
- [x] PiP implementation documented (memory/PIP.md)
- [x] Video stabilization guide created
- [x] Session history includes all PiP fixes

## Success Metrics
- **HDR Adoption**: > 20% users enable HDR for high-contrast scenes
- **Night Mode Usage**: > 30% users try night mode
- **PiP Success Rate**: > 95% successful dual camera composites
- **RAW Adoption**: > 5% pro users enable RAW (niche feature)
- **Performance**: No frame drops in dual camera preview
- **Quality**: HDR/Night images subjectively better than single-frame

## Implementation Notes

### Concurrent Camera UseCase Limits
CameraX concurrent camera mode supports **maximum 2 UseCases per camera**:
- **Main camera**: Preview + ImageCapture (2 UseCases)
- **PiP camera**: Preview only (1 UseCase)
- **Video/ImageAnalysis**: Disabled in concurrent mode, restored on single mode switch

### PiP Compositing Strategy
**Two-tier fallback**:
1. **Primary**: YUV plane compositing (direct sensor data, highest quality)
2. **Fallback**: PixelCopy window capture (captures rendered preview, works if YUV fails)

### YUV_420_888 to NV21 Conversion
Critical for dual camera compositing:
- Proper plane order: Y, U, V
- Row stride and pixel stride handling
- Interleaved VU in NV21 format
- Handles both tightly-packed and interleaved UV data

### ProcessCameraProvider Sharing
**Critical**: Both main camera and PiP camera MUST use same ProcessCameraProvider instance. Separate instances cause binding conflicts.

Solution: CameraEngine provides single provider, DualCameraCoordinator receives it via `setProvider()`.

### PiP View Layout Timing
PiP camera binding must wait for PiPOverlayView layout completion (ViewTreeObserver.OnGlobalLayoutListener) to ensure surface is ready.

## Future Enhancements
- HDR video recording (deferred - complex)
- Night mode timelapse (deferred - advanced)
- Triple camera support (deferred - hardware limited)
- Live HDR preview (deferred - performance intensive)
- RAW burst mode (deferred - storage intensive)

---

**Created**: 2025-10-19
**Last Updated**: 2025-10-19
**Owner**: CustomCamera Development Team
**Status**: Complete, Production-Ready
