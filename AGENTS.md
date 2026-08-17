# Feresa development rules

## OrcaSlicer-first implementation

- For every slicing feature, bug fix, algorithm, profile field, or G-code behavior, first inspect how it is implemented in the pinned OrcaSlicer checkout at `external/orcaslicer` (see `scripts/fetch-orcaslicer.sh` for the exact upstream commit).
- Port the smallest compatible headless OrcaSlicer / `libslic3r` implementation and preserve its behavior whenever practical. Do not invent a parallel slicing algorithm when the corresponding OrcaSlicer implementation can be reused or adapted.
- Use OrcaSlicer's setting names, defaults, units, validation rules, inheritance, and behavior as the source of truth. Feresa UI controls must be wired to the native engine; do not expose settings that the engine silently ignores.
- Keep desktop GUI, wxWidgets, OpenGL UI, device discovery, and proprietary Bambu components outside the Android native dependency graph.
- Preserve upstream copyright and license notices for ported code, record the pinned source commit, and update `THIRD_PARTY_NOTICES.md` when new OrcaSlicer code or data is incorporated.
- Write an independent implementation only when a direct or adapted port is technically unsuitable for Android. Document the reason and add comparison tests against the pinned OrcaSlicer behavior.
- Validate ports on small deterministic fixtures and on representative real STL files. Compare G-code structure and relevant statistics with the pinned OrcaSlicer implementation before treating a slicing feature as production-ready.

## APK publishing

- Keep the newest tested debug APK at `releases/feresa-slicer-latest.apk` in Git.
- Before committing an updated APK, run the relevant unit/native tests and `:app:assembleDebug`, then copy `app/build/outputs/apk/debug/app-debug.apk` to the stable release path.
- Never replace the tracked APK with an older or unverified build.
