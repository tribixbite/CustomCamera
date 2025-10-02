# CustomCamera Outstanding Issues

**Generated:** 2025-10-02
**Status:** 76 compilation errors identified

## Critical Compilation Errors

### 1. AdvancedISOControlPlugin.kt
- **Lines 287, 329, 336, 402, 433**: Unresolved reference to `launch` - missing `coroutineScope.` prefix
- **Lines 288, 330, 337, 403, 434**: Suspend functions called from non-coroutine context

### 2. AdvancedWhiteBalancePlugin.kt (24 errors)
- **Line 22**: Missing `override suspend fun initialize(context: CameraContext)` implementation
- **Line 26**: `isEnabled` needs `override` modifier
- **Line 72**: `initialize` signature incorrect (old API)
- **Lines 90-91**: Unresolved `cameraManager` and `cameraId` (need to get from context)
- **Line 125**: Missing constant `CONTROL_AWB_MODE_TUNGSTEN`
- **Line 146**: Unresolved `captureSession`
- **Line 148**: Unresolved `isRecording`
- **Line 287**: `pow()` function argument type mismatch
- **Line 295**: Float literal type mismatch
- **Line 319**: Type mismatch in multiplication
- **Line 326**: `getCurrentSettings` needs `override`
- **Line 446-447**: `onDestroy` signature incorrect

### 3. ExposureBracketingPlugin.kt (19 errors)
- Same pattern as AdvancedWhiteBalancePlugin
- **Line 26**: Missing `override suspend fun initialize(context: CameraContext)`
- **Line 30**: `isEnabled` needs `override`
- **Lines 81-82**: Unresolved `cameraManager` and `cameraId`
- **Lines 211, 305**: Unresolved `captureSession`
- **Lines 233**: Suspension function called incorrectly
- **Line 307**: Unresolved `isRecording`
- **Line 340**: `getCurrentSettings` needs `override`
- **Lines 464-465**: `onDestroy` signature incorrect

### 4. ManualApertureControlPlugin.kt (16 errors)
- Same pattern as above
- **Line 22**: Missing `override suspend fun initialize(context: CameraContext)`
- **Line 26**: `isEnabled` needs `override`
- **Lines 73-74**: Unresolved `cameraManager` and `cameraId`
- **Line 99**: Type mismatch - Double vs Float
- **Lines 138, 140**: Unresolved `captureSession` and `isRecording`
- **Lines 341-342**: `onDestroy` signature incorrect

### 5. ManualFocusControlPlugin.kt (17 errors)
- Same pattern as above
- **Line 22**: Missing `override suspend fun initialize(context: CameraContext)`
- **Line 26**: `isEnabled` needs `override`
- **Lines 89-90**: Unresolved `cameraManager` and `cameraId`
- **Line 97**: Overload resolution ambiguity in division
- **Lines 150, 152, 189, 191**: Unresolved `captureSession` and `isRecording`
- **Line 343**: `getCurrentSettings` needs `override`
- **Lines 449-450**: `onDestroy` signature incorrect

### 6. ProfessionalShutterControlPlugin.kt (14 errors)
- **Lines 145-150**: Unresolved reference to `shutterPresets` (typo - should be different property name)
- **Lines 296, 343, 350, 424**: Unresolved `launch` - missing coroutineScope prefix
- **Lines 297, 344, 351, 425**: Suspend functions called from non-coroutine context
- **Line 391**: Unresolved `shutterPresets`

### 7. ProfessionalControlsDialog.kt (10 errors)
- **Line 76**: Unresolved `R.color.surface` (color resource issue)
- **Line 168**: Type mismatch - Int vs Float expected
- **Lines 181, 183**: Missing `getAvailablePresets()` and `applyPreset()` in ISO plugin
- **Line 191**: `calculateNoiseLevel()` is private
- **Lines 209-211**: Missing `getCommonShutterSpeeds()` method
- **Lines 229, 231, 233-234**: Missing preset-related methods

## Root Causes

### A. API Mismatch
All professional control plugins use the old API:
```kotlin
override fun initialize(cameraManager: CameraManager?, cameraId: String?)
```

Should be:
```kotlin
override suspend fun initialize(context: CameraContext)
```

### B. Missing ControlPlugin Base Members
Plugins try to access `captureSession` and `isRecording` which don't exist in ControlPlugin base class

### C. Coroutine Scope Issues
Multiple plugins try to call `launch {}` without proper coroutineScope reference

### D. Missing Public Methods
Dialog expects public methods that are private or don't exist in plugins

## Fix Strategy

1. **Update all 5 professional plugins** to use new CameraContext API
2. **Add missing properties** to ControlPlugin base class OR remove references
3. **Fix coroutine calls** in plugins
4. **Expose required methods** in plugins for Dialog UI
5. **Fix type mismatches** and missing constants
6. **Fix ProfessionalControlsDialog** resource and method references

## Files to Modify

- [ ] `app/src/main/java/com/customcamera/app/plugins/AdvancedISOControlPlugin.kt`
- [ ] `app/src/main/java/com/customcamera/app/plugins/AdvancedWhiteBalancePlugin.kt`
- [ ] `app/src/main/java/com/customcamera/app/plugins/ExposureBracketingPlugin.kt`
- [ ] `app/src/main/java/com/customcamera/app/plugins/ManualApertureControlPlugin.kt`
- [ ] `app/src/main/java/com/customcamera/app/plugins/ManualFocusControlPlugin.kt`
- [ ] `app/src/main/java/com/customcamera/app/plugins/ProfessionalShutterControlPlugin.kt`
- [ ] `app/src/main/java/com/customcamera/app/ui/ProfessionalControlsDialog.kt`
- [ ] `app/src/main/res/values/colors.xml` (add surface color if missing)
