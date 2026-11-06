# CustomCamera — Google Play release: everything you still need to do

This is the single checklist for publishing. Everything Claude could do is done
(see **Already done** at the bottom); the numbered steps below need you because
they involve secrets, your Google account, or a judgement call. Run shell
commands from the repo root (`~/git/CustomCamera`).

| | |
| --- | --- |
| Application ID | `tribixbite.customcamera` (permanent once uploaded) |
| Version | 3.0.0, versionCode 30000 (`version.properties`) |
| Target / min SDK | 36 / 26 |
| Privacy policy URL | `https://github.com/tribixbite/CustomCamera/blob/main/PRIVACY.md` |
| Category | Photography |

---

## 1. Create the release key and CI secrets (no CustomCamera key exists yet)

Checked on 2026-11-06: the keystores on this device belong to other apps
(`~/.android/cleverkeys-release.keystore`, `~/git/carify-release.jks`,
`~/git/embeddy-release.jks`, …) and the `tribixbite/CustomCamera` repo has no
signing secrets. One key signs every channel (GitHub APKs, CI builds, and Play
once exported in step 5b), as with Embeddy, so users can switch between channels
without uninstalling.

```bash
scripts/generate-release-key.sh
```

It runs `keytool` (prompts for the password and a name), writes
`~/git/customcamera-release.jks` outside the repo, copies a backup to
`~/storage/shared/Documents/`, stores `CC_KEYSTORE_B64`, `CC_KEY_ALIAS`,
`CC_KEYSTORE_PASSWORD` and `CC_KEY_PASSWORD` in the GitHub repo (prompting for the
passwords), and prints the **SHA-256 certificate fingerprint** that Play Console
and Android developer verification ask for. Read it again any time with:

```bash
scripts/generate-release-key.sh --fingerprint
```

Keep a copy of the `.jks` and its password off this phone: a GitHub secret
cannot be read back, and losing the key means installed copies never update.

For local signed builds, create `keystore.properties` (gitignored) in the repo root:

```
storeFile=/data/data/com.termux/files/home/git/customcamera-release.jks
storePassword=YOUR_PASSWORD
keyAlias=customcamera
keyPassword=YOUR_PASSWORD
```

> Use `keystore.properties` locally rather than `CC_KEYSTORE_*` environment
> variables: on Termux an already-running Gradle daemon keeps its startup
> environment and ignores new env vars (add `--no-daemon` if you must use them).

## 2. What CI builds

- Every push (`android.yml`): artifact `customcamera-v<version>-apk` holds a debug
  APK and, once the secrets exist, `CustomCamera-v<version>-<sha>.apk` signed with
  the release key, plus `SHA256SUMS.txt`. The log prints each APK's certificate.
- A `v*` tag (`release.yml`): tests, lint, the signed APK and AAB; publishes a
  GitHub Release with the APK; the AAB + R8 mapping are the `…-play` artifact.
  The tag must equal `version.properties` (`v3.0.0`) or the run fails.

## 3. Build the signed bundle

Either locally:

```bash
scripts/gradle-locked.sh -PrequireReleaseSigning \
  :app:testDebugUnitTest :app:lintRelease :app:bundleRelease
keytool -printcert -jarfile app/build/outputs/bundle/release/app-release.aab | head -6
# Owner must NOT be "Android Debug"
```

…or in CI by tagging (fails rather than produce a debug-signed build):

```bash
git tag -a v3.0.0 -m "CustomCamera 3.0.0" && git push origin v3.0.0
gh run list -R tribixbite/CustomCamera --workflow release.yml -L 1
gh run download -R tribixbite/CustomCamera -n customcamera-v3.0.0-play -D dist/
```

The R8 mapping file is embedded in the bundle
(`BUNDLE-METADATA/…/proguard.map`), so Play deobfuscates crashes automatically.

## 4. Phone screenshots (need a lit scene)

Play needs at least 2 (up to 8) phone screenshots; 1080×1920 or larger keeps the
listing eligible for promotion. Every screenshot taken during development was
black because the test phone faced a dark surface. On any test phone with the
debug build installed, point it at something bright and run:

```bash
D=192.168.0.216:5555   # Pixel 8 Pro; or the Saga 192.168.1.243:5555
OUT=fastlane/metadata/android/en-US/images/phoneScreenshots; mkdir -p $OUT
for n in 1 2 3 4 5; do
  read -p "Set up screen $n (Photo, Pro, Video, Scan, Gallery…) then press Enter "
  adb -s $D exec-out screencap -p > $OUT/$n.png
done
```

## 5. Play Console

### 5a. Create the app

Play Console → **Create app** → name `CustomCamera`, default language English
(US), **App**, **Free**, accept the declarations.

**Gate for new personal accounts:** if your developer account is a *personal*
account created after 13 Nov 2023, production is locked until a **closed test
has run with ≥ 12 testers opted in for 14 consecutive days**, after which you
apply for production access (Dashboard → *Apply for production*). Organisation
accounts are exempt. The requirement is shown on the new app's **Dashboard**
when it applies — check there right after creating the app, and start the closed
test early because it is the longest step.

### 5b. Upload

**Testing → Internal testing → Create release** → upload
`app-release.aab` → at **App signing**, choose **Use a different key → Export and
upload a key from Java keystore** so Play signs with the same key as the GitHub
APKs (step 1). The page gives `pepk.jar` and `encryption_public_key.pem`; save both
to `~/storage/shared/Download/`, then on this phone:

```bash
java -jar ~/storage/shared/Download/pepk.jar \
  --keystore=$HOME/git/customcamera-release.jks --alias=customcamera \
  --output=$HOME/storage/shared/Download/customcamera-signing-key.zip \
  --include-cert --rsa-aes-encryption \
  --encryption-key-path=$HOME/storage/shared/Download/encryption_public_key.pem
# prompts for the keystore and key passwords; upload the zip on the same page
```

(Keeping the Google-generated key also works, but then Play installs and GitHub
APKs are signed differently and cannot update each other.) → release notes: paste
`fastlane/metadata/android/en-US/changelogs/30000.txt` → roll out. Then do the
same on **Closed testing** (required above) and later **Production**.

Print the files to copy from:

```bash
for f in title short_description full_description changelogs/30000; do
  echo "== $f"; cat fastlane/metadata/android/en-US/$f.txt; echo; done
```

### 5c. App content (Policy → App content)

| Form | Answer |
| --- | --- |
| Privacy policy | `https://github.com/tribixbite/CustomCamera/blob/main/PRIVACY.md` |
| App access | All functionality available without special access |
| Ads | No, the app does not contain ads |
| Content rating | Category **Utility, productivity, communication, or other**; answer No to every content question (no user-generated content sharing, no violence, no gambling…) |
| Target audience | 13 and over (do not select under-13 ages: that triggers Families policy) |
| Data safety | See below |
| Government app / Financial features / Health | No / None / None |
| News app | No |

**Data safety** — "Does your app collect or share any of the required user data
types?" → **No**. Justification if asked: the app has no `INTERNET` permission
(removed from the merged manifest), so nothing can be transmitted; photos,
videos and optional location tags are written only to the user's own files on
the device, which Play does not count as collection. "Is all user data encrypted
in transit?" is skipped when nothing is collected; no account creation, so no
deletion URL.

**Permissions declaration:** none required — the app uses no restricted
permissions (no `READ_MEDIA_*`, `MANAGE_EXTERNAL_STORAGE`, background location,
`QUERY_ALL_PACKAGES`, SMS or call log).

### 5d. Main store listing (Grow → Store presence → Main store listing)

| Field | Source |
| --- | --- |
| App name | `title.txt` (29/30 chars) |
| Short description | `short_description.txt` (80/80) |
| Full description | `full_description.txt` (≈2.2k/4000) |
| App icon | `fastlane/metadata/android/en-US/images/icon.png` (512×512 RGBA) |
| Feature graphic | `fastlane/metadata/android/en-US/images/featureGraphic.png` (1024×500) |
| Phone screenshots | step 4 |
| Category / tags | Photography; tags: Camera, Photography, QR & barcode scanner |
| Contact email | your support address (required, shown publicly) |
| Website (optional) | `https://tribixbite.com` |

Regenerate graphics after design changes with `scripts/generate-store-assets.sh`.

## 6. Every later release

1. Bump `version.properties` (e.g. `VERSION_PATCH=1` → versionCode 30001).
2. Add the release notes file for that versionCode:

```bash
$EDITOR fastlane/metadata/android/en-US/changelogs/30001.txt
git add -A && git commit -m "chore: release 3.0.1"
git tag -a v3.0.1 -m "CustomCamera 3.0.1" && git push origin main v3.0.1
```

3. Download the CI bundle (step 3) and upload it to the track in Play Console.

---

## Already done (no action needed)

- targetSdk 36 (required since 31 Aug 2026), App Bundle with language/density/ABI splits
- 16 KB page-size compliance verified: every 64-bit native library is 16 KB aligned
- R8 + resource shrinking; `staging` build type for testing the shrunk app on a device
- Signing config reads `keystore.properties` or `CC_KEYSTORE_*`; `-PrequireReleaseSigning`
  makes a missing key a hard error (verified both paths with a throwaway key)
- No `INTERNET` permission; privacy policy (`PRIVACY.md`) matches the manifest
- Store text and graphics in `fastlane/metadata/android/en-US/`
- CI (`android.yml`: tests, lint, builds on every push) and tag-triggered signed
  bundle workflow (`release.yml`)
- Application ID `tribixbite.customcamera` has no existing Play listing (checked 2026-11-01)
- Large screens: the viewfinder is not orientation-locked on ≥600dp screens (Play's large-screen
  quality checks flag locked orientation); landscape layout verified on a simulated tablet
