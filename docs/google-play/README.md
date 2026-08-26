# Google Play release checklist

This directory contains the project-side material for the Russian and English
Google Play listing. Play Console remains the source of truth for policy forms.

Before every upload or track promotion, run and record the reusable
[Pre-Play release testing](pre-release-testing.md) gate. Checked build and
console items below are historical facts about version code 31; they do not
replace a fresh test report, the documented manual matrix, Play-delivered
testing, or human authorization for the exact signed artifacts.

Target release: `0.17.0-alpha.1` (`versionCode` 33).

## Release gates

- [x] Package name fixed as `tech.g24.feresaslicer`.
- [x] `compileSdk` and `targetSdk` set to API 36.
- [x] Android App Bundle release task configured.
- [x] Russian and English app/store metadata prepared.
- [x] Privacy policy and Data safety draft prepared.
- [x] Ads declaration: no ads.
- [x] Only the `INTERNET` permission is requested.
- [x] Viewer test suite passed (retain the command output as evidence; do not
  hard-code the changing test count here).
- [x] JVM unit tests passed.
- [x] `lintRelease` passed.
- [x] 512×512 icon, 1024×500 feature graphic, and four current phone
      screenshots prepared for each locale.
- [x] Permanent upload key created; the keystore and password are stored
      outside Git in the macOS user keychain.
- [x] Play App Signing enabled in Play Console.
- [x] AGPL source repository is public at
      <https://github.com/AMVavilov/feresa-slicer> and linked from the app.
- [x] Rebuild the ARM64 Orca engine from pinned source with NDK r28c and no
      OCCT runtime dependency.
- [x] Build the final upload-signed `app-release.aab` and APK, run
      `scripts/verify-16kb-aab.sh`, and record their SHA-256 values and sizes in
      `verification.md`.
- [x] Historical only: version code 31's signed `app-release.aab` was submitted
      before the hardened two-device gate was adopted. This is not evidence of
      a current automated or human approval.
- [x] Upload the prepared localized text and graphics from `fastlane/metadata`.
- [x] Complete App content declarations using `app-content.md` and
      `data-safety.md`.
- [ ] Run the hardened automated gate from a clean commit on distinct ARM64
      4 KiB and 16 KiB targets and retain both instrumentation and exact-signed
      APK launch-smoke evidence.
- [ ] Complete and link the manual release matrix for the exact signed APK.
- [ ] Run internal testing, followed by closed testing if required for the
      developer account.
- [ ] Test the Play-delivered build on the documented device matrix, including
      at least one ARM64 physical phone and a real printer before production
      rollout.
- [ ] Record human upload/promotion authorization only after the automated,
      manual, and Play-delivered stages are complete.

## Current policy baseline (August 21, 2026)

- New mobile apps and updates must target Android 16 / API 36 from August 31,
  2026.
- Google Play delivers new apps as Android App Bundles. The maximum compressed
  download generated for one device is 200 MB.
- A public privacy-policy URL and a completed Data safety form are required for
  closed, open, and production tracks. An internal-only track is exempt from the
  Data safety display, but preparing it before review is recommended.
- Personal developer accounts created after November 13, 2023 normally need a
  closed test with at least 12 opted-in testers for 14 continuous days before
  production access.
- Version code 31 uses a source-built ARM64 Orca engine and NDK r28c runtime.
  Every packaged ELF and Play-generated APK must pass the 16 KiB release gate;
  do not reuse the version code 30 native artifacts that relied on Android's
  compatibility mode.

Official references:

- <https://support.google.com/googleplay/android-developer/answer/11926878>
- <https://support.google.com/googleplay/android-developer/answer/9859152>
- <https://support.google.com/googleplay/android-developer/answer/9866151>
- <https://support.google.com/googleplay/android-developer/answer/10787469>
- <https://developer.android.com/guide/practices/page-sizes>
- <https://support.google.com/googleplay/android-developer/answer/14151465>

## Metadata locations

Localized text and release notes use the conventional Fastlane directory layout:

```text
fastlane/metadata/android/en-US/
fastlane/metadata/android/ru-RU/
```

The files can be copied into Play Console manually even if Fastlane is not used.

The generated images are located in each locale's `images` directory. Each
locale has four phone screenshots using a Play-compatible 1080×2160 (2:1)
aspect ratio.
