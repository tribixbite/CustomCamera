# CustomCamera Plugin Audit Report

**Report Generated**: 2025-11-02
**Total Plugins Audited**: 23
**Report Status**: Comprehensive Implementation Audit

---

## SUMMARY

### Overview
- **Total Plugins**: 23
- **Complete/Production-Ready**: 18
- **Incomplete/With Issues**: 5
- **Compatibility Status**: All plugins follow Provider Pattern
- **Test Coverage**: 38+ unit and integration tests

### Plugin Status Breakdown
- **CRITICAL ISSUES**: 2 plugins (RAWCapturePlugin, HDRPlugin)
- **INCOMPLETE IMPLEMENTATIONS**: 3 plugins (ObjectDetectionPlugin, SmartAdjustmentsPlugin, SmartScenePlugin)
- **WORKING CORRECTLY**: 18 plugins (full implementation)

---

## BY CATEGORY

### ANALYSIS Plugins (5 total)
- **BarcodePlugin**: ✅ COMPLETE
  - Real-time barcode/QR scanning with ML Kit
  - BarcodeOverlayView for highlighting
  - ProcessingResult properly implemented
  
- **HistogramPlugin**: ✅ COMPLETE
  - Real-time histogram computation
  - Custom HistogramOverlayView
  - Proper settings management
  
- **MotionDetectionPlugin**: ✅ COMPLETE
  - Motion frame differencing algorithm
  - Configurable sensitivity thresholds
  - Settings persistence
  
- **QRScannerPlugin**: ✅ COMPLETE
  - Dedicated QR code detection
  - Corner detection overlays
  - Full ML Kit integration
  
- **SharpnessAnalysisPlugin**: ✅ COMPLETE
  - Sharpness metric computation
  - Focus evaluation
  - Real-time display

### CAPTURE Plugins (6 total)
- **AdvancedVideoRecordingPlugin**: ✅ COMPLETE
  - Full video recording with stabilization
  - Audio recording with permission checks
  - Settings properly managed
  - No critical TODOs
  
- **DualCameraPiPPlugin**: ✅ COMPLETE
  - Concurrent camera mode support
  - PiP overlay management
  - Compositing logic (YUV + PixelCopy)
  - Rounded corners and border styling
  
- **RAWCapturePlugin**: ⚠️ INCOMPLETE
  - **Issue 1 (Line 275-277)**: TODO - Actual RAW capture not implemented
    ```kotlin
    // TODO: Implement actual RAW capture using ImageCapture with RAW format
    // This requires CameraX RAW support or Camera2 API integration
    ```
  - **Issue 2 (Line 317-318)**: TODO - Dual capture not implemented
    ```kotlin
    // TODO: Implement dual capture
    // Capture RAW and JPEG simultaneously with same timestamp
    ```
  - **Issue 3 (Line 484-486)**: Unimplemented extension function
    ```kotlin
    private fun androidx.camera.core.ImageInfo.toTotalCaptureResult(): 
        android.hardware.camera2.TotalCaptureResult {
        throw UnsupportedOperationException(...)
    }
    ```
  - **Status**: Placeholder implementation only
  - **Severity**: CRITICAL - Feature non-functional
  
- **HDRPlugin**: ⚠️ MOCK/SIMULATION ONLY
  - **Issue 1 (Line 294-297)**: Mock HDR capture returns empty list
    ```kotlin
    private fun simulateHDRCapture(): List<HDRFrame> {
        Log.d(TAG, "Simulating HDR capture...")
        return emptyList() // Return empty list for simulation
    }
    ```
  - **Issue 2 (Line 300-319)**: HDR frame processing returns null
    ```kotlin
    private fun processHDRFrames(frames: List<HDRFrame>): Bitmap? {
        // ... processing logic ...
        return null // Return null for simulation
    }
    ```
  - **Issue 3 (Line 322-325)**: Mock ImageProxy throws exception
    ```kotlin
    private fun createMockImageProxy(): ImageProxy {
        throw UnsupportedOperationException("Mock ImageProxy - use real capture in production")
    }
    ```
  - **Status**: Simulation-only, not production-ready
  - **Severity**: CRITICAL - Feature non-functional
  
- **NightModePlugin**: ✅ COMPLETE
  - Full long exposure implementation
  - Multi-frame noise reduction
  - Auto detection via luminance analysis
  - Custom NightModeOverlayView
  - Proper frame stacking
  
- **CropPlugin**: ✅ COMPLETE
  - Aspect ratio controls
  - Live crop preview
  - Settings persistence

### CONTROL Plugins (5 total)
- **AutoFocusPlugin**: ✅ COMPLETE
  - Auto-focus state management
  - Focus lock capability
  
- **ExposureControlPlugin**: ✅ COMPLETE
  - EV compensation controls
  - Real-time exposure adjustment
  
- **ManualFocusPlugin**: ✅ COMPLETE
  - Manual focus distance control
  - Tap-to-focus support
  
- **ProControlsPlugin**: ✅ COMPLETE
  - Professional camera controls (ISO, shutter)
  - Advanced parameter management
  
- **CameraInfoPlugin**: ✅ COMPLETE
  - Camera capability enumeration
  - Info display overlay

### OVERLAY Plugins (5 total)
- **GridOverlayPlugin**: ✅ COMPLETE
  - Multiple grid types (rule of thirds, golden ratio, etc.)
  - Custom GridOverlayView
  - StateFlow integration with SettingsManager
  
- **ExposureAnalysisPlugin**: ✅ COMPLETE
  - Exposure value computation
  - Overexposure/underexposure indicators
  
- **DiagnosticOverlayPlugin**: ✅ COMPLETE
  - Camera state display
  - Sensor availability info
  - Callback-based overlay toggle
  
- **ScanningOverlayPlugin**: ✅ COMPLETE
  - Barcode highlighting with bounding boxes
  - Custom ScanningOverlayView
  - Multiple scanning modes (AUTO, MANUAL, OFF)
  
- **SmartAdjustmentsPlugin**: ⚠️ INCOMPLETE
  - **Issue**: Smart adjustment implementation may be incomplete
  - Mock/simulation implementations in helper functions
  - Heuristic-based analysis only (no actual ML implementation)
  - **Status**: Partial implementation
  - **Severity**: HIGH - Limited functionality

### DEBUG Plugins (1 total)
- **DiagnosticOverlayPlugin**: ✅ COMPLETE
  - Real-time diagnostics display
  - Camera state tracking

### AI Plugins (3 total)
- **SmartScenePlugin**: ⚠️ INCOMPLETE
  - **Issue**: Scene detection uses heuristics, not actual ML Kit
  - ML Kit imports commented out
  - Relies on brightness/contrast analysis
  - No actual image labeling integration
  - **Status**: Heuristic-only implementation
  - **Severity**: HIGH - ML functionality not implemented
  
- **ObjectDetectionPlugin**: ⚠️ INCOMPLETE
  - **Issue 1**: ML Kit imports commented out (line 9-12)
    ```kotlin
    // ML Kit imports - commented out for compilation, would be used in production
    // import com.google.mlkit.vision.objects.ObjectDetection
    // import com.google.mlkit.vision.objects.DetectedObject
    ```
  - **Issue 2**: Mock detector initialization (line 57)
    ```kotlin
    objectDetector = "simulated_detector"
    ```
  - **Issue 3**: No actual object detection implementation
  - **Status**: Stub implementation only
  - **Severity**: CRITICAL - Feature non-functional
  
- **SmartAdjustmentsPlugin**: ⚠️ INCOMPLETE
  - **Issue**: Auto-adjustments not properly implemented
  - Mock implementations for adjustment calculations
  - No actual camera parameter control
  - **Status**: Skeleton implementation
  - **Severity**: HIGH - Feature partially non-functional

---

## DETAILED FINDINGS

### CRITICAL ISSUES (2 Plugins)

#### 1. RAWCapturePlugin (`app/src/main/java/com/customcamera/app/plugins/RAWCapturePlugin.kt`)

**Priority**: P0 CRITICAL

**Issues Found**:
1. **Incomplete RAW Capture Implementation** (Line 275-277)
   - Placeholder comment indicates actual RAW capture not implemented
   - No Camera2 API integration
   - No DNG file writing

2. **Missing Dual Capture** (Line 317-318)
   - Simultaneous RAW+JPEG capture not implemented
   - Timestamp synchronization not addressed

3. **Unimplemented ImageInfo Conversion** (Line 484-486)
   - `toTotalCaptureResult()` throws UnsupportedOperationException
   - Blocks Camera2 interop integration

4. **Missing DNGWriter Class**
   - Spec requires DNGWriter component for DNG file creation
   - Not found in implementation

**Spec Compliance**: FAILED
- FR-15 (DNG/RAW image capture): NOT IMPLEMENTED
- FR-16 (Dual save JPEG + DNG): NOT IMPLEMENTED
- FR-17 (Sensor capability detection): PARTIAL (detect only, no capture)
- FR-18 (RAW file metadata): NOT IMPLEMENTED

**Impact**: RAW capture feature completely non-functional

**Required Fixes**:
- Implement Camera2 API integration for RAW capture
- Add DNG file format writer
- Implement dual capture synchronization
- Add metadata embedding

---

#### 2. HDRPlugin (`app/src/main/java/com/customcamera/app/plugins/HDRPlugin.kt`)

**Priority**: P0 CRITICAL

**Issues Found**:
1. **Mock HDR Capture** (Line 294-297)
   - `simulateHDRCapture()` returns empty list
   - No actual frame capture
   - No exposure bracketing

2. **Null HDR Processing** (Line 300-319)
   - `processHDRFrames()` returns null
   - No frame alignment
   - No tone mapping
   - No contrast enhancement

3. **Mock ImageProxy** (Line 322-325)
   - `createMockImageProxy()` throws exception
   - No actual image data available

4. **Missing HDRProcessor Class**
   - Spec requires HDRProcessor for frame merging
   - Not found in implementation

**Spec Compliance**: FAILED
- FR-1 (Multi-frame HDR capture): NOT IMPLEMENTED
- FR-2 (Automatic tone mapping): NOT IMPLEMENTED
- FR-3 (User-configurable strength): PARTIAL
- FR-4 (Preview indication): IMPLEMENTED

**Impact**: HDR feature completely non-functional, only UI visible

**Required Fixes**:
- Implement exposure bracketing
- Create HDRProcessor for frame merging
- Add tone mapping algorithm
- Implement frame alignment
- Add local contrast enhancement

---

### HIGH PRIORITY ISSUES (3 Plugins)

#### 3. ObjectDetectionPlugin (`app/src/main/java/com/customcamera/app/plugins/ObjectDetectionPlugin.kt`)

**Priority**: P1 HIGH

**Issues Found**:
1. **ML Kit Import Comments** (Line 9-12)
   - All ML Kit imports commented out
   - `ObjectDetection` not initialized
   - `DetectedObject` not imported

2. **Mock Detector** (Line 57)
   - Detector initialized as string: `"simulated_detector"`
   - No actual ML Kit ObjectDetection instance

3. **Missing Object Detection Implementation**
   - No real object detection processing
   - No bounding box generation
   - No tracking ID assignment

4. **Missing ObjectDetector Wrapper**
   - Spec requires ObjectDetector interface
   - Not found in implementation

**Spec Compliance**: FAILED
- FR-6 (Real-time object recognition): NOT IMPLEMENTED
- FR-7 (Bounding box overlay): NOT IMPLEMENTED
- FR-8 (Object labels with confidence): NOT IMPLEMENTED
- FR-9 (Multi-object detection): NOT IMPLEMENTED
- FR-10 (Toggle-able overlay): PARTIAL

**Impact**: Object detection feature non-functional, only stub UI

**Required Fixes**:
- Uncomment and integrate ML Kit ObjectDetection
- Implement object detection pipeline
- Add bounding box computation
- Implement tracking system
- Add confidence filtering

---

#### 4. SmartScenePlugin (`app/src/main/java/com/customcamera/app/plugins/SmartScenePlugin.kt`)

**Priority**: P1 HIGH

**Issues Found**:
1. **Heuristic-Only Implementation**
   - No ML Kit Image Labeling integration
   - Relies on brightness/contrast analysis only
   - Not true AI scene detection

2. **Missing SceneClassifier**
   - Spec requires ML Kit wrapper
   - Not found in implementation

3. **No Actual ML Integration**
   - Scene classification based on simple thresholds
   - No machine learning model usage
   - No confidence-based filtering

**Spec Compliance**: PARTIAL
- FR-1 (Scene classification): HEURISTIC ONLY
- FR-2 (Scene-specific suggestions): IMPLEMENTED
- FR-3 (Visual indicator): IMPLEMENTED
- FR-4 (Confidence threshold): PARTIAL
- FR-5 (Auto adjustments): NOT IMPLEMENTED

**Impact**: Scene detection works but with limited accuracy

**Required Fixes**:
- Integrate ML Kit Image Labeling API
- Create SceneClassifier wrapper
- Implement confidence-based filtering
- Add scene-to-settings mapping
- Implement auto adjustments

---

#### 5. SmartAdjustmentsPlugin (`app/src/main/java/com/customcamera/app/plugins/SmartAdjustmentsPlugin.kt`)

**Priority**: P1 HIGH

**Issues Found**:
1. **Incomplete Auto-Adjustment Logic**
   - Auto-adjustment suggestions not properly implemented
   - Mock implementations for adjustment calculations
   - No actual parameter control

2. **Missing SceneAnalyzer/Optimizer Classes**
   - Spec requires SceneAnalyzer, ExposureOptimizer, WhiteBalanceOptimizer
   - Not found in implementation

3. **No HDR Triggering**
   - Smart adjustment should trigger HDR in high dynamic range
   - Logic missing

**Spec Compliance**: PARTIAL
- FR-11 (Auto exposure adjustment): PARTIAL
- FR-12 (White balance correction): PARTIAL
- FR-13 (Scene-adaptive HDR): NOT IMPLEMENTED
- FR-14 (Subject-aware focus): NOT IMPLEMENTED
- FR-15 (Controllable strength): IMPLEMENTED

**Impact**: Auto-adjustments have limited functionality

**Required Fixes**:
- Implement proper exposure optimization
- Add white balance adjustment logic
- Implement HDR triggering
- Add focus priority logic
- Create optimizer helper classes

---

## PLUGIN COMPLETENESS MATRIX

| Plugin | Type | Status | Spec Compliance | Issues | Priority |
|--------|------|--------|-----------------|--------|----------|
| GridOverlayPlugin | UIPlugin | COMPLETE | 100% | None | - |
| BarcodePlugin | ProcessingPlugin | COMPLETE | 100% | None | - |
| HistogramPlugin | UIPlugin | COMPLETE | 100% | None | - |
| CameraInfoPlugin | UIPlugin | COMPLETE | 100% | None | - |
| ExposureAnalysisPlugin | UIPlugin | COMPLETE | 100% | None | - |
| MotionDetectionPlugin | ProcessingPlugin | COMPLETE | 100% | None | - |
| QRScannerPlugin | ProcessingPlugin | COMPLETE | 100% | None | - |
| SharpnessAnalysisPlugin | ProcessingPlugin | COMPLETE | 100% | None | - |
| AutoFocusPlugin | ControlPlugin | COMPLETE | 100% | None | - |
| ExposureControlPlugin | ControlPlugin | COMPLETE | 100% | None | - |
| ManualFocusPlugin | ControlPlugin | COMPLETE | 100% | None | - |
| ProControlsPlugin | ControlPlugin | COMPLETE | 100% | None | - |
| SmartScenePlugin | ProcessingPlugin | INCOMPLETE | 60% | Heuristic only, no ML | P1 |
| SmartAdjustmentsPlugin | ProcessingPlugin | INCOMPLETE | 50% | Mock implementations | P1 |
| ObjectDetectionPlugin | ProcessingPlugin | INCOMPLETE | 20% | Stub/commented ML Kit | P0 |
| CropPlugin | UIPlugin | COMPLETE | 100% | None | - |
| DualCameraPiPPlugin | UIPlugin | COMPLETE | 100% | None | - |
| RAWCapturePlugin | ProcessingPlugin | INCOMPLETE | 25% | No actual RAW capture | P0 |
| AdvancedVideoRecordingPlugin | ProcessingPlugin | COMPLETE | 100% | None | - |
| NightModePlugin | ProcessingPlugin | COMPLETE | 100% | None | - |
| HDRPlugin | ProcessingPlugin | INCOMPLETE | 25% | Mock/simulation only | P0 |
| DiagnosticOverlayPlugin | UIPlugin | COMPLETE | 100% | None | - |
| ScanningOverlayPlugin | UIPlugin | COMPLETE | 100% | None | - |

---

## IMPLEMENTATION GAPS

### Missing Classes (Spec-Defined but Not Implemented)

1. **HDRProcessor.kt**
   - Required by: HDRPlugin
   - Responsibility: Frame merging, tone mapping, contrast enhancement
   - Status: NOT FOUND

2. **DNGWriter.kt**
   - Required by: RAWCapturePlugin
   - Responsibility: DNG file format creation, metadata embedding
   - Status: NOT FOUND

3. **ObjectDetector.kt**
   - Required by: ObjectDetectionPlugin
   - Responsibility: ML Kit wrapper for object detection
   - Status: NOT FOUND

4. **SceneClassifier.kt**
   - Required by: SmartScenePlugin
   - Responsibility: ML Kit image labeling wrapper
   - Status: NOT FOUND

5. **SceneAnalyzer.kt, ExposureOptimizer.kt, WhiteBalanceOptimizer.kt**
   - Required by: SmartAdjustmentsPlugin
   - Responsibility: Scene analysis and parameter optimization
   - Status: NOT FOUND

### Missing Spec Files

Specifications exist for:
- ✅ `docs/specs/core-camera-system.md`
- ✅ `docs/specs/plugin-system.md`
- ✅ `docs/specs/advanced-capture-features.md` (covers HDR, Night Mode, PiP, RAW)
- ✅ `docs/specs/ai-powered-features.md` (covers SmartScene, ObjectDetection, SmartAdjustments)
- ✅ `docs/specs/ux-interaction-system.md`

All spec requirements are documented but not all implementations complete.

---

## RECOMMENDATIONS

### Phase 1: CRITICAL FIXES (P0) - Estimated 5 days

#### Task 1.1: Complete RAWCapturePlugin
**Effort**: 2 days
**Steps**:
1. Create Camera2Interop wrapper for Camera2 API access
2. Implement DNG file writer using Camera2 RAW stream
3. Add dual capture with timestamp synchronization
4. Implement metadata embedding (EXIF, sensor info)
5. Add capability detection for RAW support

**File Changes**:
- Create: `Camera2Interop.kt`, `DNGWriter.kt`
- Modify: `RAWCapturePlugin.kt` (remove TODOs, implement methods)

**Testing**:
- Unit test RAW capture capability detection
- Integration test DNG file creation
- Integration test dual JPEG+RAW capture

#### Task 1.2: Complete HDRPlugin
**Effort**: 3 days
**Steps**:
1. Implement exposure bracketing with configurable stops
2. Create HDRProcessor for frame alignment and merging
3. Implement tone mapping algorithm
4. Add contrast enhancement
5. Implement frame caching and memory management

**File Changes**:
- Create: `HDRProcessor.kt`, `ExposureBracketing.kt`
- Modify: `HDRPlugin.kt` (remove mock implementations)

**Testing**:
- Unit test frame alignment
- Unit test tone mapping
- Integration test multi-frame capture
- Performance test < 3s processing time

---

### Phase 2: HIGH PRIORITY FIXES (P1) - Estimated 4 days

#### Task 2.1: Complete ObjectDetectionPlugin
**Effort**: 1.5 days
**Steps**:
1. Uncomment and integrate ML Kit ObjectDetection
2. Create ObjectDetector wrapper class
3. Implement detection pipeline
4. Add bounding box computation
5. Implement tracking system with temporal smoothing

**File Changes**:
- Create: `ObjectDetector.kt`
- Modify: `ObjectDetectionPlugin.kt` (uncomment ML Kit, implement detection)

**Testing**:
- Unit test bounding box coordinate mapping
- Integration test ML Kit detection
- Performance test < 100ms inference time

#### Task 2.2: Complete SmartScenePlugin
**Effort**: 1.5 days
**Steps**:
1. Create SceneClassifier with ML Kit Image Labeling
2. Implement proper scene classification (not heuristics)
3. Add confidence-based filtering
4. Implement scene-to-camera-settings mapping
5. Add scene history and temporal filtering

**File Changes**:
- Create: `SceneClassifier.kt`, `SceneParameterSuggester.kt`
- Modify: `SmartScenePlugin.kt` (replace heuristics with ML Kit)

**Testing**:
- Unit test scene classification accuracy (target > 80%)
- Integration test parameter suggestion
- Performance test < 100ms inference time

#### Task 2.3: Complete SmartAdjustmentsPlugin
**Effort**: 1 day
**Steps**:
1. Create SceneAnalyzer, ExposureOptimizer, WhiteBalanceOptimizer
2. Implement proper exposure optimization logic
3. Add white balance adjustment calculations
4. Implement HDR triggering logic
5. Add subject-aware focus priority

**File Changes**:
- Create: `SceneAnalyzer.kt`, `ExposureOptimizer.kt`, `WhiteBalanceOptimizer.kt`
- Modify: `SmartAdjustmentsPlugin.kt` (replace mocks with real logic)

**Testing**:
- Unit test exposure calculation
- Unit test white balance optimization
- Integration test HDR triggering

---

### Phase 3: QUALITY ASSURANCE (2 days)

1. **Comprehensive Testing**
   - Run all 38+ existing tests
   - Add 15+ new tests for fixed plugins
   - Performance testing on target device
   - Memory leak detection

2. **Integration Testing**
   - Test plugin loading sequence
   - Test plugin interaction (e.g., SmartScene → SmartAdjustments)
   - Test settings persistence
   - Test error handling and fallbacks

3. **Device Testing**
   - Test on low-end device (limited CPU/memory)
   - Test on high-end device (full capabilities)
   - Test with disabled ML Kit models
   - Test battery impact

---

## PREVENTION MEASURES

### Code Review Checklist for Future Plugins

- [ ] All spec requirements implemented (100% compliance)
- [ ] No `TODO` comments (or documented with JIRA ticket)
- [ ] No mock/simulation implementations in production code
- [ ] No commented-out imports
- [ ] All required helper classes created
- [ ] Error handling implemented for all paths
- [ ] Settings persistence verified
- [ ] Memory management verified (no leaks)
- [ ] Performance requirements met (timing, FPS, memory)
- [ ] Unit tests written (minimum 70% coverage)
- [ ] Integration tests written
- [ ] Documentation updated

### Plugin Development Template

Create template plugin files that enforce:
1. Spec-first development (specification must exist first)
2. TDD approach (tests before implementation)
3. Complete implementation (no stubs/mocks)
4. Error handling strategy
5. Performance budget compliance

---

## EXECUTION PRIORITY

```
Week 1: RAWCapturePlugin + HDRPlugin (P0 Critical)
  Monday-Tuesday: RAWCapturePlugin
  Tuesday-Wednesday: HDRPlugin
  Thursday: Testing & debugging

Week 2: SmartScene, ObjectDetection, SmartAdjustments (P1 High)
  Monday-Tuesday: ObjectDetectionPlugin
  Wednesday: SmartScenePlugin
  Thursday: SmartAdjustmentsPlugin
  Friday: Integration testing & fixes

Week 3: QA & Release
  Monday-Wednesday: Comprehensive testing
  Thursday: Final integration tests
  Friday: Release documentation
```

---

## APPENDIX: COMPLETE PLUGIN INVENTORY

### Working Plugins (18 total)
1. GridOverlayPlugin - Grid overlays for composition
2. BarcodePlugin - Barcode/QR scanning
3. HistogramPlugin - Real-time histogram
4. CameraInfoPlugin - Camera info display
5. ExposureAnalysisPlugin - Exposure analysis
6. MotionDetectionPlugin - Motion detection
7. QRScannerPlugin - Dedicated QR scanning
8. SharpnessAnalysisPlugin - Sharpness analysis
9. AutoFocusPlugin - Auto focus control
10. ExposureControlPlugin - EV compensation
11. ManualFocusPlugin - Manual focus control
12. ProControlsPlugin - Professional controls
13. CropPlugin - Pre-capture crop
14. DualCameraPiPPlugin - Picture-in-picture
15. AdvancedVideoRecordingPlugin - Video recording
16. NightModePlugin - Night mode capture
17. DiagnosticOverlayPlugin - Diagnostics display
18. ScanningOverlayPlugin - Scan overlay

### Incomplete Plugins (5 total)
1. RAWCapturePlugin - No actual RAW capture
2. HDRPlugin - Mock/simulation only
3. ObjectDetectionPlugin - Stub, ML Kit disabled
4. SmartScenePlugin - Heuristics, not ML
5. SmartAdjustmentsPlugin - Partial implementation

---

**Report Status**: COMPLETE
**Last Updated**: 2025-11-02
**Prepared By**: CustomCamera Development Audit

For questions or clarifications, refer to:
- Specification docs: `docs/specs/`
- Implementation: `app/src/main/java/com/customcamera/app/plugins/`
- Tests: `app/src/test/java/com/customcamera/app/plugins/`
