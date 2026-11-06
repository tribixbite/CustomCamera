# F-Droid

CustomCamera is prepared for F-Droid's **reproducible-build (published-binary)**
mode: F-Droid builds each release tag from source, compares it with the APK on
GitHub Releases, and if they match apart from the signature it publishes *our*
signed APK. The f-droid.org and GitHub files are then the same bytes, so users
can switch between them without uninstalling, and Android developer verification
only needs our one key (`RELEASING.md` §1–2).

## What is ready

| Requirement | Status |
|---|---|
| FOSS licence | MIT (`LICENSE`) |
| No proprietary dependencies | ML Kit replaced by ZXing (after 3.0.0); no Play Services, Firebase, analytics, ads |
| No network | No `INTERNET` permission in the merged manifest |
| Version from the tag alone | `version.properties` is the only source; no env vars or CI numbers |
| Unsigned keyless build | `assembleRelease` without a key → `app-release-unsigned.apk` |
| No dependency-metadata block | `dependenciesInfo { includeInApk = false }` |
| Reproducibility check | `release.yml` job `reproducible` (below) |
| Listing | `fastlane/metadata/android/en-US/` (text, icon, feature graphic, 5 screenshots, changelogs) |
| Recipe | `fdroid/tribixbite.customcamera.yml` (draft, targets v3.1.0) |

## Reproducibility check

Before a tag becomes a GitHub Release, `release.yml` rebuilds it the way F-Droid
will — JDK 17 instead of CI's 21, a different checkout path, no Gradle build
cache, no signing key — and runs `apksigcopier compare --unsigned` (the tool
F-Droid uses) between that unsigned build and the signed APK. The `publish` job
depends on it, so a release that F-Droid could not verify is never published.
Run it without tagging from the Actions tab (`Release` → *Run workflow*); that
builds and compares but publishes nothing.

What it cannot rehearse: F-Droid's build server image (Debian, its own SDK
install). If F-Droid's build differs, `fdroid build` prints the differing
entries; the usual culprits are a different JDK major (add a `sudo:` step that
installs `openjdk-21-jdk-headless`) or build-tools version.

## Versioning and update checks

versionCode = MAJOR·10000 + MINOR·100 + PATCH (one universal APK, no ABI
suffixes). Because it is computed in `app/build.gradle.kts`, F-Droid cannot read
it from source, so the recipe uses `UpdateCheckMode: HTTP`: every GitHub Release
body starts with

```
Version: 3.1.0
VersionCode: 30100
```

and `UpdateCheckData` matches those two lines exactly. Rewording them in
`release.yml` silently stops F-Droid noticing new releases.

Each release needs `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`
(≤ 500 characters) in the tagged commit.

## Submitting (your account, public)

F-Droid submission happens on F-Droid's GitLab and posts publicly under your
name, so it is left to you:

1. Tag the first ZXing release (`v3.1.0`) and confirm `release.yml` passed,
   including the `reproducible` job.
2. Optionally open a Request For Packaging issue:
   <https://gitlab.com/fdroid/rfp/-/issues> ("App Inclusion" template).
3. Fork <https://gitlab.com/fdroid/fdroiddata>, copy
   `fdroid/tribixbite.customcamera.yml` to `metadata/tribixbite.customcamera.yml`,
   and open a merge request.
4. Locally (with fdroidserver) before the MR, if possible:

   ```sh
   fdroid lint tribixbite.customcamera
   fdroid readmeta
   fdroid build -v -l tribixbite.customcamera
   ```

## Anti-features

None expected: no network access at all, no tracking, no non-free dependencies,
no non-free assets. Worth stating in the merge request.
