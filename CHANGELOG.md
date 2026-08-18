# Changelog

## 0.12.0-alpha.1 — 2026-08-18

First GitHub alpha built around the pinned OrcaSlicer Mobile native engine.

### Model workspace

- Import one or several STL, OBJ, or 3MF geometry files. Imported OBJ/3MF content is validated and
  converted offline to normalized binary STL; 3MF project settings are not imported.
- Detect the actual file format, reject extension/content mismatches, resolve the OPC root model in
  3MF packages, and bound parser memory/complexity with fail-closed XML entity protection.
- Select multiple plate objects in the 3D view and keep stable object identity while editing.
- Move and rotate on X/Y/Z; use linked or per-axis scale.
- Preserve intentional Z elevation in the composed plate passed to Orca instead of
  silently dropping every object back onto the bed.
- Duplicate, rename, center, move to the bed, rotate 90 degrees, and lay the largest mesh face flat.
- Deterministic auto-arrange, transformed build-volume validation, and conservative AABB collision
  warnings.
- Compose every transformed plate object before handing it to Orca, so preview and G-code use the
  same scale -> Rx -> Ry -> Rz -> translation convention.

### Orca-backed slicing and profiles

- Pinned ARM64 OrcaSlicer Mobile `libslic3r` engine with checksum-verified dependencies.
- 72 Orca process wire keys, inherited bundled/cloud presets, and device-level effect checks for
  walls, shells, infill, supports, and speed.
- Actual layer/toolpath preview with role, width, height, speed, travel filtering, and G-code view.
- Read-only OrcaCloud profile synchronization and offline cache.
- Moonraker and OctoPrint upload/start flow with explicit confirmation.

### Release limits

- Development APK, debug-signed, ARM64 only; Android API 28 or newer.
- One plate and one active filament. No 3MF project-setting restoration, object-to-filament
  assignment, slice cancellation, or production certification for unattended printing.
- Inspect generated G-code and verify the physical printer/profile before printing.
