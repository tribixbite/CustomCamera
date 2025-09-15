# CustomCamera - Master Task List & Implementation Plan

## 🚨 CRITICAL ISSUES (Fix Immediately)

### P0: Camera ID Selection Not Respected (BLOCKING)
**Status**: Under Investigation
**Description**: User selects camera 1 or 2 in selection screen, but camera app always uses camera 0 (back camera)

**Tasks**:
- [ ] **Debug Current Implementation**
  - [ ] Test with actual device and check complete log flow
  - [ ] Analyze camera filter execution in detail
  - [ ] Verify intent extra passing works correctly
  - [ ] Check if CameraX is internally overriding selection

- [ ] **Alternative Camera Selection Approaches**
  - [ ] Try CameraCharacteristics-based selection instead of filter
  - [ ] Implement camera testing during selection phase
  - [ ] Use sequential camera binding to find working cameras
  - [ ] Consider device-specific camera ID handling

- [ ] **Camera Reset and Recovery**
  - [ ] Implement camera reset functionality for failed bindings
  - [ ] Add camera queue flush capabilities
  - [ ] Create camera provider reinitialization

**Code Locations**:
- `CameraActivity.kt:selectCamera()` - Main camera selection logic
- `CameraActivity.kt:createCameraSelectorForIndex()` - Camera filter creation
- `CameraSelectionActivity.kt:setupClickListeners()` - Intent extra passing

### P1: Camera 0 Broken Graceful Handling
**Status**: Needs Testing
**Description**: App should gracefully handle when camera 0 (back camera) is broken/unavailable

**Tasks**:
- [ ] Test fallback logic on devices with broken camera 0
- [ ] Test on devices with only front camera
- [ ] Test on devices with unusual camera configurations
- [ ] Enhance error handling in `handleCameraError()` method

## 🏗️ CORE ARCHITECTURE IMPLEMENTATION

### Phase 1: Plugin Architecture Foundation (Sessions 1-3)

#### Core Plugin System
- [ ] **Create CameraEngine.kt** - Central camera coordinator
  ```kotlin
  class CameraEngine {
      private val pluginManager = PluginManager()
      suspend fun initialize()
      suspend fun bindCamera(config: CameraConfig)
      fun registerPlugin(plugin: CameraPlugin)
  }
  ```

- [ ] **Create CameraPlugin.kt** - Base plugin classes
  ```kotlin
  abstract class CameraPlugin {
      abstract val name: String
      abstract suspend fun initialize(context: CameraContext)
      abstract suspend fun onCameraReady(camera: Camera)
      abstract fun cleanup()
  }
  ```

- [ ] **Create PluginManager.kt** - Plugin lifecycle management
  ```kotlin
  class PluginManager {
      fun registerPlugin(plugin: CameraPlugin)
      fun initializeAll(context: CameraContext)
      fun processFrame(image: ImageProxy)
  }
  ```

- [ ] **Create CameraContext.kt** - Shared state and utilities
  ```kotlin
  class CameraContext {
      val cameraProvider: ProcessCameraProvider
      val debugLogger: DebugLogger
      val settingsManager: SettingsManager
  }
  ```

#### Debug Infrastructure Foundation
- [ ] **Create DebugLogger.kt** - Comprehensive logging system
  ```kotlin
  class DebugLogger {
      fun logCameraAPI(action: String, details: Map<String, Any>)
      fun logCameraBinding(cameraId: String, result: BindingResult)
      fun exportDebugLog(): String
  }
  ```

- [ ] **Create CameraAPIMonitor.kt** - API communication monitoring
  ```kotlin
  class CameraAPIMonitor {
      fun monitorCameraProvider(): Flow<CameraEvent>
      fun logCameraCharacteristics(cameraId: String)
      fun trackFrameProcessing()
  }
  ```

## 🎯 ADVANCED CAMERA FEATURES

### Phase 2: Focus Control System (Sessions 4-5)

#### Auto Focus Implementation
- [ ] **Create AutoFocusPlugin.kt**
  - [ ] Setup continuous auto focus
  - [ ] Implement tap-to-focus functionality
  - [ ] Create focus indicator UI
  - [ ] Add focus lock capability

- [ ] **Create TapToFocusHandler.kt**
  ```kotlin
  class TapToFocusHandler {
      fun setupTouchListener(previewView: PreviewView, camera: Camera)
      fun createFocusPoint(x: Float, y: Float): MeteringPoint
      fun triggerAutoFocus(meteringPoint: MeteringPoint)
      fun showFocusIndicator(x: Float, y: Float)
  }
  ```

#### Manual Focus Implementation
- [ ] **Create ManualFocusPlugin.kt**
  - [ ] Create manual focus distance slider
  - [ ] Add focus lock toggle
  - [ ] Implement focus distance indicator
  - [ ] Add reset to auto focus functionality

### Phase 3: Dual Camera PiP Overlay (Sessions 6-7)

#### PiP System Implementation
- [ ] **Create PiPPlugin.kt** - Picture-in-picture overlay system
  ```kotlin
  class PiPPlugin : UIPlugin() {
      private val frontCamera = CameraInstance()
      private val rearCamera = CameraInstance()

      suspend fun bindDualCameras()
      fun createPiPOverlay(): PiPOverlayView
  }
  ```

- [ ] **Create DualCameraManager.kt** - Simultaneous camera management
  - [ ] Bind both front and rear cameras simultaneously
  - [ ] Handle dual camera preview surfaces
  - [ ] Synchronize capture between both cameras
  - [ ] Manage dual camera resource allocation

- [ ] **Create PiPOverlayView.kt** - PiP UI implementation
  ```kotlin
  class PiPOverlayView : FrameLayout {
      fun setMainPreview(preview: PreviewView)
      fun setPiPPreview(preview: PreviewView)
      fun animatePiPPosition()
      fun togglePiPSize()
      fun swapCameras()
  }
  ```

#### PiP Features
- [ ] **PiP Position Control**
  - [ ] Draggable PiP window positioning
  - [ ] Corner snapping for PiP overlay
  - [ ] PiP size adjustment controls
  - [ ] Camera swap functionality (main <-> PiP)

### Phase 4: Computer Vision Integration (Sessions 8-10)

#### Automatic Barcode/QR Scanning
- [ ] **Create BarcodePlugin.kt** - ML Kit barcode scanning
  ```kotlin
  class BarcodePlugin : ProcessingPlugin() {
      private val scanner = BarcodeScanning.getClient()
      override suspend fun processFrame(image: ImageProxy): ProcessingResult
      fun highlightDetectedCodes(barcodes: List<Barcode>)
  }
  ```

- [ ] **Barcode Scanning Features**
  - [ ] Real-time barcode detection and highlighting
  - [ ] Support multiple barcode formats (QR, UPC, Code128, etc.)
  - [ ] Auto-action triggers (open URLs, save contacts)
  - [ ] Scanning history and management
  - [ ] Manual scan mode toggle

- [ ] **Create QRScannerPlugin.kt** - Specialized QR code handling
  - [ ] QR code content parsing (URLs, WiFi, contacts, text)
  - [ ] Automatic action suggestions based on QR content
  - [ ] QR code generation functionality
  - [ ] QR scanning overlay with corner detection

#### Scanning UI Components
- [ ] **Create ScanningOverlayPlugin.kt** - Scanning UI overlay
  - [ ] Barcode highlighting with bounding boxes
  - [ ] QR code corner detection indicators
  - [ ] Scan result display and actions
  - [ ] Scanning mode toggle controls

### Phase 5: Custom Pre-Shot Crop System (Sessions 11-12)

#### Crop System Implementation
- [ ] **Create CropPlugin.kt** - Pre-shot crop functionality
  ```kotlin
  class CropPlugin : UIPlugin() {
      private var cropArea: RectF = RectF(0.25f, 0.25f, 0.75f, 0.75f)
      fun applyCropToCapture(image: ImageProxy): ImageProxy
  }
  ```

- [ ] **Create CropOverlayView.kt** - Interactive crop interface
  ```kotlin
  class CropOverlayView : View {
      override fun onDraw(canvas: Canvas) // Crop overlay rendering
      override fun onTouchEvent(event: MotionEvent): Boolean // Drag/resize
  }
  ```

#### Crop Features
- [ ] **Interactive Crop Controls**
  - [ ] Draggable crop area with visual feedback
  - [ ] Resize handles for crop area adjustment
  - [ ] Aspect ratio constraints and presets
  - [ ] Grid overlay for composition guidance
  - [ ] Real-time crop preview

- [ ] **Crop Integration**
  - [ ] Apply crop to photo capture
  - [ ] Apply crop to video recording
  - [ ] Save crop presets for reuse
  - [ ] Reset crop to full frame

## 🔧 PROFESSIONAL CAMERA CONTROLS

### Phase 6: Manual Camera Controls (Sessions 13-15)

#### Professional Control System
- [ ] **Create ProControlsPlugin.kt** - Professional manual controls
  ```kotlin
  class ProControlsPlugin : UIPlugin() {
      fun addISOControl(range: IntRange)
      fun addShutterSpeedControl(range: ClosedFloatingPointRange<Float>)
      fun addExposureCompensation()
      fun addWhiteBalanceControl()
  }
  ```

#### Individual Controls
- [ ] **ISO Control**
  - [ ] ISO range slider (50-6400)
  - [ ] Real-time ISO value display
  - [ ] Auto ISO toggle
  - [ ] ISO performance impact warnings

- [ ] **Shutter Speed Control**
  - [ ] Shutter speed range (1/8000s - 30s)
  - [ ] Bulb mode for extended exposures
  - [ ] Shutter speed display with fractions
  - [ ] Motion blur preview indication

- [ ] **Exposure & White Balance**
  - [ ] Exposure compensation slider (-2 to +2 EV)
  - [ ] White balance presets (auto, daylight, cloudy, tungsten, fluorescent)
  - [ ] Manual color temperature control (2000K-10000K)
  - [ ] White balance fine-tuning

#### Advanced Controls
- [ ] **Focus Distance Control**
  - [ ] Manual focus distance slider
  - [ ] Focus distance display (cm/m/infinity)
  - [ ] Focus peaking indicator
  - [ ] Hyperfocal distance calculator

- [ ] **Aperture Control** (if supported by device)
  - [ ] Variable aperture control
  - [ ] Depth of field preview
  - [ ] Aperture value display

### Phase 7: Analysis and Monitoring Tools (Sessions 16-18)

#### Image Analysis
- [ ] **Create HistogramPlugin.kt** - Real-time histogram analysis
  ```kotlin
  class HistogramPlugin : ProcessingPlugin() {
      override suspend fun processFrame(image: ImageProxy): ProcessingResult
      private fun calculateHistogram(image: ImageProxy): Histogram
  }
  ```

- [ ] **Histogram Features**
  - [ ] RGB histogram display
  - [ ] Luminance histogram
  - [ ] Over/under exposure warnings
  - [ ] Dynamic range analysis

- [ ] **Create ExposureAnalysisPlugin.kt** - Exposure monitoring
  - [ ] Real-time exposure analysis
  - [ ] Exposure warnings and suggestions
  - [ ] Dynamic range measurement
  - [ ] Optimal exposure recommendations

#### Performance Monitoring
- [ ] **Frame Rate Monitoring**
  - [ ] Real-time FPS display
  - [ ] Frame processing time analysis
  - [ ] Memory usage tracking
  - [ ] Camera performance metrics

## 🛠️ COMPREHENSIVE DEBUG SYSTEM

### Phase 8: Debug Infrastructure (Sessions 19-21)

#### Camera API Communication Monitor
- [ ] **Enhanced CameraAPIMonitor.kt**
  ```kotlin
  class CameraAPIMonitor {
      fun logCameraProviderCall(method: String, params: Map<String, Any>)
      fun logCameraBinding(cameraId: String, useCases: List<UseCase>)
      fun logCameraControl(action: String, params: Map<String, Any>)
      fun generateDebugReport(): String
  }
  ```

#### Debug UI System
- [ ] **Create DebugActivity.kt** - Comprehensive debug interface
  - [ ] Live camera information display
  - [ ] Camera enumeration with characteristics
  - [ ] Individual camera testing buttons
  - [ ] Camera API call log viewer
  - [ ] Debug data export functionality

#### Camera Troubleshooting Tools
- [ ] **Create CameraResetManager.kt** - Camera recovery tools
  ```kotlin
  class CameraResetManager {
      suspend fun resetCameraID(cameraId: String)
      suspend fun flushCameraQueue()
      suspend fun reinitializeCameraProvider()
      fun clearCameraCache()
  }
  ```

#### Verbose Debug Output
- [ ] **Create VerboseLogger.kt** - Detailed logging system
  - [ ] Camera API call logging with parameters
  - [ ] Frame processing pipeline monitoring
  - [ ] Plugin activity tracking
  - [ ] Error history with context
  - [ ] Performance metrics collection

### Debug Features Implementation
- [ ] **Debug Output Page in Settings**
  - [ ] Live debug log viewer
  - [ ] Log level filtering (Verbose, Debug, Info, Error)
  - [ ] Search functionality in logs
  - [ ] Export debug logs to file
  - [ ] Clear debug log functionality

- [ ] **Camera ID Debug Tools**
  - [ ] Test each camera ID individually
  - [ ] Display camera characteristics for each ID
  - [ ] Show camera binding success/failure status
  - [ ] Camera availability testing
  - [ ] Force camera release and rebind

## 🎨 ADVANCED UI/UX FEATURES

### Phase 9: Complete Settings System (Sessions 22-24)

#### Settings Categories Implementation
- [ ] **Camera Settings**
  - [ ] Default camera ID selection
  - [ ] Photo resolution options (from camera capabilities)
  - [ ] Video resolution selection
  - [ ] Photo quality slider (1-100%)
  - [ ] Video quality selection
  - [ ] Flash mode preferences
  - [ ] Grid overlay toggle
  - [ ] Level indicator toggle

- [ ] **Focus Settings**
  - [ ] Auto focus mode selection
  - [ ] Tap-to-focus enable/disable
  - [ ] Manual focus default distance
  - [ ] Focus indicator style options

- [ ] **Advanced Settings**
  - [ ] RAW capture enable/disable
  - [ ] Histogram overlay toggle
  - [ ] Camera info overlay
  - [ ] Performance monitoring toggle
  - [ ] Verbose logging enable/disable

- [ ] **Scanning Settings**
  - [ ] Barcode auto-scan toggle
  - [ ] QR auto-scan toggle
  - [ ] Scanning overlay visibility
  - [ ] Auto-action enable/disable

- [ ] **PiP Settings**
  - [ ] PiP overlay enable/disable
  - [ ] PiP position preferences
  - [ ] PiP size selection
  - [ ] PiP transparency adjustment

#### Settings UI Implementation
- [ ] **Create SettingsActivity.kt**
  - [ ] Settings category organization
  - [ ] Preference persistence with SharedPreferences
  - [ ] Settings import/export functionality
  - [ ] Reset to defaults option

- [ ] **Create activity_settings.xml**
  - [ ] Modern Material3 settings layout
  - [ ] Category sections with headers
  - [ ] Switch, slider, and dropdown controls
  - [ ] Preview sections for visual settings

### Phase 10: Video Recording System (Sessions 25-26)

#### Video Capture Implementation
- [ ] **Add VideoCapture to CameraActivity.kt**
  ```kotlin
  private var videoCapture: VideoCapture<Recorder>? = null
  private var activeRecording: Recording? = null
  ```

- [ ] **Video Recording Features**
  - [ ] Record/stop button with state indication
  - [ ] Recording duration timer display
  - [ ] Video quality selection in real-time
  - [ ] Recording indicator overlay
  - [ ] Pause/resume recording functionality

- [ ] **Video Effects and Processing**
  - [ ] Real-time video stabilization
  - [ ] Video filters and color grading
  - [ ] Video resolution and bitrate control
  - [ ] Audio recording with level indicators

### Phase 11: Enhanced Gallery System (Sessions 27-28)

#### In-App Gallery
- [ ] **Create GalleryActivity.kt** - Photo/video management
  - [ ] Grid view of captured media
  - [ ] Photo detail view with EXIF data
  - [ ] Video playback with controls
  - [ ] Share and delete functionality
  - [ ] Bulk operations (select multiple)

- [ ] **Gallery Integration**
  - [ ] Last photo preview in camera interface
  - [ ] Quick access to recent photos
  - [ ] Photo metadata display
  - [ ] Sharing controls with multiple apps

#### Photo Metadata System
- [ ] **Create PhotoMetadata.kt** - EXIF and custom metadata
  ```kotlin
  data class PhotoMetadata(
      val cameraId: String,
      val timestamp: Date,
      val location: Location?,
      val exposureSettings: ExposureSettings,
      val imageSize: Size,
      val cropArea: RectF?
  )
  ```

## 🎛️ PROFESSIONAL FEATURES

### Phase 12: Manual Camera Controls (Sessions 29-31)

#### Individual Control Components
- [ ] **Create ISOControl.kt**
  - [ ] ISO range slider (50-6400)
  - [ ] Real-time noise preview
  - [ ] Auto ISO toggle
  - [ ] ISO performance impact display

- [ ] **Create ShutterSpeedControl.kt**
  - [ ] Shutter speed range (1/8000s - 30s)
  - [ ] Bulb mode for long exposures
  - [ ] Motion blur indicators
  - [ ] Shutter speed fraction display

- [ ] **Create ExposureControl.kt**
  - [ ] Exposure compensation (-2 to +2 EV)
  - [ ] Real-time exposure preview
  - [ ] Over/under exposure warnings
  - [ ] Suggested exposure adjustments

#### Advanced Professional Features
- [ ] **Manual White Balance**
  - [ ] Color temperature slider (2000K-10000K)
  - [ ] White balance presets
  - [ ] Custom white balance from reference
  - [ ] White balance fine-tuning controls

- [ ] **Focus Controls**
  - [ ] Manual focus distance control
  - [ ] Focus peaking overlay
  - [ ] Hyperfocal distance calculator
  - [ ] Focus stacking for macro photography

### Phase 13: Analysis Tools (Sessions 32-33)

#### Real-time Analysis
- [ ] **Histogram Display**
  - [ ] RGB channel histograms
  - [ ] Luminance histogram
  - [ ] Histogram overlay toggle
  - [ ] Histogram-based exposure guidance

- [ ] **Exposure Analysis**
  - [ ] Dynamic range measurement
  - [ ] Highlight/shadow clipping warnings
  - [ ] Optimal exposure suggestions
  - [ ] Zone system overlay

#### Image Quality Tools
- [ ] **Sharpness Analysis**
  - [ ] Real-time sharpness measurement
  - [ ] Focus confirmation indicators
  - [ ] Optimal aperture suggestions
  - [ ] Depth of field preview

## 🌙 SPECIALIZED MODES

### Phase 14: Night Mode and HDR (Sessions 34-35)

#### Night Photography
- [ ] **Create NightModePlugin.kt**
  - [ ] Low-light detection
  - [ ] Multi-frame noise reduction
  - [ ] Extended exposure handling
  - [ ] Night mode UI indicators

#### HDR Implementation
- [ ] **Create HDRPlugin.kt**
  - [ ] Multi-exposure capture
  - [ ] HDR tone mapping
  - [ ] Bracketing controls
  - [ ] HDR preview processing

## 🔧 TECHNICAL DEBT & POLISH

### Code Quality Improvements
- [ ] **Fix Deprecated API Usage**
  - [ ] Replace deprecated systemUiVisibility with WindowInsetsController
  - [ ] Update to modern Android 12+ APIs
  - [ ] Remove deprecated CameraX APIs

- [ ] **Enhanced Error Handling**
  - [ ] Create custom exception classes for camera errors
  - [ ] More granular error recovery strategies
  - [ ] Better user feedback for different error types

### Performance Optimization
- [ ] **Memory Management**
  - [ ] Optimize camera preview memory usage
  - [ ] Efficient bitmap handling for processing
  - [ ] Background thread optimization for image analysis

- [ ] **Battery Optimization**
  - [ ] Reduce camera processing when not needed
  - [ ] Optimize flash usage
  - [ ] Background processing optimization

### UI/UX Polish
- [ ] **Camera Selection UI Enhancement**
  - [ ] Add camera preview thumbnails to selection buttons
  - [ ] Better visual selection indicators
  - [ ] Smooth transitions between selection and camera

- [ ] **Modern UI Refinements**
  - [ ] Improved button animations and feedback
  - [ ] Better loading states and progress indicators
  - [ ] Enhanced error message presentation
  - [ ] Accessibility improvements

## 📱 MISSING RESOURCES

### Drawable Resources
- [ ] **Complete Icon Set**
  - [ ] Refine existing vector icons for better visual quality
  - [ ] Add missing button states (pressed, focused, disabled)
  - [ ] Create consistent icon sizing and styling
  - [ ] Add night mode compatible icons

### Layout Resources
- [ ] **Additional Layout Files**
  - [ ] `activity_settings.xml` - Comprehensive settings interface
  - [ ] `activity_gallery.xml` - In-app photo gallery
  - [ ] `activity_debug.xml` - Debug and troubleshooting interface
  - [ ] `item_camera_option.xml` - Enhanced camera selection item
  - [ ] `view_crop_overlay.xml` - Crop overlay component
  - [ ] `view_pip_overlay.xml` - Picture-in-picture overlay

## 🎯 IMMEDIATE NEXT SESSION PRIORITIES

### Critical Path (Must Fix First)
1. **P0**: Camera ID selection bug resolution
2. **P1**: Core plugin architecture foundation
3. **P2**: Basic debug infrastructure

### Development Sequence
1. **Fix camera selection** using alternative approaches
2. **Establish plugin system** with CameraEngine
3. **Add debug monitoring** for better troubleshooting
4. **Implement tap-to-focus** as first advanced feature
5. **Create settings framework** for configuration

### Session Commands
```bash
cd ~/git/swype/CustomCamera

# Review status
cat CLAUDE.md
cat memory/todo.md

# Development workflow
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb logcat -d | grep "customcamera"

# Focus on camera ID selection fix first
```

---

**MASTER TASK LIST STATUS**: All tasks consolidated from memory files
**TOTAL TASKS**: 100+ implementation items across 6 phases
**CRITICAL PATH**: Camera ID selection bug blocks advanced development
**ARCHITECTURE**: Plugin-based modular system for extensibility