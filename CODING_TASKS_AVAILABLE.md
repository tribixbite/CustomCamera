# Available Coding Tasks

**Created**: 2025-10-23  
**Status**: Ready to implement  
**Priority Levels**: P0 (Critical), P1 (High), P2 (Medium), P3 (Low)

---

## P0 - CRITICAL (UI/UX Issues)

### 1. Fix Status Bar Visibility in Fullscreen Mode
**File**: `CameraActivityEngine.kt:176-195`  
**Issue**: Status bar appears in screenshots (should be fully hidden in immersive mode)  
**Impact**: Breaks fullscreen camera experience  
**Effort**: 30 minutes  
**Evidence**: test_capture.png shows status bar at top

**Fix Options**:
- Verify SYSTEM_UI_FLAG_FULLSCREEN is properly set
- Add window.setDecorFitsSystemWindows(false) for Android 11+
- Ensure flags persist after resume/configuration changes

---

## P1 - HIGH PRIORITY (Functionality Gaps)

### 2. Implement Device Capability Checking (18 plugins affected)
**Files**: All plugins (BarcodePlugin, HDRPlugin, CameraInfoPlugin, etc.)  
**Current**: All plugins have `// TODO: Add device capability checking if needed`  
**Issue**: Plugins may crash on devices lacking required hardware  
**Impact**: App instability on certain devices  
**Effort**: 2-3 hours

**What to implement**:
```kotlin
override fun checkCapabilities(context: CameraContext): PluginCapabilityResult {
    return when {
        !hasRequiredHardware() -> PluginCapabilityResult.Unsupported("Hardware not available")
        !hasRequiredAPI() -> PluginCapabilityResult.Unsupported("API level too low")
        else -> PluginCapabilityResult.Supported
    }
}
```

**Affected Plugins**:
- BarcodePlugin (ML Kit)
- HDRPlugin (HDR capability)
- RAWCapturePlugin (RAW format support)
- DualCameraPiPPlugin (concurrent camera)
- NightModePlugin (low-light capture)
- All 18 plugins with the TODO

---

### 3. Implement Actual RAW Capture (RAWCapturePlugin)
**File**: `RAWCapturePlugin.kt`  
**Current**: Mock implementation with TODOs  
**TODOs**:
- Line ~180: "TODO: Implement actual RAW capture using ImageCapture with RAW format"
- Line ~200: "TODO: Implement dual capture"
- Line ~250: "TODO: Implement proper conversion from ImageInfo to TotalCaptureResult"

**Impact**: RAW capture feature non-functional  
**Effort**: 4-6 hours

**Implementation needed**:
- Configure ImageCapture with RAW format
- Handle DNG file creation
- Implement dual JPEG+RAW capture
- Extract proper metadata from TotalCaptureResult

---

### 4. Implement App Restart Logic (ErrorHandlingManager)
**File**: `ErrorHandlingManager.kt`  
**Current**: `// TODO: Implement app restart logic`  
**Impact**: Recovery strategy incomplete  
**Effort**: 1 hour

**What to add**:
```kotlin
private fun restartApp() {
    val intent = context.packageManager
        .getLaunchIntentForPackage(context.packageName)
    intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
    Runtime.getRuntime().exit(0)
}
```

---

## P2 - MEDIUM PRIORITY (Code Quality)

### 5. Add Comprehensive Logging to CameraEngine
**File**: `CameraEngine.kt`  
**Current**: Basic logging exists but could be enhanced  
**Impact**: Easier debugging and diagnostics  
**Effort**: 1-2 hours

**What to add**:
- Performance metrics (frame processing time)
- State transition logging (OPENING → OPEN → CLOSING)
- Plugin execution timing
- Memory usage tracking

---

### 6. Implement Video Recording Quality Presets
**File**: `AdvancedVideoRecordingPlugin.kt`  
**Current**: Hardcoded quality settings  
**Enhancement**: Add preset system (1080p60, 4K30, 4K60, etc.)  
**Effort**: 2 hours

**What to add**:
```kotlin
enum class VideoQualityPreset {
    HD_720P_30(1280, 720, 30),
    FULL_HD_1080P_30(1920, 1080, 30),
    FULL_HD_1080P_60(1920, 1080, 60),
    UHD_4K_30(3840, 2160, 30),
    UHD_4K_60(3840, 2160, 60),
    UHD_8K_30(7680, 4320, 30)
}
```

---

### 7. Add Haptic Feedback for All Major Actions
**File**: `CameraActivityEngine.kt`  
**Current**: Haptic feedback exists for some actions  
**Enhancement**: Ensure all buttons have appropriate haptic feedback  
**Effort**: 1 hour

**Actions needing haptic**:
- Photo capture (already has)
- Video start/stop (add)
- Camera switch (add)
- Flash toggle (add)
- Plugin toggle (add)
- Settings open (add)

---

### 8. Improve Error Messages (User-Friendly)
**Files**: Throughout codebase  
**Current**: Technical error messages (e.g., "BufferQueue has been abandoned")  
**Enhancement**: User-friendly error messages  
**Effort**: 2-3 hours

**Examples**:
```kotlin
// Current
"Camera binding failed: UseCase limit exceeded"

// Better
"Cannot enable PiP mode while recording video. Please stop recording first."
```

---

### 9. Add Loading Indicators for Slow Operations
**File**: `CameraActivityEngine.kt`  
**Current**: LoadingIndicatorManager exists but underutilized  
**Enhancement**: Show loading for camera switch, plugin enable, mode changes  
**Effort**: 1-2 hours

---

### 10. Implement Settings Export/Import
**File**: `SettingsManager.kt`  
**Current**: Settings saved to SharedPreferences  
**Enhancement**: Allow users to export/import settings as JSON  
**Effort**: 2-3 hours

**Features**:
- Export all settings to JSON file
- Import settings from JSON
- Share settings between devices
- Backup/restore functionality

---

## P3 - LOW PRIORITY (Nice to Have)

### 11. Add Accessibility Improvements
**Files**: All UI components  
**Current**: Basic contentDescription on some buttons  
**Enhancement**: Complete accessibility support  
**Effort**: 3-4 hours

**What to add**:
- Content descriptions for all interactive elements
- Proper focus order
- Announce state changes (TalkBack)
- Support for large text
- High contrast mode support

---

### 12. Add Gesture Tutorial (First Run)
**File**: `CameraActivityEngine.kt`  
**Current**: GestureHintsOverlay exists (6-tap to show)  
**Enhancement**: Show automatically on first run  
**Effort**: 1 hour

**Implementation**:
```kotlin
private fun showGestureTutorialIfFirstRun() {
    val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    if (!prefs.getBoolean("gesture_tutorial_shown", false)) {
        gestureHintsOverlay?.visibility = View.VISIBLE
        prefs.edit().putBoolean("gesture_tutorial_shown", true).apply()
    }
}
```

---

### 13. Add Photo/Video Gallery Preview in App
**File**: `GalleryActivity.kt` (if exists) or create new  
**Current**: Gallery button opens system gallery  
**Enhancement**: In-app gallery with swipe, delete, share  
**Effort**: 6-8 hours

---

### 14. Implement Smart Scene Detection Actions
**File**: `SmartScenePlugin.kt`  
**Current**: Detects scenes but no auto-actions  
**Enhancement**: Auto-adjust settings based on scene  
**Effort**: 2-3 hours

**Examples**:
- Landscape detected → Enable grid overlay, increase sharpness
- Portrait detected → Enable face detection, soften skin tones
- Food detected → Increase saturation, warm color temperature
- Document detected → Increase contrast, enable crop grid

---

### 15. Add Watermark Feature
**Files**: New `WatermarkPlugin.kt`  
**Enhancement**: Add text/image watermark to photos  
**Effort**: 4-5 hours

**Features**:
- Custom text watermark
- Image watermark
- Position selection (corners, center)
- Opacity control
- Font/size customization

---

### 16. Implement Burst Mode
**Files**: New `BurstModePlugin.kt` or enhance `CameraActivityEngine.kt`  
**Current**: Single photo capture only  
**Enhancement**: Capture multiple photos rapidly (burst mode)  
**Effort**: 3-4 hours

**Features**:
- Configurable burst count (3, 5, 10 shots)
- Configurable interval
- Best shot selection (sharpness-based)
- Save all or best only

---

### 17. Add Timer/Self-Timer
**File**: `CameraActivityEngine.kt`  
**Current**: No timer feature  
**Enhancement**: 3s/5s/10s countdown timer  
**Effort**: 2 hours

---

### 18. Implement Focus Peaking Enhancements
**File**: `FocusPeakingOverlay.kt`  
**Current**: Basic focus peaking exists  
**Enhancement**: Color options, sensitivity adjustment  
**Effort**: 1-2 hours

---

### 19. Add Histogram Analysis Actions
**File**: `HistogramPlugin.kt`  
**Current**: Shows histogram overlay  
**Enhancement**: Auto-exposure adjustment based on histogram  
**Effort**: 2-3 hours

**Features**:
- Detect overexposure (clipping highlights)
- Detect underexposure (clipping shadows)
- Suggest exposure compensation
- Auto-apply adjustment (optional)

---

### 20. Implement Pro Video Features
**File**: `AdvancedVideoRecordingPlugin.kt`  
**Enhancement**: Add pro video controls  
**Effort**: 4-6 hours

**Features**:
- Manual ISO during recording
- Manual focus pull
- Zebra stripes (overexposure warning)
- Audio level meters
- Bitrate control
- Frame rate override

---

## Quick Wins (< 1 hour each)

### 21. Add Version Info to Settings
**File**: `SettingsActivity.kt`  
**Add**: App version, build number, build date  
**Effort**: 15 minutes

### 22. Add "About" Screen
**File**: New `AboutActivity.kt`  
**Content**: Credits, licenses, GitHub link  
**Effort**: 30 minutes

### 23. Improve Toast Messages
**Files**: Throughout codebase  
**Current**: Plain toasts  
**Enhancement**: Use EnhancedToast everywhere  
**Effort**: 30-45 minutes

### 24. Add Screenshot Detection
**Enhancement**: Detect when user takes screenshot, offer to save  
**Effort**: 30 minutes

### 25. Add Shake to Undo
**Enhancement**: Shake device to undo last action  
**Effort**: 45 minutes

---

## Technical Debt Items

### 26. Remove Deprecated Code
**Search for**: `@Deprecated`, `@Suppress("DEPRECATION")`  
**Effort**: 1-2 hours

### 27. Update Dependencies
**File**: `build.gradle`  
**Current**: Check for outdated dependencies  
**Effort**: 1 hour + testing

### 28. Add ProGuard Rules for Release
**File**: `proguard-rules.pro`  
**Current**: May need optimization  
**Effort**: 1-2 hours

### 29. Optimize APK Size
**Actions**: Remove unused resources, enable shrinking, compress images  
**Effort**: 2-3 hours

### 30. Add Crashlytics/Analytics
**Enhancement**: Firebase Crashlytics for crash reporting  
**Effort**: 2 hours

---

## RECOMMENDED PRIORITY ORDER

**Start Here** (High impact, reasonable effort):

1. **Fix Status Bar Visibility** (P0, 30min) ← Start here
2. **Implement App Restart Logic** (P1, 1hr)
3. **Add Haptic Feedback** (P2, 1hr)
4. **Add Version Info** (Quick win, 15min)
5. **Improve Toast Messages** (Quick win, 45min)
6. **Device Capability Checking** (P1, 2-3hrs) ← High value
7. **Implement RAW Capture** (P1, 4-6hrs)
8. **Settings Export/Import** (P2, 2-3hrs)
9. **Timer/Self-Timer** (P3, 2hrs)
10. **In-App Gallery** (P3, 6-8hrs)

---

**Total Available Tasks**: 30+  
**Estimated Total Effort**: 60-80 hours  
**Quick Wins Available**: 5 tasks (< 1 hour each)

---

**Ready to implement any of these! Which would you like to start with?**
