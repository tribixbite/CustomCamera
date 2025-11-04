# CustomCamera Manual Testing Guide

**Date:** 2025-10-21
**APK Version:** v2.1.22-build.32
**Purpose:** Comprehensive functional verification of all activities, settings, and features

## Prerequisites

- ✅ App installed: `adb install -r app/build/outputs/apk/debug/app-debug.apk`
- ✅ Camera permission granted
- ✅ Storage permissions granted:
  ```bash
  adb shell pm grant com.customcamera.app android.permission.READ_EXTERNAL_STORAGE
  adb shell pm grant com.customcamera.app android.permission.WRITE_EXTERNAL_STORAGE
  ```
- ✅ Audio permission granted (for video recording)

## Known Limitations

### ADB Automation Challenges
- **Focus Stealing:** Termux steals focus when running ADB commands, preventing reliable automated tapping
- **Touch Events:** Some UI elements don't respond to `adb shell input tap` consistently
- **Recommendation:** Manual testing is more reliable for comprehensive verification

### Photo Storage Location
- Photos are saved to `/data/data/com.customcamera.app/files/` (internal app storage)
- NOT saved to `/sdcard/DCIM/CustomCamera/`
- Use `adb shell run-as com.customcamera.app ls files/` to list captured photos

## Test Plan

### 1. MainActivity (Launcher Activity)

**Test:** App Launch
1. From device home screen, tap CustomCamera icon
2. Verify MainActivity welcome screen appears
3. Verify "Select Camera" button is visible and clickable

**Expected Result:**
- MainActivity displays with branding/welcome message
- Navigation to CameraSelectionActivity works

**ADB Command to Launch:**
```bash
adb shell am start -n com.customcamera.app/.MainActivity
```

---

### 2. CameraSelectionActivity (Camera Picker)

**Test:** Camera Selection UI
1. From MainActivity, tap "Select Camera"
2. Verify all available cameras are listed (device has 4 cameras)
3. Select each camera and verify preview works
4. Confirm selection navigates to CameraActivityEngine

**Expected Result:**
- All 4 cameras listed with proper labels
- Camera preview works for each selection
- Selected camera index passed to CameraActivityEngine

**ADB Command:**
```bash
# Cannot launch directly (not exported)
# Must navigate from MainActivity
```

---

### 3. CameraActivityEngine (Main Camera with Plugins)

**Test:** Core Camera Functionality

#### 3a. Photo Capture
1. Tap large circular capture button (center-bottom)
2. Verify shutter animation plays
3. Check internal storage for photo file

**Verification:**
```bash
adb shell run-as com.customcamera.app ls -lt files/ | grep CAMERA_ENGINE
```

**Expected:** Files named `CAMERA_ENGINE_YYYYMMDD_HHmmss.jpg`

#### 3b. Camera Switching
1. Tap switch camera button (bottom-right area)
2. Verify camera switches between available cameras
3. Check preview updates correctly

#### 3c. Flash Control
1. Tap flash button (top row)
2. Cycle through: Auto → On → Off → Torch
3. Verify flash state indicator updates

#### 3d. Night Mode
1. Tap night mode button (top row)
2. Verify UI indicates night mode enabled
3. Capture photo in night mode
4. Verify long exposure processing occurs

#### 3e. Picture-in-Picture (PiP)
1. Tap PiP button (top row)
2. Verify dual camera mode activates
3. Verify secondary camera preview appears in corner
4. Capture photo with both cameras
5. Tap PiP button again to disable

#### 3f. Video Recording
1. Tap video record button (top row)
2. Verify recording indicator appears
3. Record for 5-10 seconds
4. Tap again to stop recording
5. Verify video file created

---

### 4. Plugin Dropdown Menu

**Test:** Plugin Accessibility

#### UI Element Locations (from UI Dump)
- **Master Plugin Button:** bounds="[912,1149][1038,1275]" (right side, mid-screen)
- **Settings Button:** bounds="[750,169][897,316]" (top row, right side)

#### 4a. Plugin Dropdown Toggle
1. Tap master plugin button (circular button with plugin icon)
2. Verify dropdown menu appears
3. Scroll through plugin list
4. Verify all 15 dropdown plugins visible:
   - GridOverlay
   - Barcode
   - Histogram
   - CameraInfo
   - ExposureAnalysis
   - MotionDetection
   - QRScanner
   - SharpnessAnalysis
   - SmartScene
   - SmartAdjustments
   - ObjectDetection
   - Crop
   - RAWCapture
   - AdvancedVideoRecording
   - HDR

#### 4b. DiagnosticOverlay Plugin (Priority Test)
1. Open plugin dropdown
2. Find "DiagnosticOverlay" (under DEBUG category)
3. Toggle it ON
4. Verify overlay appears showing:
   - Camera state
   - Active sensors
   - Permissions status
   - Event log
5. Toggle OFF
6. Verify overlay disappears

**Expected:** Plugin toggle persists across app restarts

---

### 5. Settings Screens

#### 5a. SimpleSettingsActivity (Quick Settings)
1. Tap settings button (top row, gear icon)
2. Verify simple settings screen opens

**Settings to Test:**
- [ ] Debug Logging toggle
- [ ] Performance Monitoring toggle
- [ ] RAW Capture toggle
- [ ] Manual Controls toggle
- [ ] Flash Mode dropdown (Auto/On/Off/Torch)
- [ ] Grid Overlay Default toggle
- [ ] Level Indicator toggle

**Verification:** Toggle each setting, restart app, verify persistence

#### 5b. SettingsActivity (Full Settings)
Access via app menu or ADB:
```bash
adb shell am start -n com.customcamera.app/.SettingsActivity
```

**Test Each Category:**

**Camera Selection (2 settings)**
- [ ] Main Camera Selection
- [ ] PiP Camera Selection

**Photo Settings (4 settings)**
- [ ] Photo Quality slider (0-100)
- [ ] Photo Resolution dropdown
- [ ] Grid Overlay Default switch
- [ ] Flash Mode dropdown

**Video Settings (2 settings)**
- [ ] Video Quality dropdown (SD/HD/FHD/UHD)
- [ ] Video Stabilization switch

**Focus Settings (2 settings)**
- [ ] Auto Focus Mode dropdown
- [ ] Tap to Focus switch

**Grid & Overlays (4 settings)**
- [ ] Grid Type dropdown (None/Rule of Thirds/3x3/Golden Ratio)
- [ ] Camera Info Overlay switch
- [ ] Histogram Overlay switch
- [ ] Level Indicator switch

**Manual Controls (3 settings)**
- [ ] Manual Controls switch
- [ ] Default Exposure slider
- [ ] Exposure Lock switch

**Advanced Settings (4 settings)**
- [ ] Debug Logging switch
- [ ] Performance Monitoring switch
- [ ] Processing Interval slider
- [ ] RAW Capture switch

---

### 6. GalleryActivity

**Test:** Gallery Access
1. From camera screen, tap gallery button (bottom-left)
2. Verify gallery opens
3. Verify photos display correctly
4. Tap photo to view full-screen
5. Test delete/share functions

**ADB Launch:**
```bash
adb shell am start -n com.customcamera.app/.GalleryActivity
```

---

### 7. DebugActivity

**Test:** Debug Information Display
1. Launch via ADB:
```bash
adb shell am start -n com.customcamera.app/.DebugActivity
```
2. Verify debug info displays:
   - System information
   - Camera capabilities
   - Sensor details
   - Permissions status
   - API call log

---

### 8. Gesture Controls

**Multi-Tap Gestures** (tap preview area repeatedly):
- 2× tap → Grid overlay toggle
- 3× tap → Barcode scanning
- 4× tap → Pre-shot crop
- 5× tap → Smart scene detection
- 6× tap → Gesture hints overlay
- 7× tap → Demo showcase mode

**Other Gestures:**
- Pinch → Zoom control
- Long-press preview → AI features status

**Verify:** Each gesture triggers expected function with haptic feedback

---

### 9. Plugin-Specific Tests

#### GridOverlayPlugin
1. Enable via plugin dropdown or 2× tap
2. Verify grid lines appear on preview
3. Cycle through grid types in settings

#### BarcodePlugin
1. Enable via plugin dropdown or 3× tap
2. Point at QR code or barcode
3. Verify detection toast appears
4. Verify scan result displayed

#### HistogramPlugin
1. Enable via plugin dropdown
2. Verify histogram overlay appears
3. Move camera to different scenes
4. Verify histogram updates in real-time

#### MotionDetectionPlugin
1. Enable via plugin dropdown
2. Move camera
3. Verify motion detection logs appear
4. Verify continuous monitoring works

#### CropPlugin
1. Enable via plugin dropdown or 4× tap
2. Verify crop frame appears on preview
3. Adjust crop frame
4. Capture photo
5. Verify photo cropped to frame

#### DualCameraPiPPlugin
1. Enable via PiP button (dedicated button)
2. Verify secondary camera preview in corner
3. Verify both camera feeds active
4. Capture photo
5. Verify photo composites both camera views

---

### 10. Settings Persistence Tests

**Procedure:**
1. Change a setting (e.g., enable RAW Capture)
2. Verify setting takes effect
3. Force-close app: `adb shell am force-stop com.customcamera.app`
4. Relaunch app
5. Verify setting persisted

**Settings to Verify:**
- [ ] Debug Logging
- [ ] RAW Capture
- [ ] Manual Controls
- [ ] Photo Quality
- [ ] Video Stabilization
- [ ] Grid Type
- [ ] Flash Mode
- [ ] All plugin enable/disable states

---

### 11. Crash & Stability Tests

#### 11a. Rapid Camera Switching
1. Rapidly tap switch camera button 20 times
2. Verify no crashes or ANRs
3. Verify camera remains responsive

#### 11b. Rapid Plugin Toggling
1. Open plugin dropdown
2. Toggle 10 different plugins rapidly
3. Verify no crashes
4. Verify plugins respond correctly

#### 11c. Activity Lifecycle
1. Launch camera
2. Press home button (send to background)
3. Wait 30 seconds
4. Return to app
5. Verify camera resumes correctly
6. Verify plugins still work

#### 11d. Low Memory Scenario
1. Launch camera
2. Open several heavy apps (Chrome, etc.)
3. Return to camera
4. Verify app didn't crash
5. Verify camera still functional

---

### 12. Performance Tests

#### 12a. Preview Frame Rate
1. Enable Performance Monitor in settings
2. Verify FPS counter displays
3. Check FPS stays at ~30fps or higher
4. Move camera around
5. Verify no significant frame drops

#### 12b. Photo Capture Speed
1. Tap capture button
2. Measure time from tap to shutter
3. Verify < 500ms capture latency
4. Capture 10 photos rapidly
5. Verify no lag or slowdown

#### 12c. Plugin Processing Performance
1. Enable multiple analysis plugins:
   - Histogram
   - MotionDetection
   - SmartScene
   - ObjectDetection
2. Verify preview remains smooth
3. Check FPS doesn't drop below 20fps

---

## Test Results Template

### Activity Tests
- [ ] MainActivity launches correctly
- [ ] CameraSelectionActivity shows all cameras
- [ ] CameraActivityEngine initializes without errors
- [ ] SimpleSettingsActivity accessible
- [ ] SettingsActivity accessible
- [ ] GalleryActivity accessible
- [ ] DebugActivity accessible

### Core Functions
- [ ] Photo capture works
- [ ] Video recording works
- [ ] Camera switching works
- [ ] Flash control works
- [ ] Night mode works
- [ ] PiP mode works
- [ ] Zoom works (pinch gesture)

### Plugin System
- [ ] Plugin dropdown accessible
- [ ] DiagnosticOverlay works (HIGH PRIORITY)
- [ ] GridOverlay works
- [ ] BarcodePlugin works
- [ ] HistogramPlugin works
- [ ] MotionDetection works
- [ ] CropPlugin works
- [ ] All 23 plugins load without errors

### Settings
- [ ] All settings accessible
- [ ] Settings changes take effect
- [ ] Settings persist across restarts
- [ ] Settings export/import works

### Stability
- [ ] No crashes during normal use
- [ ] No ANRs (App Not Responding)
- [ ] App survives background/foreground
- [ ] App survives low memory scenarios

---

## Reporting Issues

If any test fails:
1. Note exact steps to reproduce
2. Capture logcat: `adb logcat -d > issue_log.txt`
3. Take screenshots if applicable
4. Note APK version and device info

**Check Logs:**
```bash
# Camera engine logs
adb logcat -d | grep "CameraEngine\|CameraActivityEngine"

# Plugin logs
adb logcat -d | grep "Plugin"

# Errors
adb logcat -d | grep -i "error\|exception\|fatal"

# Crashes
adb logcat -d | grep "AndroidRuntime"
```

---

## Automated Testing Supplement

While manual testing is primary, the automated script can verify basic functionality:

```bash
./test-all-activities.sh
```

This script automatically checks:
- App installation
- Activity launches
- Permission status
- Plugin system active
- Basic crash detection

**Note:** Photo capture automation is unreliable due to ADB focus issues. Test manually.

---

**Last Updated:** 2025-10-21
**Status:** Ready for comprehensive manual testing
**Priority:** DiagnosticOverlay plugin verification (build 32 feature)
