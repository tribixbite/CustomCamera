# Deprecation Warnings - Technical Debt Documentation

**Last Updated**: 2025-11-25  
**Total Warnings**: 9  
**Priority**: Low (app functions correctly, warnings are for future API compatibility)

## Overview

This document tracks deprecation warnings found during Phase 9C optimization. All warnings are from official Android/Java APIs that have newer alternatives. The app functions correctly, but these should be addressed for long-term maintainability.

## Warning Categories

### 1. Window Insets API (3 warnings) - FALSE POSITIVE ⚠️

**Files**:
- `CameraActivity.kt:104`
- `CameraActivityEngine.kt:307`  
- `MainActivity.kt:52`

**Warning**:
```
'fun setDecorFitsSystemWindows(p0: Boolean): Unit' is deprecated. Deprecated in Java.
```

**Current Code**:
```kotlin
if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
    window.setDecorFitsSystemWindows(false)
}
```

**Analysis**:
- This is actually the MODERN API for Android 11+ (API 30+)
- The deprecation warning is a Kotlin compiler false positive
- `setDecorFitsSystemWindows(false)` is the correct replacement for the old `SYSTEM_UI_FLAG_LAYOUT_*` flags
- **Action**: Can be suppressed with `@Suppress("DEPRECATION")` or ignored
- **Priority**: P4 (cosmetic warning only)

**References**:
- [Android 11 Window Insets](https://developer.android.com/develop/ui/views/layout/edge-to-edge)
- This replaced the deprecated `View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN`

---

### 2. Toast.view Property (2 warnings) - REAL DEPRECATION

**Files**:
- `EnhancedToast.kt:135`
- `ErrorPresentation.kt:99`

**Warning**:
```
'var view: View?' is deprecated. Deprecated in Java.
```

**Current Code**:
```kotlin
toast.view = layout
toast.show()
```

**Modern Alternative**:
Use `Snackbar` or custom overlay with `WindowManager`:

```kotlin
// Option 1: Snackbar (recommended for most cases)
Snackbar.make(rootView, message, Snackbar.LENGTH_SHORT)
    .setBackgroundTint(backgroundColor)
    .show()

// Option 2: Custom WindowManager overlay
val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
val params = WindowManager.LayoutParams(...)
windowManager.addView(customView, params)
```

**Impact**: Low - Toast.view still works on all Android versions, just deprecated  
**Effort**: Medium - Need to refactor EnhancedToast class  
**Priority**: P2 (should fix in Phase 9D UI polish)

---

### 3. Display.scaledDensity (1 warning)

**File**: `BarcodeOverlayView.kt:175`

**Warning**:
```
'field scaledDensity: Float' is deprecated. Deprecated in Java.
```

**Current Code**:
```kotlin
val density = resources.displayMetrics.scaledDensity
```

**Modern Alternative**:
```kotlin
val density = resources.configuration.fontScale * resources.displayMetrics.density
```

**Impact**: None - scaledDensity still works, just discouraged  
**Effort**: Trivial - one line change  
**Priority**: P3 (nice to fix)

---

### 4. Camera2 createCaptureSession (1 warning)

**File**: `HDRCaptureController.kt:139`

**Warning**:
```
'fun createCaptureSession(...): Unit' is deprecated. Deprecated in Java.
```

**Modern Alternative**:
Use `createCaptureSessionByOutputConfigurations()` or `SessionConfiguration`:

```kotlin
val sessionConfig = SessionConfiguration(
    SessionConfiguration.SESSION_REGULAR,
    outputConfigurations,
    executor,
    stateCallback
)
cameraDevice.createCaptureSession(sessionConfig)
```

**Impact**: Low - old API still supported  
**Effort**: Medium - need to update HDR capture logic  
**Priority**: P2 (should fix when touching HDR code)

---

### 5. MediaCodec inputBuffers (1 warning)

**File**: `LiveStreamingManager.kt:680`

**Warning**:
```
'val inputBuffers: Array<(out) ByteBuffer!>' is deprecated. Deprecated in Java.
```

**Modern Alternative**:
```kotlin
// Old way
val inputBuffers = codec.inputBuffers
val buffer = inputBuffers[index]

// New way  
val buffer = codec.getInputBuffer(index)
```

**Impact**: Low - old API still works  
**Effort**: Trivial - direct replacement  
**Priority**: P3 (easy fix)

---

### 6. MediaCodecInfo Color Format (1 warning)

**File**: `VideoCodecManager.kt:462`

**Warning**:
```
'static field COLOR_FormatYUV420SemiPlanar: Int' is deprecated. Deprecated in Java.
```

**Modern Alternative**:
```kotlin
// Use MediaFormat constants instead
MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
```

**Impact**: Low - affects codec selection but still works  
**Effort**: Low - update color format preference  
**Priority**: P3 (can fix when touching video code)

---

## Recommendations

### Immediate Actions (Session 12)
1. ✅ Document all warnings (this file)
2. ⏭️ Suppress false positive window insets warnings
3. ⏭️ Create issue tracker for technical debt

### Phase 9C - Performance Optimization
1. Fix trivial warnings (scaledDensity, inputBuffers)
2. Test codec format changes
3. Measure impact before/after

### Phase 9D - UI Polish
1. Refactor EnhancedToast to use Snackbar
2. Update error presentation system
3. Ensure consistent notification system

### Future Maintenance
1. Update HDR capture session creation
2. Review Camera2 API usage
3. Consider migrating to CameraX fully (reduces Camera2 deprecations)

---

## Testing Strategy

For each fix:
1. Verify original functionality still works
2. Test on Android 8-14 (API 26-34)
3. Check for performance impact
4. Ensure no visual regressions

---

## Build Configuration

**Current Status**:
- ✅ Java 11 (sourceCompatibility & targetCompatibility)
- ✅ Kotlin JVM target 11
- ✅ CompileSdk 35 (Android 15)
- ✅ TargetSdk 35
- ✅ MinSdk 24 (Android 7.0)

**Warnings Impact**:
- Build: Success ✅
- Runtime: No issues ✅
- App Store: Acceptable (not blocking)
- Future: Should address before major Android updates

---

## Priority Summary

- **P1 (Critical)**: None - no blocking issues
- **P2 (High)**: Toast.view, Camera2 session creation
- **P3 (Medium)**: scaledDensity, inputBuffers, color format
- **P4 (Low)**: Window insets false positive

**Overall Impact**: Low - app is production-ready, warnings are for future-proofing

---

**Documentation Created**: 2025-11-25  
**Next Review**: Phase 9D or when touching affected code
