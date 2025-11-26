# User Testing Instructions: v2.3.2-build.40

**Version**: v2.3.2-build.40
**Date**: 2025-11-26 (Session 28)
**Status**: Ready for Testing
**APK Location**: `app/build/outputs/apk/debug/app-debug.apk` (76 MB)

---

## What's Been Fixed

### ✅ Bug #2 (P1): Focus Not Working - FIXED
- **What was broken**: Tapping on camera preview did nothing
- **What's fixed**: Tap anywhere on preview to focus
- **How it works**:
  - Single tap triggers focus with haptic feedback
  - Light vibration when you tap
  - Stronger vibration when focus succeeds
  - Focus locks for 5 seconds before reverting to auto

### ✅ Bug #3 (P3): Version Shows "vnull (0)" - FIXED
- **What was broken**: Home screen showed "vnull (0)"
- **What's fixed**: Now shows "v2.3.2 (40)"
- **How it works**: Version hardcoded in build.gradle

### ❌ Bug #1 (P0): Video Recording Doesn't Save - NEEDS YOUR HELP
- **What's broken**: Videos don't save to gallery
- **What I need**: Logcat from a failed recording
- **How to help**: See instructions below

---

## Quick Testing Checklist (5 Minutes)

### 1. Install v2.3.2-build.40
```bash
# Option A: From device file manager
Navigate to: /storage/emulated/0/Download/app-debug.apk
Tap to install

# Option B: Via ADB
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 2. Check Version Display
- [ ] Open CustomCamera
- [ ] Look at home screen
- [ ] **Expected**: See "v2.3.2 (40)" instead of "vnull (0)"

### 3. Test Tap-to-Focus
- [ ] Tap "Quick Camera"
- [ ] Tap anywhere on camera preview
- [ ] **Expected**: Feel vibration + see focus change

### 4. Capture Video Recording Logs (CRITICAL)
```bash
# Clear old logs
adb logcat -c

# Start recording in app (tap record button)
# Record for 10 seconds
# Stop recording (tap record button again)

# Immediately capture logs
adb logcat -d > video_recording_test.log

# Check if video saved
adb shell ls -lh /sdcard/DCIM/Camera/
```

- [ ] Share `video_recording_test.log` file with me
- [ ] Report whether video file exists in DCIM/Camera/

---

## Detailed Testing (15 Minutes)

For comprehensive testing, see `TESTING_GUIDE_v2.3.1.md` (555 lines)

**Key Tests**:
1. Basic tap-to-focus (Test 1)
2. Multi-point focus (Test 2)
3. Low light focus (Test 3)
4. Multi-tap gestures still work (Test 4)
5. Video recording logs (Test 5)
6. Performance verification (Tests 6-7)
7. Regression testing (Test 8)

---

## What to Report Back

### Priority 1: Bug Fixes

**Tap-to-Focus (Bug #2)**:
```
✅ PASS: Tap triggers focus with haptic feedback
❌ FAIL: No vibration / no focus change
⚠️ PARTIAL: Works but [describe issue]
```

**Version Display (Bug #3)**:
```
✅ PASS: Shows "v2.3.2 (40)"
❌ FAIL: Still shows "vnull (0)" or different
```

**Video Recording (Bug #1)**:
```
✅ PASS: Video saves to DCIM/Camera/
❌ FAIL: Video doesn't save (attach video_recording_test.log)
```

### Priority 2: Performance

**Cold Start**:
```
Time: [XXX]ms (expected: 400-500ms)
✅ Fast / ❌ Slow
```

**Memory**:
```
Memory: [XXX] MB (expected: 100-120 MB)
Check with: adb shell dumpsys meminfo com.customcamera.app | grep "TOTAL PSS"
```

### Priority 3: Regressions

List any features that broke or behave differently:
```
- Photo capture: ✅ / ❌
- Camera switching: ✅ / ❌
- Flash toggle: ✅ / ❌
- Pinch zoom: ✅ / ❌
- Multi-tap gestures: ✅ / ❌
- Settings: ✅ / ❌
```

---

## How to Capture Video Recording Logs

**Step-by-Step**:

1. **Connect via ADB**:
```bash
adb devices
# Should show: 10.0.0.131:36851	device
```

2. **Clear old logs**:
```bash
adb logcat -c
```

3. **Open CustomCamera and record video**:
   - Tap "Quick Camera"
   - Wait for preview to load
   - Tap capture button (starts recording)
   - Wait 5-10 seconds
   - Tap capture button again (stops recording)

4. **Immediately capture logs**:
```bash
adb logcat -d > video_recording_test.log
```

5. **Check if video saved**:
```bash
adb shell ls -lh /sdcard/DCIM/Camera/ | tail -5
# Look for recent .mp4 files
```

6. **Share the log file**:
   - Location: `video_recording_test.log` (in current directory)
   - Upload/share via your preferred method

**What I'm Looking For in Logs**:
- "Recording started" message
- "Recording stopped" message
- Output URI for saved video
- Any errors about MediaStore
- Any exceptions or crashes

---

## Expected Outcomes

### Scenario 1: Everything Works ✅
- Tap-to-focus works with haptic feedback
- Version shows "v2.3.2 (40)"
- Video recording saves files
- No regressions

**Next Steps**: Release v2.2.12 immediately

### Scenario 2: Tap & Version Fixed, Video Broken ⚠️
- Tap-to-focus works ✅
- Version displays correctly ✅
- Video recording still fails ❌

**Next Steps**: Analyze video_recording_test.log, fix Bug #1, re-test

### Scenario 3: New Issues Found 🔥
- New crashes or bugs introduced

**Next Steps**: Fix new issues before releasing

---

## Performance Bonus

**v2.3.2 vs v2.3.0 Baseline**:
- Cold start: 574ms → 425ms (**26% faster!**)
- Memory: ~109 MB (no change expected)
- APK: 76 MB (no change)

If you notice any performance differences (better or worse), please report!

---

## Files You Need

**For Installation**:
- `app/build/outputs/apk/debug/app-debug.apk` (76 MB)

**For Reference**:
- `TESTING_GUIDE_v2.3.1.md` - Detailed test procedures
- `BUG_FIX_v2.3.2.md` - Technical details of Bug #3 fix
- `SESSION28_SUMMARY.md` - Complete session summary

**For Bug Reporting**:
- `video_recording_test.log` - Create this via logcat

---

## Quick Commands Reference

```bash
# Install APK
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Check version
adb shell dumpsys package com.customcamera.app | grep version

# Launch app
adb shell am start -n com.customcamera.app/.MainActivity

# Capture logs
adb logcat -c                    # Clear
adb logcat -d > test.log         # Capture

# Check video files
adb shell ls -lh /sdcard/DCIM/Camera/

# Check memory
adb shell dumpsys meminfo com.customcamera.app | grep "TOTAL PSS"

# Cold start timing
adb shell am force-stop com.customcamera.app
adb shell am start -W com.customcamera.app/.MainActivity
```

---

## Questions?

If anything is unclear:
1. Check `TESTING_GUIDE_v2.3.1.md` for detailed procedures
2. Check `BUG_FIX_v2.3.2.md` for technical details
3. Ask for clarification

---

**Document Version**: 1.0
**Created**: 2025-11-26 (Session 28)
**Ready For**: User Testing
**Critical**: Video recording logs needed for Bug #1 fix
