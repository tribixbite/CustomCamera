# CustomCamera — Claude Code project notes

Kotlin / CameraX 1.6 camera app targeting Google Play (targetSdk 36, minSdk 26).
Rebuilt in v3.0.0 (2026-11-01); the pre-3.0 "plugin engine" was removed because its
manual controls never reached the camera. **Task list: `memory/todo.md`.**

## Commands

```bash
scripts/gradle-locked.sh :app:testDebugUnitTest      # 60 JVM tests (core/ package)
scripts/gradle-locked.sh :app:lintDebug              # should report only AGP-9 upgrade notices
scripts/gradle-locked.sh :app:assembleDebug :app:assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w com.customcamera.app.debug.test/androidx.test.runner.AndroidJUnitRunner   # 13 tests
scripts/gradle-locked.sh :app:assembleStaging :app:bundleRelease   # shrunk builds
scripts/generate-store-assets.sh                     # Play icon + feature graphic
```

Always build through `scripts/gradle-locked.sh` on the Termux box (shared
`~/.cache/cleverkeys-build.lock`; other projects' guards otherwise kill our daemon).
`connectedAndroidTest` is not used: the SDK's adb is x86_64 — install the two APKs
and run `am instrument` with Termux's adb instead.

Application ids: `.debug` (debug), `.staging` (R8 + debug key), plain id for release.
The device also has the **old v2 app** `com.customcamera.app` installed with photos in
its private storage — never uninstall it without asking.

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
- Lint-vital on release variants needs `MaxMetaspaceSize=1g` (OOM at 512m).
- adb UI testing happens on the user's own phone: guard every `input` with a
  focus check (the session's Termux window can be in front and taps become typing),
  and return to Termux afterwards.
