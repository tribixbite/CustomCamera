# Settings Testing Checklist - SimpleSettingsActivity

## Testing Requirements
Each setting requires **2 tests minimum**:
1. **Manual functional test** - Verify UI behavior works correctly
2. **Automated unit test** - Verify persistence and state management

## Settings Inventory (18 functional settings)

### 📷 Camera Selection (2 settings)

#### 1. Main Camera Selection
- [ ] **Manual Test**: Select each camera (0-3), verify camera switches in preview
- [ ] **Automated Test**: `testMainCameraIndexPersistence()` - Set index, restart, verify persisted
- **Expected**: Camera switches immediately, selection persists across app restarts
- **Test File**: `app/src/test/java/com/customcamera/app/settings/CameraSelectionTest.kt`

#### 2. PiP Camera Selection
- [ ] **Manual Test**: Select PiP camera, enable PiP mode, verify correct camera appears in PiP
- [ ] **Automated Test**: `testPipCameraIndexPersistence()` - Set PiP index, restart, verify persisted
- **Expected**: PiP camera selection persists, correct camera shown in dual mode
- **Test File**: `app/src/test/java/com/customcamera/app/settings/PiPCameraTest.kt`

---

### 📸 Photo Settings (4 settings)

#### 3. Photo Quality (Slider: 1-100%)
- [ ] **Manual Test**: Set quality to 50%, 100%, capture photos, verify file sizes differ
- [ ] **Automated Test**: `testPhotoQualityPersistence()` - Set 75%, restart, verify 75%
- **Expected**: Lower quality = smaller JPEG file size, setting persists
- **Test File**: `app/src/test/java/com/customcamera/app/settings/PhotoSettingsTest.kt`

#### 4. Photo Resolution (Dropdown)
- [ ] **Manual Test**: Select 4K, 1080p, 720p, capture photos, verify resolution in EXIF
- [ ] **Automated Test**: `testPhotoResolutionPersistence()` - Set "4k", restart, verify "4k"
- **Expected**: Photos captured at selected resolution, setting persists
- **Test File**: `app/src/test/java/com/customcamera/app/settings/PhotoSettingsTest.kt`

#### 5. Grid Overlay Default (Switch)
- [ ] **Manual Test**: Enable, close app, reopen, verify grid appears automatically
- [ ] **Automated Test**: `testGridOverlayDefaultPersistence()` - Set true, restart, verify true
- **Expected**: Grid shows on startup when enabled, setting persists
- **Test File**: `app/src/test/java/com/customcamera/app/settings/GridSettingsTest.kt`

#### 6. Flash Mode (Dropdown)
- [ ] **Manual Test**: Select Auto/On/Off/Torch, verify flash behavior in low light
- [ ] **Automated Test**: `testFlashModePersistence()` - Set "torch", restart, verify "torch"
- **Expected**: Flash behaves according to mode, setting persists
- **Test File**: `app/src/test/java/com/customcamera/app/settings/FlashSettingsTest.kt`

---

### 🎥 Video Settings (2 settings)

#### 7. Video Quality (Dropdown)
- [ ] **Manual Test**: Select 4K, 1080p, 720p, record video, verify resolution
- [ ] **Automated Test**: `testVideoQualityPersistence()` - Set "4k", restart, verify "4k"
- **Expected**: Videos recorded at selected quality, setting persists
- **Test File**: `app/src/test/java/com/customcamera/app/settings/VideoSettingsTest.kt`

#### 8. Video Stabilization (Switch)
- [ ] **Manual Test**: Enable, record while walking, compare with disabled (should be smoother)
- [ ] **Automated Test**: `testVideoStabilizationPersistence()` - Set true, restart, verify true
- **Expected**: EIS enabled reduces shake, setting persists
- **Test File**: `app/src/test/java/com/customcamera/app/settings/VideoSettingsTest.kt`

---

### 🎯 Focus Settings (2 settings)

#### 9. Auto Focus Mode (Dropdown)
- [ ] **Manual Test**: Select Continuous/Single/Manual, verify focus behavior differs
- [ ] **Automated Test**: `testAutoFocusModePersistence()` - Set "single", restart, verify "single"
- **Expected**: Focus behavior matches mode, setting persists
- **Test File**: `app/src/test/java/com/customcamera/app/settings/FocusSettingsTest.kt`

#### 10. Tap to Focus (Switch)
- [ ] **Manual Test**: Enable, tap preview, verify focus; Disable, tap, verify no focus
- [ ] **Automated Test**: `testTapToFocusPersistence()` - Set true, restart, verify true
- **Expected**: Tap focus works when enabled only, setting persists
- **Test File**: `app/src/test/java/com/customcamera/app/settings/FocusSettingsTest.kt`

---

### 📐 Grid & Overlays (4 settings)

#### 11. Grid Type (Dropdown)
- [ ] **Manual Test**: Select each type (Rule of Thirds/Golden Ratio/etc), verify grid changes
- [ ] **Automated Test**: `testGridTypePersistence()` - Set "GOLDEN_RATIO", restart, verify
- **Expected**: Grid style changes visually, setting persists
- **Test File**: `app/src/test/java/com/customcamera/app/settings/GridSettingsTest.kt`

#### 12. Camera Info Overlay (Switch)
- [ ] **Manual Test**: Enable, verify camera info appears; Disable, verify hidden
- [ ] **Automated Test**: `testCameraInfoOverlayPersistence()` - Set true, restart, verify true
- **Expected**: Info overlay visibility matches setting, persists
- **Test File**: `app/src/test/java/com/customcamera/app/settings/OverlaySettingsTest.kt`

#### 13. Histogram Overlay (Switch)
- [ ] **Manual Test**: Enable, verify histogram appears; Disable, verify hidden
- [ ] **Automated Test**: `testHistogramOverlayPersistence()` - Set true, restart, verify true
- **Expected**: Histogram visibility matches setting, persists
- **Test File**: `app/src/test/java/com/customcamera/app/settings/OverlaySettingsTest.kt`

#### 14. Level Indicator (Switch)
- [ ] **Manual Test**: Enable, tilt device, verify level indicator appears and responds
- [ ] **Automated Test**: `testLevelIndicatorPersistence()` - Set true, restart, verify true
- **Expected**: Level indicator visible when enabled, persists
- **Test File**: `app/src/test/java/com/customcamera/app/settings/OverlaySettingsTest.kt`

---

### 🎛️ Manual Controls (3 settings)

#### 15. Enable Manual Controls (Switch)
- [ ] **Manual Test**: Enable, verify pro controls UI appears; Disable, verify hidden
- [ ] **Automated Test**: `testManualControlsEnabledPersistence()` - Set true, restart, verify true
- **Expected**: Pro controls visibility matches setting, persists
- **Test File**: `app/src/test/java/com/customcamera/app/settings/ManualControlsTest.kt`

#### 16. Default Exposure Compensation (Slider: -6 to +6)
- [ ] **Manual Test**: Set -3, 0, +3, verify preview brightness changes
- [ ] **Automated Test**: `testDefaultExposurePersistence()` - Set 3, restart, verify 3
- **Expected**: Exposure adjusts on startup, setting persists
- **Test File**: `app/src/test/java/com/customcamera/app/settings/ManualControlsTest.kt`

#### 17. Exposure Lock (Switch)
- [ ] **Manual Test**: Enable, verify exposure locks at startup; Disable, verify auto-adjust
- [ ] **Automated Test**: `testExposureLockPersistence()` - Set true, restart, verify true
- **Expected**: Exposure lock state at startup matches setting, persists
- **Test File**: `app/src/test/java/com/customcamera/app/settings/ManualControlsTest.kt`

---

### 🔧 Advanced Settings (4 settings)

#### 18. Debug Logging (Switch)
- [ ] **Manual Test**: Enable, check logcat for debug logs; Disable, verify logs reduced
- [ ] **Automated Test**: `testDebugLoggingPersistence()` - Set true, restart, verify true
- **Expected**: Debug verbosity matches setting, persists
- **Test File**: `app/src/test/java/com/customcamera/app/settings/AdvancedSettingsTest.kt`

#### 19. Performance Monitoring (Switch)
- [ ] **Manual Test**: Enable, verify FPS/memory overlay appears; Disable, verify hidden
- [ ] **Automated Test**: `testPerformanceMonitoringPersistence()` - Set true, restart, verify true
- **Expected**: Performance overlay visibility matches setting, persists
- **Test File**: `app/src/test/java/com/customcamera/app/settings/AdvancedSettingsTest.kt`

#### 20. Processing Interval (Slider: 100-5000ms)
- [ ] **Manual Test**: Set 100ms, 5000ms, verify plugin processing frequency changes
- [ ] **Automated Test**: `testProcessingIntervalPersistence()` - Set 2000, restart, verify 2000
- **Expected**: Processing interval adjusts, setting persists
- **Test File**: `app/src/test/java/com/customcamera/app/settings/AdvancedSettingsTest.kt`

#### 21. RAW Capture (Switch)
- [ ] **Manual Test**: Enable, capture photo, verify DNG file created alongside JPEG
- [ ] **Automated Test**: `testRawCapturePersistence()` - Set true, restart, verify true
- **Expected**: DNG files saved when enabled, setting persists
- **Test File**: `app/src/test/java/com/customcamera/app/settings/AdvancedSettingsTest.kt`

---

## Plugin Settings (22 plugins)

### Test Requirements for Each Plugin
Each plugin requires **2 tests minimum**:
1. **Manual functional test** - Enable plugin, verify functionality works
2. **Automated unit test** - Verify enable/disable state persists

### OVERLAYS (1 plugin)
- [ ] **GridOverlayPlugin**: Test grid display, persistence

### ANALYSIS (7 plugins)
- [ ] **BarcodePlugin**: ⚠️ Should be ACTION button (see PLUGIN_UI_AUDIT.md)
- [ ] **HistogramPlugin**: Test histogram display, persistence
- [ ] **CameraInfoPlugin**: Test info display, persistence
- [ ] **ExposureAnalysisPlugin**: Test exposure analysis, persistence
- [ ] **MotionDetectionPlugin**: Test motion detection, persistence
- [ ] **QRScannerPlugin**: ⚠️ Should be ACTION button (see PLUGIN_UI_AUDIT.md)
- [ ] **SharpnessAnalysisPlugin**: Test sharpness analysis, persistence

### CONTROLS (4 plugins)
- [ ] **AutoFocusPlugin**: Test auto-focus behavior, persistence
- [ ] **ExposureControlPlugin**: Test exposure controls, persistence
- [ ] **ManualFocusPlugin**: Test manual focus, persistence
- [ ] **ProControlsPlugin**: Test pro controls UI, persistence

### AI (3 plugins)
- [ ] **SmartScenePlugin**: Test scene detection, persistence
- [ ] **SmartAdjustmentsPlugin**: Test auto-adjustments, persistence
- [ ] **ObjectDetectionPlugin**: Test object detection, persistence

### CAPTURE (7 plugins)
- [ ] **CropPlugin**: Test crop functionality, persistence
- [ ] **DualCameraPiPPlugin**: ⚠️ Has dedicated button (check redundancy)
- [ ] **RAWCapturePlugin**: Test RAW capture, persistence
- [ ] **AdvancedVideoRecordingPlugin**: Test advanced video, persistence
- [ ] **NightModePlugin**: Test night mode, persistence
- [ ] **HDRPlugin**: Test HDR processing, persistence

---

## Test Implementation Status

### Automated Tests to Write
Create test files in `app/src/test/java/com/customcamera/app/settings/`:

1. `CameraSelectionTest.kt` - Main and PiP camera tests
2. `PhotoSettingsTest.kt` - Quality and resolution tests
3. `GridSettingsTest.kt` - Grid overlay and type tests
4. `FlashSettingsTest.kt` - Flash mode tests
5. `VideoSettingsTest.kt` - Video quality and stabilization tests
6. `FocusSettingsTest.kt` - Focus mode and tap-to-focus tests
7. `OverlaySettingsTest.kt` - Info, histogram, level indicator tests
8. `ManualControlsTest.kt` - Manual controls, exposure, lock tests
9. `AdvancedSettingsTest.kt` - Debug, performance, interval, RAW tests
10. `PluginPersistenceTest.kt` - All 22 plugin enable/disable tests

### Test Template
```kotlin
@Test
fun testSettingNamePersistence() {
    // Set value
    settingsManager.setSettingName(testValue)

    // Verify StateFlow updated
    assertEquals(testValue, settingsManager.settingName.value)

    // Verify SharedPreferences persisted
    val preferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    assertEquals(testValue, preferences.getType("KEY_SETTING_NAME", defaultValue))
}
```

---

## Manual Testing Procedure

### Pre-Test Setup
1. Install clean APK: `adb install -r app/build/outputs/apk/debug/app-debug.apk`
2. Clear app data: `adb shell pm clear com.customcamera.app`
3. Launch app and grant camera/storage permissions

### Testing Each Setting
1. Navigate to Settings
2. Change setting value
3. Exit settings (verify Toast "Setting updated")
4. Verify behavior in camera view
5. Close and reopen app
6. Navigate to Settings
7. Verify setting value persisted
8. Check `adb logcat` for any errors

### Reporting Issues
If a setting fails:
1. Note the setting name
2. Describe expected vs actual behavior
3. Check logcat for errors: `adb logcat -d | grep -i error`
4. Capture screenshot if UI issue
5. Create GitHub issue with details

---

## Summary

**Total Tests Required**:
- **36 automated tests** (18 settings × 2 tests each)
- **44 automated tests** (22 plugins × 2 tests each)
- **Total: 80 automated tests**

**Manual Testing**:
- All 18 settings require manual verification
- All 22 plugins require manual verification
- **Total: 40 manual tests**

**Priority**:
1. **HIGH**: Settings that affect capture (quality, resolution, flash, focus)
2. **MEDIUM**: UI/overlay settings (grids, info, histogram, level)
3. **LOW**: Debug/advanced settings (logging, performance monitor)

**See Also**:
- `memory/PLUGIN_UI_AUDIT.md` - Plugin toggle vs action button analysis
- `app/src/test/README_TESTS.md` - Existing test infrastructure
