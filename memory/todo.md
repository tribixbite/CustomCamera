# CustomCamera — Task List

_Updated 2026-11-02 (v3.0.0 rebuild + large screens). Older history lives in git; the pre-3.0 task
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

- [x] 30 instrumented tests (2026-11-03; staging cold start 273 ms); earlier 27, 18.
- [ ] Known flake (pre-existing): a DUAL recording made right after another camera session
      sometimes gets no encoder frames → ERROR_NO_VALID_DATA, no file. Baseline 44063c68: 2/20
      (photo test + DUAL test pair); current: 0/20 for that pair, 3/20 after the volume-key
      test; 0/12 from a fresh start. Preview streams, encoder never gets a keyframe (CameraX
      concurrent composition / HAL). Users now see "Nothing was recorded… Try again". incl. DUAL recording, peaking, torch strength and the
      crop editor (`scripts/device-test.sh 192.168.0.216:5555`)
- [x] R8 staging build cold start 330 ms, no crash
- [x] Night extension listed and captures (ext=3, 2560×1920 JPEG). Portrait and HDR
      are not offered by this device's extension service, so they stay hidden.
- Shared test phones: check out/in via `/sdcard/agent-sharing.md` on the device.

## Verified on Saga (API 33, 192.168.0.224, 2026-11-03)

- [x] 30/30 instrumented tests twice; staging cold start 251–376 ms. Ran (not skipped):
      DUAL, RAW+DNG, ZSL, focus peaking, location EXIF, snapshot while recording, volume zoom,
      every-lens rebind, Pro slider. Skipped by hardware: Ultra HDR (API 34), HLG, torch strength.
- [x] DUAL flake pair (photo test + DUAL test) 10/10 on the Saga — the flake looks Pixel-specific.
- [ ] Vendor camera HAL crashed once at app launch (`camera.qcom.so CamX::OIS::LensPositionThread`,
      ubsan sub-overflow abort; tombstone_10). CameraX reported it as ERROR_CAMERA_IN_USE and
      reconnected ~9 s later. The toast now says "busy or restarting" instead of blaming another
      app. Not reproduced in the following 30 tests + 10 DUAL loops.
- [x] Saga 192.168.1.130 (API 34, PIN-locked; unlocked with the owner's PIN, 2026-11-04): 30/30,
      each test also run singly — 27 ran, 3 skipped by hardware (camera Ultra HDR, HLG, torch
      strength). The editor's Ultra HDR gain-map test ran on API 34 here. No tombstones.
- 192.168.0.203 was in use (game open) and was not touched.

## Not yet verified on device

- [ ] Actual QR decode + bounding-box alignment (needs a code in front of the camera)
- [ ] Portrait / HDR extensions (Night verified on Pixel; S25 Ultra's proxy times out)
- [x] RAW (DNG) + JPEG saved from Pro (Pixel 8 Pro, `rawCaptureSavesDngBesideJpeg`)
- [ ] Ultra HDR capture: CameraX offers no JPEG_R on the Pixel 8 Pro or the API 34 Saga
      (`media.camera` lists no JPEG_R stream); needs a phone whose HAL advertises JPEG_R
- [x] 10-bit HLG video: the saved MP4's video track reports COLOR_TRANSFER_HLG
      (`hdrVideoIsRecordedAsHlg`, Pixel 8 Pro, 2026-11-03)
- [x] ZSL photo saved on the Saga (API 33, `zeroShutterLagPhotoIsSaved` ran, 2026-11-03);
      the Pixel 8 Pro back camera reports no ZSL and skips
- [x] Location tagging writes EXIF GPS (`locationTaggingWritesGpsExif`, Pixel with location on)
- [ ] Location permission prompt flow from Settings, by hand
- [x] Volume-key shutter and 3 s self-timer (instrumented tests)
- [x] Volume-key zoom (`volumeKeysZoomWhenSetToZoom`: up zooms in, down out, no photo)
- [ ] Level indicator turning yellow when level: the line draws over the live scene, but
      checking the colour change needs someone to tilt the phone
- [ ] API 26–28 storage path (WRITE_EXTERNAL_STORAGE + DATA column) — needs an old device/emulator
- [x] Pro slider: per-parameter TalkBack name and spoken values ("1/125", not the stop index);
      uiautomator dumps omit Material sliders (provider-based nodes) — test reads the provider.
- [ ] Full TalkBack walk-through by hand

## Ported from main (v2.4.3)

Audit of v2.4.3 (2026-11-02): every feature that worked there exists in v3; the
items below were missing and are now implemented and verified on the Pixel 8 Pro.

- [x] Dual-camera picture-in-picture → DUAL mode (CameraX concurrent cameras +
      CompositionSettings; the inset is composited into the recording at stream
      resolution, verified by extracting a frame from a 1080×1920 clip). Tap inset =
      next corner (default top-right), flip = swap cameras; falls back to Video.
- [x] Freeform crop → non-destructive Crop & rotate editor in the viewer (saves an
      `_edit` copy with EXIF; `core/CropMath.kt`). Handle drag + 1:1 checked by hand.
      - [x] Ultra HDR gain map survives crop + rotate on API 34+ (platform createBitmap /
            compress keep it; `ultraHdrGainmapSurvivesTheEditor`). Below API 34 copies are SDR.
      - [x] Landscape insets checked on the Pixel (phone and forced-tablet sizes).
      - [x] Gesture-nav edge exclusion (Pixel switched to gesture nav and back, 2026-11-03):
            6 exclusion rects at the handles; a drag from x=8 moved the left edge, no Back.
- [x] Focus peaking in Pro mode while focus is manual (`core/FocusPeaking.kt`).
      - [x] Visual alignment (2026-11-03, dim room): at 37 cm the outlines sit on the pump
            bottle's edges; at far focus they move to the mid-distance bag.
- [x] Torch brightness slider (Android 15+ hardware with adjustable torch strength).
- [x] Pro manual values persist between sessions (was backlog #2), clamped per camera.
- Not ported (no working code in v2): SOS/strobe flashlight (spec only), AI scene/object
  detection plugins (only ran during barcode scanning), debug/diagnostic activities.

## Large screens (2026-11-02)

- [x] Landscape/tablet viewfinder: `ui/ViewfinderLayout` swaps ConstraintSets (shutter rail
      on the end side), `core/ViewfinderGeometry` places the preview, the ViewPort ratio
      follows the window shape, icons counter-rotate relative to the display. Phones stay
      portrait-locked; sw ≥ 600dp request UNSPECIFIED. Verified on the Pixel forced to
      1600x2560 @320dpi: 4:3 and 16:9 captures (4032×2268) correct, Pro panel capped at
      560dp, portrait ↔ landscape switch while running, gallery/viewer/editor in landscape.
- [x] DUAL inset never moved after the first bind (CameraX LifecycleCamera reuse, see
      CLAUDE.md) — fixed with a per-bind `SessionLifecycleOwner`; TL→TR→BR verified on screen.
- [x] Crop editor `layout-land` (tools in a side column); gallery grid side insets.
- [ ] Not checked on real tablet/foldable hardware, in split-screen, or with RTL locales.
- [ ] DUAL instrumented test asserts state only; a pixel-level check needs a lit scene.

## Done 2026-11-02 (second round)

- [x] Photo while recording: VIDEO binds an extra JPEG ImageCapture when the session allows
      (dropped first on the bind fallback ladder); snapshot button in the mode-tab slot while
      recording. 4032×2268 still beside a 1080p clip on the Pixel.
- [x] Shutter labels snap to standard third stops within 1/6 stop, else 2 significant
      figures (sensor limit reads 1/38000, live 1/117 reads 1/125).
- [x] Burst: hold the shutter in Photo/Pro (not RAW, extensions, timer or capture intents);
      each shot is requested when the previous starts, max 30, flash held off. ~1.4 shots/s
      on the Pixel 8 Pro with the default Quality priority (4 shots in a 3 s hold).
      - [ ] Faster bursts would need pipelining >1 request or a latency-mode ImageCapture.
- [x] Rail switch no longer resets child visibility (ConstraintSet VISIBILITY_MODE_IGNORE);
      rotating mid-recording keeps the chip, pause and snapshot buttons.

## Review fixes (2026-11-02, independent review of this session's diff)

- [x] VIDEO snapshot never fires the photo flash; start() applies the mode's flash rule
- [x] Burst only from a real touch hold (key/accessibility long-press = one shot); onPause stops it
- [x] Orientation lock re-decided on fold/unfold (onConfigurationChanged)
- [x] RTL landscape preview centred (absolute gravity)
- [x] Burst click/blink flag carried on CaptureStarted; no burst through the HDR extension
- [x] Window-shape/rotation rebinds deferred while any recording runs (was DUAL only)
- [x] Icons turned twice on a tablet rotation (sensor fires before the display rotates):
      while the display can follow (unlocked + auto-rotate on), a sensor turn now waits
      700 ms and is cancelled by the display change. Not reproducible on the Pixel (the
      fix needs a physical turn on a large screen); simulated-tablet layout re-checked.

## Backlog (prioritised)

1. [ ] Upgrade to AGP 9.x + compileSdk 37 (unblocks core-ktx 1.19+).
2. [ ] Localisation (strings are resource-based; arrays marked translatable).
3. [ ] Baseline profile for faster cold start.
4. [ ] Optional: replace ML Kit with an Apache-licensed scanner to shrink the APK
       (~5 MB native lib per ABI) and drop the Google ToS dependency.
