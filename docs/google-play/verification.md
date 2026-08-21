# Release verification

Verified locally on August 21, 2026:

- application ID: `tech.g24.feresaslicer`;
- version: `0.14.0-alpha.1` (`versionCode` 27);
- minimum Android version: API 28;
- target and compile SDK: API 36;
- delivery ABI: `arm64-v8a`;
- debug unit tests: passed;
- debug and release Lint: passed with warnings only;
- debug APK: assembled and launched on an Android 16 / API 36 emulator;
- English and Russian UI: manually opened and captured on the emulator;
- release Android App Bundle: built, signed with a disposable local verification
  key, and accepted by Bundletool `validate`;
- release AAB size: approximately 39 MB.

The disposable verification key is not an upload key and the locally generated
release AAB must not be submitted to Google Play. Create the permanent upload
key as described in `release-signing.md` and rebuild the bundle before upload.

## Known release constraints

- Native delivery is ARM64-only. Google Play will exclude incompatible devices;
  ChromeOS x86_64 coverage is not guaranteed.
- Current Orca native libraries use 4 KB ELF alignment and rely on Android's
  16 KB compatibility mode. They must be rebuilt with 16 KB alignment before
  the enforcement date documented in the release checklist.
- The privacy-policy and AGPL source URLs must be publicly reachable before a
  closed, open, or production submission.
