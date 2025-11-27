# Custom Camera - User Test Guide

**Build Version**: v2.1.48-build.33
**Test Date**: 2025-11-13
**Estimated Time**: 10 minutes
**Device**: Your phone with physical touch

---

## Before You Start

### What Was Fixed
✅ **CRITICAL**: Photos now save to DCIM/Camera directory (will appear in gallery)
✅ **VERIFIED**: Haptic feedback already working
✅ **VERIFIED**: Grid overlay settings work correctly

### What To Test
1. Photo capture (CRITICAL - must verify fix works)
2. Preview exposure quality
3. Video recording
4. PiP dual camera
5. Plugin system

---

## Test 1: Photo Capture (CRITICAL) ⭐

**Why**: This tests the critical bug fix

**Steps**:
1. Launch "Custom Camera" app
2. Point camera at something with detail (not a blank wall)
3. **Physically tap the large purple capture button**
4. Feel for haptic vibration (should vibrate)
5. See button animate (should scale briefly)

**Verify**:
1. Open "Gallery" app
2. Look for **newest photo** at top
3. Photo filename should be: `yyyyMMdd_HHmmss.jpg` (e.g., `20251113_011500.jpg`)
4. Open the photo
5. **Confirm photo is NOT blank** - should show what camera saw

**Expected Result**: ✅ Photo appears in gallery with content

**If It Fails**: ❌ Report: "Photo not in gallery" OR "Photo is blank"

---

## Test 2: Preview Exposure Quality

**Why**: Check if preview is over-exposed/washed out

**Steps**:
1. Launch Custom Camera
2. Point at different scenes:
   - Well-lit room
   - Darker area
   - Outdoor scene
3. Compare to native Camera app preview

**Verify**:
- Preview shows scene accurately (not washed out brown/tan color)
- Can see details clearly
- Colors look correct

**Expected Result**: ✅ Preview looks accurate

**If It Fails**: ❌ Report: "Preview over-exposed" + describe conditions

---

## Test 3: Video Recording

**Why**: Verify video capture works

**Steps**:
1. Launch Custom Camera
2. Look for video icon in top controls
3. **Tap video mode button** (if needed)
4. **Tap REC button** (should be visible)
5. Record for 5 seconds
6. **Tap STOP button**

**Verify**:
1. Open Gallery app
2. Find newest video file
3. Play video
4. Video plays smoothly (not corrupted)

**Expected Result**: ✅ Video file created and playable

**If It Fails**: ❌ Report: "Video not created" OR "Video corrupted"

---

## Test 4: PiP Dual Camera (If Available)

**Why**: Test dual camera compositing

**Steps**:
1. Launch Custom Camera
2. **Tap the PiP button** (overlapping squares icon, top right)
3. Small overlay should appear showing second camera
4. **Take a photo** (tap purple button)
5. Open Gallery

**Verify**:
- Photo shows BOTH cameras
- Main camera view
- Small PiP overlay in corner
- Both images clear

**Expected Result**: ✅ Dual camera composite photo

**If It Fails**: ❌ Report: "PiP not working" + describe issue

**Note**: If device doesn't support concurrent cameras, PiP button may not work (expected)

---

## Test 5: Plugin Dropdown Menu

**Why**: Verify plugin system works

**Steps**:
1. Launch Custom Camera
2. **Tap puzzle piece icon** (plugin button, right side)
3. Dropdown menu should appear
4. **Toggle a plugin** (e.g., Grid Overlay)
5. Close dropdown
6. Verify plugin state changed

**Verify**:
- Dropdown opens smoothly
- Shows list of plugins
- Toggle switches work
- Plugin enables/disables correctly

**Expected Result**: ✅ Plugin dropdown works

**If It Fails**: ❌ Report: "Dropdown doesn't open" OR "Toggle doesn't work"

---

## Test 6: Camera Switching

**Why**: Verify all cameras work

**Steps**:
1. Launch Custom Camera
2. **Tap camera flip/switch button**
3. Preview should switch to different camera
4. **Take photo with each camera**:
   - Back camera
   - Front camera
   - Any additional cameras

**Verify**:
- Preview changes when switching
- Photos from each camera work
- All photos appear in gallery

**Expected Result**: ✅ All cameras work

**If It Fails**: ❌ Report: "Camera X doesn't work" (specify which)

---

## Test 7: Grid Overlay

**Why**: Verify grid toggle works

**Steps**:
1. Launch Custom Camera
2. Note if grid is visible
3. Open plugin dropdown
4. **Toggle Grid Overlay OFF**
5. Grid should disappear
6. **Toggle Grid Overlay ON**
7. Grid should reappear

**Verify**:
- Grid toggles smoothly
- Setting persists after relaunch

**Expected Result**: ✅ Grid toggle works

**If It Fails**: ❌ Report: "Grid doesn't toggle"

---

## Test 8: Flash Modes

**Why**: Verify flash control works

**Steps**:
1. Launch Custom Camera
2. **Tap flash icon** (lightning bolt, top left)
3. Cycle through modes: Auto → On → Off
4. Take photo in each mode (in darker conditions)

**Verify**:
- Flash icon changes appearance
- Flash fires when set to ON
- Flash doesn't fire when set to OFF

**Expected Result**: ✅ Flash modes work

**If It Fails**: ❌ Report: "Flash doesn't work" + mode that failed

---

## Quick Test Checklist

Use this for a fast 5-minute test:

- [ ] Photo capture (CRITICAL)
- [ ] Photo appears in gallery
- [ ] Photo is NOT blank
- [ ] Preview looks clear (not over-exposed)
- [ ] Video recording works
- [ ] One plugin toggle works
- [ ] Camera switch works

**If all checked**: ✅ App works!

---

## Reporting Results

### Format:
```
TEST RESULTS:

Test 1 (Photo Capture): ✅ PASS / ❌ FAIL
  - Details: [describe what you saw]

Test 2 (Preview): ✅ PASS / ❌ FAIL
  - Details: [describe quality]

Test 3 (Video): ✅ PASS / ❌ FAIL
Test 4 (PiP): ✅ PASS / ❌ FAIL / ⏭️ SKIPPED
Test 5 (Plugins): ✅ PASS / ❌ FAIL
Test 6 (Cameras): ✅ PASS / ❌ FAIL
Test 7 (Grid): ✅ PASS / ❌ FAIL
Test 8 (Flash): ✅ PASS / ❌ FAIL

Overall: ✅ ALL PASS / ⚠️ SOME ISSUES / ❌ MAJOR FAILURES
```

---

## Diagnostic Info to Collect

If you want to help debug issues:

### 1. Get App Logs
```bash
adb logcat -d > camera_logs.txt
grep -i "customcamera\|exposure\|photo\|capture" camera_logs.txt > filtered_logs.txt
```

### 2. Check Photo Location
```bash
adb shell ls -l /sdcard/DCIM/Camera/
```

### 3. Get Screenshot
```bash
adb exec-out screencap -p > camera_screenshot.png
```

---

## Expected Timeline

| Test | Time | Priority |
|------|------|----------|
| Photo Capture | 1 min | ⭐ CRITICAL |
| Preview Quality | 1 min | ⭐ HIGH |
| Video Recording | 1 min | ⭐ HIGH |
| PiP Dual Camera | 2 min | MEDIUM |
| Plugin Dropdown | 1 min | MEDIUM |
| Camera Switching | 2 min | MEDIUM |
| Grid Toggle | 1 min | LOW |
| Flash Modes | 1 min | LOW |

**Total**: ~10 minutes for comprehensive test

**Minimum**: 3 minutes for critical tests only (Tests 1, 2, 3)

---

## What Success Looks Like

### Perfect Result ✅
- Photos appear in gallery immediately
- Photos show clear content (not blank)
- Preview looks good (not over-exposed)
- Video records and plays
- All features work smoothly

### Acceptable Result ⚠️
- Photos work (critical fix verified)
- Some minor issues (preview quality, etc.)
- Most features functional

### Failure Result ❌
- Photos still not appearing in gallery
- Photos are blank
- App crashes frequently

---

## Troubleshooting

### Photo Not in Gallery
1. Check `/sdcard/DCIM/Camera/` via file manager
2. Check storage permissions granted
3. Try restarting Gallery app
4. Check photo filename format

### App Crashes
1. Clear app data
2. Reinstall app
3. Check logs with `adb logcat`

### Features Not Working
1. Restart app
2. Grant all permissions
3. Try different camera
4. Check logs for errors

---

## Contact & Feedback

**Report Issues**: [Describe where to report]

**Provide**:
- Test results (use format above)
- Screenshot if relevant
- Logs if available
- Device model and Android version

---

**Good luck testing!** 📸✨

*Remember: The photo capture test is the MOST IMPORTANT - that's the critical bug we fixed.*
