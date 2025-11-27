# Device Testing Checklist - CameraX 1.5.0 Upgrade

**Date**: 2025-11-05
**Version**: Post-upgrade (AGP 8.6.0, Kotlin 2.1.20, CameraX 1.5.0)
**APK**: `app/build/outputs/apk/debug/app-debug.apk` (88MB)

---

## Installation Instructions

### Option 1: ADB Installation (Recommended)
```bash
# Connect device to computer via USB
adb devices

# Uninstall old version (if needed)
adb uninstall com.customcamera.app

# Install new APK
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Launch app
adb shell am start -n com.customcamera.app/.MainActivity
```

### Option 2: Manual Installation
1. Copy `app/build/outputs/apk/debug/app-debug.apk` to device storage
2. Use file manager to locate the APK
3. Tap to install (allow installation from unknown sources if prompted)
4. Launch CustomCamera app

### Option 3: Build Script (From Termux)
```bash
cd ~/git/swype/CustomCamera
./build-and-install.sh
```
Note: Requires `adb` or root access

---

## Pre-Testing Verification

### Build Information
- [ ] APK builds successfully without errors
- [ ] APK size reasonable (~88MB is normal with all dependencies)
- [ ] Version number updated in app (check About screen)
- [ ] Build date reflects upgrade date

### Installation
- [ ] App installs without errors
- [ ] No permission errors during installation
- [ ] App icon appears correctly in launcher
- [ ] Previous settings preserved (if reinstalling)

---

## Critical Path Testing (15 minutes)

### 1. App Launch & Permissions
- [ ] App launches without crashing
- [ ] Camera permission request displays
- [ ] Camera permission granted successfully
- [ ] Audio permission granted (for video recording)
- [ ] Storage permission granted (for saving media)

### 2. Basic Camera Functionality
- [ ] **Camera Preview**: Live preview displays correctly
- [ ] **Photo Capture**: Tap shutter button captures photo
- [ ] **Photo Saved**: Photo appears in gallery
- [ ] **Camera Switch**: Switch between available cameras works
- [ ] **Flash Toggle**: Flash modes cycle correctly (auto/on/off/torch)
- [ ] **Zoom**: Pinch-to-zoom works smoothly

### 3. Video Recording (CameraX 1.5.0 Specific)
- [ ] **Start Recording**: Red button starts video recording
- [ ] **Recording Indicator**: Timer shows elapsed time
- [ ] **Stop Recording**: Stop button saves video
- [ ] **Video Playback**: Video plays correctly in gallery
- [ ] **Audio Recording**: Video has audio track
- [ ] **Video Quality Settings Test** (CRITICAL FIX 6138da70):
  - [ ] Set video quality to 4K in settings → record video → check resolution is 3840x2160
  - [ ] Set to 1080p in settings → record video → check resolution is 1920x1080
  - [ ] Set to 720p in settings → record video → check resolution is 1280x720
  - [ ] Settings now actually control recording quality (was hardcoded to HIGHEST)

**Known Issue**: Frame rate configuration disabled (see Priority 3)
- Videos use default frame rates (typically 30fps)
- Variable frame rate features temporarily unavailable

---

## Plugin Testing (23 Plugins - 30 minutes)

### OVERLAYS (1 plugin)

#### GridOverlayPlugin
- [ ] Enable via dropdown menu
- [ ] Grid lines display on preview
- [ ] Cycle through grid types (rule of thirds, 4x4, golden ratio)
- [ ] Grid toggles off correctly

### ANALYSIS (7 plugins)

#### BarcodePlugin
- [ ] Enable via dropdown
- [ ] Point camera at QR code
- [ ] Barcode detected and highlighted
- [ ] Scan result displays

#### HistogramPlugin
- [ ] Enable via dropdown (or toggle in settings - fix 50a19ace)
- [ ] Histogram overlay appears
- [ ] Histogram updates in real-time
- [ ] Shows RGB channel distribution
- [ ] Toggle in settings correctly enables/disables overlay

#### CameraInfoPlugin
- [ ] Enable via dropdown
- [ ] Camera info overlay displays
- [ ] Shows camera ID, resolution, etc.
- [ ] Updates when switching cameras

#### ExposureAnalysisPlugin
- [ ] Enable via dropdown
- [ ] Exposure analysis displays
- [ ] Shows over/under exposed regions
- [ ] Updates with scene changes

#### MotionDetectionPlugin
- [ ] Enable via dropdown
- [ ] Move camera to detect motion
- [ ] Motion indicators appear
- [ ] Motion history tracked

#### QRScannerPlugin
- [ ] Enable via dropdown
- [ ] Scan QR code
- [ ] QR content displayed
- [ ] Auto-action triggers (if configured)

#### SharpnessAnalysisPlugin
- [ ] Enable via dropdown
- [ ] Sharpness overlay displays
- [ ] Focus areas highlighted
- [ ] Updates with focus changes

### CONTROLS (4 plugins)

#### AutoFocusPlugin
- [ ] Always active (no toggle needed)
- [ ] Tap preview to focus
- [ ] Focus indicator appears
- [ ] Focus locks correctly

#### ExposureControlPlugin
- [ ] Always active
- [ ] Exposure compensation slider works
- [ ] Brightness adjusts correctly
- [ ] Exposure lock functions

#### ManualFocusPlugin
- [ ] Enable in settings
- [ ] Manual focus slider appears
- [ ] Focus distance control works
- [ ] Focus value displays

#### ProControlsPlugin
- [ ] Enable in settings
- [ ] Professional controls panel appears
- [ ] ISO adjustment works
- [ ] Shutter speed control works
- [ ] Focus distance control works

### AI (3 plugins)

#### SmartScenePlugin
- [ ] Enable via dropdown
- [ ] Point at different scenes
- [ ] Scene detection works (landscape, portrait, food, etc.)
- [ ] Scene labels display correctly
- [ ] Google Play Services available (ML Kit dependency)

#### SmartAdjustmentsPlugin
- [ ] Enable via dropdown
- [ ] AI adjustments apply automatically
- [ ] Camera settings optimized per scene
- [ ] Adjustment indicators display

#### ObjectDetectionPlugin
- [ ] Enable via dropdown
- [ ] Point at objects
- [ ] Objects detected and labeled
- [ ] Bounding boxes displayed
- [ ] Google Play Services available

### CAPTURE (6 plugins)

#### CropPlugin
- [ ] Enable via dropdown
- [ ] Crop frame overlay appears
- [ ] Aspect ratio selection works
- [ ] Crop area adjustable
- [ ] Photo captures with crop applied

#### DualCameraPiPPlugin
- [ ] Toggle PiP button (dedicated UI button)
- [ ] Secondary camera preview appears
- [ ] Photo captures both views
- [ ] Composite image saved correctly
- [ ] Android 11+ required

#### RAWCapturePlugin
- [ ] Enable in settings (CRITICAL: now uses SettingsManager.getRawCapture() - fix 30fc278f)
- [ ] Capture photo
- [ ] DNG file created alongside JPEG
- [ ] Disable in settings
- [ ] Capture photo - verify NO DNG created
- [ ] DNG metadata preserved when enabled
- [ ] RAW capability detected (device-dependent)

#### AdvancedVideoRecordingPlugin
- [ ] Enable via dropdown
- [ ] Video quality selection works
- [ ] Bitrate control functions
- [ ] Video codec selection available
- [ ] Duration tracking accurate

#### NightModePlugin
- [ ] Toggle night mode button (dedicated UI button)
- [ ] Low-light enhancement activates
- [ ] Preview brightens in dark scenes
- [ ] Photos capture with night mode applied
- [ ] Longer exposure time used
- [ ] **CameraX 1.5.0**: Low-light boost enabled on Android 15+ (commit 3a480d20)

#### HDRPlugin
- [ ] Enable via dropdown
- [ ] HDR indicator displays
- [ ] Multiple exposures captured
- [ ] HDR merge creates balanced image
- [ ] Exposure compensation support detected

### DEBUG (1 plugin)

#### DiagnosticOverlayPlugin
- [ ] Enable via dropdown
- [ ] Diagnostic overlay appears
- [ ] Shows camera state info
- [ ] Displays sensor data
- [ ] Shows permissions status
- [ ] Event log updates

---

## Kotlin 2.1.20 Null-Safety Verification (10 minutes)

### Test Scenarios for Fixed Null-Safety Issues

#### MainActivity - ApplicationInfo Nullability
- [ ] Open **Settings** → **About**
- [ ] Verify "Target SDK" displays (or shows "N/A" if null)
- [ ] No crashes when viewing app info
- [ ] Version name displays correctly (or "unknown" if null)

#### AI Managers - Bitmap.config Nullability
**Test all AI features that process bitmaps:**

##### AIBackgroundBlurManager
- [ ] Enable background blur (if available)
- [ ] Capture photo with blur
- [ ] No crashes during bitmap processing
- [ ] Blurred image saved correctly

##### AIFaceDetectionManager
- [ ] Enable face detection (if available)
- [ ] Point camera at faces
- [ ] Face detection works without crashes
- [ ] Bitmap copy operations succeed

##### AIImageProcessingManager
- [ ] Capture photo with any AI enhancement
- [ ] Image processing completes without crashes
- [ ] Enhanced image saved correctly
- [ ] All bitmap operations handle null config

##### AITextRecognitionManager
- [ ] Enable text recognition (if available)
- [ ] Point camera at text
- [ ] Text detection works without crashes
- [ ] OCR processing completes

**Expected Behavior**: All bitmap operations should use `Bitmap.Config.ARGB_8888` as fallback when `bitmap.config` is null. No `NullPointerException` should occur.

---

## Performance Testing (10 minutes)

### Camera Preview Performance
- [ ] Preview framerate smooth (no stuttering)
- [ ] Preview latency acceptable (<100ms)
- [ ] No memory leaks during extended use
- [ ] App responsive to touch input

### Photo Capture Performance
- [ ] Capture time reasonable (<500ms)
- [ ] No lag between captures
- [ ] Image processing completes quickly
- [ ] Gallery updates promptly

### Video Recording Performance
- [ ] Recording starts immediately
- [ ] No dropped frames during recording
- [ ] File writing doesn't block UI
- [ ] Recording stops cleanly

### Memory Performance
- [ ] App uses reasonable memory (<500MB)
- [ ] No memory warnings during use
- [ ] No ANR (Application Not Responding) dialogs
- [ ] Clean app restart without issues

---

## Regression Testing (15 minutes)

### Previously Fixed Issues
- [ ] **Camera lifecycle**: All cameras open successfully
- [ ] **PiP black camera**: PERFORMANCE mode works
- [ ] **Bixby Vision**: OIS libraries accessible
- [ ] **Status bar**: Immersive mode works on Android 11+
- [ ] **Settings persistence**: All settings save/restore correctly
- [ ] **Plugin states**: Plugin enable/disable persists across restarts

### Multi-Camera Testing
- [ ] Camera 0 (back wide) works
- [ ] Camera 1 (front) works
- [ ] Camera 2 (back ultra-wide) works
- [ ] Camera 3 (back telephoto) works
- [ ] All cameras switch smoothly
- [ ] No camera enumeration errors

### Settings Testing
- [ ] **Photo Quality**: Slider saves value
- [ ] **Video Quality**: Dropdown persists selection **AND ACTUALLY AFFECTS RECORDINGS** (critical fix 6138da70)
- [ ] **Flash Mode**: Mode saved across launches
- [ ] **Grid Type**: Grid selection persists
- [ ] **Plugin States**: Enabled/disabled states persist
- [ ] **Advanced Settings**: Debug logging, RAW capture, etc. persist
- [ ] **RAW Capture**: Toggle actually enables/disables DNG files (critical fix 30fc278f)
- [ ] **Histogram Overlay**: Toggle in settings controls histogram display (fix 50a19ace)
- [ ] **Settings Singleton**: Same values across all activities (architecture fix e469c353)

---

## Edge Cases & Stress Testing (15 minutes)

### Boundary Conditions
- [ ] **Maximum Zoom**: Zoom to max without crashes
- [ ] **Rapid Captures**: Take 10+ photos quickly
- [ ] **Long Recording**: Record 5+ minute video
- [ ] **Low Storage**: Test with <100MB storage remaining
- [ ] **Airplane Mode**: App functions without network
- [ ] **Low Battery**: Test with battery saver mode

### Error Handling
- [ ] **Permissions Denied**: App handles gracefully
- [ ] **Camera in Use**: Error when another app has camera
- [ ] **Storage Full**: Clear error message
- [ ] **Corrupted Settings**: App recovers from bad prefs
- [ ] **Plugin Errors**: Individual plugin failures don't crash app

### Android Version Compatibility
Test on multiple Android versions if available:
- [ ] **Android 15 (API 35)**: Full functionality
- [ ] **Android 14 (API 34)**: Compatible
- [ ] **Android 13 (API 33)**: Compatible
- [ ] **Android 11 (API 30)**: PiP mode works
- [ ] **Android 10 (API 29)**: Basic features work
- [ ] **Android 7 (API 24)**: Minimum SDK test

---

## Known Issues to Monitor

### Frame Rate Configuration (Priority 3)
- **Status**: Documented, not implemented
- **Impact**: Videos use default frame rates only
- **Workaround**: None required (default rates acceptable)
- **Testing**: Verify videos record at ~30fps
- **Future**: Implement SessionConfig.setFrameRateRange()

### CameraX 1.5.0 Compatibility
- **Monitor**: New APIs may have device-specific quirks
- **Check**: Preview stabilization behavior
- **Check**: Low-light boost availability (if implemented)
- **Check**: Concurrent camera streams (PiP mode)

### ML Kit Dependencies
- **Requirement**: Google Play Services installed
- **Fallback**: Plugins gracefully disable if GMS unavailable
- **Affected**: SmartScenePlugin, ObjectDetectionPlugin, BarcodePlugin, QRScannerPlugin

---

## Test Results Template

### Environment
- **Device**: [Model name]
- **Android Version**: [e.g., Android 14]
- **API Level**: [e.g., 34]
- **Build Number**: [Device build]
- **Test Date**: [Date]
- **Tester**: [Name]

### Overall Results
- **Critical Path**: [PASS / FAIL]
- **Plugin Testing**: [X/23 plugins working]
- **Performance**: [PASS / FAIL]
- **Regressions**: [None / List issues]

### Issues Found
1. **[Issue Title]**
   - Severity: [Critical / High / Medium / Low]
   - Steps to reproduce: [...]
   - Expected: [...]
   - Actual: [...]
   - Logs: [...]

2. **[Issue Title]**
   - ...

### Recommendations
- [ ] Ready for release
- [ ] Needs fixes before release
- [ ] Additional testing required

---

## Automated Testing Supplement

While manual testing is comprehensive, also run:

```bash
# Unit tests (GitHub CI/CD with x86_64 runner)
./gradlew test

# Build verification
./gradlew assembleDebug

# Lint checks
./gradlew lint
```

---

## Sign-Off

- [ ] All critical path tests passed
- [ ] No regressions from previous version
- [ ] Performance acceptable
- [ ] Known issues documented
- [ ] Ready for next development phase

**Tested By**: ________________
**Date**: ________________
**Signature**: ________________

---

**Last Updated**: 2025-11-05
**Upgrade Version**: CameraX 1.5.0, AGP 8.6.0, Kotlin 2.1.20
**Total Test Time**: ~90 minutes (comprehensive)
