cmake_minimum_required(VERSION 3.22.1)

project(feresa_orca_headless_spike LANGUAGES C CXX)

if(NOT ANDROID)
    message(FATAL_ERROR "This spike is intentionally Android-only. Configure it with the Android NDK toolchain.")
endif()

set(ORCA_ROOT
    "${CMAKE_CURRENT_LIST_DIR}/../../external/orcaslicer"
    CACHE PATH
    "Pinned OrcaSlicer source checkout"
)

if(NOT EXISTS "${ORCA_ROOT}/deps_src/clipper2/CMakeLists.txt")
    message(FATAL_ERROR
        "Pinned OrcaSlicer checkout is missing. Run scripts/fetch-orcaslicer.sh first."
    )
endif()

# Clipper2 is an actual Orca/libslic3r geometry dependency. Building it first
# gives us a small, deterministic NDK milestone without pulling desktop-only
# OpenGL, wxWidgets, Python, OpenVDB, OpenCV, or OCCT into the Android graph.
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
add_subdirectory(
    "${ORCA_ROOT}/deps_src/clipper2"
    "${CMAKE_CURRENT_BINARY_DIR}/clipper2"
    EXCLUDE_FROM_ALL
)

# First dependency tranche that is self-contained in the pinned Orca checkout
# and compiles without desktop SDKs or prebuilt host libraries.
set(SLIC3R_STATIC ON)
set(MCUT_BUILD_DOCUMENTATION OFF CACHE BOOL "" FORCE)
set(MCUT_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(MCUT_BUILD_TUTORIALS OFF CACHE BOOL "" FORCE)
set(MCUT_BUILD_AS_SHARED_LIB OFF CACHE BOOL "" FORCE)
add_subdirectory("${ORCA_ROOT}/deps_src/miniz" "${CMAKE_CURRENT_BINARY_DIR}/miniz" EXCLUDE_FROM_ALL)
add_subdirectory("${ORCA_ROOT}/deps_src/qhull" "${CMAKE_CURRENT_BINARY_DIR}/qhull" EXCLUDE_FROM_ALL)
add_subdirectory("${ORCA_ROOT}/deps_src/qoi" "${CMAKE_CURRENT_BINARY_DIR}/qoi" EXCLUDE_FROM_ALL)
add_subdirectory("${ORCA_ROOT}/deps_src/semver" "${CMAKE_CURRENT_BINARY_DIR}/semver" EXCLUDE_FROM_ALL)
add_subdirectory("${ORCA_ROOT}/deps_src/glu-libtess" "${CMAKE_CURRENT_BINARY_DIR}/glu-libtess" EXCLUDE_FROM_ALL)
add_subdirectory("${ORCA_ROOT}/deps_src/mcut" "${CMAKE_CURRENT_BINARY_DIR}/mcut" EXCLUDE_FROM_ALL)

add_library(feresa_orca_geometry_spike SHARED
    orca_geometry_smoke.cpp
)

target_compile_features(feresa_orca_geometry_spike PRIVATE cxx_std_17)
target_link_libraries(feresa_orca_geometry_spike PRIVATE
    Clipper2
    miniz
    qhull
    qoi
    semver
    glu-libtess
    mcut
)

add_executable(feresa_orca_geometry_spike_runner
    orca_geometry_smoke.cpp
    orca_geometry_smoke_main.cpp
)

target_compile_features(feresa_orca_geometry_spike_runner PRIVATE cxx_std_17)
target_link_libraries(feresa_orca_geometry_spike_runner PRIVATE
    Clipper2
    miniz
    qhull
    qoi
    semver
    glu-libtess
    mcut
)
