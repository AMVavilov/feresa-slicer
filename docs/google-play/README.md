# Google Play release checklist

This directory contains the project-side material for the Russian and English
Google Play listing. Play Console remains the source of truth for policy forms.

Target release: `0.15.0-alpha.2` (`versionCode` 30).

## Release gates

- [x] Package name fixed as `tech.g24.feresaslicer`.
- [x] `compileSdk` and `targetSdk` set to API 36.
- [x] Android App Bundle release task configured.
- [x] Russian and English app/store metadata prepared.
- [x] Privacy policy and Data safety draft prepared.
- [x] Ads declaration: no ads.
- [x] Only the `INTERNET` permission is requested.
- [x] Viewer test suite passed: 32/32 tests.
- [x] JVM unit tests passed.
- [x] `lintRelease` passed.
- [x] 512×512 icon, 1024×500 feature graphic, and four current phone
      screenshots prepared for each locale.
- [x] Permanent upload key created; the keystore and password are stored
      outside Git in the macOS user keychain.
- [x] Play App Signing enabled in Play Console.
- [x] AGPL source repository is public at
      <https://github.com/AMVavilov/feresa-slicer> and linked from the app.
- [x] Build the final upload-signed `app-release.aab`, validate it with
      Bundletool, and record its SHA-256 and size in `verification.md`.
- [ ] Upload the final signed `app-release.aab`.
- [ ] Upload the prepared localized text and graphics from `fastlane/metadata`.
- [ ] Complete App content declarations using `app-content.md` and
      `data-safety.md`.
- [ ] Run internal testing, followed by closed testing if required for the
      developer account.
- [ ] Test the Play-delivered build on at least one ARM64 physical phone and a
      real printer before production rollout.

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
- The bundled Orca native libraries currently rely on Android's 16 KB
  compatibility mode. Rebuild them with 16 KB ELF alignment before the Google
  Play enforcement date of February 1, 2027.

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
