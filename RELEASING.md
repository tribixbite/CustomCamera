# CustomCamera — releasing (GitHub Releases + F-Droid)

The single checklist for publishing. Distribution is **GitHub Releases** and
**F-Droid** (reproducible builds: F-Droid republishes our own signed APK), plus
the package registration Android's **developer verification** requires. Google
Play is not used. Run commands from the repo root (`~/git/CustomCamera`).

| | |
| --- | --- |
| Application ID | `tribixbite.customcamera` (permanent) |
| Version | `version.properties`; versionCode = MAJOR·10000 + MINOR·100 + PATCH |
| Target / min SDK | 36 / 26 |
| Licence | MIT (`LICENSE`) |
| Privacy policy | `https://github.com/tribixbite/CustomCamera/blob/main/PRIVACY.md` |

## 1. Release key — done 2026-11-06

`~/git/customcamera-release.jks` (alias `customcamera`, backup in
`~/storage/shared/Documents/`), created with `scripts/generate-release-key.sh`,
which also set the `CC_KEYSTORE_B64`, `CC_KEY_ALIAS`, `CC_KEYSTORE_PASSWORD` and
`CC_KEY_PASSWORD` repo secrets. Certificate SHA-256:

```
B9:75:D2:E1:72:AA:17:83:F0:5A:83:1D:EA:86:D3:F2:20:C7:60:2C:41:55:A3:BE:6A:54:84:D8:1E:72:77:CB
```

Every CI-built release APK must show this (`apksigner verify --print-certs`); F-Droid
pins it in `AllowedAPKSigningKeys`. Read it again with
`scripts/generate-release-key.sh --fingerprint`. Keep the `.jks` and password off this
phone too: a GitHub secret cannot be read back, and a lost key means installed
copies can never update.

For local signed builds create `keystore.properties` (gitignored):

```
storeFile=/data/data/com.termux/files/home/git/customcamera-release.jks
storePassword=YOUR_PASSWORD
keyAlias=customcamera
keyPassword=YOUR_PASSWORD
```

Without it, `assembleRelease` produces an **unsigned** APK (what F-Droid builds);
use `:app:assembleStaging` for an installable shrunk build.

## 2. Android developer verification (package registration)

In the Android Developer Console: **Register package name** →
`tribixbite.customcamera` → add the key by the SHA-256 above. To prove you own
the key the console gives a snippet: send it to Claude (or put it in
`app/src/main/assets/adi-registration.properties`, exact name, no `.txt`), build
a signed release (`scripts/gradle-locked.sh :app:assembleRelease` with
`keystore.properties` present), check it, and upload that APK on the same page:

```bash
apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk | awk '/SHA-256/'
unzip -p app/build/outputs/apk/release/app-release.apk assets/adi-registration.properties
```

The proof APK is a one-off; do not commit the snippet into a release unless the
console asks for it to stay. F-Droid needs no extra key here: in reproducible
mode it distributes our APK, signed with this same key.

## 3. Cutting a release

1. Bump `version.properties` and add release notes for the new versionCode
   (F-Droid shows them; 500 characters max):

   ```bash
   $EDITOR fastlane/metadata/android/en-US/changelogs/<versionCode>.txt
   git commit -am "chore: release X.Y.Z"
   git tag -a vX.Y.Z -m "CustomCamera X.Y.Z" && git push origin main vX.Y.Z
   ```

2. `release.yml` then: runs tests + lint and builds the signed APK; rebuilds the
   tag the F-Droid way (JDK 17, other path, no key) and fails unless
   `apksigcopier compare` finds the two identical apart from the signature;
   publishes the GitHub Release with `CustomCamera-vX.Y.Z.apk`, `SHA256SUMS.txt`
   and the `Version:` / `VersionCode:` lines F-Droid's update check reads. The
   tag must equal `version.properties` or the run stops.

3. Once F-Droid carries the app it picks up new tags on its own
   (`AutoUpdateMode`), builds them, and publishes our APK if it matches.

Every push also uploads `customcamera-v<ver>-apk` from `android.yml`: a debug APK
and a release-key-signed APK for testing.

## 4. F-Droid submission

See [`fdroid/README.md`](fdroid/README.md). The recipe draft is
`fdroid/tribixbite.customcamera.yml`; submission is a merge request to
`gitlab.com/fdroid/fdroiddata` under your account.

## Listing assets

`fastlane/metadata/android/en-US/` holds the title, short and full description,
changelogs, `images/icon.png`, `images/featureGraphic.png` and
`images/phoneScreenshots/1–5.jpg` (Pixel 8 Pro, 2026-11-06: Photo, Scan, Pro,
viewer, Settings). F-Droid reads them from the tagged commit. Regenerate the icon
and feature graphic with `scripts/generate-store-assets.sh`.

## Already done

- No proprietary dependencies: ML Kit was replaced by ZXing after 3.0.0; no Google
  Play Services, analytics, ads or crash reporting.
- No `INTERNET` permission (merged manifest checked); `PRIVACY.md` matches.
- Dependency-metadata signing block disabled (`dependenciesInfo`).
- R8 + resource shrinking; `staging` build type to test the shrunk app on a device.
- 16 KB page-size compliance: every 64-bit native library is 16 KB aligned.
- targetSdk 36; large screens not orientation-locked.
