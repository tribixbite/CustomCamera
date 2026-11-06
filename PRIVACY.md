# CustomCamera Privacy Policy

_Last updated: 1 November 2026_

CustomCamera ("the app") is a camera app for Android. This policy explains what
the app does with your information. In short: **it collects nothing and sends
nothing anywhere.**

## No data collection

- The app has **no internet access** — it does not request the Android
  `INTERNET` permission, so it cannot send data off your device.
- There are no ads, analytics, crash reporters, accounts or sign-ins.
- The developer receives no information about you or how you use the app.

## What stays on your device

| Data | Where it goes | Why |
| --- | --- | --- |
| Photos and videos you take | Your device's shared storage, `DCIM/CustomCamera` | So they appear in your gallery |
| Location (only if you turn on **Save location**) | Written into the photo's EXIF / video metadata on your device | Geotagging your own captures |
| App settings | App-private storage (included in Android backup if you use it) | Remembering your preferences |
| QR / barcode contents | Processed in memory on the device; never stored | Showing the scan result |

You can delete captures from the in-app gallery or any gallery app. Uninstalling
the app removes its settings; your photos and videos stay in your gallery.

## Permissions

| Permission | When it is requested | Used for |
| --- | --- | --- |
| Camera | On first launch | Viewfinder, photos, videos, QR scanning |
| Microphone | First time you record video with sound | Video audio (you can record without it) |
| Location | Only when you enable **Save location** | Geotagging captures |
| Storage (Android 9 and lower only) | On first launch on those versions | Saving to `DCIM/CustomCamera` |

## Third-party components

Barcode scanning uses ZXing, an open-source (Apache-2.0) decoder that runs
entirely inside the app. No component sends data anywhere: the app has no
internet permission, so nothing can leave the device. (Version 3.0.0 used Google
ML Kit for scanning; it is no longer included.)

## Children

The app does not collect personal information from anyone, including children.

## Changes

Updates to this policy are published in this file in the app's source
repository: <https://github.com/tribixbite/CustomCamera/blob/main/PRIVACY.md>.

## Contact

Questions: open an issue at <https://github.com/tribixbite/CustomCamera/issues>.
