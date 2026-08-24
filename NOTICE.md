# Notices

Copyright (C) 2026 Feresa Slicer contributors.

Feresa Slicer is an independent Android project. The project is intended to
integrate slicing technology derived from OrcaSlicer while preserving the
requirements of GNU AGPL version 3.

OrcaSlicer is Copyright its respective contributors and is distributed under
GNU AGPL version 3:

https://github.com/OrcaSlicer/OrcaSlicer

The APK includes a generated subset of OrcaSlicer's system printer-profile
catalog. Source revision and details are recorded in `THIRD_PARTY_NOTICES.md`.
It also includes a source-built ARM64 slicing engine based on the pinned
OrcaSlicer Mobile source revision recorded there. Feresa's Android build omits
the OCCT-backed STEP, SVG-object, and TextShape paths and packages no Open
CASCADE runtime.

The optional proprietary Bambu networking plugin is intentionally excluded.
The working product name, icons, and visual identity are not affiliated with
or endorsed by OrcaSlicer or Bambu Lab.

The APK bundles Three.js under the MIT License. Build tooling also uses
esbuild under the MIT License. See `THIRD_PARTY_NOTICES.md`.
