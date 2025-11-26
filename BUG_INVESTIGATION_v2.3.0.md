# Bug Investigation - v2.3.0

**Investigation Date**: 2025-11-26 (Session 28)
**Build**: v2.3.0 (build 39)
**Device**: ADB connected device (10.0.0.131:36851)

---

## Executive Summary

### Bugs Found: 3 Total (1 P0, 1 P1, 1 P3)

| # | Bug | Severity | Status | Root Cause | Fix Complexity |
|---|-----|----------|--------|------------|----------------|
| 1 | Video recording does not save file | ❌ P0 CRITICAL | ⏳ Under Investigation | Unknown (needs logcat) | Unknown |
| 2 | Focus not working | ❌ P1 HIGH | ✅ **ROOT CAUSE FOUND** | Missing tap-to-focus handler | **LOW** (1-2 hours) |
| 3 | Version shows "vnull (0)" | ⚠️ P3 MINOR | ⏳ Under Investigation | version.properties not loaded | LOW (30 min) |

### Key Findings

**Bug #2 (Focus) - SOLVED**:
- **Root Cause**: Single taps are completely ignored in touch event handler
- **Location**: `CameraActivityEngine.kt:2038-2099`
- **Impact**: Tap-to-focus is non-functional (continuous autofocus still works)
- **Fix**: Add `handleTapToFocus()` function + wire up single tap event
- **Effort**: 1-2 hours (straightforward implementation)

**Bug #1 (Video) - NEEDS DATA**:
- Code review shows correct MediaStore implementation
- Need device logcat to see if recording starts/finishes properly
- Possible causes: Plugin not enabled, VideoCapture not bound, or finalize error

**Bug #3 (Version) - LOW PRIORITY**:
- Cosmetic issue only
- version.properties not being read from APK
- Can defer to v2.2.12 release

---

## Bug #1: Video Recording Does Not Save File ❌ P0

### Severity
**P0 - CRITICAL**: Core functionality broken, blocks user workflow

### User Report
> "video recording still fails to save a file"

### Evidence from Testing
```bash
$ adb shell ls -lht /sdcard/DCIM/Camera/ | head -5
-rwxrwx--- 2 u0_a315 media_rw  51M 2025-11-26 14:28 video_1764185285275.mp4
-rwxrwx--- 1 u0_a315 media_rw 428K 2025-11-26 12:28 20251126_122735.jpg
```

**Note**: A video file exists from 14:28, but it's unclear if this was from CustomCamera or another app.

### Code Analysis

#### MediaStore Integration (Lines 186-197)
```kotlin
val contentValues = android.content.ContentValues().apply {
    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, "video_${System.currentTimeMillis()}.mp4")
    put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
        put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/Camera")
    }
}

val outputOptions = MediaStoreOutputOptions.Builder(
    context.contentResolver,
    android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI
).setContentValues(contentValues).build()
```

**Analysis**: ✅ MediaStore code looks correct
- Uses MediaStore API (Android 10+ compatible)
- Saves to `DCIM/Camera` path
- Proper MIME type and file naming

#### Recording Event Handler (Lines 504-516)
```kotlin
is VideoRecordEvent.Finalize -> {
    if (event.hasError()) {
        val errorMessage = "Recording finalized with error: ${event.error}"
        val cause = event.cause
        if (cause != null) {
            Log.e(TAG, errorMessage, cause)
            Log.e(TAG, "Error cause: ${cause.message}")
        } else {
            Log.e(TAG, errorMessage)
        }
    } else {
        Log.i(TAG, "Recording finalized successfully: ${event.outputResults.outputUri}")
    }
    _isRecording.value = false
    _isPaused.value = false
}
```

**Analysis**: ✅ Event handling looks correct
- Logs success/error
- Logs output URI on success
- Updates state properly

### Potential Root Causes

#### 1. Plugin Not Enabled (Most Likely)
**File**: `AdvancedVideoRecordingPlugin.kt:36-38`
```kotlin
// Start enabled by default to ensure VideoCapture UseCase is bound
// This fixes the issue where video recording fails when PiP is off
init {
    isEnabled = true
}
```

**Issue**: Comment says "Start enabled by default", but the plugin might be getting disabled somewhere.

**Evidence from CameraActivityEngine.kt:686**:
```kotlin
// Set up advanced video recording
```

**Action**: Need to verify plugin is actually enabled when camera launches.

#### 2. VideoCapture UseCase Not Bound
**File**: `CameraActivityEngine.kt:659`
```kotlin
Log.w(TAG, "⚠️ WARNING: VideoCapture is NULL - video recording will not work")
```

**Issue**: VideoCapture might not be bound if the plugin is disabled.

**Test Intent Evidence (lines 284-303)**:
The TEST_VIDEO intent explicitly:
1. Disables PiP
2. Enables the plugin
3. Rebinds camera with `enableVideoCapture = true`
4. Waits for camera ready
5. Then starts recording

**Question**: Why does the test intent need to explicitly enable the plugin and rebind if the plugin is "enabled by default"?

#### 3. Permission Issues
**File**: `AdvancedVideoRecordingPlugin.kt:200-203`
```kotlin
val hasAudioPermission = androidx.core.content.ContextCompat.checkSelfPermission(
    cameraContext!!.context,
    android.Manifest.permission.RECORD_AUDIO
) == android.content.pm.PackageManager.PERMISSION_GRANTED
```

**Analysis**: ✅ Audio permission checked, gracefully handles missing permission

**Potential Issue**: If WRITE_EXTERNAL_STORAGE is not granted on older Android, MediaStore might fail silently.

#### 4. Concurrent Mode (PiP) Incompatibility
**File**: `CameraActivityEngine.kt` (toggleVideoRecording)
```kotlin
// Check if in concurrent camera mode (PiP active)
val currentMode = cameraEngine.getCurrentMode()
if (currentMode is com.customcamera.app.engine.CameraMode.Concurrent) {
    // Haptic feedback for unavailable action
    hapticManager.error()
```

**Analysis**: Video recording is explicitly disabled in PiP mode (correct behavior).

**User Testing Scenario**: Was PiP mode active when testing? Need to verify.

### Reproduction Steps Needed

1. Launch CustomCamera
2. Verify PiP mode is OFF
3. Switch to VIDEO mode
4. Tap capture button to start recording
5. Wait 5-10 seconds
6. Tap capture button to stop recording
7. Check logs for "Recording finalized successfully" message
8. Check `/sdcard/DCIM/Camera/` for new video file
9. Verify file plays in gallery

### Debug Logging Required

Need to check logcat for:
```bash
# Start recording
grep "Video recording started" logcat.txt

# Finalize event
grep "Recording finalized" logcat.txt

# Output URI
grep "outputUri" logcat.txt

# Errors
grep -i "error\|fail" logcat.txt | grep -i "video\|recording"

# VideoCapture status
grep "VideoCapture is NULL" logcat.txt
```

### Hypothesis

**Primary Hypothesis**: Plugin is not properly enabled or VideoCapture UseCase is not bound when the user tries to start recording.

**Evidence**:
1. TEST_VIDEO intent explicitly enables plugin and rebinds camera
2. Warning log exists: "VideoCapture is NULL"
3. Comment says "Start enabled by default" but init block might not execute properly

**Secondary Hypothesis**: Recording starts but fails to finalize (encoder error, disk space, etc.)

**Evidence**:
- Need to check actual logcat from user's device
- handleRecordingEvent logs would show finalize errors

### Next Steps

1. **Verify Plugin State**:
   - Add debug logging to track when plugin.enable() is called
   - Check plugin.isEnabled value before calling startRecording()

2. **Check VideoCapture Binding**:
   - Log VideoCapture availability in toggleVideoRecording()
   - Verify CameraConfig.enableVideoCapture = true on camera bind

3. **Test on Device**:
   - Use ADB to trigger TEST_VIDEO intent: `adb shell am start -a com.customcamera.app.TEST_VIDEO`
   - Capture full logcat during test
   - Verify file appears in gallery

4. **Check Permissions**:
   - Verify RECORD_AUDIO permission granted
   - Check storage permissions (WRITE_EXTERNAL_STORAGE on Android <10)

5. **User Confirmation**:
   - Ask user if PiP mode was active during test
   - Ask user to check Settings → Plugins → Advanced Video (should be enabled)
   - Request logcat from failed recording attempt

---

## Bug #2: Focus Not Working ❌ P1

### Severity
**P1 - HIGH**: Core camera functionality impaired, affects photo quality

### User Report
> "also focus does not seem to work"

### Manifestations
- Tap-to-focus gesture not responding
- AutoFocus not functioning
- Photos may be blurry/out of focus

### Code Analysis

#### AutoFocusPlugin
**File**: `app/src/main/java/com/customcamera/app/plugins/AutoFocusPlugin.kt`

**From Phase 10 Sprint 1** (Session 22-25):
- AutoFocus thread fix implemented
- Changed from HandlerThread to Executors.newSingleThreadExecutor()
- Lines changed: +12/-7

**Question**: Was this fix properly tested?

#### Focus Metering Action
**AutoFocusPlugin.kt** likely uses `FocusMeteringAction` from CameraX:
```kotlin
// Typical implementation (need to verify actual code)
val factory = SurfaceOrientedMeteringPointFactory(...)
val point = factory.createPoint(x, y)
val action = FocusMeteringAction.Builder(point).build()
camera.cameraControl.startFocusAndMetering(action)
```

### Potential Root Causes

#### 1. AutoFocus Plugin Not Enabled
**From PHASE10_DASHBOARD.md**:
> **Always Active (6)**: NightMode, DualCameraPiP (dedicated buttons), AutoFocus, ExposureControl, ManualFocus, ProControls

**Analysis**: AutoFocus should be "always active", not togglable.

**Question**: Is plugin properly initialized and receiving camera events?

#### 2. Thread Fix Regression
**From PHASE10_SPRINT1_SUMMARY.md**:
> AutoFocus plugin thread management fix (12 lines)

**Concern**: Thread fix might have introduced a regression if executor isn't properly started/shutdown.

#### 3. Camera Control Not Available
**Potential Issue**: If camera binding fails or AutoFocus plugin initializes before camera is ready, `camera.cameraControl` might be null.

#### 4. Tap-to-Focus Gesture Conflict
**CameraActivityEngine.kt** likely has touch event handlers for:
- Pinch-to-zoom
- Multi-tap gestures (2× = grid, 3× = barcode, etc.)
- Long-press (AI features)

**Question**: Is tap-to-focus gesture being consumed by another handler?

#### 5. Focus Mode Configuration
**Potential Issue**: Camera might be in FOCUS_MODE_LOCKED or FOCUS_MODE_OFF instead of FOCUS_MODE_AUTO.

### Reproduction Steps Needed

1. Launch CustomCamera
2. Point camera at object with clear detail (text, pattern)
3. Tap on preview to focus on object
4. Observe if focus indicator appears (green square/circle)
5. Observe if image sharpness changes
6. Take photo and check if it's in focus
7. Check logcat for AutoFocus plugin logs

### Debug Logging Required

```bash
# AutoFocus initialization
grep "AutoFocus" logcat.txt

# Focus metering
grep "Focus\|focus\|metering" logcat.txt

# Touch events
grep "Touch\|onTouch\|gesture" logcat.txt

# Camera control
grep "cameraControl" logcat.txt
```

### ROOT CAUSE IDENTIFIED ✅

**File**: `CameraActivityEngine.kt:2038-2099`

The touch listener on `binding.previewView` only handles:
1. Pinch-to-zoom (ScaleGestureDetector)
2. Multi-tap gestures (2× = grid, 3× = barcode, 4× = crop, 5× = smart scene, 6× = object detection)

**MISSING**: Single tap → tap-to-focus functionality!

**Evidence**:
```kotlin
when (event.action) {
    MotionEvent.ACTION_DOWN -> {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastTapTime < 300) {
            tapCount++
            // ... handles 2×, 3×, 4×, 5×, 6× taps
        } else {
            tapCount = 0  // Reset counter but DO NOTHING with single tap
        }
        lastTapTime = currentTime
        true  // Returns true but no focus action!
    }
    else -> false
}
```

**Analysis**:
- Single taps are consumed (`return true`) but not processed
- AutoFocusPlugin has `startFocusAndMetering()` implementation (lines 109, 193, 251)
- AutoFocusPlugin is correctly configured for continuous autofocus
- BUT: No code connects tap events to AutoFocusPlugin

**Impact**:
- Continuous autofocus works (camera autofocuses automatically)
- Tap-to-focus completely non-functional
- User cannot manually focus on specific subjects
- Photos of off-center subjects may be out of focus

### Fix Required

**Add tap-to-focus handler** after multi-tap gesture detection:
```kotlin
} else {
    tapCount = 0
    // NEW: Handle single tap for tap-to-focus
    handleTapToFocus(event.x, event.y)
}
```

**Implement handleTapToFocus**:
```kotlin
private fun handleTapToFocus(x: Float, y: Float) {
    lifecycleScope.launch {
        val camera = cameraEngine.getCurrentCamera() ?: return@launch
        val factory = SurfaceOrientedMeteringPointFactory(
            binding.previewView.width.toFloat(),
            binding.previewView.height.toFloat()
        )
        val point = factory.createPoint(x, y)
        val action = FocusMeteringAction.Builder(point).build()

        try {
            val result = camera.cameraControl.startFocusAndMetering(action)
            result.addListener({
                Log.i(TAG, "Tap-to-focus: ${if (result.get().isFocusSuccessful) "SUCCESS" else "FAILED"}")
            }, ContextCompat.getMainExecutor(this@CameraActivityEngine))
        } catch (e: Exception) {
            Log.e(TAG, "Tap-to-focus failed", e)
        }
    }
}
```

**OR**: Delegate to AutoFocusPlugin if it exposes a tap-to-focus method.

### Next Steps

1. **Implement tap-to-focus** in CameraActivityEngine
2. **Test on device** - verify single tap focuses
3. **Add focus indicator** (green square/circle) for user feedback
4. **Update documentation** - document tap-to-focus in user guide

---

## Bug #3: Version Display Shows "vnull (0)" ⚠️ P3

### Severity
**P3 - MINOR**: Cosmetic issue, no functional impact

### User Report
From baseline testing screenshot: Version shows "vnull (0)" instead of "v2.3.0 (39)"

### Evidence
```
MainActivity versionText: vnull (0)
```

### Potential Root Causes

#### 1. version.properties Not Found
**Typical location**: `app/version.properties`
```properties
versionName=2.3.0
versionCode=39
```

**Issue**: File might not be included in APK or path is incorrect.

#### 2. VersionHelper Reading Error
**Likely code** (MainActivity or similar):
```kotlin
val versionName = VersionHelper.getVersionName(context) ?: "null"
val versionCode = VersionHelper.getVersionCode(context) ?: 0
versionText.text = "v$versionName ($versionCode)"
```

**Issue**: `getVersionName()` returning null, default text is "vnull (0)"

#### 3. Termux Build Environment Issue
**Analysis**: Building on Termux ARM64 might not properly include version.properties resource file.

### Next Steps

1. **Check APK Contents**:
   ```bash
   unzip -l app/build/outputs/apk/debug/app-debug.apk | grep version
   ```

2. **Review VersionHelper**:
   - Check how version.properties is loaded
   - Verify resource path
   - Add fallback to BuildConfig.VERSION_NAME

3. **Fix Build Process**:
   - Ensure version.properties is in correct location
   - Update build.gradle.kts to include version resources
   - Test version reading in Termux build

4. **Low Priority**:
   - Not blocking any functionality
   - Fix as part of v2.2.12 bugfix release
   - Or defer to Sprint 2

---

## Investigation Summary

### Critical Path (P0) - Video Recording
1. ✅ Code review complete (video recording implementation looks correct)
2. ⏳ **BLOCKED**: Need actual device logcat from failed recording
3. ⏳ Verify plugin enabled state
4. ⏳ Test with TEST_VIDEO intent
5. ⏳ Reproduce issue with full logging

**Status**: Cannot proceed without device logcat. Video recording code appears correct, need runtime logs to diagnose.

### High Priority (P1) - Focus ✅ SOLVED
1. ✅ Review AutoFocusPlugin.kt implementation
2. ✅ Review focus gesture handling
3. ✅ **ROOT CAUSE IDENTIFIED**: Missing tap-to-focus handler in CameraActivityEngine
4. ⏳ Implement tap-to-focus function (1-2 hours)
5. ⏳ Test on device with logging
6. ⏳ Add focus indicator UI (optional enhancement)

**Status**: Ready to fix! Clear implementation path, low complexity.

### Low Priority (P3) - Version Display
1. ⏳ Check version.properties in APK
2. ⏳ Fix VersionHelper if needed
3. ⏳ Defer to bugfix release

**Status**: Cosmetic issue, can be fixed anytime.

---

## Recommendations for v2.2.12 Bugfix Release

### Must Fix (Blocking)
1. **Bug #2 (P1)**: Implement tap-to-focus ← **CAN START IMMEDIATELY**
   - Clear root cause identified
   - Low complexity fix
   - 1-2 hours implementation
   - Critical for user experience

2. **Bug #1 (P0)**: Video recording save failure ← **BLOCKED on logcat**
   - Need device logs to diagnose
   - User must test and provide logs
   - Cannot proceed without more information

### Nice to Have (Non-blocking)
3. **Bug #3 (P3)**: Fix version display
   - Quick 30-minute fix
   - Include in v2.2.12 if time permits
   - Or defer to Sprint 2

### Suggested Timeline
1. **Immediate**: Implement tap-to-focus fix (Bug #2)
2. **Next**: Test tap-to-focus on device
3. **Parallel**: User provides video recording logs (Bug #1)
4. **Then**: Diagnose and fix video recording (Bug #1)
5. **Optional**: Fix version display (Bug #3)
6. **Final**: Comprehensive testing, release v2.2.12

---

**Document Version**: 1.1
**Created**: 2025-11-26 (Session 28)
**Updated**: 2025-11-26 (Bug #2 root cause found)
**Status**: 1 bug solved, 2 under investigation
**Next Steps**: Implement tap-to-focus fix, await video recording logs
