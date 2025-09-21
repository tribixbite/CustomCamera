# CustomCamera - Claude Code Configuration

## Project Overview
Modern Kotlin camera app with Samsung/Google-style floating UI, robust camera selection, and graceful error handling for broken cameras.

**Status**: MVP Complete, Camera ID Selection Issue Under Investigation
**Technology**: Kotlin, CameraX, Material3, ViewBinding
**Architecture**: Clean Android with defensive programming patterns

## Build Commands
- `./gradlew assembleDebug`: Build debug APK
- `./gradlew clean assembleDebug`: Clean build
- `adb install -r app/build/outputs/apk/debug/app-debug.apk`: Install app
- `adb logcat -d | grep "customcamera\|CameraActivity\|CameraSelection"`: Check app logs

## Task Management

### MASTER TASK LIST
**All tasks are tracked in `memory/todo.md` - ALWAYS check and update this file**

Current status:
- **Critical Issues**: Camera ID selection bug (P0), Camera 0 broken handling (P1)
- **Implementation Queue**: 100+ tasks across 14 phases
- **Architecture**: Plugin-based modular system for extensibility
- **Next Priority**: Fix camera selection bug in `CameraActivity.kt:selectCamera()`

### Quick Task Reference
**Before each session**: Review `memory/todo.md` for current priorities
**During development**: Update task completion status in `memory/todo.md`
**Session end**: Commit progress and update `memory/todo.md` with new findings

## Technical Debt
- Deprecated systemUiVisibility warnings (Android 11+ issue)
- ViewBinding could be further leveraged for type safety
- Error handling could be more granular with custom exceptions
- Camera selection screen UI could be more polished

## Architecture

### Core Files
```
app/src/main/java/com/customcamera/app/
├── MainActivity.kt                    # App entry point with camera launch
├── CameraSelectionActivity.kt        # Camera detection and selection UI
├── CameraActivity.kt                  # Main camera interface with capture
└── [Future files]
    ├── CameraRepository.kt           # Camera management abstraction
    ├── SettingsActivity.kt           # Camera settings and preferences
    └── GalleryActivity.kt            # Photo gallery and management
```

### Layout Files
```
app/src/main/res/layout/
├── activity_main.xml                 # Simple launcher with camera button
├── activity_camera_selection.xml     # Camera detection and selection UI
├── activity_camera.xml              # Modern floating camera interface
└── [Future layouts]
    ├── activity_settings.xml         # Camera settings screen
    └── item_camera_option.xml        # Camera selection list item
```

### Resources
```
app/src/main/res/
├── drawable/                         # UI graphics and icons
├── values/
│   ├── strings.xml                   # App text resources
│   ├── colors.xml                    # Material3 color scheme
│   └── themes.xml                    # Material3 theme configuration
└── mipmap-*/                         # App launcher icons
```

## Features Implemented ✅

### Camera Core
- [x] Camera detection and enumeration
- [x] Permission handling with modern Activity Result API
- [x] CameraX integration with lifecycle management
- [x] Photo capture with timestamp naming
- [x] Camera switching between available cameras
- [x] Flash control with state management

### UI/UX
- [x] Material3 theme integration
- [x] Samsung/Google-style floating UI design
- [x] Fullscreen immersive camera experience
- [x] Smooth button animations (scale, rotation)
- [x] Auto-selection of first camera for better UX
- [x] Modern Kotlin with ViewBinding

### Error Handling
- [x] Graceful permission denial handling
- [x] Camera provider initialization error handling
- [x] Camera binding failure recovery
- [x] No cameras available scenario
- [x] Comprehensive logging for debugging

## Features To Implement 🚧

### Camera Functionality
- [ ] **Fix Camera ID Selection** (HIGH PRIORITY)
  - Ensure selected camera index is properly respected
  - Debug why camera filter isn't working as expected
  - Consider alternative camera selection approaches

- [ ] **Video Recording**
  - Add video capture capability with UI toggle
  - Video quality selection
  - Recording duration indicator

- [ ] **Advanced Camera Controls**
  - Manual focus control (tap to focus)
  - Zoom gestures (pinch to zoom)
  - Exposure compensation
  - White balance control

### UI Enhancements
- [ ] **Camera Selection UI Polish**
  - Add camera preview thumbnails to selection buttons
  - Better visual indication of selected camera
  - Smooth transitions between selection and camera

- [ ] **Settings Screen**
  - Camera resolution options
  - Photo quality settings
  - Timer functionality
  - Grid overlay toggle

- [ ] **Gallery Integration**
  - In-app photo gallery
  - Last photo preview in camera interface
  - Photo sharing capabilities
  - Photo metadata display

### Advanced Features
- [ ] **Night Mode**
  - Low-light optimization
  - Extended exposure for better night photos
  - Night mode UI indicators

- [ ] **Portrait Mode**
  - Depth-based background blur
  - Portrait lighting effects
  - Bokeh intensity control

- [ ] **Pro Mode**
  - Manual camera controls (ISO, shutter speed)
  - Histogram display
  - RAW photo capture option

## Key Functions Reference

### CameraActivity.kt
- `onCreate()`: Activity initialization and layout setup
- `startCamera()`: Camera provider initialization
- `bindCameraUseCases()`: Preview and capture use case binding
- `selectCamera()`: Camera selection logic with fallback ⚠️ **NEEDS FIX**
- `createCameraSelectorForIndex()`: Specific camera selector creation
- `capturePhoto()`: Photo capture with file handling
- `switchCamera()`: Runtime camera switching
- `toggleFlash()`: Flash control with state management
- `handleCameraError()`: Error recovery and user feedback

### CameraSelectionActivity.kt
- `detectAvailableCameras()`: Camera enumeration and validation
- `setupCameraButtons()`: Dynamic UI creation for camera options
- `createCameraButton()`: Individual camera selection button
- `updateButtonSelection()`: Visual selection state management

### Critical Data Flow
```
MainActivity → CameraSelectionActivity → CameraActivity
     ↓              ↓                        ↓
Launch camera → Select camera index → Use selected camera
                     ↓                        ↑
               Pass via Intent extras ─────────┘
               Key: EXTRA_CAMERA_INDEX (Int)
```

## Development Workflow

### Session Startup
1. `cd ~/git/swype/CustomCamera`
2. Check current status: `git status`
3. Review active issues in this CLAUDE.md
4. Focus on highest priority items first

### Testing Workflow
1. Make code changes
2. `./gradlew assembleDebug`
3. `adb install -r app/build/outputs/apk/debug/app-debug.apk`
4. Test on device
5. `adb logcat -d | grep "customcamera"` for debugging

### Debugging Camera Issues
1. Check logs for camera enumeration: "Available cameras: X"
2. Verify intent passing: "Intent extra value: X"
3. Trace camera selector creation: "Creating camera selector for index X"
4. Confirm camera binding: "Camera bound successfully"

## Known Working Components
- ✅ Project builds successfully with Kotlin + CameraX
- ✅ Camera permission handling works correctly
- ✅ Camera enumeration detects all available cameras
- ✅ UI layouts inflate without theme conflicts
- ✅ Basic camera preview and capture functionality
- ✅ Floating UI design matches modern camera apps
- ✅ Error handling prevents crashes

## ✅ CURRENT SESSION COMPLETED (2025-09-20)

### ✅ Critical Issues Resolved
1. **✅ Settings Screen Crashes Fixed** - Added missing `openFullSettings()` function
2. **✅ Plugin UI Integration Complete** - All plugin buttons visible and functional
3. **✅ Plugin Management System Working** - Browser, import, export fully implemented
4. **✅ Build Success** - Clean compilation with 26MB APK ready

### ✅ Technical Achievements
- **UI Button Integration**: All camera interface buttons properly wired to handlers
- **Settings Navigation**: Long-press settings button opens comprehensive SettingsActivity
- **Plugin Controls**: Grid, barcode, manual controls buttons visible and functional
- **Error Handling**: Comprehensive fallback mechanisms for settings failures
- **Code Quality**: Clean build with proper exception handling

## ✅ PHASE 8C COMPLETED: Custom Pre-Shot Crop System

### ✅ Implementation Complete
- **CropPlugin Integration**: Fully integrated with CameraActivityEngine
- **UI Controls**: Quadruple tap gesture to toggle crop mode
- **Interactive Crop**: Drag to adjust crop area with visual overlay
- **Aspect Ratios**: Support for Free, 1:1, 4:3, 3:2, 16:9, 9:16 ratios
- **Settings Persistence**: Crop preferences saved across sessions

### User Guide
**How to use Crop Mode:**
1. **Enable**: Tap camera preview 4 times quickly
2. **Adjust**: Drag crop overlay to resize and position
3. **Disable**: Tap camera preview 4 times again
4. **Capture**: Take photos with crop area applied

**Gesture Controls:**
- **Double Tap**: Toggle grid overlay
- **Triple Tap**: Toggle barcode scanning
- **Quadruple Tap**: Toggle crop mode

## Next Session Priorities
1. **Choose Phase 8 Next**:
   - **Option D**: Night Mode with Long Exposure
   - **Option E**: Advanced UI Polish and Performance Optimization
   - **Option F**: Advanced Video Recording Enhancements
2. **Device Testing** - Test crop and settings fixes on physical device
3. **Performance Optimization** - Monitor plugin system efficiency
4. **Advanced Features** - Continue roadmap implementation

## Camera Selection Debug Strategy
The current issue appears to be that the camera selector filter isn't properly constraining to the selected camera. Consider these approaches:
1. Test with different devices to see if it's device-specific
2. Try binding cameras one at a time to isolate the working one
3. Use CameraCharacteristics for more direct camera control
4. Implement camera testing during selection phase

## Session Workflow

### Before Each Session
1. **Check Master Task List**: `cat memory/todo.md` - Review critical issues and priorities
2. **Review Current Status**: Check git log and current app state
3. **Focus on P0 Issues**: Always tackle critical blockers first

### During Development
1. **Update Progress**: Mark completed tasks in `memory/todo.md`
2. **Document Findings**: Add new issues/tasks as discovered
3. **Test Frequently**: Build, install, and test changes immediately

### Session End
1. **Update todo.md**: Mark progress and add new tasks
2. **Commit Changes**: Descriptive commit messages
3. **Update Documentation**: Refresh this CLAUDE.md if needed

### Emergency Session Recovery
If lost or confused, run:
```bash
cd ~/git/swype/CustomCamera
cat CLAUDE.md && echo "====" && cat memory/todo.md | head -50
```

---
*Last Updated: 2025-09-14*
*Next Session: Focus on camera ID selection fix in CameraActivity.kt*
*Master Task List: memory/todo.md (ALWAYS CHECK FIRST)*