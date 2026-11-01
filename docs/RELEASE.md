# Releasing CustomCamera to Google Play

## 1. One-time: upload key

Play App Signing holds the real app-signing key; you sign uploads with an
**upload key**. Create it once and back it up somewhere safe (losing it means a
key-reset request to Google):

```bash
mkdir -p release
keytool -genkeypair -v -keystore release/customcamera-upload.jks \
  -alias customcamera-upload -keyalg RSA -keysize 4096 -validity 10000
cp keystore.properties.example keystore.properties   # then fill in passwords
```

`*.jks` and `keystore.properties` are gitignored. Without them, release builds
fall back to the debug key and Gradle prints a warning — such a bundle must not
be uploaded.

## 2. Version

Edit `version.properties`. `versionCode = MAJOR*10000 + MINOR*100 + PATCH`
(3.0.0 → 30000, which sorts above every pre-3.0 build). Every Play upload
needs a higher versionCode. Add `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`.

## 3. Build and verify

```bash
./gradlew :app:testDebugUnitTest :app:lintRelease :app:bundleRelease
# on-device tests (device connected):
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w com.customcamera.app.debug.test/androidx.test.runner.AndroidJUnitRunner
# shrunk build smoke test, installable beside a Play release:
./gradlew :app:assembleStaging && adb install -r app/build/outputs/apk/staging/app-staging.apk
```

Upload `app/build/outputs/bundle/release/app-release.aab`, and upload
`app/build/outputs/mapping/release/mapping.txt` as the deobfuscation file
(Play Console → App bundle explorer) so crash traces are readable.

## 4. Play Console answers

**App content → Privacy policy:** `https://github.com/tribixbite/CustomCamera/blob/main/PRIVACY.md`

**Data safety:**
- Does your app collect or share any required user data types? **No.**
  The app has no `INTERNET` permission (removed from the merged manifest), so
  nothing can be transmitted. Location is only written into the user's own files
  on-device, which Play does not count as collection.
- Is all data encrypted in transit? Not applicable (no data transmitted).
- Account deletion: not applicable (no accounts).

**Ads:** No ads. **Target audience:** 13+ (general camera app). **Content rating
questionnaire:** utility; no user-generated content shared, no violence, etc.

**Permissions declaration:** no sensitive or restricted permissions are used
(no `READ_MEDIA_*`, `MANAGE_EXTERNAL_STORAGE`, background location, or
`QUERY_ALL_PACKAGES`). Camera, microphone and location are standard runtime
permissions requested in context.

**Target API:** targetSdk 36, as required for new apps and updates since
31 Aug 2026.

## 5. Store listing

Text and graphics live in `fastlane/metadata/android/en-US/` (fastlane
`supply` layout). Regenerate the icon and feature graphic with
`scripts/generate-store-assets.sh`.

Still needed before submission: **at least 2 phone screenshots** (1080×1920
or larger, 9:16), ideally of Photo, Pro, Video, Scan and Gallery taken in a
well-lit scene. Put them in `fastlane/metadata/android/en-US/images/phoneScreenshots/`.
