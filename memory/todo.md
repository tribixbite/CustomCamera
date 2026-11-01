# CustomCamera — Task List

_Updated 2026-11-01 (v3.0.0 rebuild). Older history lives in git; the pre-3.0 task
list described a plugin engine that has been removed._

## Status

v3.0.0 builds, passes 60 unit + 13 instrumented tests (SM-S938U1, API 37), is
lint-clean apart from AGP-9 upgrade notices, and the R8 `staging` build was
smoke-tested on device (capture, settings, scan, viewer — no crashes).

## Before the first Play upload (owner actions)

- [ ] Create the upload keystore + `keystore.properties` (docs/RELEASE.md §1).
- [ ] Push to GitHub so the privacy-policy URL in the app and listing resolves
      (`PRIVACY.md` on `main`); confirm CI passes (`.github/workflows/android.yml`).
- [ ] Phone screenshots (≥2, 9:16, lit scene) → `fastlane/metadata/android/en-US/images/phoneScreenshots/`.
      Dev-box screenshots so far are black: the test phone's camera faces a dark surface.
- [ ] Decide on the final applicationId before the first upload — it can never change
      afterwards. `com.customcamera.app` has no public Play listing (404 on 2026-11-01),
      but an unpublished/draft app elsewhere could still hold it; a domain you own
      (e.g. `io.github.tribixbite.customcamera`) avoids collisions.
- [ ] Fill Play Console Data safety / content rating per docs/RELEASE.md §4.

## Verified on device (SM-S938U1)

- [x] Camera binds (logical rear camera 0, 4 Camera2 devices listed with focal lengths)
- [x] Photo → `DCIM/CustomCamera`, 4080×3060, correct EXIF orientation
- [x] Video 1080p H.264 + AAC, pause/resume, persistent recording
- [x] Pro ISO/shutter reach the sensor (EXIF ISO 12, 1/11977 s after setting them)
- [x] Scan mode loads ML Kit's bundled model with no network permission
- [x] Front camera, lens picker, settings, gallery, viewer, IMAGE_CAPTURE review

## Not yet verified on device

- [ ] Actual QR decode + bounding-box alignment (needs a code in front of the camera)
- [ ] Night / Portrait / HDR extensions — the S25 Ultra's extension proxy times out,
      so these modes stay hidden there; test on a Pixel or another vendor
- [ ] RAW (DNG) and Ultra HDR output files; 10-bit HLG video; ZSL
- [ ] Location tagging end-to-end (permission flow + EXIF GPS)
- [ ] Volume-key shutter/zoom, self-timer, level indicator in a lit scene
- [ ] API 26–28 storage path (WRITE_EXTERNAL_STORAGE + DATA column) — needs an old device/emulator
- [ ] TalkBack pass (Pro slider did not appear in a uiautomator dump)

## Backlog (prioritised)

1. [ ] **Large screens / landscape layout.** Android 16+ ignores the portrait lock on
       ≥600dp screens for targetSdk 36 apps; the viewfinder layout is portrait-only and
       needs a landscape/tablet variant (capture rotation is already correct).
2. [ ] Persist Pro-mode manual values across sessions (currently reset to auto).
3. [ ] Burst / long-press shutter, and photo capture during video recording.
4. [ ] Shutter-speed labels at sensor extremes show raw values (e.g. "1/11977");
       consider snapping display to the nearest standard stop.
5. [ ] Upgrade to AGP 9.x + compileSdk 37 (unblocks core-ktx 1.19+).
6. [ ] Localisation (strings are resource-based; arrays marked translatable).
7. [ ] Baseline profile for faster cold start.
8. [ ] Optional: replace ML Kit with an Apache-licensed scanner to shrink the APK
       (~5 MB native lib per ABI) and drop the Google ToS dependency.
