# Release verification

Verified locally for the release candidate on August 24, 2026:

- application ID: `tech.g24.feresaslicer`;
- version: `0.15.0-alpha.2` (`versionCode` 30);
- minimum Android version: API 28;
- target and compile SDK: API 36;
- delivery ABI: `arm64-v8a`;
- viewer test suite: passed, 32/32 tests;
- JVM unit tests: passed;
- `lintRelease`: passed;
- store assets: four current 1080×2160 phone screenshots prepared for each of
  the English and Russian locales;
- permanent upload key: configured; the keystore and password are stored
  outside Git in the macOS user keychain;
- Play App Signing: enabled;
- AGPL source code: publicly available at
  <https://github.com/AMVavilov/feresa-slicer>.

## Final artifact record

- release AAB: `app/build/outputs/bundle/release/app-release.aab`;
  - Bundletool 1.18.0 validation: passed;
  - upload certificate SHA-256: `22:82:75:CC:B1:84:1C:D9:21:AB:45:A5:14:DE:DA:11:32:3F:81:04:0D:7A:BC:2A:F7:B9:F9:28:8D:3F:C0:BB`;
  - SHA-256: `f58049ac93841845159e2fb04147905b0c715b587dcf892bae276073d6b5d765`;
  - size: 41,820,888 bytes;
- release APK: `app/build/outputs/apk/release/app-release.apk`;
  - install/smoke test: passed on an API 35 ARM64 Google Play emulator;
  - APK Signature Scheme v2 verification: passed;
  - SHA-256: `ec7311934a3bdf44671830f08c5cd0841551aa8ca31c739e521a8eb9b284edde`;
  - size: 114,725,894 bytes.

The final AAB must be signed with the permanent upload key documented in
`release-signing.md`. The APK is a direct-distribution verification artifact;
Google Play receives the AAB and produces device-specific APKs through Play App
Signing.

## Known release constraints

- Native delivery is ARM64-only. Google Play will exclude incompatible devices;
  ChromeOS x86_64 coverage is not guaranteed.
- Current Orca native libraries use 4 KB ELF alignment and rely on Android's
  16 KB compatibility mode. They must be rebuilt with 16 KB alignment before
  the enforcement date documented in the release checklist.
- The privacy-policy URL must remain publicly reachable for closed, open, and
  production submissions.
