# Manual Testing Guide - Plugin Management Features

**Version**: 2.3.8 (Build 40)
**Date**: November 27, 2025
**Features to Test**: Export/Import Plugin Configuration, Plugin Browser

## Pre-Test Verification ✅

### APK Installation
- **APK**: `v2.3.6-build40-20251127-083540` (76MB debug build)
- **Installation**: ✅ SUCCESS
- **Method**: `adb install ~/git/swype/CustomCamera/apk_downloads/app-debug.apk`
- **Status**: Installed on device `192.168.1.247:38579`

### Settings Screen Verification
- **Launch**: ✅ SUCCESS
- **Command**: `adb shell am start -n com.customcamera.app/.SettingsActivity`
- **Sections Created**: ✅ All 11 sections confirmed in logcat

```
11-27 03:57:39.371 I SettingsActivity:   Section 0: Camera Settings
11-27 03:57:39.371 I SettingsActivity:   Section 1: Focus Settings
11-27 03:57:39.371 I SettingsActivity:   Section 2: Manual Controls
11-27 03:57:39.371 I SettingsActivity:   Section 3: Grid & Overlays
11-27 03:57:39.371 I SettingsActivity:   Section 4: Video Settings
11-27 03:57:39.371 I SettingsActivity:   Section 5: Debug & Advanced
11-27 03:57:39.371 I SettingsActivity:   Section 6: Plugin Browser & Import ✅
11-27 03:57:39.371 I SettingsActivity:   Section 7: Plugin Control ✅
11-27 03:57:39.371 I SettingsActivity:   Section 8: Pixel Camera Style
11-27 03:57:39.371 I SettingsActivity:   Section 9: Samsung Camera Style
11-27 03:57:39.371 I SettingsActivity:   Section 10: About CustomCamera
```

---

## Test 1: Export Plugin Configuration

### Prerequisites
- App installed and Settings screen accessible
- Device storage permission granted (should auto-request)

### Steps
1. **Launch Settings**
   - Open CustomCamera app
   - Navigate to Settings (gear icon or menu)

2. **Navigate to Plugin Control Section**
   - Scroll down to find "Plugin Control" section (Section 7)
   - Should be below "Plugin Browser & Import" (Section 6)

3. **Tap Export Button**
   - Find button labeled "Export Plugin Configuration"
   - Tap the button

4. **Select Save Location**
   - System file picker should appear
   - Navigate to desired save location (e.g., Downloads, Documents)
   - Default filename format: `customcamera_plugins_YYYYMMDD_HHMMSS.json`

5. **Verify Export Success**
   - Toast notification should appear with message:
     ```
     Plugin configuration exported successfully
     File size: X.X KB
     ```

6. **Verify JSON File Created**
   - Navigate to save location using file manager
   - Confirm JSON file exists
   - Open file to verify structure (see Expected JSON Format below)

### Expected JSON Format
```json
{
  "version": "1.0",
  "appVersion": "2.3.8",
  "appBuild": 40,
  "exportDate": 1732732680000,
  "exportDateFormatted": "2025-11-27 04:58:00",
  "pluginStates": {
    "AutoFocus": true,
    "GridOverlay": false,
    "CameraInfo": true,
    "Barcode": false,
    ...
  },
  "cameraSettings": {
    "defaultCameraIndex": 0,
    "photoQuality": 95,
    "gridOverlay": "ruleOfThirds",
    "flashMode": "auto",
    "photoResolution": "high"
  },
  "videoSettings": {
    "videoQuality": "1080p",
    "videoStabilization": true
  },
  "focusSettings": {
    "autoFocusMode": "continuous",
    "tapToFocus": true
  },
  "advancedSettings": {
    "rawCapture": false,
    "histogramOverlay": false,
    "cameraInfoOverlay": false,
    "debugLogging": true,
    "performanceMonitoring": false,
    "levelIndicator": false
  }
}
```

### Success Criteria
- [  ] File picker opens successfully
- [  ] JSON file is created at selected location
- [  ] Toast notification appears with success message
- [  ] JSON file contains all expected sections
- [  ] File size is reasonable (typically 1-3 KB)
- [  ] Export date matches current timestamp
- [  ] App version and build number are correct

### Common Issues
- **No file picker appears**: Storage permission may not be granted
- **Toast shows error**: Check logcat for specific error message
- **File not found**: Check if app has write permission to selected directory

---

## Test 2: Import Plugin Configuration

### Prerequisites
- Exported JSON file from Test 1 (or any valid plugin configuration JSON)
- App installed and Settings screen accessible

### Steps
1. **Modify Some Plugin Settings** (to verify import works)
   - Change a few plugin states (enable/disable)
   - Change some camera settings
   - This helps verify that import actually restores settings

2. **Navigate to Plugin Control Section**
   - Scroll to "Plugin Control" section (Section 7)

3. **Tap Import Button**
   - Find button labeled "Import Plugin Configuration"
   - Tap the button

4. **Select JSON File**
   - System file picker should appear
   - Navigate to location of exported JSON file
   - Select the `.json` file

5. **Review Confirmation Dialog**
   - Dialog should appear with metadata:
     ```
     Import Plugin Configuration?

     App Version: 2.3.8
     Export Date: November 27, 2025 04:58:00

     This will replace your current plugin settings.
     ```
   - Two buttons: "Cancel" and "Import"

6. **Confirm Import**
   - Tap "Import" button

7. **Verify Import Success**
   - Toast notification should appear:
     ```
     Plugin configuration imported successfully
     Restored X plugins
     ```
   - Settings UI should refresh automatically
   - Plugin states should match exported configuration

8. **Verify Settings Restored**
   - Check plugin states match exported file
   - Check camera settings match exported file
   - Navigate through camera to verify plugins are active

### Success Criteria
- [  ] File picker opens successfully
- [  ] Can select `.json` file
- [  ] Confirmation dialog appears with correct metadata
- [  ] App version matches exported version (or shows warning if different)
- [  ] Export date is formatted correctly
- [  ] Import button applies changes
- [  ] Cancel button dismisses dialog without changes
- [  ] Toast notification appears on success
- [  ] UI refreshes after import
- [  ] All plugin states are restored correctly
- [  ] Camera/video/focus/advanced settings are restored

### Common Issues
- **Invalid file format**: JSON parsing error toast appears
- **Version mismatch**: Dialog should warn if versions don't match
- **No confirmation dialog**: Check logcat for errors
- **Settings not restored**: Verify JSON file structure matches expected format

---

## Test 3: Plugin Browser

### Prerequisites
- App installed and Settings screen accessible

### Steps
1. **Navigate to Plugin Browser Section**
   - Scroll to "Plugin Browser & Import" section (Section 6)

2. **Tap Plugin Browser Button**
   - Find button labeled "Plugin Browser"
   - Tap the button

3. **Review Plugin List**
   - Dialog/screen should appear titled "Built-in Plugins (23 total)"
   - Plugins should be grouped by category:
     - **OVERLAYS**: Grid Overlay, Camera Info, etc.
     - **ANALYSIS**: Histogram, Motion Detection, etc.
     - **AI**: Smart Scene, Object Detection, etc.
     - **CAPTURE**: HDR, Night Mode, RAW Capture, etc.

4. **Verify Status Indicators**
   - Enabled plugins: `✓ Plugin Name`
   - Disabled plugins: `○ Plugin Name`

5. **Tap a Plugin**
   - Tap any plugin in the list
   - Details dialog should appear showing:
     - Plugin name
     - Category
     - Current status (Enabled/Disabled)
     - Toggle button (if user-toggleable)

6. **Toggle Plugin** (if available)
   - Tap "Enable" or "Disable" button
   - Dialog should close
   - Browser list should refresh
   - Status indicator should update (✓ or ○)

7. **Close Plugin Browser**
   - Tap back button or dismiss dialog
   - Should return to Settings screen

### Expected Plugin Categories

**OVERLAYS**:
- Grid Overlay
- Camera Info
- Crop Overlay

**ANALYSIS**:
- Histogram
- Motion Detection
- Exposure Analysis
- Sharpness Analysis

**AI**:
- Smart Scene Detection
- Smart Adjustments
- Object Detection

**CAPTURE**:
- HDR
- Night Mode
- RAW Capture
- Advanced Video Recording
- Dual Camera PiP

**CONTROLS**:
- Auto Focus
- Exposure Control
- Manual Focus
- Pro Controls

**SCANNING**:
- Barcode Scanner
- QR Scanner

### Success Criteria
- [  ] Plugin browser opens successfully
- [  ] All 23 plugins are listed
- [  ] Plugins are grouped by category
- [  ] Category headers are visually distinct
- [  ] Status indicators (✓/○) are accurate
- [  ] Tapping a plugin shows details dialog
- [  ] Toggle button works for user-toggleable plugins
- [  ] List refreshes after toggling
- [  ] Back button returns to Settings

### Common Issues
- **Wrong plugin count**: Should be exactly 23 plugins
- **Missing categories**: All categories should be present
- **Toggle not working**: Some plugins are system-managed and can't be toggled
- **Status incorrect**: Check if plugin state in PluginRegistry matches display

---

## Additional Verification Tests

### Test 4: Export/Import Round-Trip
1. Export configuration (Test 1)
2. Modify several plugin states manually
3. Import previously exported configuration (Test 2)
4. Verify all changes are reverted to exported state

### Test 5: Configuration Sharing
1. Export configuration on Device A
2. Transfer JSON file to Device B (via email, cloud, etc.)
3. Import configuration on Device B
4. Verify same plugin states on both devices

### Test 6: Error Handling

#### Invalid JSON File
1. Create a text file with invalid JSON
2. Try to import it
3. Expected: Error toast "Invalid plugin configuration file"

#### Corrupted JSON Structure
1. Edit exported JSON and remove required fields
2. Try to import it
3. Expected: Error toast with specific missing field

#### Wrong File Type
1. Try to import a .txt or .png file
2. Expected: File picker should filter to .json only

---

## Logcat Monitoring

### Useful Commands

**Monitor plugin-related logs**:
```bash
adb logcat -s SettingsActivity:* PluginRegistry:* PluginManager:*
```

**Check export operation**:
```bash
adb logcat | grep -i "export.*plugin"
```

**Check import operation**:
```bash
adb logcat | grep -i "import.*plugin"
```

**Check plugin browser**:
```bash
adb logcat | grep -i "plugin.*browser"
```

---

## Automated Testing Checklist

### Build Verification
- ✅ CI/CD build passed (run 19729908671)
- ✅ Both APKs created (debug 79.5MB, release 76.8MB)
- ✅ Release published to GitHub
- ✅ APK installable via ADB

### Code Quality
- ✅ Modern Kotlin with null safety
- ✅ ViewBinding used throughout
- ✅ Proper coroutine usage (Dispatchers.IO)
- ✅ Error handling in place
- ✅ StateFlow reactive architecture

### Settings Screen
- ✅ All 11 sections created
- ✅ New sections (6, 7) present
- ✅ No crashes on launch
- ✅ Logs show proper initialization

---

## Test Results Template

```markdown
## Test Session: [Date]
**Tester**: [Name]
**Device**: [Device Model + Android Version]
**APK Version**: 2.3.8 (Build 40)

### Test 1: Export Plugin Configuration
- [ ] Passed  |  [X] Failed  |  [ ] Skipped
**Notes**:


### Test 2: Import Plugin Configuration
- [ ] Passed  |  [ ] Failed  |  [ ] Skipped
**Notes**:


### Test 3: Plugin Browser
- [ ] Passed  |  [ ] Failed  |  [ ] Skipped
**Notes**:


### Issues Found
1.
2.

### Additional Observations
-
```

---

## Next Steps After Manual Testing

1. **If all tests pass**:
   - Mark P2 (Manual Testing) as complete
   - Move to User Acceptance Testing
   - Prepare for Play Store submission

2. **If issues found**:
   - Document specific issues with logcat output
   - Create bug report for each issue
   - Prioritize and fix critical issues
   - Re-test after fixes

3. **Future Enhancements (P3)**:
   - Plugin usage statistics
   - Plugin crash reporting
   - Cloud sync for configurations
   - QR code for configuration sharing
   - Plugin recommendations

---

**Last Updated**: 2025-11-27 04:00 UTC
**Status**: Ready for Manual Testing
**CI/CD**: All builds passing ✅
