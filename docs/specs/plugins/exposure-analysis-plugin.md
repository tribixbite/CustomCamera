# ExposureAnalysisPlugin Specification

## Plugin Overview
**Plugin Name**: ExposureAnalysisPlugin
**Display Name**: Exposure Analysis
**Category**: Analysis & Processing
**Priority**: P2
**Status**: Complete ✅
**Version**: 1.0.0

### Summary
Real-time exposure analysis detecting over-exposure, under-exposure, and optimal exposure zones with visual warnings and recommendations.

### Motivation
Proper exposure is critical for quality photography. ExposureAnalysisPlugin provides real-time analysis of scene exposure, detecting clipped highlights, blocked shadows, and exposure distribution, helping photographers achieve optimal exposure before capture.

## Requirements

### Functional Requirements
1. **FR-1**: Must analyze frame luminance to detect over/under-exposure
2. **FR-2**: Must calculate exposure histogram and statistics
3. **FR-3**: Must provide visual warnings for clipping
4. **FR-4**: Must integrate with SettingsManager for analysis thresholds

### Non-Functional Requirements
1. **NFR-1**: Performance - Analysis must complete within frame time (< 33ms)
2. **NFR-2**: Accuracy - Exposure assessment must be reliable
3. **NFR-3**: Responsiveness - Warnings must appear in real-time

### User Stories
- **As a** photographer, **I want** highlight clipping warnings, **so that** I don't lose sky detail
- **As a** portrait photographer, **I want** shadow detail monitoring, **so that** I preserve skin tones
- **As a** landscape photographer, **I want** exposure recommendations, **so that** I achieve optimal dynamic range

## Technical Design

### Architecture
```
CameraEngine → PluginManager → ExposureAnalysisPlugin
                                     ↓
                            ImageAnalysis UseCase
                                     ↓
                    Luminance Extraction (Y channel)
                                     ↓
                    Histogram Calculation → Statistics
                                     ↓
                    Clipping Detection + Recommendations
```

### Plugin Type
**Base Class**: ProcessingPlugin

### Key Methods
```kotlin
override fun initialize(context: Context)
override fun processImage(image: ImageProxy, callback: (ImageProxy) -> Unit)

// Exposure analysis specific methods
fun calculateLuminance(image: ImageProxy): FloatArray
fun analyzeExposure(luminance: FloatArray): ExposureAnalysis
fun detectClipping(analysis: ExposureAnalysis): ClippingWarnings
fun getExposureRecommendation(analysis: ExposureAnalysis): ExposureRecommendation
```

### State Management
- **Settings Integration**: SettingsManager for clipping thresholds
- **Enable/Disable**: Plugin StateFlow for activation
- **Exposure Analysis**: StateFlow for current exposure state
- **Warnings**: StateFlow for clipping warnings

### Component Breakdown
1. **Luminance Extractor**: Extracts Y channel from YUV image
2. **Histogram Calculator**: Computes luminance histogram (256 bins)
3. **Statistics Engine**: Calculates mean, median, std dev
4. **Clipping Detector**: Identifies over/under-exposed regions
5. **Recommendation Engine**: Suggests exposure corrections

### Data Structures
```kotlin
data class ExposureAnalysis(
    val mean: Float,           // 0.0-255.0
    val median: Float,
    val stdDev: Float,
    val histogram: IntArray,   // 256 bins
    val highlightPercent: Float, // % of pixels > 245
    val shadowPercent: Float,    // % of pixels < 10
    val midtonePercent: Float,   // % of pixels 10-245
    val dynamicRange: Float,     // Stops
    val timestamp: Long
)

data class ClippingWarnings(
    val highlightClipping: Boolean,
    val shadowClipping: Boolean,
    val clippedHighlightPercent: Float,
    val clippedShadowPercent: Float,
    val severity: ClippingSeverity
)

enum class ClippingSeverity {
    NONE,        // < 1% clipping
    MINOR,       // 1-5% clipping
    MODERATE,    // 5-10% clipping
    SEVERE       // > 10% clipping
}

data class ExposureRecommendation(
    val currentEV: Float,
    val recommendedEV: Float,
    val correction: Float,      // Stops to adjust
    val message: String,        // Human-readable advice
    val confidence: Float       // 0.0-1.0
)
```

### API/Interface Design
```kotlin
interface ExposureAnalysisInterface {
    fun getExposureAnalysis(): Flow<ExposureAnalysis>
    fun getClippingWarnings(): Flow<ClippingWarnings>
    fun getExposureRecommendation(): Flow<ExposureRecommendation>
    fun setClippingThreshold(highlightThreshold: Int, shadowThreshold: Int)
}
```

## Implementation Status

### Phase 1: Luminance Analysis ✅
- [x] Y channel extraction from YUV_420_888
- [x] Histogram calculation (256 bins)
- [x] Mean, median, std dev calculation
- [x] Efficient pixel iteration

### Phase 2: Clipping Detection ✅
- [x] Highlight clipping (> 245)
- [x] Shadow clipping (< 10)
- [x] Clipping percentage calculation
- [x] Severity classification

### Phase 3: Recommendations ✅
- [x] EV calculation from histogram
- [x] Optimal exposure estimation
- [x] Correction suggestion (stops)
- [x] Confidence scoring

### Phase 4: Integration ✅
- [x] Real-time StateFlow updates
- [x] Visual warning integration
- [x] HistogramPlugin coordination
- [x] Settings persistence

## Testing Strategy

### Unit Tests
- Test luminance extraction accuracy
- Test histogram calculation (synthetic images)
- Test clipping detection with known thresholds
- Test EV calculation logic

### Integration Tests
- Test ImageAnalysis integration
- Test StateFlow updates
- Test warning triggering
- Test recommendation accuracy

### Device Testing
- Test with various lighting conditions
- Test clipping warnings accuracy
- Test performance (< 33ms analysis)
- Test with over/under-exposed test scenes

## Dependencies

### Internal Dependencies
- CameraEngine (ImageAnalysis)
- PluginManager (registration & lifecycle)
- SettingsManager (thresholds)
- HistogramPlugin (optional coordination)

### External Dependencies
- CameraX ImageAnalysis
- YUV_420_888 format support

### Breaking Changes
- [ ] This plugin introduces no breaking changes

## Error Handling

### Error Scenarios
1. **Analysis Failure**: Skip frame, continue with next
2. **Invalid Format**: Log warning, disable plugin
3. **Math Exception**: Use default values, log error
4. **Threshold Invalid**: Clamp to valid range

### Fallback Behavior
- Skips frames on analysis failure
- Uses conservative thresholds if settings invalid
- Disables recommendations on repeated errors

## Performance Metrics

### Target Performance
- Analysis time: < 33ms per frame
- Frame rate: 30fps maintained
- Memory usage: < 10 MB
- No preview impact

### Current Performance ✅
- Analysis time: ~25ms
- Frame rate: 30fps stable
- Memory: ~7 MB
- No preview impact

## Success Metrics

- ✅ Registered in PluginRegistry
- ✅ Real-time exposure analysis functional
- ✅ Clipping detection accurate
- ✅ Recommendations helpful
- ✅ Performance targets met
- ✅ Settings integration complete

## Known Limitations

1. **Preview-Based**: Analysis based on preview, may differ from final capture
2. **Global Analysis**: Does not analyze individual regions (face, sky, etc.)
3. **Update Rate**: 30fps analysis may lag behind rapid exposure changes
4. **Format Dependency**: Requires YUV_420_888 format

## Future Enhancements

1. **Zone Analysis**: Separate analysis for different scene regions
2. **Face Priority**: Weight exposure towards detected faces
3. **HDR Recommendation**: Suggest HDR when dynamic range exceeds limits
4. **Exposure Bracketing**: Auto-bracket recommendations
5. **ML Enhancement**: AI-powered optimal exposure prediction

---

**Created**: 2025-11-12
**Last Updated**: 2025-11-12
**Location**: `app/src/main/java/com/customcamera/app/plugins/ExposureAnalysisPlugin.kt`
**Documentation**: [Plugin System](../plugin-system.md) | [Histogram Plugin](histogram-plugin.md)
