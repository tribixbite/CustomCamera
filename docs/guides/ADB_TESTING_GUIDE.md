# ADB Testing Guide

## Quick Reference

### Testing Commands

```bash
# Normal camera launch
adb shell am start -a com.customcamera.app.TEST_CAMERA

# Launch with PiP mode enabled (dual camera)
adb shell am start -a com.customcamera.app.TEST_PIP

# Launch and auto-capture photo
adb shell am start -a com.customcamera.app.TEST_CAPTURE

# Capture screenshot
adb exec-out screencap -p > screenshot.png

# Check logs
adb logcat -d | grep "CameraActivityEngine\|DualCameraPiP"
```

### One-Line Test Scripts

```bash
# Test and screenshot normal mode
adb shell am start -a com.customcamera.app.TEST_CAMERA && sleep 3 && adb exec-out screencap -p > test_camera.png

# Test and screenshot PiP mode
adb shell am start -a com.customcamera.app.TEST_PIP && sleep 3 && adb exec-out screencap -p > test_pip.png

# Test photo capture
adb shell am start -a com.customcamera.app.TEST_CAPTURE && sleep 4 && adb exec-out screencap -p > test_capture.png
```

## Implementation Details

### Intent Filters (AndroidManifest.xml)

```xml
<activity
    android:name=".CameraActivityEngine"
    android:exported="true"
    android:screenOrientation="portrait">
    <intent-filter>
        <action android:name="com.customcamera.app.TEST_CAMERA" />
        <category android:name="android.intent.category.DEFAULT" />
    </intent-filter>
    <intent-filter>
        <action android:name="com.customcamera.app.TEST_PIP" />
        <category android:name="android.intent.category.DEFAULT" />
    </intent-filter>
    <intent-filter>
        <action android:name="com.customcamera.app.TEST_CAPTURE" />
        <category android:name="android.intent.category.DEFAULT" />
    </intent-filter>
</activity>
```

### Intent Handler (CameraActivityEngine.kt:182-210)

```kotlin
private fun handleTestIntent() {
    when (intent?.action) {
        "com.customcamera.app.TEST_CAMERA" -> {
            Log.i(TAG, "🧪 TEST_CAMERA intent received - camera will launch normally")
        }
        "com.customcamera.app.TEST_PIP" -> {
            Log.i(TAG, "🧪 TEST_PIP intent received - will enable PiP mode after camera starts")
            lifecycleScope.launch {
                kotlinx.coroutines.delay(2000) // Wait for camera to initialize
                val isEnabled = dualCameraPiPPlugin?.isPiPEnabled?.value ?: false
                if (!isEnabled) {
                    togglePiP()
                    Log.i(TAG, "🧪 PiP mode enabled via test intent")
                } else {
                    Log.i(TAG, "🧪 PiP mode already enabled, keeping it enabled")
                }
            }
        }
        "com.customcamera.app.TEST_CAPTURE" -> {
            Log.i(TAG, "🧪 TEST_CAPTURE intent received - will capture photo after camera starts")
            lifecycleScope.launch {
                kotlinx.coroutines.delay(2000) // Wait for camera to initialize
                capturePhoto()
                Log.i(TAG, "🧪 Photo captured via test intent")
            }
        }
    }
}
```

## Verified Features

### Material 3 Redesign ✅
- Purple active buttons (#6750A4)
- Gray inactive buttons (#424242)
- Material red 600 REC button
- Proper elevation and rounded corners
- Material typography with correct letter spacing

### PiP Black Camera Fix ✅
- Both cameras render correctly in PiP mode
- PERFORMANCE mode consistency (CameraActivityEngine.kt:128)
- No Z-order/rendering conflicts

### ADB Testing Infrastructure ✅
- State-aware intent handlers
- Auto-trigger after camera initialization
- Automated screenshot capture capability

## Troubleshooting

### LeakCanary Interference
If LeakCanary memory profiler blocks preview:
- This is a debug-only tool
- Does not affect release builds
- Can be disabled in build.gradle if needed

### Preview Not Showing
- Wait 3-4 seconds after launch before screenshot
- Check logs for "Preview stream state to STREAMING"
- Verify camera permissions granted

### PiP Mode Not Activating
- Check logs for "PiP mode enabled successfully"
- Verify device has multiple cameras
- Ensure 2-second delay allows camera initialization

## Version
Created: 2025-10-23
APK Version: v2.1.41-build.33
