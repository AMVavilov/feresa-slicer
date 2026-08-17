# Contributing to Feresa Slicer

## Shared workflow

1. Update `main` before starting work.
2. Create a short-lived branch such as `feature/toolpath-preview` or
   `fix/profile-import`.
3. Keep commits focused and describe the user-visible result.
4. Open a pull request into `main` and wait for the Android build check.
5. Merge only after the branch builds and the changed screen or flow has been
   tested on an emulator or device.

Avoid working directly on the same branch from two computers. Separate
branches make concurrent work reviewable and prevent accidental overwrites.

## Local and sensitive files

Never commit:

- `local.properties` or machine-specific Android SDK paths;
- OrcaCloud tokens, cookies, exported personal profile caches, or user IDs;
- printer IP addresses, API keys, passwords, or private certificates;
- Android signing keystores or their passwords;
- APK, AAB, ZIP, Gradle, CMake, IDE, or `node_modules` build artifacts.

Use synthetic values in tests and documentation. Public identifiers already
documented in OrcaSlicer's AGPL source must be clearly marked as public and
must never be confused with private credentials.

## Verification

For Android changes, run:

```bash
./gradlew :app:assembleDebug lintDebug
```

For viewer changes, also run:

```bash
npm ci
npm run test:viewer
npm run build:viewer
```

The generated viewer bundle is committed so an Android-only checkout does not
need Node.js unless the viewer source changes.

## License and attribution

Contributions are accepted under GNU AGPL version 3. Preserve the SPDX headers,
`NOTICE.md`, and `THIRD_PARTY_NOTICES.md`. New third-party code or data must
have a compatible license and its source and license must be documented.
