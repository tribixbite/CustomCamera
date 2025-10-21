# Active TODOs - Settings & Plugin Testing

**Last Updated**: 2025-10-21
**Priority**: MEDIUM - Core camera issues resolved, forensics complete
**Status**: Camera working, diagnostic tools integrated, ready for feature work

## Current Session Context

Just completed:
- ✅ **CAMERA SYSTEM-WIDE FIX** - Identified Bixby Vision Framework dependency
- ✅ **FORENSIC ANALYSIS** - Root cause: OIS driver requires DL interface libraries
- ✅ Fixed premature camera closure during initialization
- ✅ Added getCurrentCameraState() method to prevent rapid rebinds
- ✅ Created DiagnosticOverlay.kt for real-time debugging
- ✅ **INTEGRATED DiagnosticOverlay** - 8-tap gesture to show/hide overlay
- ✅ Wired diagnostic overlay to camera state updates
- ✅ Created debug-camera.sh automated testing script
- ✅ Enhanced CameraEngine logging (system info, sensors, permissions)
- ✅ Documented camera fix in CAMERA_FIX_FORENSICS.md
- ✅ All 4 cameras verified working system-wide

## Immediate Next Steps (Priority Order)

### ✅ CAMERA ISSUES RESOLVED

**System-Wide Camera Fix:**
- Enabled `com.samsung.android.bixbyvision.framework` (provides OIS libraries)
- Enabled `com.samsung.android.bixby.agent` (supporting AI services)
- All 4 cameras now working in all apps

**App-Specific Camera Lifecycle Fix:**
- Added getCurrentCameraState() check to prevent rapid rebinds
- Modified onResume() to check camera state before switching
- Cameras now properly transition CLOSED → OPENING → OPEN

**Diagnostic Tools Created:**
- ✅ DiagnosticOverlay integrated with plugin dropdown (was 8-tap gesture)
- debug-camera.sh automated testing script
- Enhanced logging in CameraEngine
- CAMERA_FIX_FORENSICS.md documentation

### 🟡 HIGH PRIORITY - Feature Testing & Polish

1. **Test DiagnosticOverlay Integration**
   - Install latest APK
   - ✅ Moved from 8-tap gesture to plugin dropdown (v2.1.21)
   - Test plugin dropdown toggle to show/hide overlay
   - Verify camera state, sensor info, permissions display correctly
   - Check event log shows camera lifecycle events

2. **Camera Selector UI Review** (if issues exist)
   - Check for black spaces around UI elements
   - Verify navigation buttons work correctly
   - Confirm navigation flows to camera view

### 🟡 HIGH PRIORITY - Plugin UI Investigation

4. **Investigate Questionable Plugins** (see `memory/PLUGIN_UI_AUDIT.md`)
   - Check if **MotionDetectionPlugin** is continuous or one-shot
   - Check if **CropPlugin** is persistent frame or pre-shot setup
   - Check if **DualCameraPiPPlugin** toggle is redundant

5. **Decide on Plugin UI Patterns**
   - BarcodePlugin: Convert to action button? ❌ (definitely action-based)
   - QRScannerPlugin: Convert to action button? ❌ (definitely action-based)
   - Others: Keep as toggles or convert based on investigation

### 🟢 MEDIUM PRIORITY - Code Changes

6. **Convert Barcode/QRScanner to Action Buttons** (if user confirms)
   - Set `userToggleable = false` in BarcodePlugin
   - Set `userToggleable = false` in QRScannerPlugin
   - Add action buttons to CameraActivityEngine UI
   - Implement trigger methods for scanning mode

### 🔵 LOW PRIORITY - Automated Testing

7. **Write Automated Tests for Settings** (80 tests total)
   - Create test files in `app/src/test/java/com/customcamera/app/settings/`
   - Use template from `SETTINGS_TESTING_CHECKLIST.md`
   - Priority order: Photo/Video → Focus → Grid/Overlays → Advanced

8. **Write Automated Tests for Plugins** (44 tests total)
   - Create `PluginPersistenceTest.kt`
   - Test each plugin enable/disable state persists
   - Test plugin settings persist

## Detailed Testing Tasks (All Pending)

### Settings Testing (36 automated + 18 manual = 54 tests)

#### Camera Selection (2 settings)
- [ ] Test Main Camera Selection functionality
- [ ] Write automated test for Main Camera Selection
- [ ] Test PiP Camera Selection functionality
- [ ] Write automated test for PiP Camera Selection

#### Photo Settings (4 settings)
- [ ] Test Photo Quality slider functionality
- [ ] Write automated test for Photo Quality
- [ ] Test Photo Resolution dropdown functionality
- [ ] Write automated test for Photo Resolution
- [ ] Test Grid Overlay Default switch functionality
- [ ] Write automated test for Grid Overlay Default
- [ ] Test Flash Mode dropdown functionality ⭐ NEW
- [ ] Write automated test for Flash Mode ⭐ NEW

#### Video Settings (2 settings)
- [ ] Test Video Quality dropdown functionality
- [ ] Write automated test for Video Quality
- [ ] Test Video Stabilization switch functionality
- [ ] Write automated test for Video Stabilization

#### Focus Settings (2 settings)
- [ ] Test Auto Focus Mode dropdown functionality
- [ ] Write automated test for Auto Focus Mode
- [ ] Test Tap to Focus switch functionality
- [ ] Write automated test for Tap to Focus

#### Grid & Overlays (4 settings)
- [ ] Test Grid Type dropdown functionality
- [ ] Write automated test for Grid Type
- [ ] Test Camera Info Overlay switch functionality
- [ ] Write automated test for Camera Info Overlay
- [ ] Test Histogram Overlay switch functionality
- [ ] Write automated test for Histogram Overlay
- [ ] Test Level Indicator switch functionality ⭐ NEW
- [ ] Write automated test for Level Indicator ⭐ NEW

#### Manual Controls (3 settings)
- [ ] Test Manual Controls switch functionality
- [ ] Write automated test for Manual Controls
- [ ] Test Default Exposure slider functionality
- [ ] Write automated test for Default Exposure
- [ ] Test Exposure Lock switch functionality
- [ ] Write automated test for Exposure Lock

#### Advanced Settings (4 settings)
- [ ] Test Debug Logging switch functionality
- [ ] Write automated test for Debug Logging
- [ ] Test Performance Monitoring switch functionality
- [ ] Write automated test for Performance Monitoring
- [ ] Test Processing Interval slider functionality
- [ ] Write automated test for Processing Interval
- [ ] Test RAW Capture switch functionality
- [ ] Write automated test for RAW Capture

### Plugin Investigation & Changes
- [ ] Audit plugin dropdown for non-sensical toggles
- [ ] Investigate MotionDetectionPlugin (continuous vs one-shot)
- [ ] Investigate CropPlugin (persistent vs pre-shot)
- [ ] Investigate DualCameraPiPPlugin toggle redundancy
- [ ] Convert BarcodePlugin to action button (if confirmed)
- [ ] Convert QRScannerPlugin to action button (if confirmed)
- [ ] Add action buttons to camera UI for scanners

### Plugin Testing (44 automated + 22 manual = 66 tests)
- [ ] Test and verify each of 22 plugins works correctly
- [ ] Write automated persistence test for each of 22 plugins

## Test Files to Create

Priority order for automated test creation:

1. **app/src/test/java/com/customcamera/app/settings/CameraSelectionTest.kt**
   - testMainCameraIndexPersistence()
   - testPipCameraIndexPersistence()

2. **app/src/test/java/com/customcamera/app/settings/PhotoSettingsTest.kt**
   - testPhotoQualityPersistence()
   - testPhotoResolutionPersistence()

3. **app/src/test/java/com/customcamera/app/settings/FlashSettingsTest.kt** ⭐ NEW
   - testFlashModePersistence()
   - testFlashModeOptions() (auto/on/off/torch)

4. **app/src/test/java/com/customcamera/app/settings/VideoSettingsTest.kt**
   - testVideoQualityPersistence()
   - testVideoStabilizationPersistence()

5. **app/src/test/java/com/customcamera/app/settings/FocusSettingsTest.kt**
   - testAutoFocusModePersistence()
   - testTapToFocusPersistence()

6. **app/src/test/java/com/customcamera/app/settings/GridSettingsTest.kt**
   - testGridTypePersistence()
   - testGridOverlayDefaultPersistence()

7. **app/src/test/java/com/customcamera/app/settings/OverlaySettingsTest.kt**
   - testCameraInfoOverlayPersistence()
   - testHistogramOverlayPersistence()
   - testLevelIndicatorPersistence() ⭐ NEW

8. **app/src/test/java/com/customcamera/app/settings/ManualControlsTest.kt**
   - testManualControlsEnabledPersistence()
   - testDefaultExposurePersistence()
   - testExposureLockPersistence()

9. **app/src/test/java/com/customcamera/app/settings/AdvancedSettingsTest.kt**
   - testDebugLoggingPersistence()
   - testPerformanceMonitoringPersistence()
   - testProcessingIntervalPersistence()
   - testRawCapturePersistence()

10. **app/src/test/java/com/customcamera/app/plugins/PluginPersistenceTest.kt**
    - testPluginEnableDisablePersistence() (parametrized for all 22 plugins)

## Reference Documents

- **Testing Procedures**: `memory/SETTINGS_TESTING_CHECKLIST.md`
- **Plugin Analysis**: `memory/PLUGIN_UI_AUDIT.md`
- **Missing Settings**: `memory/MISSING_SETTINGS_AUDIT.md`
- **Existing Tests**: `app/src/test/README_TESTS.md`
- **Architecture**: `docs/ARCHITECTURE.md`

## Quick Commands

```bash
# Build and install
./build-and-install.sh

# Run all tests
./gradlew test

# Run specific test
./gradlew test --tests "PhotoSettingsTest"

# Check logs
adb logcat -d | grep "customcamera"

# View API log (in app)
Settings → Debug & System Info → View API Call Log
```

## Notes for Next Session

**What Was Done This Session**:
1. ✅ **Camera Fix Forensics** - Identified Bixby Vision Framework as root cause fix
2. ✅ **DiagnosticOverlay Plugin** - Moved from 8-tap gesture to plugin dropdown (23 plugins total)
3. ✅ Created CAMERA_FIX_FORENSICS.md documentation
4. ✅ Added DEBUG plugin category
5. Previous session: Found and fixed camera_0 preview bug (duplicate observers)
6. Previous session: Added Flash Mode and Level Indicator settings
7. Previous session: Created 120-test comprehensive testing framework

**What Needs Attention**:
1. ✅ Camera lifecycle fixed - all cameras open successfully
2. Fix camera selector UI issues (black spaces, navigation buttons, flow)
3. Decision on plugin UI patterns (toggle vs action) - 2 plugins identified
4. Implementation of action buttons if needed
5. User manual testing of all settings
6. Settings require INSTRUMENTED tests on device (SharedPreferences dependency)

**Open Questions**:
1. ✅ MotionDetectionPlugin: CONFIRMED continuous monitoring (toggle OK)
2. ✅ CropPlugin: CONFIRMED persistent frame (toggle OK)
3. Is DualCameraPiPPlugin toggle redundant with dedicated PiP button?
4. Should BarcodePlugin and QRScannerPlugin be action buttons?

**Build Status**: ✅ All changes compile successfully
**Test Status**: ⚠️ Manual testing required, automated tests pending
