# CustomCamera - Technical Architecture

## System Architecture

### High-Level Design
```
┌─────────────────┐    ┌──────────────────────┐    ┌─────────────────┐
│   MainActivity  │───▶│ CameraSelectionActivity│───▶│  CameraActivity │
│   (Launcher)    │    │  (Camera Detection)   │    │ (Main Camera)   │
└─────────────────┘    └──────────────────────┘    └─────────────────┘
         │                        │                          │
         ▼                        ▼                          ▼
┌─────────────────┐    ┌──────────────────────┐    ┌─────────────────┐
│ Simple UI with  │    │ Dynamic camera list  │    │ Floating camera │
│ camera button   │    │ with auto-selection  │    │ controls (SOTA) │
└─────────────────┘    └──────────────────────┘    └─────────────────┘
```

### Component Relationships

#### Data Flow
1. **MainActivity** → Launches camera selection
2. **CameraSelectionActivity** → Detects cameras, user selects
3. **Intent** → Passes selected camera index
4. **CameraActivity** → Uses selected camera for preview/capture

#### Key Abstractions
- **ProcessCameraProvider**: CameraX camera management
- **CameraSelector**: Camera filtering and selection
- **ImageCapture**: Photo capture use case
- **Preview**: Camera preview use case

## Core Components Detail

### MainActivity.kt
**Purpose**: Simple app launcher
**Dependencies**: None
**Key Methods**:
- `onCreate()`: Setup launcher UI
- `setupClickListeners()`: Handle camera app launch

**UI Elements**:
- App title and description
- "Open Camera" button (launches CameraSelectionActivity)
- Exit button

### CameraSelectionActivity.kt
**Purpose**: Camera detection and user selection
**Dependencies**: CameraX ProcessCameraProvider
**Key Methods**:
- `detectAvailableCameras()`: Enumerate available cameras
- `setupCameraButtons()`: Create dynamic UI for camera options
- `createCameraButton()`: Factory for camera selection buttons
- `updateButtonSelection()`: Visual selection state

**State Management**:
- `selectedCameraIndex: Int`: Currently selected camera
- `availableCameras: List<CameraInfo>`: Detected cameras
- Auto-selection of first camera for UX

**Intent Contract**:
- **Output**: `EXTRA_CAMERA_INDEX` (Int) → CameraActivity

### CameraActivity.kt ⚠️ **CRITICAL ISSUE HERE**
**Purpose**: Main camera interface with preview and capture
**Dependencies**: CameraX (Preview, ImageCapture, ProcessCameraProvider)
**Key Methods**:

#### Camera Management
- `startCamera()`: Initialize camera provider
- `bindCameraUseCases()`: Bind preview and capture
- **`selectCamera()`**: ⚠️ **BROKEN** - Should use selected index
- `createCameraSelectorForIndex()`: Create camera filter
- `handleCameraError()`: Error recovery

#### Camera Operations
- `capturePhoto()`: Photo capture with file saving
- `switchCamera()`: Runtime camera switching
- `toggleFlash()`: Flash control with state

#### UI Management
- `setupUI()`: Button click handlers
- `updateFlashButton()`: Flash UI state
- Animation methods: Scale, rotation effects

**State Management**:
- `cameraIndex: Int`: Selected camera from intent ⚠️ **NOT WORKING**
- `camera: Camera?`: Active camera instance
- `imageCapture: ImageCapture?`: Capture use case
- `isFlashOn: Boolean`: Flash state

**Intent Contract**:
- **Input**: `EXTRA_CAMERA_INDEX` (Int) from CameraSelectionActivity

## Camera Selection Technical Analysis

### Current Bug Analysis
**Issue**: Camera selector filter not properly constraining to selected camera

**Hypothesis 1**: Camera filter is called but returns wrong camera
```kotlin
// In createCameraSelectorForIndex()
.addCameraFilter { cameraInfos ->
    cameraInfos.filter { it == targetCamera } // ⚠️ May not work as expected
}
```

**Hypothesis 2**: CameraX internally overrides camera selection
- DEFAULT_BACK_CAMERA might take precedence
- Camera binding might ignore filter results

**Hypothesis 3**: Camera identity comparison fails
- `it == targetCamera` might not work for CameraInfo objects
- Need to compare using camera characteristics instead

### Alternative Approaches to Try

#### Approach 1: Lens Facing Selection
```kotlin
val selector = when (targetCamera.lensFacing) {
    CameraSelector.LENS_FACING_FRONT -> CameraSelector.DEFAULT_FRONT_CAMERA
    CameraSelector.LENS_FACING_BACK -> CameraSelector.DEFAULT_BACK_CAMERA
    else -> // Custom filter for external cameras
}
```

#### Approach 2: Camera Characteristics
```kotlin
// Use CameraManager for more direct control
val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
val characteristics = cameraManager.getCameraCharacteristics(cameraId)
```

#### Approach 3: Sequential Testing
```kotlin
// Test each camera during selection phase
private fun testCameraWorking(cameraInfo: CameraInfo): Boolean {
    // Actually try to bind a preview to verify camera works
}
```

## Resource Dependencies

### Required Drawable Resources
Current status of camera UI icons:

#### ✅ Completed
- `camera_control_background.xml` - Floating button background
- `capture_button_background.xml` - Main capture button
- `ic_camera.xml` - Basic camera icon
- `ic_flash_off.xml` - Flash off state
- `ic_flash_on.xml` - Flash on state
- `ic_gallery.xml` - Gallery button icon
- `ic_settings.xml` - Settings button icon
- `ic_switch_camera.xml` - Camera switch icon

#### 🚧 May Need Refinement
- Icon designs could be more polished
- Consider using vector icons from Material Design
- Add pressed/focused states for better feedback

### Layout Dependencies
- All layouts reference correct drawable resources
- ViewBinding generates correct binding classes
- Material3 theme provides consistent styling

## Development Environment

### Build Configuration
- **Kotlin**: 1.8.20
- **Android Gradle Plugin**: 8.0.2
- **Compile SDK**: 34
- **Min SDK**: 24
- **Target SDK**: 34

### Key Dependencies
```gradle
// CameraX
implementation 'androidx.camera:camera-core:1.3.1'
implementation 'androidx.camera:camera-camera2:1.3.1'
implementation 'androidx.camera:camera-lifecycle:1.3.1'
implementation 'androidx.camera:camera-view:1.3.1'

// Modern Android
implementation 'androidx.core:core-ktx:1.12.0'
implementation 'com.google.android.material:material:1.10.0'
implementation 'androidx.activity:activity-ktx:1.8.2'
```

### Build Environment
- **Termux ARM64** build environment
- **AAPT2 Override**: Custom aapt2-arm64 for Termux compatibility
- **Git Repository**: Initialized in CustomCamera directory

## Testing Strategy

### Manual Testing Checklist
- [ ] App launches without crashes
- [ ] Camera selection screen shows all available cameras
- [ ] Camera selection is visually clear (highlighted button)
- [ ] Continue button launches camera with selected camera
- [ ] Camera preview shows correct camera (front vs back)
- [ ] Photo capture works with selected camera
- [ ] Camera switching cycles through available cameras
- [ ] Flash control works (if available)
- [ ] Gallery button opens photo picker
- [ ] Back button returns to main app

### Log Analysis Checklist
- [ ] Camera enumeration: "Available cameras: X"
- [ ] Intent passing: "Intent extra value: X"
- [ ] Camera selection: "Using requested camera X"
- [ ] Camera binding: "Camera bound successfully"
- [ ] Error handling: Any error messages or fallbacks

### Device Compatibility Testing
- [ ] Test on devices with 1 camera (tablet/budget phone)
- [ ] Test on devices with 2 cameras (standard phone)
- [ ] Test on devices with 3+ cameras (flagship phone)
- [ ] Test broken camera scenarios

## Known Issues & Workarounds

### Current Bugs
1. **Camera ID Selection** - Main blocker for proper functionality
2. **System UI Deprecation Warnings** - Low priority cosmetic issue

### Technical Debt
- Deprecated systemUiVisibility calls (Android 11+)
- Could use more modern WindowInsetsController
- Error handling could be more specific
- Camera selection UI could be more polished

### Platform Limitations
- Termux build environment requires custom AAPT2 configuration
- Some advanced camera features may not work on all devices
- Camera enumeration behavior varies by OEM

---

## 🎯 SUCCESS CRITERIA

### Minimum Viable Product (MVP)
- [x] App launches and shows camera selection
- [x] Camera selection detects available cameras
- [ ] **Selected camera ID is respected** ⚠️ **CRITICAL BUG**
- [x] Basic photo capture works
- [x] UI follows modern camera app design patterns

### Full Feature Set
- [ ] All camera controls working (capture, switch, flash, gallery)
- [ ] Settings screen with camera configuration options
- [ ] Video recording capability
- [ ] Advanced camera features (manual controls, effects)
- [ ] Robust error handling for all edge cases

---

*Architecture documented: 2025-09-14*
*Critical focus: Camera ID selection bug in CameraActivity.kt*