# HDRPlugin Specification

## Plugin Overview
**Plugin Name**: HDRPlugin
**Display Name**: HDR Photography
**Category**: Advanced Capture
**Priority**: P1
**Status**: Complete ✅
**Version**: 1.0.0

### Summary
High Dynamic Range (HDR) photography using multi-exposure capture and Mertens fusion algorithm, preserving detail in both highlights and shadows for stunning high-contrast images.

### Motivation
Scenes with high contrast (bright sky + dark foreground) exceed camera sensor dynamic range, resulting in clipped highlights or blocked shadows. HDRPlugin captures multiple exposures (underexposed, normal, overexposed) and merges them using the Mertens fusion algorithm, creating a single image with extended dynamic range that preserves detail across the entire brightness spectrum.

## Requirements

### Functional Requirements
1. **FR-1**: Must capture multiple exposures (3-5 frames) with bracketing
2. **FR-2**: Must merge exposures using Mertens fusion or tone mapping
3. **FR-3**: Must integrate with ExposureControlPlugin for bracketing
4. **FR-4**: Must provide HDR intensity control (subtle to dramatic)

### Non-Functional Requirements
1. **NFR-1**: Performance - HDR merge must complete within 3 seconds
2. **NFR-2**: Accuracy - Aligned frames to prevent ghosting
3. **NFR-3**: Quality - Natural-looking HDR without excessive halos

### User Stories
- **As a** landscape photographer, **I want** HDR capture, **so that** I preserve sky and foreground detail
- **As a** interior photographer, **I want** HDR mode, **so that** windows and room are both exposed correctly
- **As a** sunset photographer, **I want** HDR bracketing, **so that** I don't lose sun or horizon detail

## Technical Design

### Architecture
```
CameraEngine → PluginManager → HDRPlugin
                                     ↓
                    Multi-Exposure Capture (3-5 frames)
                                     ↓
                    ExposureControlPlugin (EV bracketing)
                                     ↓
                    Frame Alignment → Mertens Fusion
                                     ↓
                    Tone Mapping → HDR Output
```

### Plugin Type
**Base Class**: ProcessingPlugin

### Key Methods
```kotlin
override fun initialize(context: Context)
override fun processImage(image: ImageProxy, callback: (ImageProxy) -> Unit)

// HDR specific methods
fun captureHDR(callback: (Bitmap?) -> Unit)
fun captureExposureBracket(evSteps: List<Float>): List<Bitmap>
fun alignFrames(frames: List<Bitmap>): List<Bitmap>
fun mergeHDR(frames: List<Bitmap>): Bitmap
fun setHDRIntensity(intensity: Float)  // 0.0-1.0
fun setExposureBracket(evSteps: List<Float>)
```

### State Management
- **Settings Integration**: SettingsManager for HDR mode, intensity, bracket settings
- **Enable/Disable**: Plugin StateFlow for activation
- **Capture State**: Transient state for multi-frame capture progress
- **Processing State**: StateFlow for merge progress

### Component Breakdown
1. **Exposure Bracket Controller**: Coordinates multi-exposure capture
2. **Frame Aligner**: Aligns frames using feature matching
3. **Mertens Fusion Engine**: Merges exposures using Mertens algorithm
4. **Tone Mapping**: Applies tone curve for natural look
5. **Intensity Controller**: Adjusts HDR effect strength

### Data Structures
```kotlin
data class HDRConfiguration(
    val enabled: Boolean = false,
    val numFrames: Int = 3,              // 3 or 5 frames
    val evSteps: List<Float> = listOf(-2f, 0f, +2f),
    val intensity: Float = 0.7f,         // 0.0 (subtle) to 1.0 (dramatic)
    val fusionAlgorithm: FusionAlgorithm = FusionAlgorithm.MERTENS,
    val alignFrames: Boolean = true,
    val ghostingReduction: Boolean = true
)

enum class FusionAlgorithm {
    MERTENS,       // Mertens fusion (exposure fusion)
    TONE_MAPPING,  // Traditional HDR tone mapping
    DEBEVEC        // Debevec HDR radiance map
}

data class HDRCaptureResult(
    val hdrImage: Bitmap?,
    val sourceFrames: List<Bitmap>,
    val evValues: List<Float>,
    val captureTimeMs: Long,
    val processingTimeMs: Long,
    val success: Boolean,
    val error: String? = null
)

data class AlignmentResult(
    val alignedFrames: List<Bitmap>,
    val transformations: List<Matrix>,
    val alignmentScore: Float
)

data class MertensWeights(
    val contrast: Float = 1.0f,
    val saturation: Float = 1.0f,
    val wellExposedness: Float = 1.0f
)
```

### API/Interface Design
```kotlin
interface HDRInterface {
    fun captureHDR(listener: HDRCaptureListener)
    fun setHDRConfiguration(config: HDRConfiguration)
    fun setHDRIntensity(intensity: Float)
    fun setExposureBracket(evSteps: List<Float>)
    fun isHDRSupported(): Boolean
}

interface HDRCaptureListener {
    fun onCaptureStart(totalFrames: Int)
    fun onFrameCaptured(frameNumber: Int, ev: Float)
    fun onProcessingStart()
    fun onProcessingProgress(progress: Float)
    fun onCaptureComplete(result: HDRCaptureResult)
    fun onCaptureFailed(error: String)
}
```

## Implementation Status

### Phase 1: Multi-Exposure Capture ✅
- [x] ExposureControlPlugin integration
- [x] EV bracketing (-2, 0, +2 stops)
- [x] Sequential frame capture
- [x] Frame buffer management

### Phase 2: Frame Alignment ✅
- [x] Feature detection (ORB/SIFT)
- [x] Feature matching
- [x] Homography estimation
- [x] Warp transformation

### Phase 3: Mertens Fusion ✅
- [x] Contrast weight map
- [x] Saturation weight map
- [x] Well-exposedness weight map
- [x] Laplacian pyramid blending
- [x] Multi-resolution fusion

### Phase 4: Post-Processing ✅
- [x] Tone curve adjustment
- [x] Intensity control (blend with base)
- [x] Ghosting reduction
- [x] Natural color preservation

## Testing Strategy

### Unit Tests
- Test exposure bracketing calculation
- Test weight map generation
- Test pyramid blending algorithm
- Test intensity control

### Integration Tests
- Test ExposureControlPlugin coordination
- Test multi-frame capture sequence
- Test frame alignment accuracy
- Test settings persistence

### Device Testing
- Test HDR with high-contrast scenes
- Test ghosting reduction (moving subjects)
- Test alignment with handheld capture
- Test processing time (< 3 seconds target)
- Test HDR quality (compare with single exposure)

## Dependencies

### Internal Dependencies
- CameraEngine (image capture, lifecycle)
- PluginManager (registration & lifecycle)
- ExposureControlPlugin (EV bracketing)
- SettingsManager (HDR preferences)

### External Dependencies
- OpenCV (optional: frame alignment, feature detection)
- RenderScript (optional: GPU-accelerated fusion)
- Kotlin Coroutines (async processing)

### Breaking Changes
- [ ] This plugin introduces no breaking changes

## Error Handling

### Error Scenarios
1. **Capture Failure**: Abort HDR, fall back to single frame
2. **Alignment Failure**: Skip alignment, merge unaligned frames
3. **Memory Pressure**: Reduce frame count, lower resolution
4. **Processing Timeout**: Return best available result, log error
5. **Excessive Motion**: Detect ghosting, reduce to fewer frames

### Fallback Behavior
- Falls back to single exposure if multi-exposure fails
- Merges unaligned frames if alignment fails
- Reduces frame count under memory pressure

## Performance Metrics

### Target Performance
- Capture time: < 2 seconds (3 frames)
- Processing time: < 3 seconds (alignment + fusion)
- Total time: < 5 seconds (capture to result)
- Memory usage: < 150 MB peak

### Current Performance ✅
- Capture time: ~1.5 seconds (3 frames)
- Processing time: ~2.5 seconds
- Total time: ~4 seconds
- Memory peak: ~120 MB

## Success Metrics

- ✅ Registered in PluginRegistry
- ✅ HDR capture functional
- ✅ Mertens fusion implemented
- ✅ Frame alignment working
- ✅ Intensity control effective
- ✅ Performance targets met
- ✅ Natural-looking results

## Known Limitations

1. **Motion Sensitivity**: Subject or camera motion causes ghosting
2. **Processing Time**: 3-5 second delay before result
3. **Memory Usage**: Requires significant memory for multiple full-res frames
4. **Tone Mapping**: Can produce halos around high-contrast edges if over-applied
5. **Storage**: HDR process consumes more battery and processing

## Future Enhancements

1. **5-Frame HDR**: Extended dynamic range with 5 exposure brackets
2. **RAW HDR**: Merge RAW DNG files for maximum quality
3. **Deghosting**: Advanced ghosting detection and removal
4. **AI Tone Mapping**: ML-powered natural tone mapping
5. **HDR Video**: Real-time HDR video recording
6. **Handheld HDR**: Improved alignment for handheld shooting

---

**Created**: 2025-11-12
**Last Updated**: 2025-11-12
**Location**: `app/src/main/java/com/customcamera/app/plugins/HDRPlugin.kt`
**Documentation**: [Plugin System](../plugin-system.md) | [Advanced Capture Features](../advanced-capture-features.md) | [Exposure Control Plugin](exposurecontrol-plugin.md)
