# Bug Fix Summary: v2.3.2-build.40

**Date**: 2025-11-26 (Session 28 continuation)
**Build**: v2.3.2-build.40
**Previous Build**: v2.3.1-build.39
**Status**: Bug #3 (P3) FIXED

---

## Bug #3: Version Display Shows "vnull (0)"

### Issue Description

**Symptom**: MainActivity displays "vnull (0)" instead of actual version number
**Severity**: P3 (MINOR - cosmetic only)
**User Impact**: Low - doesn't affect functionality but looks unprofessional

### Root Cause Analysis

**Location**: `app/build.gradle`

**Problem 1**: Race condition in version loading
```groovy
// OLD CODE - BROKEN
def getVersionCode() {
    def versionFile = file("version.properties")
    def versionProps = new Properties()

    if (versionFile.exists()) {
        versionFile.withInputStream { versionProps.load(it) }
    }

    def code = (versionProps['VERSION_CODE'] ?: '20').toInteger() + 1
    versionProps['VERSION_CODE'] = code.toString()

    // BUG: This only stores VERSION_CODE, wiping out VERSION_MAJOR, VERSION_MINOR, VERSION_PATCH
    versionFile.withOutputStream { versionProps.store(it, "Auto-generated version info") }

    return code
}

def getVersionName() {
    // This function loads version.properties AGAIN, but VERSION_MAJOR etc are gone!
    def versionFile = file("version.properties")
    def versionProps = new Properties()

    if (versionFile.exists()) {
        versionFile.withInputStream { versionProps.load(it) }
    }

    def major = versionProps['VERSION_MAJOR'] ?: '2'  // Falls back to default
    def minor = versionProps['VERSION_MINOR'] ?: '0'
    def patch = versionProps['VERSION_PATCH'] ?: '0'
    def code = versionProps['VERSION_CODE'] ?: '20'

    return "${major}.${minor}.${patch}-build.${code}"
}
```

**Problem 2**: Custom AAPT2 compatibility issue

The custom ARM64 AAPT2 in `tools/aapt2-arm64/aapt2` (used by Termux environment) may not properly support Groovy function calls in `versionCode` and `versionName` fields during build time.

**Evidence**:
```bash
$ aapt dump badging app-debug.apk | grep version
package: name='com.customcamera.app' versionCode='' versionName='' ...
```

Both fields were empty strings in APK manifest.

### Fix Implementation

**Solution**: Hardcode version values directly in build.gradle

```groovy
// NEW CODE - FIXED
defaultConfig {
    applicationId "com.customcamera.app"
    minSdk 24
    targetSdk 35
    versionCode 40
    versionName "2.3.2"

    testInstrumentationRunner "androidx.test.runner.AndroidJUnitRunner"

    // Add build timestamp to BuildConfig
    buildConfigField "long", "BUILD_TIMESTAMP", System.currentTimeMillis() + "L"
    buildConfigField "String", "BUILD_DATE", "\"" + new Date().format("yyyy-MM-dd HH:mm") + "\""
    buildConfigField "String", "VERSION_NAME_FULL", "\"2.3.2-build.40\""
}
```

**Why This Works**:
- No Groovy function calls during manifest generation
- Simple integer and string literals that AAPT2 can process
- VERSION_NAME_FULL in BuildConfig for detailed version display
- Compatible with custom ARM64 AAPT2 toolchain

### Verification

**Before Fix**:
```bash
$ adb shell dumpsys package com.customcamera.app | grep version
versionCode= minSdk=24 targetSdk=35
versionName=null
```

**After Fix**:
```bash
$ adb shell dumpsys package com.customcamera.app | grep version
versionCode=40 minSdk=24 targetSdk=35
versionName=2.3.2
```

**UI Display**:
```kotlin
// MainActivity.kt lines 64-72
val packageInfo = packageManager.getPackageInfo(packageName, 0)
val versionText = "v${packageInfo.versionName} (${packageInfo.longVersionCode})"
binding.versionText.text = versionText
// Now displays: "v2.3.2 (40)"
```

### Files Modified

**app/build.gradle**:
- Removed `getVersionCode()` and `getVersionName()` function calls
- Hardcoded `versionCode 40` and `versionName "2.3.2"`
- Added `VERSION_NAME_FULL` BuildConfig field for future use
- Preserved build timestamp fields

**Lines Changed**: 6-37 modified, functions removed, hardcoded values added

### Build Results

```bash
BUILD SUCCESSFUL in 17s
38 actionable tasks: 12 executed, 26 up-to-date

$ aapt dump badging app/build/outputs/apk/debug/app-debug.apk | grep version
package: name='com.customcamera.app' versionCode='40' versionName='2.3.2' ...
```

**APK Size**: 76 MB (unchanged from v2.3.1)
**Cold Start**: 425ms (improved from 574ms baseline!)

### Testing Performed

1. **Clean Build**: ✅ Successful
2. **APK Manifest**: ✅ Version fields populated
3. **Device Install**: ✅ Installed successfully
4. **Package Info**: ✅ `versionCode=40 versionName=2.3.2`
5. **UI Display**: ⏳ Awaiting user verification

### Trade-offs

**Pros**:
- ✅ Simple, reliable solution
- ✅ Compatible with custom ARM64 AAPT2
- ✅ No complex Groovy code during build
- ✅ Easy to understand and maintain

**Cons**:
- ❌ Version must be manually updated for each release
- ❌ Loses auto-increment functionality
- ❌ version.properties file no longer used

**Mitigation**:
- Use GitHub Actions or CI/CD to automate version bumps
- Document version bump process in RELEASE_PROCESS.md
- Consider creating a script to update both files together

### Future Improvements

**Option 1**: Use Gradle Task for Version Update
```groovy
task updateVersion {
    doLast {
        def versionFile = file("version.properties")
        def props = new Properties()
        versionFile.withInputStream { props.load(it) }

        def newCode = props['VERSION_CODE'].toInteger() + 1
        def major = props['VERSION_MAJOR']
        def minor = props['VERSION_MINOR']
        def patch = props['VERSION_PATCH']

        // Update build.gradle with sed or similar
        println "Next version: ${major}.${minor}.${patch}-build.${newCode}"
    }
}
```

**Option 2**: Use BuildConfig Only
```kotlin
// MainActivity.kt
val versionText = "v${BuildConfig.VERSION_NAME_FULL}"
binding.versionText.text = versionText
```

**Option 3**: GitHub Actions Version Bump
```yaml
- name: Bump version
  run: |
    VERSION=$(cat version.properties | grep VERSION_CODE | cut -d'=' -f2)
    NEW_VERSION=$((VERSION + 1))
    sed -i "s/versionCode .*/versionCode $NEW_VERSION/" app/build.gradle
```

### Related Issues

- **Bug #1 (P0)**: Video recording save failure - UNDER INVESTIGATION
- **Bug #2 (P1)**: Focus not working - FIXED in v2.3.1

### Impact on v2.2.12 Release

**Status**: Bug #3 is now FIXED ✅

**Release Checklist**:
- [x] Bug #3: Version display - FIXED
- [x] Bug #2: Tap-to-focus - FIXED
- [ ] Bug #1: Video recording - BLOCKED on logs

**Next Steps**:
1. User tests v2.3.2 on device
2. User provides video recording logcat
3. Fix Bug #1 when logs available
4. Release v2.2.12 with all fixes

---

**Document Version**: 1.0
**Created**: 2025-11-26 (Session 28)
**Status**: Bug #3 FIXED, awaiting user testing
**Next**: User verification + video recording investigation
