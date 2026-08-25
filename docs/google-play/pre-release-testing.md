# Pre-Play release testing

This is the reusable release gate for every Google Play upload. Run it against
the exact commit and the exact signed artifacts that will be uploaded. A green
CI build by itself is not release approval: the packaged Orca engine, the
minified release application, and the Play-delivered split APK must also be
tested on ARM64 Android.

The mandatory entry point is one command from a clean, non-iCloud checkout:

```bash
scripts/pre-play-release.sh \
  --device emulator-5554 \
  --device-16k emulator-5556
```

It runs the automated sections below, writes an immutable timestamped report to
`app/build/reports/pre-play/`, and refuses a complete automated result unless two
distinct ARM64 targets pass: one with 4 KiB pages and one with 16 KiB pages. If
either option is omitted, the gate inspects online `adb` targets and assigns a
unique target by its reported `PAGE_SIZE`; an explicitly named target must still
match the option's page-size role. `--skip-device` is preparation only and
explicitly produces an `INCOMPLETE` result that must not be uploaded to Play.

An automated success is recorded as `AUTOMATED PASSED / MANUAL +
PLAY-DELIVERED REQUIRED`. It is not upload approval. Approval remains incomplete
until the documented manual matrix and the Play-delivered internal-test checks
are complete and linked from the immutable release report.

Do not copy user models, cloud profiles, printer credentials, or tokens into the
repository or a test report. A model that reproduces a crash may become a test
fixture only when its license and provenance permit redistribution. Otherwise,
create a small synthetic reproduction and record the original issue privately.

## 1. Candidate identity and clean checkout

Record the commit, version, and clean-tree state before testing:

```bash
git rev-parse HEAD
git status --short
grep -nE 'versionCode|versionName|targetSdk|minSdk' app/build.gradle.kts
```

Run the gate from a clean checkout. On macOS, prefer a local non-iCloud build
directory or checkout so evicted/generated files cannot make a release build
hang or silently use stale output. A non-empty `git status --porcelain` is a hard
failure for the complete gate; the report records the commit, branch, source
`versionName`/`versionCode`, and the clean-tree assertion before doing any build.

## 2. Fast automated checks

### Viewer JavaScript

```bash
npm ci
npm run test:viewer
npm run build:viewer
git diff --exit-code -- app/src/main/assets/viewer/viewer.bundle.js
```

The last command proves that the committed WebView bundle was generated from
the current `web-src` sources. Node tests cover parser and scene logic, but they
do not replace the real WebView checks in the manual matrix below.

### JVM tests and Android lint

```bash
./gradlew --no-daemon --stacktrace \
  :app:testDebugUnitTest \
  :app:testReleaseUnitTest \
  :app:lintRelease
```

### Host-native regression tests

```bash
native_build_dir="$(mktemp -d)"
cmake -S app/src/main/cpp -B "$native_build_dir" \
  -DFERESA_SLICER_BUILD_TESTS=ON
cmake --build "$native_build_dir" --parallel
ctest --test-dir "$native_build_dir" --output-on-failure
```

These CTest cases cover the legacy Feresa native core. They are useful, but they
do **not** prove the production Orca `libslic3r` path. The ARM64 instrumentation
suite is the production-native gate.

## 3. ARM64 instrumentation and slicing stability

The complete suite must pass independently on a normal 4 KiB ARM64 Android
target and on the official 16 KiB page-size image. Pass both serials explicitly,
or let the gate select unambiguous online targets by `PAGE_SIZE`:

```bash
scripts/pre-play-release.sh \
  --device "$ARM64_4K_SERIAL" \
  --device-16k "$ARM64_16K_SERIAL"
```

For each role the gate records the serial, model/API, ABI list, primary ABI, and
actual page size, then sets `ANDROID_SERIAL` and runs the complete minified
`releaseTest` instrumentation suite. The two serials must be distinct, online,
report `arm64-v8a`, and report exactly `4096` and `16384`, respectively. An
ambiguous auto-selection is a failure, not a reason to pick the first emulator.
ABI and page size are revalidated immediately before and after each device run.
Fresh instrumentation XML is copied into the timestamped report before the
second device can overwrite Gradle's connected-test output; zero tests, skipped
tests, or missing `OrcaNativeParityInstrumentedTest`,
`PrePlaySlicingInstrumentedTest`, or `PrePlayModelScreenE2ETest` evidence is a
failure.

Required results:

- ABI is `arm64-v8a`;
- every instrumentation test passes, including `OrcaNativeParityInstrumentedTest`,
  `PrePlaySlicingInstrumentedTest`, and `PrePlayModelScreenE2ETest`;
- the representative plate is sliced repeatedly in one process;
- the real Model-screen Compose button starts the production pipeline and publishes
  a usable G-code result back to the real screen;
- support, wall, infill, selected filament, multi-object placement, XYZ rotation,
  and non-uniform scale reach the packaged Orca engine;
- generated G-code is non-empty, has layers and extrusion, and contains no NaN
  or infinity;
- the application process survives every slice within the documented memory
  budget.

Every direct native regression slice has a 90-second watchdog. The Model-screen
end-to-end flow has a 180-second UI deadline. A timeout is a failure; Android Test
Orchestrator then tears down that isolated test process so a stuck JNI call cannot
contaminate or indefinitely block the remaining suite.

The release regression set must include the exact current crash shape, or a
redistributable synthetic equivalent, in
`app/src/androidTest/assets/release-fixtures/`. Store a short README beside it
with source, license, SHA-256, expected settings, and the issue it prevents.
Small cube-only tests are not sufficient.

### 16 KiB runtime target

The project has an ARM64 `Feresa_16K_API_36` AVD based on the official
`google_apis_ps16k` image. Before accepting its test result, assert the page size:

```bash
page_size="$(adb -s "$ANDROID_SERIAL" shell getconf PAGE_SIZE | tr -d '\r')"
test "$page_size" = "16384"
```

A 4 KiB emulator cannot approve the 16 KiB runtime gate even when the APK's ELF
alignment is correct.

## 4. Build and verify the signed release artifacts

Configure the permanent upload key as described in `release-signing.md`. Fetch
credentials from the team secret manager; never print or commit them.

```bash
./gradlew --no-daemon --stacktrace \
  :app:assembleRelease \
  :app:bundleRelease

export RELEASE_APK=app/build/outputs/apk/release/app-release.apk
export RELEASE_AAB=app/build/outputs/bundle/release/app-release.aab

jarsigner -verify "$RELEASE_AAB"
keytool -printcert -jarfile "$RELEASE_AAB"
apksigner verify --verbose --print-certs "$RELEASE_APK"
java -jar "$BUNDLETOOL_JAR" validate --bundle="$RELEASE_AAB"
BUNDLETOOL_JAR="$BUNDLETOOL_JAR" scripts/verify-16kb-aab.sh \
  --aab "$RELEASE_AAB" \
  --apk "$RELEASE_APK"
shasum -a 256 "$RELEASE_AAB" "$RELEASE_APK"
```

The gate pins Bundletool 1.18.3 at SHA-256
`a099cfa1543f55593bc2ed16a70a7c67fe54b1747bb7301f37fdfd6d91028e29`.
An external `BUNDLETOOL_JAR` must match that digest exactly; readability alone
is not sufficient. The safe public certificate SHA-256 is recorded for both
APK and AAB and must match the pinned permanent upload certificate
`228275ccb1841cd921ab45a514deda11323f81040d7abc2af7b9f9288d3fc0bb`.
Because an Android upload certificate is normally self-signed, the gate first
pins that public digest, imports only that public certificate into a temporary
report-local truststore, and then runs strict JAR verification. It never treats
all `jarsigner -strict` status-4 warnings as acceptable. Do not accept the 16
KiB script's optional "bundletool unavailable" warning as a pass; BundleConfig
must explicitly report `PAGE_ALIGNMENT_16K`.

The gate builds the exact signed Play APK/AAB and separately selects the minified
`releaseTest` instrumentation target with `-Pferesa.testBuildType=releaseTest`.
That target uses the production R8 rules plus a tiny test-only bootstrap keep
file; it catches JNI name changes without changing the Play APK/AAB. It must run
on both page-size targets.

Only after instrumentation passes, the gate installs the byte-for-byte signed
release APK on both targets, force-stops it, cold-starts it, requires a live PID,
keeps the launch window open long enough for input-dispatch ANR reporting, and
scans captured logcat for Java/Kotlin fatal exceptions, ANRs, OOMs, and native
fatal signals. ABI/page size and the PID are required again after the capture.
This is a launch smoke test only. Instrumentation does **not** run inside the
exact signed release APK, and the gate must not claim that it automatically
sliced with that APK. Exact-APK slicing scenarios remain explicit manual-matrix
work.

A condensed equivalent of the per-device smoke is:

```bash
for ANDROID_SERIAL in "$ARM64_4K_SERIAL" "$ARM64_16K_SERIAL"; do
  # releaseTest instrumentation can leave a differently signed target package.
  adb -s "$ANDROID_SERIAL" uninstall tech.g24.feresaslicer.test || true
  adb -s "$ANDROID_SERIAL" uninstall tech.g24.feresaslicer || true
  adb -s "$ANDROID_SERIAL" install "$RELEASE_APK"
  adb -s "$ANDROID_SERIAL" logcat -c
  adb -s "$ANDROID_SERIAL" shell am force-stop tech.g24.feresaslicer
  adb -s "$ANDROID_SERIAL" shell monkey \
    -p tech.g24.feresaslicer -c android.intent.category.LAUNCHER 1
  pid="$(adb -s "$ANDROID_SERIAL" shell pidof tech.g24.feresaslicer | tr -d '\r')"
  test -n "$pid"
  adb -s "$ANDROID_SERIAL" logcat -d -v threadtime \
    > "artifacts/pre-play/logcat-${ANDROID_SERIAL}.txt"
done
```

The implementation scans each captured log, retains the scan result and PID as
evidence, and requires the PID again after capture. Any process death requires
the matching tombstone/native backtrace before a new candidate can be approved.
A Kotlin `runCatching` block cannot catch SIGSEGV, SIGABRT, or low-memory process
death.

## 5. Manual release matrix

Record Pass/Fail and evidence for every row. Test Russian and English where text
is involved, and both light and dark themes for the main workflows.

| ID | Scenario | Required result |
| --- | --- | --- |
| M01 | Clean install and first launch | Starts without authentication, crash, ANR, blank screen, or missing native-engine error. |
| M02 | Upgrade from the current Play build | Install the candidate over the current version; local settings, selected profile IDs, printer data, and imported-project state are preserved. |
| M03 | Settings persistence | Change several Print values, force-stop, relaunch, and restart the device; exact values and dirty overrides remain. |
| M04 | Filament selection | Select PLA, PETG, and two same-named system/vendor variants; the exact profile ID and its temperature, density, diameter, and volumetric limit are applied and restored after relaunch. |
| M05 | Import formats | Import one and several STL, OBJ, and 3MF files; invalid, empty, corrupt, and oversized input fails visibly without process death. |
| M06 | Viewer/WebView | First frame is non-blank; model and bed fit the viewport; orbit, pan, pinch zoom, camera presets, fit-model, fit-bed, orientation change, and return from background work without jumping or stale content. |
| M07 | Model placement | XYZ move, XYZ rotation, non-uniform scale, center, lay-flat/auto-orient, and multi-object arrangement match the preview; out-of-bed geometry blocks slicing. |
| M08 | Repeated exact-APK production slicing | With the exact signed release APK, slice the current crash regression model, a simple cube, support overhang, and transformed multi-object plate on both the 4 KiB and 16 KiB targets. Run at least three consecutive slices with changed settings; all complete and the process remains alive. |
| M09 | Slice interruption pressure | Tap Slice twice, background/foreground the app, rotate the device, and return during a long slice; no duplicate native run, corrupt output, frozen UI, or crash occurs. |
| M10 | G-code preview/export | Layer range, extrusion/travel visibility, line roles, speed/width metadata, and camera work; saved G-code matches the just-finished generation. |
| M11 | Offline/no-account flow | Import, edit, slice, preview, and save without network or OrcaCloud sign-in. |
| M12 | Printer upload | Against a supervised Moonraker and OctoPrint test printer, upload without auto-start first; then confirm one known-safe tiny print. Authentication errors are clear and never expose credentials. |
| M13 | API/device matrix | Pass on API 28, API 36 ARM64 4 KiB, API 36 ARM64 16 KiB, and at least one physical supported ARM64 phone. |
| M14 | Play-delivered artifact | Install from the internal-testing opt-in link, repeat M03, M04, M06, and M08, and verify Play pre-launch report has no crash or ANR. |

For WebGL specifically, verify loading always resolves, a non-empty model renders
a non-background frame, gestures alter the camera, reset/presets are stable,
portrait resizing preserves framing, malformed input has a usable fallback, and
repeated model/toolpath replacement does not steadily grow GPU memory.

## 6. Hard release blockers

Do not upload, promote, or resume rollout when any of the following is true:

- any required JS, JVM, lint, native, instrumentation, build, signing, Bundletool,
  or 16 KiB check failed or was skipped;
- the Git tree was not clean before the gate started;
- a crash, native fatal signal, ANR, tombstone, OOM, blank WebView, or reproducible
  infinite loading state occurred;
- the exact regression model for the reported slicing crash was not tested;
- only an instrumented test build was sliced; the exact signed release APK did
  not pass the documented manual slicing matrix;
- the 4 KiB and 16 KiB devices did not explicitly report page sizes `4096` and
  `16384`, or both serials did not run instrumentation and exact-APK launch smoke;
- settings or the exact printer/process/filament profile selection did not survive
  force-stop, relaunch, or upgrade;
- candidate commit, version code, artifact hash, signing identity, device, logs,
  or manual results are missing from the report;
- the Play-delivered internal-test build was not checked before wider rollout.

Use the internal track first. Start a production rollout only after the
Play-delivered artifact passes; use a staged rollout rather than immediately
sending an unproven native update to 100% of users.

## 7. Release report template

Copy this template into `docs/google-play/verification/<versionCode>.md` (or an
equivalent immutable release artifact). Do not overwrite an earlier report.

```markdown
# Feresa Slicer pre-Play verification

- Date/time/timezone:
- Tester:
- Git commit:
- Branch and clean-tree result:
- Version name / version code:
- Target / minimum SDK:
- Candidate AAB SHA-256 / size:
- Candidate APK SHA-256 / size:
- Upload certificate SHA-256:
- Bundletool version / SHA-256:
- Orca native engine commit / archive SHA-256:

## Automated results

| Gate | Command / target | Result | Tests | Evidence |
| --- | --- | --- | --- | --- |
| Viewer JS | `npm run test:viewer` | | | |
| Bundle freshness | `git diff --exit-code -- ...viewer.bundle.js` | | | |
| JVM debug/release | Gradle | | | |
| Lint release | Gradle | | | |
| Host native | CTest | | | |
| ARM64 4 KiB instrumentation | serial / API / page size | | | |
| ARM64 16 KiB instrumentation | serial / API / page size | | | |
| Signed release build/signature | Gradle / signer | | | |
| AAB/APK 16 KiB artifact checks | verification script | | | |
| Exact signed APK 4 KiB launch smoke | install / cold-start / PID / logcat | | | |
| Exact signed APK 16 KiB launch smoke | install / cold-start / PID / logcat | | | |
| Final candidate identity | APK/AAB re-hash / commit / clean tree | | | |

## Manual results

| ID | Device/build | Result | Evidence / issue |
| --- | --- | --- | --- |
| M01 | | | |
| M02 | | | |
| M03 | | | |
| M04 | | | |
| M05 | | | |
| M06 | | | |
| M07 | | | |
| M08 | | | |
| M09 | | | |
| M10 | | | |
| M11 | | | |
| M12 | | | |
| M13 | | | |
| M14 | | | |

## Crash-regression fixture

- Fixture / private issue reference:
- License and provenance:
- SHA-256:
- Profiles and overrides:
- Consecutive successful slices:
- Peak / retained PSS:

## Decision

- Automated gate: AUTOMATED PASSED / INCOMPLETE / FAILED
- Manual matrix: REQUIRED / COMPLETE (evidence link)
- Play-delivered internal test: REQUIRED / COMPLETE (evidence link)
- Hard blockers remaining:
- Play internal-test URL/build checked:
- Pre-launch report checked:
- Human approver:
- Upload authorization: INCOMPLETE / APPROVE / REJECT
```

The automated script may write only `AUTOMATED PASSED / MANUAL +
PLAY-DELIVERED REQUIRED`, `INCOMPLETE`, or `FAILED`. It cannot write `APPROVE` for
the upload; that decision requires a human-owned, documented manual matrix.
