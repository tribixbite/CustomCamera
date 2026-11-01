# CustomCamera — Task List

_Updated 2026-11-01 (v3.0.0 rebuild). Older history lives in git; the pre-3.0 task
list described a plugin engine that has been removed._

## Status

v3.0.0 builds, passes 60 unit + 13 instrumented tests (SM-S938U1, API 37), is
lint-clean apart from AGP-9 upgrade notices, and the R8 `staging` build was
smoke-tested on device (capture, settings, scan, viewer — no crashes).

## Before the first Play upload (owner actions)

All owner steps (upload key, GitHub secrets, signed bundle, screenshots, Play
Console forms, closed-testing gate) live in **`PLAY_RELEASE.md`** — keep that the
single source; do not duplicate the list here.

## Branches (decision pending, 2026-11-01)

- `v3` (this work) branched from a stale local snapshot; GitHub `main` carries 586
  further commits of the old v2 code base (v2.4.3, Sep–Dec 2025, targetSdk 35, ISO/
  shutter controllers still stubbed). The owner chooses how `v3` lands on `main`
  (see final session summary); never force-push without explicit approval.

## Verified on device (SM-S938U1)

- [x] Camera binds (logical rear camera 0, 4 Camera2 devices listed with focal lengths)
- [x] Photo → `DCIM/CustomCamera`, 4080×3060, correct EXIF orientation
- [x] Video 1080p H.264 + AAC, pause/resume, persistent recording
- [x] Pro ISO/shutter reach the sensor (EXIF ISO 12, 1/11977 s after setting them)
- [x] Scan mode loads ML Kit's bundled model with no network permission
- [x] Front camera, lens picker, settings, gallery, viewer, IMAGE_CAPTURE review

## Pending: run on a dedicated test device

The test phones (Pixel 8 Pro, Saga, Seeker) were unreachable on 2026-11-01
(polled 11:00–12:30 EST; nmap reported all three hosts down). When one is up:

```bash
scripts/gradle-locked.sh :app:assembleDebug :app:assembleDebugAndroidTest :app:assembleStaging
scripts/device-test.sh <ip:port>          # 13 instrumented tests + staging smoke test
```

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
8. [ ] Torch brightness (CameraX `setTorchStrengthLevel`, API 35+ devices) for video/scan
       torch — idea carried over from the v2 line's flashlight spec.
9. [ ] Optional: replace ML Kit with an Apache-licensed scanner to shrink the APK
       (~5 MB native lib per ABI) and drop the Google ToS dependency.
