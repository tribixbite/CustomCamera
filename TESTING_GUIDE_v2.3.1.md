# Testing Guide: v2.3.1-build.39

**Date**: 2025-11-26 (Session 28)
**Build**: v2.3.1-build.39
**APK Location**: `app/build/outputs/apk/debug/app-debug.apk`
**Status**: Ready for Manual Testing

---

## Overview

This build fixes **Bug #2 (P1): Focus not working** by implementing tap-to-focus functionality. It also includes baseline testing for Sprint 2 preparation.

**What's Fixed**:
- ✅ Tap-to-focus now works (single tap on preview)
- ✅ Haptic feedback for focus confirmation
- ✅ Natural gesture (no multi-tap required)

**What's Still Broken**:
- ❌ Video recording save failure (Bug #1 - needs investigation)
- ⚠️ Version shows "vnull (0)" (Bug #3 - cosmetic only)

---

## Part 1: Installation Instructions

### Step 1: Install APK

**Option A: Via Package Installer** (Recommended)
```bash
# From device file manager, navigate to:
/storage/emulated/0/Download/app-debug.apk

# Tap to install
# Allow "Install from Unknown Sources" if prompted
```

**Option B: Via ADB**
```bash
# From Termux or computer:
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Verify installation:
adb shell pm list packages | grep customcamera
# Should show: package:com.customcamera.app
```

### Step 2: Verify Version

1. Launch CustomCamera
2. Check home screen for version text
3. **Expected**: "v2.3.1 (39)" or "vnull (0)" (known Bug #3)
4. If version is different, wrong build installed

---

## Part 2: Tap-to-Focus Testing

### Test 1: Basic Tap-to-Focus

**Objective**: Verify single tap triggers focus

**Steps**:
1. Launch CustomCamera
2. Tap "Quick Camera" button
3. Camera preview should appear
4. Tap anywhere on the preview
5. **Expected behavior**:
   - Light haptic feedback immediately (tap registered)
   - Camera focuses on tapped area
   - Success haptic feedback after focus completes (stronger vibration)
   - Preview may adjust brightness (auto-exposure)

**Success Criteria**:
- ✅ Tap causes haptic feedback
- ✅ Focus changes after tap
- ✅ Success haptic confirms focus
- ✅ No error messages in logs

**Failure Indicators**:
- ❌ No haptic feedback on tap
- ❌ Focus doesn't change
- ❌ Error haptic (rapid vibration)
- ❌ App crashes

### Test 2: Multi-Point Focus

**Objective**: Verify focus works at different preview locations

**Steps**:
1. Open camera in well-lit environment
2. Point at scene with objects at different distances (foreground, background)
3. Tap on close object
4. **Expected**: Close object becomes sharp, background blurs
5. Tap on distant object
6. **Expected**: Distant object becomes sharp, foreground blurs

**Success Criteria**:
- ✅ Focus shifts between near and far objects
- ✅ Haptic feedback for each tap
- ✅ Focus locks for ~5 seconds before reverting to auto

### Test 3: Low Light Focus

**Objective**: Verify tap-to-focus works in challenging conditions

**Steps**:
1. Open camera in dim lighting
2. Tap on subject
3. **Expected**: Camera attempts focus (may take longer)
4. **Note**: Focus may fail in very low light (normal behavior)
5. If focus fails: light tap haptic instead of success haptic

**Success Criteria**:
- ✅ Focus attempt registered (initial haptic)
- ✅ Either success or failure haptic (not error)
- ✅ No crashes

### Test 4: Multi-Tap Gestures Still Work

**Objective**: Verify tap-to-focus doesn't break existing gestures

**Steps**:
1. Open camera
2. Tap preview twice quickly (2× tap)
3. **Expected**: Grid overlay toggles
4. Tap preview three times quickly (3× tap)
5. **Expected**: Barcode scanner activates
6. Single tap
7. **Expected**: Focus triggers (not grid/barcode)

**Success Criteria**:
- ✅ 2× tap = grid overlay
- ✅ 3× tap = barcode scanner
- ✅ Single tap = focus only
- ✅ No gesture interference

---

## Part 3: Video Recording Bug Investigation

### Test 5: Capture Video Recording Logs

**Objective**: Provide diagnostic logs for Bug #1 investigation

**Steps**:

1. **Clear old logs**:
```bash
adb logcat -c
```

2. **Launch camera and start recording**:
   - Open CustomCamera
   - Tap "Quick Camera"
   - Wait for preview to stabilize
   - **Tap capture button to start recording**
   - Record for 10 seconds
   - **Tap capture button to stop recording**

3. **Immediately capture logs**:
```bash
adb logcat -d > ~/git/swype/CustomCamera/video_recording_test.log
```

4. **Check if video saved**:
```bash
adb shell ls -lh /sdcard/DCIM/Camera/
# Look for recent .mp4 files
```

5. **Provide logs**:
   - Share `video_recording_test.log` file
   - Report whether video file exists in DCIM/Camera/

**What to Look For in Logs**:
- "Recording started" message
- "Recording stopped" message
- Any error messages about MediaStore
- Output URI for saved video
- Any exceptions or crashes

**Important**: Do NOT clear logcat between start and stop. We need the complete recording lifecycle.

---

## Part 4: Performance Verification

### Test 6: Cold Start Timing

**Objective**: Verify no performance regressions from tap-to-focus fix

**Steps**:
1. Force stop app:
```bash
adb shell am force-stop com.customcamera.app
```

2. Launch and time:
```bash
adb shell am start -W com.customcamera.app/.MainActivity
```

3. **Check "ThisTime" value** in output
4. **Expected**: 500-700ms (baseline: 574ms)
5. **Regression**: >1000ms

**Success Criteria**:
- ✅ Cold start <700ms
- ✅ No significant change from baseline

### Test 7: Memory Usage

**Objective**: Verify no memory leaks from focus handling

**Steps**:
1. Launch app and use camera for 2-3 minutes
2. Tap on preview 20-30 times (trigger focus repeatedly)
3. Take 5-10 photos
4. Check memory:
```bash
adb shell dumpsys meminfo com.customcamera.app | grep "TOTAL PSS"
```

5. **Expected**: 100-120 MB (baseline: 109 MB)
6. **Leak indicator**: >150 MB

**Success Criteria**:
- ✅ Memory usage stable after repeated focus operations
- ✅ PSS <120 MB

---

## Part 5: Regression Testing

### Test 8: Existing Features Still Work

**Objective**: Verify no regressions from code changes

**Checklist**:
- [ ] Photo capture works (tap capture button)
- [ ] Camera switching works (tap camera switch button)
- [ ] Flash toggle works (tap flash button)
- [ ] Pinch-to-zoom works
- [ ] 2× tap = grid overlay toggle
- [ ] 3× tap = barcode scanner toggle
- [ ] Settings open and close properly
- [ ] App doesn't crash during normal use

**Success Criteria**:
- ✅ All features functional
- ✅ No new crashes
- ✅ No UI glitches

---

## Part 6: User Experience Feedback

### Subjective Evaluation

**Questions to Consider**:
1. Does tap-to-focus feel responsive?
2. Is haptic feedback helpful or annoying?
3. Does focus lock for too long or too short (currently 5 seconds)?
4. Does focus interfere with taking photos?
5. Are multi-tap gestures still easy to trigger?

**Optional Improvements**:
- Adjust auto-cancel duration (5s → 3s or 7s?)
- Change haptic patterns (lighter/stronger?)
- Add visual focus indicator (circle at tap point)?

---

## Part 7: Reporting Results

### How to Report

**For Tap-to-Focus (Bug #2)**:
```
✅ PASS: Tap-to-focus works, haptic feedback good
❌ FAIL: No haptic feedback, focus doesn't change
⚠️ PARTIAL: Works but [specific issue]
```

**For Video Recording (Bug #1)**:
```
✅ PASS: Video saves to DCIM/Camera/
❌ FAIL: Video does not save (attached video_recording_test.log)
```

**For Performance**:
```
Cold start: [XXX]ms (expected: 500-700ms)
Memory: [XXX] MB (expected: 100-120 MB)
```

**For Regressions**:
```
List any features that broke or behave differently
```

### What to Attach

1. **video_recording_test.log** (if video recording fails)
2. Screenshots showing any visual bugs
3. Logcat snippets for any crashes:
```bash
adb logcat -d | grep -A 20 "AndroidRuntime: FATAL"
```

---

## Part 8: Expected Outcomes

### Scenario 1: Everything Works ✅

**Results**:
- ✅ Tap-to-focus works with haptic feedback
- ✅ Video recording saves files
- ✅ No regressions
- ✅ Performance similar to baseline

**Next Steps**:
- Fix version display (Bug #3)
- Release v2.2.12
- Continue Sprint 2 planning

### Scenario 2: Tap-to-Focus Works, Video Fails ⚠️

**Results**:
- ✅ Tap-to-focus works (Bug #2 fixed)
- ❌ Video recording still broken (Bug #1)

**Next Steps**:
- Analyze video_recording_test.log
- Fix video recording issue
- Re-test and release v2.2.12

### Scenario 3: Both Still Broken ❌

**Results**:
- ❌ Tap-to-focus doesn't work (Bug #2 not fixed)
- ❌ Video recording doesn't work (Bug #1)

**Next Steps**:
- Investigate why fix didn't work
- May need additional debugging
- Check if v2.3.1 build actually installed

### Scenario 4: New Issues Found 🔥

**Results**:
- New crashes or bugs introduced

**Next Steps**:
- Rollback to v2.3.0
- Fix new issues before re-testing

---

## Part 9: Quick Testing Checklist

For rapid testing, use this minimal checklist:

### 5-Minute Smoke Test
1. [ ] Install v2.3.1-build.39
2. [ ] Launch camera
3. [ ] Tap on preview (should vibrate and focus)
4. [ ] Take a photo (should work)
5. [ ] Record video (check if saves)

**If all pass**: Full testing recommended but not critical
**If any fail**: Full testing required + log capture

---

## Part 10: Technical Details (For Reference)

### What Changed in v2.3.1

**File**: `CameraActivityEngine.kt`
**Lines Modified**: 2092-2094, 2110-2168 (+61/-7 lines)

**Change 1**: Added single tap handler
```kotlin
} else {
    // Single tap - perform tap-to-focus
    tapCount = 0
    handleTapToFocus(event.x, event.y)
}
```

**Change 2**: Implemented handleTapToFocus() function
```kotlin
private fun handleTapToFocus(x: Float, y: Float) {
    lifecycleScope.launch {
        try {
            val camera = cameraEngine.getCurrentCamera()
            if (camera == null) {
                Log.w(TAG, "Tap-to-focus: Camera not available")
                return@launch
            }

            // Create metering point factory
            val factory = SurfaceOrientedMeteringPointFactory(
                binding.previewView.width.toFloat(),
                binding.previewView.height.toFloat()
            )

            // Create metering point at tap coordinates
            val point = factory.createPoint(x, y)

            // Build focus and metering action
            val action = FocusMeteringAction.Builder(point)
                .addPoint(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
                .setAutoCancelDuration(5, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            Log.d(TAG, "Tap-to-focus at ($x, $y)")

            // Haptic feedback
            hapticManager.lightTap()

            // Start focus and metering
            val result = camera.cameraControl.startFocusAndMetering(action)
            result.addListener({
                try {
                    val focusResult = result.get()
                    if (focusResult.isFocusSuccessful) {
                        Log.i(TAG, "Tap-to-focus: SUCCESS")
                        hapticManager.success()
                    } else {
                        Log.w(TAG, "Tap-to-focus: FAILED")
                        hapticManager.lightTap()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error getting focus result", e)
                }
            }, ContextCompat.getMainExecutor(this@CameraActivityEngine))

        } catch (e: Exception) {
            Log.e(TAG, "Tap-to-focus failed", e)
            hapticManager.error()
        }
    }
}
```

### Root Cause (Bug #2)

**Problem**: Touch listener consumed single taps but didn't process them.

**Before**:
```kotlin
} else {
    tapCount = 0  // Reset counter but DO NOTHING
}
lastTapTime = currentTime
true  // Returns true but no action!
```

**After**:
```kotlin
} else {
    tapCount = 0
    handleTapToFocus(event.x, event.y)  // Process single tap
}
lastTapTime = currentTime
true
```

### CameraX APIs Used

- `SurfaceOrientedMeteringPointFactory`: Maps screen coordinates to camera sensor coordinates
- `FocusMeteringAction`: Combines AF (auto-focus) and AE (auto-exposure) operations
- `FLAG_AF`: Auto-focus flag
- `FLAG_AE`: Auto-exposure flag
- `setAutoCancelDuration(5s)`: Focus locks for 5 seconds before reverting to continuous AF

---

**Document Version**: 1.0
**Created**: 2025-11-26 (Session 28)
**Status**: Ready for User Testing
**Next**: Manual testing by user, provide results + video_recording_test.log
