# Third-party notices

Last audited: 2026-08-24

This file identifies software included in the Feresa Slicer Android
application or needed to reproduce it. It is provided for attribution and
license compliance; it is not legal advice.

Corresponding Feresa Slicer source, integration code, build scripts and this
notice bundle: https://github.com/AMVavilov/feresa-slicer

The exact native-engine source and release are identified below. Full license
texts are bundled in the application under `assets/legal/licenses/`. Feresa
Slicer is independent and is not endorsed by any upstream project.

## Feresa Slicer and Orca-derived code

### Feresa Slicer

Copyright (C) 2026 Feresa Slicer contributors.

License: GNU Affero General Public License version 3
(`assets/legal/AGPL-3.0.txt`).

Source: https://github.com/AMVavilov/feresa-slicer

### OrcaSlicer-derived features and profiles

The OrcaCloud PKCE integration, slicing behavior and generated machine-profile
catalog derive from public OrcaSlicer source. OrcaSlicer is copyright its
respective contributors and is licensed under GNU AGPL version 3.

The profile catalog is based on commit
`728cf63c3d0b3a59be0a70cdc158515f251c9181`. Feresa's separately adapted
gyroid and rectilinear code is based on commit
`d5dbd96dd64b830076c81053ed5fda26d5a1771b`.

License: GNU AGPL version 3 (`assets/legal/AGPL-3.0.txt`).

Sources:

- https://github.com/OrcaSlicer/OrcaSlicer
- https://github.com/OrcaSlicer/OrcaSlicer/tree/728cf63c3d0b3a59be0a70cdc158515f251c9181/resources/profiles
- https://github.com/OrcaSlicer/OrcaSlicer/tree/d5dbd96dd64b830076c81053ed5fda26d5a1771b/src/libslic3r/Fill

The optional proprietary Bambu networking plugin is not included.

### OrcaSlicer Mobile native engine

The package includes an ARM64 native engine rebuilt from OrcaSlicer Mobile
source revision `6fc2e14b9a222301f4432cee26d7ab37d3be86d0`. Feresa builds the
engine and its dynamic GMP/MPFR dependencies with Android NDK r28c and 16 KiB
ELF page alignment. The immutable archive and individual library SHA-256
values used by the Android build are recorded in
`scripts/fetch-orca-mobile-engine.sh` in the corresponding Feresa source.
The pinned corresponding-source recipe is
`scripts/build-orca-mobile-engine-16kb.sh`; its no-OCCT and NDK r28c source
changes are recorded in
`scripts/patches/orca-mobile-6fc2e14-no-occt-ndk28.patch`.

Feresa's no-OCCT Android build excludes the OCCT-backed STEP import and
SVG/TextShape object-construction paths. The application imports STL, OBJ, and
3MF geometry; OBJ and 3MF are normalized to STL before native slicing. No
Open CASCADE shared library or object code is packaged in the application.

Copyright includes OrcaSlicer contributors, Prusa Research a.s. and its
contributors, SoftFever, SliceBeam / OrcaSlicer Mobile contributors, and the
authors retained in the corresponding source.

License for the combined Orca-derived engine: GNU AGPL version 3
(`assets/legal/AGPL-3.0.txt`), subject also to every component license below.

- Source: https://github.com/CodeMasterCody3D/OrcaSlicer-Mobile/tree/6fc2e14b9a222301f4432cee26d7ab37d3be86d0
- Upstream release baseline: https://github.com/CodeMasterCody3D/OrcaSlicer-Mobile/releases/tag/0.4.6
- Corresponding Feresa source and build integration: https://github.com/AMVavilov/feresa-slicer

## Android and Kotlin runtime

The resolved release runtime includes the following Apache License 2.0
components (`assets/legal/licenses/Apache-2.0.txt`):

- AndroidX Activity 1.10.0;
- AndroidX Annotation 1.8.1 and Annotation Experimental 1.4.1;
- AndroidX Collection 1.4.4;
- AndroidX Compose UI, Runtime, Foundation, Animation and Material 1.7.6;
- AndroidX Compose Material3 1.3.1;
- AndroidX Core/Core KTX 1.13.1, Emoji2 1.3.0 and Graphics Path 1.0.1;
- AndroidX Lifecycle 2.8.7, ProfileInstaller 1.4.0, SavedState 1.2.1,
  Startup 1.1.1, Tracing 1.0.0 and WebKit 1.17.0;
- AndroidX Arch Core 2.2.0, Autofill 1.0.0, Concurrent Futures 1.1.0,
  CustomView PoolingContainer 1.0.0, Interpolator 1.0.0 and
  VersionedParcelable 1.1.1;
- Kotlin standard library 2.1.20;
- kotlinx.coroutines Core and Android 1.9.0;
- JetBrains Java Annotations 23.0.0, JSpecify 1.0.0 and Guava
  `listenablefuture` compatibility artifact 1.0.

Copyright notices include Copyright The Android Open Source Project; Copyright
JetBrains s.r.o. and Kotlin contributors; Copyright the KotlinX Coroutines
contributors; Copyright Google LLC; and the respective project contributors.

Sources: https://github.com/androidx/androidx,
https://github.com/JetBrains/kotlin,
https://github.com/Kotlin/kotlinx.coroutines,
https://github.com/JetBrains/java-annotations,
https://github.com/jspecify/jspecify, and https://github.com/google/guava.

## Native components

These components are included in, statically linked into, or dynamically
loaded by the pinned native engine. Versions are taken from the pinned native
build inputs or from version headers retained in the corresponding source.

### Copyleft components

- **ADMesh** — Copyright (C) 1995-1996 Anthony D. Martin and Copyright
  (C) 2013-2014 contributors. GNU GPL version 2 or later.
  Text: `GPL-2.0.txt`. Source: https://github.com/admesh/admesh
- **CGAL 5.4-I-900 development snapshot** — Copyright the CGAL Project and
  contributors. Packages used by the engine are under GNU GPL version 3 or
  later and/or GNU LGPL version 3 or later. Texts: `GPL-3.0.txt` and
  `LGPL-3.0.txt`. Source: https://github.com/CGAL/cgal
- **NLopt 2.5.0** — Copyright (c) 2007-2014 Massachusetts Institute of
  Technology and algorithm authors named in `NLOpt-2.5.0.txt`. The compiled
  combination is GNU LGPL version 2.1 or later because it includes Luksan
  routines. Texts: `LGPL-2.1.txt` and `NLOpt-2.5.0.txt`.
  Source: https://github.com/stevengj/nlopt/tree/v2.5.0
- **MCUT** — Copyright (C) CutDigital Enterprise Ltd and contributors. GNU
  LGPL version 3. Embedded CDT code is Copyright (c) 2019 Leica Geosystems
  Technology AB and CDT contributors under MPL 2.0. Texts: `GPL-3.0.txt`,
  `LGPL-3.0.txt`, `MPL-2.0.txt`, and `MCUT.txt`.
  Source: https://github.com/cutdigital/mcut
- **libnest2d** — Copyright its contributors, including Tamás Mészáros. GNU
  LGPL version 3. Texts: `GPL-3.0.txt` and `LGPL-3.0.txt`.
  Source: https://github.com/tamasmeszaros/libnest2d
- **GNU MP 6.2.1 (GMP and GMP C++ interface)** — Copyright the Free Software
  Foundation, Inc. and contributors. Distributed here under its GNU
  LGPL-3.0-or-later option. Texts: `GPL-3.0.txt` and `LGPL-3.0.txt`.
  Source: https://gmplib.org/
- **GNU MPFR 4.2.1** — Copyright INRIA and MPFR contributors. GNU LGPL version
  3 or later. Texts: `GPL-3.0.txt` and `LGPL-3.0.txt`.
  Source: https://www.mpfr.org/

GMP and MPFR are separate shared libraries. MCUT, libnest2d, CGAL and NLopt
are incorporated into `libslic3r.so`. OCCT is not linked or packaged in the
Android engine. Corresponding source and material needed to rebuild/relink
modified versions must remain available.

### Mozilla Public License components

These use MPL 2.0 (`MPL-2.0.txt`):

- **OpenVDB 8.2.0**, Copyright OpenVDB Project contributors and DreamWorks
  Animation LLC — https://github.com/AcademySoftwareFoundation/openvdb
- **Eigen**, Copyright Eigen contributors including Gael Guennebaud and Benoit
  Jacob — https://gitlab.com/libeigen/eigen
- **libigl**, Copyright Alec Jacobson and contributors —
  https://github.com/libigl/libigl
- **CDT**, Copyright (c) 2019 Leica Geosystems Technology AB and contributors
  (embedded by MCUT) — https://github.com/artem-ogre/CDT

### Apache, LLVM and Boost components

- **Intel TBB 2019 (Android port)** — Copyright Intel Corporation and
  contributors. Apache 2.0: `Apache-2.0.txt`. Feresa's source build pins
  `syoyo/tbb-aarch64` commit
  `c0bf89c041df6b794ddf5970854a6b730cb480b1` (TBB interface version 11002).
  Source: https://github.com/syoyo/tbb-aarch64/tree/c0bf89c041df6b794ddf5970854a6b730cb480b1
- **Android NDK libc++ shared runtime**, NDK 28.2.13676358 (r28c) — Copyright LLVM
  Project contributors. Apache 2.0 with LLVM Exceptions:
  `LLVM-Apache-2.0-with-exception.txt`.
  Source: https://github.com/llvm/llvm-project
- **Boost 1.85.0** — Copyright Boost authors. Boost Software License 1.0:
  `BSL-1.0.txt`. Source: https://www.boost.org/
- **Clipper 6.4.2 / Clipper2 code** — Copyright Angus Johnson. Boost Software
  License 1.0: `BSL-1.0.txt`.
  Sources: http://www.angusj.com/delphi/clipper.php and
  https://github.com/AngusJohnson/Clipper2
- **RapidXML** (through cereal) — Copyright (c) 2006, 2009 Marcin Kalicinski.
  Boost Software License 1.0 option; dual-license notice: `rapidxml.txt`.

### BSD, MIT and similar components

- **Anti-Grain Geometry 2.4** — Copyright (C) 2002-2005 Maxim Shemanarev.
  Modified BSD / AGG Public License: `Anti-Grain-Geometry.txt`.
- **cereal** — Copyright (c) 2014 Randolph Voorhies and Shane Grant.
  BSD 3-Clause: `BSD-3-Clause.txt`.
- **Expat 2.4.x** — Copyright Thai Open Source Software Center Ltd, Clark
  Cooper, Fred L. Drake Jr., Karl Waclawek, Sebastian Pipping and contributors.
  MIT License and notice: `Expat.txt`.
- **Dear ImGui** — Copyright (c) 2014-2024 Omar Cornut and ImGui contributors.
  MIT License and notice: `Dear-ImGui.txt`.
- **miniz** — Copyright 2013-2014 RAD Game Tools and Valve Software; Copyright
  2010-2014 Rich Geldreich and Tenacious Software LLC. Notice: `miniz.txt`.
- **nlohmann/json 3.12.0** — Copyright (c) 2013-2026 Niels Lohmann. MIT:
  `MIT.txt`.
- **ankerl::unordered_dense 3.1.1** — Copyright (c) 2022-2023 Martin
  Leitner-Ankerl. MIT: `MIT.txt`.
- **fast_float** — Copyright Daniel Lemire, João Paulo Magalhaes and
  contributors. MIT: `MIT.txt`.
- **semver.c** — Copyright (c) 2015-2017 Tomas Aparicio. MIT: `MIT.txt`.
- **Quite OK Image (QOI)** — Copyright (c) 2021 Dominic Szablewski. MIT:
  `MIT.txt`.
- **stb code embedded by Dear ImGui** — Copyright Sean Barrett and
  contributors. MIT/public-domain terms; MIT option: `MIT.txt`.
- **MCUT pool allocator** — Copyright (c) 2013 Cosku Acay. MIT: `MIT.txt`.
- **heatshrink 0.4.1** — Copyright (c) 2013-2015 Scott Vokes. ISC:
  `heatshrink-ISC.txt`.
- **Qhull** — Copyright (c) 1993-2020 C.B. Barber and The Geometry Center,
  University of Minnesota. Custom license and source-availability notice:
  `Qhull.txt`. Source: http://www.qhull.org/
- **GLU libtess** — Copyright (C) 1991-2000 Silicon Graphics, Inc. SGI Free
  Software License B 2.0: `SGI-B-2.0.txt`.
- **NLopt permissive algorithms** — MIT/BSD authors and notices:
  `NLOpt-2.5.0.txt`.

### Image and compression components

- **zlib 1.3.1** — Copyright (C) 1995-2024 Jean-loup Gailly and Mark Adler.
  zlib License: `zlib.txt`. Source: https://zlib.net/
- **libpng 1.6.35** — Copyright Glenn Randers-Pehrson, Andreas Dilger, Guy
  Eric Schalnat and contributors. Full notice: `libpng-1.6.35.txt`.
- **libjpeg-turbo 3.0.1** — Copyright libjpeg-turbo and Independent JPEG Group
  authors. Includes BSD-3-Clause, IJG and zlib portions. Full statement:
  `libjpeg-turbo.txt`.
- **NanoSVG** — Copyright (c) 2013-2014 Mikko Mononen. zlib-style license:
  `zlib.txt`.

## Offline viewer

**Three.js 0.185.1** — Copyright (c) 2010-2026 three.js authors. Bundled for
offline rendering under MIT License (`MIT.txt`).
Source: https://github.com/mrdoob/three.js/tree/r185

## Build-only tools

**esbuild 0.28.2** — Copyright (c) 2020 Evan Wallace. Used to produce the
viewer bundle but not loaded as a runtime library. MIT License (`MIT.txt`).
Source: https://github.com/evanw/esbuild

Android Gradle Plugin, Kotlin Gradle plugin, Gradle, CMake and Android SDK/NDK
build tools are governed by their distributions and are not asserted here as
embedded runtime dependencies, except for libc++ above.

## License text index

The package includes `AGPL-3.0.txt` plus these files below `licenses/`:

`Apache-2.0.txt`, `LLVM-Apache-2.0-with-exception.txt`,
`GPL-2.0.txt`, `GPL-3.0.txt`, `LGPL-2.1.txt`, `LGPL-3.0.txt`,
`MPL-2.0.txt`, `BSL-1.0.txt`, `MIT.txt`, `BSD-3-Clause.txt`,
`zlib.txt`, `OCCT-LGPL-exception-1.0.txt`,
`Anti-Grain-Geometry.txt`, `Dear-ImGui.txt`, `Expat.txt`,
`heatshrink-ISC.txt`, `libjpeg-turbo.txt`, `libpng-1.6.35.txt`,
`MCUT.txt`, `miniz.txt`, `NLOpt-2.5.0.txt`, `Qhull.txt`,
`rapidxml.txt`, and `SGI-B-2.0.txt`.

The OCCT exception text is retained as a source-history reference. The
no-OCCT Android engine does not package or link OCCT code.
