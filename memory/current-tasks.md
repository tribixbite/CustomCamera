# CustomCamera - Current Tasks & Issues

## Active Session Status (2025-09-14)

### 🚨 CRITICAL ISSUES (Fix Immediately)

#### Issue #1: Camera ID Selection Not Respected
**Priority**: P0 - Critical Bug
**Status**: Under Investigation
**Description**: User selects camera 1 or 2 in selection screen, but camera app always uses camera 0 (back camera)

**Evidence**:
- Camera selection UI working correctly
- Intent passing appears correct (has EXTRA_CAMERA_INDEX)
- Camera enumeration shows multiple cameras available
- Camera selector filter might not be constraining properly

**Investigation Done**:
- ✅ Added comprehensive logging to both activities
- ✅ Enhanced camera selector creation with debugging
- ✅ Verified intent extra passing logic
- ✅ Added camera filter debugging

**Next Steps**:
- [ ] Test with actual device and check complete log flow
- [ ] Try alternative camera selection approach (CameraCharacteristics)
- [ ] Implement camera testing during selection phase
- [ ] Consider device-specific camera ID handling

**Code Locations**:
- `CameraActivity.kt:selectCamera()` - Main camera selection logic
- `CameraActivity.kt:createCameraSelectorForIndex()` - Camera filter creation
- `CameraSelectionActivity.kt:setupClickListeners()` - Intent extra passing

#### Issue #2: Camera 0 Broken Graceful Handling
**Priority**: P1 - High
**Status**: Needs Testing
**Description**: App should gracefully handle when camera 0 (back camera) is broken/unavailable

**Current Implementation**:
- Fallback logic exists in `selectCamera()` method
- Error handling in `handleCameraError()` method
- Needs testing on device with broken camera 0

**Test Scenarios**:
- [ ] Device with only front camera
- [ ] Device with broken back camera
- [ ] Device with unusual camera configuration

## 🔧 IMPLEMENTATION TASKS

### UI/UX Improvements
- [ ] **Polish Camera Selection UI**
  - Add camera preview thumbnails to selection buttons
  - Improve visual selection indicators
  - Add smooth transitions between screens

- [ ] **Settings Screen Implementation**
  - Create SettingsActivity.kt
  - Add camera resolution options
  - Implement photo quality settings
  - Add timer functionality
  - Grid overlay toggle option

- [ ] **Gallery Integration Enhancement**
  - Create in-app gallery view
  - Show last captured photo in camera interface
  - Add photo sharing capabilities
  - Implement photo metadata display

### Camera Feature Additions
- [ ] **Video Recording**
  - Add VideoCapture use case to camera binding
  - Implement record/stop UI controls
  - Add video quality selection
  - Recording duration indicator

- [ ] **Advanced Camera Controls**
  - Manual focus (tap to focus implementation)
  - Pinch-to-zoom gesture handling
  - Exposure compensation slider
  - White balance control options

- [ ] **Pro Camera Features**
  - Manual camera controls (ISO, shutter speed)
  - Histogram overlay display
  - RAW photo capture option
  - Night mode for low-light photography

### Technical Improvements
- [ ] **Camera Repository Pattern**
  - Create CameraRepository.kt for camera management abstraction
  - Separate camera logic from UI components
  - Enable easier testing and maintenance

- [ ] **Enhanced Error Handling**
  - Custom exception classes for camera errors
  - More granular error recovery strategies
  - Better user feedback for different error types

- [ ] **Performance Optimization**
  - Optimize camera preview performance
  - Reduce memory usage during photo capture
  - Implement background threading for camera operations

## 📋 COMPLETED WORK

### ✅ Core Implementation
- [x] Project structure with Kotlin + CameraX + Material3
- [x] MainActivity with camera app launcher
- [x] CameraSelectionActivity with camera detection
- [x] CameraActivity with floating UI design
- [x] Permission handling with modern Activity Result API
- [x] Basic photo capture with file saving
- [x] Camera switching functionality
- [x] Flash control with state management
- [x] Modern UI animations and interactions

### ✅ Error Handling & Debugging
- [x] Comprehensive logging throughout camera pipeline
- [x] Graceful permission denial handling
- [x] Camera provider initialization error handling
- [x] Layout inflation error protection
- [x] Camera binding failure recovery
- [x] No cameras available scenario handling

### ✅ UI/UX Foundation
- [x] Material3 theme integration
- [x] Samsung/Google-style floating controls
- [x] Fullscreen immersive camera experience
- [x] Auto-selection of first camera for better UX
- [x] Modern button animations and feedback

## 🗂️ FILE STRUCTURE

### Source Files (app/src/main/java/com/customcamera/app/)
```
MainActivity.kt                 # App launcher (61 lines)
├── onCreate()                  # Activity setup and click listeners
├── setupClickListeners()      # Camera app launch handling

CameraSelectionActivity.kt      # Camera detection and selection (167 lines)
├── onCreate()                  # Activity setup and permission handling
├── detectAvailableCameras()   # Camera enumeration with CameraX
├── setupCameraButtons()       # Dynamic UI creation for camera options
├── createCameraButton()       # Individual camera selection button
├── updateButtonSelection()    # Visual selection state management
└── setupClickListeners()      # Intent creation for camera launch

CameraActivity.kt               # Main camera interface (280+ lines)
├── onCreate()                  # Activity setup and camera initialization
├── startCamera()              # Camera provider initialization
├── bindCameraUseCases()       # Preview and capture use case binding
├── selectCamera()             # Camera selection logic ⚠️ NEEDS FIX
├── createCameraSelectorForIndex() # Camera filter creation
├── capturePhoto()             # Photo capture with file handling
├── switchCamera()             # Runtime camera switching
├── toggleFlash()              # Flash control with state management
├── handleCameraError()        # Error recovery and user feedback
└── Animation methods          # UI feedback animations
```

### Layout Files (app/src/main/res/layout/)
```
activity_main.xml               # Simple launcher interface
activity_camera_selection.xml   # Camera selection with scrollable list
activity_camera.xml            # Floating camera controls (Samsung/Google style)
```

### Resource Files
```
values/strings.xml              # User-facing text
values/colors.xml              # Material3 color scheme
values/themes.xml              # Material3 theme configuration
drawable/*.xml                 # Camera UI icons and backgrounds
```

## 🔄 CAMERA SELECTION FLOW

### Current Flow (Working but Camera ID Issue)
```
MainActivity
    ↓ (Open Camera button)
CameraSelectionActivity
    ↓ (Detect cameras, show list)
User selects camera X
    ↓ (Continue button with Intent extra)
CameraActivity
    ↓ (Receive camera index from intent)
Camera binding ⚠️ (Issue: Always uses camera 0)
```

### Intent Data Flow
```
CameraSelectionActivity.kt:55
intent.putExtra(EXTRA_CAMERA_INDEX, selectedCameraIndex)
    ↓
CameraActivity.kt:59
cameraIndex = intent.getIntExtra(EXTRA_CAMERA_INDEX, 0)
    ↓
CameraActivity.kt:selectCamera()
Uses cameraIndex to select specific camera ⚠️ BUT FAILS
```

## 🧪 DEBUGGING STRATEGIES

### Camera Selection Debug
1. **Log Analysis**: Check full log flow from selection to binding
2. **Alternative Approaches**:
   - Try CameraCharacteristics-based selection
   - Use sequential camera testing during selection
   - Implement camera preview in selection screen

### Device Testing
1. **Multiple Camera Configurations**: Test on devices with 1, 2, 3+ cameras
2. **Broken Camera Scenarios**: Test graceful handling
3. **Different OEMs**: Samsung, Google, OnePlus, etc.

## 🚀 FUTURE ROADMAP

### Phase 1: Core Stability (Next Session)
- Fix camera ID selection critical bug
- Add missing drawable resources
- Test broken camera handling thoroughly

### Phase 2: Feature Enhancement
- Implement settings screen with camera options
- Add video recording capability
- Enhanced gallery integration

### Phase 3: Advanced Features
- Pro camera controls (manual settings)
- Night mode photography
- Portrait mode with depth effects

### Phase 4: Polish & Optimization
- Performance optimization
- UI/UX refinement
- Comprehensive testing across devices

---

## 📞 CONTINUATION INSTRUCTIONS

**For next session in CustomCamera directory:**

1. **Start Here**: Review this CLAUDE.md and memory/current-tasks.md
2. **Priority**: Fix camera ID selection issue in CameraActivity.kt
3. **Tools**: Use enhanced logging to debug camera selection flow
4. **Test**: Install updated APK and verify camera selection works
5. **Document**: Update this file with progress and new findings

**Key Commands for Next Session:**
```bash
cd ~/git/swype/CustomCamera
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb logcat -d | grep "customcamera"
```