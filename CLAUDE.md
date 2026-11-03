# CustomCamera — Claude Code project notes

Kotlin / CameraX 1.6 camera app targeting Google Play (targetSdk 36, minSdk 26).
Rebuilt in v3.0.0 (2026-11-01); the pre-3.0 "plugin engine" was removed because its
manual controls never reached the camera. **Task list: `memory/todo.md`.**

## Commands

```bash
scripts/gradle-locked.sh :app:testDebugUnitTest      # JVM tests (core/ package)
scripts/gradle-locked.sh :app:lintDebug              # should report only AGP-9 upgrade notices
scripts/gradle-locked.sh :app:assembleDebug :app:assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w tribixbite.customcamera.debug.test/androidx.test.runner.AndroidJUnitRunner   # 27 tests
scripts/gradle-locked.sh :app:assembleStaging :app:bundleRelease   # shrunk builds
scripts/generate-store-assets.sh                     # Play icon + feature graphic
```

Always build through `scripts/gradle-locked.sh` on the Termux box (shared
`~/.cache/cleverkeys-build.lock`; other projects' guards otherwise kill our daemon).
`connectedAndroidTest` is not used: the SDK's adb is x86_64 — install the two APKs
and run `am instrument` with Termux's adb instead.

applicationId `tribixbite.customcamera` (+ `.debug`, `.staging`); the Kotlin
namespace stays `com.customcamera.app`. Release steps for the owner: `PLAY_RELEASE.md`
(keep it the single source). The S25 Ultra (this host) also has the **old v2 app**
`com.customcamera.app` with photos in private storage — never uninstall it.

**Do not test on the host phone (S25 Ultra).** Use the test devices listed in
`../swype/cleverkeys` notes: Pixel 8 Pro `192.168.0.216` (port rotates; 5555 or
wireless-debug port), Saga `192.168.1.243:5555` (never `stop`/`start` it),
Seeker `192.168.0.170:5555`.

## Layout

- `core/` — pure Kotlin, no Android imports; every rule here gets a JVM test.
- `camera/CameraController.kt` — single owner of the CameraX session; renders to
  `StateFlow<CameraState>`, one-shot `SharedFlow<CameraEvent>`.
- `ui/CameraActivity.kt` — viewfinder, gestures, capture intents, permissions.
- `gallery/`, `settings/`, `scan/`, `util/` — see README.

## Hard-won lessons (this codebase)

- **ExtensionsManager blocks its calling thread.** On the S25 Ultra (API 37) the
  framework extension proxy times out ~2 s per query; `getInstanceAsync()` and every
  `isExtensionAvailable()` run synchronously on the caller. Calling them on main
  caused an ANR before any camera bound. They now run on `extensionExecutor`, results
  are cached per camera key, and main only reads the cache. Keep it that way.
- **No `singleTask` on CameraActivity**: `IMAGE_CAPTURE`/`VIDEO_CAPTURE` callers use
  `startActivityForResult`, which cannot return a result across tasks.
- **ML Kit merges `INTERNET` + `ACCESS_NETWORK_STATE`**; they are removed with
  `tools:node="remove"` so the privacy policy's "no network" claim is OS-enforced.
  Re-check the merged manifest after adding any dependency.
- **MediaStore fills `DURATION` asynchronously** after a video is published — poll,
  don't assert immediately.
- **Static `res/xml/shortcuts.xml` hard-codes `targetPackage`** (no manifest
  placeholders in resources) — shortcuts are published dynamically instead.
- **core-ktx ≥ 1.19 needs AGP 9.1 + compileSdk 37.** Pinned to 1.18.0 until the
  toolchain moves; android-37 platform is not installed on the dev box.
- **Termux Gradle daemons ignore new env vars** (no native env injection on Android):
  `CC_KEYSTORE_*` set on a later invocation are invisible to a running daemon. Use
  `keystore.properties` locally, or `--no-daemon`. CI is unaffected.
- Lint-vital on release variants needs `MaxMetaspaceSize=1g` (OOM at 512m).
- The host phone runs this Claude session: an earlier test pass on it sent taps
  into the Termux prompt when the app lost focus. Test on the dedicated devices
  above; on any shared device guard each `adb shell input` with a focus check.
- **`am instrument` reports `assumeTrue` skips inside `OK (N tests)`.** Hardware-gated
  tests (DUAL, torch strength, manual focus) can silently skip; run them one at a time
  with `-e class …#method` and check for `AssumptionViolatedException` before claiming coverage.
- **CameraX `CompositionSettings` (DUAL) use NDC:** −1..1, origin centre, +y up; `offset`
  moves the scaled stream's centre (`core/PipLayout`). Verified on Pixel 8 Pro by pulling a
  frame from a recorded clip. Concurrent cameras allow 2 use cases per camera (Preview +
  VideoCapture here) and cap resolution below 4K.
- **Pro + RAW + an ImageAnalysis = 4 streams**, not guaranteed below LEVEL_3: the bind
  falls back silently. RAW wins; peaking/histogram analysis is skipped while RAW is on.
- **CameraX reuses a LifecycleCamera per (owner, camera pair)** and reads concurrent
  `CompositionSettings` only when it is created (camera-lifecycle 1.6.2,
  `LifecycleCameraProviderImpl.bindToLifecycleInternal`). Rebinding DUAL with a moved
  inset under the activity owner silently kept the old corner; each DUAL bind now gets a
  fresh `SessionLifecycleOwner`. State-only tests did not catch it — check the pixels.
- **ViewPort ratio is in display coordinates**: a landscape window needs `long:short` or
  captures come out as a portrait strip (16:9 → 1721×3060 instead of 4032×2268).
- **Simulating a tablet on the Pixel**: `wm size 1600x2560; wm density 320` (sw 800dp) plus
  `settings put system accelerometer_rotation 0; settings put system user_rotation 1`.
  Restore with `wm size reset; wm density reset` and the original rotation settings
  (Pixel: accelerometer_rotation=1, user_rotation=0). Leaving a portrait-locked activity
  resets the forced rotation; re-set user_rotation before landscape screenshots.
- **`ConstraintSet.applyTo` also applies each child's clone-time visibility, alpha and
  rotation.** Use `setVisibilityMode(id, VISIBILITY_MODE_IGNORE)` on every id and re-rotate
  icons afterwards, or a mid-recording rotation hides the recording chip.
- **Ultra HDR edits keep the gain map on API 34+ with no extra code**: ImageDecoder decodes
  it, `Bitmap.createBitmap(…, matrix, …)` transforms it, `compress(JPEG)` writes JPEG_R.
  Test with a synthetic `Gainmap` (the Pixel 8 Pro offers no JPEG_R through CameraX).
- **DUAL + `ERROR_NO_VALID_DATA` is a pre-existing intermittent** (also on 44063c68): right
  after another camera session the composited encoder can get no frames while the preview
  streams. Reproduce with `-e class C#photoIsSavedToDcim,C#dualModeRecordsPictureInPictureVideo`
  in a loop; one failing run in 20 is normal, compare against a baseline worktree build.
