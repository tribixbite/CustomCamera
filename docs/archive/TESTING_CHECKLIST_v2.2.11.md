# Testing Checklist - CustomCamera v2.2.11

**Version**: 2.2.11 (build 38)
**Build Date**: 2025-11-26 10:15 (verified fresh build)
**APK Location**: `app/build/outputs/apk/debug/app-debug.apk` (77MB)
**APK MD5**: `b5e586e7edb2c245a2ffb4c99397f92d`
**Backup Location**: `/sdcard/CustomCamera/latest-debug.apk`

## Installation Steps

1. Open package installer (APK should be ready from build-and-install.sh)
2. Tap "Install" button
3. Grant camera permissions when prompted
4. Launch "Custom Camera" from app drawer

## Critical Fixes to Verify (Session 15)

### Issue 1: Video Recording MediaStore Fix ✅
**What was broken**: Videos didn't save to gallery
**What was fixed**: Migrated to MediaStoreOutputOptions

**Test Steps**:
1. Launch camera app
2. Switch to VIDEO mode (mode selector above capture button)
3. Tap capture button to start recording
4. Record for 5-10 seconds
5. Tap capture button again to stop recording
6. **Expected**: Toast "Video recording stopped"
7. **Verify**: Open system gallery app
8. **Expected**: Video appears in gallery (DCIM/Camera folder)
9. **Expected**: Video plays correctly

**Success Criteria**:
- ✅ Video saves to MediaStore
- ✅ Video appears in gallery app
- ✅ Video filename format: `video_<timestamp>.mp4`
- ✅ Video location: `/sdcard/DCIM/Camera/`

### Issue 2: PiP Camera Switch Prevention ✅
**What was broken**: Changing camera in settings while PiP active didn't work
**What was fixed**: Added concurrent mode check with user feedback

**Test Steps**:
1. Launch camera app (any camera)
2. Tap PiP button (left side, below flash)
3. **Expected**: Dual camera mode activates (2 camera views)
4. While PiP is active, open Settings
5. Try to change "Default Camera" setting
6. Return to camera app
7. **Expected**: Toast appears: "Disable PiP mode before switching cameras"
8. **Expected**: Camera remains unchanged
9. Tap PiP button to disable
10. Now try changing camera in settings
11. **Expected**: Camera switches successfully

**Success Criteria**:
- ✅ Toast notification appears when switch attempted in PiP mode
- ✅ Camera doesn't switch while PiP active
- ✅ Camera DOES switch after disabling PiP
- ✅ No crashes or silent failures

## Additional Testing (Session 14 Fixes)

### Photo Capture MediaStore Fix
1. Launch camera app
2. Tap capture button (simple photo, no PiP, no crop)
3. **Expected**: Photo saves successfully
4. Open gallery app
5. **Expected**: Photo appears in gallery
6. **Expected**: Photo location: `/sdcard/DCIM/Camera/`

### PiP Button Accessibility
1. Launch camera app
2. **Verify**: PiP button visible on left side (below flash)
3. **Verify**: Button size: 48dp x 48dp
4. **Verify**: Button position: 24dp from left, 180dp from top
5. Tap PiP button
6. **Expected**: Dual camera mode activates

## Regression Testing

### Basic Camera Functions
- [ ] Camera preview displays correctly
- [ ] Flash toggle works (tap flash button)
- [ ] Camera switching works (when not in PiP mode)
- [ ] Photo capture works (PHOTO mode)
- [ ] Video recording works (VIDEO mode)
- [ ] Night mode works (NIGHT mode)
- [ ] Settings accessible (tap settings button)

### Plugin System
- [ ] Plugin dropdown menu accessible (tap hamburger menu)
- [ ] Plugins can be enabled/disabled
- [ ] Plugin settings in Settings screen
- [ ] Gesture controls work (multi-tap)

### Performance
- [ ] App launches quickly
- [ ] Camera preview is smooth (60fps)
- [ ] No memory leaks (extended use)
- [ ] Battery consumption acceptable

## Known Issues (Pre-existing)

⚠️ **Dual camera/crop modes use legacy item URI** (works, optional refactor)
- Simple photo capture: Modern collection URI ✅
- Video recording: Modern MediaStoreOutputOptions ✅
- Dual camera mode: Legacy item URI (functional)
- Crop mode: Legacy item URI (functional)

## Logs to Check

If any issues occur, collect logs:

```bash
# Photo capture logs
adb logcat -d | grep -E "CameraActivity|Photo|MediaStore|collection URI"

# Video recording logs
adb logcat -d | grep -E "Video|Recording|MediaStore"

# PiP mode logs
adb logcat -d | grep -E "PiP|Concurrent|camera switch"

# General camera logs
adb logcat -d | grep "customcamera"
```

## Success Indicators

✅ **All Critical**: Both Session 15 fixes verified working
✅ **All Basic**: Camera functions work correctly
✅ **No Regressions**: No new bugs introduced
✅ **Performance**: App performs well under normal use

## Report Results

After testing, report:
1. ✅ or ❌ for each critical fix
2. Any unexpected behavior
3. Logs if issues found
4. Overall impression (usable? stable?)

---

**Testing Status**: Ready for user verification
**Priority**: Test critical fixes first (Issue 1 & 2)
**Time Estimate**: 10-15 minutes
