# Third-party notices

## OrcaSlicer / OrcaCloud authentication flow

The OrcaCloud PKCE protocol integration is based on the public OrcaSlicer
source code and public client configuration. OrcaSlicer is licensed under GNU
Affero General Public License version 3. This application is distributed under
the same license. OrcaSlicer and OrcaCloud are projects of their respective
owners; this preview is not presented as an official OrcaSlicer mobile app.

Source: https://github.com/OrcaSlicer/OrcaSlicer

## OrcaSlicer gyroid and rectilinear infill

The Android native slicing engine contains an adapted headless port of the
gyroid wave generator and rectilinear sweep behavior from OrcaSlicer's
`src/libslic3r/Fill` at commit
`d5dbd96dd64b830076c81053ed5fda26d5a1771b`. Feresa replaces OrcaSlicer's
desktop geometry containers with its millimetre-space layer-boundary adapter;
the upstream pattern formula, density correction, angle correction, adaptive
subdivision, Z phase, and profile setting names are retained. The port and its
modifications are distributed under GNU Affero General Public License version
3.

Source: https://github.com/OrcaSlicer/OrcaSlicer/tree/d5dbd96dd64b830076c81053ed5fda26d5a1771b/src/libslic3r/Fill

## OrcaSlicer Mobile native engine

Feresa uses the unmodified ARM64 native slicing artifacts published with
OrcaSlicer Mobile 0.4.6. The release artifact is pinned by SHA-256
`25bd3b72ff698b43991005f0df65ac57f67766ed4b240c48b8f3ec943eafbbdd` and
was built from commit
`6fc2e14b9a222301f4432cee26d7ab37d3be86d0`. The build contains a headless
Android port of OrcaSlicer / `libslic3r` and its dynamically linked Open
Cascade, GMP and MPFR dependencies. The engine, its corresponding source and
Feresa's integration are distributed under GNU Affero General Public License
version 3. Feresa is independent from both projects and is not presented as an
official OrcaSlicer Mobile application.

Source: https://github.com/CodeMasterCody3D/OrcaSlicer-Mobile/tree/6fc2e14b9a222301f4432cee26d7ab37d3be86d0

Release: https://github.com/CodeMasterCody3D/OrcaSlicer-Mobile/releases/tag/0.4.6

## OrcaSlicer system printer profiles

The APK contains a compact, machine-readable catalog generated from
`resources/profiles` in the OrcaSlicer repository at commit
`728cf63c3d0b3a59be0a70cdc158515f251c9181`. The catalog preserves printer
names, manufacturers, print volumes, nozzle diameters, G-code flavors and
default process-profile names. It is covered by OrcaSlicer's GNU Affero General
Public License version 3; the generation script and resulting catalog are
included in this source tree.

Source: https://github.com/OrcaSlicer/OrcaSlicer/tree/728cf63c3d0b3a59be0a70cdc158515f251c9181/resources/profiles

## Three.js 0.185.1

Bundled in the Android APK for offline 3D model and toolpath rendering.

The MIT License

Copyright © 2010-2026 three.js authors

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.

## esbuild 0.28.2

Used only to generate the bundled JavaScript during development.

MIT License

Copyright (c) 2020 Evan Wallace

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
