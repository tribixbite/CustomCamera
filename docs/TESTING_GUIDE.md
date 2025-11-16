# CustomCamera Testing Guide

**Last Updated**: 2025-11-16
**Version**: 1.0
**Test Infrastructure**: Automated ADB Test Intents + Dynamic Coordinate Testing

---

## Table of Contents

1. [Overview](#overview)
2. [Test Intent System](#test-intent-system)
3. [Automated Test Scripts](#automated-test-scripts)
4. [Manual Testing Procedures](#manual-testing-procedures)
5. [Troubleshooting](#troubleshooting)

---

## Overview

CustomCamera has a comprehensive testing infrastructure designed for autonomous testing via ADB (Android Debug Bridge). This enables automated verification of camera functionality without manual device interaction.

### Testing Philosophy

- **Autonomous**: Tests run without human intervention
- **Intent-Based**: ADB intents trigger specific app behaviors
- **Device-Independent**: Dynamic coordinate calculation works on any screen size
- **Reproducible**: Same tests produce consistent results
- **Fast**: Typical test run completes in <20 seconds

### Prerequisites

```bash
# Verify ADB connection
adb devices

# Should show your device, e.g.:
# SM-S938U1    device

# Install latest APK
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## Test Intent System

CustomCamera provides 4 test intents for automated testing. All intents are declared in `AndroidManifest.xml` with action filters.

### 1. TEST_CAMERA - Launch Camera

**Intent**: `com.customcamera.app.TEST_CAMERA`

**Purpose**: Launch CameraActivityEngine directly for testing

**Usage**:
```bash
adb shell am start -a com.customcamera.app.TEST_CAMERA -n com.customcamera.app/.CameraActivityEngine
```

**What It Does**:
- Launches camera activity
- Skips camera selection screen
- Uses default camera (usually camera 0)
- Initializes all plugins

**Use Cases**:
- Quick camera launch for manual testing
- Baseline test for app startup
- Plugin initialization verification

**Expected Logs**:
```
I CameraActivityEngine: 📸 CustomCamera v2.1.x started
I CameraActivityEngine: ✅ Retrieved XX plugin references after initialization
```

---

### 2. TEST_PIP - Enable Dual Camera PiP

**Intent**: `com.customcamera.app.TEST_PIP`

**Purpose**: Automatically enable Picture-in-Picture dual camera mode

**Usage**:
```bash
adb shell am start -a com.customcamera.app.TEST_PIP -n com.customcamera.app/.CameraActivityEngine
```

**What It Does**:
1. Launches camera normally
2. Waits 3 seconds for camera initialization
3. Automatically enables DualCameraPiP plugin
4. Configures concurrent camera mode

**Workflow** (`CameraActivityEngine.kt:189-201`):
```kotlin
"com.customcamera.app.TEST_PIP" -> {
    lifecycleScope.launch {
        kotlinx.coroutines.delay(3000)  // Wait for camera ready
        togglePiP()                      // Enable PiP mode
        Log.i(TAG, "🧪 PiP mode enabled via test intent")
    }
}
```

**Use Cases**:
- Verify concurrent camera detection
- Test PiP UI integration
- Validate dual camera compositing

**Expected Logs**:
```
I ConcurrentCameraCapability: Found X concurrent camera combinations
I DualCameraPiPPlugin: PiP mode enabled
I CameraActivityEngine: 🧪 PiP mode enabled via test intent
```

**Verification**:
```bash
# Check for PiP activation
adb logcat -d | grep -E "PiP|concurrent"

# Expected: "PiP mode enabled" within 5 seconds of launch
```

---

### 3. TEST_CAPTURE - Automated Photo Capture

**Intent**: `com.customcamera.app.TEST_CAPTURE`

**Purpose**: Automatically capture a photo for testing

**Usage**:
```bash
adb shell am start -a com.customcamera.app.TEST_CAPTURE -n com.customcamera.app/.CameraActivityEngine
```

**What It Does** (`CameraActivityEngine.kt:202-228`):
1. Launches camera
2. Disables PiP mode if active (for cleaner test scenario)
3. Waits 5 seconds for camera binding (2s PiP + 3s camera)
4. Validates ImageCapture availability
5. Captures photo automatically
6. Saves to `/sdcard/DCIM/Camera/TIMESTAMP.jpg`

**Workflow**:
```kotlin
"com.customcamera.app.TEST_CAPTURE" -> {
    lifecycleScope.launch {
        // Disable PiP for simpler test
        if (isPiPCurrentlyEnabled) {
            togglePiP()
            delay(2000)
        }

        delay(3000)  // Wait for camera binding

        // Validate camera ready
        val imageCapture = cameraEngine.getImageCapture()
        if (imageCapture == null) {
            Log.e(TAG, "Camera not ready for capture")
            return@launch
        }

        capturePhoto()
        Log.i(TAG, "🧪 Photo captured via test intent")
    }
}
```

**Use Cases**:
- Verify photo capture pipeline
- Test file creation and gallery visibility
- Validate camera initialization timing

**Expected Logs**:
```
I CameraActivityEngine: 🧪 TEST_CAPTURE intent received
I CameraActivityEngine: 🧪 Disabling PiP mode for TEST_CAPTURE
I CameraActivityEngine: 🧪 Camera ready, capturing photo...
I CameraActivityEngine: 📸 Photo saved: /storage/emulated/0/DCIM/Camera/TIMESTAMP.jpg
I CameraActivityEngine: 🧪 Photo captured via test intent
```

**Verification**:
```bash
# List recent photos
adb shell ls -lt /sdcard/DCIM/Camera/ | head -5

# Pull photo for inspection
adb pull /sdcard/DCIM/Camera/$(adb shell ls -t /sdcard/DCIM/Camera/*.jpg | head -1) test_photo.jpg
```

**Critical Fix History**:
- **Commit 7872cccd**: Increased delay from 2s to 5s total
- **Issue**: Camera binding in PiP mode requires additional time
- **Solution**: Disable PiP + 5s total delay ensures reliable capture

---

### 4. TEST_VIDEO - Automated Video Recording

**Intent**: `com.customcamera.app.TEST_VIDEO`

**Purpose**: Automatically record a test video

**Usage**:
```bash
adb shell am start -a com.customcamera.app.TEST_VIDEO -n com.customcamera.app/.CameraActivityEngine
```

**What It Does** (`CameraActivityEngine.kt:229-288`):
1. Launches camera
2. Disables PiP mode (video recording unavailable in PiP)
3. Waits 3s for camera binding
4. **Enables AdvancedVideoRecordingPlugin** (defaults to disabled)
5. **Rebinds camera to activate VideoCapture UseCase**
6. Waits 2s for encoder initialization
7. Records for 6 seconds
8. Stops recording automatically
9. Saves to `/sdcard/DCIM/Camera/video_TIMESTAMP.mp4`

**Workflow**:
```kotlin
"com.customcamera.app.TEST_VIDEO" -> {
    lifecycleScope.launch {
        // Disable PiP (required for video)
        if (isPiPCurrentlyEnabled) {
            togglePiP()
            delay(2000)
        }

        delay(3000)  // Wait for camera binding

        // Enable plugin
        val plugin = advancedVideoRecordingPlugin
        plugin.enable()

        // CRITICAL: Rebind camera to activate VideoCapture
        val rebindConfig = CameraConfig(
            cameraIndex = cameraIndex,
            enablePreview = true,
            enableImageCapture = true,
            enableVideoCapture = true,
            enableImageAnalysis = false
        )
        cameraEngine.bindCamera(rebindConfig)
        delay(2000)

        // Start recording
        plugin.startRecording()
        delay(2000)  // Encoder initialization
        delay(6000)  // Record actual video

        // Stop recording
        plugin.stopRecording()
        delay(1000)  // Finalization

        Log.i(TAG, "🧪 Video recording completed")
    }
}
```

**Use Cases**:
- Verify video recording pipeline
- Test video file creation and gallery visibility
- Validate plugin enable/disable lifecycle
- Verify camera rebind after plugin state changes

**Expected Logs**:
```
I CameraActivityEngine: 🧪 TEST_VIDEO intent received
I CameraActivityEngine: 🧪 Disabling PiP mode for TEST_VIDEO
I CameraActivityEngine: 🧪 Enabling AdvancedVideoRecordingPlugin for test
I CameraActivityEngine: 🧪 Rebinding camera to activate VideoCapture...
I CameraActivityEngine: 🧪 Camera ready, starting video recording...
I CameraActivityEngine: 🧪 Video recording started, waiting for encoder initialization...
I CameraActivityEngine: 🧪 Recording now active, will record for 6 more seconds...
I CameraActivityEngine: 🧪 Stopping video recording...
I AdvancedVideoRecordingPlugin: Recording finalized successfully: file:///.../video_TIMESTAMP.mp4
I CameraActivityEngine: 🧪 Video recording completed via test intent
```

**Verification**:
```bash
# List recent videos
adb shell ls -lt /sdcard/DCIM/Camera/video_*.mp4 | head -3

# Check video file size (should be ~10-20MB for 6s recording)
adb shell ls -lh /sdcard/DCIM/Camera/video_*.mp4 | head -1

# Pull video for inspection
adb pull /sdcard/DCIM/Camera/$(adb shell ls -t /sdcard/DCIM/Camera/video_*.mp4 | head -1) test_video.mp4
```

**Critical Fix History**:
- **Commit 83b04687**: Fixed video save location (private → public DCIM)
- **Commit 21eb934d**: Added camera rebind after plugin.enable()
- **Issue**: Plugin state changes don't automatically trigger camera rebinding
- **Solution**: Explicit `bindCamera()` call after `plugin.enable()` activates VideoCapture UseCase

**Key Learning**: Plugin state changes require explicit camera rebinding to update active UseCases. This pattern applies to all plugins that affect camera configuration.

---

## Automated Test Scripts

### test-comprehensive-automated.sh

**Location**: `./test-comprehensive-automated.sh`
**Version**: 2.1 (Dynamic Screen Coordinates)
**Purpose**: Full app testing with 40+ test cases

**Features**:
- ✅ Dynamic screen coordinate calculation (device-independent)
- ✅ All 4 test intents
- ✅ Plugin verification (23 plugins)
- ✅ Settings persistence checks
- ✅ Screenshot capture
- ✅ Markdown + JSON test reports

**Usage**:
```bash
# Full test suite (~20 minutes)
./test-comprehensive-automated.sh

# View results
cat test-results-comprehensive-TIMESTAMP.md
```

**Dynamic Coordinate System** (v2.1):
```bash
# Query device screen size
get_screen_dimensions() {
    size=$(adb shell wm size | grep "Physical size")
    SCREEN_WIDTH=$(echo "$size" | cut -dx -f1)
    SCREEN_HEIGHT=$(echo "$size" | cut -dx -f2)
}

# Calculate tap coordinates from percentages
calc_tap_coord() {
    x_percent=$1  # 0-100
    y_percent=$2  # 0-100
    x=$((SCREEN_WIDTH * x_percent / 100))
    y=$((SCREEN_HEIGHT * y_percent / 100))
    echo "$x $y"
}

# Tap at percentage-based position
tap_at_percent() {
    coords=$(calc_tap_coord $1 $2)
    adb shell input tap $coords
}
```

**Example Usage**:
```bash
# Tap capture button (center-x 50%, bottom-y 92%)
tap_at_percent 50 92

# Tap camera selection (center-x 50%, upper-y 33%)
tap_at_percent 50 33

# Tap screen center for gestures
tap_at_percent 50 50
```

**Benefits**:
- Works on any screen size (phone, tablet, foldable)
- No hardcoded pixel coordinates
- Cached screen dimensions (query once per run)
- Clear percentage-based positioning

---

## Manual Testing Procedures

### Quick Smoke Test (3 minutes)

1. **Launch App**
   ```bash
   adb shell am start -a com.customcamera.app.TEST_CAMERA
   ```

2. **Verify Camera Preview**
   - Preview visible and updating
   - No black screen or freezes

3. **Test Photo Capture**
   ```bash
   adb shell am start -a com.customcamera.app.TEST_CAPTURE
   sleep 8
   adb shell ls -lt /sdcard/DCIM/Camera/ | head -1
   ```

4. **Test Video Recording**
   ```bash
   adb shell am start -a com.customcamera.app.TEST_VIDEO
   sleep 18
   adb shell ls -lt /sdcard/DCIM/Camera/video_*.mp4 | head -1
   ```

5. **Verify Gallery Visibility**
   - Open Gallery app
   - Navigate to Camera folder
   - Verify photo and video appear

### Full Feature Test (15 minutes)

See `memory/SETTINGS_TESTING_CHECKLIST.md` for comprehensive manual testing procedures covering:
- All 23 plugins
- Settings persistence
- UI interactions
- Edge cases

---

## Troubleshooting

### TEST_CAPTURE Issues

**Problem**: "Not bound to a valid Camera" error

**Solution**:
- Increase delay before capture (camera needs time to initialize)
- Disable PiP mode before capture
- Check ImageCapture availability before calling capture

**Logs to Check**:
```bash
adb logcat -d | grep -E "ImageCapture|Camera.*bound|TEST_CAPTURE"
```

---

### TEST_VIDEO Issues

**Problem**: "ERROR_NO_VALID_DATA" - recording stopped before data produced

**Causes**:
1. Plugin not enabled
2. VideoCapture UseCase inactive
3. Encoder initialization incomplete

**Solution**:
```kotlin
// 1. Enable plugin
plugin.enable()

// 2. Rebind camera to activate VideoCapture
cameraEngine.bindCamera(config)
delay(2000)

// 3. Start recording
plugin.startRecording()

// 4. Wait for encoder initialization
delay(2000)

// 5. Record actual video
delay(6000)

// 6. Stop recording
plugin.stopRecording()
```

**Logs to Check**:
```bash
adb logcat -d | grep -E "VideoCapture|ERROR_NO_VALID_DATA|Recording finalized"
```

**Common Log Patterns**:
- ✅ Good: `VideoCapture...ACTIVE`
- ❌ Bad: `VideoCapture...INACTIVE`
- ✅ Good: `Recording finalized successfully`
- ❌ Bad: `ERROR_NO_VALID_DATA`

---

### Dynamic Coordinate Issues

**Problem**: Taps missing UI elements

**Diagnosis**:
```bash
# Check detected screen size
adb shell wm size

# Test tap coordinates manually
adb shell input tap 540 1200  # Should tap screen center on 1080x2400 device
```

**Solution**:
- Verify percentage calculations are correct
- Check for status bar / navigation bar offsets
- Adjust Y percentages if UI has changed

---

### ADB Connection Issues

**Problem**: `adb: no devices/emulators found`

**Solutions**:
```bash
# USB connection
adb devices          # Verify device shows up
adb kill-server      # Restart ADB if needed
adb start-server

# Wireless ADB
adb tcpip 5555                    # Enable on device via USB
adb connect 192.168.1.XXX:5555   # Connect wirelessly
adb devices                       # Verify connection
```

---

## Best Practices

### Test Automation

1. **Always wait for initialization**: Camera needs time to bind, plugins need time to enable
2. **Use test intents for consistency**: Intents provide reproducible test scenarios
3. **Check logs, not just success**: Logs reveal timing issues and edge cases
4. **Verify file creation**: Don't trust success without checking actual output files
5. **Clean up test files**: Prevent storage bloat during repeated testing

### Adding New Test Intents

1. **Declare intent filter** in `AndroidManifest.xml`:
   ```xml
   <intent-filter>
       <action android:name="com.customcamera.app.TEST_YOUR_FEATURE" />
       <category android:name="android.intent.category.DEFAULT" />
   </intent-filter>
   ```

2. **Handle intent** in `CameraActivityEngine.kt` `onCreate()`:
   ```kotlin
   when (intent.action) {
       "com.customcamera.app.TEST_YOUR_FEATURE" -> {
           lifecycleScope.launch {
               delay(3000)  // Wait for camera ready
               // Your test logic here
               Log.i(TAG, "🧪 Test completed")
           }
       }
   }
   ```

3. **Document** in this guide with:
   - Purpose and use cases
   - ADB command
   - Expected workflow
   - Expected logs
   - Verification steps

---

## Summary

CustomCamera's testing infrastructure provides:

- **4 Test Intents**: Camera launch, PiP activation, photo capture, video recording
- **Autonomous Operation**: Zero manual interaction required
- **Device Independence**: Dynamic coordinates work on any screen size
- **Comprehensive Coverage**: 40+ automated test cases
- **Fast Execution**: Full suite in ~20 minutes
- **Clear Verification**: Logs + file creation + gallery visibility

**Next Steps**:
1. Run smoke test to verify current build
2. Execute full test suite before releases
3. Add new test intents as features expand
4. Maintain test documentation with code changes

---

**Document Version**: 1.0
**Last Updated**: 2025-11-16
**Contributors**: Claude Code (Autonomous Testing Infrastructure)
