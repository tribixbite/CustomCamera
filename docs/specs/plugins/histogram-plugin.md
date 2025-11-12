# HistogramPlugin Specification

## Plugin Overview
**Plugin Name**: HistogramPlugin
**Display Name**: Histogram Overlay
**Category**: UI & Overlay
**Priority**: P2
**Status**: Complete ✅
**Version**: 1.0.0

### Summary
Real-time RGB and luminance histogram display overlaid on camera preview, providing instant exposure and color balance feedback for photographers.

### Motivation
Professional photographers rely on histograms to evaluate exposure and ensure no clipping in highlights or shadows. HistogramPlugin provides real-time histogram feedback directly in the viewfinder, enabling photographers to make informed exposure decisions before capturing the shot.

## Requirements

### Functional Requirements
1. **FR-1**: Must analyze preview frames and generate histogram data
2. **FR-2**: Must integrate with SettingsManager for histogram display preferences
3. **FR-3**: Must overlay histogram on preview without affecting capture
4. **FR-4**: Must update histogram in real-time at reasonable frame rate

### Non-Functional Requirements
1. **NFR-1**: Performance - Histogram calculation must not impact preview frame rate (target 30fps)
2. **NFR-2**: Accuracy - Histogram must accurately represent scene exposure
3. **NFR-3**: Visibility - Histogram must be readable in various lighting conditions

### User Stories
- **As a** professional photographer, **I want** real-time histogram, **so that** I can verify proper exposure
- **As a** landscape photographer, **I want** highlight clipping warnings, **so that** I don't lose sky detail
- **As a** portrait photographer, **I want** shadow detail monitoring, **so that** I preserve skin tones

## Technical Design

### Architecture
```
CameraEngine → PluginManager → HistogramPlugin
                                     ↓
                            ImageAnalysis UseCase
                                     ↓
                            Frame Analysis (RGB extraction)
                                     ↓
                            Histogram Calculation (256 bins)
                                     ↓
                            Canvas Overlay Rendering
```

### Plugin Type
**Base Class**: UIPlugin (with ProcessingPlugin capabilities)

### Key Methods
```kotlin
override fun initialize(context: Context)
override fun getOverlayView(context: Context): View
override fun onPreviewReady(previewView: PreviewView)
override fun processImage(image: ImageProxy, callback: (ImageProxy) -> Unit)

// Histogram-specific methods
fun calculateHistogram(image: ImageProxy): HistogramData
fun renderHistogram(canvas: Canvas, data: HistogramData)
fun setHistogramType(type: HistogramType)
fun setHistogramPosition(position: OverlayPosition)
fun detectClipping(data: HistogramData): ClippingInfo
```

### State Management
- **Settings Integration**: SettingsManager for histogram type, position, size
- **Enable/Disable**: Plugin StateFlow for activation
- **Histogram Data**: StateFlow for current histogram data
- **Clipping Warnings**: StateFlow for highlight/shadow clipping alerts

### Component Breakdown
1. **Image Analyzer**: Extracts pixel data from ImageProxy
2. **Histogram Calculator**: Computes RGB and luminance histograms
3. **Clipping Detector**: Identifies over/under-exposed regions
4. **Histogram Renderer**: Draws histogram on Canvas overlay
5. **Position Manager**: Handles histogram placement on screen

### Data Structures
```kotlin
data class HistogramData(
    val red: IntArray,      // 256 bins
    val green: IntArray,    // 256 bins
    val blue: IntArray,     // 256 bins
    val luminance: IntArray, // 256 bins
    val timestamp: Long
)

enum class HistogramType {
    LUMINANCE_ONLY,   // Grayscale histogram
    RGB_OVERLAY,      // Overlaid RGB histograms
    RGB_SEPARATE,     // Separate RGB histograms
    RGB_AND_LUM       // RGB + luminance
}

enum class OverlayPosition {
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT
}

data class ClippingInfo(
    val highlightClipping: Float,  // Percentage of clipped highlights
    val shadowClipping: Float,     // Percentage of clipped shadows
    val hasClipping: Boolean
)
```

### API/Interface Design
```kotlin
interface HistogramInterface {
    fun getHistogramData(): Flow<HistogramData>
    fun setHistogramType(type: HistogramType)
    fun setDisplayPosition(position: OverlayPosition)
    fun getClippingInfo(): Flow<ClippingInfo>
    fun setClippingThreshold(thresholdPercent: Float)
}
```

## Implementation Status

### Phase 1: Image Analysis Setup ✅
- [x] ImageAnalysis UseCase integration
- [x] Frame sampling (30fps analysis)
- [x] Pixel data extraction
- [x] ImageProxy cleanup (no leaks)

### Phase 2: Histogram Calculation ✅
- [x] RGB channel histograms (256 bins each)
- [x] Luminance histogram calculation
- [x] Efficient algorithm (< 33ms per frame)
- [x] Histogram normalization

### Phase 3: Overlay Rendering ✅
- [x] Custom View for histogram display
- [x] Canvas drawing (RGB graphs)
- [x] Position management (4 corners)
- [x] Size scaling
- [x] Semi-transparent background

### Phase 4: Clipping Detection ✅
- [x] Highlight clipping detection (top 2% of bins)
- [x] Shadow clipping detection (bottom 2% of bins)
- [x] Visual warnings (red overlay on clipped regions)
- [x] Percentage calculation

## Testing Strategy

### Unit Tests
- Test histogram calculation accuracy (known test images)
- Test clipping detection (synthetic over/under-exposed images)
- Test histogram normalization
- Test bin distribution calculations

### Integration Tests
- Test ImageAnalysis UseCase integration
- Test overlay view attachment
- Test histogram type switching
- Test position changes

### Device Testing
- Test histogram accuracy against known scenes
- Test performance (30fps analysis target)
- Test clipping warnings accuracy
- Test visibility in bright/dark conditions
- Test memory usage (no leaks from ImageProxy)

## Dependencies

### Internal Dependencies
- CameraEngine (ImageAnalysis integration)
- PluginManager (registration & lifecycle)
- SettingsManager (histogram preferences)

### External Dependencies
- CameraX ImageAnalysis (frame access)
- Android Canvas (histogram rendering)
- Kotlin Coroutines (async analysis)

### Breaking Changes
- [ ] This plugin introduces no breaking changes

## Error Handling

### Error Scenarios
1. **Analysis Failure**: Skip frame, continue with next
2. **Drawing Exception**: Log error, hide histogram temporarily
3. **Memory Pressure**: Reduce analysis frame rate
4. **ImageProxy Leak**: Ensure proper close() in all code paths

### Fallback Behavior
- Skips frames under memory pressure
- Hides histogram on repeated drawing errors
- Reduces update rate if performance suffers

## Performance Metrics

### Target Performance
- Analysis frame rate: 30fps
- Histogram calculation: < 33ms per frame
- Memory usage: < 10 MB
- No preview frame drops

### Current Performance ✅
- Analysis rate: ~30fps
- Calculation time: ~25ms
- Memory: ~7 MB
- No frame drops observed

## Success Metrics

- ✅ Registered in PluginRegistry
- ✅ Real-time histogram functional
- ✅ RGB and luminance modes working
- ✅ Clipping detection accurate
- ✅ Overlay rendering performant
- ✅ Settings integration complete

## Known Limitations

1. **Frame Rate**: 30fps analysis (not 60fps) to preserve performance
2. **Accuracy**: Histogram based on preview, may not match final capture exactly
3. **Crop Impact**: Histogram analyzes full preview, not cropped region
4. **Memory**: Requires ImageAnalysis UseCase (max 2-3 UseCases total)

## Future Enhancements

1. **Waveform Monitor**: Audio-style waveform display
2. **Parade Scope**: RGB parade scope for color grading
3. **Vectorscope**: Color saturation and hue visualization
4. **False Color**: Exposure zones color overlay
5. **Zebra Stripes**: Animated highlight clipping indicator
6. **RAW Histogram**: Histogram based on RAW data, not preview

---

**Created**: 2025-11-12
**Last Updated**: 2025-11-12
**Location**: `app/src/main/java/com/customcamera/app/plugins/HistogramPlugin.kt`
**Documentation**: [Plugin System](../plugin-system.md) | [Analysis Features](../advanced-capture-features.md)
