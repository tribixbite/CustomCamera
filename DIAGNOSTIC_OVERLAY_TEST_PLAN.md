# DiagnosticOverlay Integration Test Plan

**Priority**: HIGH  
**Status**: READY FOR TESTING  
**Version**: v2.1.41-build.33  
**Estimated Time**: 15-20 minutes

---

## Overview

Test the DiagnosticOverlay plugin integration to verify it displays camera state, sensor info, permissions, and event logs correctly via the plugin dropdown menu.

---

## Prerequisites

1. Device connected via ADB
2. APK v2.1.41-build.33 installed
3. Camera permissions granted
4. Multiple cameras available (for state testing)

---

## Test Cases

### Test 1: Plugin Dropdown Access

**Objective**: Verify DiagnosticOverlay appears in plugin dropdown

**Steps**:
1. Launch camera: `adb shell am start -a com.customcamera.app.TEST_CAMERA`
2. Locate master plugin button (puzzle piece icon in UI)
3. Tap master plugin button to open dropdown
4. Scroll through plugin list
5. Locate "Diagnostic Overlay" or "Debug" entry

**Expected Results**:
- ✅ Plugin dropdown opens smoothly
- ✅ DiagnosticOverlay listed in dropdown
- ✅ Toggle button visible next to entry
- ✅ Entry shows current state (ON/OFF)

**Screenshot**: `test_plugin_dropdown.png`

---

### Test 2: Enable DiagnosticOverlay

**Objective**: Verify overlay activates and displays information

**Steps**:
1. With plugin dropdown open, tap DiagnosticOverlay toggle
2. Close dropdown
3. Observe overlay appearance on screen
4. Capture screenshot: `adb exec-out screencap -p > test_overlay_enabled.png`

**Expected Results**:
- ✅ Overlay appears in semi-transparent panel
- ✅ Positioned in corner/edge (not blocking camera preview)
- ✅ Shows diagnostic information sections
- ✅ Text is readable (proper contrast)
- ✅ Doesn't interfere with camera controls

**Screenshot**: `test_overlay_enabled.png`

---

### Test 3: Camera State Display

**Objective**: Verify overlay shows accurate camera state information

**Information to Verify**:
```
Camera State Section:
- Camera ID: [current camera index]
- Camera State: OPEN/CLOSED/OPENING/CLOSING
- Preview Mode: Single/Concurrent
- UseCase Count: [number]
```

**Steps**:
1. With overlay enabled, observe "Camera State" section
2. Note current camera ID and state
3. Switch to different camera
4. Verify camera ID updates
5. Verify state transitions show (CLOSED → OPENING → OPEN)

**Expected Results**:
- ✅ Camera ID matches current camera
- ✅ Camera State shows OPEN when preview visible
- ✅ State updates in real-time during camera switch
- ✅ Preview Mode shows "Single" (when not in PiP)

**Screenshot**: `test_overlay_camera_state.png`

---

### Test 4: Sensor Information Display

**Objective**: Verify overlay shows device sensor capabilities

**Information to Verify**:
```
Sensor Info Section:
- Gyroscope: Available/Not Available
- Accelerometer: Available/Not Available
- Magnetometer: Available/Not Available
- High Sampling Rate: Supported/Not Supported
```

**Steps**:
1. With overlay enabled, locate "Sensor Info" section
2. Verify sensor availability matches device capabilities
3. Cross-reference with Settings → About Phone → Sensors (if available)

**Expected Results**:
- ✅ Sensor info section visible
- ✅ At least Gyroscope and Accelerometer shown
- ✅ Availability status accurate
- ✅ High sampling rate indicator present

**Screenshot**: `test_overlay_sensors.png`

---

### Test 5: Permissions Display

**Objective**: Verify overlay shows current permission states

**Information to Verify**:
```
Permissions Section:
- Camera: GRANTED
- Record Audio: GRANTED/DENIED
- Vibrate: GRANTED
```

**Steps**:
1. With overlay enabled, locate "Permissions" section
2. Verify camera permission shows GRANTED (required for app to run)
3. Note audio permission state
4. If audio denied, grant permission and verify update

**Expected Results**:
- ✅ Permissions section visible
- ✅ Camera permission shows GRANTED
- ✅ Audio permission shows current state
- ✅ Vibrate permission shows current state
- ✅ Updates when permissions change

**Screenshot**: `test_overlay_permissions.png`

---

### Test 6: Event Log Functionality

**Objective**: Verify overlay logs camera lifecycle events

**Events to Verify**:
```
Recent events might include:
- Camera bound successfully
- Camera state: OPEN
- Preview started
- Plugin enabled: [plugin name]
- Photo captured
- Camera switched
```

**Steps**:
1. With overlay enabled, locate "Event Log" or "Recent Events" section
2. Note current events
3. Perform actions:
   - Switch camera
   - Capture photo
   - Enable/disable plugin
4. Verify new events appear in log
5. Check log scrolls/truncates appropriately

**Expected Results**:
- ✅ Event log section visible
- ✅ Recent events listed (5-10 entries)
- ✅ Events show timestamps or sequence
- ✅ New events appear when actions performed
- ✅ Old events scroll off or truncate gracefully

**Screenshot**: `test_overlay_events.png`

---

### Test 7: PiP Mode Compatibility

**Objective**: Verify overlay works correctly in PiP mode

**Steps**:
1. Enable DiagnosticOverlay
2. Enable PiP mode via dedicated button
3. Verify overlay updates to show concurrent mode
4. Capture screenshot: `adb exec-out screencap -p > test_overlay_pip.png`
5. Check logs: `adb logcat -d | grep "Diagnostic\|PiP"`

**Expected Results**:
- ✅ Overlay remains visible in PiP mode
- ✅ Preview Mode updates to "Concurrent"
- ✅ Camera State shows multiple camera IDs
- ✅ Overlay doesn't block PiP window
- ✅ Event log shows "PiP enabled" event

**Screenshot**: `test_overlay_pip.png`

---

### Test 8: Toggle Off DiagnosticOverlay

**Objective**: Verify overlay can be disabled cleanly

**Steps**:
1. With overlay enabled, open plugin dropdown
2. Tap DiagnosticOverlay toggle to OFF
3. Close dropdown
4. Verify overlay disappears
5. Re-enable overlay
6. Verify state persists (same events, same position)

**Expected Results**:
- ✅ Overlay disappears immediately when toggled off
- ✅ Camera preview unobstructed
- ✅ Re-enabling shows overlay again
- ✅ State persists (events not lost)

**Screenshot**: `test_overlay_disabled.png`

---

### Test 9: Performance Impact

**Objective**: Verify overlay doesn't degrade camera performance

**Steps**:
1. Without overlay: Note camera preview smoothness
2. Enable overlay
3. Record video for 10 seconds
4. Check for frame drops, stuttering, or lag
5. Review logs for performance warnings

**Expected Results**:
- ✅ Camera preview remains smooth (60fps target)
- ✅ No visible stuttering or lag
- ✅ Video recording works normally
- ✅ No performance warnings in logs

**Commands**:
```bash
# Check for frame drops
adb logcat -d | grep -i "frame\|drop\|lag"

# Monitor FPS (if performance monitor plugin available)
# Should show ~60fps consistently
```

---

### Test 10: Overlay Positioning

**Objective**: Verify overlay doesn't block critical UI elements

**Elements to Check**:
- ✅ Capture button (bottom center) - NOT BLOCKED
- ✅ Gallery button (bottom left) - NOT BLOCKED
- ✅ Switch camera button (bottom right) - NOT BLOCKED
- ✅ Flash button (top left) - NOT BLOCKED
- ✅ Night mode button (top) - NOT BLOCKED
- ✅ PiP button (top) - NOT BLOCKED
- ✅ Settings button (top right) - NOT BLOCKED
- ✅ Plugin dropdown button - NOT BLOCKED
- ✅ Manual Controls panel - NOT BLOCKED

**Steps**:
1. Enable overlay
2. Try accessing each UI element listed above
3. Verify all buttons remain tappable
4. Verify overlay doesn't interfere with gestures (pinch zoom, multi-tap)

**Expected Results**:
- ✅ All buttons accessible
- ✅ Overlay positioned in non-critical area
- ✅ Gestures work normally
- ✅ Overlay semi-transparent for visibility

---

## Regression Checks

After DiagnosticOverlay testing, verify previous fixes remain working:

1. **Material 3 Controls**
   - Purple active buttons, gray inactive
   - Proper spacing and elevation
   - Screenshot: `regression_material3.png`

2. **PiP Mode**
   - Both cameras rendering (no black screens)
   - Screenshot: `regression_pip.png`

3. **Manual Controls Overlap**
   - 280dp bottom margin maintained
   - All bottom buttons visible
   - Screenshot: `regression_overlap.png`

---

## Expected Issues (If Any)

Based on integration, potential issues might be:

1. **Overlay position blocking UI**
   - Fix: Adjust overlay position in DiagnosticOverlayPlugin.kt
   - Target: Top-right corner or top-left with proper margins

2. **Event log truncation**
   - Fix: Implement circular buffer with max 10 events
   - Target: Prevent memory growth

3. **State not persisting**
   - Fix: Use SettingsManager to save overlay enabled state
   - Target: Overlay state survives app restart

---

## Success Criteria

**PASS Requirements**:
- ✅ All 10 test cases pass
- ✅ No critical UI elements blocked
- ✅ No performance degradation
- ✅ Regression checks all pass
- ✅ No crashes or errors in logs

**FAIL Indicators**:
- ❌ Overlay blocks critical buttons
- ❌ Performance drops below 30fps
- ❌ Crashes when toggling
- ❌ Information inaccurate or not updating
- ❌ Previous fixes broken (Material 3, PiP, overlap)

---

## Documentation After Testing

Upon completion, update:

1. **ACTIVE_TODOS.md** - Mark DiagnosticOverlay testing complete
2. **SESSION_HISTORY.md** - Add DiagnosticOverlay test results
3. **ARCHITECTURE.md** - Add DiagnosticOverlay to plugin list (if not present)

If issues found, create:
4. **DIAGNOSTIC_OVERLAY_ISSUES.md** - Document bugs and fixes

---

## Testing Commands Reference

```bash
# Launch camera
adb shell am start -a com.customcamera.app.TEST_CAMERA

# Capture screenshot
adb exec-out screencap -p > screenshot.png

# Check diagnostic logs
adb logcat -d | grep "Diagnostic"

# Check performance
adb logcat -d | grep -i "frame\|drop\|lag\|performance"

# Check camera state changes
adb logcat -d | grep "Camera state"

# Monitor in real-time
adb logcat | grep "Diagnostic\|CameraEngine"
```

---

**Created**: 2025-10-23  
**For Version**: v2.1.41-build.33  
**Estimated Duration**: 15-20 minutes  
**Prerequisites**: ADB connection, device with camera

**Next**: Execute this test plan in next session when device reconnects
