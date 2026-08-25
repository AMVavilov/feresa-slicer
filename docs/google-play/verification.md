# Release verification

The following results belong to the superseded version code 30 candidate and
must not be used to approve version code 31. They are retained as a historical
artifact record:

- application ID: `tech.g24.feresaslicer`;
- version: `0.15.0-alpha.2` (`versionCode` 30);
- minimum Android version: API 28;
- target and compile SDK: API 36;
- delivery ABI: `arm64-v8a`;
- viewer test suite: passed (historical command output retained separately; the
  changing test count is not hard-coded here);
- JVM unit tests: passed;
- `lintRelease`: passed;
- store assets: four current 1080×2160 phone screenshots prepared for each of
  the English and Russian locales;
- permanent upload key: configured; the keystore and password are stored
  outside Git in the macOS user keychain;
- Play App Signing: enabled;
- AGPL source code: publicly available at
  <https://github.com/AMVavilov/feresa-slicer>.

## Superseded version code 30 artifact record

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

These artifacts must not be uploaded as version code 31. The replacement AAB
must be signed with the permanent upload key documented in
`release-signing.md`. The APK is a direct-distribution verification artifact;
Google Play receives the AAB and produces device-specific APKs through Play App
Signing.

## Version code 31 artifact record (legacy gate evidence)

The replacement release uses the source-built no-OCCT ARM64 engine based on
OrcaSlicer Mobile commit
`6fc2e14b9a222301f4432cee26d7ab37d3be86d0`, built with NDK r28c.

- release AAB: `app/build/outputs/bundle/release/app-release.aab`;
  - version: `0.16.0-alpha.1` (`versionCode` 31);
  - Bundletool 1.18.3 validation: passed;
  - BundleConfig native alignment: `PAGE_ALIGNMENT_16K`;
  - upload certificate SHA-256: `22:82:75:CC:B1:84:1C:D9:21:AB:45:A5:14:DE:DA:11:32:3F:81:04:0D:7A:BC:2A:F7:B9:F9:28:8D:3F:C0:BB`;
  - SHA-256: `6b0d5eab086fb7b91495d0d51329c951149dc636749d9dbc18ef23e54de5d3a5`;
  - size: 19,555,499 bytes;
- release APK: `app/build/outputs/apk/release/app-release.apk`;
  - APK signature and 16 KiB ZIP alignment verification: passed;
  - SHA-256: `aca68894c4f91d2bc1fcbf10ab34af8bddce76d895495d5dcc01624aa20ffaa1`;
  - size: 44,199,259 bytes;
- tested debug APK: `releases/feresa-slicer-latest.apk`;
  - SHA-256: `c7e0b8f1585b95c8239cb248f2b069ff943ceef29d9fde8e139e9f82d10caea1`;
  - size: 68,314,330 bytes.

The following checks were recorded under the legacy single-device gate:

- the native archive and every engine library match the hashes pinned in
  `scripts/fetch-orca-mobile-engine.sh`;
- `scripts/verify-16kb-aab.sh` passes the AAB and APK without exceptions;
- `Native.isLoaded()` and the real Orca slicing instrumentation tests passed on
  an ARM64 emulator whose `getconf PAGE_SIZE` reports `16384`;
- the Orca parity instrumentation suite passed 6/6 tests;
- the complete instrumentation suite passed 11/11 tests;
- the signed release APK was cold-started on the official Android 16 KiB system
  image without linker, JNI, or native crash errors.

This evidence does **not** satisfy the current hardened gate. It lacks a clean
tree/commit report, a distinct 4 KiB instrumentation run, instrumentation logs
from both page-size targets, exact signed APK PID/logcat launch-smoke evidence
from both targets, and the documented manual matrix. The instrumentation test
APK is also not evidence that the exact upload-signed APK performed an automatic
slice.

Current audit status for any new upload or promotion based on this record:

- automated gate: `INCOMPLETE`;
- manual matrix: `REQUIRED`;
- Play-delivered internal-test matrix: `REQUIRED`;
- upload/promotion authorization: `INCOMPLETE`.

The Play-generated APK must be tested through the internal-test track and linked
in the immutable report before a human authorizes any later promotion. It is not
a post-publication substitute for pre-release evidence.

## August 25, 2026 crash-regression development run

This is development evidence for the new pre-Play suite, not release approval.
The current sources were copied to a resident non-iCloud test checkout and run on
the API 36 ARM64 emulator `emulator-5554`, which reported `PAGE_SIZE=4096`:

- viewer JavaScript tests: passed;
- host native CTest: 1/1 passed;
- debug and release JVM unit tests: passed;
- `lintRelease`: passed;
- debug instrumentation: 15/15 passed, 0 failed/errors/skipped;
- minified `releaseTest` instrumentation: 15/15 passed, 0 failed/errors/skipped;
- the new real Model-screen slice action completed, produced non-empty G-code,
  and the production WebView reported more than 1,000 rendered segments;
- the repeated production-pipeline memory/native/WebView regression tests passed.

This run remains `INCOMPLETE` for upload because it did not use a second 16 KiB
target, an exact clean commit, the exact signed APK/AAB, the manual matrix, or the
Play-delivered internal-test artifact. No Play upload is authorized by this
section.

## Google Play submission

This is a historical console event, not proof that the new hardened automated,
manual, and Play-delivered gates passed and not a precedent for future upload
approval.

- submitted on August 24, 2026 to the production track as a 100% rollout;
- Play Console accepted version `31 (0.16.0-alpha.1)`, target SDK 36, and the
  `arm64-v8a` delivery ABI;
- the release review reported `Ready to release` and no longer reported the
  16 KiB memory-page compatibility error from version code 30;
- Google Play's pre-submission common-problem check completed without findings;
- the release and localized store-listing changes are now under Google review.

## Known release constraints

- Native delivery is ARM64-only. Google Play will exclude incompatible devices;
  ChromeOS x86_64 coverage is not guaranteed.
- The version code 30 Orca libraries used 4 KiB ELF alignment and are
  superseded. Version code 31 must ship only the verified r28c 16 KiB engine
  set described above.
- The privacy-policy URL must remain publicly reachable for closed, open, and
  production submissions.
