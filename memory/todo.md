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

History: this checkout forked at c9f2cea1 (2025-09-15) and continued to 9771e3dd
(v2.0.0, also pushed as `origin/mb`); a different clone made the 586 commits now on
`main` (v2.4.3, Sep–Dec 2025). This checkout never had them; local `main` was created
from `origin/main` on 2026-11-01. A code audit of main v2.4.3 found manual ISO/shutter/
WB/focus, Night, HDR, RAW, histogram and focus peaking stubbed or unreachable; v3
covers every working feature except those listed under "Ported from main" below.
Owner chooses how `v3` lands on `main`; never force-push without explicit approval.

## Verified on device (SM-S938U1)

- [x] Camera binds (logical rear camera 0, 4 Camera2 devices listed with focal lengths)
- [x] Photo → `DCIM/CustomCamera`, 4080×3060, correct EXIF orientation
- [x] Video 1080p H.264 + AAC, pause/resume, persistent recording
- [x] Pro ISO/shutter reach the sensor (EXIF ISO 12, 1/11977 s after setting them)
- [x] Scan mode loads ML Kit's bundled model with no network permission
- [x] Front camera, lens picker, settings, gallery, viewer, IMAGE_CAPTURE review

## Verified on Pixel 8 Pro (API 37, 2026-11-01)

- [x] 13/13 instrumented tests (`scripts/device-test.sh 192.168.0.216:5555`)
- [x] R8 staging build cold start 330 ms, no crash
- [x] Night extension listed and captures (ext=3, 2560×1920 JPEG). Portrait and HDR
      are not offered by this device's extension service, so they stay hidden.
- Shared test phones: check out/in via `/sdcard/agent-sharing.md` on the device.

## Not yet verified on device

- [ ] Actual QR decode + bounding-box alignment (needs a code in front of the camera)
- [ ] Portrait / HDR extensions (Night verified on Pixel; S25 Ultra's proxy times out)
- [ ] RAW (DNG) and Ultra HDR output files; 10-bit HLG video; ZSL
- [ ] Location tagging end-to-end (permission flow + EXIF GPS)
- [ ] Volume-key shutter/zoom, self-timer, level indicator in a lit scene
- [ ] API 26–28 storage path (WRITE_EXTERNAL_STORAGE + DATA column) — needs an old device/emulator
- [ ] TalkBack pass (Pro slider did not appear in a uiautomator dump)

## Ported from main (v2.4.3) — candidates

- [ ] Dual-camera picture-in-picture (main `CameraEngine.kt` ConcurrentCamera bind,
      `pip/ConcurrentCameraCapability.kt`); capture the PiP frame at sensor resolution
      instead of main's `PreviewView.bitmap` composite. Spec: main
      `docs/specs/features/concurrent-camera-pip.md`.
- [ ] Freeform crop overlay (main `crop/CropOverlayView.kt`), mapped through the ViewPort.
- [ ] Focus peaking in Pro mode (main's version was never reachable — new work).
- [ ] Torch strength / SOS: spec in main `docs/specs/FLASHLIGHT_MINI_APP.md` (backlog #8).

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
