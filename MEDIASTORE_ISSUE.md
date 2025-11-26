# MediaStore Photo Capture Issue

## Date
2025-11-26

## Status
⚠️ BLOCKER: Photo capture fails at MediaStore write stage

## Symptoms
- Camera launches successfully ✅
- CameraX ImageCapture initializes correctly ✅
- MediaStore URI created successfully ✅
- Photo capture fails during `FileUtil.copyFileToMediaStore()` ❌

## Error Details

```
E CameraActivityEngine: Photo capture failed
E CameraActivityEngine: androidx.camera.core.ImageCaptureException: Processing failed.
E CameraActivityEngine:   at androidx.camera.core.imagecapture.ProcessingNode.processInputPacket
E CameraActivityEngine:   at androidx.camera.core.imagecapture.FileUtil.copyFileToMediaStore
E CameraActivityEngine:   at androidx.camera.core.imagecapture.FileUtil.moveFileToTarget
E CameraActivityEngine:   at androidx.camera.core.imagecapture.JpegBytes2Disk.apply
```

## Logs Evidence

Successful steps:
```
I CameraActivityEngine: Creating MediaStore entry for: 20251126_064537.jpg
I CameraActivityEngine: MediaStore URI created: content://media/external/images/media/1000089332
```

Failure step:
```
E CameraActivityEngine: at androidx.camera.core.imagecapture.FileUtil.copyFileToMediaStore(FileUtil.java:170)
```

## Permissions Status

### Granted:
- ✅ `android.permission.CAMERA` (user set)
- ✅ `android.permission.VIBRATE`
- ✅ `android.permission.HIGH_SAMPLING_RATE_SENSORS`
- ✅ `android.permission.INTERNET`
- ✅ `android.permission.ACCESS_NETWORK_STATE`

### Media Permissions (Android 13+):
- ✅ `android.permission.READ_MEDIA_IMAGES` (granted via adb)
- ✅ `android.permission.READ_MEDIA_VIDEO` (granted via adb)
- ✅ `android.permission.RECORD_AUDIO` (granted via adb)

### Legacy Permissions (Android 12 and below):
- ⚠️ Not checked (device is Android 15)

## Root Cause Analysis

### Likely Causes:
1. **Scoped Storage Restrictions (Android 10+)**
   - App targets API 34 (Android 14)
   - Using MediaStore correctly
   - Issue may be with ContentResolver write access

2. **File Provider Configuration**
   - May need to verify file_paths.xml configuration
   - Temp file creation before MediaStore copy might be failing

3. **Storage Permission Timing**
   - Permissions granted via ADB after app install
   - App may need restart to recognize new permissions
   - Runtime permission request flow not triggered properly

4. **RELATIVE_PATH Issue**
   - Using `DCIM/Camera` as relative path
   - May need to use `Environment.DIRECTORY_DCIM` constant
   - Or use default (no RELATIVE_PATH specified)

## Code Location

**File**: `app/src/main/java/com/customcamera/app/CameraActivityEngine.kt`

**Method**: `capturePhoto()` (line ~785-835)

```kotlin
val contentValues = ContentValues().apply {
    put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        put(MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/Camera")  // ← Potential issue
        put(MediaStore.MediaColumns.IS_PENDING, 1)
    }
}

val imageUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
// ↑ This succeeds

val outputFileOptions = ImageCapture.OutputFileOptions.Builder(contentResolver, imageUri, contentValues).build()
// ↓ This fails during internal file copy
imageCapture.takePicture(outputFileOptions, ...)
```

## Attempted Fixes

### 1. Permission Grant via ADB ❌
```bash
adb shell pm grant com.customcamera.app android.permission.READ_MEDIA_IMAGES
adb shell pm grant com.customcamera.app android.permission.READ_MEDIA_VIDEO
adb shell pm grant com.customcamera.app android.permission.RECORD_AUDIO
```
**Result**: Still fails at copyFileToMediaStore()

### 2. Fresh APK Install ❌
- Built new APK with DualCameraPiP fix
- Installed via ADB
- Granted all permissions
**Result**: Still fails at copyFileToMediaStore()

## Recommended Fixes

### Priority 1: Runtime Permission Request
**Action**: Ensure app properly requests media permissions at runtime
**Rationale**: ADB-granted permissions may not be recognized properly

**Implementation**:
```kotlin
// In onCreate or before first capture
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
        != PackageManager.PERMISSION_GRANTED) {
        ActivityCompat.requestPermissions(this,
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES),
            REQUEST_MEDIA_PERMISSION)
    }
}
```

### Priority 2: Simplify RELATIVE_PATH
**Action**: Try without RELATIVE_PATH or use Environment constant

**Option A - No RELATIVE_PATH**:
```kotlin
val contentValues = ContentValues().apply {
    put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        // Remove RELATIVE_PATH - let system decide
        put(MediaStore.MediaColumns.IS_PENDING, 1)
    }
}
```

**Option B - Use Environment Constant**:
```kotlin
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DCIM)  // Not DCIM/Camera
    put(MediaStore.MediaColumns.IS_PENDING, 1)
}
```

### Priority 3: Verify ContentResolver
**Action**: Add detailed logging around ContentResolver operations

```kotlin
Log.i(TAG, "ContentResolver: $contentResolver")
Log.i(TAG, "MediaStore URI: $imageUri")

// Try opening output stream directly as verification
try {
    contentResolver.openOutputStream(imageUri)?.use { stream ->
        Log.i(TAG, "✅ Output stream opened successfully")
        stream.write(byteArrayOf(0xFF.toByte(), 0xD8.toByte())) // JPEG magic bytes
        Log.i(TAG, "✅ Test write successful")
    }
} catch (e: Exception) {
    Log.e(TAG, "❌ Output stream test failed", e)
}
```

### Priority 4: Manual Device Testing
**Action**: Test on physical device with manual UI interaction
**Rationale**:
- ADB testing may have permission timing issues
- Manual testing will trigger proper permission dialogs
- Can verify if issue is testing-specific or actual bug

## Workaround for Testing

Until fixed, use manual testing approach:
1. Launch app normally on device
2. Tap "Select Camera" button with finger
3. Tap shutter button to capture
4. System should prompt for permissions
5. Grant permissions via UI dialog
6. Verify photo saves to gallery

## Related Files
- `app/src/main/AndroidManifest.xml` - Permission declarations
- `app/src/main/java/com/customcamera/app/CameraActivityEngine.kt` - Capture implementation
- `app/src/main/res/xml/file_paths.xml` - FileProvider configuration (if exists)

## Testing Environment
- **Device**: Samsung (Android 15, API 35)
- **Build**: Debug APK, build 32
- **Testing Method**: ADB intent-based automation
- **Permissions**: Granted via ADB pm grant

## Next Steps
1. Implement runtime permission request flow
2. Test RELATIVE_PATH variations
3. Add comprehensive logging
4. Manual device testing
5. Consider fallback to direct file write (pre-Android 10 style) with permission check

## References
- [Android Media Store Documentation](https://developer.android.com/training/data-storage/shared/media)
- [CameraX Image Capture](https://developer.android.com/training/camerax/take-photo)
- [Scoped Storage Best Practices](https://developer.android.com/training/data-storage#scoped-storage)

---

**Impact**: HIGH - Blocks all photo capture functionality
**Complexity**: MEDIUM - Well-understood Android storage issue
**ETA**: 1-2 hours for fix + testing
